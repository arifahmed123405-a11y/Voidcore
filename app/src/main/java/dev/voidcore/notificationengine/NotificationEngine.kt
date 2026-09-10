package dev.voidcore.notificationengine

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dev.voidcore.app.VoidApplication
import dev.voidcore.assistantstate.AssistantState
import dev.voidcore.assistantstate.AssistantStateSource
import dev.voidcore.assistantstate.StateSynchronizedAdapter
import dev.voidcore.voiceengine.VoicePresets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale


enum class NotificationKind { TEXT, VOICE, IMAGE, VIDEO, DOCUMENT, MISSED_CALL, GROUP, OTHER }
enum class NotificationPriority { LOW, NORMAL, IMPORTANT }

data class NotificationSummary(
 val key: String,
 val packageName: String,
 val appName: String,
 val sender: String,
 val preview: String,
 val kind: NotificationKind,
 val priority: NotificationPriority,
 val postedAt: Long,
 val canReply: Boolean
)

data class NotificationPrefs(
 val announceImportant: Boolean = false,
 val speakPreview: Boolean = false
)

internal data class ReplyHandle(
 val pendingIntent: PendingIntent,
 val remoteInputs: Array<RemoteInput>
)

class NotificationRepository(private val context: Context) {
 private val mutableNotifications = MutableStateFlow<List<NotificationSummary>>(emptyList())
 val notifications: StateFlow<List<NotificationSummary>> = mutableNotifications.asStateFlow()
 private val mutablePrefs = MutableStateFlow(loadPrefs())
 val prefs: StateFlow<NotificationPrefs> = mutablePrefs.asStateFlow()
 private val mutableIncoming = MutableSharedFlow<NotificationSummary>(extraBufferCapacity = 32)
 val incoming = mutableIncoming.asSharedFlow()
 private val replyHandles = LinkedHashMap<String, ReplyHandle>()

 val latest: NotificationSummary? get() = mutableNotifications.value.firstOrNull()

 internal fun upsert(summary: NotificationSummary, replyHandle: ReplyHandle?) {
  mutableNotifications.update { current -> (listOf(summary) + current.filterNot { it.key == summary.key }).take(100) }
  if (replyHandle != null) replyHandles[summary.key] = replyHandle else replyHandles.remove(summary.key)
  mutableIncoming.tryEmit(summary)
 }

 fun remove(key: String) {
  mutableNotifications.update { it.filterNot { row -> row.key == key } }
  replyHandles.remove(key)
 }

 fun setAnnounceImportant(enabled: Boolean) {
  mutablePrefs.update { it.copy(announceImportant = enabled) }
  savePrefs()
 }

 fun setSpeakPreview(enabled: Boolean) {
  mutablePrefs.update { it.copy(speakPreview = enabled) }
  savePrefs()
 }

 fun isListenerEnabled(): Boolean = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
  ?.contains(context.packageName) == true

 fun settingsIntent(): Intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

 fun replyLatest(text: String): Result<NotificationSummary> {
  val item = latest ?: return Result.failure(IllegalStateException("No recent notification context"))
  return reply(item.key, text)
 }

 fun reply(key: String, text: String): Result<NotificationSummary> = runCatching {
  require(text.isNotBlank()) { "Reply text is empty" }
  val summary = mutableNotifications.value.firstOrNull { it.key == key } ?: error("Notification is no longer available")
  val handle = replyHandles[key] ?: error("This notification does not expose an inline reply action")
  val intent = Intent()
  val results = android.os.Bundle()
  handle.remoteInputs.forEach { results.putCharSequence(it.resultKey, text) }
  RemoteInput.addResultsToIntent(handle.remoteInputs, intent, results)
  handle.pendingIntent.send(context, 0, intent)
  summary
 }

 private fun loadPrefs(): NotificationPrefs {
  val sp = context.getSharedPreferences("notification_intelligence", Context.MODE_PRIVATE)
  return NotificationPrefs(sp.getBoolean("announce_important", false), sp.getBoolean("speak_preview", false))
 }
 private fun savePrefs() {
  context.getSharedPreferences("notification_intelligence", Context.MODE_PRIVATE).edit()
   .putBoolean("announce_important", mutablePrefs.value.announceImportant)
   .putBoolean("speak_preview", mutablePrefs.value.speakPreview)
   .apply()
 }
}

