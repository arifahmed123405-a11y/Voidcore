package dev.voidcore.localmodel

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit


enum class EmbeddedModelPhase { NOT_INSTALLED, DOWNLOADING, VERIFYING, READY, LOADING, LOADED, ERROR }

data class EmbeddedModelState(
    val phase: EmbeddedModelPhase = EmbeddedModelPhase.NOT_INSTALLED,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val message: String = "Model not installed",
    val filePath: String? = null
) {
    val progress: Float get() = if (totalBytes > 0) (downloadedBytes.toDouble() / totalBytes).toFloat().coerceIn(0f, 1f) else 0f
}

class EmbeddedModelManager(private val context: Context) {
    companion object {
        const val MODEL_NAME = "Gemma 4 E2B · LiteRT-LM"
        const val MODEL_FILE = "gemma-4-E2B-it.litertlm"
        const val MODEL_SIZE_BYTES = 2_590_000_000L
        const val MODEL_SHA256 = "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c"
        const val MODEL_URL = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm?download=true"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
    private val modelDir = File(context.filesDir, "models").apply { mkdirs() }
    private val modelFile = File(modelDir, MODEL_FILE)
    private val partFile = File(modelDir, "$MODEL_FILE.part")
    private var downloadJob: Job? = null

    private val mutableState = MutableStateFlow(initialState())
    val state = mutableState.asStateFlow()

    fun modelPathOrNull(): String? = modelFile.takeIf { it.isFile && it.length() > 0 }?.absolutePath

    fun markLoading() = mutableState.updateCompat { it.copy(phase = EmbeddedModelPhase.LOADING, message = "Loading local model…") }
    fun markLoaded() = mutableState.updateCompat { it.copy(phase = EmbeddedModelPhase.LOADED, message = "Local model loaded") }
    fun markReady(message: String = "Local model ready") = mutableState.updateCompat { it.copy(phase = EmbeddedModelPhase.READY, message = message) }
    fun markError(message: String) = mutableState.updateCompat { it.copy(phase = EmbeddedModelPhase.ERROR, message = message) }

    fun download() {
        if (downloadJob?.isActive == true) return
        downloadJob = scope.launch {
            try {
                val already = partFile.takeIf { it.exists() }?.length() ?: 0L
                val requestBuilder = Request.Builder().url(MODEL_URL)
                if (already > 0L) requestBuilder.header("Range", "bytes=$already-")
                val response = client.newCall(requestBuilder.build()).execute()
                response.use { res ->
                    if (!(res.isSuccessful || res.code == 206)) error("Download failed: HTTP ${res.code}")
                    val body = res.body ?: error("Download returned no data")
                    val append = already > 0L && res.code == 206
                    val start = if (append) already else 0L
                    if (!append && partFile.exists()) partFile.delete()
                    val remoteLength = body.contentLength().coerceAtLeast(0L)
                    val total = if (remoteLength > 0) start + remoteLength else MODEL_SIZE_BYTES
                    mutableState.value = EmbeddedModelState(EmbeddedModelPhase.DOWNLOADING, start, total, "Downloading local AI…", partFile.absolutePath)
                    FileOutputStream(partFile, append).use { out ->
                        body.byteStream().use { input ->
                            val buffer = ByteArray(1024 * 1024)
                            var downloaded = start
                            var lastUi = 0L
                            while (true) {
                                ensureActive()
                                val read = input.read(buffer)
                                if (read < 0) break
                                out.write(buffer, 0, read)
                                downloaded += read
                                val now = System.currentTimeMillis()
                                if (now - lastUi >= 250) {
                                    lastUi = now
                                    mutableState.value = EmbeddedModelState(EmbeddedModelPhase.DOWNLOADING, downloaded, total, "Downloading local AI…", partFile.absolutePath)
                                }
                            }
                            out.fd.sync()
                            mutableState.value = EmbeddedModelState(EmbeddedModelPhase.VERIFYING, downloaded, total, "Verifying model…", partFile.absolutePath)
                        }
                    }
                }
                val digest = sha256(partFile)
                if (!digest.equals(MODEL_SHA256, ignoreCase = true)) {
                    partFile.delete()
                    error("Model checksum failed. Download removed; retry on a stable connection.")
                }
                if (modelFile.exists()) modelFile.delete()
                if (!partFile.renameTo(modelFile)) {
                    partFile.copyTo(modelFile, overwrite = true)
                    partFile.delete()
                }
                mutableState.value = EmbeddedModelState(EmbeddedModelPhase.READY, modelFile.length(), modelFile.length(), "Local model ready", modelFile.absolutePath)
            } catch (t: CancellationException) {
                mutableState.value = initialState().copy(message = "Download paused. Tap Download to resume.")
            } catch (t: Throwable) {
                mutableState.value = EmbeddedModelState(EmbeddedModelPhase.ERROR, partFile.takeIf { it.exists() }?.length() ?: 0, MODEL_SIZE_BYTES, t.message ?: "Model download failed", partFile.absolutePath)
            }
        }
    }

    fun pauseDownload() { downloadJob?.cancel() }

    fun deleteModel() {
        downloadJob?.cancel()
        modelFile.delete()
        partFile.delete()
        mutableState.value = EmbeddedModelState()
    }

    private fun initialState(): EmbeddedModelState = when {
        modelFile.isFile && modelFile.length() > 0 -> EmbeddedModelState(EmbeddedModelPhase.READY, modelFile.length(), modelFile.length(), "Local model ready", modelFile.absolutePath)
        partFile.isFile && partFile.length() > 0 -> EmbeddedModelState(EmbeddedModelPhase.NOT_INSTALLED, partFile.length(), MODEL_SIZE_BYTES, "Partial download found. Tap Download to resume.", partFile.absolutePath)
        else -> EmbeddedModelState()
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

private inline fun <T> MutableStateFlow<T>.updateCompat(block: (T) -> T) { value = block(value) }
