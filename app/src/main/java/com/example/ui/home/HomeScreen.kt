package com.example.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.core.state.WeykyUiState
import com.example.device.apps.AppMatch
import com.example.device.battery.ChargingState
import com.example.ui.adaptive.AdaptiveLayoutMode
import com.example.ui.adaptive.AdaptiveOrientation
import com.example.ui.adaptive.CardDensity
import com.example.ui.adaptive.DevicePosture
import com.example.ui.adaptive.DeviceProfile
import com.example.ui.adaptive.rememberAdaptiveLayoutState
import com.example.ui.theme.JarvisHudColors
import com.example.ui.theme.jarvisTacticalBackground

@Composable
fun HomeScreen(
  uiState: WeykyUiState,
  onQuerySubmit: (String) -> Unit,
  onQueryChange: (String) -> Unit,
  onOpenSettings: () -> Unit,
  onRequestPermission: (String) -> Unit,
  onToggleVoice: () -> Unit,
  onConfirmOption: (String) -> Unit,
  onToggleAppDrawer: () -> Unit = {},
  onLaunchApp: (AppMatch) -> Unit = {},
  onRequestDefaultLauncher: () -> Unit = {},
  onToggleMediaPlayback: () -> Unit = { onQuerySubmit("أوقف الموسيقى") },
  onNextMediaTrack: () -> Unit = { onQuerySubmit("الأغنية التالية") },
  onPreviousMediaTrack: () -> Unit = { onQuerySubmit("الأغنية السابقة") },
  modifier: Modifier = Modifier
) {
  val keyboardController = LocalSoftwareKeyboardController.current
  val isLight = uiState.isLightMode
  val bgColor = Color(uiState.backgroundColorHex)
  val borderColor = Color(uiState.cardBorderColorHex)
  val panelBg = JarvisHudColors.panelBg(isLight)
  val panelBgElevated = JarvisHudColors.panelBgElevated(isLight)
  val textPrimary = JarvisHudColors.textPrimary(isLight)
  val textSecondary = JarvisHudColors.textSecondary(isLight)
  val textMuted = JarvisHudColors.textMuted(isLight)

  BoxWithConstraints(
    modifier = modifier
      .fillMaxSize()
      .jarvisTacticalBackground(
        backgroundColor = bgColor,
        accentColor = borderColor
      )
      .statusBarsPadding()
      .navigationBarsPadding()
      .imePadding()
      .testTag("home_screen_root")
  ) {
    // Resolve dynamic adaptive layout profile for current window bounds
    val deviceProfile = rememberAdaptiveLayoutState(
      containerWidthDp = maxWidth,
      containerHeightDp = maxHeight
    )

    when (deviceProfile.layoutMode) {
      AdaptiveLayoutMode.PHONE_LANDSCAPE -> {
        PhoneLandscapeHomeScreen(
          uiState = uiState,
          deviceProfile = deviceProfile,
          borderColor = borderColor,
          panelBg = panelBg,
          panelBgElevated = panelBgElevated,
          textPrimary = textPrimary,
          textSecondary = textSecondary,
          textMuted = textMuted,
          isLight = isLight,
          onQuerySubmit = onQuerySubmit,
          onQueryChange = onQueryChange,
          onOpenSettings = onOpenSettings,
          onRequestPermission = onRequestPermission,
          onToggleVoice = onToggleVoice,
          onConfirmOption = onConfirmOption,
          onToggleAppDrawer = onToggleAppDrawer,
          onToggleMediaPlayback = onToggleMediaPlayback,
          onNextMediaTrack = onNextMediaTrack,
          onPreviousMediaTrack = onPreviousMediaTrack,
          keyboardController = keyboardController
        )
      }
      AdaptiveLayoutMode.TABLET_EXPANDED,
      AdaptiveLayoutMode.FOLDABLE_BOOK -> {
        TabletExpandedHomeScreen(
          uiState = uiState,
          deviceProfile = deviceProfile,
          borderColor = borderColor,
          panelBg = panelBg,
          panelBgElevated = panelBgElevated,
          textPrimary = textPrimary,
          textSecondary = textSecondary,
          textMuted = textMuted,
          isLight = isLight,
          onQuerySubmit = onQuerySubmit,
          onQueryChange = onQueryChange,
          onOpenSettings = onOpenSettings,
          onRequestPermission = onRequestPermission,
          onToggleVoice = onToggleVoice,
          onConfirmOption = onConfirmOption,
          onToggleAppDrawer = onToggleAppDrawer,
          onToggleMediaPlayback = onToggleMediaPlayback,
          onNextMediaTrack = onNextMediaTrack,
          onPreviousMediaTrack = onPreviousMediaTrack,
          keyboardController = keyboardController
        )
      }
      AdaptiveLayoutMode.FOLDABLE_TABLETOP -> {
        FoldableTabletopHomeScreen(
          uiState = uiState,
          deviceProfile = deviceProfile,
          borderColor = borderColor,
          panelBg = panelBg,
          panelBgElevated = panelBgElevated,
          textPrimary = textPrimary,
          textSecondary = textSecondary,
          textMuted = textMuted,
          isLight = isLight,
          onQuerySubmit = onQuerySubmit,
          onQueryChange = onQueryChange,
          onOpenSettings = onOpenSettings,
          onRequestPermission = onRequestPermission,
          onToggleVoice = onToggleVoice,
          onConfirmOption = onConfirmOption,
          onToggleAppDrawer = onToggleAppDrawer,
          onToggleMediaPlayback = onToggleMediaPlayback,
          onNextMediaTrack = onNextMediaTrack,
          onPreviousMediaTrack = onPreviousMediaTrack,
          keyboardController = keyboardController
        )
      }
      AdaptiveLayoutMode.SPLIT_SCREEN_COMPACT -> {
        SplitScreenCompactHomeScreen(
          uiState = uiState,
          deviceProfile = deviceProfile,
          borderColor = borderColor,
          panelBg = panelBg,
          panelBgElevated = panelBgElevated,
          textPrimary = textPrimary,
          textSecondary = textSecondary,
          textMuted = textMuted,
          isLight = isLight,
          onQuerySubmit = onQuerySubmit,
          onQueryChange = onQueryChange,
          onOpenSettings = onOpenSettings,
          onRequestPermission = onRequestPermission,
          onToggleVoice = onToggleVoice,
          onConfirmOption = onConfirmOption,
          onToggleAppDrawer = onToggleAppDrawer,
          onToggleMediaPlayback = onToggleMediaPlayback,
          onNextMediaTrack = onNextMediaTrack,
          onPreviousMediaTrack = onPreviousMediaTrack,
          keyboardController = keyboardController
        )
      }
      AdaptiveLayoutMode.PHONE_PORTRAIT -> {
        PhonePortraitHomeScreen(
          uiState = uiState,
          deviceProfile = deviceProfile,
          borderColor = borderColor,
          panelBg = panelBg,
          panelBgElevated = panelBgElevated,
          textPrimary = textPrimary,
          textSecondary = textSecondary,
          textMuted = textMuted,
          isLight = isLight,
          onQuerySubmit = onQuerySubmit,
          onQueryChange = onQueryChange,
          onOpenSettings = onOpenSettings,
          onRequestPermission = onRequestPermission,
          onToggleVoice = onToggleVoice,
          onConfirmOption = onConfirmOption,
          onToggleAppDrawer = onToggleAppDrawer,
          onToggleMediaPlayback = onToggleMediaPlayback,
          onNextMediaTrack = onNextMediaTrack,
          onPreviousMediaTrack = onPreviousMediaTrack,
          keyboardController = keyboardController
        )
      }
    }

    if (uiState.isAppDrawerOpen) {
      AppSurfaceOverlay(
        apps = uiState.installedApps,
        deviceProfile = deviceProfile,
        isLightMode = isLight,
        backgroundColor = bgColor,
        borderColor = borderColor,
        onClose = onToggleAppDrawer,
        onLaunchApp = onLaunchApp
      )
    }
  }
}

