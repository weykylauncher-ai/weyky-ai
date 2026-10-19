package com.example.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.PhoneMissed
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhoneCallback
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.state.ImportantEvent
import com.example.core.state.WeykyUiState
import com.example.device.battery.ChargingState
import com.example.rendering.renderer.WeykyCoreView
import com.example.ui.adaptive.CardDensity
import com.example.ui.theme.HudTabPosition
import com.example.ui.theme.JarvisHudBadge
import com.example.ui.theme.JarvisHudCard
import com.example.ui.theme.JarvisHudColors
import kotlinx.coroutines.delay

@Composable
fun HudVerticalDivider(color: Color, heightDp: Dp = 44.dp) {
  Box(
    modifier = Modifier
      .width(1.dp)
      .height(heightDp)
      .background(color.copy(alpha = 0.18f))
  )
}

@Composable
fun HomeTopActionBar(
  borderColor: Color,
  panelBgElevated: Color,
  textPrimary: Color,
  onToggleAppDrawer: () -> Unit,
  onOpenSettings: () -> Unit,
  modifier: Modifier = Modifier
) {
  Row(
    modifier = modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    // Left / Start: Installed Apps Button (التطبيقات / APPS)
    JarvisHudBadge(
      accentColor = borderColor,
      containerColor = panelBgElevated,
      cutSize = 8.dp,
      onClick = onToggleAppDrawer,
      modifier = Modifier
        .height(48.dp)
        .testTag("app_drawer_button")
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
      ) {
        Icon(
          imageVector = Icons.Default.Apps,
          contentDescription = "Applications",
          tint = borderColor,
          modifier = Modifier.size(20.dp)
        )
        Text(
          text = "التطبيقات // APPS",
          fontSize = 12.sp,
          fontFamily = FontFamily.Monospace,
          fontWeight = FontWeight.Bold,
          letterSpacing = 0.8.sp,
          color = textPrimary
        )
      }
    }

    // Right / End: Settings Button (الإعدادات / SETTINGS)
    JarvisHudBadge(
      accentColor = borderColor,
      containerColor = panelBgElevated,
      cutSize = 8.dp,
      onClick = onOpenSettings,
      modifier = Modifier
        .height(48.dp)
        .testTag("settings_button")
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
      ) {
        Icon(
          imageVector = Icons.Default.Settings,
          contentDescription = "Settings",
          tint = borderColor,
          modifier = Modifier.size(20.dp)
        )
        Text(
          text = "الإعدادات // CFG",
          fontSize = 12.sp,
          fontFamily = FontFamily.Monospace,
          fontWeight = FontWeight.Bold,
          letterSpacing = 0.8.sp,
          color = textPrimary
        )
      }
    }
  }
}

/**
 * Top Row Cards: Weather Card & Date Card side-by-side in one horizontal row.
 * Strictly adheres to Section 4.1 of the Master Specification:
 * - Real Open-Meteo weather data (temperature, condition, humidity, wind).
 * - Real device date (Arabic & English formatted).
 * - No redundant clock/battery/network duplication.
 * - Perfectly responsive without text clipping.
 */
@Composable
fun HomeWeatherCard(
  uiState: WeykyUiState,
  borderColor: Color,
  panelBg: Color,
  textPrimary: Color,
  textSecondary: Color,
  textMuted: Color,
  density: CardDensity = CardDensity.STANDARD,
  onQuerySubmit: (String) -> Unit,
  modifier: Modifier = Modifier
) {
  val isCompact = density == CardDensity.COMPACT
  val weather = uiState.weatherSnapshot
  val tempVal = weather?.temperatureCelsius
  val tempText = if (weather != null && weather.isAvailable && tempVal != null) {
    "${tempVal.toInt()}°C"
  } else {
    "--°C"
  }
  val condition = weather?.condition?.ifBlank { null } ?: if (weather == null) "جاري الفحص..." else "الطقس"

  val humidity = weather?.humidityPercent
  val wind = weather?.windSpeedKmH
  val subDetails = buildString {
    if (humidity != null) append("💧 $humidity%")
    if (wind != null) {
      if (isNotEmpty()) append(" • ")
      append("💨 ${wind.toInt()} كم/س")
    }
    if (weather?.isCached == true) {
      if (isNotEmpty()) append(" • ")
      append("مُخزّن")
    }
  }.ifBlank {
    if (weather?.isAvailable == false) "بدون إنترنت" else "محطة الطقس"
  }

  val weatherIcon = when {
    condition.contains("مطر", ignoreCase = true) || condition.contains("rain", ignoreCase = true) -> Icons.Default.WaterDrop
    condition.contains("شمس", ignoreCase = true) || condition.contains("صحو", ignoreCase = true) || condition.contains("صاف", ignoreCase = true) -> Icons.Default.WbSunny
    condition.contains("عاصف", ignoreCase = true) || condition.contains("رعد", ignoreCase = true) -> Icons.Default.Warning
    else -> Icons.Default.CloudQueue
  }

  JarvisHudCard(
    accentColor = borderColor,
    containerColor = panelBg,
    cutCornerSize = if (isCompact) 10.dp else 14.dp,
    tabTitle = null,
    tabPosition = HudTabPosition.NONE,
    onClick = { onQuerySubmit("ما حالة الطقس؟") },
    modifier = modifier.testTag("home_weather_card")
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = if (isCompact) 10.dp else 12.dp, vertical = if (isCompact) 8.dp else 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Box(
        modifier = Modifier
          .size(if (isCompact) 36.dp else 42.dp)
          .clip(CircleShape)
          .background(borderColor.copy(alpha = 0.12f))
          .border(1.dp, borderColor.copy(alpha = 0.5f), CircleShape),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = weatherIcon,
          contentDescription = "Weather",
          tint = borderColor,
          modifier = Modifier.size(if (isCompact) 20.dp else 24.dp)
        )
      }

      Column(modifier = Modifier.weight(1f)) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Text(
            text = tempText,
            fontSize = if (isCompact) 16.sp else 18.sp,
            fontWeight = FontWeight.Bold,
            color = textPrimary,
            letterSpacing = 0.5.sp
          )
          Text(
            text = condition,
            fontSize = if (isCompact) 11.sp else 12.sp,
            color = textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
          text = subDetails,
          fontSize = if (isCompact) 8.sp else 9.sp,
          fontFamily = FontFamily.Monospace,
          color = textMuted,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
    }
  }
}

