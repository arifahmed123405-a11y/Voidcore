package dev.voidcore.visionengine

data class VisionInput(val localUri: String, val scopeId: String, val remoteConsent: Boolean = false)
data class ScreenUnderstanding(val summary: String, val confidence: Float, val protectedContent: Boolean)
interface VisionEngine { suspend fun understand(input: VisionInput): ScreenUnderstanding }
