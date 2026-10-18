package com.example.core.context

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sqrt

/**
 * Manages sensor listeners and feeds filtered readings to ContextClassifier.
 * Implements hysteresis and minimum dwell-time stabilization to eliminate oscillation.
 * Uses low-frequency SENSOR_DELAY_NORMAL to minimize battery drain.
 */
class ContextEngine(private val context: Context) : SensorEventListener {
  private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
  private val accelerometer: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
  private val proximitySensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)

  private val _contextState = MutableStateFlow(
    ContextSnapshot(state = ContextState.STATIONARY, confidence = 1.0f)
  )
  val contextState: StateFlow<ContextSnapshot> = _contextState.asStateFlow()

  private var isListening = false
  private var isProximityNear = false

  // Low-pass filter smoothing constants
  private val filterAlpha = 0.75f
  private var smoothAx = 0f
  private var smoothAy = 9.8f
  private var smoothAz = 0f

  // Sliding window for acceleration magnitude variance
  private val windowSize = 8
  private val magnitudeBuffer = FloatArray(windowSize)
  private var bufferIndex = 0
  private var sampleCount = 0

  // Hysteresis & Dwell-Time Stabilization
  private var confirmedSnapshot = ContextSnapshot(state = ContextState.STATIONARY, confidence = 1.0f)
  private var candidateState: ContextState = ContextState.STATIONARY
  private var candidateFirstObservedMillis: Long = System.currentTimeMillis()
  private val minimumDwellTimeMillis = 800L // Candidate must persist 800ms before becoming confirmed

  fun start() {
    if (isListening || sensorManager == null) return
    isListening = true
    accelerometer?.let {
      sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
    }
    proximitySensor?.let {
      sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
    }
  }

  fun stop() {
    if (!isListening || sensorManager == null) return
    isListening = false
    sensorManager.unregisterListener(this)
  }

  override fun onSensorChanged(event: SensorEvent) {
    val now = System.currentTimeMillis()
    when (event.sensor.type) {
      Sensor.TYPE_PROXIMITY -> {
        val maxRange = event.sensor.maximumRange
        val currentDistance = event.values[0]
        isProximityNear = currentDistance < minOf(5.0f, maxRange)
        evaluateStabilizedContext(now)
      }
      Sensor.TYPE_ACCELEROMETER -> {
        val rawAx = event.values[0]
        val rawAy = event.values[1]
        val rawAz = event.values[2]

        // 1. Low-pass filter for gravity isolation
        smoothAx = filterAlpha * smoothAx + (1 - filterAlpha) * rawAx
        smoothAy = filterAlpha * smoothAy + (1 - filterAlpha) * rawAy
        smoothAz = filterAlpha * smoothAz + (1 - filterAlpha) * rawAz

        // 2. High-pass / dynamic motion magnitude for variance calculation
        val dynamicX = rawAx - smoothAx
        val dynamicY = rawAy - smoothAy
        val dynamicZ = rawAz - smoothAz
        val dynamicMag = sqrt((dynamicX * dynamicX + dynamicY * dynamicY + dynamicZ * dynamicZ).toDouble()).toFloat()

        magnitudeBuffer[bufferIndex] = dynamicMag
        bufferIndex = (bufferIndex + 1) % windowSize
        if (sampleCount < windowSize) sampleCount++

        evaluateStabilizedContext(now)
      }
    }
  }

  override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
    // No-op
  }

  private fun evaluateStabilizedContext(currentTime: Long) {
    // Calculate variance of dynamic motion
    val count = if (sampleCount == 0) 1 else sampleCount
    var sum = 0f
    for (i in 0 until count) {
      sum += magnitudeBuffer[i]
    }
    val mean = sum / count
    var varSum = 0f
    for (i in 0 until count) {
      val diff = magnitudeBuffer[i] - mean
      varSum += diff * diff
    }
    val variance = varSum / count

    // Candidate snapshot from pure classifier
    val candidateSnapshot = ContextClassifier.classify(
      ax = smoothAx,
      ay = smoothAy,
      az = smoothAz,
      isProximityNear = isProximityNear,
      motionVariance = variance
    )

    // Hysteresis & Stabilization pipeline
    if (candidateSnapshot.state != candidateState) {
      // New candidate observed: start dwell timer
      candidateState = candidateSnapshot.state
      candidateFirstObservedMillis = currentTime
    } else {
      // Candidate persists: check if dwell time threshold satisfied
      val elapsedDwell = currentTime - candidateFirstObservedMillis
      if (elapsedDwell >= minimumDwellTimeMillis && confirmedSnapshot.state != candidateState) {
        // Confirm new state transition!
        confirmedSnapshot = candidateSnapshot.copy(timestamp = currentTime)
        _contextState.value = confirmedSnapshot
      }
    }
  }
}
