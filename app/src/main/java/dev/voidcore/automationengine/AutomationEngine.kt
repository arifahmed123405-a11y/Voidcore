package dev.voidcore.automationengine

import kotlinx.serialization.Serializable
@Serializable data class AutomationDefinition(val id: String, val name: String, val workflowId: String, val trigger: String, val timeZoneId: String, val enabled: Boolean = false)
interface AutomationEngine { suspend fun register(definition: AutomationDefinition); suspend fun disable(id: String); suspend fun cancel(id: String) }
