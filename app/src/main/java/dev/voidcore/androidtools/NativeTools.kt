package dev.voidcore.androidtools

import android.app.AlarmManager
import android.content.*
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.provider.AlarmClock
import android.provider.Settings
import android.view.KeyEvent
import android.content.ComponentName
import dev.voidcore.notificationengine.VoidNotificationListenerService
import kotlinx.coroutines.delay
import dev.voidcore.accessibilityengine.VoidAccessibilityService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class OpenAppTool(private val context: Context) : AndroidTool {
 override val category = ToolCategory.OPEN_APP
 override suspend fun execute(request: ToolRequest): ToolResult = withContext(Dispatchers.Main) {
  val wanted = request.arguments["app"]?.trim().orEmpty()
  if (wanted.isBlank()) return@withContext ToolResult.Failed("No app name supplied", false)
  val pm = context.packageManager
  val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
  val matches = pm.queryIntentActivities(launcher, 0)
  val best = matches.firstOrNull { info ->
   val label = info.loadLabel(pm).toString()
   label.equals(wanted, true) || label.contains(wanted, true) || info.activityInfo.packageName.contains(wanted, true)
  } ?: return@withContext ToolResult.Unavailable("I couldn't find an installed app matching $wanted")
  val intent = pm.getLaunchIntentForPackage(best.activityInfo.packageName)
   ?: return@withContext ToolResult.Unavailable("$wanted does not expose a launcher activity")
  intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
  context.startActivity(intent)
  ToolResult.Completed("Opened ${best.loadLabel(pm)}")
 }
}

class FlashlightTool(private val context: Context) : AndroidTool {
 override val category = ToolCategory.FLASHLIGHT
 override suspend fun execute(request: ToolRequest): ToolResult = withContext(Dispatchers.IO) {
  val enabled = request.arguments["enabled"]?.toBooleanStrictOrNull()
   ?: return@withContext ToolResult.Failed("Flashlight state missing", false)
  val manager = context.getSystemService(CameraManager::class.java)
  val id = manager.cameraIdList.firstOrNull { cameraId ->
   val chars = manager.getCameraCharacteristics(cameraId)
   chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
    chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
  } ?: return@withContext ToolResult.Unavailable("No controllable flashlight found")
  runCatching { manager.setTorchMode(id, enabled) }
   .fold({ ToolResult.Completed("Flashlight ${if(enabled) "on" else "off"}") }, { ToolResult.Failed(it.message ?: "Flashlight failed", true) })
 }
}

class VolumeTool(private val context: Context) : AndroidTool {
 override val category = ToolCategory.VOLUME
 override suspend fun execute(request: ToolRequest): ToolResult = withContext(Dispatchers.Main) {
  val audio = context.getSystemService(AudioManager::class.java)
  val op = request.operation
  when(op) {
   "raise" -> audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
   "lower" -> audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
   "mute" -> audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
   "unmute" -> audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_SHOW_UI)
   "set" -> {
    val pct=request.arguments["percent"]?.toIntOrNull()?.coerceIn(0,100) ?: return@withContext ToolResult.Failed("Volume percentage missing", false)
    val max=audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    audio.setStreamVolume(AudioManager.STREAM_MUSIC,(max*pct/100f).roundToInt(),AudioManager.FLAG_SHOW_UI)
   }
   else -> return@withContext ToolResult.Failed("Unsupported volume operation", false)
  }
  ToolResult.Completed("Volume updated")
 }
}

class BrightnessTool(private val context: Context) : AndroidTool {
 override val category = ToolCategory.BRIGHTNESS
 override suspend fun execute(request: ToolRequest): ToolResult = withContext(Dispatchers.Main) {
  if (!Settings.System.canWrite(context)) {
   val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, android.net.Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
   context.startActivity(intent)
   return@withContext ToolResult.Unavailable("Allow Modify system settings for Void Core, then ask again")
  }
  val resolver=context.contentResolver
  val current = runCatching { Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS) }.getOrDefault(128)
  val value = when(request.operation) {
   "raise" -> current + 51
   "lower" -> current - 51
   "set" -> ((request.arguments["percent"]?.toIntOrNull()?.coerceIn(1,100) ?: return@withContext ToolResult.Failed("Brightness percentage missing",false))*255/100f).roundToInt()
   else -> return@withContext ToolResult.Failed("Unsupported brightness operation",false)
  }.coerceIn(1,255)
  val modeOk=Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
  val valueOk=Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, value)
  if(!modeOk || !valueOk) return@withContext ToolResult.Failed("Android rejected the brightness change", true)
  delay(80)
  val observed=runCatching { Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS) }.getOrDefault(-1)
  if(observed<0 || kotlin.math.abs(observed-value)>8) {
   Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, value)
  }
  val pct=(value*100f/255f).roundToInt()
  ToolResult.Completed("Brightness set to about $pct%")
 }
}

