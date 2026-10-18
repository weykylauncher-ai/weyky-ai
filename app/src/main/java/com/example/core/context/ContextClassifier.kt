package com.example.core.context

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Pure, deterministic classifier for phone context.
 * Separated from Android SensorManager for 100% testability.
 */
object ContextClassifier {
  /**
   * Classify device context based on accelerometer readings, proximity state, and dynamic motion.
   *
   * @param ax X acceleration (m/s^2)
   * @param ay Y acceleration (m/s^2)
   * @param az Z acceleration (m/s^2)
   * @param isProximityNear true if proximity sensor detects object within close range (< 5cm)
   * @param motionVariance variance of acceleration magnitude over recent window
   */
  fun classify(
    ax: Float,
    ay: Float,
    az: Float,
    isProximityNear: Boolean,
    motionVariance: Float
  ): ContextSnapshot {
    val totalMagnitude = sqrt((ax * ax + ay * ay + az * az).toDouble()).toFloat()

    // Pitch: angle relative to horizontal (tilt forward/backward in degrees)
    // Roll: tilt sideways in degrees
    val pitch = Math.toDegrees(atan2(ay.toDouble(), sqrt((ax * ax + az * az).toDouble()))).toFloat()
    val roll = Math.toDegrees(atan2(-ax.toDouble(), az.toDouble())).toFloat()

    val isFlatHorizontal = abs(az) > 8.5f && abs(ax) < 3.0f && abs(ay) < 3.0f
    val isVerticalTilted = abs(ay) > 6.0f // Handheld upright or upside down
    val isUpsideDown = ay < -6.0f // Phone upside down in pocket

    val isSignificantMotion = motionVariance > 1.2f
    val isSubtleMotion = motionVariance > 0.15f

    val classifiedState: ContextState
    val confidence: Float

    when {
      // 1. In pocket: Proximity sensor is blocked AND either upside down, vertical, or moving
      isProximityNear && (isUpsideDown || isVerticalTilted || isSignificantMotion) -> {
        classifiedState = ContextState.IN_POCKET
        confidence = 0.92f
      }
      // 2. Proximity blocked while flat: phone placed face-down on table
      isProximityNear && isFlatHorizontal -> {
        classifiedState = ContextState.ON_TABLE
        confidence = 0.88f
      }
      // 3. On table: Flat horizontal with negligible motion variance
      isFlatHorizontal && !isSubtleMotion -> {
        classifiedState = ContextState.ON_TABLE
        confidence = 0.95f
      }
      // 4. Moving: High acceleration variance (walking, running, transit)
      isSignificantMotion -> {
        classifiedState = ContextState.MOVING
        confidence = 0.85f
      }
      // 5. In Hand: Moderate tilt angles characteristic of holding phone, or subtle micro-movements
      (pitch in 15.0f..75.0f || abs(roll) in 10.0f..50.0f || isSubtleMotion) -> {
        classifiedState = ContextState.IN_HAND
        confidence = 0.80f
      }
      // 6. Stationary: Device at rest in another orientation (e.g. phone stand)
      else -> {
        classifiedState = ContextState.STATIONARY
        confidence = 0.75f
      }
    }

    return ContextSnapshot(
      state = classifiedState,
      confidence = confidence,
      pitchDegrees = pitch,
      rollDegrees = roll,
      isProximityNear = isProximityNear,
      motionMagnitude = totalMagnitude
    )
  }
}
