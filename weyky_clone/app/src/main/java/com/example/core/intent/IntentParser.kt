package com.example.core.intent

import com.example.device.audio.VolumeAdjustment
import java.util.Locale

object IntentParser {

  fun normalize(raw: String): String {
    return raw.trim()
      .lowercase(Locale.ROOT)
      .replace(Regex("[\\u064B-\\u065F\\u0670]"), "") // Strip all tashkeel & shaddah
      .replace('أ', 'ا')
      .replace('إ', 'ا')
      .replace('آ', 'ا')
      .replace('ة', 'ه')
      .replace('ى', 'ي')
      .replace('ؤ', 'و')
      .replace('ئ', 'ي')
      .replace(Regex("[\\?\\!\\.\\,\\؟\\:]"), " ")
      .replace(Regex("\\s+"), " ")
      .trim()
  }

  fun extractContactTarget(rawInput: String): String {
    var query = rawInput.trim()
    val wakePrefixes = listOf("hey weyky ", "يا ويكي ", "يا weyky ", "ويكي ", "weyky ", "هاي ويكي ", "اهلا ويكي ")
    for (wp in wakePrefixes) {
      if (query.startsWith(wp, ignoreCase = true)) {
        query = query.substring(wp.length).trim()
        break
      }
    }
    val callPrefixes = listOf(
      "اتصل بـ ", "اتصل ب ", "اتصل على ", "اتصل ب", "اتصل ",
      "إتصل بـ ", "إتصل ب ", "إتصل على ", "إتصل ب", "إتصل ",
      "كلم ", "خاطب ", "رن على ", "هاتف ", "مكالمة ",
      "call ", "dial ", "phone "
    )
    for (prefix in callPrefixes) {
      if (query.startsWith(prefix, ignoreCase = true)) {
        query = query.substring(prefix.length).trim()
        break
      }
    }
    if (query.startsWith("بـ") && query.length > 2) {
      query = query.substring(2).trim()
    } else if (query.startsWith("ب") && query.length > 2 && !query.startsWith("باسم") && !query.startsWith("بدر")) {
      val withoutB = query.substring(1).trim()
      if (withoutB.isNotEmpty()) {
        query = withoutB
      }
    }
    return query.trim()
  }

  /**
   * Checks if input is directly a recognized prominent app name.
   */
  private fun isDirectAppNameQuery(input: String): Boolean {
    val directNames = setOf(
      "واتساب", "الواتساب", "واتس اب", "واتس", "whatsapp", "whats app",
      "يوتيوب", "اليوتيوب", "يوتوب", "youtube", "you tube",
      "تيليجرام", "تليجرام", "تلكرام", "التيليجرام", "telegram",
      "فيسبوك", "الفيسبوك", "فيس بوك", "facebook",
      "انستجرام", "انستغرام", "انستا", "instagram",
      "تيك توك", "تيكتوك", "tiktok",
      "سناب شات", "سناب", "snapchat",
      "كروم", "متصفح", "chrome",
      "جيميل", "gmail",
      "سبوتيفاي", "spotify",
      "نتفلكس", "نتفليكس", "netflix",
      "الكاميرا", "كاميرا", "camera",
      "المعرض", "معرض", "الاستوديو", "الصور", "gallery", "photos",
      "الخرائط", "maps",
      "الحاسبه", "الحاسبة", "حاسبه", "حاسبة", "calculator",
      "الساعه", "الساعة", "المنبه", "منبه", "clock",
      "شات جي بي تي", "تشات جي بي تي", "جي بي تي", "chatgpt", "chat gpt"
    )
    return directNames.contains(input)
  }

