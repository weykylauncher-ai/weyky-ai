package com.example.core.state

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.capabilities.CapabilityDetector
import com.example.core.capabilities.DeviceCapability
import com.example.core.context.ContextEngine
import com.example.core.context.ContextSnapshot
import com.example.core.context.ContextState
import com.example.core.intent.CommandOutcome
import com.example.core.intent.ExecutionResult
import com.example.core.intent.IntentExecutor
import com.example.core.intent.IntentParser
import com.example.core.intent.SpeechResult
import com.example.core.intent.WeykyIntent
import com.example.core.intent.handlers.AppActionHandler
import com.example.core.intent.handlers.AudioActionHandler
import com.example.core.intent.handlers.BatteryActionHandler
import com.example.core.intent.handlers.CallActionHandler
import com.example.core.intent.handlers.ContextActionHandler
import com.example.core.intent.handlers.FlashlightActionHandler
import com.example.core.intent.handlers.MediaActionHandler
import com.example.core.intent.handlers.NetworkActionHandler
import com.example.core.intent.handlers.NotificationActionHandler
import com.example.core.intent.handlers.SettingsActionHandler
import com.example.core.intent.handlers.StorageMemoryActionHandler
import com.example.core.intent.handlers.WeatherActionHandler
import com.example.core.scene.SceneResolver
import com.example.core.scene.WeykyScene
import com.example.core.voice.VoicePipeline
import com.example.core.voice.VoiceSessionState
import com.example.core.voice.WeykySpeechSynthesizer
import com.example.device.apps.AppMatch
import com.example.device.apps.AppResolver
import com.example.device.audio.AudioController
import com.example.device.battery.BatteryProvider
import com.example.device.battery.BatterySnapshot
import com.example.device.calls.CallLogProvider
import com.example.device.calls.CallSummary
import com.example.device.calls.ContactLookupProvider
import com.example.device.flashlight.FlashlightController
import com.example.device.launcher.LauncherRoleManager
import com.example.device.launcher.LauncherStatus
import com.example.device.media.ActiveMediaMetadata
import com.example.device.media.MediaPlaybackController
import com.example.device.memory.MemoryProvider
import com.example.device.memory.MemorySnapshot
import com.example.device.network.NetworkProvider
import com.example.device.network.NetworkSnapshot
import com.example.device.notifications.NotificationRepository
import com.example.device.notifications.NotificationSummary
import com.example.device.storage.StorageProvider
import com.example.device.storage.StorageSnapshot
import com.example.device.telephony.TelephonyProvider
import com.example.device.telephony.TelephonySnapshot
import com.example.device.audio.HeadsetButtonReceiver
import com.example.device.overlay.WeykyFloatingOverlayService
import com.example.device.weather.LocationProvider
import com.example.device.weather.RealWeatherProvider
import com.example.device.weather.WeatherSnapshot
import com.example.rendering.visual_state.CoreVisualState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class WeykyScreen {
  MAIN,
  SETTINGS
}

data class ImportantEvent(
  val id: String,
  val icon: String,
  val title: String,
  val subtitle: String,
  val actionTitle: String?,
  val onAction: (() -> Unit)? = null
)

data class WeykyUiState(
  val currentTimeString: String = "",
  val currentDateString: String = "",
  val visualState: CoreVisualState = CoreVisualState.IDLE,
  val currentScene: WeykyScene = WeykyScene.IDLE_SURFACE,
  val contextSnapshot: ContextSnapshot = ContextSnapshot(ContextState.STATIONARY, 1.0f),
  val batterySnapshot: BatterySnapshot? = null,
  val memorySnapshot: MemorySnapshot? = null,
  val storageSnapshot: StorageSnapshot? = null,
  val networkSnapshot: NetworkSnapshot? = null,
  val telephonySnapshot: TelephonySnapshot? = null,
  val weatherSnapshot: WeatherSnapshot? = null,
  val notificationSummary: NotificationSummary? = null,
  val missedCallsSummary: CallSummary? = null,
  val isTorchActive: Boolean = false,
  val isMusicPlaying: Boolean = false,
  val activeMediaMetadata: ActiveMediaMetadata? = null,
  val isPowerSaving: Boolean = false,
  val isSpeakingAloud: Boolean = false,
  val coreScale: Float = 0.44f,
  val isLightMode: Boolean = false,
  val backgroundColorHex: Long = 0xFF000000L,
  val cardBorderColorHex: Long = 0xFF00F0FFL,
  val inputQuery: String = "",
  val lastResponseText: String? = null,
  val proactiveSuggestions: List<com.example.core.intelligence.conversation.ActionableOption> = emptyList(),
  val conversationHistory: List<com.example.core.intelligence.conversation.ConversationTurn> = emptyList(),
  val pendingRecoveryActionTitle: String? = null,
  val pendingRecoveryAction: (() -> Unit)? = null,
  val pendingPermission: Pair<String, String>? = null,
  val pendingConfirmationPrompt: String? = null,
  val pendingConfirmationOptions: List<String> = emptyList(),
  val pendingConfirmationCallback: ((String) -> Unit)? = null,
  val isVoiceListening: Boolean = false,
  val voiceEnergyLevel: Float = 0f,
  val importantEvents: List<ImportantEvent> = emptyList(),
  val launcherStatus: LauncherStatus = LauncherStatus(),
  val installedApps: List<AppMatch> = emptyList(),
  val isAppDrawerOpen: Boolean = false,
  val voiceEngineStatus: com.example.core.voice.engine.VoiceEngineStatus? = null,
  val voiceModelStatuses: Map<String, com.example.core.voice.model.ModelInstallStatus> = emptyMap(),
  val activeTtsModelId: String = "",
  val isHybridTtsEnabled: Boolean = true,
  val cloudTtsVoiceName: String = com.example.core.voice.engine.EdgeNeuralTtsEngine.DEFAULT_VOICE,
  val isFloatingOverlayEnabled: Boolean = false,
  val isNotificationPrivacyEnabled: Boolean = false,
  val isHeadsetButtonEnabled: Boolean = false,
  val currentEnglishDateString: String = "",
  val currentScreen: WeykyScreen = WeykyScreen.MAIN
)

class WeykyViewModel(application: Application) : AndroidViewModel(application) {
  private val context = application.applicationContext

