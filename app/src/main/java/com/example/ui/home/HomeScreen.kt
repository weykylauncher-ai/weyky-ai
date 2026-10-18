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
 * Phone Landscape Composition.
 * Visual Core is visually centered horizontally and vertically in the usable screen area
 * between the top information area and bottom interaction bar.
 * Flanking side panels arrange optional contextual cards (Response, Music, Alerts) around
 * the core without ever moving or displacing it.
 */
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
    // 1. TOP INFORMATION ROW: Date & Weather cards (Centered horizontally)
    HomeTopRowCards(
      uiState = uiState,
      borderColor = borderColor,
      panelBg = panelBg,
      textPrimary = textPrimary,
      textSecondary = textSecondary,
      textMuted = textMuted,
      density = CardDensity.COMPACT,
      onQuerySubmit = onQuerySubmit,
      modifier = Modifier.widthIn(max = 720.dp)
    )

    // 2. MIDDLE USABLE AREA: The Living Core is visually centered horizontally & vertically
    LandscapeCenteredCoreWorkspace(
      uiState = uiState,
      dynamicCoreSize = dynamicCoreSize,
      borderColor = borderColor,
      panelBg = panelBg,
      panelBgElevated = panelBgElevated,
      textPrimary = textPrimary,
      textSecondary = textSecondary,
      onToggleVoice = onToggleVoice,
      onQuerySubmit = onQuerySubmit,
      onConfirmOption = onConfirmOption,
      onRequestPermission = onRequestPermission,
      onToggleMediaPlayback = onToggleMediaPlayback,
      onNextMediaTrack = onNextMediaTrack,
      onPreviousMediaTrack = onPreviousMediaTrack,
      modifier = Modifier.weight(1f)
    )

    // 3. BOTTOM INTERACTION BAR: Settings, Input Query, Voice Mic, App Drawer (Centered horizontally)
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
      keyboardController = keyboardController,
      modifier = Modifier.widthIn(max = 720.dp)
    )
  }
}

/**
 * Unified Centered Core Workspace for all Landscape Orientations (Phone & Tablet).
 * Mathematically and visually guarantees that the Living Visual Core is situated at the
 * exact 50% horizontal center of the usable screen and 50% vertical center between the top
 * information row and the bottom interaction bar.
 * Optional contextual panels (Assistant response, Music, Alerts) flank the core on the Start
 * and End sides without ever moving or displacing the core.
 */
@Composable
private fun LandscapeCenteredCoreWorkspace(
  uiState: WeykyUiState,
  dynamicCoreSize: androidx.compose.ui.unit.Dp,
  borderColor: Color,
  panelBg: Color,
  panelBgElevated: Color,
  textPrimary: Color,
  textSecondary: Color,
  onToggleVoice: () -> Unit,
  onQuerySubmit: (String) -> Unit,
  onConfirmOption: (String) -> Unit,
  onRequestPermission: (String) -> Unit,
  onToggleMediaPlayback: () -> Unit,
  onNextMediaTrack: () -> Unit,
  onPreviousMediaTrack: () -> Unit,
  modifier: Modifier = Modifier
) {
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

  BoxWithConstraints(
    modifier = modifier.fillMaxWidth(),
    contentAlignment = Alignment.Center
  ) {
    val availableWidth = maxWidth
    // Calculate the maximum width available for flanking side panels so they never touch or overlap the core
    val sidePanelMaxWidth = ((availableWidth - dynamicCoreSize) / 2f - 20.dp).coerceAtLeast(0.dp)

    // 1. CENTER LAYER: The Living Visual Core (Hero)
    // Anchored directly to Alignment.Center of the usable space
    HomeCoreSection(
      uiState = uiState,
      coreSizeDp = dynamicCoreSize,
      onToggleVoice = onToggleVoice,
      modifier = Modifier.align(Alignment.Center)
    )

    // 2. FLANKING CONTEXTUAL PANELS
    // Only displayed when there is sufficient side space to avoid crowding or covering the core
    if (sidePanelMaxWidth >= 140.dp) {
      val panelMaxConstraint = minOf(sidePanelMaxWidth, 350.dp)

      Row(
        modifier = Modifier
          .fillMaxSize()
          .padding(horizontal = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        // START SIDE PANEL: Assistant Response (or Music if no response)
        Box(
          modifier = Modifier
            .widthIn(max = panelMaxConstraint)
            .fillMaxHeight(),
          contentAlignment = Alignment.CenterStart
        ) {
          if (hasResponse) {
            Column(
              modifier = Modifier
                .widthIn(max = panelMaxConstraint)
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
          } else if (isMusicActivelyPlaying) {
            Column(
              modifier = Modifier
                .widthIn(max = panelMaxConstraint)
                .verticalScroll(rememberScrollState())
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
            }
          }
        }

        // END SIDE PANEL: Music / Alert cards (arranged on the opposite flank)
        Box(
          modifier = Modifier
            .widthIn(max = panelMaxConstraint)
            .fillMaxHeight(),
          contentAlignment = Alignment.CenterEnd
        ) {
          val showMusicOnEnd = hasResponse && isMusicActivelyPlaying
          val showAlertsOnEnd = hasAlert

          if (showMusicOnEnd || showAlertsOnEnd) {
            Column(
              modifier = Modifier
                .widthIn(max = panelMaxConstraint)
                .verticalScroll(rememberScrollState()),
              verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              if (showMusicOnEnd) {
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
              }

              if (showAlertsOnEnd) {
                HomeContextAlertCard(
                  uiState = uiState,
                  borderColor = borderColor,
                  onQuerySubmit = onQuerySubmit
                )
              }
            }
          }
        }
      }
    }
  }
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
  val isLandscape = deviceProfile.orientation == AdaptiveOrientation.LANDSCAPE &&
    deviceProfile.windowWidthDp >= 840.dp
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

  val hasActiveContextualContent = isMusicActivelyPlaying || hasResponse || hasAlert

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

    // 2. Wide Landscape Tablet: Visual Core is visually centered horizontally and vertically
    // Flanking side panels arrange optional contextual cards (Response, Music, Alerts) without displacing the core
    isLandscape -> {
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
        // Top information row: Weather + Date, centered with balanced width
        HomeTopRowCards(
          uiState = uiState,
          borderColor = borderColor,
          panelBg = panelBg,
          textPrimary = textPrimary,
          textSecondary = textSecondary,
          textMuted = textMuted,
          density = deviceProfile.cardDensity,
          onQuerySubmit = onQuerySubmit,
          modifier = Modifier.widthIn(max = 760.dp)
        )

        // Middle workspace: Visual Core centered horizontally and vertically, flanked by contextual panels
        LandscapeCenteredCoreWorkspace(
          uiState = uiState,
          dynamicCoreSize = dynamicCoreSize,
          borderColor = borderColor,
          panelBg = panelBg,
          panelBgElevated = panelBgElevated,
          textPrimary = textPrimary,
          textSecondary = textSecondary,
          onToggleVoice = onToggleVoice,
          onQuerySubmit = onQuerySubmit,
          onConfirmOption = onConfirmOption,
          onRequestPermission = onRequestPermission,
          onToggleMediaPlayback = onToggleMediaPlayback,
          onNextMediaTrack = onNextMediaTrack,
          onPreviousMediaTrack = onPreviousMediaTrack,
          modifier = Modifier.weight(1f)
        )

        // Bottom interaction row: Settings, Input, and App Drawer
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
          keyboardController = keyboardController,
          modifier = Modifier.widthIn(max = 760.dp)
        )
      }
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