/**
 * Standard Phone Portrait Composition.
 * Prioritizes clear visual hierarchy, vertical flow, and easy one-handed touch ergonomics.
 */
@Composable
private fun PhonePortraitHomeScreen(
  uiState: WeykyUiState,
  deviceProfile: DeviceProfile,
  borderColor: Color,
  panelBg: Color,
  panelBgElevated: Color,
  textPrimary: Color,
  textSecondary: Color,
  textMuted: Color,
  isLight: Boolean,
  onQuerySubmit: (String) -> Unit,
  onQueryChange: (String) -> Unit,
  onOpenSettings: () -> Unit,
  onRequestPermission: (String) -> Unit,
  onToggleVoice: () -> Unit,
  onConfirmOption: (String) -> Unit,
  onToggleAppDrawer: () -> Unit,
  onToggleMediaPlayback: () -> Unit,
  onNextMediaTrack: () -> Unit,
  onPreviousMediaTrack: () -> Unit,
  keyboardController: androidx.compose.ui.platform.SoftwareKeyboardController?
) {
  val dynamicCoreSize = deviceProfile.calculateAdaptiveCoreSize(uiState.coreScale)

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(
        horizontal = deviceProfile.contentPaddingHorizontalDp,
        vertical = deviceProfile.contentPaddingVerticalDp
      ),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.SpaceBetween
  ) {
    // TOP SECTION: Weather Card + Date Card in one horizontal row
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .widthIn(max = deviceProfile.maxCardWidthDp),
      verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      HomeTopRowCards(
        uiState = uiState,
        borderColor = borderColor,
        panelBg = panelBg,
        textPrimary = textPrimary,
        textSecondary = textSecondary,
        textMuted = textMuted,
        density = deviceProfile.cardDensity,
        onQuerySubmit = onQuerySubmit
      )

      HomeMusicCard(
        uiState = uiState,
        borderColor = borderColor,
        panelBgElevated = panelBgElevated,
        textPrimary = textPrimary,
        textSecondary = textSecondary,
        onToggleMediaPlayback = onToggleMediaPlayback,
        onNextMediaTrack = onNextMediaTrack,
        onPreviousMediaTrack = onPreviousMediaTrack
      )

      HomeContextAlertCard(
        uiState = uiState,
        borderColor = borderColor,
        onQuerySubmit = onQuerySubmit
      )
    }

    // CENTER SECTION: Living WEYKY Visual Core
    HomeCoreSection(
      uiState = uiState,
      coreSizeDp = dynamicCoreSize,
      onToggleVoice = onToggleVoice,
      modifier = Modifier
        .weight(1f, fill = true)
        .padding(vertical = 4.dp)
    )

    // BOTTOM SECTION: Response Card and Bottom Control Row (Settings + Input + Apps)
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .widthIn(max = deviceProfile.maxCardWidthDp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(deviceProfile.cardSpacingDp)
    ) {
      HomeResponseCard(
        uiState = uiState,
        borderColor = borderColor,
        panelBg = panelBg,
        panelBgElevated = panelBgElevated,
        textPrimary = textPrimary,
        onQuerySubmit = onQuerySubmit,
        onConfirmOption = onConfirmOption,
        onRequestPermission = onRequestPermission
      )

      HomeBottomControlRow(
        uiState = uiState,
        borderColor = borderColor,
        panelBg = panelBg,
        panelBgElevated = panelBgElevated,
        textPrimary = textPrimary,
        textSecondary = textSecondary,
        isLight = isLight,
        onQueryChange = onQueryChange,
        onQuerySubmit = onQuerySubmit,
        onToggleVoice = onToggleVoice,
        onOpenSettings = onOpenSettings,
        onToggleAppDrawer = onToggleAppDrawer,
        keyboardController = keyboardController
      )
    }
  }
}

