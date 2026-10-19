package com.example.ui.settings

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.state.WeykyUiState
import com.example.core.voice.model.VoiceModelInfo
import com.example.ui.adaptive.AdaptiveOrientation
import com.example.ui.adaptive.rememberAdaptiveLayoutState
import com.example.ui.theme.JarvisHudBadge
import com.example.ui.theme.JarvisHudCard
import com.example.ui.theme.JarvisHudColors
import com.example.ui.theme.jarvisTacticalBackground

@Composable
fun SettingsScreen(
  uiState: WeykyUiState,
  onBack: () -> Unit,
  onTogglePowerSaving: () -> Unit,
  onUpdateCoreScale: (Float) -> Unit,
  onSetLightMode: (Boolean) -> Unit = {},
  onUpdateBackgroundColor: (Long) -> Unit = {},
  onUpdateCardBorderColor: (Long) -> Unit = {},
  isNotificationAccessGranted: Boolean,
  onRequestDefaultLauncher: () -> Unit = {},
  onDownloadModel: (VoiceModelInfo) -> Unit = {},
  onInstallModel: (VoiceModelInfo) -> Unit = {},
  onInstallModelFromFile: (VoiceModelInfo, Uri) -> Unit = { _, _ -> },
  onDeleteModel: (VoiceModelInfo) -> Unit = {},
  onSelectActiveTtsModel: (VoiceModelInfo) -> Unit = {},
  onSetHybridTtsEnabled: (Boolean) -> Unit = {},
  onSetCloudTtsVoice: (String) -> Unit = {},
  onTestVoice: () -> Unit = {},
  onTestVoiceWithKey: (String, String) -> Unit = { _, _ -> },
  onToggleFloatingOverlay: (Boolean) -> Unit = {},
  onToggleNotificationPrivacy: (Boolean) -> Unit = {},
  onToggleHeadsetButton: (Boolean) -> Unit = {},
  modifier: Modifier = Modifier
) {
  BackHandler { onBack() }
  val context = LocalContext.current
  var pendingModelForFileInstall by remember {
    mutableStateOf<VoiceModelInfo?>(null)
  }
  val modelZipPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.GetContent()
  ) { uri: Uri? ->
    val targetModel = pendingModelForFileInstall
    if (uri != null && targetModel != null) {
      onInstallModelFromFile(targetModel, uri)
    }
    pendingModelForFileInstall = null
  }

  var selectedCategoryId by rememberSaveable { mutableStateOf(SettingsCategory.APPEARANCE.id) }
  val selectedCategory = SettingsCategory.fromId(selectedCategoryId)

  val isLight = uiState.isLightMode
  val bgColor = Color(uiState.backgroundColorHex)
  val borderColor = Color(uiState.cardBorderColorHex)
  val panelBg = JarvisHudColors.panelBg(isLight)
  val panelBgElevated = JarvisHudColors.panelBgElevated(isLight)
  val textPrimary = JarvisHudColors.textPrimary(isLight)
  val textSecondary = JarvisHudColors.textSecondary(isLight)

  BoxWithConstraints(
    modifier = modifier
      .fillMaxSize()
      .jarvisTacticalBackground(
        backgroundColor = bgColor,
        accentColor = borderColor
      )
      .statusBarsPadding()
      .navigationBarsPadding()
      .testTag("settings_screen_root")
  ) {
    val deviceProfile = rememberAdaptiveLayoutState(
      containerWidthDp = maxWidth,
      containerHeightDp = maxHeight
    )

    val isLandscapeTwoPane = deviceProfile.orientation == AdaptiveOrientation.LANDSCAPE &&
      maxWidth >= 580.dp &&
      (!deviceProfile.isSplitScreen || maxWidth >= 680.dp)

    if (isLandscapeTwoPane) {
      LandscapeDesktopSettingsLayout(
        selectedCategory = selectedCategory,
        onSelectCategory = { selectedCategoryId = it.id },
        uiState = uiState,
        isLight = isLight,
        borderColor = borderColor,
        panelBg = panelBg,
        panelBgElevated = panelBgElevated,
        textPrimary = textPrimary,
        textSecondary = textSecondary,
        onBack = onBack,
        onUpdateCoreScale = onUpdateCoreScale,
        onSetLightMode = onSetLightMode,
        onUpdateBackgroundColor = onUpdateBackgroundColor,
        onUpdateCardBorderColor = onUpdateCardBorderColor,
        onTogglePowerSaving = onTogglePowerSaving,
        onRequestDefaultLauncher = onRequestDefaultLauncher,
        isNotificationAccessGranted = isNotificationAccessGranted,
        onDownloadModel = onDownloadModel,
        onInstallModel = onInstallModel,
        onRequestFileInstall = { modelInfo -> pendingModelForFileInstall = modelInfo },
        modelZipPickerLauncher = modelZipPickerLauncher,
        onDeleteModel = onDeleteModel,
        onSelectActiveTtsModel = onSelectActiveTtsModel,
        onSetHybridTtsEnabled = onSetHybridTtsEnabled,
        onSetCloudTtsVoice = onSetCloudTtsVoice,
        onTestVoice = onTestVoice,
        onTestVoiceWithKey = onTestVoiceWithKey,
        context = context
      )
    } else {
      PortraitSettingsLayout(
        uiState = uiState,
        isLight = isLight,
        borderColor = borderColor,
        panelBg = panelBg,
        panelBgElevated = panelBgElevated,
        textPrimary = textPrimary,
        textSecondary = textSecondary,
        onBack = onBack,
        onUpdateCoreScale = onUpdateCoreScale,
        onSetLightMode = onSetLightMode,
        onUpdateBackgroundColor = onUpdateBackgroundColor,
        onUpdateCardBorderColor = onUpdateCardBorderColor,
        onTogglePowerSaving = onTogglePowerSaving,
        onRequestDefaultLauncher = onRequestDefaultLauncher,
        isNotificationAccessGranted = isNotificationAccessGranted,
        onDownloadModel = onDownloadModel,
        onInstallModel = onInstallModel,
        onRequestFileInstall = { modelInfo -> pendingModelForFileInstall = modelInfo },
        modelZipPickerLauncher = modelZipPickerLauncher,
        onDeleteModel = onDeleteModel,
        onSelectActiveTtsModel = onSelectActiveTtsModel,
        onSetHybridTtsEnabled = onSetHybridTtsEnabled,
        onSetCloudTtsVoice = onSetCloudTtsVoice,
        onTestVoice = onTestVoice,
        onTestVoiceWithKey = onTestVoiceWithKey,
        context = context
      )
    }
  }
}

