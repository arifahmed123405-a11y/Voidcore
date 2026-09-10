package dev.voidcore.voiceengine

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dev.voidcore.assistantstate.AssistantStateEngine
import dev.voidcore.assistantstate.AssistantStateSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID
import kotlin.math.pow

class AndroidVoiceEngine(
 private val context: Context,
 private val stateEngine: AssistantStateEngine
) : VoiceEngine, RecognitionListener, TextToSpeech.OnInitListener {
 override val assistantState: AssistantStateSource = stateEngine
 private val mutableEvents = MutableSharedFlow<VoiceEvent>(extraBufferCapacity = 64)
 override val events = mutableEvents.asSharedFlow()
 override val capabilities = VoiceCapabilities(
  wakeWord = false,
  vad = true,
  streamingStt = true,
  tts = true,
  bargeIn = true,
  screenOff = false,
  routes = setOf(AudioRoute.SPEAKER, AudioRoute.EARPIECE, AudioRoute.WIRED_HEADSET, AudioRoute.BLUETOOTH),
  supportedProfileControls = setOf("pitch", "speakingSpeed")
 )

 private var recognizer: SpeechRecognizer? = null
 private var tts: TextToSpeech? = null
 private var ttsReady = CompletableDeferred<Boolean>()
 private var speakingId: String? = null
 private var listening = false

 init { tts = TextToSpeech(context.applicationContext, this) }

 override fun onInit(status: Int) {
  val ok = status == TextToSpeech.SUCCESS
  if (ok) {
   tts?.language = Locale.getDefault()
   tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
    override fun onStart(utteranceId: String?) {
     if (utteranceId == speakingId) {
      stateEngine.runtime(dev.voidcore.assistantstate.AssistantState.SPEAKING, "tts started")
      mutableEvents.tryEmit(VoiceEvent.SpeechStarted)
     }
    }
    override fun onDone(utteranceId: String?) {
     if (utteranceId == speakingId) {
      speakingId = null
      stateEngine.runtime(dev.voidcore.assistantstate.AssistantState.SUCCESS, "tts completed")
      mutableEvents.tryEmit(VoiceEvent.SpeechEnded)
     }
    }
    @Deprecated("Deprecated in Java")
    override fun onError(utteranceId: String?) {
     if (utteranceId == speakingId) {
      speakingId = null
      mutableEvents.tryEmit(VoiceEvent.Unavailable("TTS playback failed"))
     }
    }
    override fun onError(utteranceId: String?, errorCode: Int) = onError(utteranceId)
   })
  }
  if (!ttsReady.isCompleted) ttsReady.complete(ok)
 }

 override suspend fun startWakeWord() {
  mutableEvents.emit(VoiceEvent.Unavailable("Always-on wake word is not enabled in Phase 2"))
 }

 override suspend fun startListening() = withContext(Dispatchers.Main.immediate) {
  interrupt()
  if (!SpeechRecognizer.isRecognitionAvailable(context)) {
   mutableEvents.emit(VoiceEvent.Unavailable("Android speech recognition is unavailable on this device"))
   return@withContext
  }
  // Android's recognizer service can remain busy for a short moment after a previous session.
  // Release the old client and give the service a small hand-off window before starting again.
  if (listening) {
   recognizer?.cancel()
   listening = false
  }
  recognizer?.destroy()
  recognizer = null
  delay(220)
  recognizer = try {
   if (Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
    SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
   } else SpeechRecognizer.createSpeechRecognizer(context)
  } catch (_: Throwable) { SpeechRecognizer.createSpeechRecognizer(context) }
  recognizer?.setRecognitionListener(this@AndroidVoiceEngine)
  val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
   putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
   putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
   putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
   putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
   putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 900L)
   putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 650L)
  }
  listening = true
  recognizer?.startListening(intent)
 }

 override suspend fun speak(text: String, profile: VoiceProfile) = withContext(Dispatchers.Main.immediate) {
  if (text.isBlank()) return@withContext
  val ready = if (ttsReady.isCompleted) runCatching { ttsReady.await() }.getOrDefault(false) else ttsReady.await()
  if (!ready) {
   mutableEvents.emit(VoiceEvent.Unavailable("Android TTS could not initialize"))
   return@withContext
  }
  val rate = profile.speakingSpeed.coerceIn(.5f, 2f)
  val pitchRatio = 2.0.pow(profile.pitch.toDouble() / 12.0).toFloat().coerceIn(.5f, 2f)
  tts?.setSpeechRate(rate)
  tts?.setPitch(pitchRatio)
  val id = UUID.randomUUID().toString()
  speakingId = id
  val result = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), id)
  if (result == TextToSpeech.ERROR) {
   speakingId = null
   mutableEvents.emit(VoiceEvent.Unavailable("Android TTS rejected the utterance"))
  }
 }

 override suspend fun interrupt() = withContext(Dispatchers.Main.immediate) {
  tts?.stop()
  if (speakingId != null) {
   speakingId = null
   mutableEvents.emit(VoiceEvent.Interrupted)
  }
 }

 override suspend fun setRoute(route: AudioRoute) {
  // Android's active route is respected automatically by SpeechRecognizer/TTS. Explicit route forcing
  // is intentionally deferred because it requires audio-focus/device APIs that vary by Android version.
 }

 override suspend fun stop(): Unit = withContext(Dispatchers.Main.immediate) {
  listening = false
  recognizer?.cancel()
  recognizer?.destroy()
  recognizer = null
  tts?.stop()
  Unit
 }

 fun release() {
  recognizer?.destroy(); recognizer = null
  tts?.shutdown(); tts = null
 }

 override fun onReadyForSpeech(params: Bundle?) { mutableEvents.tryEmit(VoiceEvent.Activity(false, 0f)) }
 override fun onBeginningOfSpeech() { mutableEvents.tryEmit(VoiceEvent.Activity(true, .12f)) }
 override fun onRmsChanged(rmsdB: Float) {
  val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
  mutableEvents.tryEmit(VoiceEvent.Activity(true, normalized))
 }
 override fun onBufferReceived(buffer: ByteArray?) = Unit
 override fun onEndOfSpeech() { listening = false; mutableEvents.tryEmit(VoiceEvent.Activity(false, 0f)) }
 override fun onError(error: Int) {
  listening = false
  val message = when(error) {
   SpeechRecognizer.ERROR_AUDIO -> "Audio capture error"
   SpeechRecognizer.ERROR_CLIENT -> "Speech recognizer client error"
   SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is missing"
   SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech recognition network error"
   SpeechRecognizer.ERROR_NO_MATCH -> "I couldn't make out the speech"
   SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer is busy"
   SpeechRecognizer.ERROR_SERVER -> "Speech recognition service error"
   SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
   else -> "Speech recognition error $error"
  }
  mutableEvents.tryEmit(VoiceEvent.Unavailable(message))
 }
 override fun onResults(results: Bundle?) {
  listening = false
  val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
  mutableEvents.tryEmit(VoiceEvent.Transcript(text, true))
 }
 override fun onPartialResults(partialResults: Bundle?) {
  val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
  if (text.isNotBlank()) mutableEvents.tryEmit(VoiceEvent.Transcript(text, false))
 }
 override fun onEvent(eventType: Int, params: Bundle?) = Unit
}