@Composable
fun HomeDateCard(
  uiState: WeykyUiState,
  borderColor: Color,
  panelBg: Color,
  textPrimary: Color,
  textSecondary: Color,
  textMuted: Color,
  density: CardDensity = CardDensity.STANDARD,
  onQuerySubmit: (String) -> Unit,
  modifier: Modifier = Modifier
) {
  val isCompact = density == CardDensity.COMPACT
  val primaryDate = uiState.currentDateString.ifBlank { "الجمعة، 16 أكتوبر" }
  val englishDate = uiState.currentEnglishDateString.ifBlank { "Friday, 16 Oct" }

  JarvisHudCard(
    accentColor = borderColor,
    containerColor = panelBg,
    cutCornerSize = if (isCompact) 10.dp else 14.dp,
    tabTitle = null,
    tabPosition = HudTabPosition.NONE,
    onClick = { onQuerySubmit("ما تاريخ اليوم؟") },
    modifier = modifier.testTag("home_date_card")
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = if (isCompact) 10.dp else 12.dp, vertical = if (isCompact) 8.dp else 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Box(
        modifier = Modifier
          .size(if (isCompact) 36.dp else 42.dp)
          .clip(CircleShape)
          .background(borderColor.copy(alpha = 0.12f))
          .border(1.dp, borderColor.copy(alpha = 0.5f), CircleShape),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Default.DateRange,
          contentDescription = "Date",
          tint = borderColor,
          modifier = Modifier.size(if (isCompact) 18.dp else 22.dp)
        )
      }

      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = primaryDate,
          fontSize = if (isCompact) 13.sp else 14.sp,
          fontWeight = FontWeight.Bold,
          color = textPrimary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
          text = englishDate.uppercase(),
          fontSize = if (isCompact) 8.sp else 9.sp,
          fontFamily = FontFamily.Monospace,
          letterSpacing = 0.8.sp,
          color = textSecondary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
    }
  }
}

@Composable
fun HomeTopRowCards(
  uiState: WeykyUiState,
  borderColor: Color,
  panelBg: Color,
  textPrimary: Color,
  textSecondary: Color,
  textMuted: Color,
  density: CardDensity = CardDensity.STANDARD,
  onQuerySubmit: (String) -> Unit,
  modifier: Modifier = Modifier
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .testTag("home_top_row_cards"),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    HomeWeatherCard(
      uiState = uiState,
      borderColor = borderColor,
      panelBg = panelBg,
      textPrimary = textPrimary,
      textSecondary = textSecondary,
      textMuted = textMuted,
      density = density,
      onQuerySubmit = onQuerySubmit,
      modifier = Modifier.weight(1f)
    )

    HomeDateCard(
      uiState = uiState,
      borderColor = borderColor,
      panelBg = panelBg,
      textPrimary = textPrimary,
      textSecondary = textSecondary,
      textMuted = textMuted,
      density = density,
      onQuerySubmit = onQuerySubmit,
      modifier = Modifier.weight(1f)
    )
  }
}

/**
 * Bottom Control Row: Settings button, flexible text input area, and installed apps button
 * in a single, perfectly unified horizontal row per Section 4.3 of the Master Specification.
 */
@Composable
fun HomeBottomControlRow(
  uiState: WeykyUiState,
  borderColor: Color,
  panelBg: Color,
  panelBgElevated: Color,
  textPrimary: Color,
  textSecondary: Color,
  isLight: Boolean,
  onQueryChange: (String) -> Unit,
  onQuerySubmit: (String) -> Unit,
  onToggleVoice: () -> Unit,
  onOpenSettings: () -> Unit,
  onToggleAppDrawer: () -> Unit,
  keyboardController: SoftwareKeyboardController?,
  modifier: Modifier = Modifier
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .testTag("home_bottom_bar"),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    // 1. Settings Button (Flank 1)
    JarvisHudBadge(
      accentColor = borderColor,
      containerColor = panelBgElevated,
      cutSize = 8.dp,
      onClick = onOpenSettings,
      modifier = Modifier
        .size(48.dp)
        .testTag("settings_button")
    ) {
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Icon(
          imageVector = Icons.Default.Settings,
          contentDescription = "Settings",
          tint = borderColor,
          modifier = Modifier.size(22.dp)
        )
      }
    }

    // 2. WEYKY Text Input Area (Center, Flexible Width weight(1f))
    val activeInputAccent = if (uiState.isVoiceListening) JarvisHudColors.MagentaPulse else borderColor
    JarvisHudCard(
      accentColor = activeInputAccent,
      containerColor = panelBg,
      cutCornerSize = 12.dp,
      tabPosition = HudTabPosition.NONE,
      modifier = Modifier
        .weight(1f)
        .height(48.dp)
        .testTag("home_bottom_input_area")
    ) {
      Row(
        modifier = Modifier
          .fillMaxSize()
          .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        // Voice Mic Button
        IconButton(
          onClick = onToggleVoice,
          modifier = Modifier
            .size(38.dp)
            .testTag("voice_input_button")
        ) {
          Icon(
            imageVector = Icons.Default.Mic,
            contentDescription = if (uiState.isVoiceListening) "Listening... tap to cancel" else "Voice Input",
            tint = if (uiState.isVoiceListening) JarvisHudColors.MagentaPulse else borderColor,
            modifier = Modifier.size(20.dp)
          )
        }

        // Text Input Field
        Box(
          modifier = Modifier
            .weight(1f)
            .padding(horizontal = 4.dp),
          contentAlignment = Alignment.CenterStart
        ) {
          if (uiState.inputQuery.isEmpty()) {
            val hint = if (uiState.isVoiceListening) "Listening... تكلم الآن" else "Ask WEYKY anything..."
            Text(
              text = hint,
              fontSize = 13.sp,
              color = if (uiState.isVoiceListening) JarvisHudColors.MagentaPulse else textSecondary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
          BasicTextField(
            value = uiState.inputQuery,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = TextStyle(
              color = textPrimary,
              fontSize = 14.sp,
              fontWeight = FontWeight.Normal
            ),
            cursorBrush = SolidColor(borderColor),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = {
              if (uiState.inputQuery.isNotBlank()) {
                onQuerySubmit(uiState.inputQuery)
                keyboardController?.hide()
              }
            }),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("ask_weyky_input")
          )
        }

        // Submit Button
        IconButton(
          onClick = {
            if (uiState.inputQuery.isNotBlank()) {
              onQuerySubmit(uiState.inputQuery)
              keyboardController?.hide()
            } else {
              onQuerySubmit("ماذا أفعل الآن؟")
            }
          },
          modifier = Modifier
            .size(36.dp)
            .testTag("submit_query_button")
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = "Send",
            tint = borderColor,
            modifier = Modifier.size(18.dp)
          )
        }
      }
    }

    // 3. Installed Apps Button (Flank 2)
    JarvisHudBadge(
      accentColor = borderColor,
      containerColor = panelBgElevated,
      cutSize = 8.dp,
      onClick = onToggleAppDrawer,
      modifier = Modifier
        .size(48.dp)
        .testTag("app_drawer_button")
    ) {
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Icon(
          imageVector = Icons.Default.Apps,
          contentDescription = "Applications",
          tint = borderColor,
          modifier = Modifier.size(22.dp)
        )
      }
    }
  }
}

