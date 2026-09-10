package dev.voidcore.app

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import dev.voidcore.coreui.*
import dev.voidcore.providerrouter.ProviderStatus
import dev.voidcore.localmodel.EmbeddedModelPhase
import dev.voidcore.voiceengine.VoicePresets

@Composable
fun rememberPhase2MicAction(vm: Phase2ViewModel): () -> Unit {
 val context = LocalContext.current
 val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
  vm.setMicPermission(granted)
  if (granted) vm.toggleListening()
 }
 LaunchedEffect(Unit) {
  vm.setMicPermission(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
 }
 return {
  if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
   vm.setMicPermission(true); vm.toggleListening()
  } else launcher.launch(Manifest.permission.RECORD_AUDIO)
 }
}

@Composable
fun ProviderSettingsScreen(vm: Phase2ViewModel, back: () -> Unit) {
 val ui by vm.ui.collectAsState()
 val model by vm.embeddedModelState.collectAsState()
 var geminiKey by remember { mutableStateOf("") }
 var groqKey by remember { mutableStateOf("") }
 LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
  item { TextButton(onClick = back) { Text("‹ Back") }; Text("AI Providers", color = VoidPalette.Ink, style = MaterialTheme.typography.headlineLarge) }
  item { Text("LOCAL FIRST. Void Core now runs its local model inside the app. No Termux and no localhost server. Device actions stay in the Android tool layer; Gemini/Groq are conversation fallbacks only.", color = VoidPalette.Muted, fontSize = 12.sp) }
  item {
   Column(Modifier.luxuryPanel().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Text("Embedded Local AI · PRIMARY", color = VoidPalette.Ink)
    Text("Gemma 4 E2B · LiteRT-LM · runs directly inside Void Core", color = VoidPalette.Muted, fontSize = 11.sp)
    Text(model.message, color = if (model.phase == EmbeddedModelPhase.ERROR) MaterialTheme.colorScheme.error else VoidPalette.Muted, fontSize = 11.sp)
    if (model.totalBytes > 0L) {
     LinearProgressIndicator(progress = { model.progress }, modifier = Modifier.fillMaxWidth())
     Text("${(model.downloadedBytes / 1_000_000)} MB / ${(model.totalBytes / 1_000_000)} MB", color = VoidPalette.Muted, fontSize = 10.sp)
    }
    Row(Modifier.horizontalScroll(rememberScrollState())) {
     when (model.phase) {
      EmbeddedModelPhase.DOWNLOADING, EmbeddedModelPhase.VERIFYING -> TextButton(onClick = vm::pauseEmbeddedModelDownload, enabled = model.phase == EmbeddedModelPhase.DOWNLOADING) { Text("Pause") }
      EmbeddedModelPhase.READY, EmbeddedModelPhase.LOADED -> TextButton(onClick = { vm.testProvider("local-embedded") }) { Text("Test local") }
      EmbeddedModelPhase.LOADING -> TextButton(onClick = {}) { Text("Loading…") }
      else -> TextButton(onClick = vm::downloadEmbeddedModel) { Text(if (model.downloadedBytes > 0) "Resume download" else "Download model") }
     }
     if (model.downloadedBytes > 0L || model.phase == EmbeddedModelPhase.READY || model.phase == EmbeddedModelPhase.LOADED) {
      TextButton(onClick = vm::deleteEmbeddedModel) { Text("Delete") }
     }
    }
    Text("~2.59 GB download. Stored in Void Core private app storage. SHA-256 verified before loading.", color = VoidPalette.Muted, fontSize = 10.sp)
   }
  }
  item {
   Column(Modifier.luxuryPanel().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Text("Gemini 3.6 Flash", color = VoidPalette.Ink)
    Text("Stable model · gemini-3.6-flash", color = VoidPalette.Muted, fontSize = 11.sp)
    OutlinedTextField(geminiKey, { geminiKey = it }, label = { Text("API key") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Row { TextButton(onClick = { vm.saveProviderKey("gemini", geminiKey); geminiKey = ""; vm.refreshProviders() }) { Text("Save securely") }; TextButton(onClick = { vm.testProvider("gemini") }) { Text("Test") } }
   }
  }
  item {
   Column(Modifier.luxuryPanel().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Text("Groq", color = VoidPalette.Ink)
    OutlinedTextField(groqKey, { groqKey = it }, label = { Text("API key") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Row { TextButton(onClick = { vm.saveProviderKey("groq", groqKey); groqKey = ""; vm.refreshProviders() }) { Text("Save securely") }; TextButton(onClick = { vm.testProvider("groq") }) { Text("Test") } }
   }
  }
  items(ui.providerStatuses) { status -> ProviderStatusCard(status.id, providerDetail(status)) }
  ui.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
  if (ui.response.isNotBlank()) item { FoundationCard("Connection result", ui.response) }
 }
}

private fun providerDetail(status: ProviderStatus) = "Health ${status.health} · ${if(status.available) "available" else "unavailable"} · priority ${status.priority + 1}"

@Composable
fun VoiceLabScreen(vm: Phase2ViewModel, back: () -> Unit) {
 val ui by vm.ui.collectAsState()
 var customName by remember { mutableStateOf("") }
 LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
  item { TextButton(onClick = back) { Text("‹ Back") }; Text("Voice Lab", color = VoidPalette.Ink, style = MaterialTheme.typography.headlineLarge) }
  item { Text("Android TTS currently applies pitch and speaking speed. Resonance, harmonics, bass, reverb, spatial width and other advanced controls remain saved design parameters for a future DSP layer.", color = VoidPalette.Muted, fontSize = 12.sp) }
  item {
   Column(Modifier.luxuryPanel().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Text("ACTIVE · ${ui.currentVoice.name}", color = VoidPalette.Accent, fontSize = 11.sp)
    Text("Pitch ${"%.1f".format(ui.currentVoice.pitch)} semitones", color = VoidPalette.Ink)
    Slider(value = ui.currentVoice.pitch, onValueChange = vm::adjustPitch, valueRange = -12f..12f)
    Text("Speed ${"%.2f".format(ui.currentVoice.speakingSpeed)}×", color = VoidPalette.Ink)
    Slider(value = ui.currentVoice.speakingSpeed, onValueChange = vm::adjustSpeed, valueRange = .5f..2f)
    Row(Modifier.horizontalScroll(rememberScrollState())) {
     TextButton(onClick = vm::previewVoice) { Text("Preview") }
     TextButton(onClick = vm::duplicateCurrentVoice) { Text("Duplicate") }
     TextButton(onClick = vm::deleteCurrentCustomVoice, enabled = ui.currentVoice.id.startsWith("custom-")) { Text("Delete custom") }
    }
    OutlinedTextField(customName, { customName = it }, label = { Text("Custom profile name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    TextButton(onClick = { vm.saveCurrentVoice(customName); customName = "" }) { Text("Save as custom") }
   }
  }
  item { Text("BUILT-IN PRESETS", color = VoidPalette.Muted, fontSize = 10.sp, letterSpacing = 2.sp) }
  items(VoicePresets.all) { profile ->
   FoundationCard(profile.name, "Pitch ${profile.pitch} st · speed ${profile.speakingSpeed}×\n${if(profile.id == ui.currentVoice.id) "ACTIVE" else "Tap to select"}", onClick = { vm.selectVoice(profile) })
  }
  if (ui.customVoices.isNotEmpty()) item { Text("CUSTOM PROFILES", color = VoidPalette.Muted, fontSize = 10.sp, letterSpacing = 2.sp) }
  items(ui.customVoices) { profile ->
   FoundationCard(profile.name, "Pitch ${profile.pitch} st · speed ${profile.speakingSpeed}×\n${if(profile.id == ui.currentVoice.id) "ACTIVE" else "Tap to select"}", onClick = { vm.selectVoice(profile) })
  }
  item { FoundationCard("Supported now", "Pitch · speaking speed · profile selection · custom profile persistence · TTS preview") }
  item { FoundationCard("Reserved for DSP", "Cadence · pause shaping · metallic resonance · synthetic depth · harmonic layer · bass · brightness · reverb · stereo width · digital texture · glitch · state-specific processing") }
 }
}

@Composable
fun Phase2DiagnosticsCard(vm: Phase2ViewModel) {
 val ui by vm.ui.collectAsState()
 FoundationCard("Phase 2 runtime", buildString {
  append("Microphone permission: ${ui.microphonePermission}\n")
  append("STT: Android SpeechRecognizer · ${if(ui.listening) "ACTIVE" else "idle"}\n")
  append("Partial transcript: ${ui.partialTranscript.ifBlank { "—" }}\n")
  append("Final transcript: ${ui.finalTranscript.ifBlank { "—" }}\n")
  append("TTS: Android TextToSpeech · ${if(ui.ttsSpeaking) "ACTIVE" else "idle"}\n")
  append("Voice profile: ${ui.currentVoice.name}\n")
  append("Active provider: ${ui.activeProvider ?: "—"}\n")
  append("Fallback/failure count: ${ui.fallbackCount}\n")
  append("Streaming: ${ui.streaming}\n")
  append("Conversation turns: ${ui.conversationTurns}\n")
  append("Task classification: ${ui.classification?.kind ?: "—"}\n")
  append("Listening amplitude: ${"%.2f".format(ui.listeningAmplitude)}\n")
  append("Last error: ${ui.error ?: "—"}")
 })
}