  // Providers & Controllers
  val capabilityDetector = CapabilityDetector(context)
  val contextEngine = ContextEngine(context)
  val batteryProvider = BatteryProvider(context)
  val memoryProvider = MemoryProvider(context)
  val storageProvider = StorageProvider()
  val networkProvider = NetworkProvider(context)
  val telephonyProvider = TelephonyProvider(context)
  val flashlightController = FlashlightController(context)
  val audioController = AudioController(context)
  val mediaPlaybackController = MediaPlaybackController(context)
  val appResolver = AppResolver(context)
  val launcherRoleManager = LauncherRoleManager(context)
  val locationProvider = LocationProvider(context)
  val weatherProvider = RealWeatherProvider(locationProvider)
  val callLogProvider = CallLogProvider(context)
  val contactLookupProvider = ContactLookupProvider(context)
  val calendarProvider = com.example.device.calendar.CalendarProvider(context)

  // Contextual Intelligence Engine
  val situationSnapshotBuilder = com.example.core.intelligence.SituationSnapshotBuilder(
    batteryProvider = batteryProvider,
    networkProvider = networkProvider,
    contextEngine = contextEngine,
    locationProvider = locationProvider,
    weatherProvider = weatherProvider,
    calendarProvider = calendarProvider,
    mediaController = mediaPlaybackController,
    callLogProvider = callLogProvider
  )
  val contextReasoningEngine = com.example.core.intelligence.ContextReasoningEngine()

  // Speech Synthesizer (TTS)
  val speechSynthesizer = WeykySpeechSynthesizer(
    context = context,
    onSpeechStateChanged = { speaking ->
      _uiState.update { state ->
        val nextVisualState = if (speaking) {
          if (state.isVoiceListening) CoreVisualState.LISTENING else CoreVisualState.RESPONDING
        } else {
          when {
            state.isVoiceListening -> CoreVisualState.LISTENING
            state.isMusicPlaying -> CoreVisualState.MUSIC
            state.visualState == CoreVisualState.RESPONDING -> CoreVisualState.IDLE
            else -> state.visualState
          }
        }
        state.copy(
          isSpeakingAloud = speaking,
          visualState = nextVisualState
        )
      }
      if (!speaking && !_uiState.value.lastResponseText.isNullOrBlank()) {
        scheduleResponseAutoDismiss(7000L)
      }
    }
  )

  // Modular Handlers
  private val batteryActionHandler = BatteryActionHandler(batteryProvider)
  private val storageMemoryActionHandler = StorageMemoryActionHandler(memoryProvider, storageProvider)
  private val flashlightActionHandler = FlashlightActionHandler(capabilityDetector, flashlightController)
  private val audioActionHandler = AudioActionHandler(audioController)
  private val mediaActionHandler = MediaActionHandler(mediaPlaybackController)
  private val appActionHandler = AppActionHandler(appResolver)
  private val callActionHandler = CallActionHandler(context, callLogProvider, contactLookupProvider)
  private val weatherActionHandler = WeatherActionHandler(weatherProvider, locationProvider)
  private val networkActionHandler = NetworkActionHandler(context, networkProvider)
  private val notificationActionHandler = NotificationActionHandler(context, capabilityDetector)
  private val settingsActionHandler = SettingsActionHandler(context)
  private val contextActionHandler = ContextActionHandler(
    getContextSnapshot = { _uiState.value.contextSnapshot },
    situationSnapshotBuilder = situationSnapshotBuilder,
    contextReasoningEngine = contextReasoningEngine
  )

  val deviceInfoProvider = com.example.device.info.DeviceInfoProvider(
    context = context,
    memoryProvider = memoryProvider,
    storageProvider = storageProvider,
    batteryProvider = batteryProvider
  )

  val localConversationalEngine = com.example.core.intelligence.conversation.LocalConversationalEngine(
    situationSnapshotBuilder = situationSnapshotBuilder,
    deviceInfoProvider = deviceInfoProvider
  )

  private val intentExecutor = IntentExecutor(
    batteryActionHandler = batteryActionHandler,
    storageMemoryActionHandler = storageMemoryActionHandler,
    flashlightActionHandler = flashlightActionHandler,
    audioActionHandler = audioActionHandler,
    mediaActionHandler = mediaActionHandler,
    appActionHandler = appActionHandler,
    callActionHandler = callActionHandler,
    weatherActionHandler = weatherActionHandler,
    networkActionHandler = networkActionHandler,
    notificationActionHandler = notificationActionHandler,
    settingsActionHandler = settingsActionHandler,
    contextActionHandler = contextActionHandler,
    localConversationalEngine = localConversationalEngine,
    deviceInfoProvider = deviceInfoProvider
  )

  // Voice Recognition Pipeline
  val voicePipeline = VoicePipeline(
    context = context,
    onTranscriptReady = { transcript -> onUserQuery(transcript) },
    onListeningStateChanged = { listening ->
      if (listening) {
        responseAutoDismissJob?.cancel()
      }
      _uiState.update {
        val listeningText = if (Locale.getDefault().language.startsWith("ar")) "أستمع إليك..." else "Listening..."
        it.copy(
          isVoiceListening = listening,
          voiceEnergyLevel = if (listening) it.voiceEnergyLevel else 0f,
          visualState = if (listening) CoreVisualState.LISTENING else if (it.isSpeakingAloud) CoreVisualState.RESPONDING else if (it.isMusicPlaying) CoreVisualState.MUSIC else CoreVisualState.IDLE,
          lastResponseText = if (listening) listeningText else it.lastResponseText
        )
      }
      if (!listening && !_uiState.value.lastResponseText.isNullOrBlank()) {
        scheduleResponseAutoDismiss(6000L)
      }
    },
    onVoiceEnergyChanged = { energy ->
      _uiState.update { it.copy(voiceEnergyLevel = energy) }
    }
  )

  private val prefs = context.getSharedPreferences("weyky_Appearance_prefs", android.content.Context.MODE_PRIVATE)