/**
 * WEYKY Input Card (Dedicated typing and voice bar for landscape middle column):
 * Houses voice mic button, expandable query input, and send button.
 */
@Composable
fun HomeInputCard(
  uiState: WeykyUiState,
  borderColor: Color,
  panelBg: Color,
  textPrimary: Color,
  textSecondary: Color,
  onQueryChange: (String) -> Unit,
  onQuerySubmit: (String) -> Unit,
  onToggleVoice: () -> Unit,
  keyboardController: SoftwareKeyboardController?,
  modifier: Modifier = Modifier
) {
  val activeInputAccent = if (uiState.isVoiceListening) JarvisHudColors.MagentaPulse else borderColor
  JarvisHudCard(
    accentColor = activeInputAccent,
    containerColor = panelBg,
    cutCornerSize = 12.dp,
    tabPosition = HudTabPosition.NONE,
    modifier = modifier
      .fillMaxWidth()
      .height(48.dp)
      .testTag("home_bottom_bar")
  ) {
    Row(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 6.dp)
        .testTag("home_bottom_input_area"),
      verticalAlignment = Alignment.CenterVertically
    ) {
      // Voice Mic Button
      IconButton(
        onClick = onToggleVoice,
        modifier = Modifier
          .size(38.dp)
          .testTag("voice_input_button")
      ) {
        Icon(
          imageVector = Icons.Default.Mic,
          contentDescription = if (uiState.isVoiceListening) "Listening... tap to cancel" else "Voice Input",
          tint = if (uiState.isVoiceListening) JarvisHudColors.MagentaPulse else borderColor,
          modifier = Modifier.size(20.dp)
        )
      }

      // Text Input Field
      Box(
        modifier = Modifier
          .weight(1f)
          .padding(horizontal = 4.dp),
        contentAlignment = Alignment.CenterStart
      ) {
        if (uiState.inputQuery.isEmpty()) {
          val hint = if (uiState.isVoiceListening) "Listening... تكلم الآن" else "Ask WEYKY anything..."
          Text(
            text = hint,
            fontSize = 13.sp,
            color = if (uiState.isVoiceListening) JarvisHudColors.MagentaPulse else textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
        BasicTextField(
          value = uiState.inputQuery,
          onValueChange = onQueryChange,
          singleLine = true,
          textStyle = TextStyle(
            color = textPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal
          ),
          cursorBrush = SolidColor(borderColor),
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
          keyboardActions = KeyboardActions(onSend = {
            if (uiState.inputQuery.isNotBlank()) {
              onQuerySubmit(uiState.inputQuery)
              keyboardController?.hide()
            }
          }),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("ask_weyky_input")
        )
      }

      // Submit Button
      IconButton(
        onClick = {
          if (uiState.inputQuery.isNotBlank()) {
            onQuerySubmit(uiState.inputQuery)
            keyboardController?.hide()
          } else {
            onQuerySubmit("ماذا أفعل الآن؟")
          }
        },
        modifier = Modifier
          .size(36.dp)
          .testTag("submit_query_button")
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.ArrowForward,
          contentDescription = "Send",
          tint = borderColor,
          modifier = Modifier.size(18.dp)
        )
      }
    }
  }
}

/**
 * Landscape Side Settings Button:
 * Dedicated tactical action button placed in the side column.
 */
@Composable
fun HomeLandscapeSettingsButton(
  borderColor: Color,
  panelBgElevated: Color,
  textPrimary: Color,
  isRtl: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  JarvisHudBadge(
    accentColor = borderColor,
    containerColor = panelBgElevated,
    cutSize = 8.dp,
    onClick = onClick,
    modifier = modifier
      .fillMaxWidth()
      .height(44.dp)
      .testTag("settings_button")
  ) {
    Row(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center
    ) {
      Icon(
        imageVector = Icons.Default.Settings,
        contentDescription = "Settings",
        tint = borderColor,
        modifier = Modifier.size(20.dp)
      )
      Spacer(modifier = Modifier.width(6.dp))
      Text(
        text = if (isRtl) "الإعدادات" else "Settings",
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = textPrimary,
        maxLines = 1
      )
    }
  }
}