  /**
   * Extracts application name from an open command.
   * e.g. "افتح واتساب" -> "واتساب", "شغل يوتيوب" -> "يوتيوب", "Open WhatsApp" -> "WhatsApp"
   */
  fun extractAppName(rawInput: String): String {
    var query = rawInput.trim()
    val wakePrefixes = listOf("hey weyky ", "يا ويكي ", "يا weyky ", "ويكي ", "weyky ", "هاي ويكي ", "اهلا ويكي ")
    for (wp in wakePrefixes) {
      if (query.startsWith(wp, ignoreCase = true)) {
        query = query.substring(wp.length).trim()
        break
      }
    }

    val openPrefixes = listOf(
      "افتح تطبيق ", "افتح برنامج ", "افتح لي ", "افتح ",
      "إفتح تطبيق ", "إفتح ",
      "شغل تطبيق ", "شغل برنامج ", "شغل لي ", "شغل ",
      "شغّل تطبيق ", "شغّل ",
      "ادخل على ", "انتقل الى ",
      "open app ", "open application ", "open ",
      "launch app ", "launch application ", "launch ",
      "start app ", "start application ", "start "
    )
    for (prefix in openPrefixes) {
      if (query.startsWith(prefix, ignoreCase = true)) {
        query = query.substring(prefix.length).trim()
        break
      }
    }
    return query.trim()
  }

  /**
   * Extracts volume percentage if specified in the query.
   */
  private fun extractVolumePercent(input: String): Int? {
    val converted = input.map { char ->
      when (char) {
        '٠' -> '0'; '١' -> '1'; '٢' -> '2'; '٣' -> '3'; '٤' -> '4'
        '٥' -> '5'; '٦' -> '6'; '٧' -> '7'; '٨' -> '8'; '٩' -> '9'
        else -> char
      }
    }.joinToString("")

    if (converted.contains("%") || converted.contains("٪") || converted.contains("بالمئه") ||
        converted.contains("بالمئة") || converted.contains("في الميه") || converted.contains("percent")) {
      val regex = Regex("""(\d{1,3})""")
      val match = regex.find(converted)
      if (match != null) {
        val num = match.groupValues[1].toIntOrNull()
        if (num != null && num in 0..100) {
          return num
        }
      }
    }

    val hasSetVerb = converted.contains("اجعل") || converted.contains("اضبط") || converted.contains("خلي") || converted.contains("set")
    if (hasSetVerb) {
      val regex = Regex("""(\d{1,3})""")
      val match = regex.find(converted)
      if (match != null) {
        val num = match.groupValues[1].toIntOrNull()
        if (num != null && num in 0..100) {
          return num
        }
      }
    }
    return null
  }

  /**
   * Extracts relative step counts from query.
   */
  private fun extractStepCount(input: String): Int {
    return when {
      input.contains("مرتين") || input.contains("خطوتين") || input.contains("درجتين") || input.contains("twice") || input.contains("2 times") || input.contains("2 steps") -> 2
      input.contains("3 مرات") || input.contains("ثلاث مرات") || input.contains("3 درجات") || input.contains("3 times") || input.contains("3 steps") -> 3
      input.contains("4 مرات") || input.contains("اربع مرات") || input.contains("4 times") -> 4
      else -> 1
    }
  }