/**
 * Landscape Three-Column Layout:
 * - Middle Column: Dedicated to the Living Core, Text Display Card (Response Card), and Typing Card (Input Bar).
 * - Start Side Column: Dedicated to Weather Card at top, contextual cards (Music/Alerts), and Settings Button at bottom.
 * - End Side Column: Dedicated to Date Card at top, contextual cards (Alerts/Reminders), and Installed Apps Button at bottom.
 * Symmetrical weights guarantee the middle column and core remain strictly centered horizontally in the screen.
 */
@Composable
private fun LandscapeThreeColumnHomeScreen(
  uiState: WeykyUiState,
  deviceProfile: DeviceProfile,
  borderColor: Color,
  panelBg: Color,
  panelBgElevated: Color,
  textPrimary: Color,
  textSecondary: Color,
  textMuted: Color,
  isLight: Boolean,
  onQuerySubmit: (String) -> Unit,
  onQueryChange: (String) -> Unit,
  onOpenSettings: () -> Unit,
  onRequestPermission: (String) -> Unit,
  onToggleVoice: () -> Unit,
  onConfirmOption: (String) -> Unit,
  onToggleAppDrawer: () -> Unit,
  onToggleMediaPlayback: () -> Unit,
  onNextMediaTrack: () -> Unit,
  onPreviousMediaTrack: () -> Unit,
  keyboardController: androidx.compose.ui.platform.SoftwareKeyboardController?
) {
  val dynamicCoreSize = deviceProfile.calculateAdaptiveCoreSize(uiState.coreScale)

  val isMusicActivelyPlaying = uiState.isMusicPlaying || (uiState.activeMediaMetadata?.isPlaying == true)
  val hasResponse = !uiState.lastResponseText.isNullOrBlank()
  val battery = uiState.batterySnapshot
  val isLowBattery = battery != null && battery.isAvailable && battery.percentage <= 20 &&
    battery.chargingState != ChargingState.CHARGING &&
    battery.chargingState != ChargingState.FULL
  val isBatteryFull = battery != null && battery.isAvailable && battery.percentage == 100 &&
    battery.chargingState == ChargingState.FULL
  val hasMissedCall = (uiState.missedCallsSummary?.missedCallsCount ?: 0) > 0
  val hasUrgentNotif = (uiState.notificationSummary?.totalImportantCount ?: 0) > 0
  val hasFallbackEvent = uiState.importantEvents.isNotEmpty()
  val hasAlert = isLowBattery || isBatteryFull || hasMissedCall || hasUrgentNotif || hasFallbackEvent

  // Central column adaptive weight:
  // Preserves strict horizontal centering via equal side weights (1f each),
  // while scaling the workspace appropriately for different screen widths.
  val middleColumnWeight = when {
    deviceProfile.windowWidthDp >= 900.dp -> 1.45f
    deviceProfile.windowWidthDp >= 700.dp -> 1.35f
    else -> 1.25f
  }

  // Adaptive response card max height:
  // On shorter screens, leaves ample space for the core and input card.
  // On taller screens or tablets, allows more response text to be visible without unnecessary scrolling.
  val responseMaxHeight = when {
    deviceProfile.windowHeightDp < 380.dp -> (deviceProfile.windowHeightDp * 0.25f).coerceIn(56.dp, 88.dp)
    deviceProfile.windowHeightDp < 550.dp -> (deviceProfile.windowHeightDp * 0.30f).coerceIn(80.dp, 130.dp)
    else -> (deviceProfile.windowHeightDp * 0.32f).coerceIn(120.dp, 220.dp)
  }

  Row(
    modifier = Modifier
      .fillMaxSize()
      .padding(
        horizontal = deviceProfile.contentPaddingHorizontalDp,
        vertical = deviceProfile.contentPaddingVerticalDp
      ),
    horizontalArrangement = Arrangement.spacedBy(deviceProfile.cardSpacingDp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    // 1. START SIDE COLUMN: Weather card at top, contextual cards in middle, Settings button at bottom
    Column(
      modifier = Modifier
        .weight(1f)
        .fillMaxHeight(),
      verticalArrangement = Arrangement.SpaceBetween,
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      // Top: Weather Card
      HomeWeatherCard(
        uiState = uiState,
        borderColor = borderColor,
        panelBg = panelBg,
        textPrimary = textPrimary,
        textSecondary = textSecondary,
        textMuted = textMuted,
        density = deviceProfile.cardDensity,
        onQuerySubmit = onQuerySubmit,
        modifier = Modifier.fillMaxWidth()
      )

      // Middle: Active auxiliary cards (Music / Context Alerts)
      Column(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth()
          .padding(vertical = 4.dp)
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        if (isMusicActivelyPlaying) {
          HomeMusicCard(
            uiState = uiState,
            borderColor = borderColor,
            panelBgElevated = panelBgElevated,
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            onToggleMediaPlayback = onToggleMediaPlayback,
            onNextMediaTrack = onNextMediaTrack,
            onPreviousMediaTrack = onPreviousMediaTrack
          )
        } else if (hasAlert) {
          HomeContextAlertCard(
            uiState = uiState,
            borderColor = borderColor,
            onQuerySubmit = onQuerySubmit
          )
        }
      }

      // Bottom: Settings Button
      HomeLandscapeSettingsButton(
        borderColor = borderColor,
        panelBgElevated = panelBgElevated,
        textPrimary = textPrimary,
        isRtl = deviceProfile.isRtl,
        onClick = onOpenSettings,
        modifier = Modifier.fillMaxWidth()
      )
    }

    // 2. MIDDLE COLUMN: Living Core, Text Display Card (Response Card), and Typing Card (Input Card)
    Column(
      modifier = Modifier
        .weight(middleColumnWeight)
        .fillMaxHeight(),
      verticalArrangement = Arrangement.SpaceBetween,
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      // Center Stage: Living Visual Core
      BoxWithConstraints(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth(),
        contentAlignment = Alignment.Center
      ) {
        val maxAvailableDim = minOf(maxHeight, maxWidth)
        val coreSize = minOf(dynamicCoreSize, maxAvailableDim * 0.94f)
          .coerceIn(minOf(56.dp, maxAvailableDim * 0.8f), 380.dp)
        HomeCoreSection(
          uiState = uiState,
          coreSizeDp = coreSize,
          onToggleVoice = onToggleVoice
        )
      }

      // Text Display Card: Assistant Response Bubble (when active)
      if (hasResponse) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .heightIn(max = responseMaxHeight)
            .verticalScroll(rememberScrollState())
        ) {
          HomeResponseCard(
            uiState = uiState,
            borderColor = borderColor,
            panelBg = panelBg,
            panelBgElevated = panelBgElevated,
            textPrimary = textPrimary,
            onQuerySubmit = onQuerySubmit,
            onConfirmOption = onConfirmOption,
            onRequestPermission = onRequestPermission
          )
        }
      }

      // Typing Card: Input Bar with voice mic, query field, and submit button
      HomeInputCard(
        uiState = uiState,
        borderColor = borderColor,
        panelBg = panelBg,
        textPrimary = textPrimary,
        textSecondary = textSecondary,
        onQueryChange = onQueryChange,
        onQuerySubmit = onQuerySubmit,
        onToggleVoice = onToggleVoice,
        keyboardController = keyboardController,
        modifier = Modifier.fillMaxWidth()
      )
    }

    // 3. END SIDE COLUMN: Date card at top, contextual cards in middle, Apps button at bottom
    Column(
      modifier = Modifier
        .weight(1f)
        .fillMaxHeight(),
      verticalArrangement = Arrangement.SpaceBetween,
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      // Top: Date Card
      HomeDateCard(
        uiState = uiState,
        borderColor = borderColor,
        panelBg = panelBg,
        textPrimary = textPrimary,
        textSecondary = textSecondary,
        textMuted = textMuted,
        density = deviceProfile.cardDensity,
        onQuerySubmit = onQuerySubmit,
        modifier = Modifier.fillMaxWidth()
      )

      // Middle: Active auxiliary cards (Context alerts if music occupies Column 1)
      Column(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth()
          .padding(vertical = 4.dp)
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        if (hasAlert && isMusicActivelyPlaying) {
          HomeContextAlertCard(
            uiState = uiState,
            borderColor = borderColor,
            onQuerySubmit = onQuerySubmit
          )
        }
      }

      // Bottom: Installed Apps Button
      HomeLandscapeAppDrawerButton(
        borderColor = borderColor,
        panelBgElevated = panelBgElevated,
        textPrimary = textPrimary,
        isRtl = deviceProfile.isRtl,
        onClick = onToggleAppDrawer,
        modifier = Modifier.fillMaxWidth()
      )
    }
  }
}