/**
 * Landscape Side App Drawer Button:
 * Dedicated tactical action button placed in the side column.
 */
@Composable
fun HomeLandscapeAppDrawerButton(
  borderColor: Color,
  panelBgElevated: Color,
  textPrimary: Color,
  isRtl: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  JarvisHudBadge(
    accentColor = borderColor,
    containerColor = panelBgElevated,
    cutSize = 8.dp,
    onClick = onClick,
    modifier = modifier
      .fillMaxWidth()
      .height(44.dp)
      .testTag("app_drawer_button")
  ) {
    Row(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center
    ) {
      Icon(
        imageVector = Icons.Default.Apps,
        contentDescription = "Applications",
        tint = borderColor,
        modifier = Modifier.size(20.dp)
      )
      Spacer(modifier = Modifier.width(6.dp))
      Text(
        text = if (isRtl) "التطبيقات" else "Apps",
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = textPrimary,
        maxLines = 1
      )
    }
  }
}

@Composable
fun HomeTelemetryCard(
  uiState: WeykyUiState,
  borderColor: Color,
  panelBg: Color,
  textPrimary: Color,
  textSecondary: Color,
  textMuted: Color,
  density: CardDensity = CardDensity.STANDARD,
  onQuerySubmit: (String) -> Unit,
  modifier: Modifier = Modifier
) {
  val isCompact = density == CardDensity.COMPACT
  val timeFontSize = if (isCompact) 22.sp else 28.sp
  val vertPadding = if (isCompact) 10.dp else 14.dp
  val horizPadding = if (isCompact) 10.dp else 14.dp

  JarvisHudCard(
    accentColor = borderColor,
    containerColor = panelBg,
    cutCornerSize = if (isCompact) 12.dp else 16.dp,
    tabTitle = null,
    tabPosition = HudTabPosition.NONE,
    modifier = modifier
      .fillMaxWidth()
      .testTag("hud_telemetry_bar")
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = horizPadding, vertical = vertPadding),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      // COLUMN 1: Time & Date (Left)
      val cleanTime = uiState.currentTimeString
        .replace(" AM", "")
        .replace(" PM", "")
        .replace(" ص", "")
        .replace(" م", "")
        .ifEmpty { "11:02" }
      Column(
        modifier = Modifier.weight(1.15f),
        horizontalAlignment = Alignment.Start
      ) {
        Text(
          text = cleanTime,
          fontSize = timeFontSize,
          fontWeight = FontWeight.Bold,
          color = textPrimary,
          letterSpacing = 0.5.sp,
          modifier = Modifier.testTag("current_time_text")
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
          text = uiState.currentDateString.uppercase().ifEmpty { "22 AUG 2026" },
          fontSize = if (isCompact) 9.sp else 10.sp,
          fontFamily = FontFamily.Monospace,
          fontWeight = FontWeight.Medium,
          letterSpacing = 0.8.sp,
          color = textSecondary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }

      HudVerticalDivider(color = borderColor, heightDp = if (isCompact) 36.dp else 44.dp)

      // COLUMN 2: Weather (Cloud Icon + Temp + WEATHER label)
      val weather = uiState.weatherSnapshot
      val tempVal = weather?.temperatureCelsius
      val tempText = if (weather != null && weather.isAvailable && tempVal != null) {
        "${tempVal.toInt()}°C"
      } else {
        "--°C"
      }
      Row(
        modifier = Modifier
          .weight(1.1f)
          .clickable { onQuerySubmit("ما حالة الطقس؟") }
          .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
      ) {
        Icon(
          imageVector = Icons.Default.CloudQueue,
          contentDescription = "Weather",
          tint = borderColor,
          modifier = Modifier.size(if (isCompact) 20.dp else 24.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Column(horizontalAlignment = Alignment.Start) {
          Text(
            text = tempText,
            fontSize = if (isCompact) 14.sp else 16.sp,
            fontWeight = FontWeight.Bold,
            color = textPrimary
          )
          Text(
            text = "WEATHER",
            fontSize = if (isCompact) 8.sp else 9.sp,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.8.sp,
            color = textSecondary
          )
        }
      }

      HudVerticalDivider(color = borderColor, heightDp = if (isCompact) 36.dp else 44.dp)

      // COLUMN 3: Battery (Icon + Percentage + CHARGING / BATTERY)
      val b = uiState.batterySnapshot
      val battPct = if (b != null && b.isAvailable) "${b.percentage}%" else "--%"
      val isCharging = b?.chargingState == ChargingState.CHARGING || b?.chargingState == ChargingState.FULL
      Column(
        modifier = Modifier
          .weight(0.95f)
          .clickable { onQuerySubmit("حالة البطارية") },
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Icon(
          imageVector = if (isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryFull,
          contentDescription = "Battery",
          tint = if (b != null && b.percentage <= 20) JarvisHudColors.CrimsonAlert else borderColor,
          modifier = Modifier.size(if (isCompact) 18.dp else 20.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
          text = battPct,
          fontSize = if (isCompact) 12.sp else 14.sp,
          fontWeight = FontWeight.Bold,
          color = textPrimary
        )
        Text(
          text = if (isCharging) "CHARGING" else "DISCHARGING",
          fontSize = 7.sp,
          fontFamily = FontFamily.Monospace,
          letterSpacing = 0.5.sp,
          color = textSecondary,
          maxLines = 1
        )
        Text(
          text = "BATTERY",
          fontSize = 7.sp,
          fontFamily = FontFamily.Monospace,
          letterSpacing = 0.5.sp,
          color = textMuted,
          maxLines = 1
        )
      }

      HudVerticalDivider(color = borderColor, heightDp = if (isCompact) 36.dp else 44.dp)

      // COLUMN 4: Network (Wifi Icon + WIFI + CONNECTED / NETWORK)
      val n = uiState.networkSnapshot
      val netConnected = n?.isConnected == true
      val netTitle = when {
        n == null || !n.isConnected -> "OFFLINE"
        n.typeLabel.contains("Wi-Fi", ignoreCase = true) || n.typeLabel.contains("واي", ignoreCase = true) -> "WIFI"
        else -> n.typeLabel.uppercase()
      }
      Column(
        modifier = Modifier
          .weight(0.95f)
          .clickable { onQuerySubmit("حالة الشبكة") },
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Icon(
          imageVector = Icons.Default.Wifi,
          contentDescription = "Network",
          tint = if (netConnected) borderColor else textMuted,
          modifier = Modifier.size(if (isCompact) 18.dp else 20.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
          text = netTitle,
          fontSize = if (isCompact) 11.sp else 13.sp,
          fontWeight = FontWeight.Bold,
          color = textPrimary,
          maxLines = 1
        )
        Text(
          text = if (netConnected) "CONNECTED" else "DISCONNECTED",
          fontSize = 7.sp,
          fontFamily = FontFamily.Monospace,
          letterSpacing = 0.5.sp,
          color = textSecondary,
          maxLines = 1
        )
        Text(
          text = "NETWORK",
          fontSize = 7.sp,
          fontFamily = FontFamily.Monospace,
          letterSpacing = 0.5.sp,
          color = textMuted,
          maxLines = 1
        )
      }
    }
  }
}

@Composable
fun HomeMusicCard(
  uiState: WeykyUiState,
  borderColor: Color,
  panelBgElevated: Color,
  textPrimary: Color,
  textSecondary: Color,
  onToggleMediaPlayback: () -> Unit,
  onNextMediaTrack: () -> Unit,
  onPreviousMediaTrack: () -> Unit,
  modifier: Modifier = Modifier
) {
  val mediaMeta = uiState.activeMediaMetadata
  val isMusicActivelyPlaying = uiState.isMusicPlaying || (mediaMeta?.isPlaying == true)

  AnimatedVisibility(
    visible = isMusicActivelyPlaying,
    enter = fadeIn() + expandVertically(),
    exit = fadeOut() + shrinkVertically(),
    modifier = modifier
  ) {
    val trackTitle = mediaMeta?.title?.takeIf { it.isNotBlank() } ?: "جاري تشغيل الموسيقى"
    val trackArtist = mediaMeta?.artist?.takeIf { it.isNotBlank() } ?: "AUDIO STREAM // ACTIVE"
    Column {
      Spacer(modifier = Modifier.height(6.dp))
      JarvisHudCard(
        accentColor = borderColor,
        containerColor = panelBgElevated,
        cutCornerSize = 14.dp,
        tabTitle = "MUSIC",
        tabPosition = HudTabPosition.TOP_START,
        modifier = Modifier
          .fillMaxWidth()
          .testTag("music_top_hud_card")
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
          ) {
            Box(
              modifier = Modifier
                .size(40.dp)
                .shadow(
                  elevation = 10.dp,
                  shape = CircleShape,
                  ambientColor = JarvisHudColors.MagentaPulse,
                  spotColor = JarvisHudColors.MagentaPulse
                )
                .clip(CircleShape)
                .background(JarvisHudColors.MagentaPulse.copy(alpha = 0.16f))
                .border(1.5.dp, JarvisHudColors.MagentaPulse.copy(alpha = 0.85f), CircleShape),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Default.GraphicEq,
                contentDescription = "Now Playing",
                tint = JarvisHudColors.MagentaPulse,
                modifier = Modifier.size(20.dp)
              )
            }

            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = trackTitle,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("music_card_title")
              )
              Spacer(modifier = Modifier.height(2.dp))
              Text(
                text = trackArtist.uppercase(),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.9.sp,
                color = textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("music_card_artist")
              )
            }
          }

          // Tactical Media Transport Controls (Previous / Pause-Play / Next)
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
          ) {
            IconButton(
              onClick = onPreviousMediaTrack,
              modifier = Modifier
                .size(48.dp)
                .testTag("music_prev_button")
            ) {
              Icon(
                imageVector = Icons.Default.SkipPrevious,
                contentDescription = "Previous Track",
                tint = textSecondary,
                modifier = Modifier.size(22.dp)
              )
            }

            IconButton(
              onClick = onToggleMediaPlayback,
              modifier = Modifier
                .size(48.dp)
                .testTag("music_play_pause_button")
            ) {
              Box(
                modifier = Modifier
                  .size(34.dp)
                  .clip(CircleShape)
                  .background(borderColor.copy(alpha = 0.16f))
                  .border(1.2.dp, borderColor, CircleShape),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = if (isMusicActivelyPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                  contentDescription = if (isMusicActivelyPlaying) "Pause Music" else "Play Music",
                  tint = borderColor,
                  modifier = Modifier.size(18.dp)
                )
              }
            }

            IconButton(
              onClick = onNextMediaTrack,
              modifier = Modifier
                .size(48.dp)
                .testTag("music_next_button")
            ) {
              Icon(
                imageVector = Icons.Default.SkipNext,
                contentDescription = "Next Track",
                tint = textSecondary,
                modifier = Modifier.size(22.dp)
              )
            }
          }
        }
      }
    }
  }
}