  private val _uiState = MutableStateFlow(
    WeykyUiState(
      isLightMode = prefs.getBoolean("is_light_mode", false),
      backgroundColorHex = prefs.getLong("bg_color_hex", 0xFF000000L),
      cardBorderColorHex = prefs.getLong("border_color_hex", 0xFF00F0FFL),
      cloudTtsVoiceName = prefs.getString("cloud_tts_voice", com.example.core.voice.engine.EdgeNeuralTtsEngine.DEFAULT_VOICE)
        ?: com.example.core.voice.engine.EdgeNeuralTtsEngine.DEFAULT_VOICE
    )
  )
  val uiState: StateFlow<WeykyUiState> = _uiState.asStateFlow()

  private val _lastCommandOutcome = MutableStateFlow<CommandOutcome?>(null)
  val lastCommandOutcome: StateFlow<CommandOutcome?> = _lastCommandOutcome.asStateFlow()

  @Volatile
  private var isExecutingCommand: Boolean = false
  private var lastCommandPendingPermission: String? = null

  private var stateRevertJob: Job? = null
  private var responseAutoDismissJob: Job? = null
  private var musicDecayJob: Job? = null
  private var clockJob: Job? = null

  init {
    speechSynthesizer.activeCloudVoice = _uiState.value.cloudTtsVoiceName
    startServices()
    startClockAndTelemetryLoop()
    refreshTelemetry()
    fetchInitialWeather()
    refreshLauncherStatus()
    voicePipeline.prewarmInstalledOfflineModelAsync()
    viewModelScope.launch {
      appResolver.warmUpIndex()
    }
  }

  fun onResume() {
    launcherRoleManager.onHomeResumed()
    refreshLauncherStatus()
    contextEngine.start()
    batteryProvider.start()
    networkProvider.start()
    mediaPlaybackController.updateMediaState()
    refreshTelemetry()
    viewModelScope.launch {
      appResolver.warmUpIndex()
      if (_uiState.value.isAppDrawerOpen) {
        refreshInstalledApps()
      }
    }
  }

  fun onPause() {
    launcherRoleManager.onHomePaused()
    voicePipeline.stopListening()
    speechSynthesizer.stopIfSpeaking()
    contextEngine.stop()
    batteryProvider.stop()
    networkProvider.stop()
  }

  fun refreshLauncherStatus() {
    val status = launcherRoleManager.checkLauncherStatus()
    _uiState.update { it.copy(launcherStatus = status) }
  }

  fun refreshInstalledApps() {
    val apps = appResolver.getInstalledLaunchableApps()
    _uiState.update { it.copy(installedApps = apps) }
  }

  fun toggleAppDrawer(open: Boolean? = null) {
    val targetOpen = open ?: !_uiState.value.isAppDrawerOpen
    val apps = if (targetOpen) appResolver.getInstalledLaunchableApps() else _uiState.value.installedApps
    _uiState.update {
      it.copy(
        isAppDrawerOpen = targetOpen,
        installedApps = apps
      )
    }
  }

  fun launchApp(app: AppMatch): Boolean {
    val launched = appResolver.launchApp(app)
    if (launched) {
      _uiState.update { it.copy(isAppDrawerOpen = false) }
    }
    return launched
  }

  fun createRequestRoleIntent(): Intent? = launcherRoleManager.createRequestRoleIntent()

  fun onRoleRequestResult(resultCode: Int) {
    launcherRoleManager.onRoleRequestResult(resultCode)
    refreshLauncherStatus()
  }

  fun toggleFloatingOverlay(enable: Boolean? = null) {
    val target = enable ?: !_uiState.value.isFloatingOverlayEnabled
    _uiState.update { it.copy(isFloatingOverlayEnabled = target) }
    if (target) {
      WeykyFloatingOverlayService.start(context)
    } else {
      WeykyFloatingOverlayService.stop(context)
    }
  }

  fun toggleNotificationPrivacy(enable: Boolean? = null) {
    val target = enable ?: !_uiState.value.isNotificationPrivacyEnabled
    NotificationRepository.isPrivacyModeEnabled = target
    _uiState.update { it.copy(isNotificationPrivacyEnabled = target) }
  }

  fun toggleHeadsetButton(enable: Boolean? = null) {
    val target = enable ?: !_uiState.value.isHeadsetButtonEnabled
    HeadsetButtonReceiver.isHeadsetTriggerEnabled = target
    HeadsetButtonReceiver.onHeadsetButtonPressedListener = if (target) {
      { toggleVoiceRecognition() }
    } else null
    _uiState.update { it.copy(isHeadsetButtonEnabled = target) }
  }

  override fun onCleared() {
    super.onCleared()
    voicePipeline.destroy()
    speechSynthesizer.shutdown()
    musicDecayJob?.cancel()
    contextEngine.stop()
    batteryProvider.stop()
    networkProvider.stop()
    flashlightController.setTorch(false)
  }

