package dev.voidcore.coreui

import dev.voidcore.assistantstate.AssistantState
import androidx.compose.ui.graphics.Color

enum class VisualQuality(val title: String, val points: Int, val strands: Int, val particles: Int, val shells: Int) {
 LOW("Low", 48, 3, 18, 2), STANDARD("Balanced", 96, 5, 34, 3), HIGH("High", 144, 7, 58, 4)
}
/** In-app scenery only; never an assistant state or Android window mode. */
enum class PreviewScene(val title: String) {
 NORMAL("Phone surface"), KEYBOARD("Mock keyboard avoidance"), FULLSCREEN("Mock fullscreen app"),
 LOCK_SCREEN("Mock lock screen"), ACTION_TARGET("Directional action target")
}
enum class DockEdge { LEFT, RIGHT }
data class VisualSettings(val reduceMotion: Boolean = false, val reduceTransparency: Boolean = false, val quality: VisualQuality = VisualQuality.HIGH, val edge: DockEdge = DockEdge.RIGHT)
data class RenderTelemetry(val phase: String = "Rest", val progress: Float = 1f, val listeningAmplitude: Float = 0f, val speakingAmplitude: Float = 0f, val status: String = "Canvas ready · device performance unverified")
/** Geometry targets only. AssistantState remains the sole semantic state enum. */
data class CorePose(
 val open: Float, val energy: Float, val activity: Float, val branch: Float = 0f,
 val project: Float = 0f, val attentive: Float = 0f, val listening: Float = 0f,
 val speaking: Float = 0f, val symmetry: Float = 0f, val recovery: Float = 0f,
 val particles: Float = 0f, val tint: Color = Color(0xFF85C7FF)
)
fun poseFor(state: AssistantState): CorePose = when(state) {
 AssistantState.SLEEPING -> CorePose(.72f, .18f, .10f, particles = .12f)
 AssistantState.INVOKING -> CorePose(1.03f, .96f, .90f, particles = 1f)
 AssistantState.LISTENING -> CorePose(1.10f, .78f, .58f, listening = 1f, particles = .42f)
 AssistantState.THINKING -> CorePose(.91f, .76f, 1f, particles = .48f, tint = Color(0xFFA7B8F0))
 AssistantState.PLANNING -> CorePose(1f, .82f, .78f, branch = 1f, particles = .78f, tint = Color(0xFFB7B4E8))
 AssistantState.ACTING -> CorePose(.84f, .94f, .90f, project = 1f, particles = .88f)
 AssistantState.WAITING_FOR_USER -> CorePose(.97f, .64f, .18f, attentive = 1f, particles = .22f, tint = Color(0xFFD4C4A3))
 AssistantState.SPEAKING -> CorePose(1.04f, .88f, .72f, speaking = 1f, particles = .52f)
 AssistantState.SUCCESS -> CorePose(1.01f, .90f, .35f, symmetry = 1f, particles = .78f, tint = Color(0xFFBDDDCA))
 AssistantState.ERROR_RECOVERY -> CorePose(.92f, .76f, .82f, recovery = 1f, particles = .82f, tint = Color(0xFFD2AE9C))
 AssistantState.DISMISSING -> CorePose(.76f, .38f, .28f, particles = .58f)
}
fun phaseDuration(state: AssistantState, fast: Boolean = false): Int = when(state) {
 AssistantState.INVOKING -> 1400
 AssistantState.DISMISSING -> if (fast) 420 else 1450
 AssistantState.WAITING_FOR_USER -> 2400
 AssistantState.SUCCESS -> 1500
 AssistantState.ERROR_RECOVERY -> 2400
 else -> 900
}
fun phaseLabel(state: AssistantState, progress: Float): String = when(state) {
 AssistantState.INVOKING -> if(progress < .3f) "Edge arrival" else if(progress < .8f) "Filament assembly" else "Settle"
 AssistantState.DISMISSING -> if(progress < .2f) "Settle" else if(progress < .65f) "Shell contraction" else if(progress < .84f) "Light filament" else "Edge retraction"
 AssistantState.ERROR_RECOVERY -> if(progress < .45f) "Separation" else if(progress < .9f) "Reformation" else "Recovered form"
 AssistantState.SUCCESS -> if(progress < .4f) "Convergence" else if(progress < .85f) "Completion pulse" else "Stable"
 else -> if(progress < 1f) "State morph" else "Sustained presence"
}