@Composable
private fun LandscapeDesktopSettingsLayout(
  selectedCategory: SettingsCategory,
  onSelectCategory: (SettingsCategory) -> Unit,
  uiState: WeykyUiState,
  isLight: Boolean,
  borderColor: Color,
  panelBg: Color,
  panelBgElevated: Color,
  textPrimary: Color,
  textSecondary: Color,
  onBack: () -> Unit,
  onUpdateCoreScale: (Float) -> Unit,
  onSetLightMode: (Boolean) -> Unit,
  onUpdateBackgroundColor: (Long) -> Unit,
  onUpdateCardBorderColor: (Long) -> Unit,
  onTogglePowerSaving: () -> Unit,
  onRequestDefaultLauncher: () -> Unit,
  isNotificationAccessGranted: Boolean,
  onDownloadModel: (VoiceModelInfo) -> Unit,
  onInstallModel: (VoiceModelInfo) -> Unit,
  onRequestFileInstall: (VoiceModelInfo) -> Unit,
  modelZipPickerLauncher: androidx.activity.result.ActivityResultLauncher<String>,
  onDeleteModel: (VoiceModelInfo) -> Unit,
  onSelectActiveTtsModel: (VoiceModelInfo) -> Unit,
  onSetHybridTtsEnabled: (Boolean) -> Unit,
  onSetCloudTtsVoice: (String) -> Unit,
  onTestVoice: () -> Unit,
  onTestVoiceWithKey: (String, String) -> Unit,
  context: android.content.Context
) {
  val contentScrollState = rememberScrollState()

  LaunchedEffect(selectedCategory) {
    contentScrollState.scrollTo(0)
  }

  Row(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 12.dp, vertical = 8.dp)
  ) {
    // PANE A: MAIN SETTINGS CATEGORIES (NAVIGATION RAIL)
    Column(
      modifier = Modifier
        .width(260.dp)
        .fillMaxHeight()
        .padding(end = 12.dp)
        .testTag("settings_navigation_pane")
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(bottom = 12.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        JarvisHudBadge(
          accentColor = borderColor,
          containerColor = panelBg,
          cutSize = 6.dp
        ) {
          IconButton(
            onClick = onBack,
            modifier = Modifier
              .size(42.dp)
              .testTag("settings_back_button")
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "Back",
              tint = borderColor,
              modifier = Modifier.size(18.dp)
            )
          }
        }
        Column(modifier = Modifier.padding(start = 10.dp)) {
          Text(
            text = "SYS.CONFIG // TACTICAL",
            fontSize = 8.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
            color = borderColor.copy(alpha = 0.8f)
          )
          Text(
            text = "إعدادات WEYKY",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = textPrimary
          )
        }
      }

      Column(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        SettingsCategory.entries.forEach { category ->
          val isSelected = category == selectedCategory
          val itemShape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp, topEnd = 2.dp, bottomStart = 2.dp)

          Box(
            modifier = Modifier
              .fillMaxWidth()
              .clip(itemShape)
              .background(
                if (isSelected) {
                  borderColor.copy(alpha = if (isLight) 0.18f else 0.22f)
                } else {
                  panelBg.copy(alpha = 0.6f)
                }
              )
              .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) borderColor else textSecondary.copy(alpha = 0.25f),
                shape = itemShape
              )
              .clickable { onSelectCategory(category) }
              .padding(horizontal = 10.dp, vertical = 9.dp)
              .testTag("settings_category_tab_${category.id}")
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              Box(
                modifier = Modifier
                  .size(3.dp, 24.dp)
                  .background(if (isSelected) borderColor else Color.Transparent)
              )

              Icon(
                imageVector = category.icon,
                contentDescription = category.shortTitle,
                tint = if (isSelected) borderColor else textSecondary,
                modifier = Modifier.size(20.dp)
              )

              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = category.codeTag,
                  fontSize = 7.5.sp,
                  fontFamily = FontFamily.Monospace,
                  fontWeight = FontWeight.Bold,
                  letterSpacing = 0.5.sp,
                  color = if (isSelected) borderColor else textSecondary.copy(alpha = 0.7f)
                )
                Text(
                  text = category.shortTitle,
                  fontSize = 12.5.sp,
                  fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                  color = if (isSelected) textPrimary else textSecondary,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
              }
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(6.dp))

      Text(
        text = "WEYKY OS // DESKTOP CORE",
        fontSize = 8.sp,
        fontFamily = FontFamily.Monospace,
        color = borderColor.copy(alpha = 0.5f),
        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
      )
    }

    // VERTICAL DIVIDER
    Box(
      modifier = Modifier
        .width(1.dp)
        .fillMaxHeight()
        .background(borderColor.copy(alpha = 0.25f))
    )

    // PANE B: SELECTED CATEGORY CONTENT
    Column(
      modifier = Modifier
        .weight(1f)
        .fillMaxHeight()
        .padding(start = 16.dp)
        .testTag("settings_content_pane")
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(bottom = 10.dp, top = 4.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column {
            Text(
              text = selectedCategory.codeTag,
              fontSize = 9.sp,
              fontFamily = FontFamily.Monospace,
              fontWeight = FontWeight.Bold,
              letterSpacing = 1.sp,
              color = borderColor
            )
            Text(
              text = selectedCategory.title,
              fontSize = 18.sp,
              fontWeight = FontWeight.Bold,
              color = textPrimary
            )
          }

          JarvisHudBadge(
            accentColor = borderColor,
            containerColor = panelBgElevated,
            cutSize = 6.dp
          ) {
            Text(
              text = selectedCategory.shortTitle,
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              color = borderColor,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
          }
        }

        Text(
          text = selectedCategory.description,
          fontSize = 11.sp,
          color = textSecondary,
          modifier = Modifier.padding(top = 2.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(borderColor.copy(alpha = 0.18f))
        )
      }

      Column(
        modifier = Modifier
          .fillMaxSize()
          .verticalScroll(contentScrollState)
          .padding(bottom = 16.dp),
        horizontalAlignment = Alignment.Start
      ) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 780.dp)
        ) {
          Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            when (selectedCategory) {
              SettingsCategory.APPEARANCE -> {
                AppearanceSettingsContent(
                  uiState = uiState,
                  isLight = isLight,
                  borderColor = borderColor,
                  panelBg = panelBg,
                  textPrimary = textPrimary,
                  textSecondary = textSecondary,
                  onUpdateCoreScale = onUpdateCoreScale,
                  onSetLightMode = onSetLightMode,
                  onUpdateBackgroundColor = onUpdateBackgroundColor,
                  onUpdateCardBorderColor = onUpdateCardBorderColor,
                  isGrouped = true
                )
              }
              SettingsCategory.POWER -> {
                PowerSettingsContent(
                  uiState = uiState,
                  isLight = isLight,
                  borderColor = borderColor,
                  textPrimary = textPrimary,
                  textSecondary = textSecondary,
                  onTogglePowerSaving = onTogglePowerSaving,
                  isGrouped = true
                )
              }
              SettingsCategory.CONTEXT -> {
                TelemetrySettingsContent(
                  uiState = uiState,
                  isLight = isLight,
                  borderColor = borderColor,
                  textPrimary = textPrimary,
                  textSecondary = textSecondary,
                  isGrouped = true
                )
              }
              SettingsCategory.LAUNCHER -> {
                LauncherSettingsContent(
                  uiState = uiState,
                  isLight = isLight,
                  borderColor = borderColor,
                  textPrimary = textPrimary,
                  textSecondary = textSecondary,
                  onRequestDefaultLauncher = onRequestDefaultLauncher,
                  isGrouped = true
                )
              }
              SettingsCategory.VOICE -> {
                VoiceSettingsContent(
                  uiState = uiState,
                  isLight = isLight,
                  borderColor = borderColor,
                  panelBg = panelBg,
                  textPrimary = textPrimary,
                  textSecondary = textSecondary,
                  context = context,
                  modelZipPickerLauncher = modelZipPickerLauncher,
                  onRequestFileInstall = onRequestFileInstall,
                  onDownloadModel = onDownloadModel,
                  onInstallModel = onInstallModel,
                  onDeleteModel = onDeleteModel,
                  onSelectActiveTtsModel = onSelectActiveTtsModel,
                  onSetHybridTtsEnabled = onSetHybridTtsEnabled,
                  onSetCloudTtsVoice = onSetCloudTtsVoice,
                  onTestVoice = onTestVoice,
                  onTestVoiceWithKey = onTestVoiceWithKey,
                  isGrouped = true
                )
              }
              SettingsCategory.PERMISSIONS -> {
                PermissionsSettingsContent(
                  isNotificationAccessGranted = isNotificationAccessGranted,
                  isLight = isLight,
                  borderColor = borderColor,
                  textPrimary = textPrimary,
                  textSecondary = textSecondary,
                  context = context,
                  isGrouped = true
                )
              }
              SettingsCategory.ABOUT -> {
                AboutSettingsContent(
                  isLight = isLight,
                  borderColor = borderColor,
                  textPrimary = textPrimary,
                  textSecondary = textSecondary,
                  isGrouped = true
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun PortraitSettingsLayout(
  uiState: WeykyUiState,
  isLight: Boolean,
  borderColor: Color,
  panelBg: Color,
  panelBgElevated: Color,
  textPrimary: Color,
  textSecondary: Color,
  onBack: () -> Unit,
  onUpdateCoreScale: (Float) -> Unit,
  onSetLightMode: (Boolean) -> Unit,
  onUpdateBackgroundColor: (Long) -> Unit,
  onUpdateCardBorderColor: (Long) -> Unit,
  onTogglePowerSaving: () -> Unit,
  onRequestDefaultLauncher: () -> Unit,
  isNotificationAccessGranted: Boolean,
  onDownloadModel: (VoiceModelInfo) -> Unit,
  onInstallModel: (VoiceModelInfo) -> Unit,
  onRequestFileInstall: (VoiceModelInfo) -> Unit,
  modelZipPickerLauncher: androidx.activity.result.ActivityResultLauncher<String>,
  onDeleteModel: (VoiceModelInfo) -> Unit,
  onSelectActiveTtsModel: (VoiceModelInfo) -> Unit,
  onSetHybridTtsEnabled: (Boolean) -> Unit,
  onSetCloudTtsVoice: (String) -> Unit,
  onTestVoice: () -> Unit,
  onTestVoiceWithKey: (String, String) -> Unit,
  context: android.content.Context
) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 20.dp)
      .verticalScroll(rememberScrollState()),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .widthIn(max = 840.dp)
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        JarvisHudBadge(
          accentColor = borderColor,
          containerColor = panelBg,
          cutSize = 8.dp
        ) {
          IconButton(
            onClick = onBack,
            modifier = Modifier
              .size(48.dp)
              .testTag("settings_back_button")
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "Back",
              tint = borderColor
            )
          }
        }
        Column(modifier = Modifier.padding(start = 12.dp)) {
          Text(
            text = "SYS.CONFIG // TACTICAL PARAMETERS",
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
            color = borderColor.copy(alpha = 0.8f)
          )
          Text(
            text = "إعدادات WEYKY",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = textPrimary
          )
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      SettingsGroup(
        title = SettingsCategory.APPEARANCE.title,
        codeTag = SettingsCategory.APPEARANCE.codeTag,
        icon = SettingsCategory.APPEARANCE.icon,
        accentColor = borderColor,
        containerColor = panelBg,
        textPrimary = textPrimary
      ) {
        AppearanceSettingsContent(
          uiState = uiState,
          isLight = isLight,
          borderColor = borderColor,
          panelBg = panelBg,
          textPrimary = textPrimary,
          textSecondary = textSecondary,
          onUpdateCoreScale = onUpdateCoreScale,
          onSetLightMode = onSetLightMode,
          onUpdateBackgroundColor = onUpdateBackgroundColor,
          onUpdateCardBorderColor = onUpdateCardBorderColor,
          isGrouped = false
        )
      }

      Spacer(modifier = Modifier.height(16.dp))

      SettingsGroup(
        title = SettingsCategory.POWER.title,
        codeTag = SettingsCategory.POWER.codeTag,
        icon = SettingsCategory.POWER.icon,
        accentColor = borderColor,
        containerColor = panelBg,
        textPrimary = textPrimary
      ) {
        PowerSettingsContent(
          uiState = uiState,
          isLight = isLight,
          borderColor = borderColor,
          textPrimary = textPrimary,
          textSecondary = textSecondary,
          onTogglePowerSaving = onTogglePowerSaving,
          isGrouped = false
        )
      }

      Spacer(modifier = Modifier.height(16.dp))

      SettingsGroup(
        title = SettingsCategory.CONTEXT.title,
        codeTag = SettingsCategory.CONTEXT.codeTag,
        icon = SettingsCategory.CONTEXT.icon,
        accentColor = borderColor,
        containerColor = panelBg,
        textPrimary = textPrimary
      ) {
        TelemetrySettingsContent(
          uiState = uiState,
          isLight = isLight,
          borderColor = borderColor,
          textPrimary = textPrimary,
          textSecondary = textSecondary,
          isGrouped = false
        )
      }

      Spacer(modifier = Modifier.height(16.dp))

      SettingsGroup(
        title = SettingsCategory.LAUNCHER.title,
        codeTag = SettingsCategory.LAUNCHER.codeTag,
        icon = SettingsCategory.LAUNCHER.icon,
        accentColor = borderColor,
        containerColor = panelBg,
        textPrimary = textPrimary
      ) {
        LauncherSettingsContent(
          uiState = uiState,
          isLight = isLight,
          borderColor = borderColor,
          textPrimary = textPrimary,
          textSecondary = textSecondary,
          onRequestDefaultLauncher = onRequestDefaultLauncher,
          isGrouped = false
        )
      }

      Spacer(modifier = Modifier.height(16.dp))

      SettingsGroup(
        title = SettingsCategory.VOICE.title,
        codeTag = SettingsCategory.VOICE.codeTag,
        icon = SettingsCategory.VOICE.icon,
        accentColor = borderColor,
        containerColor = panelBg,
        textPrimary = textPrimary
      ) {
        VoiceSettingsContent(
          uiState = uiState,
          isLight = isLight,
          borderColor = borderColor,
          panelBg = panelBg,
          textPrimary = textPrimary,
          textSecondary = textSecondary,
          context = context,
          modelZipPickerLauncher = modelZipPickerLauncher,
          onRequestFileInstall = onRequestFileInstall,
          onDownloadModel = onDownloadModel,
          onInstallModel = onInstallModel,
          onDeleteModel = onDeleteModel,
          onSelectActiveTtsModel = onSelectActiveTtsModel,
          onSetHybridTtsEnabled = onSetHybridTtsEnabled,
          onSetCloudTtsVoice = onSetCloudTtsVoice,
          onTestVoice = onTestVoice,
          onTestVoiceWithKey = onTestVoiceWithKey,
          isGrouped = false
        )
      }

      Spacer(modifier = Modifier.height(16.dp))

      SettingsGroup(
        title = SettingsCategory.PERMISSIONS.title,
        codeTag = SettingsCategory.PERMISSIONS.codeTag,
        icon = SettingsCategory.PERMISSIONS.icon,
        accentColor = borderColor,
        containerColor = panelBg,
        textPrimary = textPrimary
      ) {
        PermissionsSettingsContent(
          isNotificationAccessGranted = isNotificationAccessGranted,
          isLight = isLight,
          borderColor = borderColor,
          textPrimary = textPrimary,
          textSecondary = textSecondary,
          context = context,
          isGrouped = false
        )
      }

      Spacer(modifier = Modifier.height(16.dp))

      SettingsGroup(
        title = SettingsCategory.ABOUT.title,
        codeTag = SettingsCategory.ABOUT.codeTag,
        icon = SettingsCategory.ABOUT.icon,
        accentColor = borderColor,
        containerColor = panelBg,
        textPrimary = textPrimary
      ) {
        AboutSettingsContent(
          isLight = isLight,
          borderColor = borderColor,
          textPrimary = textPrimary,
          textSecondary = textSecondary,
          isGrouped = false
        )
      }

      Spacer(modifier = Modifier.height(30.dp))
    }
  }
}

@Composable
private fun SettingsGroup(
  title: String,
  codeTag: String,
  icon: ImageVector,
  accentColor: Color = JarvisHudColors.HologramCyan,
  containerColor: Color = JarvisHudColors.PanelBg,
  textPrimary: Color = JarvisHudColors.TextPrimary,
  content: @Composable () -> Unit
) {
  JarvisHudCard(
    accentColor = accentColor,
    containerColor = containerColor,
    cutCornerSize = 14.dp,
    bracketLength = 14.dp,
    tabTitle = codeTag,
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Icon(
          imageVector = icon,
          contentDescription = title,
          tint = accentColor,
          modifier = Modifier.size(20.dp)
        )
        Text(
          text = title,
          fontSize = 15.sp,
          fontWeight = FontWeight.Bold,
          color = textPrimary
        )
      }
      content()
    }
  }
}
