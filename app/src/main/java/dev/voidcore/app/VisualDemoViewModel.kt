package dev.voidcore.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.voidcore.assistantstate.*
import dev.voidcore.coreui.*
import dev.voidcore.overlayservice.*
import dev.voidcore.voiceengine.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class VisualDemoUi(
 val running: Boolean = false, val surfaceVisible: Boolean = false, val fastDismiss: Boolean = false,
 val command: String = "", val transcript: String = "", val workspacePaused: Boolean = false,
 val workspaceStopped: Boolean = false, val contextAttached: Boolean = false,
 val scene: PreviewScene = PreviewScene.NORMAL
)
/** All jobs are UI choreography only. This class has no provider, executor, microphone or file API. */
class VisualDemoViewModel(private val stateEngine: AssistantStateEngine, private val presence: PresencePreviewController) : ViewModel(), VisualFeedbackSource {
 override val assistantState: AssistantStateSource = stateEngine
 private val mutableFeedback = MutableSharedFlow<VisualFeedbackEvent>(extraBufferCapacity = 16)
 override val feedbackEvents = mutableFeedback.asSharedFlow()
 private val mutableUi = MutableStateFlow(VisualDemoUi())
 val ui = mutableUi.asStateFlow()
 private val mutableSettings = MutableStateFlow(VisualSettings())
 val settings = mutableSettings.asStateFlow()
 private var generation = 0
 private var sequence: Job? = null
 private var modes: Job? = null
 init {
  viewModelScope.launch {
   assistantState.snapshot.collect { snapshot ->
    val pair = when(snapshot.state) {
     AssistantState.INVOKING -> VisualCue.WAKE to HapticIntent.SOFT_TICK
     AssistantState.LISTENING -> VisualCue.LISTENING to HapticIntent.SOFT_TICK
     AssistantState.ACTING -> VisualCue.ACTION to HapticIntent.FIRM_TICK
     AssistantState.WAITING_FOR_USER -> VisualCue.CONFIRMATION to HapticIntent.DOUBLE_TICK
     AssistantState.SUCCESS -> VisualCue.SUCCESS to HapticIntent.SETTLE
     AssistantState.ERROR_RECOVERY -> VisualCue.WARNING to HapticIntent.DOUBLE_TICK
     AssistantState.DISMISSING -> VisualCue.DISMISSAL to HapticIntent.SETTLE
     else -> null
    }
    pair?.let { mutableFeedback.tryEmit(VisualFeedbackEvent(it.first, snapshot.revision, it.second)) }
   }
  }
 }
 fun settings(value: VisualSettings) { mutableSettings.value = value }
 private fun state(value: AssistantState) { stateEngine.preview(value) }
 fun select(value: AssistantState) {
  generation++; sequence?.cancel(); modes?.cancel()
  mutableUi.update { it.copy(running = false, surfaceVisible = true, fastDismiss = false, transcript = if(value == AssistantState.LISTENING) "Listening…" else "Visual preview only") }
  state(value)
 }
 fun mode(value: PresenceMode) { modes?.cancel(); viewModelScope.launch { presence.requestLayout(PresenceLayout(value)) } }
 private fun run(block: suspend CoroutineScope.() -> Unit) {
  val token = ++generation
  sequence?.cancel(); modes?.cancel()
  mutableUi.update { it.copy(running = true) }
  sequence = viewModelScope.launch {
   try { block() } finally { if(token == generation) mutableUi.update { it.copy(running = false) } }
  }
 }
 fun invoke() = run {
  mutableUi.update { it.copy(surfaceVisible = true, fastDismiss = false, transcript = "", scene = PreviewScene.NORMAL) }
  presence.requestLayout(PresenceLayout(PresenceMode.FULL_PRESENCE))
  state(AssistantState.INVOKING); delay(1600)
  state(AssistantState.LISTENING); mutableUi.update { it.copy(transcript = "Listening…") }
 }
 fun fullSequence() = run {
  modes?.cancel()
  mutableUi.update { it.copy(surfaceVisible = true, fastDismiss = false, scene = PreviewScene.NORMAL, transcript = "Visual sequence · no microphone") }
  presence.requestLayout(PresenceLayout(PresenceMode.FULL_PRESENCE))
  val sequence = listOf(
   AssistantState.SLEEPING to 700L, AssistantState.INVOKING to 1700L,
   AssistantState.LISTENING to 2300L, AssistantState.THINKING to 2200L,
   AssistantState.PLANNING to 2500L, AssistantState.ACTING to 2100L,
   AssistantState.WAITING_FOR_USER to 2200L, AssistantState.SPEAKING to 2700L,
   AssistantState.SUCCESS to 1700L, AssistantState.DISMISSING to 1600L
  )
  for ((next, dwell) in sequence) {
   state(next)
   mutableUi.update { it.copy(transcript = if(next == AssistantState.LISTENING) "Listening…" else next.name.lowercase().replace('_', ' ') + " · demo") }
   delay(dwell)
  }
  mutableUi.update { it.copy(surfaceVisible = false, transcript = "") }; state(AssistantState.SLEEPING)
 }
 fun dismiss(fast: Boolean = false) = run {
  modes?.cancel()
  mutableUi.update { it.copy(fastDismiss = fast, transcript = "") }
  state(AssistantState.DISMISSING)
  delay(if(fast) 500 else 1550)
  mutableUi.update { it.copy(surfaceVisible = false) }; state(AssistantState.SLEEPING)
 }
 fun morphModes() {
  modes?.cancel()
  mutableUi.update { it.copy(surfaceVisible = true) }
  modes = viewModelScope.launch {
   for(next in listOf(PresenceMode.FULL_PRESENCE, PresenceMode.COMPACT, PresenceMode.CAPSULE, PresenceMode.EDGE_AGENT, PresenceMode.CAPSULE, PresenceMode.COMPACT, PresenceMode.FULL_PRESENCE)) {
    presence.requestLayout(PresenceLayout(next)); delay(1500)
   }
  }
 }
 fun submit(text: String) {
  if(text.isBlank()) return
  run {
   mutableUi.update { it.copy(command = text.trim(), transcript = "Previewing a response…", surfaceVisible = true, fastDismiss = false) }
   state(AssistantState.THINKING); delay(1800)
   mutableUi.update { it.copy(transcript = "This is a visual response preview. No AI request was made.") }
   state(AssistantState.SPEAKING); delay(2600)
   state(AssistantState.SUCCESS)
  }
 }
 fun attachPreview() { mutableUi.update { it.copy(contextAttached = !it.contextAttached) } }
 fun enterWorkspace() {
  select(AssistantState.ACTING)
  mutableUi.update { it.copy(workspacePaused = false, workspaceStopped = false) }
 }
 fun pauseWorkspace() {
  val paused = !mutableUi.value.workspacePaused
  select(if(paused) AssistantState.WAITING_FOR_USER else AssistantState.ACTING)
  mutableUi.update { it.copy(workspacePaused = paused) }
 }
 fun stopWorkspace() {
  select(AssistantState.SLEEPING)
  mutableUi.update { it.copy(workspaceStopped = true, workspacePaused = false) }
 }
 fun takeControl() {
  select(AssistantState.WAITING_FOR_USER)
  mutableUi.update { it.copy(workspacePaused = true, transcript = "Manual control preview · no app has been opened") }
 }
 /** These presets move the same in-app renderer; no OS geometry or permissions are read. */
 fun scene(value: PreviewScene) {
  select(when(value) {
   PreviewScene.LOCK_SCREEN -> AssistantState.WAITING_FOR_USER
   PreviewScene.ACTION_TARGET, PreviewScene.FULLSCREEN -> AssistantState.ACTING
   else -> AssistantState.LISTENING
  })
  mutableUi.update { it.copy(scene = value) }
  mode(when(value) {
   PreviewScene.KEYBOARD, PreviewScene.LOCK_SCREEN -> PresenceMode.COMPACT
   PreviewScene.FULLSCREEN -> PresenceMode.EDGE_AGENT
   else -> PresenceMode.FULL_PRESENCE
  })
 }
 fun mockUnlock() = run {
  mutableUi.update { it.copy(scene = PreviewScene.NORMAL, surfaceVisible = true, fastDismiss = false) }
  state(AssistantState.WAITING_FOR_USER)
  presence.requestLayout(PresenceLayout(PresenceMode.FULL_PRESENCE))
  delay(900); state(AssistantState.LISTENING)
 }
 fun spatialDemo() = run {
  mutableUi.update { it.copy(surfaceVisible = true, fastDismiss = false, scene = PreviewScene.NORMAL) }
  state(AssistantState.ACTING)
  presence.requestLayout(PresenceLayout(PresenceMode.EDGE_AGENT)); delay(1800)
  mutableUi.update { it.copy(scene = PreviewScene.KEYBOARD) }
  presence.requestLayout(PresenceLayout(PresenceMode.COMPACT)); delay(2200)
  mutableUi.update { it.copy(scene = PreviewScene.FULLSCREEN) }
  presence.requestLayout(PresenceLayout(PresenceMode.EDGE_AGENT)); delay(1800)
  mutableUi.update { it.copy(scene = PreviewScene.NORMAL) }
  state(AssistantState.WAITING_FOR_USER)
  presence.requestLayout(PresenceLayout(PresenceMode.FULL_PRESENCE))
 }
 fun stopDemos() {
  generation++; sequence?.cancel(); modes?.cancel()
  mutableUi.update { it.copy(running = false) }
 }
}
