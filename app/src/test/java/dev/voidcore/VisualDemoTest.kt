package dev.voidcore

import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.voidcore.app.VisualDemoViewModel
import dev.voidcore.assistantstate.*
import dev.voidcore.overlayservice.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VisualDemoTest {
 private suspend fun TestScope.withDemo(block: suspend TestScope.(VisualDemoViewModel, AssistantStateEngine, PresencePreviewController) -> Unit) {
  Dispatchers.setMain(StandardTestDispatcher(testScheduler))
  val state = AssistantStateEngine()
  val presence = PresencePreviewController(state)
  val store = ViewModelStore()
  val factory = viewModelFactory { initializer { VisualDemoViewModel(state, presence) } }
  val vm = ViewModelProvider(store, factory)[VisualDemoViewModel::class.java]
  try { block(vm, state, presence) } finally { store.clear(); Dispatchers.resetMain() }
 }
 @Test fun manualSelectionCancelsPendingSequenceStates() = runTest {
  withDemo { vm, state, _ ->
   vm.fullSequence(); runCurrent(); advanceTimeBy(1000); runCurrent()
   assertEquals(AssistantState.INVOKING, state.snapshot.value.state)
   vm.select(AssistantState.ERROR_RECOVERY); runCurrent()
   advanceTimeBy(30000); runCurrent()
   assertEquals(AssistantState.ERROR_RECOVERY, state.snapshot.value.state)
   assertFalse(vm.ui.value.running)
   assertTrue(state.snapshot.value.preview)
  }
 }
 @Test fun stopCancelsTextPreviewBeforeItCanSpeak() = runTest {
  withDemo { vm, state, _ ->
   vm.submit("Test command"); runCurrent()
   assertEquals(AssistantState.THINKING, state.snapshot.value.state)
   vm.stopDemos(); advanceTimeBy(10000); runCurrent()
   assertEquals(AssistantState.THINKING, state.snapshot.value.state)
   assertFalse(vm.ui.value.running)
  }
 }
 @Test fun modePlaybackDoesNotChangeSemanticAssistantState() = runTest {
  withDemo { vm, state, presence ->
   vm.select(AssistantState.LISTENING)
   val snapshot = state.snapshot.value
   vm.morphModes(); runCurrent(); advanceTimeBy(4600); runCurrent()
   assertEquals(PresenceMode.EDGE_AGENT, presence.layout.value.mode)
   assertEquals(snapshot, state.snapshot.value)
   advanceTimeBy(6000); runCurrent()
   assertEquals(PresenceMode.FULL_PRESENCE, presence.layout.value.mode)
  }
 }
 @Test fun completeSequenceDismissesAndReturnsToSleep() = runTest {
  withDemo { vm, state, _ ->
   vm.fullSequence(); runCurrent(); advanceTimeBy(25000); runCurrent()
   assertEquals(AssistantState.SLEEPING, state.snapshot.value.state)
   assertFalse(vm.ui.value.surfaceVisible)
   assertFalse(vm.ui.value.running)
  }
 }
}
