package com.example.core.capabilities

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.location.LocationManager
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationManagerCompat

enum class DeviceCapability(val title: String) {
  FLASHLIGHT("Flashlight"),
  TELEPHONY("Phone Calls & Cellular"),
  WIFI("Wi-Fi Connectivity"),
  BLUETOOTH("Bluetooth"),
  VOICE_RECOGNITION("Speech Recognition"),
  NOTIFICATION_LISTENER("Notification Access"),
  ACCELEROMETER("Motion Accelerometer"),
  PROXIMITY_SENSOR("Proximity Sensor"),
  LOCATION("Location Hardware"),
  CONTACTS("Contacts Directory"),
  CALL_LOG("Call History"),
  MEDIA("Media Session Control")
}

class CapabilityDetector(private val context: Context) {
  private val packageManager: PackageManager = context.packageManager
  private val cameraManager: CameraManager? =
    context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager

  fun isCapabilitySupported(capability: DeviceCapability): Boolean {
    return when (capability) {
      DeviceCapability.FLASHLIGHT -> {
        val hasFlashFeature = packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)
        if (!hasFlashFeature) return false
        try {
          val cameraIds = cameraManager?.cameraIdList ?: emptyArray()
          cameraIds.any { id ->
            val characteristics = cameraManager?.getCameraCharacteristics(id)
            characteristics?.get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
          }
        } catch (_: Exception) {
          hasFlashFeature
        }
      }
      DeviceCapability.TELEPHONY -> {
        packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
      }
      DeviceCapability.WIFI -> {
        packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI)
      }
      DeviceCapability.BLUETOOTH -> {
        packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)
      }
      DeviceCapability.VOICE_RECOGNITION -> {
        val hasSystem = SpeechRecognizer.isRecognitionAvailable(context)
        val modelManager = com.example.core.voice.model.VoiceModelManager(context)
        val hasInstalledModel = com.example.core.voice.model.VoiceModelManager.CATALOG.any { modelManager.isModelInstalled(it) }
        hasSystem || hasInstalledModel
      }
      DeviceCapability.NOTIFICATION_LISTENER -> {
        val enabledPackages = NotificationManagerCompat.getEnabledListenerPackages(context)
        enabledPackages.contains(context.packageName)
      }
      DeviceCapability.ACCELEROMETER -> {
        packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_ACCELEROMETER)
      }
      DeviceCapability.PROXIMITY_SENSOR -> {
        packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_PROXIMITY)
      }
      DeviceCapability.LOCATION -> {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        lm != null && (packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION) ||
          packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS) ||
          packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION_NETWORK))
      }
      DeviceCapability.CONTACTS -> {
        packageManager.resolveContentProvider("com.android.contacts", 0) != null
      }
      DeviceCapability.CALL_LOG -> {
        packageManager.resolveContentProvider("call_log", 0) != null
      }
      DeviceCapability.MEDIA -> {
        context.getSystemService(Context.MEDIA_SESSION_SERVICE) != null
      }
    }
  }

  fun getMissingCapabilityExplanation(capability: DeviceCapability): String {
    return when (capability) {
      DeviceCapability.FLASHLIGHT -> "This device does not have camera flash hardware."
      DeviceCapability.TELEPHONY -> "Telephony or SIM calling is not available on this device."
      DeviceCapability.WIFI -> "Wi-Fi hardware is not available on this device."
      DeviceCapability.BLUETOOTH -> "Bluetooth hardware is not available on this device."
      DeviceCapability.VOICE_RECOGNITION -> "No speech recognition engine is installed on this device."
      DeviceCapability.NOTIFICATION_LISTENER -> "WEYKY requires Notification Access permission in system settings to read notifications."
      DeviceCapability.ACCELEROMETER -> "Accelerometer sensor is not available."
      DeviceCapability.PROXIMITY_SENSOR -> "Proximity sensor is not available."
      DeviceCapability.LOCATION -> "Location hardware or providers are not available on this device."
      DeviceCapability.CONTACTS -> "Contacts provider is not available."
      DeviceCapability.CALL_LOG -> "Call history provider is not available."
      DeviceCapability.MEDIA -> "Media session service is not available."
    }
  }
}
