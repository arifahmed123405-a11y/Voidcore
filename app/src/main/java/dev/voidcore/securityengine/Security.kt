package dev.voidcore.securityengine

import dev.voidcore.androidtools.*
import dev.voidcore.assistantstate.AssistantStateSource
import java.util.Collections
import kotlinx.coroutines.CancellationException

enum class Risk { LOW, MODERATE, HIGH, PROTECTED }
data class Check(val allowed: Boolean, val reason: String)
interface PermissionChecker { suspend fun check(request: ToolRequest): Check }
interface TaskScopeValidator { suspend fun validate(request: ToolRequest): Check }
interface RiskClassifier { suspend fun classify(request: ToolRequest): Risk }
interface ConfirmationPolicy { suspend fun check(request: ToolRequest, risk: Risk): Check }
interface SecureAppRestrictions { suspend fun check(request: ToolRequest): Check }
interface ProtectedActionChecker { suspend fun check(request: ToolRequest): Check }
data class AuditEvent(val requestId: String, val category: ToolCategory, val outcome: String)
interface ActionAuditLog { suspend fun append(event: AuditEvent) }
interface SecurityGate { suspend fun evaluate(request: ToolRequest): Check }
class PolicySecurityGate(
 private val permissions: PermissionChecker, private val scopes: TaskScopeValidator,
 private val risks: RiskClassifier, private val confirmations: ConfirmationPolicy,
 private val secureApps: SecureAppRestrictions, private val protectedActions: ProtectedActionChecker
) : SecurityGate {
 override suspend fun evaluate(request: ToolRequest): Check {
  for (check in listOf<suspend () -> Check>(
   { permissions.check(request) }, { scopes.validate(request) },
   { secureApps.check(request) }, { protectedActions.check(request) },
   { confirmations.check(request, risks.classify(request)) }
  )) { val result = check(); if (!result.allowed) return result }
  return Check(true, "Validated")
 }
}
object FoundationSecurityGate : SecurityGate {
 override suspend fun evaluate(request: ToolRequest) = Check(false, "Android execution is disabled until Phase 3")
}
/** Audit write must succeed before dispatch. No action implementations are registered in Phase 2.
 * Production adapters must recheck volatile permissions and target identity at the OS boundary. */
class GatedExecutor(
 override val assistantState: AssistantStateSource,
 private val gate: SecurityGate, private val audit: ActionAuditLog,
 tools: Map<ToolCategory, AndroidTool> = emptyMap()
) : ValidatedExecutor {
 // Snapshot the registry so callers cannot change adapters after composition.
 private val registeredTools = tools.toMap()
 override suspend fun submit(request: ToolRequest): ToolResult {
  // Map is a read-only interface, not an immutability guarantee. Freeze caller-owned arguments
  // before suspending so validation and execution see exactly the same request.
  val frozen = request.copy(arguments = Collections.unmodifiableMap(HashMap(request.arguments)))
  val check = gate.evaluate(frozen)
  audit.append(AuditEvent(frozen.id, frozen.category, if (check.allowed) "authorized" else "denied"))
  if (!check.allowed) return ToolResult.Denied(check.reason)
  val tool = registeredTools[frozen.category]
  if (tool == null) {
   audit.append(AuditEvent(frozen.id, frozen.category, "unavailable"))
   return ToolResult.Unavailable("Adapter not implemented")
  }
  val result = try {
   tool.execute(frozen)
  } catch (cancelled: CancellationException) {
   // Cancellation must propagate; never disguise uncertain execution as safe to retry.
   throw cancelled
  } catch (e: Exception) {
   audit.append(AuditEvent(frozen.id, frozen.category, "failed:${e::class.simpleName}"))
   return ToolResult.Failed(e.message?.takeIf { it.isNotBlank() } ?: "${e::class.simpleName} while executing ${frozen.category}", retryable = false)
  }
  audit.append(AuditEvent(frozen.id, frozen.category, result::class.simpleName ?: "result"))
  return result
 }
}