@Composable
private fun PhoneLandscapeHomeScreen(
  uiState: WeykyUiState,
  deviceProfile: DeviceProfile,
  borderColor: Color,
  panelBg: Color,
  panelBgElevated: Color,
  textPrimary: Color,
  textSecondary: Color,
  textMuted: Color,
  isLight: Boolean,
  onQuerySubmit: (String) -> Unit,
  onQueryChange: (String) -> Unit,
  onOpenSettings: () -> Unit,
  onRequestPermission: (String) -> Unit,
  onToggleVoice: () -> Unit,
  onConfirmOption: (String) -> Unit,
  onToggleAppDrawer: () -> Unit,
  onToggleMediaPlayback: () -> Unit,
  onNextMediaTrack: () -> Unit,
  onPreviousMediaTrack: () -> Unit,
  keyboardController: androidx.compose.ui.platform.SoftwareKeyboardController?
) {
  LandscapeThreeColumnHomeScreen(
    uiState = uiState,
    deviceProfile = deviceProfile,
    borderColor = borderColor,
    panelBg = panelBg,
    panelBgElevated = panelBgElevated,
    textPrimary = textPrimary,
    textSecondary = textSecondary,
    textMuted = textMuted,
    isLight = isLight,
    onQuerySubmit = onQuerySubmit,
    onQueryChange = onQueryChange,
    onOpenSettings = onOpenSettings,
    onRequestPermission = onRequestPermission,
    onToggleVoice = onToggleVoice,
    onConfirmOption = onConfirmOption,
    onToggleAppDrawer = onToggleAppDrawer,
    onToggleMediaPlayback = onToggleMediaPlayback,
    onNextMediaTrack = onNextMediaTrack,
    onPreviousMediaTrack = onPreviousMediaTrack,
    keyboardController = keyboardController
  )
}

