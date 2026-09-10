package dev.voidcore.workflowengine

import dev.voidcore.androidtools.ToolRequest
import dev.voidcore.assistantstate.StateSynchronizedAdapter
import kotlinx.coroutines.flow.StateFlow

/** Per-task persistence status, not an alternate semantic assistant state. */
enum class StepStatus { PENDING, RUNNING, WAITING_FOR_CONFIRMATION, PAUSED, SUCCEEDED, FAILED, SKIPPED }
enum class WorkflowStatus { DRAFT, RUNNING, PAUSED, WAITING_FOR_USER, COMPLETED, PARTIALLY_COMPLETED, FAILED, CANCELLED }
data class RetryPolicy(val maximumAttempts: Int = 1, val backoffMillis: Long = 1000, val requiresIdempotency: Boolean = true)
data class WorkflowStep(val id: String, val order: Int, val dependencies: Set<String>, val request: ToolRequest?, val confirmationRequired: Boolean = false, val verification: String? = null, val retry: RetryPolicy = RetryPolicy())
data class WorkflowDefinition(val id: String, val name: String, val steps: List<WorkflowStep>)
data class StepCheckpoint(val stepId: String, val status: StepStatus, val attempts: Int, val verified: Boolean = false, val receipt: String? = null)
data class WorkflowCheckpoint(val workflowId: String, val revision: Long, val status: WorkflowStatus, val steps: List<StepCheckpoint>)
interface CheckpointStore { suspend fun save(checkpoint: WorkflowCheckpoint); suspend fun load(workflowId: String): WorkflowCheckpoint? }
interface StepVerifier { suspend fun verify(step: WorkflowStep, receipt: String): Boolean }
/** Future implementation submits to ValidatedExecutor; it never calls Android adapters directly. */
interface WorkflowEngine : StateSynchronizedAdapter {
 val active: StateFlow<WorkflowCheckpoint?>
 suspend fun start(definition: WorkflowDefinition)
 suspend fun pause(id: String)
 suspend fun resume(id: String)
 suspend fun cancel(id: String)
 suspend fun confirm(workflowId: String, stepId: String, approved: Boolean)
}
