package com.example.core.intelligence

import com.example.core.context.ContextEngine
import com.example.core.context.ContextSnapshot
import com.example.device.battery.BatteryProvider
import com.example.device.battery.BatterySnapshot
import com.example.device.calendar.CalendarProvider
import com.example.device.calendar.CalendarSummary
import com.example.device.calls.CallLogProvider
import com.example.device.calls.CallSummary
import com.example.device.media.ActiveMediaMetadata
import com.example.device.media.MediaPlaybackController
import com.example.device.network.NetworkProvider
import com.example.device.network.NetworkSnapshot
import com.example.device.notifications.NotificationRepository
import com.example.device.notifications.NotificationSummary
import com.example.device.weather.DeviceLocation
import com.example.device.weather.LocationProvider
import com.example.device.weather.LocationResult
import com.example.device.weather.WeatherProvider
import com.example.device.weather.WeatherSnapshot
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Builds SituationSnapshots selectively based on ContextRequirements.
 * Never performs blanket queries unless strictly needed by the resolved domain.
 */
class SituationSnapshotBuilder(
  private val batteryProvider: BatteryProvider,
  private val networkProvider: NetworkProvider,
  private val contextEngine: ContextEngine,
  private val locationProvider: LocationProvider,
  private val weatherProvider: WeatherProvider,
  private val calendarProvider: CalendarProvider,
  private val mediaController: MediaPlaybackController,
  private val callLogProvider: CallLogProvider
) {

  suspend fun captureSnapshot(requirements: ContextRequirements): SituationSnapshot = coroutineScope {
    val needed = requirements.allNeeded

    // 1. Battery
    val batteryDeferred = async {
      if (needed.contains(ContextField.BATTERY)) {
        val snap = batteryProvider.batteryState.value
        if (snap != null) {
          ContextDatum.fact(snap, "BatteryProvider")
        } else {
          ContextDatum.unavailable<BatterySnapshot>("BatteryProvider", "بيانات البطارية غير متوفرة")
        }
      } else {
        ContextDatum.unavailable<BatterySnapshot>("BatteryProvider", "Not requested for domain")
      }
    }

    // 2. Network
    val networkDeferred = async {
      if (needed.contains(ContextField.NETWORK)) {
        val snap = networkProvider.networkState.value
        ContextDatum.fact(snap, "NetworkProvider")
      } else {
        ContextDatum.unavailable<NetworkSnapshot>("NetworkProvider", "Not requested for domain")
      }
    }

    // 3. Physical Context (accelerometer/proximity)
    val contextDeferred = async {
      if (needed.contains(ContextField.PHYSICAL_CONTEXT)) {
        val snap = contextEngine.contextState.value
        ContextDatum.fact(snap, "ContextEngine", confidence = snap.confidence)
      } else {
        ContextDatum.unavailable<ContextSnapshot>("ContextEngine", "Not requested for domain")
      }
    }

    // 4. Location
    val locationDeferred = async {
      if (needed.contains(ContextField.LOCATION)) {
        if (!locationProvider.hasLocationPermission()) {
          ContextDatum.unavailable<DeviceLocation>("LocationProvider", "صلاحية الموقع غير ممنوحة")
        } else {
          when (val res = locationProvider.getCurrentLocation()) {
            is LocationResult.Success -> ContextDatum.fact(res.location, "LocationProvider")
            is LocationResult.PermissionRequired -> ContextDatum.unavailable("LocationProvider", "Permission required")
            is LocationResult.LocationDisabledOrUnavailable -> ContextDatum.unavailable("LocationProvider", "خدمة الموقع معطلة")
          }
        }
      } else {
        ContextDatum.unavailable<DeviceLocation>("LocationProvider", "Not requested for domain")
      }
    }

    // 5. Weather
    val weatherDeferred = async {
      if (needed.contains(ContextField.WEATHER)) {
        try {
          val weatherSnap = weatherProvider.fetchWeather()
          if (weatherSnap.isAvailable) {
            val status = if (weatherSnap.isCached) EpistemicStatus.ESTIMATE else EpistemicStatus.FACT
            ContextDatum(
              value = weatherSnap,
              status = status,
              source = "OpenMeteoWeatherService",
              timestampMillis = weatherSnap.lastUpdatedMillis,
              confidence = if (weatherSnap.isCached) 0.85f else 1.0f
            )
          } else {
            ContextDatum.unavailable("WeatherProvider", weatherSnap.statusMessage ?: "بيانات الطقس غير متاحة")
          }
        } catch (e: Exception) {
          ContextDatum.unavailable<WeatherSnapshot>("WeatherProvider", "تعذر جلب الطقس: ${e.message}")
        }
      } else {
        ContextDatum.unavailable<WeatherSnapshot>("WeatherProvider", "Not requested for domain")
      }
    }

    // 6. Calendar
    val calendarDeferred = async {
      if (needed.contains(ContextField.CALENDAR)) {
        val calSummary = calendarProvider.queryUpcomingEvents()
        if (calSummary.isAvailable) {
          ContextDatum.fact(calSummary, "AndroidCalendarProvider")
        } else {
          ContextDatum.unavailable("AndroidCalendarProvider", calSummary.statusMessage ?: "التقويم غير متاح")
        }
      } else {
        ContextDatum.unavailable<CalendarSummary>("AndroidCalendarProvider", "Not requested for domain")
      }
    }

    // 7. Media
    val mediaDeferred = async {
      if (needed.contains(ContextField.MEDIA)) {
        mediaController.updateMediaState()
        val meta = mediaController.mediaState.value
        ContextDatum.fact(meta, "MediaPlaybackController")
      } else {
        ContextDatum.unavailable<ActiveMediaMetadata>("MediaPlaybackController", "Not requested for domain")
      }
    }

    // 8. Calls
    val callsDeferred = async {
      if (needed.contains(ContextField.CALLS)) {
        val callSum = callLogProvider.queryMissedCalls()
        if (callSum.hasPermission) {
          ContextDatum.fact(callSum, "CallLogProvider")
        } else {
          ContextDatum.unavailable("CallLogProvider", "READ_CALL_LOG permission not granted")
        }
      } else {
        ContextDatum.unavailable<CallSummary>("CallLogProvider", "Not requested for domain")
      }
    }

    // 9. Notifications
    val notificationsDeferred = async {
      if (needed.contains(ContextField.NOTIFICATIONS)) {
        val hasPermission = NotificationRepository.isServiceConnected.value
        val summary = NotificationRepository.getSummary(hasPermission)
        ContextDatum.fact(summary, "NotificationRepository")
      } else {
        ContextDatum.unavailable<NotificationSummary>("NotificationRepository", "Not requested for domain")
      }
    }

    SituationSnapshot(
      battery = batteryDeferred.await(),
      network = networkDeferred.await(),
      physicalContext = contextDeferred.await(),
      location = locationDeferred.await(),
      weather = weatherDeferred.await(),
      calendar = calendarDeferred.await(),
      media = mediaDeferred.await(),
      calls = callsDeferred.await(),
      notifications = notificationsDeferred.await()
    )
  }
}
