package dev.voidcore.providerrouter

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import dev.voidcore.localmodel.EmbeddedModelManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class EmbeddedLiteRtProvider(private val manager: EmbeddedModelManager) : AiProvider {
    override val id: String = "local-embedded"
    override val location: ProviderLocation = ProviderLocation.LOCAL
    override fun supportsVision(): Boolean = false
    override fun supportsTools(): Boolean = false

    private val initMutex = Mutex()
    @Volatile private var engine: Engine? = null
    @Volatile private var conversation: Conversation? = null

    private suspend fun ensureConversation(): Conversation = initMutex.withLock {
        conversation?.let { return@withLock it }
        val path = manager.modelPathOrNull() ?: error("Local model is not downloaded yet")
        manager.markLoading()
        try {
            val e = Engine(
                EngineConfig(
                    modelPath = path,
                    backend = Backend.CPU(),
                    cacheDir = java.io.File(path).parentFile?.absolutePath
                )
            )
            withContext(Dispatchers.IO) { e.initialize() }
            val c = e.createConversation(
                ConversationConfig(
                    systemInstruction = Contents.of(
                        "You are Void Core, a concise on-device Android assistant. Never claim a phone action happened unless the app's Android tool layer reports success. For ordinary questions answer naturally and briefly."
                    )
                )
            )
            engine = e
            conversation = c
            manager.markLoaded()
            c
        } catch (t: Throwable) {
            manager.markError(t.message ?: "Failed to load local model")
            throw t
        }
    }

    override suspend fun healthCheck(): ProviderHealth = if (manager.modelPathOrNull() != null) ProviderHealth.HEALTHY else ProviderHealth.UNAVAILABLE

    override suspend fun generate(request: GenerationRequest): GenerationResult = withContext(Dispatchers.IO) {
        val c = ensureConversation()
        val message = c.sendMessage(request.text)
        val text = message.toString().trim()
        if (text.isBlank()) error("Local model returned an empty response")
        GenerationResult(text, id)
    }

    override fun stream(request: GenerationRequest): Flow<GenerationEvent> = flow {
        try {
            val c = ensureConversation()
            var full = ""
            c.sendMessageAsync(request.text).collect { message ->
                val chunk = message.toString()
                if (chunk.isNotEmpty()) {
                    full += chunk
                    emit(GenerationEvent.Token(chunk))
                }
            }
            if (full.isBlank()) emit(GenerationEvent.Failed("Local model returned an empty response"))
            else emit(GenerationEvent.Complete(GenerationResult(full, id)))
        } catch (t: Throwable) {
            emit(GenerationEvent.Failed(t.message ?: "Embedded local model unavailable"))
        }
    }.flowOn(Dispatchers.IO)

    fun unload() {
        runCatching { conversation?.close() }
        runCatching { engine?.close() }
        conversation = null
        engine = null
        manager.markReady()
    }
}
