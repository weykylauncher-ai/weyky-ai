package com.example.core.intent.handlers

import com.example.core.intent.ExecutionResult
import com.example.device.apps.AppMatch
import com.example.device.apps.AppResolveResult
import com.example.device.apps.AppResolver
import com.example.rendering.visual_state.CoreVisualState
import java.util.Locale

class AppActionHandler(private val appResolver: AppResolver) {

  fun handle(appName: String): ExecutionResult {
    val isArabic = Locale.getDefault().language.startsWith("ar")
    val resolveResult = appResolver.resolveApp(appName)

    return when (resolveResult) {
      is AppResolveResult.FoundSingle -> {
        launchSingleApp(resolveResult.app, isArabic)
      }
      is AppResolveResult.FoundMultiple -> {
        val options = resolveResult.apps.map { "${it.label} (${it.packageName})" }
        val prompt = if (isArabic) {
          "وجدت أكثر من تطبيق يطابق '$appName'. أيها تود فتحه؟"
        } else {
          "Found multiple apps matching '$appName'. Which one would you like to open?"
        }
        ExecutionResult.NeedsConfirmation(
          prompt = prompt,
          options = options,
          onConfirmOption = { selected ->
            val pkg = selected.substringAfter("(").substringBefore(")")
            val targetApp = resolveResult.apps.firstOrNull { it.packageName == pkg }
            if (targetApp != null) {
              launchSingleApp(targetApp, isArabic)
            }
          },
          visualState = CoreVisualState.RESPONDING
        )
      }
      is AppResolveResult.NotFound -> {
        val message = if (isArabic) {
          "تطبيق '$appName' غير مثبت على هذا الجهاز."
        } else {
          "Application '$appName' is not installed on this device."
        }
        ExecutionResult.Error(
          friendlyMessage = message,
          visualState = CoreVisualState.ERROR
        )
      }
      is AppResolveResult.LaunchFailed -> {
        val message = if (isArabic) {
          "تعذر فتح التطبيق: ${resolveResult.error}"
        } else {
          "Could not launch application: ${resolveResult.error}"
        }
        ExecutionResult.Error(
          friendlyMessage = message,
          visualState = CoreVisualState.ERROR
        )
      }
    }
  }

  private fun launchSingleApp(app: AppMatch, isArabic: Boolean): ExecutionResult {
    val success = appResolver.launchApp(app)
    return if (success) {
      val message = if (isArabic) "جاري فتح ${app.label}..." else "Opening ${app.label}..."
      ExecutionResult.Success(
        speech = message,
        actionTaken = "Launched ${app.label} (${app.packageName})",
        visualState = CoreVisualState.SUCCESS
      )
    } else {
      val message = if (isArabic) "تعذر تشغيل ${app.label} على هذا الجهاز." else "Failed to open ${app.label} on this device."
      ExecutionResult.Error(
        friendlyMessage = message,
        visualState = CoreVisualState.ERROR
      )
    }
  }
}
