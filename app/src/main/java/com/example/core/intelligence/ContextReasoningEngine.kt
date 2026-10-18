package com.example.core.intelligence

import com.example.core.context.ContextState
import com.example.device.battery.ChargingState
import java.util.Locale

/**
 * Deterministic, explainable reasoning engine that connects disparate telemetry streams,
 * distinguishes facts from inferences, and provides actionable recommendations.
 */
class ContextReasoningEngine {

  fun synthesize(
    requirements: ContextRequirements,
    snapshot: SituationSnapshot,
    userQuery: String
  ): ContextualSynthesis {
    val isArabic = userQuery.any { it in '\u0600'..'\u06FF' } || Locale.getDefault().language.startsWith("ar")

    return when (requirements.domain) {
      IntentDomain.OUTDOOR_DEPARTURE -> reasonOutdoorDeparture(snapshot, isArabic)
      IntentDomain.APPOINTMENT_TIMING -> reasonAppointmentTiming(snapshot, isArabic)
      IntentDomain.BATTERY_SUFICIENCY -> reasonBatterySufficiency(snapshot, isArabic)
      IntentDomain.SITUATION_OVERVIEW -> reasonSituationOverview(snapshot, isArabic)
      IntentDomain.WEATHER_ONLY -> reasonWeatherOnly(snapshot, isArabic)
      else -> reasonGeneral(snapshot, userQuery, isArabic)
    }
  }

  // 1. "هل اخرج الان؟"
  private fun reasonOutdoorDeparture(
    snapshot: SituationSnapshot,
    isArabic: Boolean
  ): ContextualSynthesis {
    val facts = mutableListOf<String>()
    val inferences = mutableListOf<String>()
    val recommendations = mutableListOf<String>()
    val unavailabilities = mutableListOf<String>()

    val weather = snapshot.weather
    val battery = snapshot.battery
    val calendar = snapshot.calendar
    val physical = snapshot.physicalContext

    val temp = weather.value?.temperatureCelsius
    val cond = weather.value?.condition ?: ""
    val isRaining = cond.contains("Rain", ignoreCase = true) || cond.contains("مطر", ignoreCase = true) ||
      cond.contains("أمطار", ignoreCase = true) || cond.contains("Drizzle", ignoreCase = true) || cond.contains("Thunderstorm", ignoreCase = true)

    if (weather.isAvailable && temp != null) {
      facts.add(if (isArabic) "الطقس: $cond عند ${temp.toInt()}°م" else "Weather is $cond at ${temp.toInt()}°C")
    } else {
      unavailabilities.add(if (isArabic) "بيانات الطقس غير متاحة حالياً" else "Weather data unavailable")
    }

    val nextEvent = calendar.value?.nextEvent
    if (calendar.isAvailable && nextEvent != null) {
      facts.add(if (isArabic) "موعد قادم '${nextEvent.title}' بعد ${nextEvent.minutesUntilStart} دقيقة" else "Next appointment '${nextEvent.title}' in ${nextEvent.minutesUntilStart} mins")
    }

    val battPercent = battery.value?.percentage
    val isCharging = battery.value?.chargingState == ChargingState.CHARGING
    if (battery.isAvailable && battPercent != null) {
      facts.add(if (isArabic) "البطارية: $battPercent%" else "Battery $battPercent%")
      if (battPercent < 20 && !isCharging) {
        inferences.add(if (isArabic) "البطارية منخفضة وقد تنفد أثناء التنقل" else "Battery is low and may deplete during transit")
        recommendations.add(if (isArabic) "اشحن الهاتف قبل المغادرة" else "Charge phone before leaving")
      }
    }

    if (physical.isAvailable) {
      if (physical.value?.state == ContextState.MOVING) {
        inferences.add(if (isArabic) "يبدو أنك تتحرك بالفعل" else "You appear to already be in motion")
      }
    }

    // Synthesis builder
    val speech = StringBuilder()
    if (isArabic) {
      if (weather.isAvailable && temp != null) {
        speech.append("الأجواء $cond عند ${temp.toInt()}°م.")
        if (isRaining) speech.append(" يُنصح بأخذ مظلة بسبب الأمطار.")
      }
      if (nextEvent != null) {
        speech.append(" لديك موعد (${nextEvent.title}) بعد ${nextEvent.minutesUntilStart} دقيقة.")
      }
      if (battery.isAvailable && battPercent != null && battPercent < 20 && !isCharging) {
        speech.append(" تنبيه: البطارية منخفضة ($battPercent%)، يُفضل شحن الهاتف قليلاً قبل الخروج.")
      } else {
        speech.append(" الوضع مناسب للخروج الآن.")
      }
    } else {
      if (weather.isAvailable && temp != null) {
        speech.append("It's ${temp.toInt()}°C with $cond.")
        if (isRaining) speech.append(" Rain detected, take an umbrella.")
      }
      if (nextEvent != null) {
        speech.append(" You have '${nextEvent.title}' in ${nextEvent.minutesUntilStart} minutes.")
      }
      if (battery.isAvailable && battPercent != null && battPercent < 20 && !isCharging) {
        speech.append(" Battery is at $battPercent%, consider charging before heading out.")
      } else {
        speech.append(" Conditions are favorable to head out.")
      }
    }

    val nextStep = if (battPercent != null && battPercent < 20 && !isCharging) {
      NextStep(label = if (isArabic) "تفعيل توفير الطاقة" else "Enable Battery Saver")
    } else null

    return ContextualSynthesis(
      synthesizedSpeech = speech.toString().trim(),
      primaryDecision = if (isRaining) "الخروج مع مظلة" else "مناسب للخروج",
      nextStep = nextStep,
      factsUsed = facts,
      inferencesMade = inferences,
      recommendations = recommendations,
      unavailabilitiesHandled = unavailabilities,
      confidenceScore = if (weather.isAvailable) 0.95f else 0.7f
    )
  }

