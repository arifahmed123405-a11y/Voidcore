package dev.voidcore.securityengine

import dev.voidcore.androidtools.*

/** Phase 3 allows only low-risk local device actions. Sensitive categories remain denied. */
object Phase3SecurityGate : SecurityGate {
 private val allowed = setOf(
  ToolCategory.OPEN_APP,
  ToolCategory.ALARMS,
  ToolCategory.MEDIA_CONTROLS,
  ToolCategory.BRIGHTNESS,
  ToolCategory.VOLUME,
  ToolCategory.FLASHLIGHT,
  ToolCategory.CLIPBOARD,
  ToolCategory.ACCESSIBILITY_UI_ACTIONS
 )
 override suspend fun evaluate(request: ToolRequest): Check {
  if(request.taskScopeId != "interactive-command") return Check(false,"Untrusted task scope")
  if(request.category !in allowed) return Check(false,"This action still requires a later trust/confirmation phase")
  if(request.category == ToolCategory.ACCESSIBILITY_UI_ACTIONS && request.operation !in setOf("back","home","tap_text","type_text","scroll_forward"))
   return Check(false,"Unsupported accessibility action")
  return Check(true,"Phase 3 low-risk interactive action")
 }
}