  private fun startServices() {
    viewModelScope.launch {
      contextEngine.contextState.collect { snapshot ->
        _uiState.update { it.copy(contextSnapshot = snapshot) }
        recalculateSceneAndEvents()
      }
    }
    viewModelScope.launch {
      batteryProvider.batteryState.collect { battery ->
        val isNowCharging = battery?.chargingState == com.example.device.battery.ChargingState.CHARGING ||
          battery?.chargingState == com.example.device.battery.ChargingState.FULL
        _uiState.update { state ->
          val updatedVisualState = when {
            isNowCharging && state.visualState == CoreVisualState.IDLE -> CoreVisualState.CHARGING
            !isNowCharging && state.visualState == CoreVisualState.CHARGING -> CoreVisualState.IDLE
            else -> state.visualState
          }
          state.copy(
            batterySnapshot = battery,
            visualState = updatedVisualState
          )
        }
        recalculateSceneAndEvents()
      }
    }
    viewModelScope.launch {
      networkProvider.networkState.collect { net ->
        _uiState.update { it.copy(networkSnapshot = net) }
      }
    }
    viewModelScope.launch {
      flashlightController.isTorchOn.collect { torch ->
        _uiState.update { it.copy(isTorchActive = torch) }
        recalculateSceneAndEvents()
      }
    }
    viewModelScope.launch {
      mediaPlaybackController.mediaState.collect { meta ->
        Log.d("WEYKY_MEDIA", "MUSIC_STATE: isPlaying=${meta.isPlaying}, title=${meta.title}, artist=${meta.artist}")
        if (meta.isPlaying) {
          musicDecayJob?.cancel()
          musicDecayJob = null
          Log.d("WEYKY_MEDIA", "MEDIA_PLAYING: title=${meta.title}")
          _uiState.update {
            it.copy(
              isMusicPlaying = true,
              activeMediaMetadata = meta,
              visualState = if (it.visualState == CoreVisualState.IDLE || it.visualState == CoreVisualState.MUSIC) {
                CoreVisualState.MUSIC
              } else {
                it.visualState
              }
            )
          }
          recalculateSceneAndEvents()
        } else {
          Log.d("WEYKY_MEDIA", "MEDIA_PAUSED: title=${meta.title}")
          if (_uiState.value.isMusicPlaying) {
            musicDecayJob?.cancel()
            musicDecayJob = viewModelScope.launch {
              // Smooth 1200ms decay transition before settling into ambient idle
              delay(1200)
              _uiState.update {
                it.copy(
                  isMusicPlaying = false,
                  activeMediaMetadata = meta,
                  visualState = if (it.visualState == CoreVisualState.MUSIC) CoreVisualState.IDLE else it.visualState
                )
              }
              recalculateSceneAndEvents()
            }
          } else {
            _uiState.update {
              it.copy(
                isMusicPlaying = false,
                activeMediaMetadata = meta,
                visualState = if (it.visualState == CoreVisualState.MUSIC) CoreVisualState.IDLE else it.visualState
              )
            }
            recalculateSceneAndEvents()
          }
        }
      }
    }
    viewModelScope.launch {
      NotificationRepository.importantEvents.collect { _ ->
        val hasAccess = capabilityDetector.isCapabilitySupported(DeviceCapability.NOTIFICATION_LISTENER)
        val summary = NotificationRepository.getSummary(hasAccess)
        _uiState.update { it.copy(notificationSummary = summary) }
        recalculateSceneAndEvents()
      }
    }
    viewModelScope.launch {
      NotificationRepository.activeNotifications.collect { _ ->
        val hasAccess = capabilityDetector.isCapabilitySupported(DeviceCapability.NOTIFICATION_LISTENER)
        val summary = NotificationRepository.getSummary(hasAccess)
        mediaPlaybackController.updateMediaState()
        _uiState.update { it.copy(notificationSummary = summary) }
        recalculateSceneAndEvents()
      }
    }
    viewModelScope.launch {
      voicePipeline.sessionState.collect { session ->
        val isArabic = Locale.getDefault().language.startsWith("ar")
        when (session) {
          is VoiceSessionState.PermissionRequired -> {
            val reqMsg = if (isArabic) "يلزم منح صلاحية الميكروفون للتحدث مع WEYKY." else "Microphone access is required to use voice interaction."
            _uiState.update {
              it.copy(
                pendingPermission = android.Manifest.permission.RECORD_AUDIO to reqMsg,
                lastResponseText = reqMsg
              )
            }
            scheduleResponseAutoDismiss(12000L)
          }
          is VoiceSessionState.Partial -> {
            responseAutoDismissJob?.cancel()
            _uiState.update {
              it.copy(
                lastResponseText = session.text,
                visualState = CoreVisualState.LISTENING
              )
            }
          }
          is VoiceSessionState.Error -> {
            val currentVisualState = _uiState.value.visualState
            // CRITICAL: NEVER overwrite an active SUCCESS or CALL or ongoing execution with an asynchronous audio client error!
            if (currentVisualState != CoreVisualState.SUCCESS && currentVisualState != CoreVisualState.CALL && !isExecutingCommand) {
              Log.w("WEYKY_VOICE", "[VOICE] VoiceSessionState.Error surfaced to UI: ${session.message}")
              _uiState.update {
                it.copy(
                  lastResponseText = session.message,
                  visualState = if (it.isMusicPlaying) CoreVisualState.MUSIC else CoreVisualState.ERROR
                )
              }
              speakResponse(session.message)
              scheduleVisualStateReset(4000)
              scheduleResponseAutoDismiss(7000L)
            } else {
              Log.d("WEYKY_VOICE", "[VOICE] Ignored VoiceSessionState.Error '${session.message}' because visualState is $currentVisualState (isExecutingCommand=$isExecutingCommand)")
            }
          }
          else -> {}
        }
      }
    }
    viewModelScope.launch {
      voicePipeline.unifiedVoiceManager.status.collect { status ->
        _uiState.update { it.copy(voiceEngineStatus = status) }
      }
    }
    viewModelScope.launch {
      voicePipeline.unifiedVoiceManager.modelManager.modelStatuses.collect { statuses ->
        val activeTtsId = voicePipeline.unifiedVoiceManager.modelManager.getActiveTtsModelId()
        _uiState.update { it.copy(voiceModelStatuses = statuses, activeTtsModelId = activeTtsId) }
      }
    }
  }

  fun setActiveTtsModel(modelInfo: com.example.core.voice.model.VoiceModelInfo) {
    voicePipeline.unifiedVoiceManager.modelManager.setActiveTtsModelId(modelInfo.id)
    _uiState.update { it.copy(activeTtsModelId = modelInfo.id) }
    voicePipeline.unifiedVoiceManager.refresh()
  }

  fun downloadVoiceModel(modelInfo: com.example.core.voice.model.VoiceModelInfo) {
    viewModelScope.launch {
      voicePipeline.unifiedVoiceManager.modelManager.downloadModelOnly(modelInfo)
      voicePipeline.unifiedVoiceManager.refresh()
    }
  }

  fun installVoiceModel(modelInfo: com.example.core.voice.model.VoiceModelInfo) {
    viewModelScope.launch {
      voicePipeline.unifiedVoiceManager.modelManager.installDownloadedModel(modelInfo)
      voicePipeline.unifiedVoiceManager.refresh()
      val activeTtsId = voicePipeline.unifiedVoiceManager.modelManager.getActiveTtsModelId()
      _uiState.update { it.copy(activeTtsModelId = activeTtsId) }
      voicePipeline.prewarmInstalledOfflineModelAsync(modelInfo.languageCode)
    }
  }