  // 2. "هل سأصل في الموعد؟" / "هل بلحق الموعد"
  private fun reasonAppointmentTiming(
    snapshot: SituationSnapshot,
    isArabic: Boolean
  ): ContextualSynthesis {
    val facts = mutableListOf<String>()
    val inferences = mutableListOf<String>()
    val recommendations = mutableListOf<String>()
    val unavailabilities = mutableListOf<String>()

    val calendar = snapshot.calendar
    val physical = snapshot.physicalContext

    val nextEvent = calendar.value?.nextEvent
    if (!calendar.isAvailable || nextEvent == null) {
      val msg = if (isArabic) {
        "لا توجد مواعيد مسجلة في تقويمك اليوم لتقدير وقت الوصول."
      } else {
        "No appointments found on your calendar for today to evaluate timing."
      }
      return ContextualSynthesis(
        synthesizedSpeech = msg,
        primaryDecision = "No Appointments",
        factsUsed = emptyList(),
        unavailabilitiesHandled = listOf(calendar.unavailabilityReason ?: "لا توجد مواعيد")
      )
    }

    facts.add(if (isArabic) "الموعد القادم: '${nextEvent.title}' بعد ${nextEvent.minutesUntilStart} دقيقة" else "Next appointment: '${nextEvent.title}' in ${nextEvent.minutesUntilStart} mins")
    nextEvent.location?.let {
      facts.add(if (isArabic) "الموقع: $it" else "Event location: $it")
    }

    // Traffic estimation telemetry: without external turn-by-turn API, we treat estimated trip duration
    // conservatively (e.g. 20-30 mins estimate if location is specified).
    unavailabilities.add(if (isArabic) "بيانات الازدحام المروري اللحظية غير متصلة" else "Live traffic data is currently unavailable")
    val estimatedTripMinutes = if (nextEvent.location.isNullOrBlank()) 20L else 30L
    val marginMinutes = nextEvent.minutesUntilStart - estimatedTripMinutes

    val isMoving = physical.value?.state == ContextState.MOVING

    val speech = StringBuilder()
    if (isArabic) {
      speech.append("موعدك '${nextEvent.title}' يبدأ بعد ${nextEvent.minutesUntilStart} دقيقة.")
      if (marginMinutes > 15) {
        speech.append(" الوقت كافٍ جداً، لديك هامش مريح (~$marginMinutes دقيقة بعد احتساب وقت الطريق التقديري).")
      } else if (marginMinutes in 0..15) {
        speech.append(" الوقت ضيق نسبياً (الهامش ~$marginMinutes دقيقة). أنصحك بالانطلاق فوراً.")
      } else {
        speech.append(" يبدو أن الوقت المتبقي أقل من مدة الطريق التقديرية، قد تتأخر قليلاً.")
      }
      if (!isMoving) {
        speech.append(" (أنت في وضع ثبات حالياً).")
      }
    } else {
      speech.append("Your appointment '${nextEvent.title}' is in ${nextEvent.minutesUntilStart} minutes.")
      if (marginMinutes > 15) {
        speech.append(" You should comfortably arrive on time with roughly $marginMinutes minutes of buffer.")
      } else if (marginMinutes in 0..15) {
        speech.append(" Timing is tight (~$marginMinutes mins buffer). It is recommended to leave immediately.")
      } else {
        speech.append(" You may be late as estimated travel exceeds the remaining time.")
      }
    }

    return ContextualSynthesis(
      synthesizedSpeech = speech.toString().trim(),
      primaryDecision = if (marginMinutes >= 0) "ستصل في الوقت" else "احتمال تأخير",
      factsUsed = facts,
      inferencesMade = listOf("تقدير مدة التنقل: ~$estimatedTripMinutes دقيقة"),
      recommendations = if (marginMinutes <= 15) listOf(if (isArabic) "انطلق الآن" else "Leave now") else emptyList(),
      unavailabilitiesHandled = unavailabilities,
      confidenceScore = 0.85f
    )
  }

