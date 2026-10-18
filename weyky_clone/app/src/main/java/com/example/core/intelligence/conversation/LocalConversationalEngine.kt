package com.example.core.intelligence.conversation

import com.example.core.intelligence.ContextRequirementsRegistry
import com.example.core.intelligence.IntentDomain
import com.example.core.intelligence.SituationSnapshotBuilder
import com.example.device.info.DeviceInfoProvider
import java.util.Locale

/**
 * Top-level on-device Conversational Intelligence Engine.
 * Integrates conversation history, local reasoning, and device context grounding.
 * Completely local, zero cloud dependency, no API keys needed.
 */
class LocalConversationalEngine(
  private val situationSnapshotBuilder: SituationSnapshotBuilder? = null,
  deviceInfoProvider: DeviceInfoProvider? = null
) {
  val session = ConversationSession()
  private val reasoner = ConversationalReasoner(deviceInfoProvider)

  suspend fun respond(userQuery: String): ConversationalResponse {
    val query = userQuery.trim()
    session.recordUserTurn(query)

    // 1. Check if the query is a context-grounded query
    val requirements = ContextRequirementsRegistry.resolve(query)
    if (requirements.domain != IntentDomain.GENERAL && requirements.domain != IntentDomain.DIRECT_COMMAND) {
      val situation = situationSnapshotBuilder?.captureSnapshot(requirements)
      if (situation != null) {
        val reasoningEngine = com.example.core.intelligence.ContextReasoningEngine()
        val synthesis = reasoningEngine.synthesize(requirements, situation, query)

        val suggestions = mutableListOf<ActionableOption>()
        synthesis.nextStep?.let { step ->
          suggestions.add(ActionableOption(step.label, step.actionIntent ?: step.label))
        }
        val isArabic = Locale.getDefault().language.startsWith("ar")
        if (suggestions.isEmpty()) {
          suggestions.add(ActionableOption(if (isArabic) "فحص الطقس" else "Weather", "ما حالة الطقس؟"))
          suggestions.add(ActionableOption(if (isArabic) "فحص البطارية" else "Battery", "كم نسبة البطارية؟"))
        }

        session.recordAssistantTurn(synthesis.synthesizedSpeech, suggestions, requirements.domain.name)
        return ConversationalResponse(
          replyText = synthesis.synthesizedSpeech,
          topic = requirements.domain.name,
          actionableSuggestions = suggestions
        )
      }
    }

    // 2. Perform local conversational reasoning with multi-turn history
    val result = reasoner.reason(query, session)
    session.recordAssistantTurn(result.replyText, result.proactiveSuggestions, result.topicDetected)

    return ConversationalResponse(
      replyText = result.replyText,
      topic = result.topicDetected,
      actionableSuggestions = result.proactiveSuggestions
    )
  }
}

data class ConversationalResponse(
  val replyText: String,
  val topic: String?,
  val actionableSuggestions: List<ActionableOption>
)
