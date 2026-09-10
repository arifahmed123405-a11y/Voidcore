package dev.voidcore

import dev.voidcore.agentbrain.LocalActionParser
import dev.voidcore.androidtools.ToolCategory
import dev.voidcore.androidtools.ToolRequest
import dev.voidcore.securityengine.Phase3SecurityGate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class Phase3ActionTest {
 @Test fun parsesCoreNativeCommands() {
  assertEquals(ToolCategory.OPEN_APP, LocalActionParser.parse("Open Spotify")?.category)
  assertEquals("true", LocalActionParser.parse("turn flashlight on")?.arguments?.get("enabled"))
  assertEquals("600", LocalActionParser.parse("set a timer for 10 minutes")?.arguments?.get("seconds"))
  assertEquals("75", LocalActionParser.parse("set volume to 75%")?.arguments?.get("percent"))
  assertEquals("tap_text", LocalActionParser.parse("tap Continue")?.operation)
  assertEquals("play", LocalActionParser.parse("play media")?.operation)
  assertEquals("pause", LocalActionParser.parse("pause the music")?.operation)
  assertEquals("next", LocalActionParser.parse("skip track")?.operation)
  assertEquals("scroll_forward", LocalActionParser.parse("swipe up")?.operation)
  assertEquals("type_text", LocalActionParser.parse("write hello world")?.operation)
 }
 @Test fun phase3GateAllowsOnlyScopedLowRiskTools() = runTest {
  assertTrue(Phase3SecurityGate.evaluate(ToolRequest("1",ToolCategory.OPEN_APP,"open",mapOf("app" to "Spotify"),"interactive-command")).allowed)
  assertFalse(Phase3SecurityGate.evaluate(ToolRequest("2",ToolCategory.MESSAGES,"send",emptyMap(),"interactive-command")).allowed)
  assertFalse(Phase3SecurityGate.evaluate(ToolRequest("3",ToolCategory.OPEN_APP,"open",emptyMap(),"background-agent")).allowed)
 }
}
