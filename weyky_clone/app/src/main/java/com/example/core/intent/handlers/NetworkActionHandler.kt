package com.example.core.intent.handlers

import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.example.core.intent.ExecutionResult
import com.example.device.network.NetworkProvider
import com.example.device.network.NetworkType
import com.example.rendering.visual_state.CoreVisualState
import java.util.Locale

class NetworkActionHandler(
  private val context: Context,
  private val networkProvider: NetworkProvider
) {

  fun handleWifi(openSettings: Boolean): ExecutionResult {
    val isArabic = Locale.getDefault().language.startsWith("ar")

    if (openSettings) {
      val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
      }
      return try {
        context.startActivity(intent)
        ExecutionResult.Success(
          speech = if (isArabic) "فتح إعدادات الواي فاي." else "Opening Wi-Fi settings.",
          actionTaken = "Launched Wi-Fi Settings",
          visualState = CoreVisualState.SUCCESS
        )
      } catch (_: Exception) {
        val net = networkProvider.queryCurrentNetwork()
        ExecutionResult.Success(
          speech = if (isArabic) "حالة الواي فاي: ${if (net.type == NetworkType.WIFI) "متصل" else "غير نشط"}." else "Wi-Fi is ${if (net.type == NetworkType.WIFI) "connected" else "not active"}.",
          visualState = CoreVisualState.RESPONDING
        )
      }
    }

    val net = networkProvider.queryCurrentNetwork()
    return ExecutionResult.Success(
      speech = if (isArabic) "الشبكة الحالية: ${net.summary}." else "Current network: ${net.summary}.",
      actionTaken = "Queried Network",
      visualState = CoreVisualState.RESPONDING
    )
  }

  fun handleBluetooth(): ExecutionResult {
    val isArabic = Locale.getDefault().language.startsWith("ar")
    val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    return try {
      context.startActivity(intent)
      ExecutionResult.Success(
        speech = if (isArabic) "فتح إعدادات البلوتوث." else "Opening Bluetooth settings.",
        actionTaken = "Launched Bluetooth Settings",
        visualState = CoreVisualState.SUCCESS
      )
    } catch (_: Exception) {
      ExecutionResult.Fallback(
        explanation = if (isArabic) "تعذر فتح إعدادات البلوتوث." else "Unable to open Bluetooth settings on this device."
      )
    }
  }
}
