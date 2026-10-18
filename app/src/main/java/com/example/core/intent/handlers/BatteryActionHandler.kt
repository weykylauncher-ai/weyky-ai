package com.example.core.intent.handlers

import com.example.core.intent.ExecutionResult
import com.example.device.battery.BatteryProvider
import com.example.device.battery.ChargingState
import com.example.rendering.visual_state.CoreVisualState
import java.util.Locale

class BatteryActionHandler(private val batteryProvider: BatteryProvider) {

  fun handle(): ExecutionResult {
    val b = batteryProvider.queryCurrentBattery()
      ?: return ExecutionResult.Unavailable("Battery telemetry is not available on this device.")

    if (!b.isAvailable || b.percentage < 0) {
      return ExecutionResult.Unavailable("Battery information is currently unavailable.")
    }

    val isArabic = Locale.getDefault().language.startsWith("ar")
    if (isArabic) {
      val chargingText = when (b.chargingState) {
        ChargingState.CHARGING -> "وهي قيد الشحن"
        ChargingState.FULL -> "ومشحونة بالكامل"
        ChargingState.DISCHARGING -> "في وضع التفريغ"
        ChargingState.NOT_CHARGING -> "غير متصلة بالشاحن"
        ChargingState.UNKNOWN -> "حالة الشحن غير معروفة"
      }
      val text = "نسبة البطارية الآن ${b.percentage}% $chargingText."
      return ExecutionResult.Success(
        speech = text,
        actionTaken = "Queried Battery ($chargingText)",
        visualState = CoreVisualState.RESPONDING
      )
    }

    val chargingText = when (b.chargingState) {
      ChargingState.CHARGING -> "charging via ${b.powerSource.name}"
      ChargingState.FULL -> "fully charged"
      ChargingState.DISCHARGING -> "discharging"
      ChargingState.NOT_CHARGING -> "not charging"
      ChargingState.UNKNOWN -> "battery status unknown"
    }

    val text = "Battery is at ${b.percentage}%, $chargingText."

    return ExecutionResult.Success(
      speech = text,
      actionTaken = "Queried Battery ($chargingText)",
      visualState = CoreVisualState.RESPONDING
    )
  }
}
