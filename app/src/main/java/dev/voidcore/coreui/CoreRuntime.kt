package dev.voidcore.coreui

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import dev.voidcore.assistantstate.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.*

/** Shared presentation timeline. Contains no competing semantic state source. */
class CoreRenderModel(
 val snapshot: AssistantSnapshot, val settings: VisualSettings,
 val clock: State<Float>, val phase: State<Float>, val progress: State<Float>,
 val assembly: State<Float>, val dismissal: State<Float>, val completion: State<Float>,
 val open: State<Float>, val energy: State<Float>, val activity: State<Float>,
 val branch: State<Float>, val project: State<Float>, val attentive: State<Float>,
 val listening: State<Float>, val speaking: State<Float>, val symmetry: State<Float>,
 val recovery: State<Float>, val particles: State<Float>, val tint: State<androidx.compose.ui.graphics.Color>,
 val liveListeningAmplitude: Float = 0f, val liveSpeakingAmplitude: Float = 0f
) {
 fun listeningEnvelope(): Float = if (liveListeningAmplitude > .01f) liveListeningAmplitude.coerceIn(0f,1f) * listening.value else if(settings.reduceMotion) .22f * listening.value else
  ((.18f + .65f * abs(sin(clock.value * 3.1f)) * abs(sin(clock.value * 1.7f + .6f))).coerceIn(0f,1f) * listening.value)
 fun speakingEnvelope(): Float = if (liveSpeakingAmplitude > .01f) liveSpeakingAmplitude.coerceIn(0f,1f) * speaking.value else if(settings.reduceMotion) .25f * speaking.value else
  ((.12f + .8f * abs(sin(clock.value * 4.3f)) * (.35f + .65f * abs(sin(clock.value * 1.3f)))).coerceIn(0f,1f) * speaking.value)
 fun telemetry() = RenderTelemetry(phaseLabel(snapshot.state, progress.value), progress.value, listeningEnvelope(), speakingEnvelope(),
  if(settings.reduceMotion) "Reduced motion · static state geometry" else "Continuous multi-rate plasma / shell / particles · device FPS unverified")
}
@Composable fun rememberCoreRenderModel(snapshot: AssistantSnapshot, settings: VisualSettings, fastDismiss: Boolean, active: Boolean = true, liveListeningAmplitude: Float = 0f, liveSpeakingAmplitude: Float = 0f): CoreRenderModel {
 val transition = updateTransition(snapshot.state, label = "Canonical assistant choreography")
 val duration = if(settings.reduceMotion) 160 else 760
 @Composable fun parameter(label: String, value: (CorePose) -> Float): State<Float> =
  transition.animateFloat(transitionSpec = { tween(duration, easing = FastOutSlowInEasing) }, label = label) { value(poseFor(it)) }
 val open = parameter("Shell opening") { if(settings.reduceMotion) .95f else it.open }
 val energy = parameter("Internal luminance") { it.energy }
 val activity = parameter("Internal motion") { it.activity }
 val branch = parameter("Plan branching") { it.branch }
 val project = parameter("Outward projection") { it.project }
 val attentive = parameter("Attentive aperture") { it.attentive }
 val listening = parameter("Mock listening response") { it.listening }
 val speaking = parameter("Mock speech response") { it.speaking }
 val symmetry = parameter("Completion alignment") { it.symmetry }
 val recovery = parameter("Recovery separation") { it.recovery }
 val particles = parameter("Purposeful particles") { it.particles }
 val tint = transition.animateColor(transitionSpec = { tween(duration) }, label = "Restrained spectrum") { poseFor(it).tint }
 val progress = remember(snapshot.revision) { Animatable(if(snapshot.state == AssistantState.SLEEPING) 1f else 0f) }
 val assembly = remember { Animatable(1f) }
 val dismissal = remember { Animatable(0f) }
 val completion = remember { Animatable(1f) }
 LaunchedEffect(snapshot.revision, settings.reduceMotion, fastDismiss) {
  launch { progress.animateTo(1f, tween(phaseDuration(snapshot.state, fastDismiss), easing = LinearEasing)) }
  launch {
   if(snapshot.state == AssistantState.INVOKING) {
    assembly.snapTo(0f)
    assembly.animateTo(1f, tween(if(settings.reduceMotion) 180 else 1400, easing = FastOutSlowInEasing))
   } else assembly.animateTo(1f, tween(duration))
  }
  launch { dismissal.animateTo(if(snapshot.state == AssistantState.DISMISSING) 1f else 0f,
   tween(if(snapshot.state == AssistantState.DISMISSING) (if(settings.reduceMotion) 180 else phaseDuration(snapshot.state, fastDismiss)) else duration, easing = LinearEasing)) }
  launch {
   if(snapshot.state == AssistantState.SUCCESS) { completion.snapTo(0f); completion.animateTo(1f, tween(if(settings.reduceMotion) 180 else 1500)) }
   else completion.animateTo(1f, tween(duration))
  }
 }
 val clock = remember { mutableFloatStateOf(0f) }
 val phase = remember { mutableFloatStateOf(0f) }
 val phaseState = rememberUpdatedState(snapshot.state)
 val currentProgress = rememberUpdatedState(progress)
 val owner = LocalLifecycleOwner.current
 LaunchedEffect(owner, settings.reduceMotion, settings.quality, active) {
  if(active && !settings.reduceMotion) owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
   var previous = 0L
   var sampled = 0L
   while(isActive) withFrameNanos { now ->
    val interval=when(settings.quality) { VisualQuality.LOW -> 33_333_333L; VisualQuality.STANDARD -> 22_222_222L; VisualQuality.HIGH -> 0L }
    if(sampled != 0L && now-sampled<interval) return@withFrameNanos
    sampled=now
    val dt = if(previous == 0L) 0f else ((now - previous) / 1_000_000_000f).coerceIn(0f,.06f)
    previous = now
    // Continuous time intentionally does not wrap. Wrapping the clock made independent
    // motion layers snap back together and read like a looping video. Float precision
    // remains more than adequate for multi-day sessions at UI animation rates.
    clock.floatValue += dt
    val stateSpeed = .22f + activity.value * .95f
    val recoveryScale = if(phaseState.value == AssistantState.ERROR_RECOVERY)
     .35f + (1f-currentProgress.value.value)*.65f else 1f
    phase.floatValue += dt * stateSpeed * recoveryScale
   }
  }
 }
 return CoreRenderModel(snapshot, settings, clock, phase, progress.asState(), assembly.asState(), dismissal.asState(), completion.asState(),
  open, energy, activity, branch, project, attentive, listening, speaking, symmetry, recovery, particles, tint, liveListeningAmplitude, liveSpeakingAmplitude)
}
