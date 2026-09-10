package dev.voidcore.overlayservice

import dev.voidcore.assistantstate.AssistantStateSource
import dev.voidcore.assistantstate.StateSynchronizedAdapter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class PresenceMode(val title: String) {
 FULL_PRESENCE("Full Presence"), COMPACT("Compact"), CAPSULE("Capsule"), EDGE_AGENT("Edge Agent")
}
data class PresenceLayout(val mode: PresenceMode = PresenceMode.FULL_PRESENCE, val visible: Boolean = true)
/** Layout is presentation configuration, never a second semantic assistant state. */
interface OverlayController : StateSynchronizedAdapter {
 val layout: StateFlow<PresenceLayout>
 suspend fun requestLayout(layout: PresenceLayout)
}
/** App-owned in-app preview only. No Android permissions, WindowManager or service APIs. */
class PresencePreviewController(override val assistantState: AssistantStateSource) : OverlayController {
 private val mutableLayout = MutableStateFlow(PresenceLayout())
 override val layout = mutableLayout.asStateFlow()
 override suspend fun requestLayout(layout: PresenceLayout) { mutableLayout.value = layout }
}
