package dev.voidcore.agentbrain

import dev.voidcore.workflowengine.WorkflowDefinition
import dev.voidcore.providerrouter.ProviderRouter
/** Produces proposals only. UI/application coordinator submits approved plans separately. */
interface AgentPlanner { suspend fun plan(goal: String, scopeId: String): WorkflowDefinition }
interface BrainDependencies { val providers: ProviderRouter }
