package dev.voidcore.agentbrain

enum class TaskKind { CONVERSATION, SIMPLE_LOCAL_COMMAND, REASONING, REWRITE, VISION_NEEDED, UNSUPPORTED, VOICE_PROFILE }
data class TaskClassification(val kind: TaskKind, val confidence: Float, val reason: String)

object LocalTaskRouter {
 private val localPatterns = listOf(
  Regex("^(open|launch|start)\\s+.+", RegexOption.IGNORE_CASE),
  Regex("^(turn|switch)\\s+(on|off)\\s+.+", RegexOption.IGNORE_CASE),
  Regex("^(set|create)\\s+(an?\\s+)?(alarm|timer)\\b.+", RegexOption.IGNORE_CASE),
  Regex("^(increase|decrease|lower|raise|mute|unmute)\\s+.+", RegexOption.IGNORE_CASE)
 )
 fun classify(text: String): TaskClassification {
  val t = text.trim()
  if (t.isBlank()) return TaskClassification(TaskKind.UNSUPPORTED, 1f, "empty input")
  if (Regex("^(use|switch to|change (your )?voice to|make your voice|speak slower|speak faster|save this as)\\b", RegexOption.IGNORE_CASE).containsMatchIn(t))
   return TaskClassification(TaskKind.VOICE_PROFILE, .96f, "voice profile command")
  if (LocalActionParser.parse(t) != null) return TaskClassification(TaskKind.SIMPLE_LOCAL_COMMAND, .99f, "deterministic local action parser match")
  if (localPatterns.any { it.containsMatchIn(t) }) return TaskClassification(TaskKind.SIMPLE_LOCAL_COMMAND, .94f, "obvious device-action phrasing")
  if (Regex("\\b(this screen|this image|photo|screenshot|what do you see|look at this)\\b", RegexOption.IGNORE_CASE).containsMatchIn(t))
   return TaskClassification(TaskKind.VISION_NEEDED, .88f, "visual context requested")
  if (Regex("^(rewrite|rephrase|shorten|summarize|translate|make this)\\b", RegexOption.IGNORE_CASE).containsMatchIn(t))
   return TaskClassification(TaskKind.REWRITE, .9f, "text transformation")
  if (Regex("\\b(why|compare|plan|reason|explain|analyze|which is better)\\b", RegexOption.IGNORE_CASE).containsMatchIn(t))
   return TaskClassification(TaskKind.REASONING, .78f, "reasoning cues")
  return TaskClassification(TaskKind.CONVERSATION, .72f, "general conversation")
 }
}