/**
 * Tablet & Expanded Window Composition.
 * Dual-pane tactical dashboard:
 * Tablet & Expanded Window Composition.
 * Corrected per the Targeted Tablet Home Layout Optimization specification:
 * - Efficient window utilization without excessive unused space or fake placeholders.
 * - Prominent, beautifully centered Core Workspace calculated from measured bounds.
 * - Single balanced Top Information Row for Date and Weather, eliminating duplicate telemetry.
 * - Responsive dual-region arrangement on wide landscape windows when contextual content is active.
 * - Quiet, uncluttered, centered composition when no contextual stream is active.
 * - Perfectly centered single-column composition on portrait tablets preventing awkward multi-column cramping.
 * - Fully preserves existing bottom interaction bar (Settings, Input, Apps) with generous touch targets.
 */
@Composable
private fun TabletExpandedHomeScreen(
  uiState: WeykyUiState,
  deviceProfile: DeviceProfile,
  borderColor: Color,
  panelBg: Color,
  panelBgElevated: Color,
  textPrimary: Color,
  textSecondary: Color,
  textMuted: Color,
  isLight: Boolean,
  onQuerySubmit: (String) -> Unit,
  onQueryChange: (String) -> Unit,
  onOpenSettings: () -> Unit,
  onRequestPermission: (String) -> Unit,
  onToggleVoice: () -> Unit,
  onConfirmOption: (String) -> Unit,
  onToggleAppDrawer: () -> Unit,
  onToggleMediaPlayback: () -> Unit,
  onNextMediaTrack: () -> Unit,
  onPreviousMediaTrack: () -> Unit,
  keyboardController: androidx.compose.ui.platform.SoftwareKeyboardController?
) {
  val dynamicCoreSize = deviceProfile.calculateAdaptiveCoreSize(uiState.coreScale)
  val isLandscape = deviceProfile.orientation == AdaptiveOrientation.LANDSCAPE
  val isBookPosture = deviceProfile.posture == DevicePosture.BOOK

  val isMusicActivelyPlaying = uiState.isMusicPlaying || (uiState.activeMediaMetadata?.isPlaying == true)
  val hasResponse = !uiState.lastResponseText.isNullOrBlank()
  val battery = uiState.batterySnapshot
  val isLowBattery = battery != null && battery.isAvailable && battery.percentage <= 20 &&
    battery.chargingState != ChargingState.CHARGING &&
    battery.chargingState != ChargingState.FULL
  val isBatteryFull = battery != null && battery.isAvailable && battery.percentage == 100 &&
    battery.chargingState == ChargingState.FULL
  val hasMissedCall = (uiState.missedCallsSummary?.missedCallsCount ?: 0) > 0
  val hasUrgentNotif = (uiState.notificationSummary?.totalImportantCount ?: 0) > 0
  val hasFallbackEvent = uiState.importantEvents.isNotEmpty()
  val hasAlert = isLowBattery || isBatteryFull || hasMissedCall || hasUrgentNotif || hasFallbackEvent

  when {
    // 1. Foldable Book Posture: Two physical screen halves split by the vertical hinge
    isBookPosture -> {
      Row(
        modifier = Modifier
          .fillMaxSize()
          .padding(
            horizontal = deviceProfile.contentPaddingHorizontalDp,
            vertical = deviceProfile.contentPaddingVerticalDp
          ),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
      ) {
        // Left screen half: Top Row + Living Core
        Column(
          modifier = Modifier
            .weight(1f)
            .fillMaxHeight(),
          verticalArrangement = Arrangement.SpaceBetween,
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          HomeTopRowCards(
            uiState = uiState,
            borderColor = borderColor,
            panelBg = panelBg,
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            textMuted = textMuted,
            density = CardDensity.COMPACT,
            onQuerySubmit = onQuerySubmit
          )

          Box(
            modifier = Modifier
              .weight(1f)
              .fillMaxWidth(),
            contentAlignment = Alignment.Center
          ) {
            HomeCoreSection(
              uiState = uiState,
              coreSizeDp = dynamicCoreSize,
              onToggleVoice = onToggleVoice
            )
          }
        }

        // Right screen half: Contextual Stream + Response + Controls
        Column(
          modifier = Modifier
            .weight(1f)
            .fillMaxHeight(),
          verticalArrangement = Arrangement.SpaceBetween,
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Column(
            modifier = Modifier
              .weight(1f)
              .fillMaxWidth()
              .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            HomeMusicCard(
              uiState = uiState,
              borderColor = borderColor,
              panelBgElevated = panelBgElevated,
              textPrimary = textPrimary,
              textSecondary = textSecondary,
              onToggleMediaPlayback = onToggleMediaPlayback,
              onNextMediaTrack = onNextMediaTrack,
              onPreviousMediaTrack = onPreviousMediaTrack
            )

            HomeContextAlertCard(
              uiState = uiState,
              borderColor = borderColor,
              onQuerySubmit = onQuerySubmit
            )

            HomeResponseCard(
              uiState = uiState,
              borderColor = borderColor,
              panelBg = panelBg,
              panelBgElevated = panelBgElevated,
              textPrimary = textPrimary,
              onQuerySubmit = onQuerySubmit,
              onConfirmOption = onConfirmOption,
              onRequestPermission = onRequestPermission
            )
          }

          HomeBottomControlRow(
            uiState = uiState,
            borderColor = borderColor,
            panelBg = panelBg,
            panelBgElevated = panelBgElevated,
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            isLight = isLight,
            onQueryChange = onQueryChange,
            onQuerySubmit = onQuerySubmit,
            onToggleVoice = onToggleVoice,
            onOpenSettings = onOpenSettings,
            onToggleAppDrawer = onToggleAppDrawer,
            keyboardController = keyboardController
          )
        }
      }
    }

    // 2. Landscape Tablet: Three-column tactical layout with centered core and flanking panels
    isLandscape -> {
      LandscapeThreeColumnHomeScreen(
        uiState = uiState,
        deviceProfile = deviceProfile,
        borderColor = borderColor,
        panelBg = panelBg,
        panelBgElevated = panelBgElevated,
        textPrimary = textPrimary,
        textSecondary = textSecondary,
        textMuted = textMuted,
        isLight = isLight,
        onQuerySubmit = onQuerySubmit,
        onQueryChange = onQueryChange,
        onOpenSettings = onOpenSettings,
        onRequestPermission = onRequestPermission,
        onToggleVoice = onToggleVoice,
        onConfirmOption = onConfirmOption,
        onToggleAppDrawer = onToggleAppDrawer,
        onToggleMediaPlayback = onToggleMediaPlayback,
        onNextMediaTrack = onNextMediaTrack,
        onPreviousMediaTrack = onPreviousMediaTrack,
        keyboardController = keyboardController
      )
    }

    // 4. Portrait Tablet: Single-column centered vertical composition with optimal proportions
    else -> {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(
            horizontal = deviceProfile.contentPaddingHorizontalDp,
            vertical = deviceProfile.contentPaddingVerticalDp
          ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
      ) {
        // Top Section: Date + Weather, plus any active Music/Alerts
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 680.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          HomeTopRowCards(
            uiState = uiState,
            borderColor = borderColor,
            panelBg = panelBg,
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            textMuted = textMuted,
            density = deviceProfile.cardDensity,
            onQuerySubmit = onQuerySubmit
          )

          HomeMusicCard(
            uiState = uiState,
            borderColor = borderColor,
            panelBgElevated = panelBgElevated,
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            onToggleMediaPlayback = onToggleMediaPlayback,
            onNextMediaTrack = onNextMediaTrack,
            onPreviousMediaTrack = onPreviousMediaTrack
          )

          HomeContextAlertCard(
            uiState = uiState,
            borderColor = borderColor,
            onQuerySubmit = onQuerySubmit
          )
        }

        // Center Workspace: Prominent, perfectly centered Core
        Box(
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .padding(vertical = 10.dp),
          contentAlignment = Alignment.Center
        ) {
          HomeCoreSection(
            uiState = uiState,
            coreSizeDp = dynamicCoreSize,
            onToggleVoice = onToggleVoice
          )
        }

        // Bottom Section: Response (if active) + Bottom Control Row
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 720.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          HomeResponseCard(
            uiState = uiState,
            borderColor = borderColor,
            panelBg = panelBg,
            panelBgElevated = panelBgElevated,
            textPrimary = textPrimary,
            onQuerySubmit = onQuerySubmit,
            onConfirmOption = onConfirmOption,
            onRequestPermission = onRequestPermission
          )

          HomeBottomControlRow(
            uiState = uiState,
            borderColor = borderColor,
            panelBg = panelBg,
            panelBgElevated = panelBgElevated,
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            isLight = isLight,
            onQueryChange = onQueryChange,
            onQuerySubmit = onQuerySubmit,
            onToggleVoice = onToggleVoice,
            onOpenSettings = onOpenSettings,
            onToggleAppDrawer = onToggleAppDrawer,
            keyboardController = keyboardController
          )
        }
      }
    }
  }
}