@Composable
fun HomeCoreSection(
  uiState: WeykyUiState,
  coreSizeDp: Dp,
  onToggleVoice: () -> Unit,
  modifier: Modifier = Modifier
) {
  val batterySnap = uiState.batterySnapshot
  val isDeviceCharging = batterySnap?.chargingState == ChargingState.CHARGING ||
    batterySnap?.chargingState == ChargingState.FULL
  val currentBatteryPct = if (batterySnap != null && batterySnap.isAvailable) {
    batterySnap.percentage.coerceIn(0, 100)
  } else {
    100
  }

  BoxWithConstraints(
    modifier = modifier,
    contentAlignment = Alignment.Center
  ) {
    // True adaptive core sizing:
    // When normal space is available, uses full coreSizeDp.
    // When keyboard appears or available height decreases, the core gracefully adapts
    // down to fit the measured parent container without pushing controls off-screen!
    val maxAvailableDim = minOf(maxWidth, maxHeight)
    val adaptiveSize = if (maxAvailableDim > 0.dp && maxAvailableDim < coreSizeDp) {
      maxAvailableDim * 0.94f
    } else {
      coreSizeDp
    }

    WeykyCoreView(
      visualState = uiState.visualState,
      sizeDp = adaptiveSize.coerceAtLeast(48.dp),
      isPowerSaving = uiState.isPowerSaving,
      isVoiceListening = uiState.isVoiceListening,
      isSpeakingAloud = uiState.isSpeakingAloud,
      isMusicPlaying = uiState.isMusicPlaying,
      isCharging = isDeviceCharging,
      batteryPercentage = currentBatteryPct,
      voiceEnergyLevel = uiState.voiceEnergyLevel,
      onClick = onToggleVoice
    )
  }
}

