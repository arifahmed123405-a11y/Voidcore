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

/**
 * Local llama.cpp/OpenAI-compatible provider. Intended for a model server running
 * on the same Android device (for example Termux + llama.cpp on 127.0.0.1:8080).
 * No API key is required and no prompt leaves the device.
 */
class LocalLlamaProvider(
 private val client: OkHttpClient,
 private val secretStore: SecretStore,
 private val defaultEndpoint: String = "http://127.0.0.1:8080",
 private val model: () -> String = { "local" }
) : AiProvider {
 override val id = "local-llama"
 override val location = ProviderLocation.LOCAL
 override fun supportsVision() = false
 override fun supportsTools() = false

 private fun baseUrl(): String = (secretStore.get("local_model_endpoint") ?: defaultEndpoint).trim().trimEnd('/')
 private fun endpoint(path: String) = "${baseUrl()}$path"

 private fun body(request: GenerationRequest, streaming: Boolean) = buildJsonObject {
  put("model", model())
  put("stream", streaming)
  put("temperature", 0.15)
  putJsonArray("messages") {
   add(buildJsonObject { put("role", "user"); put("content", request.text) })
  }
 }.toString()

 private fun call(request: GenerationRequest, streaming: Boolean) = Request.Builder()
  .url(endpoint("/v1/chat/completions"))
  .header("Content-Type", "application/json")
  .post(body(request, streaming).toRequestBody("application/json".toMediaType()))
  .build()

 override suspend fun healthCheck(): ProviderHealth = withContext(Dispatchers.IO) {
  runCatching {
   client.newCall(Request.Builder().url(endpoint("/health")).get().build()).execute().use { response ->
    if (response.isSuccessful) ProviderHealth.HEALTHY else ProviderHealth.DEGRADED
   }
  }.getOrDefault(ProviderHealth.UNAVAILABLE)
 }

 override suspend fun generate(request: GenerationRequest): GenerationResult = withContext(Dispatchers.IO) {
  client.newCall(call(request, false)).execute().use { response ->
   val raw = response.body?.string().orEmpty()
   if (!response.isSuccessful) throw IOException("Local model ${response.code}: ${raw.take(300)}")
   val root = Json { ignoreUnknownKeys = true }.parseToJsonElement(raw).jsonObject
   val text = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject
    ?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.content.orEmpty()
   if (text.isBlank()) throw IOException("Local model returned an empty response")
   GenerationResult(text, id)
  }
 }

 override fun stream(request: GenerationRequest): Flow<GenerationEvent> = flow {
  try {
   val response = withContext(Dispatchers.IO) { client.newCall(call(request, true)).execute() }
   response.use {
    if (!it.isSuccessful) {
     emit(GenerationEvent.Failed("Local model ${it.code}: ${it.body?.string().orEmpty().take(300)}"))
     return@flow
    }
    val source: BufferedSource = it.body?.source() ?: run {
     emit(GenerationEvent.Failed("Local model returned no body")); return@flow
    }
    var full = ""
    while (!source.exhausted()) {
     val line = source.readUtf8Line() ?: break
     if (!line.startsWith("data:")) continue
     val data = line.removePrefix("data:").trim()
     if (data == "[DONE]") break
     val token = runCatching {
      Json { ignoreUnknownKeys = true }.parseToJsonElement(data).jsonObject["choices"]
       ?.jsonArray?.firstOrNull()?.jsonObject?.get("delta")?.jsonObject?.get("content")?.jsonPrimitive?.content
     }.getOrNull()
     if (!token.isNullOrEmpty()) { full += token; emit(GenerationEvent.Token(token)) }
    }
    emit(GenerationEvent.Complete(GenerationResult(full, id)))
   }
  } catch (t: Throwable) {
   emit(GenerationEvent.Failed(t.message ?: "Local model unavailable"))
  }
 }.flowOn(Dispatchers.IO)
}
