package com.example.core.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.core.voice.engine.ActiveSttEngineType
import com.example.core.voice.engine.UnifiedVoiceManager
import com.example.core.voice.engine.VoskSpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

sealed interface VoiceSessionState {
  object Idle : VoiceSessionState
  object Initializing : VoiceSessionState
  object Listening : VoiceSessionState
  object Processing : VoiceSessionState
  data class Partial(val text: String) : VoiceSessionState
  data class Success(val transcript: String) : VoiceSessionState
  data class Error(val message: String) : VoiceSessionState
  object PermissionRequired : VoiceSessionState
}

/**
 * Intelligent, Hybrid Voice Recognition Pipeline for WEYKY.
 * Order of execution & fallback:
 * - Online (with validated connectivity): Uses available RecognitionService (e.g., Google or OEM).
 * - Offline / Network Unvalidated / No Google: Switches to embedded Vosk engine using installed on-device model matching requested language.
 * - Handles mid-stream network drops cleanly, terminates stale sessions via monotonic session tokens,
 *   loads Vosk models off the Main Thread, and flushes final speech upon silence or manual stop.
 */
class VoicePipeline(
  private val context: Context,
  private val onTranscriptReady: (String) -> Unit,
  private val onListeningStateChanged: (Boolean) -> Unit,
  private val onVoiceEnergyChanged: (Float) -> Unit = {},
  val unifiedVoiceManager: UnifiedVoiceManager = UnifiedVoiceManager(context)
) : RecognitionListener {

  companion object {
    private const val TAG = "WEYKY_VOICE"
    var enableDiagnostics: Boolean = true

    // Named, maintainable watchdog timeout constants
    const val INITIAL_SPEECH_TIMEOUT_MS = 7000L
    const val POST_SPEECH_SILENCE_TIMEOUT_MS = 2800L
    const val MAX_UTTERANCE_DURATION_MS = 25000L
    const val SYSTEM_PROCESSING_TIMEOUT_MS = 5000L
    const val UNVALIDATED_NETWORK_CONNECT_TIMEOUT_MS = 3200L
    const val SYSTEM_RECOGNIZER_BIND_TIMEOUT_MS = 2200L

    // Android 12/13+ SpeechRecognizer language/server error codes
    private const val ERROR_SERVER_DISCONNECTED = 11
    private const val ERROR_LANGUAGE_NOT_SUPPORTED = 12
    private const val ERROR_LANGUAGE_UNAVAILABLE = 13
  }

  private val mainHandler = Handler(Looper.getMainLooper())
  private val modelLoadExecutor = Executors.newSingleThreadExecutor { r ->
    Thread(r, "WeykyVoskModelLoader").apply { isDaemon = true }
  }
  private var currentActivityContext: Context? = null

  private val _voiceEnergy = MutableStateFlow(0f)
  val voiceEnergy: StateFlow<Float> = _voiceEnergy.asStateFlow()

  // Monotonically increasing session ID to fence against stale callbacks and old watchdogs
  private val sessionCounter = AtomicLong(0L)

  @Volatile
  var currentSessionId: Long = 0L
    private set

  @Volatile
  private var currentRequestedLocale: String = "ar"

  @Volatile
  private var hasFallbackOccurredForSession: Boolean = false

  @Volatile
  private var isLoadingVoskModel: Boolean = false

  @Volatile
  private var latestPartialForCurrentSession: String = ""

  // System Recognizer
  private var speechRecognizer: SpeechRecognizer? = null

  // Vosk Offline Engine
  internal val voskRecognizer = VoskSpeechRecognizer(
    context = context,
    onTranscriptReady = { text ->
      handleSuccessfulTranscript(text, currentSessionId)
    },
    onPartialTranscript = { partial ->
      val cleanPartial = partial.trim()
      if (cleanPartial.isNotBlank() && isStartingOrActiveSession && !hasDeliveredResultForSession) {
        latestPartialForCurrentSession = cleanPartial
        _sessionState.value = VoiceSessionState.Partial(cleanPartial)
        // Reset initial silence watchdog and schedule post-speech silence completion timer
        resetPostSpeechSilenceWatchdog(currentSessionId)
      }
    },
    onListeningStateChanged = { listening ->
      if (!listening) {
        _voiceEnergy.value = 0f
        onVoiceEnergyChanged(0f)
      }
      onListeningStateChanged(listening)
      if (listening && isStartingOrActiveSession) {
        _sessionState.value = VoiceSessionState.Listening
      }
    },
    onVoiceRmsChanged = { energy ->
      _voiceEnergy.value = energy
      onVoiceEnergyChanged(energy)
    }
  )

  private val _sessionState = MutableStateFlow<VoiceSessionState>(VoiceSessionState.Idle)
  val sessionState: StateFlow<VoiceSessionState> = _sessionState.asStateFlow()

  @Volatile
  var activeEngineType: ActiveSttEngineType = ActiveSttEngineType.NONE
    private set

  @Volatile
  var activeEngineSelectionReason: String = "NONE"
    private set

  @Volatile
  private var isStartingOrActiveSession: Boolean = false

  @Volatile
  private var hasDeliveredResultForSession: Boolean = false

  @Volatile
  private var isExplicitlyStoppingOrPaused: Boolean = false

  @Volatile
  private var sessionTapStartMs: Long = 0L

  @Volatile
  private var hasSystemRecognizerEnteredReady: Boolean = false

  @Volatile
  private var hasLoggedFirstSystemAudioFrame: Boolean = false

  private var systemBindWatchdogRunnable: Runnable? = null
  private var initialSilenceRunnable: Runnable? = null
  private var postSpeechSilenceRunnable: Runnable? = null
  private var maxSessionDurationRunnable: Runnable? = null
  private var processingTimeoutRunnable: Runnable? = null

  private fun logDiag(message: String) {
    if (enableDiagnostics) {
      Log.d(TAG, "[VOICE-DIAG] [sid=$currentSessionId] $message")
    }
  }

  /**
   * Asynchronously pre-warms the installed local Vosk model into memory on a background thread
   * so that tapping the microphone on offline or Huawei HMS-only devices starts capturing audio
   * immediately (~15ms) instead of waiting 2-5 seconds for cold model deserialization.
   */
  fun prewarmInstalledOfflineModelAsync(languageLocale: String? = null) {
    val requestedLocale = (languageLocale ?: Locale.getDefault().language.ifBlank { "ar" }).trim()
    val installedModel = unifiedVoiceManager.getInstalledOfflineSttModel(requestedLocale)
      ?: unifiedVoiceManager.getInstalledOfflineSttModel(null)
      ?: return
    val resolvedDir = unifiedVoiceManager.modelManager.getModelDirectory(installedModel)
    if (voskRecognizer.isLoadedForDirectory(resolvedDir) || isLoadingVoskModel) {
      return
    }
    isLoadingVoskModel = true
    logDiag("Pre-warming local Vosk model asynchronously (model=${installedModel.id}, dir=${resolvedDir.name})")
    modelLoadExecutor.execute {
      val t0 = System.currentTimeMillis()
      try {
        val ok = voskRecognizer.loadModel(resolvedDir)
        val elapsed = System.currentTimeMillis() - t0
        logDiag("Background Vosk model pre-warm finished: success=$ok, elapsed=${elapsed}ms, path=${resolvedDir.absolutePath}")
      } finally {
        isLoadingVoskModel = false
      }
    }
  }

  fun attachActivity(activity: Context) {
    currentActivityContext = activity
  }

  fun detachActivity() {
    currentActivityContext = null
  }

  fun hasAudioPermission(): Boolean {
    val activeContext = currentActivityContext ?: context
    return ContextCompat.checkSelfPermission(
      activeContext,
      Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED
  }

  fun isNetworkConnected(): Boolean {
    val activeContext = currentActivityContext ?: context
    val cm = activeContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
    val activeNetwork = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
    if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return false
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    } else {
      true
    }
  }

  fun hasUnvalidatedNetworkInterface(): Boolean {
    val activeContext = currentActivityContext ?: context
    val cm = activeContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
    val activeNetwork = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
  }

  fun startListening(languageLocale: String? = null) {
    val action = Runnable {
      val requestedLocale = (languageLocale ?: Locale.getDefault().language.ifBlank { "ar" }).trim()
      val isArabicUi = Locale.getDefault().language.startsWith("ar") || requestedLocale.lowercase(Locale.ROOT).startsWith("ar")

      if (!hasAudioPermission()) {
        logDiag("startListening aborted: RECORD_AUDIO permission missing")
        _sessionState.value = VoiceSessionState.PermissionRequired
        return@Runnable
      }

      // Guard against duplicate concurrent listening sessions or parallel model loads
      if ((isStartingOrActiveSession || isLoadingVoskModel) &&
        (_sessionState.value is VoiceSessionState.Initializing || _sessionState.value is VoiceSessionState.Listening)
      ) {
        logDiag("startListening ignored: session already active (${_sessionState.value})")
        return@Runnable
      }

      // Refresh voice manager state
      val tapTimestampMs = System.currentTimeMillis()
      unifiedVoiceManager.refresh()
      val isValidatedOnline = isNetworkConnected()
      val hasRawNetwork = hasUnvalidatedNetworkInterface()
      val hasSystemRecognizer = unifiedVoiceManager.isSystemRecognitionAvailable()
      val hasUsableSystemRecognizer = unifiedVoiceManager.hasUsableSystemRecognizerForLanguage(requestedLocale)
      val installedModelForLang = unifiedVoiceManager.getInstalledOfflineSttModel(requestedLocale)
      val anyInstalledModel = unifiedVoiceManager.getInstalledOfflineSttModel(null)
      val installedServices = unifiedVoiceManager.findInstalledRecognitionServices()

      // Terminate any old session completely and assign a fresh monotonic session ID
      cancelInternal()
      val sessionId = sessionCounter.incrementAndGet()
      currentSessionId = sessionId
      sessionTapStartMs = tapTimestampMs
      currentRequestedLocale = requestedLocale
      hasFallbackOccurredForSession = false
      hasDeliveredResultForSession = false
      isExplicitlyStoppingOrPaused = false
      hasSystemRecognizerEnteredReady = false
      hasLoggedFirstSystemAudioFrame = false
      latestPartialForCurrentSession = ""
      isStartingOrActiveSession = true

      logDiag(
        "Mic tap startListening: device=${Build.MANUFACTURER}/${Build.MODEL}, lang=$requestedLocale, " +
          "validatedOnline=$isValidatedOnline, rawNet=$hasRawNetwork, sysAvailable=$hasSystemRecognizer, " +
          "sysUsableForLang=$hasUsableSystemRecognizer, services=$installedServices, " +
          "voskModel=${installedModelForLang?.id ?: "none"}"
      )

      // Decision Tree:
      // 1. Validated internet connection + usable system recognizer for requested language (Google / Samsung / AOSP) -> Use SYSTEM_ONLINE
      // 2. Installed local Vosk model for requested language (Offline, Unvalidated network, OR Huawei HMS-only device where system recognizer lacks Arabic support) -> Use VOSK_LOCAL_OFFLINE immediately
      // 3. System on-device recognizer available (Android 12+) -> Use SYSTEM_ON_DEVICE
      // 4. Validated or unvalidated network + system recognizer available (when no local Vosk model is installed, try system recognizer with fast bind watchdog) -> Use SYSTEM_ONLINE
      // 5. Otherwise -> Inform user accurately
      if (isValidatedOnline && hasUsableSystemRecognizer) {
        activeEngineSelectionReason = "VALIDATED_ONLINE_AND_USABLE_SYSTEM_RECOGNIZER"
        logDiag("Selected engine=SYSTEM_ONLINE reason=$activeEngineSelectionReason lang=$requestedLocale")
        startSystemListening(sessionId, requestedLocale, preferOffline = false, shortConnectTimeout = false)
      } else if (installedModelForLang != null) {
        activeEngineSelectionReason = when {
          !isValidatedOnline -> "OFFLINE_WITH_MATCHING_VOSK_MODEL"
          !hasSystemRecognizer -> "NO_SYSTEM_RECOGNIZER_USING_VOSK_MODEL"
          else -> "OEM_HMS_UNSUPPORTED_SYSTEM_RECOGNIZER_USING_VOSK_MODEL"
        }
        logDiag("Selected engine=VOSK_LOCAL_OFFLINE reason=$activeEngineSelectionReason model=${installedModelForLang.id} lang=$requestedLocale")
        val resolvedDir = unifiedVoiceManager.modelManager.getModelDirectory(installedModelForLang)
        startVoskListening(sessionId, resolvedDir)
      } else if (unifiedVoiceManager.isSystemOnDeviceRecognitionAvailable()) {
        activeEngineSelectionReason = "OFFLINE_SYSTEM_ON_DEVICE_RECOGNIZER"
        logDiag("Selected engine=SYSTEM_ON_DEVICE reason=$activeEngineSelectionReason lang=$requestedLocale")
        startSystemListening(sessionId, requestedLocale, preferOffline = true, shortConnectTimeout = false)
      } else if ((isValidatedOnline || hasRawNetwork) && hasSystemRecognizer) {
        activeEngineSelectionReason = if (isValidatedOnline) {
          "ONLINE_NON_STANDARD_SYSTEM_RECOGNIZER_NO_LOCAL_MODEL"
        } else {
          "UNVALIDATED_NETWORK_TRYING_SYSTEM_ONLINE"
        }
        logDiag("Selected engine=SYSTEM_ONLINE (fallback attempt) reason=$activeEngineSelectionReason lang=$requestedLocale")
        startSystemListening(sessionId, requestedLocale, preferOffline = false, shortConnectTimeout = true)
      } else {
        activeEngineType = ActiveSttEngineType.NONE
        val isRequestedEnglish = requestedLocale.lowercase(Locale.ROOT).startsWith("en")
        activeEngineSelectionReason = if (isRequestedEnglish && anyInstalledModel != null) {
          "NO_ENGLISH_OFFLINE_MODEL_INSTALLED"
        } else {
          "NO_AVAILABLE_ENGINE"
        }
        isStartingOrActiveSession = false
        val msg = if (isRequestedEnglish && anyInstalledModel != null) {
          if (isArabicUi) {
            "التعرف الصوتي الإنجليزي بدون إنترنت غير متوفر حالياً (النموذج المحلي المثبت مخصص للغة العربية فقط)."
          } else {
            "Offline English recognition is unavailable: Only Arabic offline voice models are installed."
          }
        } else if (isArabicUi) {
          if (!isValidatedOnline) {
            "التعرف الصوتي بدون إنترنت غير جاهز: لم يتم تثبيت نموذج محلي صالح. يرجى تنزيل وتثبيت نموذج الصوت العربي من الإعدادات."
          } else {
            "لا توجد خدمة تعرف صوتي مثبتة على هذا الجهاز."
          }
        } else {
          if (!isValidatedOnline) {
            "Offline recognition is not ready: No valid local model installed. Please download and install the voice model in Settings."
          } else {
            "No speech recognition service found on this device."
          }
        }
        logDiag("startListening failed: $activeEngineSelectionReason")
        _sessionState.value = VoiceSessionState.Error(msg)
        onListeningStateChanged(false)
      }
    }
    if (Looper.myLooper() == Looper.getMainLooper()) {
      action.run()
    } else {
      mainHandler.post(action)
    }
  }

  private fun startSystemListening(
    sessionId: Long,
    languageLocale: String?,
    preferOffline: Boolean,
    shortConnectTimeout: Boolean
  ) {
    if (sessionId != currentSessionId) return
    val activeContext = currentActivityContext ?: context
    activeEngineType = if (preferOffline) ActiveSttEngineType.SYSTEM_ON_DEVICE else ActiveSttEngineType.SYSTEM_ONLINE

    releaseSystemRecognizerInternal()

    speechRecognizer = try {
      if (preferOffline && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        SpeechRecognizer.isOnDeviceRecognitionAvailable(activeContext)
      ) {
        SpeechRecognizer.createOnDeviceSpeechRecognizer(activeContext).apply {
          setRecognitionListener(this@VoicePipeline)
        }
      } else {
        SpeechRecognizer.createSpeechRecognizer(activeContext).apply {
          setRecognitionListener(this@VoicePipeline)
        }
      }
    } catch (e: Exception) {
      logDiag("Failed creating system SpeechRecognizer: ${e.message}")
      null
    }

    if (speechRecognizer == null) {
      if (tryFallbackToLocalVosk(sessionId, "FALLBACK_TO_VOSK_AFTER_NULL_SYSTEM_RECOGNIZER")) {
        return
      }
      isStartingOrActiveSession = false
      _sessionState.value = VoiceSessionState.Error("تعذر تهيئة محرك التعرف الصوتي.")
      onListeningStateChanged(false)
      return
    }

    try {
      _sessionState.value = VoiceSessionState.Initializing
      val preferredLanguage = when {
        languageLocale.isNullOrBlank() -> "ar-SA"
        languageLocale.equals("ar", ignoreCase = true) -> "ar-SA"
        else -> languageLocale
      }
      val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, activeContext.packageName)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, preferOffline)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, preferredLanguage)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, preferredLanguage)
      }
      speechRecognizer?.startListening(intent)
      logDiag("System SpeechRecognizer.startListening invoked (lang=$preferredLanguage, preferOffline=$preferOffline)")
      scheduleSystemRecognizerBindWatchdog(sessionId, SYSTEM_RECOGNIZER_BIND_TIMEOUT_MS)
      val initialTimeout = if (shortConnectTimeout) UNVALIDATED_NETWORK_CONNECT_TIMEOUT_MS else INITIAL_SPEECH_TIMEOUT_MS
      scheduleInitialSilenceWatchdog(sessionId, initialTimeout)
    } catch (e: Exception) {
      Log.e(TAG, "Failed startListening on system recognizer", e)
      releaseSystemRecognizerInternal()
      if (tryFallbackToLocalVosk(sessionId, "FALLBACK_TO_VOSK_AFTER_START_EXCEPTION")) {
        return
      }
      isStartingOrActiveSession = false
      _sessionState.value = VoiceSessionState.Error("فشل بدء الاستماع: ${e.message}")
      onListeningStateChanged(false)
    }
  }

  private fun startVoskListening(sessionId: Long, modelDir: File) {
    if (sessionId != currentSessionId) return
    activeEngineType = ActiveSttEngineType.VOSK_LOCAL_OFFLINE
    _sessionState.value = VoiceSessionState.Initializing

    // Fast path: if the exact requested model directory is already loaded in memory, start immediately on Main Thread
    if (voskRecognizer.isLoadedForDirectory(modelDir)) {
      logDiag("Vosk model already warm in memory (path=${voskRecognizer.loadedModelPath}); starting capture immediately")
      beginVoskCaptureForLoadedModel(sessionId)
      return
    }

    // Slow path: Load heavy Vosk model off the Main Thread to prevent UI freeze / ANR
    isLoadingVoskModel = true
    val loadStartMs = System.currentTimeMillis()
    logDiag("Loading Vosk model asynchronously on background executor (dir=${modelDir.absolutePath}, lang=$currentRequestedLocale)")
    modelLoadExecutor.execute {
      val loaded = voskRecognizer.loadModel(modelDir)
      val loadElapsedMs = System.currentTimeMillis() - loadStartMs
      mainHandler.post {
        isLoadingVoskModel = false
        // Verify the session wasn't cancelled or superseded while the model was loading
        if (sessionId != currentSessionId || isExplicitlyStoppingOrPaused || !isStartingOrActiveSession) {
          logDiag("Vosk model load finished (${loadElapsedMs}ms) after session $sessionId ended; aborting capture start")
          return@post
        }
        if (!loaded) {
          isStartingOrActiveSession = false
          logDiag("Vosk model load failed from ${modelDir.absolutePath} after ${loadElapsedMs}ms")
          _sessionState.value = VoiceSessionState.Error("تعذر تحميل نموذج الصوت المحلي Vosk. تأكد من سلامة ملفات النموذج.")
          onListeningStateChanged(false)
          return@post
        }
        logDiag("Vosk model load succeeded in ${loadElapsedMs}ms (resolvedPath=${voskRecognizer.loadedModelPath})")
        beginVoskCaptureForLoadedModel(sessionId)
      }
    }
  }

  private fun beginVoskCaptureForLoadedModel(sessionId: Long) {
    if (sessionId != currentSessionId || isExplicitlyStoppingOrPaused) return
    val started = voskRecognizer.startListening(tapTimestampMs = sessionTapStartMs)
    if (!started) {
      isStartingOrActiveSession = false
      logDiag("Vosk startListening returned false")
      _sessionState.value = VoiceSessionState.Error("تعذر تشغيل الميكروفون للمحرك المحلي.")
      onListeningStateChanged(false)
    } else {
      val tapToListenMs = (System.currentTimeMillis() - sessionTapStartMs).coerceAtLeast(0L)
      logDiag("Vosk listening started successfully (tapToListeningMs=${tapToListenMs}ms)")
      scheduleInitialSilenceWatchdog(sessionId, INITIAL_SPEECH_TIMEOUT_MS)
      scheduleMaxSessionDurationWatchdog(sessionId, MAX_UTTERANCE_DURATION_MS)
    }
  }

  /**
   * Attempts a single per-session transition from failed network/system recognition to local Vosk
   * if a valid local model matching the requested language is installed.
   */
  private fun tryFallbackToLocalVosk(sessionId: Long, reason: String): Boolean {
    if (sessionId != currentSessionId || hasFallbackOccurredForSession || isExplicitlyStoppingOrPaused) {
      return false
    }
    val installedModel = unifiedVoiceManager.getInstalledOfflineSttModel(currentRequestedLocale)
      ?: return false

    hasFallbackOccurredForSession = true
    clearWatchdogs()
    releaseSystemRecognizerInternal()
    isStartingOrActiveSession = true
    activeEngineSelectionReason = reason
    logDiag("Transitioning to local Vosk engine (reason=$reason, model=${installedModel.id})")
    val resolvedDir = unifiedVoiceManager.modelManager.getModelDirectory(installedModel)
    startVoskListening(sessionId, resolvedDir)
    return true
  }

  fun stopListening() {
    val action = Runnable {
      val sessionId = currentSessionId
      logDiag("stopListening() invoked (activeEngine=$activeEngineType)")
      isExplicitlyStoppingOrPaused = true
      isStartingOrActiveSession = false
      clearWatchdogs()

      if (activeEngineType == ActiveSttEngineType.VOSK_LOCAL_OFFLINE) {
        // Flush any final transcript from Vosk before closing Recognizer
        val delivered = voskRecognizer.stopListening()
        if (!delivered && !hasDeliveredResultForSession && latestPartialForCurrentSession.isNotBlank()) {
          handleSuccessfulTranscript(latestPartialForCurrentSession, sessionId)
          return@Runnable
        }
      } else {
        try {
          speechRecognizer?.stopListening()
        } catch (_: Exception) {}
        voskRecognizer.stopListening()
      }

      if (!hasDeliveredResultForSession &&
        (_sessionState.value is VoiceSessionState.Listening ||
          _sessionState.value is VoiceSessionState.Initializing ||
          _sessionState.value is VoiceSessionState.Partial)
      ) {
        _sessionState.value = VoiceSessionState.Idle
      }
      onListeningStateChanged(false)
    }
    if (Looper.myLooper() == Looper.getMainLooper()) {
      action.run()
    } else {
      mainHandler.post(action)
    }
  }

  fun cancel() {
    val action = Runnable {
      logDiag("cancel() invoked (activeEngine=$activeEngineType)")
      cancelInternal()
    }
    if (Looper.myLooper() == Looper.getMainLooper()) {
      action.run()
    } else {
      mainHandler.post(action)
    }
  }

  private fun releaseSystemRecognizerInternal() {
    val sr = speechRecognizer ?: return
    speechRecognizer = null
    try {
      sr.cancel()
    } catch (_: Exception) {}
    try {
      sr.destroy()
      logDiag("System SpeechRecognizer destroyed and released")
    } catch (_: Exception) {}
  }

  private fun cancelInternal() {
    sessionCounter.incrementAndGet()
    currentSessionId = sessionCounter.get()
    isExplicitlyStoppingOrPaused = true
    isStartingOrActiveSession = false
    isLoadingVoskModel = false
    latestPartialForCurrentSession = ""
    clearWatchdogs()
    releaseSystemRecognizerInternal()
    voskRecognizer.cancel()
    _sessionState.value = VoiceSessionState.Idle
    onListeningStateChanged(false)
  }

  fun destroy() {
    val action = Runnable {
      logDiag("destroy() invoked")
      sessionCounter.incrementAndGet()
      currentSessionId = sessionCounter.get()
      isStartingOrActiveSession = false
      isLoadingVoskModel = false
      latestPartialForCurrentSession = ""
      clearWatchdogs()
      releaseSystemRecognizerInternal()
      voskRecognizer.destroy()
      currentActivityContext = null
      _sessionState.value = VoiceSessionState.Idle
      onListeningStateChanged(false)
    }
    if (Looper.myLooper() == Looper.getMainLooper()) {
      action.run()
    } else {
      mainHandler.post(action)
    }
  }

  private fun clearWatchdogs() {
    systemBindWatchdogRunnable?.let { mainHandler.removeCallbacks(it) }
    initialSilenceRunnable?.let { mainHandler.removeCallbacks(it) }
    postSpeechSilenceRunnable?.let { mainHandler.removeCallbacks(it) }
    maxSessionDurationRunnable?.let { mainHandler.removeCallbacks(it) }
    processingTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
    systemBindWatchdogRunnable = null
    initialSilenceRunnable = null
    postSpeechSilenceRunnable = null
    maxSessionDurationRunnable = null
    processingTimeoutRunnable = null
  }

  /**
   * Fast watchdog (2.2s) for OEM devices (such as Huawei HMS-only phones) where
   * SpeechRecognizer.startListening() silently hangs without ever invoking onReadyForSpeech() or onError().
   */
  private fun scheduleSystemRecognizerBindWatchdog(sessionId: Long, timeoutMs: Long) {
    systemBindWatchdogRunnable?.let { mainHandler.removeCallbacks(it) }
    val runnable = Runnable {
      if (sessionId != currentSessionId || !isStartingOrActiveSession || hasSystemRecognizerEnteredReady) return@Runnable
      logDiag("VOICE_TIMEOUT: System SpeechRecognizer failed to bind/enter onReadyForSpeech within ${timeoutMs}ms")
      if (tryFallbackToLocalVosk(sessionId, "SYSTEM_RECOGNIZER_BIND_TIMEOUT_FALLBACK_TO_VOSK")) {
        return@Runnable
      }
    }
    systemBindWatchdogRunnable = runnable
    mainHandler.postDelayed(runnable, timeoutMs)
  }

  private fun scheduleInitialSilenceWatchdog(sessionId: Long, timeoutMs: Long) {
    initialSilenceRunnable?.let { mainHandler.removeCallbacks(it) }
    val runnable = Runnable {
      if (sessionId != currentSessionId || !isStartingOrActiveSession) return@Runnable
      logDiag("VOICE_TIMEOUT: Initial silence timeout (${timeoutMs}ms, engine=$activeEngineType)")
      if (activeEngineType == ActiveSttEngineType.SYSTEM_ONLINE &&
        tryFallbackToLocalVosk(sessionId, "INITIAL_TIMEOUT_ON_SYSTEM_ONLINE_FALLBACK_TO_VOSK")
      ) {
        return@Runnable
      }
      if (activeEngineType == ActiveSttEngineType.VOSK_LOCAL_OFFLINE) {
        finishVoskSessionOnSilence(sessionId)
      } else {
        cancelInternal()
      }
    }
    initialSilenceRunnable = runnable
    mainHandler.postDelayed(runnable, timeoutMs)
  }

  private fun resetPostSpeechSilenceWatchdog(sessionId: Long) {
    if (sessionId != currentSessionId) return
    initialSilenceRunnable?.let { mainHandler.removeCallbacks(it) }
    initialSilenceRunnable = null
    postSpeechSilenceRunnable?.let { mainHandler.removeCallbacks(it) }

    val runnable = Runnable {
      if (sessionId != currentSessionId || !isStartingOrActiveSession) return@Runnable
      logDiag("VOICE_TIMEOUT: Post-speech silence complete (${POST_SPEECH_SILENCE_TIMEOUT_MS}ms, engine=$activeEngineType); finalizing utterance")
      if (activeEngineType == ActiveSttEngineType.VOSK_LOCAL_OFFLINE) {
        finishVoskSessionOnSilence(sessionId)
      }
    }
    postSpeechSilenceRunnable = runnable
    mainHandler.postDelayed(runnable, POST_SPEECH_SILENCE_TIMEOUT_MS)
  }

  private fun scheduleMaxSessionDurationWatchdog(sessionId: Long, maxDurationMs: Long) {
    maxSessionDurationRunnable?.let { mainHandler.removeCallbacks(it) }
    val runnable = Runnable {
      if (sessionId != currentSessionId || !isStartingOrActiveSession) return@Runnable
      logDiag("VOICE_TIMEOUT: Max utterance duration reached (${maxDurationMs}ms, engine=$activeEngineType); finalizing utterance")
      if (activeEngineType == ActiveSttEngineType.VOSK_LOCAL_OFFLINE) {
        finishVoskSessionOnSilence(sessionId)
      } else {
        stopListening()
      }
    }
    maxSessionDurationRunnable = runnable
    mainHandler.postDelayed(runnable, maxDurationMs)
  }

  private fun scheduleProcessingWatchdog(sessionId: Long, timeoutMs: Long) {
    processingTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
    val runnable = Runnable {
      if (sessionId != currentSessionId) return@Runnable
      logDiag("VOICE_TIMEOUT: Processing timeout (${timeoutMs}ms, engine=$activeEngineType)")
      if (activeEngineType == ActiveSttEngineType.SYSTEM_ONLINE &&
        latestPartialForCurrentSession.isBlank() &&
        tryFallbackToLocalVosk(sessionId, "PROCESSING_TIMEOUT_FALLBACK_TO_VOSK")
      ) {
        return@Runnable
      }
      if (latestPartialForCurrentSession.isNotBlank() && !hasDeliveredResultForSession) {
        handleSuccessfulTranscript(latestPartialForCurrentSession, sessionId)
      } else {
        cancelInternal()
      }
    }
    processingTimeoutRunnable = runnable
    mainHandler.postDelayed(runnable, timeoutMs)
  }

  private fun finishVoskSessionOnSilence(sessionId: Long) {
    if (sessionId != currentSessionId) return
    clearWatchdogs()
    isStartingOrActiveSession = false
    val delivered = voskRecognizer.stopListening()
    if (!delivered && !hasDeliveredResultForSession) {
      if (latestPartialForCurrentSession.isNotBlank()) {
        handleSuccessfulTranscript(latestPartialForCurrentSession, sessionId)
      } else {
        _sessionState.value = VoiceSessionState.Idle
        onListeningStateChanged(false)
      }
    }
  }

  private fun handleSuccessfulTranscript(text: String, sessionId: Long = currentSessionId) {
    if (sessionId != currentSessionId) {
      logDiag("Ignored transcript from stale session $sessionId (active=$currentSessionId)")
      return
    }
    if (hasDeliveredResultForSession) {
      logDiag("Ignored duplicate transcript callback for session $sessionId")
      return
    }
    clearWatchdogs()
    isStartingOrActiveSession = false

    val clean = text.trim()
    if (clean.isNotBlank()) {
      hasDeliveredResultForSession = true
      latestPartialForCurrentSession = ""
      releaseSystemRecognizerInternal()
      voskRecognizer.cancel()
      logDiag("Transcript recognized (via $activeEngineType, chars=${clean.length})")
      _sessionState.value = VoiceSessionState.Success(clean)
      onTranscriptReady(clean)
    } else {
      releaseSystemRecognizerInternal()
      voskRecognizer.cancel()
      _sessionState.value = VoiceSessionState.Error("لم يتم التقاط أي كلمات.")
    }
    onListeningStateChanged(false)
  }

  // --- RecognitionListener Callbacks (System SpeechRecognizer) ---

  override fun onReadyForSpeech(params: Bundle?) {
    hasSystemRecognizerEnteredReady = true
    systemBindWatchdogRunnable?.let { mainHandler.removeCallbacks(it) }
    systemBindWatchdogRunnable = null
    val readyLatencyMs = if (sessionTapStartMs > 0L) (System.currentTimeMillis() - sessionTapStartMs).coerceAtLeast(0L) else 0L
    logDiag("System SpeechRecognizer onReadyForSpeech (tapToReadyMs=${readyLatencyMs}ms)")
    isStartingOrActiveSession = true
    _sessionState.value = VoiceSessionState.Listening
    onListeningStateChanged(true)
  }

  override fun onBeginningOfSpeech() {
    hasSystemRecognizerEnteredReady = true
    systemBindWatchdogRunnable?.let { mainHandler.removeCallbacks(it) }
    systemBindWatchdogRunnable = null
    initialSilenceRunnable?.let { mainHandler.removeCallbacks(it) }
    initialSilenceRunnable = null
    val speechStartLatencyMs = if (sessionTapStartMs > 0L) (System.currentTimeMillis() - sessionTapStartMs).coerceAtLeast(0L) else 0L
    logDiag("System SpeechRecognizer onBeginningOfSpeech (tapToSpeechStartMs=${speechStartLatencyMs}ms)")
    _sessionState.value = VoiceSessionState.Listening
    onListeningStateChanged(true)
  }

  override fun onRmsChanged(rmsdB: Float) {
    // Android SpeechRecognizer rmsdB typically ranges from -2.0f to +10.0f
    val normalized = ((rmsdB + 2.0f) / 12.0f).coerceIn(0f, 1f)
    if (!hasLoggedFirstSystemAudioFrame && normalized > 0.08f) {
      hasLoggedFirstSystemAudioFrame = true
      val firstSignalMs = if (sessionTapStartMs > 0L) (System.currentTimeMillis() - sessionTapStartMs).coerceAtLeast(0L) else 0L
      logDiag("System SpeechRecognizer first non-silent audio level received (tapToSignalMs=${firstSignalMs}ms, rmsdB=${"%.2f".format(rmsdB)}, norm=${"%.2f".format(normalized)})")
    }
    _voiceEnergy.value = normalized
    onVoiceEnergyChanged(normalized)
  }

  override fun onBufferReceived(buffer: ByteArray?) {}

  override fun onEndOfSpeech() {
    logDiag("System SpeechRecognizer onEndOfSpeech -> Processing")
    clearWatchdogs()
    _voiceEnergy.value = 0f
    onVoiceEnergyChanged(0f)
    _sessionState.value = VoiceSessionState.Processing
    onListeningStateChanged(false)
    scheduleProcessingWatchdog(currentSessionId, SYSTEM_PROCESSING_TIMEOUT_MS)
  }

  /**
   * Distinguishes network/server/offline-language pack failures from user cancellation,
   * audio hardware issues, permission denials, or genuine speech no-match.
   */
  internal fun isFallbackEligibleSystemError(error: Int): Boolean {
    return when (error) {
      SpeechRecognizer.ERROR_NETWORK,
      SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
      SpeechRecognizer.ERROR_SERVER,
      ERROR_SERVER_DISCONNECTED,
      ERROR_LANGUAGE_NOT_SUPPORTED,
      ERROR_LANGUAGE_UNAVAILABLE -> true
      // If speech timeout occurs while the network is not actually validated, it's a hung online recognizer
      SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> !isNetworkConnected()
      else -> false
    }
  }

  override fun onError(error: Int) {
    val sessionId = currentSessionId
    clearWatchdogs()
    isStartingOrActiveSession = false
    val isArabic = Locale.getDefault().language.startsWith("ar")
    logDiag("System SpeechRecognizer onError(code=$error, isExplicitlyStopping=$isExplicitlyStoppingOrPaused, hasDelivered=$hasDeliveredResultForSession)")

    if (hasDeliveredResultForSession) {
      onListeningStateChanged(false)
      return
    }

    if (error == SpeechRecognizer.ERROR_CLIENT && isExplicitlyStoppingOrPaused) {
      _sessionState.value = VoiceSessionState.Idle
      onListeningStateChanged(false)
      return
    }

    // If we already captured a valid partial transcript before a network/timeout hiccup at the end of speech, deliver it rather than dropping the user's words
    if (latestPartialForCurrentSession.isNotBlank() &&
      (error == SpeechRecognizer.ERROR_NETWORK ||
        error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT ||
        error == SpeechRecognizer.ERROR_SERVER ||
        error == SpeechRecognizer.ERROR_NO_MATCH)
    ) {
      logDiag("Recovering completed partial transcript after system code=$error")
      handleSuccessfulTranscript(latestPartialForCurrentSession, sessionId)
      return
    }

    // MID-STREAM NETWORK / SERVER / OFFLINE-PACK FAILURE: Switch cleanly once to Vosk if installed for requested language
    if (isFallbackEligibleSystemError(error) && !isExplicitlyStoppingOrPaused) {
      if (tryFallbackToLocalVosk(sessionId, "SYSTEM_ERROR_${error}_FALLBACK_TO_VOSK")) {
        return
      }
    }

    releaseSystemRecognizerInternal()

    val message = when (error) {
      SpeechRecognizer.ERROR_AUDIO -> if (isArabic) "خطأ في تسجيل الصوت من الميكروفون." else "Audio recording error."
      SpeechRecognizer.ERROR_CLIENT -> if (isArabic) "تم إلغاء جلسة الاستماع." else "Client error."
      SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> if (isArabic) "صلاحية الميكروفون مطلوبة." else "Microphone permission required."
      SpeechRecognizer.ERROR_NETWORK,
      SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
      SpeechRecognizer.ERROR_SERVER,
      ERROR_SERVER_DISCONNECTED -> {
        if (isArabic) "تعذر الاتصال بخدمة التعرف الصوتي عبر الشبكة. ثبّت نموذج الصوت المحلي من الإعدادات للتحدث بدون إنترنت."
        else "Network speech service unreachable. Install a local voice model in Settings to speak offline."
      }
      SpeechRecognizer.ERROR_NO_MATCH,
      SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> if (isArabic) "لم أتمكن من تمييز الكلام، حاول التحدث بوضوح." else "No speech recognized."
      SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> if (isArabic) "محرك الصوت مشغول حالياً." else "Recognizer busy."
      ERROR_LANGUAGE_NOT_SUPPORTED,
      ERROR_LANGUAGE_UNAVAILABLE -> if (isArabic) "حزمة اللغة المطلوبة غير متوفرة بدون إنترنت." else "Requested language pack is unavailable offline."
      else -> if (isArabic) "تعذر التعرف على الصوت (خطأ $error)." else "Speech recognition error ($error)."
    }

    _sessionState.value = VoiceSessionState.Error(message)
    onListeningStateChanged(false)
  }

  override fun onResults(results: Bundle?) {
    val sessionId = currentSessionId
    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
    val bestMatch = matches?.firstOrNull()?.trim().orEmpty().ifBlank { latestPartialForCurrentSession.trim() }
    if (bestMatch.isNotBlank()) {
      handleSuccessfulTranscript(bestMatch, sessionId)
    } else {
      isStartingOrActiveSession = false
      releaseSystemRecognizerInternal()
      _sessionState.value = VoiceSessionState.Error(if (Locale.getDefault().language.startsWith("ar")) "لم يتم التعرف على كلمات." else "No words detected.")
      onListeningStateChanged(false)
    }
  }

  override fun onPartialResults(partialResults: Bundle?) {
    if (hasDeliveredResultForSession || isExplicitlyStoppingOrPaused) return
    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
    val partial = matches?.firstOrNull()?.trim()
    if (!partial.isNullOrBlank()) {
      latestPartialForCurrentSession = partial
      _sessionState.value = VoiceSessionState.Partial(partial)
      initialSilenceRunnable?.let { mainHandler.removeCallbacks(it) }
      initialSilenceRunnable = null
    }
  }

  override fun onEvent(eventType: Int, params: Bundle?) {}
}
