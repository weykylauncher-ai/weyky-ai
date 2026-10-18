package com.example.core.intelligence

import com.example.core.context.ContextSnapshot
import com.example.device.battery.BatterySnapshot
import com.example.device.calendar.CalendarSummary
import com.example.device.calls.CallSummary
import com.example.device.media.ActiveMediaMetadata
import com.example.device.network.NetworkSnapshot
import com.example.device.notifications.NotificationSummary
import com.example.device.weather.DeviceLocation
import com.example.device.weather.WeatherSnapshot

/**
 * High-fidelity, timestamped, source-attributed snapshot of current situation.
 */
data class SituationSnapshot(
  val timestampMillis: Long = System.currentTimeMillis(),
  val battery: ContextDatum<BatterySnapshot>,
  val network: ContextDatum<NetworkSnapshot>,
  val physicalContext: ContextDatum<ContextSnapshot>,
  val location: ContextDatum<DeviceLocation>,
  val weather: ContextDatum<WeatherSnapshot>,
  val calendar: ContextDatum<CalendarSummary>,
  val media: ContextDatum<ActiveMediaMetadata>,
  val calls: ContextDatum<CallSummary>,
  val notifications: ContextDatum<NotificationSummary>
)
