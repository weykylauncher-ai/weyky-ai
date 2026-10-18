package com.example.core.context

/**
 * Context states recognized by WEYKY:
 * IN_HAND, IN_POCKET, ON_TABLE, MOVING, STATIONARY
 */
enum class ContextState(val displayName: String, val description: String) {
  IN_HAND("In Hand", "User is actively holding the phone"),
  IN_POCKET("In Pocket", "Phone is enclosed in pocket or bag"),
  ON_TABLE("On Table", "Phone is resting horizontally on a surface"),
  MOVING("Moving", "User or device is in motion"),
  STATIONARY("Stationary", "Device is at rest")
}

data class ContextSnapshot(
  val state: ContextState,
  val confidence: Float,
  val pitchDegrees: Float = 0f,
  val rollDegrees: Float = 0f,
  val isProximityNear: Boolean = false,
  val motionMagnitude: Float = 0f,
  val timestamp: Long = System.currentTimeMillis()
)
