package com.example.core.intelligence

/**
 * Epistemological status of any piece of context or reasoning.
 * Distinguishes verified ground truths from calculated, estimated, or inferred values.
 */
enum class EpistemicStatus {
  FACT,           // Direct verified sensor or OS telemetry (e.g. Battery is 24%)
  CALCULATION,    // Mathematical deduction (e.g. 50 mins until 3:00 PM)
  ESTIMATE,       // Approximation based on models/averages (e.g. Travel duration ~30m)
  INFERENCE,      // Contextual synthesis of multiple facts (e.g. Battery might be tight)
  RECOMMENDATION, // Actionable advice to the user (e.g. Charge phone before leaving)
  UNAVAILABLE     // Missing data, permission denied, or offline
}

data class ContextDatum<T>(
  val value: T?,
  val status: EpistemicStatus,
  val source: String,
  val timestampMillis: Long = System.currentTimeMillis(),
  val confidence: Float = 1.0f,
  val unavailabilityReason: String? = null
) {
  val isAvailable: Boolean get() = status != EpistemicStatus.UNAVAILABLE && value != null

  companion object {
    fun <T> unavailable(source: String, reason: String): ContextDatum<T> =
      ContextDatum(
        value = null,
        status = EpistemicStatus.UNAVAILABLE,
        source = source,
        confidence = 0f,
        unavailabilityReason = reason
      )

    fun <T> fact(value: T, source: String, confidence: Float = 1.0f): ContextDatum<T> =
      ContextDatum(value = value, status = EpistemicStatus.FACT, source = source, confidence = confidence)

    fun <T> calculation(value: T, source: String): ContextDatum<T> =
      ContextDatum(value = value, status = EpistemicStatus.CALCULATION, source = source, confidence = 1.0f)

    fun <T> estimate(value: T, source: String, confidence: Float = 0.8f): ContextDatum<T> =
      ContextDatum(value = value, status = EpistemicStatus.ESTIMATE, source = source, confidence = confidence)

    fun <T> inference(value: T, source: String, confidence: Float = 0.75f): ContextDatum<T> =
      ContextDatum(value = value, status = EpistemicStatus.INFERENCE, source = source, confidence = confidence)
  }
}
