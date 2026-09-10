package dev.voidcore.assistantstate

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AssistantState { SLEEPING, INVOKING, LISTENING, THINKING, PLANNING, ACTING, WAITING_FOR_USER, SPEAKING, SUCCESS, ERROR_RECOVERY, DISMISSING }
data class AssistantSnapshot(val state: AssistantState = AssistantState.SLEEPING, val revision: Long = 0, val source: String = "foundation", val preview: Boolean = true)
interface AssistantStateSource { val snapshot: StateFlow<AssistantSnapshot> }
/** One application-owned source. Preview transitions never authorize side effects. */
class AssistantStateEngine : AssistantStateSource {
 private val mutable = MutableStateFlow(AssistantSnapshot())
 override val snapshot = mutable.asStateFlow()
 @Synchronized fun preview(state: AssistantState) { mutable.value = AssistantSnapshot(state, mutable.value.revision + 1, "diagnostics preview", true) }
 /** Runtime transition for trusted coordinator/voice components. This changes presentation state only. */
 @Synchronized fun runtime(state: AssistantState, source: String) {
  mutable.value = AssistantSnapshot(state, mutable.value.revision + 1, source, false)
 }
 @Synchronized fun transition(expectedRevision: Long, state: AssistantState, source: String): Boolean {
  if (mutable.value.revision != expectedRevision) return false
  mutable.value = AssistantSnapshot(state, expectedRevision + 1, source, false)
  return true
 }
}
/** Voice, sound, haptic, overlay and executor adapters observe this same flow. */
interface StateSynchronizedAdapter { val assistantState: AssistantStateSource }
