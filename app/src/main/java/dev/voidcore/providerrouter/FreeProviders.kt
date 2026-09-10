package dev.voidcore.providerrouter

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSource
import java.io.IOException
import java.util.concurrent.TimeUnit

private val json = Json { ignoreUnknownKeys = true }
private fun String.safeMessage(limit: Int = 500) = take(limit).replace(Regex("(?i)(api[_-]?key|authorization)[^,\\n]*"), "credential redacted")

abstract class RemoteFreeTierProvider(
 final override val id: String,
 protected val client: OkHttpClient,
 protected val secretStore: SecretStore,
 protected val secretName: String
) : AiProvider {
 override val location = ProviderLocation.REMOTE
 protected fun key(): String = secretStore.get(secretName)?.takeIf { it.isNotBlank() }
  ?: throw IllegalStateException("$id API key is not configured")
 override suspend fun healthCheck(): ProviderHealth = if (secretStore.get(secretName).isNullOrBlank()) ProviderHealth.UNAVAILABLE else ProviderHealth.UNKNOWN
}

class GroqProvider(
 client: OkHttpClient,
 secretStore: SecretStore,
 private val model: () -> String = { "openai/gpt-oss-20b" }
) : RemoteFreeTierProvider("groq", client, secretStore, "groq_api_key") {
 override fun supportsVision() = false
 override fun supportsTools() = true
 private fun body(request: GenerationRequest, streaming: Boolean) = buildJsonObject {
  put("model", model())
  put("stream", streaming)
  putJsonArray("messages") { add(buildJsonObject { put("role", "user"); put("content", request.text) }) }
 }.toString()
 private fun call(request: GenerationRequest, streaming: Boolean) = Request.Builder()
  .url("https://api.groq.com/openai/v1/chat/completions")
  .header("Authorization", "Bearer ${key()}")
  .header("Content-Type", "application/json")
  .post(body(request, streaming).toRequestBody("application/json".toMediaType()))
  .build()

 override suspend fun generate(request: GenerationRequest): GenerationResult = withContext(Dispatchers.IO) {
  client.newCall(call(request, false)).execute().use { response ->
   val raw = response.body?.string().orEmpty()
   if (!response.isSuccessful) throw IOException("Groq ${response.code}: ${raw.safeMessage()}")
   val root = json.parseToJsonElement(raw).jsonObject
   val text = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.content.orEmpty()
   GenerationResult(text, id)
  }
 }

 override fun stream(request: GenerationRequest): Flow<GenerationEvent> = flow {
  try {
   val response = withContext(Dispatchers.IO) { client.newCall(call(request, true)).execute() }
   response.use {
    if (!it.isSuccessful) { emit(GenerationEvent.Failed("Groq ${it.code}: ${it.body?.string().orEmpty().safeMessage()}")); return@flow }
    val source: BufferedSource = it.body?.source() ?: run { emit(GenerationEvent.Failed("Groq returned no body")); return@flow }
    var full = ""
    while (!source.exhausted()) {
     val line = source.readUtf8Line() ?: break
     if (!line.startsWith("data:")) continue
     val data = line.removePrefix("data:").trim()
     if (data == "[DONE]") break
     val token = runCatching {
      json.parseToJsonElement(data).jsonObject["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("delta")?.jsonObject?.get("content")?.jsonPrimitive?.content
     }.getOrNull()
     if (!token.isNullOrEmpty()) { full += token; emit(GenerationEvent.Token(token)) }
    }
    emit(GenerationEvent.Complete(GenerationResult(full, id)))
   }
  } catch (t: Throwable) { emit(GenerationEvent.Failed(t.message ?: "Groq streaming failed")) }
 }.flowOn(Dispatchers.IO)
}

class GeminiProvider(
 client: OkHttpClient,
 secretStore: SecretStore,
 private val model: () -> String = { "gemini-3.6-flash" }
) : RemoteFreeTierProvider("gemini", client, secretStore, "gemini_api_key") {
 override fun supportsVision() = true
 override fun supportsTools() = true
 private fun body(request: GenerationRequest) = buildJsonObject {
  putJsonArray("contents") { add(buildJsonObject { putJsonArray("parts") { add(buildJsonObject { put("text", request.text) }) } }) }
 }.toString()
 private fun endpoint(streaming: Boolean) = "https://generativelanguage.googleapis.com/v1beta/models/${model()}:${if (streaming) "streamGenerateContent?alt=sse" else "generateContent"}"
 private fun call(request: GenerationRequest, streaming: Boolean) = Request.Builder()
  .url(endpoint(streaming))
  .header("x-goog-api-key", key())
  .header("Content-Type", "application/json")
  .post(body(request).toRequestBody("application/json".toMediaType()))
  .build()
 private fun textFrom(element: JsonElement): String = element.jsonObject["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
  ?.get("content")?.jsonObject?.get("parts")?.jsonArray?.joinToString("") { it.jsonObject["text"]?.jsonPrimitive?.content.orEmpty() }.orEmpty()

 override suspend fun generate(request: GenerationRequest): GenerationResult = withContext(Dispatchers.IO) {
  client.newCall(call(request, false)).execute().use { response ->
   val raw = response.body?.string().orEmpty()
   if (!response.isSuccessful) throw IOException("Gemini ${response.code}: ${raw.safeMessage()}")
   GenerationResult(textFrom(json.parseToJsonElement(raw)), id)
  }
 }
 override fun stream(request: GenerationRequest): Flow<GenerationEvent> = flow {
  try {
   val response = withContext(Dispatchers.IO) { client.newCall(call(request, true)).execute() }
   response.use {
    if (!it.isSuccessful) { emit(GenerationEvent.Failed("Gemini ${it.code}: ${it.body?.string().orEmpty().safeMessage()}")); return@flow }
    val source = it.body?.source() ?: run { emit(GenerationEvent.Failed("Gemini returned no body")); return@flow }
    var full = ""
    while (!source.exhausted()) {
     val line = source.readUtf8Line() ?: break
     if (!line.startsWith("data:")) continue
     val data = line.removePrefix("data:").trim()
     val token = runCatching { textFrom(json.parseToJsonElement(data)) }.getOrNull().orEmpty()
     if (token.isNotEmpty()) { full += token; emit(GenerationEvent.Token(token)) }
    }
    emit(GenerationEvent.Complete(GenerationResult(full, id)))
   }
  } catch (t: Throwable) { emit(GenerationEvent.Failed(t.message ?: "Gemini streaming failed")) }
 }.flowOn(Dispatchers.IO)
}

fun phase2HttpClient(): OkHttpClient = OkHttpClient.Builder()
 .connectTimeout(12, TimeUnit.SECONDS)
 .readTimeout(60, TimeUnit.SECONDS)
 .writeTimeout(30, TimeUnit.SECONDS)
 .build()
