package dev.voidcore.app

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.voidcore.assistantstate.AssistantState
import dev.voidcore.coreui.*
import dev.voidcore.overlayservice.PresenceMode
import kotlinx.coroutines.delay
import java.util.Locale

@Composable private fun VisualHeader(title: String, subtitle: String) {
 Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
   Text("V O I D C O R E",color=VoidPalette.Ink,fontSize=11.sp,letterSpacing=3.sp)
   Text("P R E S E N C E",color=VoidPalette.Accent,fontSize=8.sp)
  }
  Spacer(Modifier.height(12.dp))
  Text(title,color=VoidPalette.Ink,style=MaterialTheme.typography.headlineLarge)
  Text(subtitle,color=VoidPalette.Muted,fontSize=12.sp,lineHeight=18.sp)
 }
}
@Composable private fun Composer(vm: VisualDemoViewModel, ui: VisualDemoUi, model: CoreRenderModel, phase2: Phase2ViewModel) {
 val micAction = rememberPhase2MicAction(phase2)
 CommandSurface(ui.contextAttached,phase2::submit,micAction,vm::attachPreview,model)
}
@Composable fun PresenceHome(model: CoreRenderModel, ui: VisualDemoUi, vm: VisualDemoViewModel, phase2: Phase2ViewModel, navigate: (AppRoute) -> Unit) {
 val p2 by phase2.ui.collectAsState()
 val liveText = when { p2.partialTranscript.isNotBlank() -> p2.partialTranscript; p2.finalTranscript.isNotBlank() -> p2.finalTranscript; else -> "" }
 LazyColumn(Modifier.fillMaxSize().imePadding(),contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
  item { VisualHeader("Your next thought,\nin focus.","PHASE 5G   /   LUMINOUS VEIL + EMBEDDED AI") }
  item { AiCoreContainer(model) }
  item { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.Center) { Text(model.snapshot.state.name.replace('_',' '),color=VoidPalette.Accent,fontSize=10.sp,letterSpacing=2.sp) } }
  if(liveText.isNotBlank()) item { Text(liveText,color=VoidPalette.Muted,fontSize=13.sp) }
  if(p2.response.isNotBlank()) item { Text(p2.response,color=VoidPalette.Accent,fontSize=14.sp,lineHeight=21.sp) }
  if(p2.error!=null) item { Text(p2.error!!,color=MaterialTheme.colorScheme.error,fontSize=12.sp) }
  item { Composer(vm,ui,model,phase2) }
  item { Spacer(Modifier.height(8.dp)); HorizontalDivider(color=VoidPalette.Line) }
  item {
   Column(Modifier.luxuryPanel().padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
    Text("01   /   TASK PREVIEW",color=VoidPalette.Muted,fontSize=10.sp,letterSpacing=2.sp)
    Text("Send latest PDF to Ahmed",color=VoidPalette.Ink,fontSize=17.sp)
    Text("A prepared visual scenario. No file or contact access.",color=VoidPalette.Muted,fontSize=12.sp)
    TextButton(onClick={ navigate(AppRoute.AGENT_WORKSPACE) }) { Text("Open workspace ↗") }
   }
  }
  item { Row(Modifier.horizontalScroll(rememberScrollState())) {
   TextButton(onClick={ navigate(AppRoute.CONVERSATION) }) { Text("Conversation") }
   TextButton(onClick={ navigate(AppRoute.DIAGNOSTICS) }) { Text("Visual lab ↗") }
  } }
 }
}
@Composable fun ConversationPreview(model: CoreRenderModel, ui: VisualDemoUi, vm: VisualDemoViewModel, phase2: Phase2ViewModel, back: () -> Unit) {
 val p2 by phase2.ui.collectAsState()
 LazyColumn(Modifier.fillMaxSize().imePadding(),contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
  item { TextButton(onClick=back) { Text("‹ Back") }; VisualHeader("Room to think.","CONVERSATION · PHASE 5G") }
  item { HorizontalDivider(color=VoidPalette.Line); Spacer(Modifier.height(18.dp)); Text("YOUR THOUGHT",color=VoidPalette.Muted,fontSize=10.sp,letterSpacing=2.sp); Spacer(Modifier.height(10.dp)); Text(ui.command.ifBlank { "Help me find a little clarity." },color=VoidPalette.Ink,fontSize=21.sp,lineHeight=29.sp) }
  item { AiCoreContainer(model,height=390.dp) }
  item { Text(p2.response.ifBlank { "Ask by voice or text. Safe local device actions are now live." },color=VoidPalette.Accent,fontSize=16.sp,lineHeight=24.sp) }
  item { Composer(vm,ui,model,phase2) }
 }
}
@Composable fun AgentWorkspacePreview(model: CoreRenderModel, ui: VisualDemoUi, vm: VisualDemoViewModel, back: () -> Unit) {
 LaunchedEffect(Unit) { vm.enterWorkspace() }
 val steps=listOf("Find file" to "✓", "Resolve Ahmed" to "✓", "Open WhatsApp" to "…", "Attach PDF" to "○", "Confirm send" to "○", "Verify" to "○")
 LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
  item { TextButton(onClick=back) { Text("‹ Back") }; VisualHeader("Agent workspace", "SCRIPTED PROGRESS · NOTHING IS SENT") }
  item { Text("Send latest PDF\nto Ahmed",color=VoidPalette.Ink,fontSize=27.sp,lineHeight=34.sp) }
  item { AiCoreContainer(model,height=230.dp) }
  item { Text(if(ui.workspaceStopped) "Stopped · preview" else if(ui.workspacePaused) "Paused · waiting for you" else "Acting · simulated",color=VoidPalette.Accent,fontSize=12.sp) }
  items(steps) { (name, marker) ->
   val active=marker=="…" && !ui.workspaceStopped
   Row(Modifier.fillMaxWidth().then(if(active) Modifier.luxuryPanel(radius=20) else Modifier).padding(horizontal=16.dp,vertical=14.dp),
    verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
    Column(horizontalAlignment=Alignment.CenterHorizontally) {
     Text(if(ui.workspaceStopped) "—" else marker,color=if(active || marker=="✓") VoidPalette.Accent else VoidPalette.Muted,fontSize=18.sp)
     Spacer(Modifier.height(5.dp))
     Box(Modifier.width(1.dp).height(14.dp).background(VoidPalette.Line))
    }
    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
     Text(name,color=if(active) VoidPalette.Ink else VoidPalette.Muted,fontSize=16.sp)
     if(active) Text(if(ui.workspacePaused) "SUSPENDED · PREVIEW" else "IN PROGRESS · PREVIEW",color=VoidPalette.Accent,fontSize=8.sp,letterSpacing=1.sp)
    }
    if(name=="Confirm send") Text("USER",color=VoidPalette.Muted,fontSize=9.sp)
   }
  }
  item { HorizontalDivider(color=VoidPalette.Line); Row(Modifier.horizontalScroll(rememberScrollState())) {
   TextButton(onClick=vm::pauseWorkspace,enabled=!ui.workspaceStopped) { Text(if(ui.workspacePaused) "Resume" else "Pause") }
   TextButton(onClick=vm::stopWorkspace,enabled=!ui.workspaceStopped) { Text("Stop") }
   TextButton(onClick=vm::takeControl,enabled=!ui.workspaceStopped) { Text("Take Control") }
  } }
  if(ui.workspaceStopped) item { TextButton(onClick=vm::enterWorkspace) { Text("Reset preview") } }
  item { Text("Checkmarks and progress are scripted. No PDF was found, no person was resolved, and WhatsApp was not opened.",color=VoidPalette.Muted,fontSize=12.sp,lineHeight=18.sp) }
 }
}
@Composable private fun ToggleSetting(label: String, checked: Boolean, change: (Boolean) -> Unit) {
 Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) { Text(label,color=VoidPalette.Ink,modifier=Modifier.weight(1f)); Switch(checked=checked,onCheckedChange=change) }
}
@Composable fun VisualDiagnostics(model: CoreRenderModel, ui: VisualDemoUi, mode: PresenceMode, vm: VisualDemoViewModel, phase2: Phase2ViewModel, back: () -> Unit) {
 var metrics by remember { mutableStateOf(RenderTelemetry()) }
 // Diagnostics reads at 8 Hz. The renderer reads the frame clock directly during draw, not layout.
 LaunchedEffect(model) { while(true) { metrics=model.telemetry(); delay(125) } }
 val settings=model.settings
 fun number(value: Float)=String.format(Locale.ROOT,"%.2f",value)
 LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
  item { TextButton(onClick=back) { Text("‹ Back") }; VisualHeader("Visual Lab", "IN-APP INVOCATION SURFACE · NO OVERLAY SERVICE") }
  item { PresenceStage(model,mode,ui.surfaceVisible,{ vm.dismiss(true) },scene=ui.scene) }
  item {
   Row(Modifier.horizontalScroll(rememberScrollState())) {
    TextButton(onClick=vm::invoke) { Text("Invoke") }
    TextButton(onClick={ vm.dismiss() },enabled=ui.surfaceVisible) { Text("Dismiss") }
    TextButton(onClick={ vm.dismiss(true) },enabled=ui.surfaceVisible) { Text("Fast swipe-away") }
   }
  }
  item { Row(Modifier.horizontalScroll(rememberScrollState())) {
   TextButton(onClick=vm::fullSequence) { Text("Play full sequence") }
   TextButton(onClick=vm::stopDemos) { Text("Stop sequence") }
  } }
  item { Text("SPATIAL BEHAVIOR · IN-APP DEMOS",color=VoidPalette.Muted,fontSize=10.sp,letterSpacing=2.sp) }
  item { Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
   PreviewScene.entries.forEach { scene -> ContextChip((if(scene==ui.scene) "• " else "")+scene.title) { vm.scene(scene) } }
  } }
  item { Row(Modifier.horizontalScroll(rememberScrollState())) {
   TextButton(onClick=vm::spatialDemo) { Text("Play safe edge → keyboard → fullscreen → attention") }
   if(ui.scene==PreviewScene.LOCK_SCREEN) TextButton(onClick=vm::mockUnlock) { Text("Mock unlock & expand") }
  } }
  item { Text("Scene: ${ui.scene.title}. No screen, keyboard, biometrics or lock state is read from Android.",color=VoidPalette.Muted,fontSize=11.sp) }
  item { Text("STATE CHOREOGRAPHY",color=VoidPalette.Muted,fontSize=10.sp,letterSpacing=2.sp) }
  item { Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
   AssistantState.entries.forEach { state -> ContextChip((if(state==model.snapshot.state) "• " else "")+state.name.replace('_',' ')) { vm.select(state) } }
  } }
  item { Text("PRESENCE MORPH",color=VoidPalette.Muted,fontSize=10.sp,letterSpacing=2.sp) }
  item { Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
   PresenceMode.entries.forEach { target -> ContextChip((if(target==mode) "• " else "")+target.title) { vm.mode(target) } }
  } }
  item { TextButton(onClick=vm::morphModes) { Text("Play morph → and back") } }
  item {
   Column(Modifier.luxuryPanel().padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
    Text("COMFORT & RENDERING",color=VoidPalette.Accent,fontSize=10.sp,letterSpacing=1.sp)
    ToggleSetting("Reduce Motion",settings.reduceMotion) { vm.settings(settings.copy(reduceMotion=it)) }
    HorizontalDivider(color=VoidPalette.Line)
    ToggleSetting("Reduce Transparency",settings.reduceTransparency) { vm.settings(settings.copy(reduceTransparency=it)) }
   }
  }
  item { Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
   VisualQuality.entries.forEach { quality -> ContextChip((if(quality==settings.quality) "• " else "")+quality.title) { vm.settings(settings.copy(quality=quality)) } }
  } }
  item { Row { TextButton(onClick={ vm.settings(settings.copy(edge=DockEdge.LEFT)) }) { Text("Dock left") }; TextButton(onClick={ vm.settings(settings.copy(edge=DockEdge.RIGHT)) }) { Text("Dock right") } } }
  item { FoundationCard("Visual engine · IMPLEMENTED source", "State: ${model.snapshot.state}\nPresence: ${mode.title}\nPhase: ${metrics.phase}\nProgress: ${(metrics.progress*100).toInt()}%\nListening amplitude: ${number(metrics.listeningAmplitude)}\nSpeaking amplitude: ${number(metrics.speakingAmplitude)}\nReduce Motion: ${settings.reduceMotion}\nReduce Transparency: ${settings.reduceTransparency}\nQuality: ${settings.quality.title}\nRenderer: ${metrics.status}\nSequence: ${if(ui.running) "playing" else "idle"}") }
  item { Phase2DiagnosticsCard(phase2) }
  item { Text("IMPLEMENTED = source exists, not device-verified. MOCKED = visual simulation. NOT AVAILABLE = no live capability.",color=VoidPalette.Muted,fontSize=12.sp) }
  items(foundationDiagnostics) { item -> FoundationCard("${item.name} · ${item.status.title}",item.detail) }
 }
}