  // 3. "هل البطارية ستكفي؟"
  private fun reasonBatterySufficiency(
    snapshot: SituationSnapshot,
    isArabic: Boolean
  ): ContextualSynthesis {
    val facts = mutableListOf<String>()
    val inferences = mutableListOf<String>()
    val recommendations = mutableListOf<String>()

    val battery = snapshot.battery.value
    val calendar = snapshot.calendar.value
    val physical = snapshot.physicalContext.value

    if (battery == null) {
      val fallback = if (isArabic) "بيانات البطارية غير متوفرة حالياً." else "Battery information unavailable."
      return ContextualSynthesis(synthesizedSpeech = fallback, primaryDecision = "Unknown")
    }

    val pct = battery.percentage
    val isCharging = battery.chargingState == ChargingState.CHARGING
    facts.add(if (isArabic) "نسبة البطارية الحالية: $pct%" else "Battery level $pct%")

    val nextEvent = calendar?.nextEvent

    val speech = StringBuilder()
    if (isCharging) {
      speech.append(
        if (isArabic) "القياس الفعلي المؤكد: البطارية قيد الشحن حالياً عند $pct% ولن تنفد طالما الهاتف متصل بالشاحن."
        else "Confirmed measurement: Battery is currently charging at $pct% and will not deplete while connected to power."
      )
    } else if (pct > 50) {
      speech.append(
        if (isArabic) "القياس الفعلي المؤكد من النظام هو $pct%. التقدير العام يشير إلى أنها ستكفي للاستخدام المعتاد حتى المساء، لكن هذا تقدير تقريبي وليس قياساً مضموناً من الهاتف؛ إذ يعتمد الصمود الفعلي على سطوع ووقت تشغيل الشاشة، قوة تغطية الشبكة وبيانات 4G/5G، التطبيقات النشطة في الخلفية، وصحة البطارية."
        else "Actual measurement from the system is $pct% battery. General estimation suggests this should cover typical usage until evening. However, this is an approximation rather than a guaranteed phone measurement; actual endurance depends on screen-on time, network signal/cellular data, background apps, and battery health."
      )
      if (nextEvent != null) {
        speech.append(
          if (isArabic) " كما أن لديك موعداً بعد ${nextEvent.minutesUntilStart} دقيقة."
          else " You also have an upcoming appointment in ${nextEvent.minutesUntilStart} minutes."
        )
      }
    } else if (pct in 21..50) {
      speech.append(
        if (isArabic) "القياس الفعلي المؤكد هو $pct%. كتقدير عام، قد تكفي لعدة ساعات من الاستخدام الخفيف، لكن استمرارها حتى المساء غير مضمون ويتوقف بشدة على استهلاك الشاشة والشبكة والتطبيقات الخلفية."
        else "Actual measurement is $pct% battery. Generally, this may last several hours of moderate use, but lasting until evening is uncertain without a top-up, depending heavily on screen time, network radios, and background processes."
      )
      if (nextEvent != null && nextEvent.minutesUntilStart > 180) {
        speech.append(
          if (isArabic) " لديك التزامات لاحقة اليوم، لذا يُنصح بشحن الهاتف احتياطياً."
          else " You have later commitments today, so topping up charge is recommended."
        )
      }
    } else {
      speech.append(
        if (isArabic) "القياس الفعلي المؤكد: البطارية منخفضة جداً عند $pct%. كتقدير عام، لن تكفي حتى المساء بدون تفعيل وضع توفير الطاقة أو الشحن الفوري."
        else "Actual measurement: Battery is critically low at $pct%. Generally, it will not last until evening without power saving mode or immediate charging."
      )
      recommendations.add(if (isArabic) "توصيل الشاحن أو تفعيل توفير الطاقة" else "Connect charger or enable battery saver")
    }

    return ContextualSynthesis(
      synthesizedSpeech = speech.toString().trim(),
      primaryDecision = if (pct > 30 || isCharging) "البطارية كافية" else "تحتاج شحن",
      factsUsed = facts,
      inferencesMade = inferences,
      recommendations = recommendations,
      confidenceScore = 0.85f
    )
  }