  fun installVoiceModelFromUri(modelInfo: com.example.core.voice.model.VoiceModelInfo, uri: android.net.Uri) {
    viewModelScope.launch {
      voicePipeline.unifiedVoiceManager.modelManager.installModelFromUri(modelInfo, uri)
      voicePipeline.unifiedVoiceManager.refresh()
      val activeTtsId = voicePipeline.unifiedVoiceManager.modelManager.getActiveTtsModelId()
      _uiState.update { it.copy(activeTtsModelId = activeTtsId) }
      voicePipeline.prewarmInstalledOfflineModelAsync(modelInfo.languageCode)
    }
  }

  fun deleteVoiceModel(modelInfo: com.example.core.voice.model.VoiceModelInfo) {
    voicePipeline.unifiedVoiceManager.modelManager.deleteModel(modelInfo)
    voicePipeline.unifiedVoiceManager.refresh()
    val activeTtsId = voicePipeline.unifiedVoiceManager.modelManager.getActiveTtsModelId()
    _uiState.update { it.copy(activeTtsModelId = activeTtsId) }
  }

  fun toggleMediaPlayback() {
    mediaPlaybackController.toggle()
    mediaPlaybackController.updateMediaState()
  }

  fun skipToNextMediaTrack() {
    mediaPlaybackController.skipToNext()
    mediaPlaybackController.updateMediaState()
  }

  fun skipToPreviousMediaTrack() {
    mediaPlaybackController.skipToPrevious()
    mediaPlaybackController.updateMediaState()
  }

