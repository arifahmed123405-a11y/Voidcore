package dev.voidcore.voiceengine

import dev.voidcore.assistantstate.StateSynchronizedAdapter
import kotlinx.coroutines.flow.Flow

enum class AudioRoute { SPEAKER, EARPIECE, WIRED_HEADSET, BLUETOOTH }
data class VoiceCapabilities(val wakeWord: Boolean, val vad: Boolean, val streamingStt: Boolean, val tts: Boolean, val bargeIn: Boolean, val screenOff: Boolean, val routes: Set<AudioRoute>, val supportedProfileControls: Set<String>)
sealed interface VoiceEvent {
 data class WakeWord(val phrase: String) : VoiceEvent
 data class Activity(val speechDetected: Boolean, val level: Float) : VoiceEvent
 data class Transcript(val text: String, val final: Boolean) : VoiceEvent
 data object SpeechStarted : VoiceEvent
 data object SpeechEnded : VoiceEvent
 data object Interrupted : VoiceEvent
 data class Unavailable(val reason: String) : VoiceEvent
}
interface VoiceEngine : StateSynchronizedAdapter {
 val events: Flow<VoiceEvent>
 val capabilities: VoiceCapabilities
 suspend fun startWakeWord()
 suspend fun startListening()
 suspend fun speak(text: String, profile: VoiceProfile)
 suspend fun interrupt()
 suspend fun setRoute(route: AudioRoute)
 suspend fun stop()
}
