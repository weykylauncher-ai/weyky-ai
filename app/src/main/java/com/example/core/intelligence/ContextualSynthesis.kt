package com.example.core.intelligence

/**
 * Proposed next actionable step by WEYKY after reasoning.
 */
data class NextStep(
  val label: String,
  val actionIntent: String? = null,
  val requiresConfirmation: Boolean = false,
  val isSafetyCritical: Boolean = false
)

/**
 * Result of contextual reasoning over a situation snapshot.
 */
data class ContextualSynthesis(
  val synthesizedSpeech: String,
  val primaryDecision: String,
  val nextStep: NextStep? = null,
  val factsUsed: List<String> = emptyList(),
  val inferencesMade: List<String> = emptyList(),
  val recommendations: List<String> = emptyList(),
  val unavailabilitiesHandled: List<String> = emptyList(),
  val confidenceScore: Float = 1.0f
)
