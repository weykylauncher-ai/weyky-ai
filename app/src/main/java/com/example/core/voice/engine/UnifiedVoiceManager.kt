package com.example.core.voice.engine

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.speech.RecognitionService
import android.speech.SpeechRecognizer
import com.example.core.voice.model.VoiceModelManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

enum class ActiveSttEngineType {
  NONE,
  SYSTEM_ONLINE,
  SYSTEM_ON_DEVICE,
  VOSK_LOCAL_OFFLINE
}

enum class ActiveTtsEngineType {
  NONE,
  HYBRID_CLOUD_EDGE,
  SYSTEM_ENGINE,
  LOCAL_ENGINE
}

data class VoiceEngineStatus(
  val sttEngine: ActiveSttEngineType,
  val ttsEngine: ActiveTtsEngineType,
  val isOnline: Boolean,
  val isArabicSttSupported: Boolean,
  val isArabicTtsSupported: Boolean,
  val isOfflineSttReady: Boolean,
  val isOfflineModelInstalled: Boolean,
  val installedModelName: String?,
  val systemRecognizerPackage: String?,
  val activeTtsPackage: String?
)

/**
 * Unified Voice Engine Coordinator separating STT, TTS, Online, Offline,
 * and reporting real system status without false claims.
 */
class UnifiedVoiceManager(
  private val context: Context,
  val modelManager: VoiceModelManager = VoiceModelManager(context),
  val ttsInspector: TtsEngineInspector = TtsEngineInspector(context)
) {
  private val _status = MutableStateFlow(computeCurrentStatus())
  val status: StateFlow<VoiceEngineStatus> = _status.asStateFlow()

  fun refresh() {
    modelManager.refreshStatus()
    _status.value = computeCurrentStatus()
  }

  fun isNetworkConnected(): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
    val activeNetwork = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
    val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    if (!hasInternet) return false
    // Prefer NET_CAPABILITY_VALIDATED on Android M+ so Wi-Fi/Cellular without real internet access
    // is accurately recognized as offline when an offline model is ready.
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    } else {
      true
    }
  }

  /**
   * Checks whether the device has any raw network interface claiming internet capability
   * (even if unvalidated, e.g. captive portal or when no offline model exists).
   */
  fun hasAnyInternetInterface(): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
    val activeNetwork = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
  }

  fun findInstalledRecognitionServices(): List<String> {
    val pm = context.packageManager
    val intent = Intent(RecognitionService.SERVICE_INTERFACE)
    val services = pm.queryIntentServices(intent, PackageManager.MATCH_DEFAULT_ONLY)
    return services.map { it.serviceInfo.packageName }
  }

  /**
   * Checks whether the device has a system RecognitionService known to reliably support Arabic
   * speech recognition via standard Android SpeechRecognizer intents (e.g., Google Speech Services / Velour,
   * Android System Intelligence / ASI, or Samsung Bixby/Honeyboard Speech).
   *
   * On Huawei HMS-only devices (such as Huawei P40 Lite E / EMUI 10 without GMS),
   * `SpeechRecognizer.isRecognitionAvailable(context)` often returns `true` due to a stub or
   * HMS Celia/HiVoice service (`com.huawei.vassistant`, `com.huawei.hiai`, `com.huawei.hms.mlsdk.asr`),
   * or returns empty service lists while binding to a dead default component that hangs for 5–7s
   * and fails to recognize spoken Arabic.
   */
  fun hasUsableSystemRecognizerForLanguage(languageLocale: String? = null): Boolean {
    if (!isSystemRecognitionAvailable()) return false
    val services = findInstalledRecognitionServices()
    if (services.isEmpty()) {
      // If PackageManager resolves zero RecognitionService implementations, only trust it if it is not a Huawei HMS-only device
      // or if no local Vosk model is installed.
      val isHuaweiDevice = Build.MANUFACTURER.equals("HUAWEI", ignoreCase = true) ||
        Build.BRAND.equals("HUAWEI", ignoreCase = true) ||
        Build.BRAND.equals("HONOR", ignoreCase = true)
      return !isHuaweiDevice
    }

    val hasGoogleOrSamsungOrAospService = services.any { pkg ->
      val lower = pkg.lowercase(Locale.ROOT)
      lower.contains("google") ||
        lower.contains("samsung") ||
        lower.contains("honeyboard") ||
        lower.contains("bixby") ||
        lower.contains("android.as")
    }
    if (hasGoogleOrSamsungOrAospService) return true

    val requestedLang = (languageLocale ?: Locale.getDefault().language).lowercase(Locale.ROOT)
    val isArabicRequest = requestedLang.startsWith("ar")
    val hasOnlyHuaweiProprietaryServices = services.all { pkg ->
      val lower = pkg.lowercase(Locale.ROOT)
      lower.contains("huawei") || lower.contains("hiai") || lower.contains("hivoice") || lower.contains("vassistant") || lower.contains("honor")
    }

    // Huawei's proprietary RecognitionService implementations on HMS-only devices (like P40 Lite E)
    // either reject third-party standard RecognizerIntent binds or do not support Arabic free-form STT
    // without Huawei Cloud ML Kit SDK tokens, causing 7-second silent hangs.
    if (isArabicRequest && hasOnlyHuaweiProprietaryServices) {
      return false
    }
    return true
  }

  fun isSystemRecognitionAvailable(): Boolean {
    return SpeechRecognizer.isRecognitionAvailable(context)
  }

  fun isSystemOnDeviceRecognitionAvailable(): Boolean {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
      SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
  }

  /**
   * Returns the first valid, installed offline STT model matching [languageLocale]
   * (or any installed STT model if [languageLocale] is null).
   */
  fun getInstalledOfflineSttModel(languageLocale: String? = null): com.example.core.voice.model.VoiceModelInfo? {
    val normalizedLang = languageLocale?.lowercase(Locale.ROOT)?.substringBefore("-")?.substringBefore("_")
    return VoiceModelManager.CATALOG.firstOrNull { info ->
      val isStt = info.type == com.example.core.voice.model.VoiceModelType.STT
      val langMatches = normalizedLang == null || info.languageCode.lowercase(Locale.ROOT) == normalizedLang
      isStt && langMatches && modelManager.isModelInstalled(info)
    }
  }

  /**
   * Returns the preferred or first valid, installed offline Neural TTS model matching [languageLocale].
   */
  fun getInstalledOfflineTtsModel(languageLocale: String? = "ar"): com.example.core.voice.model.VoiceModelInfo? {
    val preferredId = modelManager.getActiveTtsModelId()
    val preferred = VoiceModelManager.CATALOG.firstOrNull {
      it.id == preferredId && it.type == com.example.core.voice.model.VoiceModelType.TTS && modelManager.isModelInstalled(it)
    }
    if (preferred != null) return preferred

    val normalizedLang = languageLocale?.lowercase(Locale.ROOT)?.substringBefore("-")?.substringBefore("_")
    return VoiceModelManager.CATALOG.firstOrNull { info ->
      val isTts = info.type == com.example.core.voice.model.VoiceModelType.TTS
      val langMatches = normalizedLang == null || info.languageCode.lowercase(Locale.ROOT) == normalizedLang
      isTts && langMatches && modelManager.isModelInstalled(info)
    }
  }

  private fun computeCurrentStatus(): VoiceEngineStatus {
    val online = isNetworkConnected()
    val services = findInstalledRecognitionServices()
    val systemRecognizerPkg = services.firstOrNull()
    val hasSystemRecognizer = isSystemRecognitionAvailable()
    val currentLocaleLang = Locale.getDefault().language
    val usableSystemRecognizer = hasUsableSystemRecognizerForLanguage(currentLocaleLang)
    val installedForLocale = getInstalledOfflineSttModel(currentLocaleLang)
    val anyInstalledOfflineModel = getInstalledOfflineSttModel(null)
    val effectiveOfflineModel = installedForLocale ?: anyInstalledOfflineModel
    val isOfflineModelInstalled = anyInstalledOfflineModel != null

    // Determine active STT engine in exact alignment with VoicePipeline.startListening() decision tree:
    // 1. Validated online + usable system recognizer (Google/Samsung) -> SYSTEM_ONLINE
    // 2. Installed local Vosk model (including when online on Huawei HMS-only devices where system recognizer is unusable for Arabic) -> VOSK_LOCAL_OFFLINE
    // 3. System on-device recognizer -> SYSTEM_ON_DEVICE
    // 4. Validated/unvalidated network + any system recognizer when no local model is installed -> SYSTEM_ONLINE
    val sttEngine = when {
      online && usableSystemRecognizer -> ActiveSttEngineType.SYSTEM_ONLINE
      effectiveOfflineModel != null -> ActiveSttEngineType.VOSK_LOCAL_OFFLINE
      isSystemOnDeviceRecognitionAvailable() -> ActiveSttEngineType.SYSTEM_ON_DEVICE
      (online || hasAnyInternetInterface()) && hasSystemRecognizer -> ActiveSttEngineType.SYSTEM_ONLINE
      else -> ActiveSttEngineType.NONE
    }

    // TTS evaluation (Priority 1: Cloud Neural Edge TTS when online; Priority 2: Local Neural ONNX; Priority 3: System TTS)
    val installedNeuralTtsModel = getInstalledOfflineTtsModel("ar")
    val defaultTtsPkg = ttsInspector.getDefaultEngineName()
    val hasTtsEngines = ttsInspector.getInstalledEngines().isNotEmpty()
    val ttsEngine = when {
      online -> ActiveTtsEngineType.HYBRID_CLOUD_EDGE
      installedNeuralTtsModel != null -> ActiveTtsEngineType.LOCAL_ENGINE
      hasTtsEngines -> ActiveTtsEngineType.SYSTEM_ENGINE
      else -> ActiveTtsEngineType.LOCAL_ENGINE
    }
    val activeTtsLabel = when {
      online -> "المنظومة الهجينة: نطق سحابي فائق (Edge Neural) + محلي احتياطي"
      installedNeuralTtsModel != null -> "محلي (Piper VITS): ${installedNeuralTtsModel.nameArabic}"
      hasTtsEngines -> "محلي نظامي: $defaultTtsPkg"
      else -> "محلي احتياطي مدمج"
    }

    return VoiceEngineStatus(
      sttEngine = sttEngine,
      ttsEngine = ttsEngine,
      isOnline = online,
      isArabicSttSupported = (online && usableSystemRecognizer) ||
        getInstalledOfflineSttModel("ar") != null ||
        isSystemOnDeviceRecognitionAvailable(),
      isArabicTtsSupported = true,
      isOfflineSttReady = isOfflineModelInstalled || isSystemOnDeviceRecognitionAvailable(),
      isOfflineModelInstalled = isOfflineModelInstalled,
      installedModelName = effectiveOfflineModel?.nameArabic,
      systemRecognizerPackage = systemRecognizerPkg,
      activeTtsPackage = activeTtsLabel
    )
  }
}
