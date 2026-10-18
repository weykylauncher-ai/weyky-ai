package com.example.core.intent

import com.example.device.audio.VolumeAdjustment

enum class WeatherQueryType {
  CURRENT,
  HOURLY_EVENING,
  RAIN_SOON,
  WIND_TOMORROW,
  OUTDOOR_SUITABILITY,
  COMPARE_TOMORROW,
  WEEK_RAIN
}

enum class CallQueryType {
  DIAL_TARGET,
  MISSED,
  INCOMING,
  OUTGOING,
  ALL
}

sealed interface WeykyIntent {
  object BatteryIntent : WeykyIntent
  object RamIntent : WeykyIntent
  object StorageIntent : WeykyIntent
  object DeviceInfoIntent : WeykyIntent
  data class FlashlightIntent(val turnOn: Boolean) : WeykyIntent

  // Volume intents
  open class VolumeIntent(val adjustment: VolumeAdjustment) : WeykyIntent {
    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (other !is VolumeIntent) return false
      return adjustment == other.adjustment
    }
    override fun hashCode(): Int = adjustment.hashCode()
  }
  data class VolumeStepIntent(val increase: Boolean, val stepCount: Int = 1, val forRingtone: Boolean = false) :
    VolumeIntent(if (increase) VolumeAdjustment.RAISE else VolumeAdjustment.LOWER)
  data class VolumePercentIntent(val percent: Int, val forRingtone: Boolean = false) :
    VolumeIntent(VolumeAdjustment.SET_PERCENT)
  data class VolumeMuteIntent(val mute: Boolean, val forRingtone: Boolean = false) :
    VolumeIntent(if (mute) VolumeAdjustment.MUTE else VolumeAdjustment.UNMUTE)

  data class WifiIntent(val openSettings: Boolean = true) : WeykyIntent
  object BluetoothIntent : WeykyIntent

  // Media intents
  enum class MediaAction { PLAY, PAUSE, TOGGLE, NEXT, PREVIOUS }
  data class MediaIntent(val action: MediaAction) : WeykyIntent

  // App opening intent
  data class OpenAppIntent(val appName: String) : WeykyIntent

  data class CallIntent(
    val target: String? = null,
    val queryType: CallQueryType = CallQueryType.DIAL_TARGET,
    val showMissed: Boolean = false
  ) : WeykyIntent
  data class NotificationIntent(val openSettings: Boolean = false) : WeykyIntent
  data class WeatherIntent(
    val queryType: WeatherQueryType = WeatherQueryType.CURRENT,
    val rawQuery: String = ""
  ) : WeykyIntent
  object TimeIntent : WeykyIntent

  enum class SettingsType { BATTERY, DISPLAY, SOUND, NOTIFICATIONS, APPS, GENERAL }
  data class SettingsIntent(val type: SettingsType) : WeykyIntent

  object ContextIntent : WeykyIntent

  data class ContextualQueryIntent(
    val rawQuery: String,
    val domain: com.example.core.intelligence.IntentDomain
  ) : WeykyIntent

  data class ConversationalIntent(
    val rawQuery: String
  ) : WeykyIntent

  object HelpIntent : WeykyIntent
  object WakeIntent : WeykyIntent

  // Specific song intents
  data class PlaySpecificSongIntent(val songTitle: String) : WeykyIntent
  object RequestSongTitleIntent : WeykyIntent

  // Identity and Device Hardware/OS Specs
  object UserIdentityIntent : WeykyIntent
  object PhoneModelIntent : WeykyIntent
  object DeviceInfoDetailedIntent : WeykyIntent

  // Hypothetical Scenarios (Separating questions from actions)
  object HypotheticalBatteryMusicIntent : WeykyIntent
  data class HypotheticalScheduleDelayIntent(val delayMinutes: Int = 10) : WeykyIntent

  data class UnknownIntent(val rawQuery: String) : WeykyIntent
}

val WeykyIntent.stepCount: Int get() = (this as? WeykyIntent.VolumeStepIntent)?.stepCount ?: 1
val WeykyIntent.increase: Boolean get() = (this as? WeykyIntent.VolumeStepIntent)?.increase ?: false
val WeykyIntent.percent: Int get() = (this as? WeykyIntent.VolumePercentIntent)?.percent ?: 0
val WeykyIntent.forRingtone: Boolean get() = (this as? WeykyIntent.VolumeStepIntent)?.forRingtone ?: (this as? WeykyIntent.VolumePercentIntent)?.forRingtone ?: false
val WeykyIntent.mute: Boolean get() = (this as? WeykyIntent.VolumeMuteIntent)?.mute ?: false
val WeykyIntent.appName: String get() = (this as? WeykyIntent.OpenAppIntent)?.appName ?: ""
val WeykyIntent.target: String? get() = (this as? WeykyIntent.CallIntent)?.target
val WeykyIntent.action: WeykyIntent.MediaAction get() = (this as? WeykyIntent.MediaIntent)?.action ?: WeykyIntent.MediaAction.PLAY

sealed interface ExecutionResult {
  data class Success(
    val speech: String,
    val actionTaken: String? = null,
    val visualState: com.example.rendering.visual_state.CoreVisualState = com.example.rendering.visual_state.CoreVisualState.SUCCESS,
    val primaryDecision: String? = null,
    val nextStepLabel: String? = null,
    val proactiveSuggestions: List<com.example.core.intelligence.conversation.ActionableOption> = emptyList()
  ) : ExecutionResult

  data class Fallback(
    val explanation: String,
    val recoveryActionTitle: String? = null,
    val onRecoveryAction: (() -> Unit)? = null,
    val visualState: com.example.rendering.visual_state.CoreVisualState = com.example.rendering.visual_state.CoreVisualState.RESPONDING
  ) : ExecutionResult

  data class RequiresPermission(
    val permission: String,
    val rationale: String,
    val visualState: com.example.rendering.visual_state.CoreVisualState = com.example.rendering.visual_state.CoreVisualState.RESPONDING
  ) : ExecutionResult

  data class Error(
    val friendlyMessage: String,
    val visualState: com.example.rendering.visual_state.CoreVisualState = com.example.rendering.visual_state.CoreVisualState.ERROR
  ) : ExecutionResult

  data class Unavailable(
    val reason: String,
    val visualState: com.example.rendering.visual_state.CoreVisualState = com.example.rendering.visual_state.CoreVisualState.RESPONDING
  ) : ExecutionResult

  data class NeedsConfirmation(
    val prompt: String,
    val options: List<String>,
    val onConfirmOption: (String) -> Unit,
    val visualState: com.example.rendering.visual_state.CoreVisualState = com.example.rendering.visual_state.CoreVisualState.RESPONDING
  ) : ExecutionResult

  data class NotSupported(
    val reason: String,
    val visualState: com.example.rendering.visual_state.CoreVisualState = com.example.rendering.visual_state.CoreVisualState.ERROR
  ) : ExecutionResult
}