  private fun startClockAndTelemetryLoop() {
    clockJob?.cancel()
    clockJob = viewModelScope.launch {
      val timeFormat = SimpleDateFormat("HH:mm", Locale.US)
      val dateFormat = SimpleDateFormat("EEEE، d MMMM", Locale("ar"))
      val englishDateFormat = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.US)

      while (isActive) {
        val now = Date()
        val timeStr = timeFormat.format(now)
        val dateStr = dateFormat.format(now)
        val englishDateStr = englishDateFormat.format(now)

        _uiState.update {
          it.copy(
            currentTimeString = timeStr,
            currentDateString = dateStr,
            currentEnglishDateString = englishDateStr
          )
        }
        refreshTelemetry()
        delay(15000)
      }
    }
  }

  fun refreshTelemetry() {
    viewModelScope.launch {
      val mem = memoryProvider.queryMemory()
      val storage = storageProvider.queryStorage()
      val tel = telephonyProvider.queryTelephony()
      val calls = callLogProvider.queryMissedCalls()
      mediaPlaybackController.updateMediaState()
      val musicActive = mediaPlaybackController.isMediaActive()

      _uiState.update {
        it.copy(
          memorySnapshot = mem,
          storageSnapshot = storage,
          telephonySnapshot = tel,
          missedCallsSummary = calls,
          isMusicPlaying = musicActive
        )
      }
      recalculateSceneAndEvents()
    }
  }

  fun fetchInitialWeather() {
    viewModelScope.launch {
      val weather = weatherProvider.fetchWeather()
      _uiState.update { it.copy(weatherSnapshot = weather) }
    }
  }

  private fun recalculateSceneAndEvents() {
    val state = _uiState.value
    val events = mutableListOf<ImportantEvent>()

    // 1. Missed calls event (Priority 1 for the bottom card)
    state.missedCallsSummary?.let { calls ->
      if (calls.missedCallsCount > 0) {
        val latest = calls.latestMissedCall
        val caller = latest?.callerName?.ifBlank { null } ?: latest?.number?.ifBlank { null } ?: "مكالمة فائتة"
        val sub = if (calls.missedCallsCount > 1) {
          "MISSED CALLS (${calls.missedCallsCount})"
        } else {
          "MISSED CALL"
        }
        events.add(
          ImportantEvent(
            id = "missed_calls",
            icon = "phone_missed",
            title = caller,
            subtitle = sub,
            actionTitle = latest?.formattedTime ?: state.currentTimeString.ifBlank { "NOW" },
            onAction = { onUserQuery("المكالمات الفائتة") }
          )
        )
      }
    }

    // 2. Urgent & Immediate Real-Time Notifications (Priority 2 for the bottom card)
    state.notificationSummary?.let { notifs ->
      notifs.activeImportantEvents.forEach { notifEvent ->
        val categoryLabel = when (notifEvent.importanceCategory) {
          com.example.device.notifications.NotificationImportanceCategory.MISSED_CALL -> "MISSED CALL ALERT"
          com.example.device.notifications.NotificationImportanceCategory.SECURITY_SYSTEM_ALERT -> "URGENT ALERT // ${notifEvent.appLabel ?: "SYSTEM"}"
          com.example.device.notifications.NotificationImportanceCategory.REMINDER_EVENT -> "IMMEDIATE REMINDER // ${notifEvent.appLabel ?: "CALENDAR"}"
          com.example.device.notifications.NotificationImportanceCategory.PERSONAL_MESSAGE -> "${notifEvent.appLabel ?: "MESSAGE"} // ${notifEvent.text}"
          else -> notifEvent.text.ifBlank { "IMPORTANT NOTIFICATION" }
        }
        val eventTimeStr = SimpleDateFormat("HH:mm", Locale.US)
          .format(Date(notifEvent.timestampMillis))

        events.add(
          ImportantEvent(
            id = "notif_${notifEvent.eventId}",
            icon = if (notifEvent.importanceCategory == com.example.device.notifications.NotificationImportanceCategory.MISSED_CALL) {
              "phone_missed"
            } else {
              "notifications"
            },
            title = notifEvent.title.ifBlank { notifEvent.appLabel ?: "إشعار هام" },
            subtitle = categoryLabel,
            actionTitle = eventTimeStr,
            onAction = { onUserQuery("اقرأ الإشعارات") }
          )
        )
      }
    }

    // Resolve Scene
    val resolvedScene = SceneResolver.resolveScene(
      contextSnapshot = state.contextSnapshot,
      isMusicPlaying = state.isMusicPlaying,
      hasMissedCalls = (state.missedCallsSummary?.missedCallsCount ?: 0) > 0,
      hasActiveNotifications = (state.notificationSummary?.totalCount ?: 0) > 0,
      currentVisualState = state.visualState,
      isPowerSaving = state.isPowerSaving
    )

    _uiState.update {
      it.copy(
        importantEvents = events,
        currentScene = resolvedScene
      )
    }
  }

  fun updateInputQuery(query: String) {
    _uiState.update { it.copy(inputQuery = query) }
    if (!_uiState.value.lastResponseText.isNullOrBlank()) {
      scheduleResponseAutoDismiss(9000L)
    }
  }

  fun toggleVoiceRecognition() {
    val currentlyListening = _uiState.value.isVoiceListening
    Log.d("WEYKY_VOICE", "[VOICE-DIAG] Mic button tapped (currentlyListening=$currentlyListening, externalMusicPlaying=${_uiState.value.isMusicPlaying})")
    if (currentlyListening) {
      voicePipeline.stopListening()
      _uiState.update {
        it.copy(
          isVoiceListening = false,
          visualState = if (it.isMusicPlaying) CoreVisualState.MUSIC else CoreVisualState.IDLE
        )
      }
      scheduleResponseAutoDismiss(5000L)
    } else {
      startVoiceRecognition()
    }
  }

  fun startVoiceRecognition() {
    // Only stop WEYKY's own TTS if it is actively speaking so mic doesn't pick up assistant speech;
    // never touch or interrupt external media playback!
    val stoppedTts = speechSynthesizer.stopIfSpeaking()
    Log.d("WEYKY_VOICE", "[VOICE-DIAG] startVoiceRecognition: stoppedActiveWeykyTts=$stoppedTts, externalMusicPlaying=${_uiState.value.isMusicPlaying}")

    if (!voicePipeline.hasAudioPermission()) {
      val isArabic = Locale.getDefault().language.startsWith("ar")
      val msg = if (isArabic) "يلزم منح صلاحية الميكروفون للاستماع للأوامر الصوتية." else "Microphone permission is required to listen for voice commands."
      _uiState.update {
        it.copy(
          pendingPermission = android.Manifest.permission.RECORD_AUDIO to msg,
          lastResponseText = msg
        )
      }
      scheduleResponseAutoDismiss(12000L)
      return
    }
    val locale = if (Locale.getDefault().language.startsWith("ar")) "ar" else "en-US"
    voicePipeline.startListening(locale)
  }

  fun onUserQuery(query: String): Job {
    return viewModelScope.launch {
      executeUserQuery(query)
    }
  }

  suspend fun executeUserQuery(query: String): CommandOutcome {
    val commandId = "cmd_${System.currentTimeMillis()}"
    Log.d("WEYKY_VOICE", "[VOICE] command=\"$query\" (commandId=$commandId)")

    if (query.isBlank()) {
      val outcome = CommandOutcome(commandId, ExecutionResult.Error("Empty query"), SpeechResult.Skipped)
      _lastCommandOutcome.value = outcome
      return outcome
    }

    isExecutingCommand = true
    try {
      // Stop previous WEYKY TTS playback only if actively speaking
      speechSynthesizer.stopIfSpeaking()

      val parsedIntent = IntentParser.parse(query, localConversationalEngine.session)
      Log.d("WEYKY_VOICE", "[VOICE] intent=${parsedIntent.javaClass.simpleName} (commandId=$commandId)")

      // Check if it is a wake command ("Hey WEYKY" / "يا ويكي" / "ويكي")
      if (parsedIntent is WeykyIntent.WakeIntent) {
        val isArabic = Locale.getDefault().language.startsWith("ar")
        val wakeGreeting = if (isArabic) "أهلاً، أنا أستمع إليك." else "I'm listening."
        _uiState.update {
          it.copy(
            inputQuery = "",
            visualState = CoreVisualState.LISTENING,
            lastResponseText = wakeGreeting
          )
        }
        val speechRes = speakResponse(wakeGreeting)
        viewModelScope.launch {
          delay(1100)
          startVoiceRecognition()
        }
        val outcome = CommandOutcome(
          commandId,
          ExecutionResult.Success(wakeGreeting, "Woken up", CoreVisualState.LISTENING),
          speechRes
        )
        _lastCommandOutcome.value = outcome
        return outcome
      }

      val thinkingText = if (Locale.getDefault().language.startsWith("ar")) "جاري التفكير..." else "Thinking..."
      _uiState.update {
        it.copy(
          inputQuery = "",
          visualState = CoreVisualState.PROCESSING,
          lastResponseText = thinkingText,
          proactiveSuggestions = emptyList(),
          pendingConfirmationPrompt = null,
          pendingConfirmationOptions = emptyList(),
          pendingConfirmationCallback = null
        )
      }

      var finalIntent = parsedIntent
      if (finalIntent is WeykyIntent.ConversationalIntent) {
        val appCheck = appResolver.resolveApp(query)
        if (appCheck is com.example.device.apps.AppResolveResult.FoundSingle) {
          finalIntent = WeykyIntent.OpenAppIntent(appName = query)
        }
      } else if (finalIntent is WeykyIntent.UnknownIntent) {
        val appCheck = appResolver.resolveApp(query)
        if (appCheck is com.example.device.apps.AppResolveResult.FoundSingle) {
          finalIntent = WeykyIntent.OpenAppIntent(appName = query)
        }
      }

      if (finalIntent !is WeykyIntent.ConversationalIntent && finalIntent !is WeykyIntent.UnknownIntent) {
        localConversationalEngine.session.recordUserTurn(query)
      }

      Log.d("WEYKY_VOICE", "[EXEC] ${finalIntent.javaClass.simpleName} START (commandId=$commandId)")
      val result = intentExecutor.execute(finalIntent)
      if (result is ExecutionResult.Success) {
        Log.d("WEYKY_VOICE", "[EXEC] ${finalIntent.javaClass.simpleName} SUCCESS action=\"${result.actionTaken}\" (commandId=$commandId)")
      } else {
        Log.w("WEYKY_VOICE", "[EXEC] ${finalIntent.javaClass.simpleName} FAILED result=${result.javaClass.simpleName} (commandId=$commandId)")
      }

      val speechResult: SpeechResult

      when (result) {
        is ExecutionResult.Success -> {
          if (finalIntent !is WeykyIntent.ConversationalIntent && finalIntent !is WeykyIntent.UnknownIntent) {
            localConversationalEngine.session.recordAssistantTurn(result.speech, result.proactiveSuggestions)
          }

          // 1. Immediately establish primary command success on UI state with diacritized text
          val vocalizedSpeech = vocalizeResponseForDisplayAndSpeech(result.speech)
          _uiState.update {
            it.copy(
              visualState = result.visualState,
              lastResponseText = vocalizedSpeech,
              proactiveSuggestions = result.proactiveSuggestions,
              conversationHistory = localConversationalEngine.session.turns,
              pendingRecoveryActionTitle = null,
              pendingRecoveryAction = null,
              pendingPermission = null
            )
          }

          // 2. Independently attempt TTS feedback with full diacritics and unit expansions
          speechResult = try {
            speakResponse(vocalizedSpeech)
          } catch (e: Exception) {
            Log.w("WEYKY_VOICE", "[TTS] FAILED reason=${e.message} (commandId=$commandId)")
            SpeechResult.Failed(e.message ?: "Unknown TTS exception")
          }

          Log.d("WEYKY_VOICE", "[VOICE] FINAL_RESULT=SUCCESS (commandId=$commandId)")
          scheduleVisualStateReset()
        }
        is ExecutionResult.Fallback -> {
          val vocalizedExp = vocalizeResponseForDisplayAndSpeech(result.explanation)
          _uiState.update {
            it.copy(
              visualState = result.visualState,
              lastResponseText = vocalizedExp,
              proactiveSuggestions = emptyList(),
              pendingRecoveryActionTitle = result.recoveryActionTitle,
              pendingRecoveryAction = result.onRecoveryAction,
              pendingPermission = null
            )
          }
          speechResult = speakResponse(vocalizedExp)
          Log.d("WEYKY_VOICE", "[VOICE] FINAL_RESULT=FALLBACK (commandId=$commandId)")
          scheduleVisualStateReset(6000)
        }
        is ExecutionResult.RequiresPermission -> {
          lastCommandPendingPermission = query
          val vocalizedRationale = vocalizeResponseForDisplayAndSpeech(result.rationale)
          _uiState.update {
            it.copy(
              visualState = result.visualState,
              lastResponseText = vocalizedRationale,
              pendingRecoveryActionTitle = "منح الصلاحية",
              pendingRecoveryAction = null,
              pendingPermission = result.permission to result.rationale
            )
          }
          speechResult = speakResponse(vocalizedRationale)
          Log.d("WEYKY_VOICE", "[VOICE] FINAL_RESULT=REQUIRES_PERMISSION (commandId=$commandId)")
          scheduleVisualStateReset(8000)
        }
        is ExecutionResult.Unavailable -> {
          val vocalizedReason = vocalizeResponseForDisplayAndSpeech(result.reason)
          _uiState.update {
            it.copy(
              visualState = result.visualState,
              lastResponseText = vocalizedReason,
              pendingRecoveryActionTitle = null,
              pendingRecoveryAction = null,
              pendingPermission = null
            )
          }
          speechResult = speakResponse(vocalizedReason)
          Log.w("WEYKY_VOICE", "[VOICE] FINAL_RESULT=UNAVAILABLE (commandId=$commandId)")
          scheduleVisualStateReset(6000)
        }
        is ExecutionResult.NeedsConfirmation -> {
          val vocalizedPrompt = vocalizeResponseForDisplayAndSpeech(result.prompt)
          _uiState.update {
            it.copy(
              visualState = result.visualState,
              lastResponseText = vocalizedPrompt,
              pendingConfirmationPrompt = result.prompt,
              pendingConfirmationOptions = result.options,
              pendingConfirmationCallback = result.onConfirmOption,
              pendingRecoveryActionTitle = null,
              pendingRecoveryAction = null,
              pendingPermission = null
            )
          }
          speechResult = speakResponse(vocalizedPrompt)
          Log.d("WEYKY_VOICE", "[VOICE] FINAL_RESULT=NEEDS_CONFIRMATION (commandId=$commandId)")
        }
        is ExecutionResult.NotSupported -> {
          val vocalizedReason = vocalizeResponseForDisplayAndSpeech(result.reason)
          _uiState.update {
            it.copy(
              visualState = result.visualState,
              lastResponseText = vocalizedReason,
              pendingRecoveryActionTitle = null,
              pendingRecoveryAction = null,
              pendingPermission = null
            )
          }
          speechResult = speakResponse(vocalizedReason)
          Log.w("WEYKY_VOICE", "[VOICE] FINAL_RESULT=NOT_SUPPORTED (commandId=$commandId)")
          scheduleVisualStateReset(6000)
        }
        is ExecutionResult.Error -> {
          val vocalizedMsg = vocalizeResponseForDisplayAndSpeech(result.friendlyMessage)
          _uiState.update {
            it.copy(
              visualState = result.visualState,
              lastResponseText = vocalizedMsg,
              pendingRecoveryActionTitle = null,
              pendingRecoveryAction = null,
              pendingPermission = null
            )
          }
          speechResult = speakResponse(vocalizedMsg)
          Log.w("WEYKY_VOICE", "[VOICE] FINAL_RESULT=ERROR (commandId=$commandId)")
          scheduleVisualStateReset(6000)
        }
      }

      val outcome = CommandOutcome(commandId, result, speechResult)
      _lastCommandOutcome.value = outcome
      val autoDismissDelay = when (result) {
        is ExecutionResult.NeedsConfirmation,
        is ExecutionResult.RequiresPermission -> 15000L
        else -> 8000L
      }
      scheduleResponseAutoDismiss(autoDismissDelay)
      return outcome
    } finally {
      isExecutingCommand = false
    }
  }

  fun onPermissionGranted(permission: String) {
    clearPendingPermission()
    refreshTelemetry()
    when (permission) {
      android.Manifest.permission.RECORD_AUDIO -> {
        startVoiceRecognition()
      }
      android.Manifest.permission.CALL_PHONE,
      android.Manifest.permission.READ_CONTACTS -> {
        val pendingCmd = lastCommandPendingPermission
        lastCommandPendingPermission = null
        if (!pendingCmd.isNullOrBlank()) {
          onUserQuery(pendingCmd)
        }
      }
      android.Manifest.permission.READ_CALL_LOG -> {
        onUserQuery("المكالمات الفائتة")
      }
      android.Manifest.permission.ACCESS_COARSE_LOCATION,
      android.Manifest.permission.ACCESS_FINE_LOCATION -> {
        fetchInitialWeather()
        onUserQuery("ما حالة الطقس؟")
      }
    }
  }

  /**
   * Vocalizes and diacritizes Arabic response text with proper tashkeel marks,
   * number expansion, and unit normalization for visual clarity and optimal speech pronunciation.
   */
  private fun vocalizeResponseForDisplayAndSpeech(text: String): String {
    return com.example.core.voice.engine.ArabicTextDiacritizer.prepareAndDiacritize(text)
  }

  fun speakResponse(text: String): SpeechResult {
    return speechSynthesizer.speak(text)
  }

  fun stopSpeaking() {
    speechSynthesizer.stop()
  }

  fun setHybridTtsEnabled(enabled: Boolean) {
    speechSynthesizer.isHybridModeEnabled = enabled
    _uiState.update { it.copy(isHybridTtsEnabled = enabled) }
    voicePipeline.unifiedVoiceManager.refresh()
    _uiState.update { it.copy(voiceEngineStatus = voicePipeline.unifiedVoiceManager.status.value) }
  }

  fun setCloudTtsVoice(voice: String) {
    prefs.edit().putString("cloud_tts_voice", voice).apply()
    speechSynthesizer.activeCloudVoice = voice
    _uiState.update { it.copy(cloudTtsVoiceName = voice) }
  }

  fun testVoice(voice: String? = null, phrase: String? = null) {
    if (voice != null) {
      prefs.edit().putString("cloud_tts_voice", voice).apply()
      speechSynthesizer.activeCloudVoice = voice
      _uiState.update { it.copy(cloudTtsVoiceName = voice) }
    }
    val sampleText = phrase ?: "أَهْلًا بِكَ، أَنَا وَايْكِي رَهْنُ إِشَارَتِكَ. هَذَا تَجْرِبَةٌ لِلنُّطْقِ الْعَصَبِيِّ الْفَصِيحِ."
    speakResponse(sampleText)
  }

  fun confirmOption(option: String) {
    val cb = _uiState.value.pendingConfirmationCallback
    _uiState.update {
      it.copy(
        pendingConfirmationPrompt = null,
        pendingConfirmationOptions = emptyList(),
        pendingConfirmationCallback = null
      )
    }
    cb?.invoke(option)
  }

  fun clearPendingPermission() {
    _uiState.update { it.copy(pendingPermission = null) }
  }

  fun navigateTo(screen: WeykyScreen) {
    _uiState.update { it.copy(currentScreen = screen) }
  }

  fun togglePowerSaving() {
    _uiState.update { it.copy(isPowerSaving = !it.isPowerSaving) }
  }

  fun updateCoreScale(scale: Float) {
    _uiState.update { it.copy(coreScale = scale.coerceIn(0.30f, 0.65f)) }
  }

  fun setLightMode(enabled: Boolean) {
    val currentBg = _uiState.value.backgroundColorHex
    val newBg = if (enabled) {
      // Switch to Light Mode default background if currently on a dark preset
      if (currentBg == 0xFF000000L || currentBg == 0xFF020710L || currentBg == 0xFF040B07L ||
        currentBg == 0xFF080B10L || currentBg == 0xFF0E0307L || currentBg == 0xFF060310L
      ) {
        0xFFF2F7FBL
      } else {
        currentBg
      }
    } else {
      // Switch back to OLED Black if currently on a light preset
      if (currentBg == 0xFFF2F7FBL || currentBg == 0xFFFFFFFFL || currentBg == 0xFFEAF2F8L || currentBg == 0xFFF4F5F7L) {
        0xFF000000L
      } else {
        currentBg
      }
    }
    prefs.edit()
      .putBoolean("is_light_mode", enabled)
      .putLong("bg_color_hex", newBg)
      .apply()
    _uiState.update {
      it.copy(
        isLightMode = enabled,
        backgroundColorHex = newBg
      )
    }
  }

  fun updateBackgroundColor(colorHex: Long) {
    val isLightPreset = colorHex == 0xFFF2F7FBL || colorHex == 0xFFFFFFFFL || colorHex == 0xFFEAF2F8L || colorHex == 0xFFF4F5F7L
    prefs.edit()
      .putLong("bg_color_hex", colorHex)
      .putBoolean("is_light_mode", isLightPreset)
      .apply()
    _uiState.update {
      it.copy(
        backgroundColorHex = colorHex,
        isLightMode = isLightPreset
      )
    }
  }

  fun updateCardBorderColor(colorHex: Long) {
    prefs.edit().putLong("border_color_hex", colorHex).apply()
    _uiState.update { it.copy(cardBorderColorHex = colorHex) }
  }

  private fun scheduleVisualStateReset(delayMillis: Long = 4500) {
    stateRevertJob?.cancel()
    stateRevertJob = viewModelScope.launch {
      delay(delayMillis)
      _uiState.update {
        val isChargingNow = it.batterySnapshot?.chargingState == com.example.device.battery.ChargingState.CHARGING ||
          it.batterySnapshot?.chargingState == com.example.device.battery.ChargingState.FULL
        val targetVisualState = when {
          it.isVoiceListening -> CoreVisualState.LISTENING
          it.isSpeakingAloud -> CoreVisualState.RESPONDING
          it.isMusicPlaying -> CoreVisualState.MUSIC
          isChargingNow -> CoreVisualState.CHARGING
          else -> CoreVisualState.IDLE
        }
        it.copy(visualState = targetVisualState)
      }
    }
  }

  /**
   * Automatically hides the RESPONSE card after a period of inactivity.
   * Waits if WEYKY is actively speaking aloud or listening to the user so the card never vanishes mid-speech.
   */
  fun scheduleResponseAutoDismiss(delayMillis: Long = 8000L) {
    responseAutoDismissJob?.cancel()
    responseAutoDismissJob = viewModelScope.launch {
      delay(delayMillis)
      // Wait while assistant is actively speaking or user is speaking into microphone
      while (_uiState.value.isSpeakingAloud || _uiState.value.isVoiceListening || isExecutingCommand) {
        delay(1000L)
      }
      _uiState.update {
        it.copy(
          lastResponseText = null,
          proactiveSuggestions = emptyList(),
          pendingConfirmationPrompt = null,
          pendingConfirmationOptions = emptyList(),
          pendingConfirmationCallback = null,
          pendingRecoveryActionTitle = null,
          pendingRecoveryAction = null,
          pendingPermission = null
        )
      }
    }
  }
}