  fun parse(rawInput: String, session: com.example.core.intelligence.conversation.ConversationSession? = null): WeykyIntent {
    var input = normalize(rawInput)
    if (input.isBlank()) return WeykyIntent.UnknownIntent("")

    // 0. Wake phrase detection
    if (input == "hey weyky" || input == "يا ويكي" || input == "ويكي" || input == "مرحبا ويكي" || input == "weyky" || input == "هاي ويكي" || input == "يا weyky" || input == "اهلا ويكي") {
      return WeykyIntent.WakeIntent
    }

    // Strip leading wake phrase
    val wakePrefixes = listOf("hey weyky ", "يا ويكي ", "يا weyky ", "ويكي ", "weyky ", "هاي ويكي ", "مرحبا ويكي ", "اهلا ويكي ")
    for (wp in wakePrefixes) {
      if (input.startsWith(wp)) {
        input = input.substring(wp.length).trim()
        break
      }
    }

    // 1. Semantic classification & disambiguation: Questions vs Commands vs Hypotheticals
    val classified = MessageIntentClassifier.classify(rawInput, session)
    when (classified.primarySubject) {
      "battery_music_impact" -> return WeykyIntent.HypotheticalBatteryMusicIntent
      "schedule_delay_impact" -> return WeykyIntent.HypotheticalScheduleDelayIntent(classified.extractedDelayMinutes ?: 10)
      "song_title_provided" -> {
        val title = classified.extractedSongTitle ?: rawInput
        return WeykyIntent.PlaySpecificSongIntent(title)
      }
      "request_song_title" -> return WeykyIntent.RequestSongTitleIntent
      "play_specific_song" -> {
        val title = classified.extractedSongTitle ?: rawInput
        return WeykyIntent.PlaySpecificSongIntent(title)
      }
      "user_identity" -> return WeykyIntent.UserIdentityIntent
      "phone_model" -> return WeykyIntent.PhoneModelIntent
      "device_detailed_specs" -> return WeykyIntent.DeviceInfoDetailedIntent
    }

    // Contextual Weather follow-ups when previous topic was weather or direct weather questions
    val isWeatherContext = session?.currentTopic == "weather" || session?.lastReferencedSubject == "weather"

    // "ماذا عن المساء؟" / "طقس المساء"
    if (input.contains("المساء") || input.contains("الليله") || input.contains("مساء") || input.contains("في الليل") || input.contains("evening") || input.contains("tonight")) {
      if (input.contains("طقس") || input.contains("جو") || input.contains("حراره") || input.contains("مطر") || isWeatherContext) {
        return WeykyIntent.WeatherIntent(WeatherQueryType.HOURLY_EVENING, rawInput)
      }
    }

    // "هل ستمطر خلال الساعتين القادمتين؟" / "هل في مطر قريبا"
    if ((input.contains("تمطر") || input.contains("ستمطر") || input.contains("مطر") || input.contains("rain")) &&
      (input.contains("ساعتين") || input.contains("قادمه") || input.contains("قريبا") || input.contains("الان") || input.contains("اليوم") || input.contains("soon") || input.contains("hours"))
    ) {
      return WeykyIntent.WeatherIntent(WeatherQueryType.RAIN_SOON, rawInput)
    }

    // "كيف ستكون الرياح غدا؟" / "سرعه الرياح غدا"
    if ((input.contains("رياح") || input.contains("الرياح") || input.contains("هواء") || input.contains("wind")) &&
      (input.contains("غدا") || input.contains("بكره") || input.contains("اليوم") || input.contains("كم") || input.contains("tomorrow") || isWeatherContext)
    ) {
      return WeykyIntent.WeatherIntent(WeatherQueryType.WIND_TOMORROW, rawInput)
    }

    // "قارن بين طقس اليوم وغدا"
    if (input.contains("قارن") || input.contains("مقارنه") || (input.contains("اليوم") && (input.contains("غدا") || input.contains("وغدا") || input.contains("بكره")))) {
      if (input.contains("طقس") || input.contains("جو") || isWeatherContext) {
        return WeykyIntent.WeatherIntent(WeatherQueryType.COMPARE_TOMORROW, rawInput)
      }
    }

    // "متى اقرب احتمال للمطر هذا الاسبوع؟"
    if ((input.contains("مطر") || input.contains("للمطر") || input.contains("الامطار") || input.contains("rain")) &&
      (input.contains("اسبوع") || input.contains("الاسبوع") || input.contains("متي") || input.contains("week") || input.contains("اقرب") || input.contains("قادم"))
    ) {
      return WeykyIntent.WeatherIntent(WeatherQueryType.WEEK_RAIN, rawInput)
    }

    // "هل الجو مناسب للخروج الان؟"
    if ((input.contains("الجو") || input.contains("الطقس") || isWeatherContext) &&
      (input.contains("مناسب للخروج") || input.contains("اخرج") || input.contains("للخروج") || input.contains("طلعه"))
    ) {
      return WeykyIntent.WeatherIntent(WeatherQueryType.OUTDOOR_SUITABILITY, rawInput)
    }

    val isForRingtone = input.contains("رنين") || input.contains("نغمه") || input.contains("مكالمات") || input.contains("ringtone") || input.contains("ring")

    // Contextual Intelligence Domain Queries (e.g. "هل اخرج الان؟" with outdoor departure reasoning)
    val req = com.example.core.intelligence.ContextRequirementsRegistry.resolve(input)
    if (req.domain != com.example.core.intelligence.IntentDomain.GENERAL &&
      req.domain != com.example.core.intelligence.IntentDomain.WEATHER_ONLY &&
      req.domain != com.example.core.intelligence.IntentDomain.DIRECT_COMMAND
    ) {
      return WeykyIntent.ContextualQueryIntent(rawQuery = rawInput, domain = req.domain)
    }

    return when {
      // 1. Settings, Wifi, Bluetooth commands
      input.contains("wifi") || input.contains("wi-fi") || input.contains("واي فاي") || input.contains("الواي فاي") -> {
        WeykyIntent.WifiIntent(openSettings = true)
      }
      input.contains("bluetooth") || input.contains("بلوتوث") -> {
        WeykyIntent.BluetoothIntent
      }
      (input.contains("setting") || input.contains("اعدادات") || input.contains("الإعدادات") || (input.contains("ضبط") && !input.contains("صوت"))) && !input.startsWith("افتح ") && !input.startsWith("open ") -> {
        when {
          input.contains("battery") || input.contains("بطاريه") || input.contains("البطاريه") -> WeykyIntent.SettingsIntent(WeykyIntent.SettingsType.BATTERY)
          input.contains("display") || input.contains("screen") || input.contains("شاشه") || input.contains("الشاشه") -> WeykyIntent.SettingsIntent(WeykyIntent.SettingsType.DISPLAY)
          input.contains("sound") || input.contains("audio") || input.contains("صوت") -> WeykyIntent.SettingsIntent(WeykyIntent.SettingsType.SOUND)
          input.contains("app") || input.contains("تطبيقات") -> WeykyIntent.SettingsIntent(WeykyIntent.SettingsType.APPS)
          else -> WeykyIntent.SettingsIntent(WeykyIntent.SettingsType.GENERAL)
        }
      }

      // 2. Calls: Missed, Outgoing, Incoming, or Specific dial
      input.contains("مكالمه صادره") || input.contains("اتصال صادر") || input.contains("اخر من اتصلت") || input.contains("من اتصلت به") || input.contains("last outgoing call") -> {
        WeykyIntent.CallIntent(queryType = CallQueryType.OUTGOING)
      }
      input.contains("مكالمه وارده") || input.contains("اتصال وارد") || input.contains("اخر مكالمه مستلمه") || input.contains("last incoming call") -> {
        WeykyIntent.CallIntent(queryType = CallQueryType.INCOMING)
      }
      input.contains("من اتصل بي") || input.contains("مين اتصل") || input.contains("من رن علي") || input.contains("who called") -> {
        WeykyIntent.CallIntent(queryType = CallQueryType.INCOMING)
      }
      input.contains("سجل المكالمات") || input.contains("اخر المكالمات") || input.contains("المكالمات الاخيره") -> {
        WeykyIntent.CallIntent(queryType = CallQueryType.ALL)
      }
      input.contains("missed call") || input.contains("مكالمات فائته") || input.contains("مكالمه فائته") ||
      input.contains("المكالمات الفائته") || input.contains("فائته") || input.contains("فائتة") ||
      input.contains("غير مردود") || input.contains("لم ارد") || input.contains("المكالمات") ||
      input.contains("مكالمات") || input.contains("مكالمه") -> {
        WeykyIntent.CallIntent(queryType = CallQueryType.MISSED, showMissed = true)
      }

      // Direct Dial
      input.startsWith("call ") || input.startsWith("dial ") ||
      input.startsWith("اتصل") || input.startsWith("إتصل") || input.startsWith("كلم ") ||
      input.startsWith("خاطب ") || input.startsWith("رن على ") || input.startsWith("هاتف ") || input.startsWith("phone ") -> {
        val target = extractContactTarget(rawInput)
        WeykyIntent.CallIntent(target = target.ifBlank { null }, queryType = CallQueryType.DIAL_TARGET, showMissed = false)
      }

      // 3. Media Next / Previous Track
      input.contains("الاغنيه التاليه") || input.contains("المقطع التالي") || input.contains("التالي") ||
      input.contains("تخطي الاغنيه") || input.contains("غير الاغنيه") || input.contains("next track") || input.contains("next song") || input.contains("skip track") ||
      input == "next" -> {
        WeykyIntent.MediaIntent(WeykyIntent.MediaAction.NEXT)
      }
      input.contains("الاغنيه السابقه") || input.contains("المقطع السابق") || input.contains("السابق") ||
      input.contains("previous track") || input.contains("previous song") || input == "previous" -> {
        WeykyIntent.MediaIntent(WeykyIntent.MediaAction.PREVIOUS)
      }

      // 4. Volume / Audio with Incremental Steps & Percentage
      input.contains("volume") || input.contains("audio") || input.contains("sound") || input.contains("صوت") || input.contains("الصوت") -> {
        val percent = extractVolumePercent(input)
        val stepCount = extractStepCount(input)

        when {
          percent != null -> WeykyIntent.VolumePercentIntent(percent, forRingtone = isForRingtone)
          (input.contains("mute") && !input.contains("unmute")) || input.contains("كتم") || input.contains("اكتم") ->
            WeykyIntent.VolumeMuteIntent(mute = true, forRingtone = isForRingtone)
          input.contains("unmute") || input.contains("الغاء كتم") || input.contains("فك الكتم") ->
            WeykyIntent.VolumeMuteIntent(mute = false, forRingtone = isForRingtone)
          input.contains("up") || input.contains("raise") || input.contains("increase") || input.contains("louder") || input.contains("more") ||
          input.contains("ارفع") || input.contains("رفع") || input.contains("علي") || input.contains("اعلي") || input.contains("زياده") || input.contains("زود") ->
            WeykyIntent.VolumeStepIntent(increase = true, stepCount = stepCount, forRingtone = isForRingtone)
          input.contains("down") || input.contains("lower") || input.contains("decrease") || input.contains("quieter") || input.contains("less") ||
          input.contains("اخفض") || input.contains("خفض") || input.contains("وطي") || input.contains("قلل") || input.contains("نقص") || input.contains("اقل") ->
            WeykyIntent.VolumeStepIntent(increase = false, stepCount = stepCount, forRingtone = isForRingtone)
          else -> WeykyIntent.VolumeStepIntent(increase = true, stepCount = 1, forRingtone = isForRingtone)
        }
      }

      // 5. Open Application Commands
      (input.startsWith("افتح ") || input.startsWith("إفتح ") || input.startsWith("شغل ") ||
       input.startsWith("شغّل ") || input.startsWith("ادخل ") || input.startsWith("انتقل ") ||
       input.startsWith("تطبيق ") || input.startsWith("open ") || input.startsWith("launch ") ||
       input.startsWith("start ") || isDirectAppNameQuery(input)) &&
      !input.contains("كشاف") && !input.contains("فلاش") && !input.contains("مصباح") && !input.contains("ضوء") && !input.contains("نور") &&
      !input.contains("موسيقي") && !input.contains("موسيقى") && !input.contains("اغنيه") && !input.contains("أغنية") &&
      !input.contains("torch") && !input.contains("flashlight") && !input.contains("music") -> {
        val appName = extractAppName(rawInput)
        if (appName.isNotBlank()) {
          WeykyIntent.OpenAppIntent(appName = appName)
        } else {
          WeykyIntent.UnknownIntent(rawInput)
        }
      }

      // 6. Media / Music Play/Pause/Toggle
      (input.contains("music") || input.contains("موسيقي") || input.contains("موسيقى") || input.contains("اغنيه") || input.contains("أغنية") ||
       input == "اوقف" || input == "أوقف" || input == "ايقاف" || input == "وقف" || input == "استمر" || input == "كمل" || input == "pause" || input == "play") &&
      (input.contains("pause") || input.contains("stop") || input.contains("play") || input.contains("resume") ||
       input.contains("شغل") || input.contains("تشغيل") || input.contains("اوقف") || input.contains("أوقف") || input.contains("ايقاف") || input.contains("وقف") || input.contains("استمر") || input.contains("كمل")) -> {
        when {
          input.contains("pause") || input.contains("stop") || input.contains("اوقف") || input.contains("أوقف") || input.contains("ايقاف") || input.contains("إيقاف") || input.contains("وقف") ->
            WeykyIntent.MediaIntent(WeykyIntent.MediaAction.PAUSE)
          input.contains("play") || input.contains("resume") || input.contains("شغل") || input.contains("تشغيل") || input.contains("استمر") ->
            WeykyIntent.MediaIntent(WeykyIntent.MediaAction.PLAY)
          else -> WeykyIntent.MediaIntent(WeykyIntent.MediaAction.TOGGLE)
        }
      }

      // 7. Battery
      input.contains("battery") || input.contains("charge") || input.contains("charging") ||
      input.contains("بطاريه") || input.contains("البطاريه") || input.contains("شحن") -> {
        if (input.contains("setting") || input.contains("اعدادات") || input.contains("ضبط")) {
          WeykyIntent.SettingsIntent(WeykyIntent.SettingsType.BATTERY)
        } else {
          WeykyIntent.BatteryIntent
        }
      }

      // 8. Storage
      input.contains("storage") || input.contains("disk") || input.contains("free space") || input.contains("space left") ||
      input.contains("تخزين") || input.contains("مساحه") || input.contains("المساحه") -> {
        WeykyIntent.StorageIntent
      }

      // 9. RAM
      input.contains("ram") || input.contains("memory") || input.contains("ذاكره") || input.contains("الرام") -> {
        WeykyIntent.RamIntent
      }

      // 10. Flashlight
      input.contains("flashlight") || input.contains("torch") || (input.contains("light") && !input.contains("sun")) ||
      input.contains("كشاف") || input.contains("فلاش") || input.contains("مصباح") -> {
        val turnOff = input.contains("off") || input.contains("stop") || input.contains("disable") ||
          input.contains("اطفئ") || input.contains("أطفئ") || input.contains("ايقاف") || input.contains("طفي") || input.contains("اغلق")
        val turnOn = input.contains("on") || input.contains("enable") || input.contains("start") ||
          input.contains("شغل") || input.contains("تشغيل") || input.contains("افتح") || input.contains("اضئ") || input.contains("ولع")
        if (turnOff) WeykyIntent.FlashlightIntent(turnOn = false)
        else if (turnOn) WeykyIntent.FlashlightIntent(turnOn = true)
        else WeykyIntent.FlashlightIntent(turnOn = true)
      }

      // 11. Notifications
      input.contains("notification") || input.contains("اشعار") || input.contains("تنبيه") -> {
        val openSettings = input.contains("setting") || input.contains("permission") || input.contains("access") ||
          input.contains("اعدادات") || input.contains("صلاحيه") || input.contains("ضبط")
        WeykyIntent.NotificationIntent(openSettings = openSettings)
      }

      // 12. Weather General
      input.contains("weather") || input.contains("forecast") || input.contains("rain") || input.contains("sunny") || input.contains("outside") ||
      input.contains("طقس") || input.contains("الطقس") || input.contains("جو") || input.contains("امطار") || input.contains("حراره") -> {
        WeykyIntent.WeatherIntent(WeatherQueryType.CURRENT, rawInput)
      }

      // 13. Time and Date
      input.contains("time") || input.contains("clock") || input.contains("date") || input.contains("today") ||
      input.contains("وقت") || input.contains("ساعه") || input.contains("تاريخ") || input.contains("اليوم") -> {
        WeykyIntent.TimeIntent
      }

      // 14. Context
      input.contains("context") || input.contains("where am i") || input.contains("in pocket") || input.contains("on table") ||
      input.contains("سياق") || ((input.contains("وضع") || input.contains("حاله") || input.contains("اين")) && (input.contains("هاتف") || input.contains("جيب") || input.contains("طاوله"))) -> {
        WeykyIntent.ContextIntent
      }

      // 15. Device specs
      input.contains("device") || input.contains("phone") || input.contains("status") || input.contains("specs") || input.contains("info") ||
      input.contains("جهاز") || input.contains("هاتف") || input.contains("مواصفات") -> {
        WeykyIntent.DeviceInfoIntent
      }

      // 16. Help
      input.contains("help") || input.contains("what can you do") || input.contains("features") || input.contains("hello") || input.contains("hi") ||
      input.contains("مساعده") || input.contains("ماذا تستطيع") || input.contains("مرحبا") || input.contains("اهلا") || input.contains("اوامر") -> {
        WeykyIntent.HelpIntent
      }

      else -> WeykyIntent.ConversationalIntent(rawInput)
    }
  }
}