class AlarmTool(private val context: Context) : AndroidTool {
 override val category = ToolCategory.ALARMS
 override suspend fun execute(request: ToolRequest): ToolResult = withContext(Dispatchers.Main) {
  val isTimer=request.operation=="timer"
  val intent = when(request.operation) {
   "timer" -> Intent(AlarmClock.ACTION_SET_TIMER).apply {
    putExtra(AlarmClock.EXTRA_LENGTH, request.arguments["seconds"]?.toIntOrNull() ?: return@withContext ToolResult.Failed("Timer duration missing",false))
    putExtra(AlarmClock.EXTRA_MESSAGE, "Void Core timer")
    putExtra(AlarmClock.EXTRA_SKIP_UI, true)
   }
   "alarm" -> Intent(AlarmClock.ACTION_SET_ALARM).apply {
    putExtra(AlarmClock.EXTRA_HOUR, request.arguments["hour"]?.toIntOrNull() ?: return@withContext ToolResult.Failed("Alarm hour missing",false))
    putExtra(AlarmClock.EXTRA_MINUTES, request.arguments["minute"]?.toIntOrNull() ?: 0)
    putExtra(AlarmClock.EXTRA_MESSAGE, "Void Core alarm")
    putExtra(AlarmClock.EXTRA_SKIP_UI, true)
   }
   else -> return@withContext ToolResult.Failed("Unsupported alarm operation",false)
  }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
  if (intent.resolveActivity(context.packageManager)==null) return@withContext ToolResult.Unavailable("No alarm app can handle this request")
  val first=runCatching { context.startActivity(intent) }
  if(first.isFailure) {
   val visible=Intent(intent).apply { putExtra(AlarmClock.EXTRA_SKIP_UI,false) }
   val second=runCatching { context.startActivity(visible) }
   if(second.isFailure) return@withContext ToolResult.Failed(second.exceptionOrNull()?.message ?: "Alarm app rejected the request", false)
  }
  ToolResult.Completed(if(isTimer) "Timer requested in your Clock app" else "Alarm requested in your Clock app")
 }
}

class MediaTool(private val context: Context) : AndroidTool {
 override val category = ToolCategory.MEDIA_CONTROLS
 override suspend fun execute(request: ToolRequest): ToolResult = withContext(Dispatchers.Main) {
  val sessionManager=context.getSystemService(MediaSessionManager::class.java)
  val component=ComponentName(context, VoidNotificationListenerService::class.java)
  val controllers=runCatching { sessionManager.getActiveSessions(component) }.getOrDefault(emptyList())
  val controller=controllers.firstOrNull { it.playbackState?.state in setOf(
   PlaybackState.STATE_PLAYING, PlaybackState.STATE_PAUSED, PlaybackState.STATE_BUFFERING, PlaybackState.STATE_CONNECTING
  ) } ?: controllers.firstOrNull()
  if(controller!=null) {
   val controls=controller.transportControls
   when(request.operation) {
    "play_pause" -> if(controller.playbackState?.state==PlaybackState.STATE_PLAYING) controls.pause() else controls.play()
    "play" -> controls.play()
    "pause" -> controls.pause()
    "next" -> controls.skipToNext()
    "previous" -> controls.skipToPrevious()
    else -> return@withContext ToolResult.Failed("Unsupported media operation",false)
   }
   return@withContext ToolResult.Completed("Media command sent to ${controller.packageName}")
  }
  val audio=context.getSystemService(AudioManager::class.java)
  val key=when(request.operation) {
   "play_pause" -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
   "play" -> KeyEvent.KEYCODE_MEDIA_PLAY
   "pause" -> KeyEvent.KEYCODE_MEDIA_PAUSE
   "next" -> KeyEvent.KEYCODE_MEDIA_NEXT
   "previous" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
   else -> return@withContext ToolResult.Failed("Unsupported media operation",false)
  }
  audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,key))
  audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP,key))
  ToolResult.Completed("Media command sent")
 }
}

class ClipboardTool(private val context: Context) : AndroidTool {
 override val category = ToolCategory.CLIPBOARD
 override suspend fun execute(request: ToolRequest): ToolResult = withContext(Dispatchers.Main) {
  val text=request.arguments["text"] ?: return@withContext ToolResult.Failed("Clipboard text missing",false)
  val clipboard=context.getSystemService(ClipboardManager::class.java)
  clipboard.setPrimaryClip(ClipData.newPlainText("Void Core",text))
  ToolResult.Completed("Copied to clipboard")
 }
}

class AccessibilityUiTool : AndroidTool {
 override val category = ToolCategory.ACCESSIBILITY_UI_ACTIONS
 override suspend fun execute(request: ToolRequest): ToolResult {
  val service = VoidAccessibilityService.current ?: return ToolResult.Unavailable("Enable Void Core Accessibility Service first")
  suspend fun withPreviousAppFallback(action: () -> Boolean): Boolean {
   // If Void Core is the foreground activity, screen actions must target the app
   // the user was using before invoking the assistant, not Void Core itself.
   if(service.isVoidCoreForeground()) {
    if(!service.goBack()) return false
    delay(550)
   }
   if(action()) return true
   delay(120)
   return action()
  }
  val ok = when(request.operation) {
   "back" -> service.goBack()
   "home" -> service.goHome()
   "tap_text" -> withPreviousAppFallback { service.tapText(request.arguments["text"].orEmpty()) }
   "type_text" -> withPreviousAppFallback { service.typeIntoFocused(request.arguments["text"].orEmpty()) }
   "scroll_forward" -> withPreviousAppFallback { service.scrollForward() }
   else -> false
  }
  return if(ok) ToolResult.Completed("Accessibility action completed") else when(request.operation) {
   "type_text" -> ToolResult.Unavailable("I couldn't find an editable text field. Focus a text box in the target app and try again.")
   "scroll_forward" -> ToolResult.Unavailable("I couldn't find a scrollable surface on the active screen.")
   else -> ToolResult.Failed("I couldn't complete that screen action",true)
  }
 }
}

fun nativeToolRegistry(context: Context): Map<ToolCategory, AndroidTool> = listOf(
 OpenAppTool(context), FlashlightTool(context), VolumeTool(context), BrightnessTool(context), AlarmTool(context), MediaTool(context), ClipboardTool(context), AccessibilityUiTool()
).associateBy { it.category }