@Composable
fun HomeResponseCard(
  uiState: WeykyUiState,
  borderColor: Color,
  panelBg: Color,
  panelBgElevated: Color,
  textPrimary: Color,
  onQuerySubmit: (String) -> Unit,
  onConfirmOption: (String) -> Unit,
  onRequestPermission: (String) -> Unit,
  modifier: Modifier = Modifier
) {
  AnimatedVisibility(
    visible = !uiState.lastResponseText.isNullOrBlank(),
    enter = fadeIn() + expandVertically(),
    exit = fadeOut() + shrinkVertically(),
    modifier = modifier
  ) {
    JarvisHudCard(
      accentColor = borderColor,
      containerColor = panelBgElevated,
      cutCornerSize = 14.dp,
      tabTitle = "RESPONSE",
      tabPosition = HudTabPosition.TOP_START,
      modifier = Modifier
        .fillMaxWidth()
        .testTag("response_bubble")
    ) {
      Column(modifier = Modifier.padding(14.dp)) {
        Text(
          text = uiState.lastResponseText ?: "",
          fontSize = 14.sp,
          color = textPrimary,
          lineHeight = 20.sp
        )

        // Proactive Conversational Suggestions & Next Steps
        if (uiState.proactiveSuggestions.isNotEmpty()) {
          Spacer(modifier = Modifier.height(10.dp))
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            uiState.proactiveSuggestions.forEach { suggestion ->
              JarvisHudBadge(
                accentColor = borderColor,
                containerColor = panelBg,
                cutSize = 7.dp,
                onClick = { onQuerySubmit(suggestion.queryToExecute) }
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                  Text(
                    text = suggestion.title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = textPrimary
                  )
                  Spacer(modifier = Modifier.width(4.dp))
                  Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = borderColor,
                    modifier = Modifier.size(12.dp)
                  )
                }
              }
            }
          }
        }

        // Multiple Options Selection (Contacts or Apps)
        if (uiState.pendingConfirmationOptions.isNotEmpty()) {
          Spacer(modifier = Modifier.height(10.dp))
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            uiState.pendingConfirmationOptions.forEach { option ->
              JarvisHudBadge(
                accentColor = borderColor,
                containerColor = panelBg,
                cutSize = 8.dp,
                onClick = { onConfirmOption(option) },
                modifier = Modifier.fillMaxWidth()
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  Text(text = option, fontSize = 13.sp, color = textPrimary)
                  Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = borderColor,
                    modifier = Modifier.size(16.dp)
                  )
                }
              }
            }
          }
        }

        // Permission Request Recovery Action
        if (uiState.pendingPermission != null) {
          Spacer(modifier = Modifier.height(8.dp))
          Button(
            onClick = { onRequestPermission(uiState.pendingPermission.first) },
            colors = ButtonDefaults.buttonColors(containerColor = borderColor),
            shape = CutCornerShape(8.dp),
            modifier = Modifier
              .align(Alignment.End)
              .testTag("grant_permission_button")
          ) {
            Text("منح الصلاحية", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = JarvisHudColors.PitchBlack)
          }
        } else if (uiState.pendingRecoveryActionTitle != null && uiState.pendingRecoveryAction != null) {
          Spacer(modifier = Modifier.height(8.dp))
          Button(
            onClick = { uiState.pendingRecoveryAction.invoke() },
            colors = ButtonDefaults.buttonColors(containerColor = borderColor),
            shape = CutCornerShape(8.dp),
            modifier = Modifier
              .align(Alignment.End)
              .testTag("recovery_action_button")
          ) {
            Text(uiState.pendingRecoveryActionTitle, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = JarvisHudColors.PitchBlack)
          }
        }
      }
    }
  }
}

