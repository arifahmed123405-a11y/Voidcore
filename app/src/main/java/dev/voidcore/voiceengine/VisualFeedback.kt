package dev.voidcore.voiceengine

import dev.voidcore.assistantstate.StateSynchronizedAdapter
import kotlinx.coroutines.flow.SharedFlow

enum class VisualCue { WAKE, LISTENING, ACTION, CONFIRMATION, SUCCESS, WARNING, DISMISSAL }
enum class HapticIntent { SOFT_TICK, FIRM_TICK, DOUBLE_TICK, SETTLE }
data class VisualFeedbackEvent(val cue: VisualCue, val stateRevision: Long, val haptic: HapticIntent)
/** Animation/state milestone hooks only. No speaker or vibrator adapter is installed. */
interface VisualFeedbackSource : StateSynchronizedAdapter { val feedbackEvents: SharedFlow<VisualFeedbackEvent> }
interface VisualFeedbackSink { suspend fun accept(event: VisualFeedbackEvent) }
