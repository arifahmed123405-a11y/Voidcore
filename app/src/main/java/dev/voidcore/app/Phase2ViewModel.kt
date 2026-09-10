package dev.voidcore.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.voidcore.agentbrain.LocalTaskRouter
import dev.voidcore.agentbrain.LocalActionParser
import dev.voidcore.agentbrain.LocalModelActionPlanner
import dev.voidcore.androidtools.*
import dev.voidcore.agentbrain.TaskClassification
import dev.voidcore.agentbrain.TaskKind
import dev.voidcore.assistantstate.AssistantState
import dev.voidcore.assistantstate.AssistantStateEngine
import dev.voidcore.providerrouter.*
import dev.voidcore.memory.FoundationDao
import dev.voidcore.memory.VoiceProfileEntity
import dev.voidcore.voiceengine.*
import dev.voidcore.notificationengine.NotificationRepository
import dev.voidcore.localmodel.EmbeddedModelManager
import dev.voidcore.localmodel.EmbeddedModelState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

private data class ConversationTurn(val user: String, val assistant: String)

data class Phase2Ui(
 val listening: Boolean = false,
 val partialTranscript: String = "",
 val finalTranscript: String = "",
 val response: String = "",
 val error: String? = null,
 val activeProvider: String? = null,
 val fallbackCount: Int = 0,
 val streaming: Boolean = false,
 val listeningAmplitude: Float = 0f,
 val speakingAmplitude: Float = 0f,
 val classification: TaskClassification? = null,
 val conversationTurns: Int = 0,
 val currentVoice: VoiceProfile = VoicePresets.all.first(),
 val ttsSpeaking: Boolean = false,
 val providerStatuses: List<ProviderStatus> = emptyList(),
 val microphonePermission: Boolean = false,
 val customVoices: List<VoiceProfile> = emptyList()
)