/**
 * Foldable Tabletop Posture Composition (Device partially folded with horizontal hinge).
 * Top display half: Viewing pane (Telemetry & prominent Core).
 * Bottom display half: Interaction pane (Response, Ask Weyky, Mic, and App access).
 */
@Composable
private fun FoldableTabletopHomeScreen(
  uiState: WeykyUiState,
  deviceProfile: DeviceProfile,
  borderColor: Color,
  panelBg: Color,
  panelBgElevated: Color,
  textPrimary: Color,
  textSecondary: Color,
  textMuted: Color,
  isLight: Boolean,
  onQuerySubmit: (String) -> Unit,
  onQueryChange: (String) -> Unit,
  onOpenSettings: () -> Unit,
  onRequestPermission: (String) -> Unit,
  onToggleVoice: () -> Unit,
  onConfirmOption: (String) -> Unit,
  onToggleAppDrawer: () -> Unit,
  onToggleMediaPlayback: () -> Unit,
  onNextMediaTrack: () -> Unit,
  onPreviousMediaTrack: () -> Unit,
  keyboardController: androidx.compose.ui.platform.SoftwareKeyboardController?
) {
  val dynamicCoreSize = deviceProfile.calculateAdaptiveCoreSize(uiState.coreScale)

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(
        horizontal = deviceProfile.contentPaddingHorizontalDp,
        vertical = deviceProfile.contentPaddingVerticalDp
      )
  ) {
    // TOP HALF: Viewing Half
    Column(
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth(),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.SpaceBetween
    ) {
      HomeTopRowCards(
        uiState = uiState,
        borderColor = borderColor,
        panelBg = panelBg,
        textPrimary = textPrimary,
        textSecondary = textSecondary,
        textMuted = textMuted,
        density = CardDensity.COMPACT,
        onQuerySubmit = onQuerySubmit
      )

      Box(
        modifier = Modifier.weight(1f),
        contentAlignment = Alignment.Center
      ) {
        HomeCoreSection(
          uiState = uiState,
          coreSizeDp = dynamicCoreSize,
          onToggleVoice = onToggleVoice
        )
      }
    }

    Spacer(modifier = Modifier.height(12.dp))

    // BOTTOM HALF: Interaction Half
    Column(
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth()
        .verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(8.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      HomeMusicCard(
        uiState = uiState,
        borderColor = borderColor,
        panelBgElevated = panelBgElevated,
        textPrimary = textPrimary,
        textSecondary = textSecondary,
        onToggleMediaPlayback = onToggleMediaPlayback,
        onNextMediaTrack = onNextMediaTrack,
        onPreviousMediaTrack = onPreviousMediaTrack
      )

      HomeContextAlertCard(
        uiState = uiState,
        borderColor = borderColor,
        onQuerySubmit = onQuerySubmit
      )

      HomeResponseCard(
        uiState = uiState,
        borderColor = borderColor,
        panelBg = panelBg,
        panelBgElevated = panelBgElevated,
        textPrimary = textPrimary,
        onQuerySubmit = onQuerySubmit,
        onConfirmOption = onConfirmOption,
        onRequestPermission = onRequestPermission
      )

      HomeBottomControlRow(
        uiState = uiState,
        borderColor = borderColor,
        panelBg = panelBg,
        panelBgElevated = panelBgElevated,
        textPrimary = textPrimary,
        textSecondary = textSecondary,
        isLight = isLight,
        onQueryChange = onQueryChange,
        onQuerySubmit = onQuerySubmit,
        onToggleVoice = onToggleVoice,
        onOpenSettings = onOpenSettings,
        onToggleAppDrawer = onToggleAppDrawer,
        keyboardController = keyboardController
      )
    }
  }
}

