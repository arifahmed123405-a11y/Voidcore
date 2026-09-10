package dev.voidcore.coreui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object VoidPalette {
 val Background = Color(0xFF05070C); val Surface = Color(0xFF0E1420)
 val Raised = Color(0xFF182437); val Ink = Color(0xFFF0F4FA)
 val Muted = Color(0xFF97A8BE); val Accent = Color(0xFFB8DCFF)
 val Violet = Color(0xFF9789FF); val Magenta = Color(0xFFF19AD7)
 val Line = Color(0xFF2B394A)
}
object VoidSpace { val Small = 8.dp; val Medium = 16.dp; val Large = 24.dp; val Section = 32.dp }
val VoidShape = RoundedCornerShape(24.dp)
@Composable fun VoidTheme(content: @Composable () -> Unit) {
 MaterialTheme(colorScheme = darkColorScheme(primary = VoidPalette.Accent, background = VoidPalette.Background, surface = VoidPalette.Surface, onSurface = VoidPalette.Ink),
 typography = Typography(headlineLarge = androidx.compose.ui.text.TextStyle(fontSize = 36.sp, fontWeight = FontWeight.Light, letterSpacing = (-1).sp), titleLarge = androidx.compose.ui.text.TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Medium), bodyMedium = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, lineHeight = 21.sp)), content = content)
}
@Composable fun FoundationCard(title: String, detail: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
 Column(modifier.fillMaxWidth().luxuryPanel().then(if(onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(20.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
  Text(title, style = MaterialTheme.typography.titleMedium, color = VoidPalette.Ink)
  Text(detail, style = MaterialTheme.typography.bodyMedium, color = VoidPalette.Muted)
 }
}
@Composable fun ContextChip(text: String, onClick: (() -> Unit)? = null) {
 Text(text, color = VoidPalette.Accent, fontSize = 12.sp, modifier = Modifier.heightIn(min = 48.dp).luxuryPanel(radius = 18).then(if(onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 14.dp, vertical = 9.dp).wrapContentHeight())
}
@Composable fun CommandBar(onPreview: () -> Unit) = FoundationCard("Awaken the core  ↗", "Preview presence · microphone is off", onClick = onPreview)
@Composable fun VoiceTranscriptArea(text: String) = FoundationCard("Voice transcript", text)
@Composable fun EdgeCapsule(state: String) = ContextChip("│  $state")
@Composable fun TaskTimeline(steps: List<String>) = FoundationCard("Task timeline", steps.mapIndexed { i, s -> "${i + 1}  $s" }.joinToString("\n"))
@Composable fun DecisionCard(title: String, detail: String) = FoundationCard(title, detail)
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ConfirmationSheet(title: String, detail: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
 ModalBottomSheet(onDismissRequest = onDismiss, containerColor = VoidPalette.Surface) {
  Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
   Text(title, style = MaterialTheme.typography.titleLarge); Text(detail)
   Button(onClick = onConfirm) { Text("Confirm") }; TextButton(onClick = onDismiss) { Text("Cancel") }
  }
 }
}
@Composable fun NotificationIntelligenceCard(title: String, detail: String) = FoundationCard(title, detail)
@Composable fun RoutineCard(title: String, detail: String) = FoundationCard(title, detail)
@Composable fun SearchResultCard(title: String, detail: String) = FoundationCard(title, detail)
@Composable fun PermissionCard(title: String, detail: String) = FoundationCard(title, detail)
@Composable fun MemoryCard(title: String, detail: String) = FoundationCard(title, detail)
@Composable fun ProviderStatusCard(title: String, detail: String) = FoundationCard(title, detail)
@Composable fun ErrorRecoveryCard(detail: String) = FoundationCard("Recovery", detail)
@Composable fun SuccessReceipt(detail: String) = FoundationCard("Complete", detail)