@Composable
fun HomeCommandInputCard(
  uiState: WeykyUiState,
  borderColor: Color,
  panelBg: Color,
  textPrimary: Color,
  textSecondary: Color,
  isLight: Boolean,
  onQueryChange: (String) -> Unit,
  onQuerySubmit: (String) -> Unit,
  onToggleVoice: () -> Unit,
  keyboardController: SoftwareKeyboardController?,
  modifier: Modifier = Modifier
) {
  val activeInputAccent = if (uiState.isVoiceListening) JarvisHudColors.MagentaPulse else borderColor

  JarvisHudCard(
    accentColor = activeInputAccent,
    containerColor = panelBg,
    cutCornerSize = 16.dp,
    tabTitle = "WEYKY",
    tabPosition = HudTabPosition.TOP_START,
    modifier = modifier.fillMaxWidth()
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      // Left Microphone Button (48dp touch target)
      IconButton(
        onClick = onToggleVoice,
        modifier = Modifier
          .size(48.dp)
          .testTag("voice_input_button")
      ) {
        Icon(
          imageVector = Icons.Default.Mic,
          contentDescription = if (uiState.isVoiceListening) "Listening... tap to cancel" else "Voice Input",
          tint = if (uiState.isVoiceListening) JarvisHudColors.MagentaPulse else borderColor,
          modifier = Modifier.size(24.dp)
        )
      }

      // Center Seamless Input Field ("Ask Weyky...")
      Box(
        modifier = Modifier
          .weight(1f)
          .padding(horizontal = 8.dp),
        contentAlignment = Alignment.CenterStart
      ) {
        if (uiState.inputQuery.isEmpty()) {
          val hint = if (uiState.isVoiceListening) "Listening... Speak now" else "Ask Weyky..."
          Text(
            text = hint,
            fontSize = 16.sp,
            color = if (uiState.isVoiceListening) JarvisHudColors.MagentaPulse else textSecondary
          )
        }
        BasicTextField(
          value = uiState.inputQuery,
          onValueChange = onQueryChange,
          singleLine = true,
          textStyle = TextStyle(
            color = textPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Normal
          ),
          cursorBrush = SolidColor(borderColor),
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
          keyboardActions = KeyboardActions(onSend = {
            if (uiState.inputQuery.isNotBlank()) {
              onQuerySubmit(uiState.inputQuery)
              keyboardController?.hide()
            }
          }),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("ask_weyky_input")
        )
      }

      // Right Circular Arrow Action Button
      IconButton(
        onClick = {
          if (uiState.inputQuery.isNotBlank()) {
            onQuerySubmit(uiState.inputQuery)
            keyboardController?.hide()
          } else {
            onQuerySubmit("ماذا أفعل الآن؟")
          }
        },
        modifier = Modifier
          .size(48.dp)
          .testTag("submit_query_button")
      ) {
        Box(
          modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(if (isLight) borderColor.copy(alpha = 0.12f) else Color(0x33071828))
            .border(1.2.dp, borderColor.copy(alpha = 0.65f), CircleShape),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = "Send",
            tint = textSecondary,
            modifier = Modifier.size(18.dp)
          )
        }
      }
    }
  }
}

