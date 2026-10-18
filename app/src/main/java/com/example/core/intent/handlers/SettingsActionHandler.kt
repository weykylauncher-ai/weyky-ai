package com.example.core.intent.handlers

import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.example.core.intent.ExecutionResult
import com.example.core.intent.WeykyIntent
import com.example.rendering.visual_state.CoreVisualState
import java.util.Locale

class SettingsActionHandler(private val context: Context) {

  fun handle(type: WeykyIntent.SettingsType): ExecutionResult {
    val isArabic = Locale.getDefault().language.startsWith("ar")
    val action = when (type) {
      WeykyIntent.SettingsType.BATTERY -> Intent.ACTION_POWER_USAGE_SUMMARY
      WeykyIntent.SettingsType.DISPLAY -> Settings.ACTION_DISPLAY_SETTINGS
      WeykyIntent.SettingsType.SOUND -> Settings.ACTION_SOUND_SETTINGS
      WeykyIntent.SettingsType.NOTIFICATIONS -> Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
      WeykyIntent.SettingsType.APPS -> Settings.ACTION_APPLICATION_SETTINGS
      WeykyIntent.SettingsType.GENERAL -> Settings.ACTION_SETTINGS
    }

    val intent = Intent(action).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
    return try {
      context.startActivity(intent)
      val label = if (isArabic) {
        when (type) {
          WeykyIntent.SettingsType.BATTERY -> "إعدادات البطارية"
          WeykyIntent.SettingsType.DISPLAY -> "إعدادات الشاشة"
          WeykyIntent.SettingsType.SOUND -> "إعدادات الصوت"
          WeykyIntent.SettingsType.NOTIFICATIONS -> "إعدادات الإشعارات"
          WeykyIntent.SettingsType.APPS -> "إعدادات التطبيقات"
          WeykyIntent.SettingsType.GENERAL -> "الإعدادات العامة"
        }
      } else {
        "${type.name.lowercase().replaceFirstChar { it.uppercase() }} settings"
      }
      ExecutionResult.Success(
        speech = if (isArabic) "فتح $label." else "Opening $label.",
        actionTaken = "Opened System Settings (${type.name})",
        visualState = CoreVisualState.SUCCESS
      )
    } catch (_: Exception) {
      ExecutionResult.Fallback(
        explanation = if (isArabic) "تعذر فتح الإعدادات على هذا الجهاز." else "Unable to open ${type.name.lowercase()} settings on this device."
      )
    }
  }
}
