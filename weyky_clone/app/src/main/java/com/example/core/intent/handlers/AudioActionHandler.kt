package com.example.core.intent.handlers

import com.example.core.intent.ExecutionResult
import com.example.device.audio.AudioController
import com.example.device.audio.VolumeAdjustment
import com.example.rendering.visual_state.CoreVisualState
import java.util.Locale

class AudioActionHandler(private val audioController: AudioController) {

  fun handleStep(increase: Boolean, stepCount: Int = 1, forRingtone: Boolean = false): ExecutionResult {
    val isArabic = Locale.getDefault().language.startsWith("ar")
    val res = audioController.adjustVolumeStep(increase, stepCount, isArabic, forRingtone)

    return if (res.success) {
      ExecutionResult.Success(
        speech = res.message,
        actionTaken = "Volume adjusted to ${res.percent}% (${res.currentStep}/${res.maxSteps})",
        visualState = CoreVisualState.SUCCESS
      )
    } else {
      ExecutionResult.Unavailable(
        reason = res.message,
        visualState = CoreVisualState.RESPONDING
      )
    }
  }

  fun handlePercent(percent: Int, forRingtone: Boolean = false): ExecutionResult {
    val isArabic = Locale.getDefault().language.startsWith("ar")
    val res = audioController.setVolumePercent(percent, isArabic, forRingtone)

    return if (res.success) {
      ExecutionResult.Success(
        speech = res.message,
        actionTaken = "Volume set to ${res.percent}% (${res.currentStep}/${res.maxSteps})",
        visualState = CoreVisualState.SUCCESS
      )
    } else {
      ExecutionResult.Unavailable(
        reason = res.message,
        visualState = CoreVisualState.RESPONDING
      )
    }
  }

  fun handleMute(mute: Boolean, forRingtone: Boolean = false): ExecutionResult {
    val isArabic = Locale.getDefault().language.startsWith("ar")
    val res = audioController.muteVolume(mute, isArabic, forRingtone)

    return ExecutionResult.Success(
      speech = res.message,
      actionTaken = if (mute) "Audio muted" else "Audio unmuted",
      visualState = CoreVisualState.SUCCESS
    )
  }

  fun handle(adjustment: VolumeAdjustment): ExecutionResult {
    return when (adjustment) {
      VolumeAdjustment.RAISE -> handleStep(increase = true, stepCount = 1)
      VolumeAdjustment.LOWER -> handleStep(increase = false, stepCount = 1)
      VolumeAdjustment.MUTE -> handleMute(mute = true)
      VolumeAdjustment.UNMUTE -> handleMute(mute = false)
      VolumeAdjustment.SET_PERCENT -> handlePercent(50)
      VolumeAdjustment.STEPS -> handleStep(increase = true, stepCount = 1)
    }
  }
}
