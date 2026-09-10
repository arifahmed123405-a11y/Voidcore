package dev.voidcore

import dev.voidcore.agentbrain.LocalTaskRouter
import dev.voidcore.agentbrain.TaskKind
import dev.voidcore.voiceengine.VoicePresets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase2Test {
 @Test fun localDeviceCommandsSkipAiClassification() {
  assertEquals(TaskKind.SIMPLE_LOCAL_COMMAND, LocalTaskRouter.classify("Open Spotify").kind)
  assertEquals(TaskKind.SIMPLE_LOCAL_COMMAND, LocalTaskRouter.classify("Set an alarm for 5").kind)
 }
 @Test fun voiceCommandsStayLocal() {
  assertEquals(TaskKind.VOICE_PROFILE, LocalTaskRouter.classify("Use Omega").kind)
  assertTrue(VoicePresets.all.any { it.name == "Omega" })
 }
 @Test fun normalQuestionUsesConversationPath() {
  assertEquals(TaskKind.CONVERSATION, LocalTaskRouter.classify("What is RAM?").kind)
 }
}
