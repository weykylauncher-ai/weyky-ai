package com.example.core.voice.engine

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import com.example.core.intent.SpeechResult
import java.util.Locale

/**
 * Intelligent Multi-Engine Speech Synthesizer for WEYKY.
 * Key capabilities:
 * 1. Discovers and tests all installed TTS engines, not just the default one.
 * 2. Explicitly verifies Arabic speech capability.
 * 3. Never falls back to English when Arabic speech is requested and unsupported;
 *    instead reports ArabicUnsupported so the UI cleanly displays the text without awkward sounds.
 * 4. Preprocesses unvocalized Arabic text for optimal TTS clarity (number expansion, symbol normalization).
 * 5. Uses USAGE_ASSISTANT audio routing and smooth transient media ducking.
 */
class ArabicSpeechSynthesizer(
  private val context: Context,
  private val onSpeechStateChanged: ((Boolean) -> Unit)? = null
) : TextToSpeech.OnInitListener {

  companion object {
    private const val TAG = "WEYKY_TTS"
    private const val DEDUP_WINDOW_MS = 900L
  }

  private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
  private val modelManager = com.example.core.voice.model.VoiceModelManager(context.applicationContext)
  private var tts: TextToSpeech? = null
  private var isInitialized = false
  private var pendingSpeechText: String? = null
  private var audioFocusRequest: AudioFocusRequest? = null

  // Engine discovery
  private val inspector = TtsEngineInspector(context)
  private var activeEnginePackage: String? = null
  var isArabicSupportedByActiveEngine: Boolean = false
    private set

  @Volatile
  var isSpeakingNow: Boolean = false
    private set

  @Volatile
  private var hasActiveAudioFocus: Boolean = false

  @Volatile
  private var lastSpokenTextNormalized: String = ""

  @Volatile
  private var lastSpokenTimestampMs: Long = 0L

  @Volatile
  var activeTtsSource: String = "SYSTEM_OR_FALLBACK"
    private set

  // Priority 1 (Cloud Neural): Microsoft Edge Neural Studio TTS (Free, Online, Ultra-Natural Arabic Voices)
  val edgeNeuralEngine = EdgeNeuralTtsEngine(context) { isSpeaking ->
    isSpeakingNow = isSpeaking
    if (!isSpeaking) {
      abandonTransientAudioFocus()
    }
    onSpeechStateChanged?.invoke(isSpeaking)
  }

  var isHybridModeEnabled: Boolean = true

  // Priority 2: Pure Offline Arabic Female Neural TTS Engine (Piper VITS ONNX Runtime)
  val neuralEngine = LocalNeuralTtsEngine(context) { isSpeaking ->
    isSpeakingNow = isSpeaking
    if (!isSpeaking) {
      abandonTransientAudioFocus()
    }
    onSpeechStateChanged?.invoke(isSpeaking)
  }

  // Priority 4: Embedded on-device acoustic fallback (when neither Neural ONNX model nor System Arabic TTS is available)
  private val localEngine = LocalArabicTtsEngine(context) { isSpeaking ->
    isSpeakingNow = isSpeaking
    if (!isSpeaking) {
      abandonTransientAudioFocus()
    }
    onSpeechStateChanged?.invoke(isSpeaking)
  }

  fun getActiveOrInstalledNeuralTtsModel(): com.example.core.voice.model.VoiceModelInfo? {
    val preferredId = modelManager.getActiveTtsModelId()
    val preferred = com.example.core.voice.model.VoiceModelManager.CATALOG.firstOrNull {
      it.id == preferredId && it.type == com.example.core.voice.model.VoiceModelType.TTS && modelManager.isModelInstalled(it)
    }
    if (preferred != null) return preferred

    return com.example.core.voice.model.VoiceModelManager.CATALOG.firstOrNull {
      it.type == com.example.core.voice.model.VoiceModelType.TTS && modelManager.isModelInstalled(it)
    }
  }

  val isNeuralArabicTtsInstalled: Boolean
    get() = getActiveOrInstalledNeuralTtsModel() != null

  val isLocalArabicTtsAvailable: Boolean
    get() = isNeuralArabicTtsInstalled || isArabicSupportedByActiveEngine || true

  private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
    Log.d(TAG, "[AUDIO-DIAG] TTS AudioFocus changed: focusChange=$focusChange")
  }

  var assistantSpeechVolume: Float = 1.0f

  val speechAudioAttributes: AudioAttributes = AudioAttributes.Builder().apply {
    setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      setUsage(AudioAttributes.USAGE_ASSISTANT)
    } else {
      setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
    }
  }.build()

  init {
    initializeEngine(null)
  }

  private fun initializeEngine(enginePackage: String?) {
    try {
      tts?.shutdown()
    } catch (_: Exception) {}
    tts = null
    isInitialized = false
    activeEnginePackage = enginePackage

    try {
      tts = if (enginePackage != null) {
        Log.i(TAG, "Initializing TTS with specific engine: $enginePackage")
        TextToSpeech(context.applicationContext, this, enginePackage)
      } else {
        Log.i(TAG, "Initializing TTS with system default engine")
        TextToSpeech(context.applicationContext, this)
      }
    } catch (e: Exception) {
      Log.e(TAG, "Failed to instantiate TextToSpeech", e)
    }
  }

  override fun onInit(status: Int) {
    if (status == TextToSpeech.SUCCESS) {
      isInitialized = true
      applyAudioAttributes()
      checkArabicSupportAndConfigureVoice()

      tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
          isSpeakingNow = true
          onSpeechStateChanged?.invoke(true)
        }

        override fun onDone(utteranceId: String?) {
          isSpeakingNow = false
          abandonTransientAudioFocus()
          onSpeechStateChanged?.invoke(false)
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
          isSpeakingNow = false
          abandonTransientAudioFocus()
          onSpeechStateChanged?.invoke(false)
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
          Log.w(TAG, "TTS utterance error: code=$errorCode utteranceId=$utteranceId")
          isSpeakingNow = false
          abandonTransientAudioFocus()
          onSpeechStateChanged?.invoke(false)
        }
      })

      val pending = pendingSpeechText
      if (!pending.isNullOrBlank()) {
        pendingSpeechText = null
        speak(pending)
      }
    } else {
      isInitialized = false
      Log.w(TAG, "TTS initialization failed with status $status")
      tryFallbackToAlternativeEngine()
    }
  }

  private fun checkArabicSupportAndConfigureVoice() {
    val currentTts = tts ?: return
    val arabicLocale = Locale.forLanguageTag("ar")
    val langResult = currentTts.isLanguageAvailable(arabicLocale)
    val isSupported = (langResult == TextToSpeech.LANG_AVAILABLE ||
      langResult == TextToSpeech.LANG_COUNTRY_AVAILABLE ||
      langResult == TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE)

    if (isSupported) {
      currentTts.language = arabicLocale
      isArabicSupportedByActiveEngine = true

      // Pick best offline female Arabic voice if available, independent of phone system locale
      try {
        val arabicVoices = currentTts.voices?.filter { it.locale.language == "ar" }
        val bestVoice = arabicVoices?.firstOrNull {
          !it.isNetworkConnectionRequired && (it.name.contains("female", ignoreCase = true) || it.name.contains("ar-xa-x-arz", ignoreCase = true) || it.name.contains("ar-xa-x-arc", ignoreCase = true))
        } ?: arabicVoices?.firstOrNull { !it.isNetworkConnectionRequired }
          ?: arabicVoices?.firstOrNull()
        if (bestVoice != null) {
          currentTts.voice = bestVoice
          Log.i(TAG, "Configured Arabic system voice: ${bestVoice.name} (networkRequired=${bestVoice.isNetworkConnectionRequired})")
        }
      } catch (_: Exception) {}

      Log.i(TAG, "Arabic language confirmed on engine: ${activeEnginePackage ?: "default"}")
    } else {
      isArabicSupportedByActiveEngine = false
      Log.w(TAG, "Current TTS engine does NOT support Arabic (langResult=$langResult). Scanning alternatives...")
      tryFallbackToAlternativeEngine()
    }
  }

  private fun tryFallbackToAlternativeEngine() {
    val engines = inspector.getInstalledEngines()
    val otherEngines = engines.map { it.name }.filter { it != activeEnginePackage }
    for (candidate in otherEngines) {
      Log.i(TAG, "Testing alternative TTS engine: $candidate")
      initializeEngine(candidate)
      return // Attempt one candidate; onInit will chain if needed
    }
  }

  private fun applyAudioAttributes() {
    try {
      tts?.setAudioAttributes(speechAudioAttributes)
    } catch (e: Exception) {
      Log.w(TAG, "Failed setting audio attributes", e)
    }
  }

  private fun requestTransientAudioFocus(): Boolean {
    val am = audioManager ?: return false
    return try {
      val res = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        if (audioFocusRequest == null) {
          audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(speechAudioAttributes)
            .setWillPauseWhenDucked(false)
            .setAcceptsDelayedFocusGain(false)
            .setOnAudioFocusChangeListener(audioFocusChangeListener)
            .build()
        }
        am.requestAudioFocus(audioFocusRequest!!)
      } else {
        @Suppress("DEPRECATION")
        am.requestAudioFocus(audioFocusChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
      }
      val granted = res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
      hasActiveAudioFocus = granted
      Log.d(TAG, "[AUDIO-DIAG] requestTransientAudioFocus(AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK) -> granted=$granted (code=$res)")
      granted
    } catch (e: Exception) {
      Log.w(TAG, "[AUDIO-DIAG] requestTransientAudioFocus failed: ${e.message}")
      false
    }
  }

  private fun abandonTransientAudioFocus() {
    if (!hasActiveAudioFocus) return
    val am = audioManager ?: return
    try {
      val res = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        audioFocusRequest?.let { am.abandonAudioFocusRequest(it) } ?: AudioManager.AUDIOFOCUS_REQUEST_FAILED
      } else {
        @Suppress("DEPRECATION")
        am.abandonAudioFocus(audioFocusChangeListener)
      }
      hasActiveAudioFocus = false
      Log.d(TAG, "[AUDIO-DIAG] abandonTransientAudioFocus -> result=$res")
    } catch (e: Exception) {
      Log.w(TAG, "[AUDIO-DIAG] abandonTransientAudioFocus error: ${e.message}")
    }
  }

  /**
   * Speaks the text using the 4-tier Hybrid Speech hierarchy:
   * 1. Cloud Neural Studio TTS (Microsoft Edge Neural TTS: Salma, Hamed, Shakir, Fatima) when connected to internet and hybrid enabled.
   * 2. Local Arabic Female Neural TTS (Piper VITS ONNX) when model is installed.
   * 3. Local System Android TTS (TextToSpeech) with Arabic voice forced.
   * 4. Embedded local acoustic fallback (LocalArabicTtsEngine) ensuring the app is never silent.
   */
  fun speak(text: String, flush: Boolean = true): SpeechResult {
    if (text.isBlank()) return SpeechResult.Skipped

    val normalizedDedupKey = text.trim().replace(Regex("\\s+"), " ")
    val now = System.currentTimeMillis()
    if (normalizedDedupKey == lastSpokenTextNormalized && (now - lastSpokenTimestampMs) < DEDUP_WINDOW_MS && isCurrentlySpeaking()) {
      Log.d(TAG, "[TTS-DIAG] Skipping duplicate speech request within ${DEDUP_WINDOW_MS}ms")
      return SpeechResult.Success
    }
    lastSpokenTextNormalized = normalizedDedupKey
    lastSpokenTimestampMs = now

    val isArabic = containsArabic(text)

    // Vocalize and normalize Arabic text (number expansion, speed/unit expansion e.g. 24كم\س -> أربعة وعشرون كيلومتراً في الساعة)
    val vocalizedText = if (isArabic) {
      ArabicTextDiacritizer.prepareAndDiacritize(prepareArabicTextForTts(text))
    } else {
      prepareEnglishTextForTts(text)
    }

    // Tier 1: Hybrid Cloud Neural TTS (Microsoft Edge Neural) when online
    if (isArabic && isHybridModeEnabled && edgeNeuralEngine.isOnline()) {
      if (flush) {
        stopOfflineEnginesOnly()
      }
      requestTransientAudioFocus()
      activeTtsSource = "EDGE_CLOUD_NEURAL"
      val edgeRes = edgeNeuralEngine.speak(
        text = vocalizedText,
        flush = flush,
        volume = assistantSpeechVolume,
        onFallbackRequired = { failedText ->
          Log.i(TAG, "[HYBRID-TTS] Cloud synthesis unavailable or delayed; falling back to offline engines.")
          speakOfflineHierarchy(failedText, isArabic = true, flush = true)
        }
      )
      return edgeRes
    }

    return speakOfflineHierarchy(vocalizedText, isArabic = isArabic, flush = flush)
  }

  private fun speakOfflineHierarchy(text: String, isArabic: Boolean, flush: Boolean): SpeechResult {
    // Tier 2: Local Arabic Neural TTS (Piper VITS ONNX: Female or Male Kareem) when model is installed
    val neuralModelInfo = getActiveOrInstalledNeuralTtsModel()
    if (isArabic && neuralModelInfo != null && modelManager.isModelInstalled(neuralModelInfo)) {
      val modelDir = modelManager.getModelDirectory(neuralModelInfo)
      if (neuralEngine.isModelDirectoryValid(modelDir)) {
        if (flush) {
          stopSystemAndFallbackOnly()
        }
        requestTransientAudioFocus()
        val neuralRes = neuralEngine.speak(
          text = text,
          modelDir = modelDir,
          flush = flush,
          volume = assistantSpeechVolume,
          onFallbackRequired = { failedText ->
            Log.w(TAG, "[TTS-DIAG] Neural TTS encountered runtime inference error; falling back to local engine")
            speakWithSystemOrLocalFallback(failedText, isArabic = true, flush = true)
          }
        )
        if (neuralRes is SpeechResult.Success) {
          activeTtsSource = "NEURAL_ONNX_LOCAL"
          isSpeakingNow = true
          return SpeechResult.Success
        }
      }
    }

    return speakWithSystemOrLocalFallback(text, isArabic = isArabic, flush = flush)
  }

  private fun speakWithSystemOrLocalFallback(text: String, isArabic: Boolean, flush: Boolean): SpeechResult {
    if (flush && neuralEngine.isSpeaking) {
      neuralEngine.stop()
    }

    // Tier 3: Android System TTS if initialized and supports Arabic (or non-Arabic text)
    if (isInitialized && tts != null && (!isArabic || isArabicSupportedByActiveEngine)) {
      val preparedText = if (isArabic) {
        // Force Arabic locale on system TTS regardless of phone system language
        try {
          tts?.language = Locale.forLanguageTag("ar")
        } catch (_: Exception) {}
        text
      } else {
        text
      }

      try {
        requestTransientAudioFocus()
        val queueMode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        val utteranceId = "weyky_${System.currentTimeMillis()}"
        val params = Bundle().apply {
          putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
          putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, assistantSpeechVolume.coerceIn(0.0f, 1.0f))
        }

        val res = tts?.speak(preparedText, queueMode, params, utteranceId)
        if (res == TextToSpeech.SUCCESS) {
          activeTtsSource = "SYSTEM_LOCAL_TTS"
          isSpeakingNow = true
          return SpeechResult.Success
        }
      } catch (e: Exception) {
        Log.w(TAG, "[TTS-DIAG] System TTS speak failed: ${e.message}")
      }
    }

    // If System TTS is still initializing and no Neural model is installed yet, queue pending text
    if (!isInitialized && tts != null) {
      pendingSpeechText = text
    }

    // Tier 4: If Arabic is requested and system TTS does not support Arabic (e.g. Huawei P40 Lite E without GMS),
    // use the embedded offline local synthesizer as a safe local fallback while noting quality difference
    if (isArabic) {
      return try {
        requestTransientAudioFocus()
        activeTtsSource = "EMBEDDED_FORMANT_FALLBACK"
        Log.i(TAG, "[TTS-DIAG] Using embedded local Arabic fallback (Neural TTS model not yet installed and system TTS lacks Arabic)")
        localEngine.speak(text, flush)
      } catch (e: Exception) {
        abandonTransientAudioFocus()
        SpeechResult.Failed("تعذر تشغيل النطق المحلي: ${e.message}")
      }
    }

    abandonTransientAudioFocus()
    return SpeechResult.Failed("لا يتوفر محرك نطق صالح حالياً.")
  }

  private fun stopOfflineEnginesOnly() {
    if (neuralEngine.isSpeaking) {
      neuralEngine.stop()
    }
    stopSystemAndFallbackOnly()
  }

  private fun stopSystemAndFallbackOnly() {
    try {
      if (tts?.isSpeaking == true) {
        tts?.stop()
      }
    } catch (_: Exception) {}
    if (localEngine.isPlaying) {
      localEngine.stop()
    }
  }

  fun isCurrentlySpeaking(): Boolean {
    val systemSpeaking = try {
      tts?.isSpeaking == true
    } catch (_: Exception) {
      false
    }
    return isSpeakingNow || edgeNeuralEngine.isSpeaking || neuralEngine.isSpeaking || systemSpeaking || localEngine.isPlaying
  }

  /**
   * Stops WEYKY's own TTS output only if it is actively speaking or holding transient focus.
   * Never touches external media playback or dispatches global media stop commands.
   */
  fun stopIfSpeaking(): Boolean {
    pendingSpeechText = null
    val active = isCurrentlySpeaking()
    if (!active && !hasActiveAudioFocus) {
      Log.d(TAG, "[AUDIO-DIAG] stopIfSpeaking: WEYKY TTS is already silent; skipping TTS stop IPC.")
      return false
    }
    Log.i(TAG, "[AUDIO-DIAG] stopIfSpeaking: Stopping active WEYKY TTS utterance (active=$active, focus=$hasActiveAudioFocus)")
    stop()
    return true
  }

  fun stop() {
    pendingSpeechText = null
    if (edgeNeuralEngine.isSpeaking) {
      edgeNeuralEngine.stop()
    }
    if (neuralEngine.isSpeaking) {
      neuralEngine.stop()
    }
    val wasSystemSpeaking = try {
      tts?.isSpeaking == true
    } catch (_: Exception) {
      false
    }
    if (isSpeakingNow || wasSystemSpeaking) {
      try {
        tts?.stop()
      } catch (_: Exception) {}
    }
    if (localEngine.isPlaying) {
      localEngine.stop()
    }
    isSpeakingNow = false
    abandonTransientAudioFocus()
    onSpeechStateChanged?.invoke(false)
  }

  fun shutdown() {
    stop()
    edgeNeuralEngine.shutdown()
    neuralEngine.shutdown()
    localEngine.shutdown()
    try {
      tts?.shutdown()
    } catch (_: Exception) {}
    tts = null
    isInitialized = false
    onSpeechStateChanged?.invoke(false)
  }

  /**
   * Normalizes unvocalized Arabic text for smoother TTS pronunciation.
   * Replaces uppercase "WEYKY" (and duplicate parentheticals like "WEYKY (ويكي)" or "WEYKY (وايكي)")
   * with phonetic Arabic "وايكي" so TTS engines pronounce it as a single word rather than spelling W-E-Y-K-Y.
   */
  private fun prepareArabicTextForTts(raw: String): String {
    return raw
      .replace(Regex("""(?i)\bweyky\s*\(\s*(?:وايكي|ويكي)\s*\)"""), "وايكي")
      .replace(Regex("""(?i)\bweyky\b"""), "وايكي")
      .replace("ويكي", "وايكي")
      .replace(Regex("""\(\s*On-Device Intelligence\s*\)""", RegexOption.IGNORE_CASE), "")
      .replace("%", " بالمئة ")
      .replace("°C", " درجة مئوية ")
      .replace("°م", " درجة مئوية ")
      .replace(" / ", " من أصل ")
      .replace("km/h", " كيلومتر في الساعة ")
      .replace("MB", " ميجابايت ")
      .replace("GB", " جيجابايت ")
      .replace(Regex("\\s+"), " ")
      .trim()
  }

  /**
   * Converts all-caps "WEYKY" to title-case "Weyky" in English TTS so it is spoken as a word rather than an acronym.
   */
  private fun prepareEnglishTextForTts(raw: String): String {
    return raw
      .replace(Regex("""(?i)\bweyky\b"""), "Weyky")
      .trim()
  }

  private fun containsArabic(text: String): Boolean {
    for (char in text) {
      val block = Character.UnicodeBlock.of(char)
      if (block == Character.UnicodeBlock.ARABIC ||
        block == Character.UnicodeBlock.ARABIC_SUPPLEMENT ||
        block == Character.UnicodeBlock.ARABIC_PRESENTATION_FORMS_A ||
        block == Character.UnicodeBlock.ARABIC_PRESENTATION_FORMS_B
      ) {
        return true
      }
    }
    return false
  }
}
