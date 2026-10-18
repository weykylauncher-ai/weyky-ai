package com.example.core.intent.handlers

import com.example.core.capabilities.CapabilityDetector
import com.example.core.capabilities.DeviceCapability
import com.example.core.intent.ExecutionResult
import com.example.device.flashlight.FlashlightController
import com.example.rendering.visual_state.CoreVisualState
import java.util.Locale

class FlashlightActionHandler(
  private val capabilityDetector: CapabilityDetector,
  private val flashlightController: FlashlightController
) {

  fun handle(turnOn: Boolean): ExecutionResult {
    val isArabic = Locale.getDefault().language.startsWith("ar")

    if (!capabilityDetector.isCapabilitySupported(DeviceCapability.FLASHLIGHT)) {
      return ExecutionResult.NotSupported(
        reason = capabilityDetector.getMissingCapabilityExplanation(DeviceCapability.FLASHLIGHT),
        visualState = CoreVisualState.ERROR
      )
    }

    val result = flashlightController.setTorch(turnOn)
    return if (result.isSuccess) {
      val speech = if (isArabic) {
        if (turnOn) "تم تشغيل الكشاف." else "تم إطفاء الكشاف."
      } else {
        if (turnOn) "Flashlight turned on." else "Flashlight turned off."
      }
      val actionText = if (turnOn) "Torch turned on" else "Torch turned off"
      ExecutionResult.Success(
        speech = speech,
        actionTaken = actionText,
        visualState = CoreVisualState.SUCCESS
      )
    } else {
      val msg = if (isArabic) {
        "تعذر التحكم في الكشاف. قد تكون الكاميرا قيد الاستخدام من قبل تطبيق آخر."
      } else {
        "Could not control the flashlight. The camera hardware may be in use by another app."
      }
      ExecutionResult.Error(
        friendlyMessage = msg,
        visualState = CoreVisualState.ERROR
      )
    }
  }
}
