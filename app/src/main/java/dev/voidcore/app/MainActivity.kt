package dev.voidcore.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.*
import dev.voidcore.assistantstate.*
import dev.voidcore.coreui.*
import dev.voidcore.overlayservice.PresenceMode
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.compose.LifecycleStartEffect
import dev.voidcore.voiceengine.*

class MainActivity : ComponentActivity() {
 override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); enableEdgeToEdge()
  val app = application as VoidApplication
  setContent { VoidTheme { VoidApp(app) } }
 }
}
@Composable fun VoidApp(app: VoidApplication) {
 val nav = rememberNavController()
 val entry by nav.currentBackStackEntryAsState()
 val snapshot by app.assistantState.snapshot.collectAsStateWithLifecycle()
 val presence by app.presence.layout.collectAsStateWithLifecycle()
 val factory = remember(app) { viewModelFactory { initializer { VisualDemoViewModel(app.assistantState, app.presence) } } }
 val vm: VisualDemoViewModel = viewModel(factory = factory)
 val phase2Factory = remember(app) { viewModelFactory { initializer { Phase2ViewModel(app.assistantState, app.voiceEngine, app.providerRouter, app.secretStore, app.database.foundation(), app.executor, app.notificationRepository, app.embeddedModelManager) } } }
 val phase2: Phase2ViewModel = viewModel(factory = phase2Factory)
 val phase2Ui by phase2.ui.collectAsStateWithLifecycle()
 val demo by vm.ui.collectAsStateWithLifecycle()
 val settings by vm.settings.collectAsStateWithLifecycle()
 val route = AppRoute.entries.firstOrNull { it.name == entry?.destination?.route } ?: AppRoute.HOME
 val hasVisual = route in setOf(AppRoute.HOME, AppRoute.CONVERSATION, AppRoute.AGENT_WORKSPACE, AppRoute.DIAGNOSTICS)
 val model = rememberCoreRenderModel(snapshot, settings, demo.fastDismiss, hasVisual && (route != AppRoute.DIAGNOSTICS || demo.surfaceVisible), phase2Ui.listeningAmplitude, phase2Ui.speakingAmplitude)
 LifecycleStartEffect(vm) { onStopOrDispose { vm.stopDemos(); phase2.stopAudio() } }
 LaunchedEffect(route) { vm.stopDemos() }
 CompositionLocalProvider(LocalVisualSettings provides settings) {
 Scaffold(containerColor = VoidPalette.Background, bottomBar = {
  Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 14.dp, vertical = 8.dp).luxuryPanel(radius=26,elevated=true).padding(5.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
   AppRoute.entries.filter { it.primary }.forEach { item ->
    Text(item.title, textAlign = TextAlign.Center, color = if(route == item) VoidPalette.Accent else VoidPalette.Muted, fontSize = 12.sp, fontWeight = if(route == item) FontWeight.Bold else FontWeight.Normal,
     modifier = Modifier.weight(1f).heightIn(min = 48.dp).background(if(route==item) Brush.horizontalGradient(listOf(Color(0xFF1A2A40),Color(0xFF20263E),Color(0xFF241D35))) else Brush.horizontalGradient(listOf(Color.Transparent,Color.Transparent)),RoundedCornerShape(20.dp)).border(.7.dp,if(route==item) Brush.horizontalGradient(listOf(VoidPalette.Accent.copy(alpha=.42f),VoidPalette.Violet.copy(alpha=.26f),VoidPalette.Magenta.copy(alpha=.18f))) else Brush.horizontalGradient(listOf(Color.Transparent,Color.Transparent)),RoundedCornerShape(20.dp)).semantics { selected = route == item; role = Role.Tab }.clickable { nav.navigate(item.name) { popUpTo(AppRoute.HOME.name) { saveState = true }; launchSingleTop = true; restoreState = true } }.padding(10.dp))
   }
  }
 }) { padding ->
  NavHost(nav, AppRoute.HOME.name, Modifier.padding(padding).spatialScene(settings)) {
   AppRoute.entries.forEach { destination -> composable(destination.name) {
    when(destination) {
     AppRoute.HOME -> PresenceHome(model, demo, vm, phase2) { nav.navigate(it.name) }
     AppRoute.CONVERSATION -> ConversationPreview(model, demo, vm, phase2) { nav.popBackStack() }
     AppRoute.AGENT_WORKSPACE -> AgentWorkspacePreview(model, demo, vm) { nav.popBackStack() }
     AppRoute.VOICE_LAB -> VoiceLabScreen(phase2) { nav.popBackStack() }
     AppRoute.AI_PROVIDERS -> ProviderSettingsScreen(phase2) { nav.popBackStack() }
     AppRoute.DIAGNOSTICS -> VisualDiagnostics(model, demo, presence.mode, vm, phase2) { nav.popBackStack() }
     AppRoute.NOTIFICATIONS -> NotificationIntelligenceScreen(app.notificationRepository) { nav.popBackStack() }
     else -> Screen(destination, { nav.navigate(it.name) }, { nav.popBackStack() })
    }
   } }
  }
 }
}
}
@Composable private fun Screen(route: AppRoute, navigate: (AppRoute) -> Unit, back: () -> Unit) {
 LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
  item {
   Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
    if(!route.primary) Text("‹ Back", color = VoidPalette.Muted, modifier = Modifier.clickable(onClick = back).padding(vertical = 8.dp))
    else Text("V O I D  /  C O R E", color = VoidPalette.Muted, fontSize = 11.sp)
    ContextChip("PHASE 5")
   }
  }
  item { Text(if(route == AppRoute.HOME) "A presence.\nNot a persona." else route.title, color = VoidPalette.Ink, style = MaterialTheme.typography.headlineLarge) }
  when(route) {
   AppRoute.YOU -> {
    item { Text("Your intelligence, your boundaries.", color = VoidPalette.Muted) }
    items(AppRoute.entries.filter { !it.primary }) { target -> FoundationCard(target.title, if(target == AppRoute.DIAGNOSTICS) "Foundation preview & module status" else "Open foundation", onClick = { navigate(target) }) }
   }
   AppRoute.VOICE_LAB -> {
    item { Text("Preset definitions only. These controls require a compatible future synthesis/DSP engine.", color = VoidPalette.Muted) }
    items(VoicePresets.all) { voice -> FoundationCard(voice.name, "Pitch ${voice.pitch} st · speed ${voice.speakingSpeed}×\nSynthetic depth ${voice.syntheticDepth} · metallic ${voice.metallicResonance}") }
    item { FoundationCard("Custom names & temporary choices", "Prepared: Use Omega. Switch to Settings 1. Make your voice deeper. Speak slower. Save this as Dark Voice. Use my Night Voice until morning.\nCommand interpretation is not connected.") }
   }
   AppRoute.AUDIO_LAB -> {
    items(AudioPresets.all) { audio -> FoundationCard(audio.name, "Cue slots prepared · no audio assets installed · haptics off") }
   }
   AppRoute.AUTOMATIONS -> { item { RoutineCard("No automations yet", "Trigger and workflow contracts are ready. Nothing is scheduled.") } }
   AppRoute.ACTIVITY -> { item { TaskTimeline(listOf("No actions have been executed.", "Future receipts appear after validated execution.")) } }
   AppRoute.AI_PROVIDERS -> { item { ProviderStatusCard("No provider connected", "Free provider and local adapter interfaces are ready. No paid API or runtime builder dependency.") } }
   AppRoute.MEMORY -> { item { MemoryCard("Local by default", "App-private local models prepared. Personal context is not sent anywhere. Profile editing and recall are future work.") } }
   AppRoute.PRIVACY, AppRoute.TRUST_SAFETY -> { item { PermissionCard("Phase 3 permissions", "Microphone and internet support voice + free AI. Flashlight, brightness, alarms, media and app launching use native Android APIs. Accessibility remains opt-in and must be enabled manually. Android backup is disabled.") } }
   else -> { item { FoundationCard("Reserved for a later phase", "${route.title} has a navigation destination and architecture boundary. Its real capability is intentionally not implemented.") } }
  }
  item { Spacer(Modifier.height(16.dp)) }
 }
}
