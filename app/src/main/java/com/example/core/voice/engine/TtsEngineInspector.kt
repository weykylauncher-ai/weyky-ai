package com.example.core.voice.engine

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

/**
 * Diagnostic info for an installed Text-To-Speech engine on the device.
 */
data class TtsEngineInfo(
  val packageName: String,
  val label: String,
  val isDefault: Boolean,
  val supportsArabic: Boolean,
  val availableArabicVoicesCount: Int
)

/**
 * Inspector for all installed TTS engines on Android, testing their real Arabic capabilities.
 */
class TtsEngineInspector(private val context: Context) {

  companion object {
    private const val TAG = "WEYKY_TTS_INSPECT"
  }

  fun getInstalledEngines(): List<TextToSpeech.EngineInfo> {
    var probeTts: TextToSpeech? = null
    return try {
      probeTts = TextToSpeech(context.applicationContext) { _ -> }
      probeTts.engines ?: emptyList()
    } catch (e: Exception) {
      Log.e(TAG, "Error enumerating TTS engines", e)
      emptyList()
    } finally {
      try {
        probeTts?.shutdown()
      } catch (_: Exception) {}
    }
  }

  fun getDefaultEngineName(): String? {
    var probeTts: TextToSpeech? = null
    return try {
      probeTts = TextToSpeech(context.applicationContext) { _ -> }
      probeTts.defaultEngine
    } catch (_: Exception) {
      null
    } finally {
      try {
        probeTts?.shutdown()
      } catch (_: Exception) {}
    }
  }

  /**
   * Tests whether a specific TTS engine supports Arabic by initializing it and checking isLanguageAvailable.
   */
  fun inspectEngine(enginePackageName: String, onResult: (TtsEngineInfo) -> Unit) {
    val defaultEngine = getDefaultEngineName()
    var targetTts: TextToSpeech? = null
    try {
      targetTts = TextToSpeech(context.applicationContext, { status ->
        if (status == TextToSpeech.SUCCESS) {
          val arabicLocale = Locale.forLanguageTag("ar")
          val langStatus = targetTts?.isLanguageAvailable(arabicLocale) ?: TextToSpeech.LANG_NOT_SUPPORTED
          val supportsArabic = (langStatus == TextToSpeech.LANG_AVAILABLE ||
            langStatus == TextToSpeech.LANG_COUNTRY_AVAILABLE ||
            langStatus == TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE)

          val arabicVoicesCount = try {
            val voices = targetTts?.voices
            voices?.count { it.locale.language == "ar" } ?: if (supportsArabic) 1 else 0
          } catch (_: Exception) {
            if (supportsArabic) 1 else 0
          }

          val info = TtsEngineInfo(
            packageName = enginePackageName,
            label = enginePackageName.substringAfterLast('.'),
            isDefault = (enginePackageName == defaultEngine),
            supportsArabic = supportsArabic,
            availableArabicVoicesCount = arabicVoicesCount
          )
          onResult(info)
        } else {
          onResult(
            TtsEngineInfo(
              packageName = enginePackageName,
              label = enginePackageName,
              isDefault = (enginePackageName == defaultEngine),
              supportsArabic = false,
              availableArabicVoicesCount = 0
            )
          )
        }
        try {
          targetTts?.shutdown()
        } catch (_: Exception) {}
      }, enginePackageName)
    } catch (e: Exception) {
      Log.e(TAG, "Failed to instantiate TTS for $enginePackageName", e)
      onResult(
        TtsEngineInfo(
          packageName = enginePackageName,
          label = enginePackageName,
          isDefault = (enginePackageName == defaultEngine),
          supportsArabic = false,
          availableArabicVoicesCount = 0
        )
      )
    }
  }
}
