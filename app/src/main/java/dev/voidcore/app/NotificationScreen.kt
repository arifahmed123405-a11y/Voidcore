package dev.voidcore.app

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.voidcore.coreui.*
import dev.voidcore.notificationengine.NotificationKind
import dev.voidcore.notificationengine.NotificationRepository
import java.text.DateFormat
import java.util.Date

@Composable
fun NotificationIntelligenceScreen(repository: NotificationRepository, back: () -> Unit) {
 val context = LocalContext.current
 val rows by repository.notifications.collectAsState()
 val prefs by repository.prefs.collectAsState()
 var enabled by remember { mutableStateOf(repository.isListenerEnabled()) }
 LaunchedEffect(Unit) { enabled = repository.isListenerEnabled() }

 LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
  item {
   TextButton(onClick = back) { Text("‹ Back") }
   Text("Notification Intelligence", color = VoidPalette.Ink, style = MaterialTheme.typography.headlineLarge)
   Text("Phase 4 · understand important notifications and reply from the active notification context.", color = VoidPalette.Muted, fontSize = 12.sp)
  }
  item {
   Column(Modifier.luxuryPanel().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Text(if (enabled) "ACCESS ENABLED" else "ACCESS REQUIRED", color = if (enabled) VoidPalette.Accent else MaterialTheme.colorScheme.error)
    Text("Android requires you to explicitly grant Notification access. Void Core cannot enable it silently.", color = VoidPalette.Muted, fontSize = 12.sp)
    TextButton(onClick = {
     context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }) { Text("Open notification access") }
    TextButton(onClick = { enabled = repository.isListenerEnabled() }) { Text("Refresh status") }
   }
  }
  item {
   Column(Modifier.luxuryPanel().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
     Column(Modifier.weight(1f)) {
      Text("Announce important messages", color = VoidPalette.Ink)
      Text("Direct messaging notifications and missed calls only. Off by default for privacy.", color = VoidPalette.Muted, fontSize = 11.sp)
     }
     Switch(prefs.announceImportant, repository::setAnnounceImportant)
    }
    HorizontalDivider(color = VoidPalette.Muted.copy(alpha = .15f))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
     Column(Modifier.weight(1f)) {
      Text("Speak notification preview", color = VoidPalette.Ink)
      Text("When enabled, spoken announcements may include visible notification text.", color = VoidPalette.Muted, fontSize = 11.sp)
     }
     Switch(prefs.speakPreview, repository::setSpeakPreview)
    }
   }
  }
  item {
   FoundationCard("Natural follow-up", "After an incoming message you can say: “What did he say?” or “Tell him I’ll talk to you later.” Void Core uses the latest notification context and Android's inline reply action when available.")
  }
  if (rows.isEmpty()) item { FoundationCard("No captured notifications yet", "Enable notification access, then wait for a new message. Existing notification history is not imported.") }
  items(rows, key = { it.key }) { item ->
   val kind = when (item.kind) {
    NotificationKind.VOICE -> "VOICE"
    NotificationKind.IMAGE -> "PHOTO"
    NotificationKind.VIDEO -> "VIDEO"
    NotificationKind.DOCUMENT -> "DOCUMENT"
    NotificationKind.MISSED_CALL -> "MISSED CALL"
    NotificationKind.GROUP -> "GROUP"
    NotificationKind.TEXT -> "TEXT"
    NotificationKind.OTHER -> "OTHER"
   }
   FoundationCard(
    "${item.sender} · ${item.appName}",
    "$kind · ${item.priority}\n${item.preview.ifBlank { "No visible preview" }}\n${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(item.postedAt))} · ${if(item.canReply) "Inline reply available" else "Read-only"}"
   )
  }
 }
}
