package com.example.core.intent.handlers

import com.example.core.context.ContextSnapshot
import com.example.core.intelligence.ContextReasoningEngine
import com.example.core.intelligence.ContextRequirementsRegistry
import com.example.core.intelligence.SituationSnapshotBuilder
import com.example.core.intent.ExecutionResult
import com.example.rendering.visual_state.CoreVisualState
import java.util.Locale

class ContextActionHandler(
  private val getContextSnapshot: () -> ContextSnapshot,
  private val situationSnapshotBuilder: SituationSnapshotBuilder? = null,
  private val contextReasoningEngine: ContextReasoningEngine? = null
) {

  fun handle(): ExecutionResult {
    val snapshot = getContextSnapshot()
    val isArabic = Locale.getDefault().language.startsWith("ar")
    val text = if (isArabic) {
      "سياق الهاتف الحالي: ${snapshot.state.displayName} (${(snapshot.confidence * 100).toInt()}% ثقة)."
    } else {
      "Phone context is ${snapshot.state.displayName}: ${snapshot.state.description} (${(snapshot.confidence * 100).toInt()}% confidence)."
    }
    return ExecutionResult.Success(
      speech = text,
      actionTaken = "Queried Context (${snapshot.state.name})",
      visualState = CoreVisualState.RESPONDING
    )
  }

  suspend fun handleContextualQuery(rawQuery: String): ExecutionResult {
    val requirements = ContextRequirementsRegistry.resolve(rawQuery)
    val builder = situationSnapshotBuilder
    val reasoner = contextReasoningEngine

    if (builder == null || reasoner == null) {
      // Graceful fallback to basic context handling
      return handle()
    }

    // 1. Collect only required contextual telemetry
    val situation = builder.captureSnapshot(requirements)

    // 2. Synthesize situation, connect facts, make inferences and recommendations
    val synthesis = reasoner.synthesize(requirements, situation, rawQuery)

    return ExecutionResult.Success(
      speech = synthesis.synthesizedSpeech,
      actionTaken = "Contextual Intelligence [${synthesis.primaryDecision}]",
      visualState = CoreVisualState.RESPONDING,
      primaryDecision = synthesis.primaryDecision,
      nextStepLabel = synthesis.nextStep?.label
    )
  }

  suspend fun handleHypotheticalBatteryMusic(): ExecutionResult {
    val builder = situationSnapshotBuilder
    val reasoner = contextReasoningEngine
    val isArabic = Locale.getDefault().language.startsWith("ar")

    if (builder == null || reasoner == null) {
      val fallback = if (isArabic) {
        "تشغيل الموسيقى يستهلك عادة حوالي 4% إلى 8% من البطارية في الساعة كتقدير إحصائي عام (وليس قياساً خاصاً بهاتفك)، ويعتمد على مستوى الصوت، السماعة، والبث. لم أقم بتشغيل الموسيقى، قل 'شغل الموسيقى الآن' إذا أردت البدء."
      } else {
        "Music playback consumes approximately 4% to 8% battery per hour as a general statistical estimate (not a device-specific measurement), depending on volume, output device, and streaming. I have not started playback; say 'play music now' to begin."
      }
      return ExecutionResult.Success(
        speech = fallback,
        actionTaken = "Hypothetical Music Battery Impact (Fallback)",
        visualState = CoreVisualState.RESPONDING,
        proactiveSuggestions = listOf(
          com.example.core.intelligence.conversation.ActionableOption(if (isArabic) "شغّل الموسيقى الآن" else "Play music now", "شغل الموسيقى")
        )
      )
    }

    val req = com.example.core.intelligence.ContextRequirements(
      domain = com.example.core.intelligence.IntentDomain.BATTERY_SUFICIENCY,
      required = setOf(com.example.core.intelligence.ContextField.BATTERY)
    )
    val snapshot = builder.captureSnapshot(req)
    val synthesis = reasoner.reasonHypotheticalBatteryMusic(snapshot, isArabic)

    val suggestions = mutableListOf<com.example.core.intelligence.conversation.ActionableOption>()
    synthesis.nextStep?.let { step ->
      suggestions.add(com.example.core.intelligence.conversation.ActionableOption(step.label, step.actionIntent ?: step.label))
    }
    if (suggestions.isEmpty()) {
      suggestions.add(com.example.core.intelligence.conversation.ActionableOption(if (isArabic) "شغّل الموسيقى الآن" else "Play music now", "شغل الموسيقى"))
    }

    return ExecutionResult.Success(
      speech = synthesis.synthesizedSpeech,
      actionTaken = "Hypothetical Analysis: ${synthesis.primaryDecision}",
      visualState = CoreVisualState.RESPONDING,
      proactiveSuggestions = suggestions
    )
  }

  suspend fun handleHypotheticalScheduleDelay(delayMinutes: Int): ExecutionResult {
    val builder = situationSnapshotBuilder
    val reasoner = contextReasoningEngine
    val isArabic = Locale.getDefault().language.startsWith("ar")

    if (builder == null || reasoner == null) {
      val fallback = if (isArabic) {
        "التأخر لمدة $delayMinutes دقائق يقلل من الوقت المتاح للوصول للمواعيد القادمة بمقدار $delayMinutes دقائق."
      } else {
        "A $delayMinutes-minute delay reduces remaining time for upcoming events by $delayMinutes minutes."
      }
      return ExecutionResult.Success(
        speech = fallback,
        actionTaken = "Hypothetical Schedule Delay (Fallback)",
        visualState = CoreVisualState.RESPONDING
      )
    }

    val req = com.example.core.intelligence.ContextRequirements(
      domain = com.example.core.intelligence.IntentDomain.APPOINTMENT_TIMING,
      required = setOf(com.example.core.intelligence.ContextField.CALENDAR)
    )
    val snapshot = builder.captureSnapshot(req)
    val synthesis = reasoner.reasonHypotheticalScheduleDelay(snapshot, delayMinutes, isArabic)

    return ExecutionResult.Success(
      speech = synthesis.synthesizedSpeech,
      actionTaken = "Hypothetical Delay: ${synthesis.primaryDecision}",
      visualState = CoreVisualState.RESPONDING
    )
  }
}