class Phase2ViewModel(
 private val state: AssistantStateEngine,
 private val voice: AndroidVoiceEngine,
 private val router: ProviderRouter,
 private val secrets: SecretStore,
 private val dao: FoundationDao,
 private val executor: ValidatedExecutor,
 private val notifications: NotificationRepository,
 private val embeddedModels: EmbeddedModelManager
) : ViewModel() {
 private val mutable = MutableStateFlow(Phase2Ui())
 val ui = mutable.asStateFlow()
 val embeddedModelState = embeddedModels.state
 private val turns = ArrayDeque<ConversationTurn>()
 private var requestJob: Job? = null

 init {
  viewModelScope.launch { router.refreshHealth() }
  viewModelScope.launch {
   dao.voices().collect { rows -> mutable.update { it.copy(customVoices = rows.map { row -> row.profile }) } }
  }
  viewModelScope.launch {
   router.statuses.collect { statuses -> mutable.update { it.copy(providerStatuses = statuses) } }
  }
  viewModelScope.launch {
   voice.events.collect { event ->
    when (event) {
     is VoiceEvent.Activity -> {
      mutable.update { it.copy(listening = event.speechDetected || it.listening, listeningAmplitude = event.level) }
     }
     is VoiceEvent.Transcript -> {
      if (event.final) {
       mutable.update { it.copy(listening = false, finalTranscript = event.text, partialTranscript = "", listeningAmplitude = 0f) }
       if (event.text.isNotBlank()) submit(event.text, fromVoice = true)
      } else mutable.update { it.copy(partialTranscript = event.text) }
     }
     VoiceEvent.SpeechStarted -> {
      state.runtime(AssistantState.SPEAKING, "tts started")
      mutable.update { it.copy(ttsSpeaking = true, speakingAmplitude = 0f) }
     }
     VoiceEvent.SpeechEnded -> {
      mutable.update { it.copy(ttsSpeaking = false, speakingAmplitude = 0f) }
      state.runtime(AssistantState.SUCCESS, "tts completed")
     }
     VoiceEvent.Interrupted -> {
      mutable.update { it.copy(ttsSpeaking = false, speakingAmplitude = 0f) }
     }
     is VoiceEvent.Unavailable -> {
      mutable.update { it.copy(error = event.reason, listening = false, listeningAmplitude = 0f, ttsSpeaking = false) }
      state.runtime(AssistantState.ERROR_RECOVERY, "voice error")
     }
     is VoiceEvent.WakeWord -> Unit
    }
   }
  }
 }

 fun setMicPermission(granted: Boolean) { mutable.update { it.copy(microphonePermission = granted) } }
 fun clearError() { mutable.update { it.copy(error = null) } }

 fun toggleListening() {
  viewModelScope.launch {
   if (!mutable.value.microphonePermission) {
    mutable.update { it.copy(error = "Microphone permission is required") }
    return@launch
   }
   // A second tap while listening is a stop, not a second recognizer session.
   if (mutable.value.listening) {
    voice.stop()
    mutable.update { it.copy(listening = false, listeningAmplitude = 0f) }
    state.runtime(AssistantState.WAITING_FOR_USER, "manual listening stopped")
    return@launch
   }
   voice.interrupt()
   state.runtime(AssistantState.LISTENING, "manual microphone")
   mutable.update { it.copy(listening = true, partialTranscript = "", finalTranscript = "", error = null, response = "") }
   voice.startListening()
  }
 }

 fun interruptAndListen() = toggleListening()

 fun submit(text: String, fromVoice: Boolean = false) {
  val clean = text.trim()
  if (clean.isBlank()) return
  requestJob?.cancel()
  requestJob = viewModelScope.launch {
   mutable.update { it.copy(error = null, response = "", finalTranscript = if(fromVoice) clean else it.finalTranscript) }
   if (handleNotificationContext(clean)) return@launch
   // Device actions always stay local. The deterministic parser gets first refusal before any model.
   val deterministic = LocalActionParser.parse(clean)
   if (deterministic != null) {
    mutable.update { it.copy(classification = TaskClassification(TaskKind.SIMPLE_LOCAL_COMMAND, .99f, "deterministic local action"), streaming = false) }
    executeLocalAction(clean, deterministic, "local-action")
    return@launch
   }

   val classification = LocalTaskRouter.classify(clean)
   mutable.update { it.copy(classification = classification, streaming = false) }
   when (classification.kind) {
    TaskKind.SIMPLE_LOCAL_COMMAND -> {
     // Natural/ambiguous Android commands may be interpreted by the on-device llama.cpp model only.
     // Gemini/Groq are never allowed to turn text into executable Android actions.
     state.runtime(AssistantState.PLANNING, "local model action parser")
     val request = runCatching {
      router.generate(
       GenerationRequest(LocalModelActionPlanner.prompt(clean)),
       RoutingPolicy(listOf("local-embedded"), preferLocal = true, allowFailover = false)
      )
     }.getOrNull()?.let { LocalModelActionPlanner.parse(it.text) }
     if (request == null) {
      state.runtime(AssistantState.WAITING_FOR_USER, "local model unavailable or ambiguous")
      mutable.update { it.copy(response = "I treated that as a phone command, so I did not send it to Gemini. Start the local model or phrase the command more directly.") }
     } else executeLocalAction(clean, request, "local-model-action")
    }
    TaskKind.VOICE_PROFILE -> handleVoiceCommand(clean)
    TaskKind.VISION_NEEDED -> {
     state.runtime(AssistantState.WAITING_FOR_USER, "vision unavailable")
     mutable.update { it.copy(response = "I can understand that request, but screen/camera vision begins in a later phase.") }
    }
    TaskKind.UNSUPPORTED -> mutable.update { it.copy(error = "That request isn't supported yet") }
    else -> askProviders(clean)
   }
  }
 }

 private suspend fun executeLocalAction(userText: String, request: ToolRequest, provider: String) {
  state.runtime(AssistantState.ACTING, "validated Android tool")
  when (val result = executor.submit(request)) {
   is ToolResult.Completed -> deliver(userText, result.receipt, provider)
   is ToolResult.Denied -> { state.runtime(AssistantState.WAITING_FOR_USER, "action denied"); mutable.update { it.copy(response = result.reason) } }
   is ToolResult.Unavailable -> { state.runtime(AssistantState.WAITING_FOR_USER, "action unavailable"); mutable.update { it.copy(response = result.reason) } }
   is ToolResult.Failed -> { state.runtime(AssistantState.ERROR_RECOVERY, "action failed"); mutable.update { it.copy(error = result.reason) } }
  }
 }

 private suspend fun handleNotificationContext(text: String): Boolean {
  val latest = notifications.latest ?: return false
  val l = text.trim().lowercase(Locale.ROOT)
  val asksContent = l in setOf("what did he say", "what did she say", "what did they say", "what did he say?", "what did she say?", "what did they say?", "read it", "read the message", "what's the message", "what is the message")
  if (asksContent) {
   state.runtime(AssistantState.SPEAKING, "notification context read")
   val answer = if (latest.preview.isBlank()) "The notification doesn't expose message text." else "${latest.sender} said: ${latest.preview}"
   deliver(text, answer, "notification-context")
   return true
  }
  val prefixes = listOf("tell him ", "tell her ", "tell them ", "reply ", "reply with ", "respond ", "respond with ")
  val prefix = prefixes.firstOrNull { l.startsWith(it) }
  if (prefix != null) {
   val reply = text.trim().substring(prefix.length).trim()
   if (reply.isBlank()) {
    state.runtime(AssistantState.WAITING_FOR_USER, "notification reply missing text")
    mutable.update { it.copy(response = "What should I reply to ${latest.sender}?") }
    return true
   }
   state.runtime(AssistantState.ACTING, "notification inline reply")
   notifications.replyLatest(reply)
    .onSuccess { deliver(text, "Sent to ${it.sender}.", "notification-reply") }
    .onFailure {
     state.runtime(AssistantState.WAITING_FOR_USER, "notification inline reply unavailable")
     mutable.update { it.copy(response = it.response.ifBlank { "I can see ${latest.sender}'s notification, but Android didn't expose an inline reply action for it." }, error = null) }
    }
   return true
  }
  if (l == "who messaged me" || l == "who messaged me?" || l == "who sent that") {
   deliver(text, "${latest.sender} on ${latest.appName}.", "notification-context")
   return true
  }
  return false
 }

 private suspend fun askProviders(text: String) {
  state.runtime(AssistantState.THINKING, "provider routing")
  val context = buildContext(text)
  val policy = RoutingPolicy(listOf("local-embedded", "gemini", "groq"), preferLocal = true, allowFailover = true)
  var full = ""
  var active: String? = null
  var failures = 0
  mutable.update { it.copy(streaming = true) }
  router.stream(GenerationRequest(context), policy).collect { event ->
   when (event) {
    is GenerationEvent.Token -> {
     full += event.text
     mutable.update { it.copy(response = full, streaming = true) }
    }
    is GenerationEvent.Complete -> {
     active = event.result.providerId
     if (full.isBlank()) full = event.result.text
     mutable.update { it.copy(response = full, activeProvider = active, streaming = false, fallbackCount = failures) }
    }
    is GenerationEvent.Failed -> {
     failures++
     mutable.update { it.copy(error = event.reason, fallbackCount = failures, streaming = false) }
    }
   }
  }
  if (full.isBlank()) {
   state.runtime(AssistantState.ERROR_RECOVERY, "providers unavailable")
   mutable.update { it.copy(error = it.error ?: "No configured free AI provider is available") }
   return
  }
  rememberTurn(text, full)
  voice.speak(full, mutable.value.currentVoice)
 }

 private fun buildContext(latest: String): String {
  val history = turns.takeLast(4).joinToString("\n") { "User: ${it.user}\nAssistant: ${it.assistant}" }
  return buildString {
   append("You are Void Core, a concise, calm synthetic Android assistant. Answer naturally and do not claim device actions occurred. Device actions are executed by a separate local security-gated tool layer. ")
   append("Keep ordinary voice replies brief unless the user asks for detail.\n")
   if (history.isNotBlank()) append(history).append('\n')
   append("User: ").append(latest)
  }
 }

 private suspend fun deliver(userText: String, answer: String, provider: String) {
  mutable.update { it.copy(response = answer, activeProvider = provider, streaming = false) }
  rememberTurn(userText, answer)
  voice.speak(answer, mutable.value.currentVoice)
 }

 private fun rememberTurn(user: String, assistant: String) {
  turns.addLast(ConversationTurn(user, assistant))
  while (turns.size > 6) turns.removeFirst()
  mutable.update { it.copy(conversationTurns = turns.size) }
 }

 private suspend fun handleVoiceCommand(text: String) {
  state.runtime(AssistantState.THINKING, "local voice profile parser")
  val lowered = text.lowercase(Locale.ROOT)
  val profile = (VoicePresets.all + mutable.value.customVoices).firstOrNull { lowered.contains(it.name.lowercase(Locale.ROOT)) }
  if (profile != null) {
   mutable.update { it.copy(currentVoice = profile) }
   deliver(text, "${profile.name} voice selected.", "local-voice")
   return
  }
  val current = mutable.value.currentVoice
  val changed = when {
   "deeper" in lowered -> current.copy(id = "custom-${UUID.randomUUID()}", name = "Adjusted voice", pitch = (current.pitch - 2f).coerceAtLeast(-12f))
   "slower" in lowered -> current.copy(id = "custom-${UUID.randomUUID()}", name = "Adjusted voice", speakingSpeed = (current.speakingSpeed - .1f).coerceAtLeast(.5f))
   "faster" in lowered -> current.copy(id = "custom-${UUID.randomUUID()}", name = "Adjusted voice", speakingSpeed = (current.speakingSpeed + .1f).coerceAtMost(2f))
   else -> null
  }
  if (changed != null) {
   mutable.update { it.copy(currentVoice = changed) }
   deliver(text, "Voice adjusted.", "local-voice")
  } else {
   state.runtime(AssistantState.WAITING_FOR_USER, "voice profile ambiguity")
   mutable.update { it.copy(response = "Tell me which saved voice profile to use, or say deeper, slower, or faster.") }
  }
 }

 fun selectVoice(profile: VoiceProfile) { mutable.update { it.copy(currentVoice = profile) } }
 fun adjustPitch(value: Float) { mutable.update { it.copy(currentVoice = it.currentVoice.copy(pitch = value.coerceIn(-12f, 12f))) } }
 fun adjustSpeed(value: Float) { mutable.update { it.copy(currentVoice = it.currentVoice.copy(speakingSpeed = value.coerceIn(.5f, 2f))) } }
 fun saveCurrentVoice(name: String) {
  val clean = name.trim().ifBlank { "Custom Voice" }
  val profile = mutable.value.currentVoice.copy(id = "custom-${UUID.randomUUID()}", name = clean)
  mutable.update { it.copy(currentVoice = profile) }
  viewModelScope.launch { dao.save(VoiceProfileEntity(profile.id, profile)) }
 }
 fun duplicateCurrentVoice() = saveCurrentVoice("${mutable.value.currentVoice.name} Copy")
 fun deleteCurrentCustomVoice() {
  val id = mutable.value.currentVoice.id
  if (!id.startsWith("custom-")) return
  viewModelScope.launch { dao.deleteVoice(id) }
  mutable.update { it.copy(currentVoice = VoicePresets.all.first()) }
 }
 fun previewVoice() { viewModelScope.launch { voice.speak("Void Core voice preview.", mutable.value.currentVoice) } }
 fun downloadEmbeddedModel() { embeddedModels.download() }
 fun pauseEmbeddedModelDownload() { embeddedModels.pauseDownload() }
 fun deleteEmbeddedModel() { embeddedModels.deleteModel(); refreshProviders() }
 fun saveProviderKey(providerId: String, key: String) { secrets.put("${providerId}_api_key", key.trim()) }
 fun configured(providerId: String): Boolean = !secrets.get("${providerId}_api_key").isNullOrBlank()
 fun refreshProviders() { viewModelScope.launch { router.refreshHealth() } }
 fun stopAudio() { viewModelScope.launch { voice.stop() }; mutable.update { it.copy(listening=false, listeningAmplitude=0f, ttsSpeaking=false, speakingAmplitude=0f) } }
 fun testProvider(providerId: String) {
  viewModelScope.launch {
   state.runtime(AssistantState.THINKING, "provider test")
   val policy = RoutingPolicy(listOf(providerId), preferLocal = providerId == "local-embedded", allowFailover = false)
   runCatching { router.generate(GenerationRequest("Reply with exactly: connection ok"), policy) }
    .onSuccess { mutable.update { ui -> ui.copy(response = "${it.providerId}: ${it.text}", activeProvider = it.providerId, error = null) }; state.runtime(AssistantState.SUCCESS, "provider test") }
    .onFailure { mutable.update { ui -> ui.copy(error = it.message ?: "Connection test failed") }; state.runtime(AssistantState.ERROR_RECOVERY, "provider test") }
  }
 }

 override fun onCleared() { voice.release(); super.onCleared() }
}
