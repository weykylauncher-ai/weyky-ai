package com.example.core.intent

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
import com.example.core.intelligence.conversation.ActionableOption
import com.example.core.intelligence.conversation.LocalConversationalEngine
import com.example.core.intelligence.conversation.PendingExpectation
import com.example.device.info.DeviceInfoProvider
import com.example.rendering.visual_state.CoreVisualState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class IntentExecutor(
  private val batteryActionHandler: BatteryActionHandler,
  private val storageMemoryActionHandler: StorageMemoryActionHandler,
  private val flashlightActionHandler: FlashlightActionHandler,
  private val audioActionHandler: AudioActionHandler,
  private val mediaActionHandler: MediaActionHandler,
  private val appActionHandler: AppActionHandler,
  private val callActionHandler: CallActionHandler,
  private val weatherActionHandler: WeatherActionHandler,
  private val networkActionHandler: NetworkActionHandler,
  private val notificationActionHandler: NotificationActionHandler,
  private val settingsActionHandler: SettingsActionHandler,
  private val contextActionHandler: ContextActionHandler,
  private val localConversationalEngine: LocalConversationalEngine? = null,
  private val deviceInfoProvider: DeviceInfoProvider? = null
) {

  suspend fun execute(intent: WeykyIntent): ExecutionResult {
    return when (intent) {
      is WeykyIntent.BatteryIntent -> {
        localConversationalEngine?.session?.let {
          it.currentTopic = "battery"
          it.lastReferencedSubject = "battery"
        }
        batteryActionHandler.handle()
      }
      is WeykyIntent.RamIntent -> storageMemoryActionHandler.handleRam()
      is WeykyIntent.StorageIntent -> storageMemoryActionHandler.handleStorage()
      is WeykyIntent.DeviceInfoIntent -> executeDeviceInfo()
      is WeykyIntent.PhoneModelIntent -> executePhoneModel()
      is WeykyIntent.DeviceInfoDetailedIntent -> executeDeviceInfoDetailed()
      is WeykyIntent.UserIdentityIntent -> executeUserIdentity()
      is WeykyIntent.FlashlightIntent -> flashlightActionHandler.handle(intent.turnOn)

      // Volume intents (step, percent, mute)
      is WeykyIntent.VolumeStepIntent -> audioActionHandler.handleStep(intent.increase, intent.stepCount, intent.forRingtone)
      is WeykyIntent.VolumePercentIntent -> audioActionHandler.handlePercent(intent.percent, intent.forRingtone)
      is WeykyIntent.VolumeMuteIntent -> audioActionHandler.handleMute(intent.mute, intent.forRingtone)
      is WeykyIntent.VolumeIntent -> audioActionHandler.handle(intent.adjustment)

      // Application launching
      is WeykyIntent.OpenAppIntent -> appActionHandler.handle(intent.appName)

      // Media controls
      is WeykyIntent.MediaIntent -> {
        localConversationalEngine?.session?.let {
          it.currentTopic = "music"
          it.lastReferencedSubject = "music"
        }
        mediaActionHandler.handle(intent.action)
      }
      is WeykyIntent.PlaySpecificSongIntent -> {
        localConversationalEngine?.session?.let {
          it.currentTopic = "music"
          it.lastReferencedSubject = "song"
          it.pendingExpectation = PendingExpectation.NONE
        }
        mediaActionHandler.handlePlaySpecificSong(intent.songTitle)
      }
      is WeykyIntent.RequestSongTitleIntent -> {
        localConversationalEngine?.session?.let {
          it.currentTopic = "music"
          it.lastReferencedSubject = "song"
          it.pendingExpectation = PendingExpectation.AWAITING_SONG_TITLE
        }
        mediaActionHandler.handleRequestSongTitle()
      }

      is WeykyIntent.HypotheticalBatteryMusicIntent -> {
        localConversationalEngine?.session?.let {
          it.currentTopic = "battery"
          it.lastReferencedSubject = "battery"
        }
        contextActionHandler.handleHypotheticalBatteryMusic()
      }
      is WeykyIntent.HypotheticalScheduleDelayIntent -> {
        localConversationalEngine?.session?.let {
          it.currentTopic = "schedule"
          it.lastReferencedSubject = "schedule"
        }
        contextActionHandler.handleHypotheticalScheduleDelay(intent.delayMinutes)
      }

      is WeykyIntent.WifiIntent -> networkActionHandler.handleWifi(intent.openSettings)
      is WeykyIntent.BluetoothIntent -> networkActionHandler.handleBluetooth()
      is WeykyIntent.CallIntent -> callActionHandler.handle(intent)
      is WeykyIntent.NotificationIntent -> notificationActionHandler.handle(intent.openSettings)
      is WeykyIntent.WeatherIntent -> {
        localConversationalEngine?.session?.let {
          it.currentTopic = "weather"
          it.lastReferencedSubject = "weather"
        }
        weatherActionHandler.handle(intent.queryType)
      }
      is WeykyIntent.TimeIntent -> executeTime()
      is WeykyIntent.SettingsIntent -> settingsActionHandler.handle(intent.type)
      is WeykyIntent.ContextIntent -> contextActionHandler.handle()
      is WeykyIntent.ContextualQueryIntent -> contextActionHandler.handleContextualQuery(intent.rawQuery)
      is WeykyIntent.ConversationalIntent -> executeConversational(intent.rawQuery)
      is WeykyIntent.HelpIntent -> executeHelp()
      is WeykyIntent.WakeIntent -> executeWake()
      is WeykyIntent.UnknownIntent -> executeConversational(intent.rawQuery)
    }
  }

  private fun executeWake(): ExecutionResult {
    val isArabic = Locale.getDefault().language.startsWith("ar")
    val msg = if (isArabic) "أهلاً، أنا أستمع إليك." else "I'm listening."
    return ExecutionResult.Success(
      speech = msg,
      actionTaken = "Woken up",
      visualState = CoreVisualState.LISTENING
    )
  }

  private fun executeDeviceInfo(): ExecutionResult {
    val isArabic = Locale.getDefault().language.startsWith("ar")
    if (deviceInfoProvider != null) {
      val text = deviceInfoProvider.getDetailedTechnicalSpecs(isArabic)
      return ExecutionResult.Success(
        speech = text,
        actionTaken = "Queried Device Overview",
        visualState = CoreVisualState.RESPONDING
      )
    }
    val bResult = batteryActionHandler.handle()
    val rResult = storageMemoryActionHandler.handleRam()
    val sResult = storageMemoryActionHandler.handleStorage()

    val bText = if (bResult is ExecutionResult.Success) bResult.speech else "بيانات البطارية غير متاحة."
    val rText = if (rResult is ExecutionResult.Success) rResult.speech else ""
    val sText = if (sResult is ExecutionResult.Success) sResult.speech else ""

    val combined = listOf(bText, rText, sText).filter { it.isNotBlank() }.joinToString(" ")
    return ExecutionResult.Success(
      speech = combined,
      actionTaken = "Queried Device Overview",
      visualState = CoreVisualState.RESPONDING
    )
  }

  private fun executePhoneModel(): ExecutionResult {
    val isArabic = Locale.getDefault().language.startsWith("ar")
    localConversationalEngine?.session?.let {
      it.currentTopic = "device_info"
      it.lastReferencedSubject = "phone"
    }
    val text = deviceInfoProvider?.getPhoneModelSummary(isArabic)
      ?: if (isArabic) "هاتفك يعمل بنظام Android." else "Your phone runs Android."
    return ExecutionResult.Success(
      speech = text,
      actionTaken = "Queried Phone Model",
      visualState = CoreVisualState.RESPONDING,
      proactiveSuggestions = listOf(
        ActionableOption(if (isArabic) "وماذا تعرف عنه؟" else "What else?", "وماذا تعرف عنه؟"),
        ActionableOption(if (isArabic) "البطارية" else "Battery", "كم نسبة البطارية؟")
      )
    )
  }

  private fun executeDeviceInfoDetailed(): ExecutionResult {
    val isArabic = Locale.getDefault().language.startsWith("ar")
    localConversationalEngine?.session?.let {
      it.currentTopic = "device_info"
      it.lastReferencedSubject = "phone"
    }
    val text = deviceInfoProvider?.getDetailedTechnicalSpecs(isArabic)
      ?: if (isArabic) "مواصفات الهاتف غير متوفرة حالياً." else "Phone specs unavailable."
    return ExecutionResult.Success(
      speech = text,
      actionTaken = "Queried Detailed Device Specs",
      visualState = CoreVisualState.RESPONDING,
      proactiveSuggestions = listOf(
        ActionableOption(if (isArabic) "البطارية" else "Battery", "البطارية"),
        ActionableOption(if (isArabic) "الذاكرة" else "RAM usage", "الذاكرة")
      )
    )
  }

  private fun executeUserIdentity(): ExecutionResult {
    val isArabic = Locale.getDefault().language.startsWith("ar")
    val text = deviceInfoProvider?.getUserIdentityDescription(isArabic)
      ?: if (isArabic) {
        "أنت مستخدم هذا الجهاز. لم يتم تسجيل اسم شخصي في WEYKY حفاظاً على خصوصيتك."
      } else {
        "You are the user of this device. No personal identity is registered in WEYKY yet, respecting your privacy."
      }
    return ExecutionResult.Success(
      speech = text,
      actionTaken = "Queried User Identity",
      visualState = CoreVisualState.RESPONDING,
      proactiveSuggestions = listOf(
        ActionableOption(if (isArabic) "ما هو هاتفي؟" else "Phone model?", "ما هو هاتفي؟"),
        ActionableOption(if (isArabic) "ماذا تعرف عن جهازي؟" else "About my phone?", "ماذا تعرف عن هاتفي؟")
      )
    )
  }

  private fun executeTime(): ExecutionResult {
    val isArabic = Locale.getDefault().language.startsWith("ar")
    val sdf = SimpleDateFormat("EEEE, d MMMM - HH:mm", Locale.getDefault())
    val text = if (isArabic) "الوقت الآن ${sdf.format(Date())}." else "It is ${sdf.format(Date())}."
    return ExecutionResult.Success(
      speech = text,
      actionTaken = "Queried Time",
      visualState = CoreVisualState.RESPONDING
    )
  }

  private fun executeHelp(): ExecutionResult {
    val isArabic = Locale.getDefault().language.startsWith("ar")
    val text = if (isArabic) {
      "أهلاً بك! أنا WEYKY، نواتك الذكية. يمكنك التحدث معي بحرية في أي موضوع، طرح أسئلة عامة وعلمية، التحكم بهاتفك (فتح تطبيقات، كشاف، صوت، موسيقى)، أو متابعة سياقك اليومي (طقس، بطارية، مواعيد، نصيحة الخروج)."
    } else {
      "I am WEYKY, your smart assistant. You can converse naturally with me, ask questions, control device functions (apps, media, volume, torch), or track your daily context (weather, battery, schedule, departure advice)."
    }
    return ExecutionResult.Success(
      speech = text,
      actionTaken = "Queried Help",
      visualState = CoreVisualState.RESPONDING,
      proactiveSuggestions = listOf(
        ActionableOption(if (isArabic) "ماذا أفعل الآن؟" else "Current situation", "ماذا افعل الان؟"),
        ActionableOption(if (isArabic) "الطقس" else "Weather", "ما حالة الطقس؟"),
        ActionableOption(if (isArabic) "قصة قصيرة" else "Story", "احك لي قصة قصيرة")
      )
    )
  }

  private suspend fun executeConversational(raw: String): ExecutionResult {
    val engine = localConversationalEngine
    if (engine != null) {
      val response = engine.respond(raw)
      return ExecutionResult.Success(
        speech = response.replyText,
        actionTaken = "Conversational Intelligence [${response.topic ?: "Dialogue"}]",
        visualState = CoreVisualState.RESPONDING,
        proactiveSuggestions = response.actionableSuggestions
      )
    }
    return executeUnknown(raw)
  }

  private fun executeUnknown(raw: String): ExecutionResult {
    val isArabic = Locale.getDefault().language.startsWith("ar")
    val text = if (isArabic) {
      "لم أتعرف على هذا الطلب. جرب السؤال عن الطقس، البطارية، فتح تطبيق، أو التحدث معي في أي موضوع."
    } else {
      "I didn't recognize that request. Try asking about weather, battery, opening an app, or chatting about any topic."
    }
    return ExecutionResult.Fallback(
      explanation = text,
      visualState = CoreVisualState.RESPONDING
    )
  }
}
