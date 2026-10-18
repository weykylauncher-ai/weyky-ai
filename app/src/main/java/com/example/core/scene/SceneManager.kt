package com.example.core.scene

import com.example.core.context.ContextSnapshot
import com.example.core.context.ContextState
import com.example.rendering.visual_state.CoreVisualState

enum class WeykyScene(val displayName: String, val allowsFullInteraction: Boolean) {
  IDLE_SURFACE("Resting on Surface", allowsFullInteraction = true),
  IN_HAND_ACTIVE("In Hand - Active", allowsFullInteraction = true),
  IN_POCKET_STANDBY("In Pocket - Standby", allowsFullInteraction = false),
  IN_MOTION("In Motion", allowsFullInteraction = true),
  CALL_ALERT("Call Alert", allowsFullInteraction = true),
  MEDIA_ACTIVE("Media Playback", allowsFullInteraction = true),
  NOTIFICATION_ALERT("Notification Alert", allowsFullInteraction = true),
  INTERACTING("Interaction Active", allowsFullInteraction = true)
}

object SceneResolver {
  fun resolveScene(
    contextSnapshot: ContextSnapshot,
    isMusicPlaying: Boolean,
    hasMissedCalls: Boolean,
    hasActiveNotifications: Boolean,
    currentVisualState: CoreVisualState,
    isPowerSaving: Boolean
  ): WeykyScene {
    // 1. Interaction overrides ambient scenes
    if (currentVisualState == CoreVisualState.LISTENING ||
      currentVisualState == CoreVisualState.PROCESSING ||
      currentVisualState == CoreVisualState.RESPONDING
    ) {
      return WeykyScene.INTERACTING
    }

    // 2. High priority event scenes
    if (hasMissedCalls || currentVisualState == CoreVisualState.CALL) {
      return WeykyScene.CALL_ALERT
    }

    // 3. Media playback scene
    if (isMusicPlaying || currentVisualState == CoreVisualState.MUSIC) {
      return WeykyScene.MEDIA_ACTIVE
    }

    // 4. In pocket scene
    if (contextSnapshot.state == ContextState.IN_POCKET) {
      return WeykyScene.IN_POCKET_STANDBY
    }

    // 5. In motion scene
    if (contextSnapshot.state == ContextState.MOVING) {
      return WeykyScene.IN_MOTION
    }

    // 6. In hand active scene
    if (contextSnapshot.state == ContextState.IN_HAND) {
      return WeykyScene.IN_HAND_ACTIVE
    }

    // 7. On surface / resting
    return WeykyScene.IDLE_SURFACE
  }
}
