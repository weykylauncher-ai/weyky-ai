package com.example.core.voice

import android.content.Context
import com.example.core.intent.SpeechResult
import com.example.core.voice.engine.ArabicSpeechSynthesizer

/**
 * Android TextToSpeech engine wrapper for WEYKY, powered by ArabicSpeechSynthesizer.
 * Provides backward-compatible interface while guaranteeing real multi-engine Arabic inspection,
 * proper text preprocessing, and avoiding embarrassing English fallbacks.
 */
class WeykySpeechSynthesizer(
  private val context: Context,
  private val onSpeechStateChanged: ((Boolean) -> Unit)? = null
) {
  val synthesizer = ArabicSpeechSynthesizer(
    context = context,
    onSpeechStateChanged = onSpeechStateChanged
  )

  var assistantSpeechVolume: Float
    get() = synthesizer.assistantSpeechVolume
    set(value) { synthesizer.assistantSpeechVolume = value }

  val isArabicSupported: Boolean
    get() = synthesizer.isArabicSupportedByActiveEngine || synthesizer.isHybridModeEnabled

  val isSpeaking: Boolean
    get() = synthesizer.isCurrentlySpeaking()

  var isHybridModeEnabled: Boolean
    get() = synthesizer.isHybridModeEnabled
    set(value) { synthesizer.isHybridModeEnabled = value }

  var activeCloudVoice: String
    get() = synthesizer.edgeNeuralEngine.activeVoiceName
    set(value) { synthesizer.edgeNeuralEngine.activeVoiceName = value }

  val activeTtsSource: String
    get() = synthesizer.activeTtsSource

  fun speak(text: String, flush: Boolean = true): SpeechResult {
    return synthesizer.speak(text, flush)
  }

  fun stopIfSpeaking(): Boolean {
    return synthesizer.stopIfSpeaking()
  }

  fun stop() {
    synthesizer.stop()
  }

  fun shutdown() {
    synthesizer.shutdown()
  }
}