  // 4. "ماذا أفعل الآن؟"
  private fun reasonSituationOverview(
    snapshot: SituationSnapshot,
    isArabic: Boolean
  ): ContextualSynthesis {
    val facts = mutableListOf<String>()
    val recommendations = mutableListOf<String>()

    val nextEvent = snapshot.calendar.value?.nextEvent
    val missedCalls = snapshot.calls.value?.latestMissedCall
    val missedCount = snapshot.calls.value?.missedCallsCount ?: 0
    val battery = snapshot.battery.value
    val unreadNotifs = snapshot.notifications.value?.totalCount ?: 0

    val speech = StringBuilder()
    if (isArabic) {
      if (missedCount > 0 && missedCalls != null) {
        val caller = missedCalls.callerName ?: missedCalls.number
        speech.append("لديك مكالمة فائتة من $caller.")
        recommendations.add("معاودة الاتصال بـ $caller")
      }
      if (nextEvent != null) {
        speech.append(" موعدك القادم '${nextEvent.title}' بعد ${nextEvent.minutesUntilStart} دقيقة.")
      }
      if (battery != null && battery.percentage < 20 && battery.chargingState != ChargingState.CHARGING) {
        speech.append(" البطارية منخفضة (${battery.percentage}%)، يُستحسن شحنها.")
      }
      if (speech.isEmpty()) {
        speech.append("كل شيء هادئ الآن؛ لا توجد مكالمات فائتة أو مواعيد عاجلة. يمكنك الاسترخاء أو التركيز على مهامك.")
      }
    } else {
      if (missedCount > 0 && missedCalls != null) {
        val caller = missedCalls.callerName ?: missedCalls.number
        speech.append("You have a missed call from $caller.")
      }
      if (nextEvent != null) {
        speech.append(" Next event is '${nextEvent.title}' in ${nextEvent.minutesUntilStart} minutes.")
      }
      if (battery != null && battery.percentage < 20 && battery.chargingState != ChargingState.CHARGING) {
        speech.append(" Battery is at ${battery.percentage}%, consider charging.")
      }
      if (speech.isEmpty()) {
        speech.append("Everything is quiet right now with no urgent tasks or alerts.")
      }
    }

    return ContextualSynthesis(
      synthesizedSpeech = speech.toString().trim(),
      primaryDecision = "Overview",
      factsUsed = facts,
      recommendations = recommendations,
      confidenceScore = 0.95f
    )
  }