interface NotificationEngine : StateSynchronizedAdapter {
 val notifications: StateFlow<List<NotificationSummary>>
 suspend fun isUserEnabled(): Boolean
}

class VoidNotificationListenerService : NotificationListenerService() {
 private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
 private val app: VoidApplication get() = application as VoidApplication

 override fun onNotificationPosted(sbn: StatusBarNotification?) {
  val item = sbn ?: return
  if (item.packageName == packageName) return
  val n = item.notification ?: return
  if ((n.flags and Notification.FLAG_ONGOING_EVENT) != 0) return
  val summary = summarize(item, n) ?: return
  app.notificationRepository.upsert(summary, replyHandle(n))
  if (app.notificationRepository.prefs.value.announceImportant && summary.priority == NotificationPriority.IMPORTANT) {
   val state = app.assistantState.snapshot.value.state
   if (state !in setOf(AssistantState.LISTENING, AssistantState.SPEAKING, AssistantState.ACTING)) {
    scope.launch {
     app.assistantState.runtime(AssistantState.WAITING_FOR_USER, "important notification")
     val noun = when (summary.kind) {
      NotificationKind.VOICE -> "voice message"
      NotificationKind.IMAGE -> "photo"
      NotificationKind.VIDEO -> "video"
      NotificationKind.DOCUMENT -> "document"
      NotificationKind.MISSED_CALL -> "missed call"
      else -> "message"
     }
     val base = if (summary.kind == NotificationKind.MISSED_CALL) {
      "You missed a call from ${summary.sender}."
     } else {
      "${summary.sender} sent you a $noun on ${summary.appName}."
     }
     val spoken = if (app.notificationRepository.prefs.value.speakPreview && summary.preview.isNotBlank()) "$base ${summary.preview}" else base
     app.voiceEngine.speak(spoken, VoicePresets.all.first())
    }
   }
  }
 }

 override fun onNotificationRemoved(sbn: StatusBarNotification?) {
  sbn?.key?.let(app.notificationRepository::remove)
 }

 override fun onDestroy() {
  scope.cancel()
  super.onDestroy()
 }

 private fun summarize(sbn: StatusBarNotification, n: Notification): NotificationSummary? {
  val extras = n.extras ?: return null
  val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
  val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
  val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim().orEmpty()
  val preview = bigText.ifBlank { text }
  if (title.isBlank() && preview.isBlank()) return null
  val appName = runCatching {
   val info = packageManager.getApplicationInfo(sbn.packageName, 0)
   packageManager.getApplicationLabel(info).toString()
  }.getOrDefault(sbn.packageName.substringAfterLast('.'))
  val joined = "$title $preview".lowercase(Locale.ROOT)
  val kind = when {
   "missed call" in joined -> NotificationKind.MISSED_CALL
   "voice message" in joined || "voice note" in joined || "audio message" in joined -> NotificationKind.VOICE
   "photo" in joined || "image" in joined -> NotificationKind.IMAGE
   "video" in joined -> NotificationKind.VIDEO
   "document" in joined || "pdf" in joined || "file" in joined -> NotificationKind.DOCUMENT
   looksLikeGroup(title, preview) -> NotificationKind.GROUP
   else -> NotificationKind.TEXT
  }
  val messaging = sbn.packageName in setOf("com.whatsapp", "org.telegram.messenger", "com.google.android.apps.messaging") ||
   appName.lowercase(Locale.ROOT).let { "whatsapp" in it || "telegram" in it || "messages" in it }
  val priority = when {
   kind == NotificationKind.MISSED_CALL -> NotificationPriority.IMPORTANT
   messaging && kind != NotificationKind.GROUP && title.isNotBlank() -> NotificationPriority.IMPORTANT
   messaging -> NotificationPriority.NORMAL
   else -> NotificationPriority.LOW
  }
  return NotificationSummary(sbn.key, sbn.packageName, appName, title.ifBlank { appName }, preview, kind, priority, sbn.postTime, replyHandle(n) != null)
 }

 private fun looksLikeGroup(title: String, preview: String): Boolean {
  val p = preview.lowercase(Locale.ROOT)
  return " messages" in p || "new messages" in p || title.contains("(") && title.contains(")")
 }

 private fun replyHandle(n: Notification): ReplyHandle? {
  val action = n.actions?.firstOrNull { !it.remoteInputs.isNullOrEmpty() } ?: return null
  return ReplyHandle(action.actionIntent, action.remoteInputs ?: return null)
 }
}
