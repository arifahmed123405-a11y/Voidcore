package dev.voidcore.accessibilityengine

import dev.voidcore.androidtools.ToolRequest
interface AccessibilityEngine { suspend fun isUserEnabled(): Boolean; suspend fun describeAvailability(): String }
/** Requests still pass through ValidatedExecutor. No service is declared. */
interface AccessibilityRequestFactory { fun action(scopeId: String, targetNodeId: String, action: String): ToolRequest }
