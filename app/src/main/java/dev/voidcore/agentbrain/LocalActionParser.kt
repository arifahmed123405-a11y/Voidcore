package dev.voidcore.agentbrain

import dev.voidcore.androidtools.*
import java.util.UUID

object LocalActionParser {
 fun parse(text: String): ToolRequest? {
  val t=text.trim(); val l=t.lowercase()
  fun req(cat: ToolCategory, op: String, args: Map<String,String> = emptyMap()) = ToolRequest(UUID.randomUUID().toString(),cat,op,args,"interactive-command")

  Regex("^(?:open|launch|start)\\s+(.+)$",RegexOption.IGNORE_CASE).find(t)?.let { match ->
   val app=match.groupValues[1].trim()
   if(!app.startsWith("timer",true) && !app.startsWith("alarm",true)) return req(ToolCategory.OPEN_APP,"open",mapOf("app" to app))
  }

  if(Regex("\\bflash(?:light)?\\b").containsMatchIn(l)) {
   if("on" in l) return req(ToolCategory.FLASHLIGHT,"set",mapOf("enabled" to "true"))
   if("off" in l) return req(ToolCategory.FLASHLIGHT,"set",mapOf("enabled" to "false"))
  }

  if("volume" in l || l.startsWith("mute") || l.startsWith("unmute")) {
   Regex("(\\d{1,3})\\s*%?").find(l)?.groupValues?.get(1)?.let { return req(ToolCategory.VOLUME,"set",mapOf("percent" to it)) }
   return when {
    "mute" in l && "unmute" !in l -> req(ToolCategory.VOLUME,"mute")
    "unmute" in l -> req(ToolCategory.VOLUME,"unmute")
    listOf("increase","raise","up","louder","higher").any { it in l } -> req(ToolCategory.VOLUME,"raise")
    listOf("decrease","lower","down","quieter","softer").any { it in l } -> req(ToolCategory.VOLUME,"lower")
    else -> null
   }
  }

  if("brightness" in l || "brighter" in l || "dimmer" in l || "screen brighter" in l || "screen darker" in l) {
   Regex("(\\d{1,3})\\s*%?").find(l)?.groupValues?.get(1)?.let { return req(ToolCategory.BRIGHTNESS,"set",mapOf("percent" to it)) }
   return when {
    listOf("increase","raise","brighter","up","higher").any { it in l } -> req(ToolCategory.BRIGHTNESS,"raise")
    else -> req(ToolCategory.BRIGHTNESS,"lower")
   }
  }

  Regex("(?:set|start)?\\s*(?:a\\s+)?timer\\s+(?:for\\s+)?(\\d+)\\s*(second|minute|hour)s?",RegexOption.IGNORE_CASE).find(t)?.let {
   val n=it.groupValues[1].toInt(); val seconds=when(it.groupValues[2].lowercase()){ "hour"->n*3600; "minute"->n*60; else->n }
   return req(ToolCategory.ALARMS,"timer",mapOf("seconds" to seconds.toString()))
  }

  Regex("(?:set|create)?\\s*(?:an?\\s+)?alarm\\s+(?:for|at)?\\s*(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?",RegexOption.IGNORE_CASE).find(t)?.let {
   var hour=it.groupValues[1].toInt().coerceIn(0,23); val minute=it.groupValues[2].toIntOrNull()?.coerceIn(0,59) ?: 0
   when(it.groupValues[3].lowercase()) { "pm" -> if(hour<12) hour+=12; "am" -> if(hour==12) hour=0 }
   return req(ToolCategory.ALARMS,"alarm",mapOf("hour" to hour.toString(),"minute" to minute.toString()))
  }

  when {
   Regex("^(play|resume)(?:\\s+(?:the\\s+)?)?(music|media|audio|playback)?[.!]?$",RegexOption.IGNORE_CASE).matches(t) -> return req(ToolCategory.MEDIA_CONTROLS,"play")
   Regex("^(pause|stop)(?:\\s+(?:the\\s+)?)?(music|media|audio|playback)?[.!]?$",RegexOption.IGNORE_CASE).matches(t) -> return req(ToolCategory.MEDIA_CONTROLS,"pause")
   Regex("^(play pause|toggle playback|toggle media)[.!]?$",RegexOption.IGNORE_CASE).matches(t) -> return req(ToolCategory.MEDIA_CONTROLS,"play_pause")
   Regex("^(next|skip)(?:\\s+(song|track|media))?[.!]?$",RegexOption.IGNORE_CASE).matches(t) -> return req(ToolCategory.MEDIA_CONTROLS,"next")
   Regex("^(previous|back)(?:\\s+(song|track))?[.!]?$",RegexOption.IGNORE_CASE).matches(t) -> return req(ToolCategory.MEDIA_CONTROLS,"previous")
   l.startsWith("copy ") && l.endsWith(" to clipboard") -> return req(ToolCategory.CLIPBOARD,"copy",mapOf("text" to t.substring(5,t.length-13).trim()))
   l == "go back" || l == "back" -> return req(ToolCategory.ACCESSIBILITY_UI_ACTIONS,"back")
   l == "go home" -> return req(ToolCategory.ACCESSIBILITY_UI_ACTIONS,"home")
  }

  Regex("^(?:tap|click|press) (.+)$",RegexOption.IGNORE_CASE).find(t)?.let { return req(ToolCategory.ACCESSIBILITY_UI_ACTIONS,"tap_text",mapOf("text" to it.groupValues[1])) }
  Regex("^(?:type|enter|write) (.+)$",RegexOption.IGNORE_CASE).find(t)?.let { return req(ToolCategory.ACCESSIBILITY_UI_ACTIONS,"type_text",mapOf("text" to it.groupValues[1])) }
  if(l in setOf("scroll down","scroll","swipe up","move down")) return req(ToolCategory.ACCESSIBILITY_UI_ACTIONS,"scroll_forward")
  return null
 }
}
