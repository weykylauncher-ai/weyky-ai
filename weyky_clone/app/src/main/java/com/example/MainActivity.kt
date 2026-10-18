package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.core.capabilities.DeviceCapability
import com.example.core.state.WeykyScreen
import com.example.core.state.WeykyViewModel
import com.example.ui.home.HomeScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  private val viewModel: WeykyViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      val uiState by viewModel.uiState.collectAsState()
      MyApplicationTheme(darkTheme = !uiState.isLightMode) {

        val permissionLauncher = rememberLauncherForActivityResult(
          contract = ActivityResultContracts.RequestPermission()
        ) { isGranted ->
          val requested = uiState.pendingPermission?.first
          if (isGranted && requested != null) {
            viewModel.onPermissionGranted(requested)
          } else {
            viewModel.clearPendingPermission()
          }
        }

        val roleLauncher = rememberLauncherForActivityResult(
          contract = ActivityResultContracts.StartActivityForResult()
        ) { result ->
          viewModel.onRoleRequestResult(result.resultCode)
        }

        // Back button behavior adhering to Android Home launcher semantics
        BackHandler(enabled = uiState.isAppDrawerOpen) {
          viewModel.toggleAppDrawer(false)
        }
        BackHandler(enabled = uiState.currentScreen == WeykyScreen.SETTINGS) {
          viewModel.navigateTo(WeykyScreen.MAIN)
        }
        BackHandler(enabled = uiState.currentScreen == WeykyScreen.MAIN && !uiState.isAppDrawerOpen && uiState.launcherStatus.isDefaultLauncher) {
          // In Default Launcher mode, Home surface is root; pressing Back does not finish activity
        }

        Surface(
          modifier = Modifier.fillMaxSize(),
          color = Color(uiState.backgroundColorHex)
        ) {
          when (uiState.currentScreen) {
            WeykyScreen.MAIN -> {
              HomeScreen(
                uiState = uiState,
                onQuerySubmit = { query -> viewModel.onUserQuery(query) },
                onQueryChange = { query -> viewModel.updateInputQuery(query) },
                onOpenSettings = { viewModel.navigateTo(WeykyScreen.SETTINGS) },
                onRequestPermission = { permission ->
                  permissionLauncher.launch(permission)
                },
                onToggleVoice = {
                  viewModel.toggleVoiceRecognition()
                },
                onConfirmOption = { option ->
                  viewModel.confirmOption(option)
                },
                onToggleAppDrawer = {
                  viewModel.toggleAppDrawer()
                },
                onLaunchApp = { app ->
                  viewModel.launchApp(app)
                },
                onRequestDefaultLauncher = {
                  val intent = viewModel.createRequestRoleIntent()
                  if (intent != null) {
                    roleLauncher.launch(intent)
                  }
                },
                onToggleMediaPlayback = { viewModel.toggleMediaPlayback() },
                onNextMediaTrack = { viewModel.skipToNextMediaTrack() },
                onPreviousMediaTrack = { viewModel.skipToPreviousMediaTrack() }
              )
            }
            WeykyScreen.SETTINGS -> {
              val isNotifAccessGranted = viewModel.capabilityDetector.isCapabilitySupported(
                DeviceCapability.NOTIFICATION_LISTENER
              )
              SettingsScreen(
                uiState = uiState,
                onBack = { viewModel.navigateTo(WeykyScreen.MAIN) },
                onTogglePowerSaving = { viewModel.togglePowerSaving() },
                onUpdateCoreScale = { scale -> viewModel.updateCoreScale(scale) },
                onSetLightMode = { isLight -> viewModel.setLightMode(isLight) },
                onUpdateBackgroundColor = { colorHex -> viewModel.updateBackgroundColor(colorHex) },
                onUpdateCardBorderColor = { colorHex -> viewModel.updateCardBorderColor(colorHex) },
                isNotificationAccessGranted = isNotifAccessGranted,
                onRequestDefaultLauncher = {
                  val intent = viewModel.createRequestRoleIntent()
                  if (intent != null) {
                    roleLauncher.launch(intent)
                  }
                },
                onDownloadModel = { modelInfo -> viewModel.downloadVoiceModel(modelInfo) },
                onInstallModel = { modelInfo -> viewModel.installVoiceModel(modelInfo) },
                onInstallModelFromFile = { modelInfo, uri -> viewModel.installVoiceModelFromUri(modelInfo, uri) },
                onDeleteModel = { modelInfo -> viewModel.deleteVoiceModel(modelInfo) },
                onSelectActiveTtsModel = { modelInfo -> viewModel.setActiveTtsModel(modelInfo) },
                onSetHybridTtsEnabled = { enabled -> viewModel.setHybridTtsEnabled(enabled) },
                onSetCloudTtsVoice = { voice -> viewModel.setCloudTtsVoice(voice) },
                onTestVoice = { viewModel.testVoice() },
                onTestVoiceWithKey = { voiceKey, phrase -> viewModel.testVoice(voiceKey, phrase) }
              )
            }
          }
        }
      }
    }
  }

  override fun onResume() {
    super.onResume()
    viewModel.voicePipeline.attachActivity(this)
    viewModel.onResume()
  }

  override fun onPause() {
    super.onPause()
    viewModel.voicePipeline.detachActivity()
    viewModel.onPause()
  }
}
