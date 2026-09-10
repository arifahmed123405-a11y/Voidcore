package dev.voidcore.agentbrain

import dev.voidcore.androidtools.ToolCategory
import dev.voidcore.androidtools.ToolRequest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

/** Parses ONLY a tiny, whitelisted JSON schema produced by the local model.
 * Remote providers never feed this parser and never execute Android actions. */
object LocalModelActionPlanner {
 private val allowed = mapOf(
  "open_app" to Pair(ToolCategory.OPEN_APP, setOf("open")),
  "flashlight" to Pair(ToolCategory.FLASHLIGHT, setOf("set")),
  "volume" to Pair(ToolCategory.VOLUME, setOf("raise", "lower", "mute", "unmute", "set")),
  "brightness" to Pair(ToolCategory.BRIGHTNESS, setOf("raise", "lower", "set")),
  "alarm" to Pair(ToolCategory.ALARMS, setOf("alarm", "timer")),
  "media" to Pair(ToolCategory.MEDIA_CONTROLS, setOf("play", "pause", "play_pause", "next", "previous")),
  "clipboard" to Pair(ToolCategory.CLIPBOARD, setOf("copy")),
  "accessibility" to Pair(ToolCategory.ACCESSIBILITY_UI_ACTIONS, setOf("back", "home", "tap_text", "type_text", "scroll_forward"))
 )

 fun prompt(userText: String): String = """
You are the LOCAL Android intent parser for Void Core. Do not answer conversationally.
Return exactly one compact JSON object and nothing else.
If the request is not a supported Android action, return {"action":"none"}.
Supported actions:
- open_app/open arguments: app
- flashlight/set arguments: enabled=true|false
- volume/raise|lower|mute|unmute|set arguments for set: percent
- brightness/raise|lower|set arguments for set: percent
- alarm/timer arguments: seconds
- alarm/alarm arguments: hour(0-23), minute(0-59)
- media/play|pause|play_pause|next|previous
- clipboard/copy arguments: text
- accessibility/back|home|tap_text|type_text|scroll_forward arguments for tap_text/type_text: text
Schema: {"action":"open_app","operation":"open","arguments":{"app":"Spotify"}}
Never invent an action outside this list. Never say an action succeeded.
User request: ${userText.trim()}
""".trimIndent()

 fun parse(raw: String): ToolRequest? {
  val jsonText = raw.substringAfter('{', "").let { if (it.isBlank()) return null else "{$it" }.substringBeforeLast('}', "").let { if (it.isBlank()) return null else "$it}" }
  val root = runCatching { Json.parseToJsonElement(jsonText).jsonObject }.getOrNull() ?: return null
  val action = root["action"]?.jsonPrimitive?.content?.lowercase() ?: return null
  if (action == "none") return null
  val spec = allowed[action] ?: return null
  val operation = root["operation"]?.jsonPrimitive?.content?.lowercase() ?: return null
  if (operation !in spec.second) return null
  val args = root["arguments"]?.jsonObject?.mapValues { (_, v) -> v.jsonPrimitive.content }.orEmpty()
  return ToolRequest(UUID.randomUUID().toString(), spec.first, operation, args, "interactive-command")
 }
}
