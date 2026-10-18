package com.example.core.intent.handlers

import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.example.core.capabilities.CapabilityDetector
import com.example.core.capabilities.DeviceCapability
import com.example.core.intent.ExecutionResult
import com.example.device.notifications.NotificationRepository
import com.example.rendering.visual_state.CoreVisualState
import java.util.Locale

class NotificationActionHandler(
  private val context: Context,
  private val capabilityDetector: CapabilityDetector
) {

  fun handle(openSettings: Boolean): ExecutionResult {
    val isArabic = Locale.getDefault().language.startsWith("ar")

    if (openSettings) {
      val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
      }
      return try {
        context.startActivity(intent)
        ExecutionResult.Success(
          speech = if (isArabic) "فتح إعدادات الوصول للإشعارات." else "Opening notification access settings.",
          actionTaken = "Opened Notification Listener Settings",
          visualState = CoreVisualState.SUCCESS
        )
      } catch (_: Exception) {
        ExecutionResult.Fallback(
          explanation = if (isArabic) "تعذر فتح إعدادات الإشعارات." else "Could not open notification settings."
        )
      }
    }

    val hasAccess = capabilityDetector.isCapabilitySupported(DeviceCapability.NOTIFICATION_LISTENER)
    val summary = NotificationRepository.getSummary(hasAccess)

    return if (!hasAccess) {
      ExecutionResult.Fallback(
        explanation = if (isArabic) "الوصول إلى الإشعارات غير مفعل لـ WEYKY." else "Notification access is not enabled for WEYKY.",
        recoveryActionTitle = if (isArabic) "تفعيل الوصول" else "Enable Access",
        onRecoveryAction = {
          try {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
              flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
          } catch (_: Exception) {}
        }
      )
    } else {
      val count = summary.totalCount
      val latest = summary.latestTitle
      val text = if (isArabic) {
        if (count > 0) {
          if (!latest.isNullOrBlank()) "لديك $count إشعارات مهمة. آخرها: $latest" else "لديك $count إشعارات مهمة."
        } else {
          "لا توجد إشعارات جديدة."
        }
      } else {
        if (count > 0) {
          if (!latest.isNullOrBlank()) "You have $count active notification(s). Latest: $latest" else "You have $count active notification(s)."
        } else {
          "No active notifications."
        }
      }
      ExecutionResult.Success(
        speech = text,
        actionTaken = "Queried Notifications ($count)",
        visualState = CoreVisualState.NOTIFICATION
      )
    }
  }
}
