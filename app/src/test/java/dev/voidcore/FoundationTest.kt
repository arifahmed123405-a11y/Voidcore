package dev.voidcore

import dev.voidcore.assistantstate.*
import dev.voidcore.androidtools.*
import dev.voidcore.securityengine.*
import dev.voidcore.voiceengine.*
import dev.voidcore.overlayservice.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class FoundationTest {
 @Test fun staleStateWritersCannotOverwriteNewerState() {
  val state = AssistantStateEngine()
  assertTrue(state.transition(0, AssistantState.INVOKING, "test"))
  assertFalse(state.transition(0, AssistantState.SPEAKING, "stale"))
  assertEquals(AssistantState.INVOKING, state.snapshot.value.state)
 }
 @Test fun diagnosticsAreExplicitlyPreviews() {
  val state = AssistantStateEngine()
  AssistantState.entries.forEach { state.preview(it); assertEquals(it, state.snapshot.value.state); assertTrue(state.snapshot.value.preview) }
 }
 @Test fun securityDenialNeverDispatchesAndIsAudited() = runTest {
  var called = false
  val audit = mutableListOf<AuditEvent>()
  val tool = object : AndroidTool {
   override val category = ToolCategory.CALLS
   override suspend fun execute(request: ToolRequest): ToolResult { called = true; return ToolResult.Completed("should never happen") }
  }
  val executor = GatedExecutor(AssistantStateEngine(), FoundationSecurityGate, object : ActionAuditLog { override suspend fun append(event: AuditEvent) { audit.add(event) } }, mapOf(ToolCategory.CALLS to tool))
  val result = executor.submit(ToolRequest("1", ToolCategory.CALLS, "call", emptyMap(), "scope"))
  assertTrue(result is ToolResult.Denied); assertFalse(called); assertEquals("denied", audit.single().outcome)
 }
 @Test fun auditFailurePreventsDispatch() = runTest {
  var called = false
  val tool = object : AndroidTool {
   override val category = ToolCategory.OPEN_APP
   override suspend fun execute(request: ToolRequest): ToolResult { called = true; return ToolResult.Completed("x") }
  }
  val executor = GatedExecutor(AssistantStateEngine(), object : SecurityGate { override suspend fun evaluate(request: ToolRequest) = Check(true, "test") }, object : ActionAuditLog { override suspend fun append(event: AuditEvent) { error("storage failure") } }, mapOf(tool.category to tool))
  try { executor.submit(ToolRequest("1", tool.category, "open", emptyMap(), "scope")); fail("Expected audit failure") } catch (_: IllegalStateException) { }
  assertFalse(called)
 }
 @Test fun customProfileNamesAndControlBounds() {
  assertEquals("Night Voice", VoicePresets.all.last().copy(name = "Night Voice").name)
  assertEquals(6, VoicePresets.all.map { it.id }.toSet().size)
  assertEquals(listOf("Neutral Core", "Void", "Architect", "Spectral", "Titan", "Omega"), VoicePresets.all.map { it.name })
  try { VoiceProfile("bad", "Bad", pitch = 20f); fail("Expected bounds validation") } catch (_: IllegalArgumentException) { }
 }

 @Test fun presenceUsesSameStateAndDoesNotOverwriteIt() = runTest {
  val state = AssistantStateEngine()
  val presence = PresencePreviewController(state)
  state.preview(AssistantState.LISTENING)
  val before = state.snapshot.value
  PresenceMode.entries.forEach { mode ->
   presence.requestLayout(PresenceLayout(mode))
   assertSame(state, presence.assistantState)
   assertEquals(mode, presence.layout.value.mode)
   assertEquals(before, state.snapshot.value)
  }
 }
 @Test fun callerMutationCannotChangeValidatedArguments() = runTest {
  val args = mutableMapOf("target" to "original")
  var executedTarget: String? = null
  val gate = object : SecurityGate {
   override suspend fun evaluate(request: ToolRequest): Check {
    assertEquals("original", request.arguments["target"])
    args["target"] = "different"
    return Check(true, "test only")
   }
  }
  val tool = object : AndroidTool {
   override val category = ToolCategory.OPEN_APP
   override suspend fun execute(request: ToolRequest): ToolResult {
    executedTarget = request.arguments["target"]
    return ToolResult.Completed("test receipt")
   }
  }
  val executor = GatedExecutor(AssistantStateEngine(), gate,
   object : ActionAuditLog { override suspend fun append(event: AuditEvent) {} }, mapOf(tool.category to tool))
  executor.submit(ToolRequest("1", tool.category, "open", args, "scope"))
  assertEquals("original", executedTarget)
 }
 @Test fun policyScopeDenialShortCircuitsLaterChecks() = runTest {
  val checks = mutableListOf<String>()
  val gate = PolicySecurityGate(
   object : PermissionChecker { override suspend fun check(request: ToolRequest): Check { checks.add("permission"); return Check(true, "test") } },
   object : TaskScopeValidator { override suspend fun validate(request: ToolRequest): Check { checks.add("scope"); return Check(false, "outside scope") } },
   object : RiskClassifier { override suspend fun classify(request: ToolRequest): Risk { error("Must not reach risk") } },
   object : ConfirmationPolicy { override suspend fun check(request: ToolRequest, risk: Risk): Check { error("Must not reach confirmation") } },
   object : SecureAppRestrictions { override suspend fun check(request: ToolRequest): Check { error("Must not reach secure app") } },
   object : ProtectedActionChecker { override suspend fun check(request: ToolRequest): Check { error("Must not reach protected action") } }
  )
  val result = gate.evaluate(ToolRequest("1", ToolCategory.CALLS, "call", emptyMap(), "scope"))
  assertFalse(result.allowed)
  assertEquals(listOf("permission", "scope"), checks)
 }
}