@Composable
fun HomeContextAlertCard(
  uiState: WeykyUiState,
  borderColor: Color,
  onQuerySubmit: (String) -> Unit,
  modifier: Modifier = Modifier
) {
  val battery = uiState.batterySnapshot
  val isLowBattery = battery != null && battery.isAvailable && battery.percentage <= 20 &&
    battery.chargingState != ChargingState.CHARGING && battery.chargingState != ChargingState.FULL
  val isBatteryFull = battery != null && battery.isAvailable && battery.percentage == 100 &&
    battery.chargingState == ChargingState.FULL

  val missedCallsSummary = uiState.missedCallsSummary
  val latestMissedCall = missedCallsSummary?.latestMissedCall
  val missedCount = missedCallsSummary?.missedCallsCount ?: 0

  val notifSummary = uiState.notificationSummary
  val latestUrgentNotif = notifSummary?.latestImportantEvent
  val urgentNotifCount = notifSummary?.totalImportantCount ?: 0

  val fallbackEvent = uiState.importantEvents.firstOrNull()

  val activeEventSignature: String? = when {
    isLowBattery -> "batt_low_${battery?.percentage}"
    isBatteryFull -> "batt_full_100"
    latestMissedCall != null && missedCount > 0 ->
      "missed_${latestMissedCall.id}_${latestMissedCall.dateMillis}_$missedCount"
    latestUrgentNotif != null && urgentNotifCount > 0 ->
      "notif_${latestUrgentNotif.eventId}_${latestUrgentNotif.timestampMillis}"
    fallbackEvent != null ->
      "event_${fallbackEvent.id}_${fallbackEvent.title}_${fallbackEvent.subtitle}"
    else -> null
  }

  var dismissedSignature by remember { mutableStateOf<String?>(null) }

  val isCardVisible = activeEventSignature != null && activeEventSignature != dismissedSignature

  AnimatedVisibility(
    visible = isCardVisible,
    enter = fadeIn() + expandVertically(),
    exit = fadeOut() + shrinkVertically(),
    modifier = modifier
  ) {
    val mainTitle: String
    val subLabel: String
    val rightTopText: String
    val rightBottomLabel: String
    val iconColor: Color
    val iconVector: androidx.compose.ui.graphics.vector.ImageVector
    val onCardClick: () -> Unit

    when {
      isLowBattery -> {
        mainTitle = "طاقة منخفضة (${battery?.percentage}%)"
        subLabel = "LOW BATTERY ALERT • يُرجى توصيل الشاحن"
        rightTopText = "${battery?.percentage}%"
        rightBottomLabel = "POWER"
        iconColor = JarvisHudColors.CrimsonAlert
        iconVector = Icons.Default.BatteryChargingFull
        onCardClick = {
          dismissedSignature = activeEventSignature
          onQuerySubmit("حالة البطارية")
        }
      }

      isBatteryFull -> {
        mainTitle = "اكتمل الشحن (100%)"
        subLabel = "BATTERY CHARGED • يمكنك فصل الشاحن الآن"
        rightTopText = "100%"
        rightBottomLabel = "FULL"
        iconColor = JarvisHudColors.NeonGreen
        iconVector = Icons.Default.BatteryChargingFull
        onCardClick = {
          dismissedSignature = activeEventSignature
          onQuerySubmit("حالة البطارية")
        }
      }

      latestMissedCall != null && missedCount > 0 -> {
        val callerOrNumber = latestMissedCall.number.ifBlank {
          latestMissedCall.callerName ?: "مكالمة فائتة"
        }
        val callerSub = if (!latestMissedCall.callerName.isNullOrBlank() && latestMissedCall.callerName != latestMissedCall.number) {
          "MISSED CALL • ${latestMissedCall.callerName.uppercase()}"
        } else if (missedCount > 1) {
          "MISSED CALL ($missedCount)"
        } else {
          "MISSED CALL"
        }
        mainTitle = callerOrNumber
        subLabel = callerSub
        rightTopText = latestMissedCall.formattedTime
          .replace(" AM", "")
          .replace(" PM", "")
          .replace(" ص", "")
          .replace(" م", "")
          .ifBlank { uiState.currentDateString.ifBlank { "TODAY" } }
        rightBottomLabel = "TIME"
        iconColor = JarvisHudColors.NeonGreen
        iconVector = Icons.Default.PhoneCallback
        onCardClick = {
          dismissedSignature = activeEventSignature
          onQuerySubmit("المكالمات الفائتة")
        }
      }

      latestUrgentNotif != null && urgentNotifCount > 0 -> {
        val appTag = latestUrgentNotif.appLabel?.uppercase() ?: "ALERT"
        val countSuffix = if (urgentNotifCount > 1) " (+${urgentNotifCount - 1})" else ""
        mainTitle = latestUrgentNotif.title.ifBlank { appTag } + countSuffix
        subLabel = "$appTag • ${latestUrgentNotif.text.ifBlank { "NEW MESSAGE" }}".uppercase()

        val timeFormatted = remember(latestUrgentNotif.timestampMillis) {
          java.text.SimpleDateFormat("HH:mm", java.util.Locale.US)
            .format(java.util.Date(latestUrgentNotif.timestampMillis))
        }
        rightTopText = timeFormatted
        rightBottomLabel = "NOW"
        iconColor = when (latestUrgentNotif.importanceCategory) {
          com.example.device.notifications.NotificationImportanceCategory.SECURITY_SYSTEM_ALERT -> JarvisHudColors.CrimsonAlert
          com.example.device.notifications.NotificationImportanceCategory.REMINDER_EVENT -> JarvisHudColors.TacticalAmber
          else -> JarvisHudColors.NeonGreen
        }
        iconVector = when (latestUrgentNotif.importanceCategory) {
          com.example.device.notifications.NotificationImportanceCategory.MISSED_CALL -> Icons.AutoMirrored.Filled.PhoneMissed
          com.example.device.notifications.NotificationImportanceCategory.SECURITY_SYSTEM_ALERT -> Icons.Default.Warning
          else -> Icons.Default.Notifications
        }
        onCardClick = {
          dismissedSignature = activeEventSignature
          onQuerySubmit("اقرأ الإشعارات")
        }
      }

      else -> {
        val ev = fallbackEvent
        mainTitle = ev?.title ?: ""
        subLabel = ev?.subtitle?.uppercase() ?: ""
        rightTopText = ev?.actionTitle ?: uiState.currentTimeString
          .replace(" AM", "")
          .replace(" PM", "")
          .replace(" ص", "")
          .replace(" م", "")
        rightBottomLabel = "TIME"
        iconColor = JarvisHudColors.NeonGreen
        iconVector = if (ev?.icon == "phone_missed") Icons.Default.PhoneCallback else Icons.Default.Notifications
        onCardClick = {
          dismissedSignature = activeEventSignature
          ev?.onAction?.invoke() ?: onQuerySubmit("المكالمات الفائتة والإشعارات")
        }
      }
    }

    val testTagId = fallbackEvent?.let { "event_banner_${it.id}" } ?: "events_bottom_card"
    val isLight = uiState.isLightMode
    val panelBg = JarvisHudColors.panelBg(isLight)
    val textPrimary = JarvisHudColors.textPrimary(isLight)
    val textSecondary = JarvisHudColors.textSecondary(isLight)

    JarvisHudCard(
      accentColor = borderColor,
      containerColor = panelBg,
      cutCornerSize = 16.dp,
      tabTitle = "ALERTS",
      tabPosition = HudTabPosition.BOTTOM_END,
      onClick = onCardClick,
      modifier = modifier
        .fillMaxWidth()
        .testTag(testTagId)
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          modifier = Modifier.weight(1f)
        ) {
          Box(
            modifier = Modifier
              .size(42.dp)
              .shadow(elevation = 8.dp, shape = CircleShape, ambientColor = iconColor, spotColor = iconColor)
              .clip(CircleShape)
              .background(iconColor.copy(alpha = 0.14f))
              .border(1.5.dp, iconColor.copy(alpha = 0.85f), CircleShape),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = iconVector,
              contentDescription = mainTitle,
              tint = iconColor,
              modifier = Modifier.size(20.dp)
            )
          }

          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = mainTitle,
              fontSize = 15.sp,
              fontFamily = FontFamily.Monospace,
              fontWeight = FontWeight.Bold,
              color = textPrimary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
              text = subLabel,
              fontSize = 10.sp,
              fontFamily = FontFamily.Monospace,
              letterSpacing = 0.8.sp,
              color = textSecondary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
        }

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.padding(start = 4.dp)
          ) {
            Text(
              text = rightTopText,
              fontSize = 12.sp,
              fontFamily = FontFamily.Monospace,
              fontWeight = FontWeight.SemiBold,
              color = textPrimary,
              textAlign = TextAlign.End
            )
            Text(
              text = rightBottomLabel,
              fontSize = 9.sp,
              fontFamily = FontFamily.Monospace,
              letterSpacing = 0.8.sp,
              color = textSecondary,
              textAlign = TextAlign.End
            )
          }

          IconButton(
            onClick = { dismissedSignature = activeEventSignature },
            modifier = Modifier
              .size(32.dp)
              .testTag("dismiss_alert_button")
          ) {
            Icon(
              imageVector = Icons.Default.Close,
              contentDescription = "Dismiss Alert",
              tint = textSecondary,
              modifier = Modifier.size(16.dp)
            )
          }
        }
      }
    }
  }
}

/**
 * Backward compatibility alias for EventsBottomHudCard.
 */
@Composable
fun EventsBottomHudCard(
  uiState: WeykyUiState,
  borderColor: Color,
  onQuerySubmit: (String) -> Unit,
  modifier: Modifier = Modifier
) {
  HomeContextAlertCard(
    uiState = uiState,
    borderColor = borderColor,
    onQuerySubmit = onQuerySubmit,
    modifier = modifier
  )
}