  // 5. "الطقس" / "ما حالة الطقس؟"
  private fun reasonWeatherOnly(
    snapshot: SituationSnapshot,
    isArabic: Boolean
  ): ContextualSynthesis {
    val weather = snapshot.weather.value
    val speech = if (weather != null && weather.isAvailable && weather.temperatureCelsius != null) {
      val t = weather.temperatureCelsius.toInt()
      val c = weather.condition ?: ""
      if (isArabic) "الطقس الآن: $c ودرجة الحرارة $t°م." else "It's $t°C with $c."
    } else {
      if (isArabic) "بيانات الطقس غير متاحة حالياً." else "Weather information is temporarily unavailable."
    }
    return ContextualSynthesis(
      synthesizedSpeech = speech,
      primaryDecision = "Weather Report",
      factsUsed = listOf(speech),
      confidenceScore = 1.0f
    )
  }

  private fun reasonGeneral(
    snapshot: SituationSnapshot,
    userQuery: String,
    isArabic: Boolean
  ): ContextualSynthesis {
    val speech = if (isArabic) "تم استلام طلبك: $userQuery" else "Received request: $userQuery"
    return ContextualSynthesis(synthesizedSpeech = speech, primaryDecision = "General Query")
  }

  // 6. Hypothetical: "لو شغلت الموسيقى الآن هل سيؤثر على البطارية؟" (Estimates battery impact without playing music)
  fun reasonHypotheticalBatteryMusic(
    snapshot: SituationSnapshot,
    isArabic: Boolean
  ): ContextualSynthesis {
    val battery = snapshot.battery.value
    val pct = battery?.percentage ?: 50
    val isCharging = battery?.chargingState == ChargingState.CHARGING

    val speech = if (isArabic) {
      if (isCharging) {
        "القياس الفعلي المؤكد: الهاتف قيد الشحن حالياً عند $pct%، لذا فإن تشغيل الموسيقى لن يستهلك من طاقة البطارية. (لم أقم بتشغيل الموسيقى؛ إذا أردت تشغيلها قل: شغّل الموسيقى الآن)."
      } else {
        "القياس الفعلي المؤكد من النظام هو أن بطاريتك الآن عند $pct%. أما بخصوص استهلاك الموسيقى، فإن نسبة 4% إلى 8% في الساعة هي تقدير إحصائي عام للهواتف وليست قياساً خاصاً بجهازك؛ وهي قد تقلل صمود البطارية بنحو ساعة إلى ساعتين تقريباً. ما يحد من دقة هذا التقدير هو: مستوى الصوت، استخدام مكبر الصوت الخارجي أم سماعات الرأس، البث عبر الإنترنت 4G/5G/Wi-Fi أم تشغيل ملفات محلية، وحالة إضاءة الشاشة. (لم أقم بتشغيل الموسيقى، قل 'شغّل الموسيقى الآن' إذا رغبت بالبدء)."
      }
    } else {
      if (isCharging) {
        "Confirmed actual measurement: Phone is currently charging at $pct%. Music playback will not deplete power while connected to charger. I have not started playback; say 'play music now' if you wish to start."
      } else {
        "Actual confirmed measurement from the system is $pct% battery. Regarding music playback, the estimated 4% to 8% per hour is only a general statistical average across smartphones, not a dedicated measurement for your specific phone. It may reduce battery life by roughly 1 to 2 hours. Factors limiting the precision of this estimate include: volume level, built-in loudspeaker vs headphones, local audio playback vs cellular/Wi-Fi streaming, and screen-on state. I have not started music; say 'play music now' to begin."
      }
    }

    return ContextualSynthesis(
      synthesizedSpeech = speech,
      primaryDecision = "Music Impact Estimated (No Playback)",
      factsUsed = listOf("Measured Battery: $pct%", "General statistical estimate: ~4-8%/hr"),
      inferencesMade = listOf("Actual consumption depends on volume, speaker/headset, streaming, and screen state"),
      recommendations = listOf(if (isArabic) "تشغيل الموسيقى عند الرغبة" else "Play music when desired"),
      nextStep = NextStep(label = if (isArabic) "شغّل الموسيقى الآن" else "Play music now", actionIntent = "شغل الموسيقى"),
      confidenceScore = 0.85f
    )
  }