/**
 * Split-Screen Compact Composition.
 * Handles split-screen, resized windows, or multi-window mode where height or width is tight.
 */
@Composable
private fun SplitScreenCompactHomeScreen(
  uiState: WeykyUiState,
  deviceProfile: DeviceProfile,
  borderColor: Color,
  panelBg: Color,
  panelBgElevated: Color,
  textPrimary: Color,
  textSecondary: Color,
  textMuted: Color,
  isLight: Boolean,
  onQuerySubmit: (String) -> Unit,
  onQueryChange: (String) -> Unit,
  onOpenSettings: () -> Unit,
  onRequestPermission: (String) -> Unit,
  onToggleVoice: () -> Unit,
  onConfirmOption: (String) -> Unit,
  onToggleAppDrawer: () -> Unit,
  onToggleMediaPlayback: () -> Unit,
  onNextMediaTrack: () -> Unit,
  onPreviousMediaTrack: () -> Unit,
  keyboardController: androidx.compose.ui.platform.SoftwareKeyboardController?
) {
  val dynamicCoreSize = deviceProfile.calculateAdaptiveCoreSize(uiState.coreScale)

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 8.dp, vertical = 4.dp)
      .verticalScroll(rememberScrollState()),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(6.dp)
  ) {
    HomeTopRowCards(
      uiState = uiState,
      borderColor = borderColor,
      panelBg = panelBg,
      textPrimary = textPrimary,
      textSecondary = textSecondary,
      textMuted = textMuted,
      density = CardDensity.COMPACT,
      onQuerySubmit = onQuerySubmit
    )

    HomeCoreSection(
      uiState = uiState,
      coreSizeDp = dynamicCoreSize,
      onToggleVoice = onToggleVoice,
      modifier = Modifier.padding(vertical = 2.dp)
    )

    HomeMusicCard(
      uiState = uiState,
      borderColor = borderColor,
      panelBgElevated = panelBgElevated,
      textPrimary = textPrimary,
      textSecondary = textSecondary,
      onToggleMediaPlayback = onToggleMediaPlayback,
      onNextMediaTrack = onNextMediaTrack,
      onPreviousMediaTrack = onPreviousMediaTrack
    )

    HomeContextAlertCard(
      uiState = uiState,
      borderColor = borderColor,
      onQuerySubmit = onQuerySubmit
    )

    HomeResponseCard(
      uiState = uiState,
      borderColor = borderColor,
      panelBg = panelBg,
      panelBgElevated = panelBgElevated,
      textPrimary = textPrimary,
      onQuerySubmit = onQuerySubmit,
      onConfirmOption = onConfirmOption,
      onRequestPermission = onRequestPermission
    )

    HomeBottomControlRow(
      uiState = uiState,
      borderColor = borderColor,
      panelBg = panelBg,
      panelBgElevated = panelBgElevated,
      textPrimary = textPrimary,
      textSecondary = textSecondary,
      isLight = isLight,
      onQueryChange = onQueryChange,
      onQuerySubmit = onQuerySubmit,
      onToggleVoice = onToggleVoice,
      onOpenSettings = onOpenSettings,
      onToggleAppDrawer = onToggleAppDrawer,
      keyboardController = keyboardController
    )
  }
}
