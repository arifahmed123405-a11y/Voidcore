package dev.voidcore.androidtools

import dev.voidcore.assistantstate.StateSynchronizedAdapter

enum class ToolCategory { OPEN_APP, CONTACTS, CALLS, MESSAGES, ALARMS, CALENDAR, MEDIA_CONTROLS, BRIGHTNESS, VOLUME, FLASHLIGHT, CLIPBOARD, FILES, NOTIFICATIONS, INTENTS_DEEP_LINKS, SHARE_ACTIONS, ACCESSIBILITY_UI_ACTIONS, SCREEN_UNDERSTANDING }
data class ToolRequest(val id: String, val category: ToolCategory, val operation: String, val arguments: Map<String, String>, val taskScopeId: String)
sealed interface ToolResult { data class Completed(val receipt: String) : ToolResult; data class Denied(val reason: String) : ToolResult; data class Unavailable(val reason: String) : ToolResult; data class Failed(val reason: String, val retryable: Boolean) : ToolResult }
/** The planner receives no AndroidTool or executor reference. */
interface AndroidTool { val category: ToolCategory; suspend fun execute(request: ToolRequest): ToolResult }
interface ValidatedExecutor : StateSynchronizedAdapter { suspend fun submit(request: ToolRequest): ToolResult }