  // 7. Hypothetical: "ماذا لو تأخرت 10 دقائق؟" (Calculates impact on arrival margin)
  fun reasonHypotheticalScheduleDelay(
    snapshot: SituationSnapshot,
    delayMinutes: Int,
    isArabic: Boolean
  ): ContextualSynthesis {
    val calendar = snapshot.calendar
    val nextEvent = calendar.value?.nextEvent

    if (!calendar.isAvailable || nextEvent == null) {
      val msg = if (isArabic) {
        "لا توجد مواعيد مسجلة في تقويمك اليوم، لذا فإن التأخر لمدة $delayMinutes دقائق لن يؤثر على أي التزام مجدول."
      } else {
        "There are no scheduled events on your calendar today, so a $delayMinutes-minute delay won't affect any commitments."
      }
      return ContextualSynthesis(
        synthesizedSpeech = msg,
        primaryDecision = "No Appointments",
        confidenceScore = 0.9f
      )
    }

    val estimatedTripMinutes = if (nextEvent.location.isNullOrBlank()) 20L else 30L
    val originalMargin = nextEvent.minutesUntilStart - estimatedTripMinutes
    val delayedMargin = originalMargin - delayMinutes

    val speech = if (isArabic) {
      val sb = StringBuilder()
      sb.append("موعدك القادم '${nextEvent.title}' يبدأ بعد ${nextEvent.minutesUntilStart} دقيقة (وقت الطريق التقديري ~$estimatedTripMinutes دقيقة). ")
      if (delayedMargin > 10) {
        sb.append("لو تأخرت $delayMinutes دقائق، سيتبقى لديك هامش أمان مريح (~$delayedMargin دقيقة) وستصل في الموعد.")
      } else if (delayedMargin in 0..10) {
        sb.append("لو تأخرت $delayMinutes دقائق، سيصبح الوقت حرجاً جداً (الهامش المتبقي $delayedMargin دقائق فقط)، لذا لا يُنصح بالتأخر.")
      } else {
        sb.append("لو تأخرت $delayMinutes دقائق، ستصل متأخراً عن موعدك بحوالي ${-delayedMargin} دقيقة. يُفضل الانطلاق فوراً.")
      }
      sb.toString().trim()
    } else {
      val sb = StringBuilder()
      sb.append("Your event '${nextEvent.title}' is in ${nextEvent.minutesUntilStart} mins (estimated travel ~$estimatedTripMinutes mins). ")
      if (delayedMargin > 10) {
        sb.append("A $delayMinutes-minute delay leaves you with ~$delayedMargin mins buffer; you should still make it.")
      } else if (delayedMargin in 0..10) {
        sb.append("A $delayMinutes-minute delay tightens your buffer to just $delayedMargin mins; leaving now is advised.")
      } else {
        sb.append("A $delayMinutes-minute delay will make you late by roughly ${-delayedMargin} minutes. You should leave immediately.")
      }
      sb.toString().trim()
    }

    return ContextualSynthesis(
      synthesizedSpeech = speech,
      primaryDecision = if (delayedMargin >= 0) "ممكن مع هامش ضيق" else "سيسبب تأخيراً",
      factsUsed = listOf("Event: ${nextEvent.title}", "Delay considered: $delayMinutes mins"),
      inferencesMade = listOf("Buffer after delay: $delayedMargin mins"),
      recommendations = if (delayedMargin <= 10) listOf(if (isArabic) "انطلق بدون تأخير" else "Leave now without delay") else emptyList(),
      confidenceScore = 0.9f
    )
  }
}
