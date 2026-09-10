package dev.voidcore.voiceengine

import kotlinx.serialization.Serializable
import dev.voidcore.assistantstate.StateSynchronizedAdapter
@Serializable data class SoundCue(val assetUri: String? = null, val gain: Float = .5f, val loop: Boolean = false)
@Serializable data class HapticPairing(val enabled: Boolean = false, val timingsMillis: List<Long> = emptyList(), val amplitudes: List<Int> = emptyList())
@Serializable data class AudioProfile(
 val id: String, val name: String,
 val wakeSound: SoundCue = SoundCue(), val listeningCue: SoundCue = SoundCue(),
 val thinkingAmbience: SoundCue = SoundCue(loop = true), val actionCue: SoundCue = SoundCue(),
 val confirmationCue: SoundCue = SoundCue(), val successTone: SoundCue = SoundCue(),
 val warningTone: SoundCue = SoundCue(), val errorTone: SoundCue = SoundCue(),
 val notificationSoundFamily: List<SoundCue> = emptyList(), val dismissalSound: SoundCue = SoundCue(),
 val hapticPairing: HapticPairing = HapticPairing()) {
 init { require(id.isNotBlank()); require(name.isNotBlank()) }
}
object AudioPresets { val all = listOf(AudioProfile("silent", "Silent Core"), AudioProfile("custom", "My Audio")) }
interface AudioFeedbackEngine : StateSynchronizedAdapter { suspend fun apply(profile: AudioProfile); suspend fun stop() }

/** Storage/catalog boundary only; no preset editor or playback is connected in Phase 0. */
interface AudioProfileCatalog {
 suspend fun findByName(name: String): List<AudioProfile>
 suspend fun saveCustom(profile: AudioProfile)
}
