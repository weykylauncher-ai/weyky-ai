package com.example.core.intent.handlers

import android.Manifest
import com.example.core.intent.ExecutionResult
import com.example.core.intent.WeatherQueryType
import com.example.device.weather.LocationProvider
import com.example.device.weather.WeatherProvider
import com.example.device.weather.WeatherSnapshot
import com.example.rendering.visual_state.CoreVisualState
import java.util.Locale

class WeatherActionHandler(
  private val weatherProvider: WeatherProvider,
  private val locationProvider: LocationProvider
) {

  suspend fun handle(queryType: WeatherQueryType = WeatherQueryType.CURRENT): ExecutionResult {
    val isArabic = Locale.getDefault().language.startsWith("ar")

    if (!locationProvider.hasLocationPermission()) {
      return ExecutionResult.RequiresPermission(
        permission = Manifest.permission.ACCESS_COARSE_LOCATION,
        rationale = if (isArabic) "يلزم منح صلاحية الموقع لجلب حالة الطقس الحقيقية لمنطقتك." else "Location permission is required to fetch real weather conditions for your area."
      )
    }

    val w = weatherProvider.fetchWeather()
    if (!w.isAvailable) {
      return ExecutionResult.Unavailable(
        reason = w.statusMessage ?: if (isArabic) "بيانات الطقس غير متاحة مؤقتاً." else "Weather information is temporarily unavailable.",
        visualState = CoreVisualState.RESPONDING
      )
    }

    val speech = when (queryType) {
      WeatherQueryType.CURRENT -> formatCurrentWeather(w, isArabic)
      WeatherQueryType.HOURLY_EVENING -> formatEveningWeather(w, isArabic)
      WeatherQueryType.RAIN_SOON -> formatRainSoon(w, isArabic)
      WeatherQueryType.WIND_TOMORROW -> formatWindTomorrow(w, isArabic)
      WeatherQueryType.OUTDOOR_SUITABILITY -> formatOutdoorSuitability(w, isArabic)
      WeatherQueryType.COMPARE_TOMORROW -> formatCompareTomorrow(w, isArabic)
      WeatherQueryType.WEEK_RAIN -> formatWeekRain(w, isArabic)
    }

    return ExecutionResult.Success(
      speech = speech,
      actionTaken = "Queried Weather (${queryType.name})",
      visualState = CoreVisualState.RESPONDING
    )
  }

  private fun formatCurrentWeather(w: WeatherSnapshot, isArabic: Boolean): String {
    val temp = w.temperatureCelsius?.let { "${it.toInt()}°" } ?: "--"
    val feels = w.apparentTemperatureCelsius?.let { " (المحسوسة ${it.toInt()}°)" } ?: ""
    val cond = w.condition ?: "مستقر"
    val maxMin = if (w.maxTempToday != null && w.minTempToday != null) {
      " العظمى اليوم ${w.maxTempToday.toInt()}° والصغرى ${w.minTempToday.toInt()}°."
    } else ""
    val rainProb = w.rainProbTodayMax?.let { " احتمال الأمطار $it%." } ?: ""
    val wind = if (w.windSpeedKmH != null) {
      val dir = w.windDirectionCompass?.let { " ($it)" } ?: ""
      " سرعة الرياح ${w.windSpeedKmH.toInt()} كم/س$dir."
    } else ""
    val humidity = w.humidityPercent?.let { " الرطوبة $it%." } ?: ""
    val uv = w.uvIndex?.let { " مؤشر الأشعة UV: ${String.format(Locale.US, "%.1f", it)}." } ?: " (مؤشر الأشعة UV غير متوفر)."
    val cacheNote = if (w.isCached) " [بيانات مخزنة منذ ${w.lastUpdatedFormatted}]." else ""

    return if (isArabic) {
      "الطقس الآن: $cond ودرجة الحرارة $temp مئوية$feels.$maxMin$rainProb$wind$humidity$uv$cacheNote"
    } else {
      val feelsEn = w.apparentTemperatureCelsius?.let { " (feels like ${it.toInt()}°C)" } ?: ""
      "Current weather: $cond at $temp°C$feelsEn. Rain probability ${w.rainProbTodayMax ?: 0}%. Wind: ${w.windSpeedKmH?.toInt() ?: 0} km/h."
    }
  }

  private fun formatEveningWeather(w: WeatherSnapshot, isArabic: Boolean): String {
    val eveningItems = w.hourlyForecast.filter { item ->
      val hour = item.hourLabel.substringBefore(":").toIntOrNull() ?: -1
      hour in 18..23
    }
    if (eveningItems.isEmpty()) {
      return if (isArabic) {
        "في المساء، يُتوقع أن تميل الأجواء إلى الاعتدال مع درجة حرارة تقارب ${w.minTempToday?.toInt() ?: w.temperatureCelsius?.toInt() ?: 20}° مئوية."
      } else {
        "Evening forecast suggests temperatures around ${w.minTempToday?.toInt() ?: 20}°C."
      }
    }
    val avgTemp = eveningItems.map { it.temperature }.average().toInt()
    val maxRain = eveningItems.maxOfOrNull { it.precipitationProbability } ?: 0
    val mainCond = eveningItems.firstOrNull()?.condition ?: "صافٍ"
    return if (isArabic) {
      val rainNote = if (maxRain > 30) " مع احتمال هطول أمطار بنسبة $maxRain%." else " ولا توجد فرص عالية للأمطار."
      "توقعات المساء: الأجواء $mainCond ومتوسط الحرارة حول $avgTemp° مئوية$rainNote"
    } else {
      "This evening: $mainCond, temperature around $avgTemp°C with $maxRain% rain chance."
    }
  }

  private fun formatRainSoon(w: WeatherSnapshot, isArabic: Boolean): String {
    val nextHours = w.hourlyForecast.take(3)
    if (nextHours.isEmpty()) {
      val prob = w.rainProbTodayMax ?: 0
      return if (isArabic) {
        if (prob > 40) "هناك احتمال للأمطار اليوم بنسبة $prob%." else "احتمال المطر اليوم منخفض ($prob%)."
      } else {
        "Rain probability for today is $prob%."
      }
    }
    val maxProb = nextHours.maxOfOrNull { it.precipitationProbability } ?: 0
    val totalPrecip = nextHours.sumOf { it.precipitationMm.toDouble() }.toFloat()
    return if (isArabic) {
      if (maxProb >= 40) {
        "نعم، هناك احتمال لهطول أمطار خلال الساعات القليلة القادمة بنسبة $maxProb% (كمية متوقعة ~$totalPrecip ملم). يُنصح بأخذ مظلة."
      } else {
        "لا يُتوقع هطول أمطار خلال الساعتين القادمتين؛ الاحتمال منخفض ($maxProb%)."
      }
    } else {
      if (maxProb >= 40) {
        "Yes, rain is likely in the next two hours with $maxProb% probability. Take an umbrella."
      } else {
        "No rain expected in the next couple of hours (probability $maxProb%)."
      }
    }
  }

  private fun formatWindTomorrow(w: WeatherSnapshot, isArabic: Boolean): String {
    val tom = w.dailyForecast.getOrNull(1)
    val maxWind = tom?.maxWindSpeedKmH ?: w.windSpeedTomorrowMax
    val gusts = tom?.maxWindGustsKmH
    return if (isArabic) {
      if (maxWind != null) {
        val gustText = if (gusts != null && gusts > maxWind) " مع هبات تصل إلى ${gusts.toInt()} كم/س." else ""
        "غداً ستصل سرعة الرياح القصوى إلى ${maxWind.toInt()} كم/س$gustText"
      } else {
        "بيانات الرياح ليوم غد غير متوفرة حالياً."
      }
    } else {
      "Wind tomorrow will reach around ${maxWind?.toInt() ?: 0} km/h."
    }
  }

  private fun formatOutdoorSuitability(w: WeatherSnapshot, isArabic: Boolean): String {
    val temp = w.temperatureCelsius ?: 25f
    val rainProb = w.rainProbTodayMax ?: 0
    val wind = w.windSpeedKmH ?: 10f
    val isRaining = w.condition?.contains("مطر") == true || rainProb > 50

    return if (isArabic) {
      val sb = StringBuilder()
      if (isRaining) {
        sb.append("الأجواء ماطرة أو هناك احتمال مرتفع للمطر ($rainProb%). إذا قررت الخروج فاحرص على أخذ مظلة.")
      } else if (wind > 35) {
        sb.append("الرياح نشطة حالياً (${wind.toInt()} كم/س)، يُفضل الحذر أثناء التنقل الخارجي.")
      } else if (temp > 38) {
        sb.append("الجو حار حالياً (${temp.toInt()}°م)، يُفضل شرب الماء وتجنب الشمس المباشرة.")
      } else {
        sb.append("نعم، الجو مناسب جداً للخروج الآن؛ الأجواء ${w.condition ?: "معتدلة"} والحرارة ${temp.toInt()}° مئوية.")
      }
      sb.toString()
    } else {
      if (isRaining) "Weather is rainy ($rainProb% chance), take an umbrella."
      else "Conditions are favorable for going out (${temp.toInt()}°C, $wind km/h wind)."
    }
  }

  private fun formatCompareTomorrow(w: WeatherSnapshot, isArabic: Boolean): String {
    val maxToday = w.maxTempToday?.toInt()
    val minToday = w.minTempToday?.toInt()
    val maxTom = w.maxTempTomorrow?.toInt()
    val minTom = w.minTempTomorrow?.toInt()
    val condToday = w.condition ?: "مستقر"
    val condTom = w.conditionTomorrow ?: "مستقر"
    val rainTom = w.rainProbTomorrowMax ?: 0

    return if (isArabic) {
      if (maxToday != null && maxTom != null) {
        "مقارنة الطقس: اليوم الأجواء $condToday (العظمى $maxToday° والصغرى $minToday°). أما غداً فالأجواء $condTom (العظمى $maxTom° والصغرى $minTom°) مع احتمال أمطار $rainTom%."
      } else {
        "اليوم الطقس $condToday، وغداً يُتوقع أن يكون $condTom مع احتمال أمطار $rainTom%."
      }
    } else {
      "Today: $condToday ($maxToday°C / $minToday°C). Tomorrow: $condTom ($maxTom°C / $minTom°C)."
    }
  }

  private fun formatWeekRain(w: WeatherSnapshot, isArabic: Boolean): String {
    val rainDays = w.dailyForecast.filter { it.precipitationProbabilityMax >= 30 }
    return if (isArabic) {
      if (rainDays.isNotEmpty()) {
        val details = rainDays.joinToString("، ") { "${it.dayLabel} (${it.precipitationProbabilityMax}%)" }
        "أقرب احتمالات المطر هذا الأسبوع: $details."
      } else {
        "لا توجد احتمالات مرتفعة لهطول الأمطار خلال الأيام السبعة القادمة حسب التوقعات الحالية."
      }
    } else {
      if (rainDays.isNotEmpty()) {
        "Rain expected on: " + rainDays.joinToString(", ") { "${it.dayLabel} (${it.precipitationProbabilityMax}%)" }
      } else {
        "No significant rain expected this week."
      }
    }
  }
}
