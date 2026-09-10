package dev.voidcore.voiceengine

import kotlinx.serialization.Serializable

/** Pitch is semitones [-12,12], speed multiplier [0.5,2]; all other controls normalized [0,1]. */
@Serializable
data class VoiceProfile(val id: String, val name: String, val pitch: Float = 0f, val speakingSpeed: Float = 1f,
 val cadence: Float = 0.3f,
 val pauseLength: Float = 0.3f,
 val energy: Float = 0.3f,
 val warmth: Float = 0.3f,
 val metallicResonance: Float = 0.3f,
 val syntheticDepth: Float = 0.3f,
 val harmonicLayer: Float = 0.3f,
 val bassPresence: Float = 0.3f,
 val brightness: Float = 0.3f,
 val clarity: Float = 0.8f,
 val breathiness: Float = 0.3f,
 val reverb: Float = 0.3f,
 val stereoWidth: Float = 0.3f,
 val digitalTexture: Float = 0.3f,
 val glitchAmount: Float = 0.3f,
 val sentenceEndModulation: Float = 0.3f,
 val emotionIntensity: Float = 0.3f,
 val questionInflection: Float = 0.3f,
 val warningIntensity: Float = 0.3f,
 val confirmationEmphasis: Float = 0.3f) {
 init { require(id.isNotBlank()); require(name.isNotBlank()); require(pitch in -12f..12f); require(speakingSpeed in 0.5f..2f)
 require(listOf(cadence, pauseLength, energy, warmth, metallicResonance, syntheticDepth, harmonicLayer, bassPresence, brightness, clarity, breathiness, reverb, stereoWidth, digitalTexture, glitchAmount, sentenceEndModulation, emotionIntensity, questionInflection, warningIntensity, confirmationEmphasis).all { it in 0f..1f }) }
}
object VoicePresets {
 val all = listOf(
 VoiceProfile("neutral", "Neutral Core"),
 VoiceProfile("void", "Void", pitch = -5f, syntheticDepth = .8f, metallicResonance = .35f),
 VoiceProfile("architect", "Architect", pitch = -3f, speakingSpeed = .95f, cadence = .55f,
 clarity = .95f, syntheticDepth = .5f, metallicResonance = .45f),
 VoiceProfile("spectral", "Spectral", pitch = 2f, harmonicLayer = .65f, reverb = .4f),
 VoiceProfile("titan", "Titan", pitch = -7f, bassPresence = .85f, energy = .6f),
 // Original machine aesthetic; these are requested DSP values, not rendered voices.
 VoiceProfile("omega", "Omega", pitch = -9f, speakingSpeed = .83f, warmth = .08f,
 metallicResonance = .78f, syntheticDepth = .95f, bassPresence = .9f,
 breathiness = 0f, digitalTexture = .65f, emotionIntensity = .15f, clarity = .9f))
}
sealed interface VoiceCommand {
 data class Use(val name: String, val expiresAtEpochMillis: Long? = null) : VoiceCommand
 data class Adjust(val control: String, val delta: Float) : VoiceCommand
 data class SaveAs(val customName: String) : VoiceCommand
}
interface VoiceCommandInterpreter { suspend fun interpret(text: String, timeZoneId: String): VoiceCommand? }

/** Name lookup is separate from natural-language interpretation. Preserve arbitrary user names.
 * Future repositories must return all matches rather than silently selecting an ambiguous name. */
sealed interface VoiceProfileMatch {
 data class Found(val profile: VoiceProfile) : VoiceProfileMatch
 data class Ambiguous(val profiles: List<VoiceProfile>) : VoiceProfileMatch
 data object NotFound : VoiceProfileMatch
}
interface VoiceProfileCatalog {
 suspend fun findByName(name: String): VoiceProfileMatch
 suspend fun saveCustom(profile: VoiceProfile)
}
