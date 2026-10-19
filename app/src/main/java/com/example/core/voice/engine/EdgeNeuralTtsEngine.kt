package com.example.core.voice.engine

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import com.example.core.intent.SpeechResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * High-definition Cloud Neural Speech Synthesis Engine for WEYKY (Microsoft Edge Neural TTS Protocol).
 *
 * Characteristics:
 * 1. 100% Free & Open: No subscription, no credit card, no proprietary API keys required.
 * 2. Studio Quality: High-bitrate 24kHz neural speech using state-of-the-art Arabic models (Hamed, Salma, Shakir, Fatima).
 * 3. Ultra-Low Latency: Streaming WebSocket synthesis with immediate audio buffering and file caching.
 * 4. Hybrid Safe-Fail: Checks network connectivity first; if offline or if synthesis times out (2500ms),
 *    immediately triggers seamless fallback to on-device neural (Piper VITS) or system TTS.
 */
class EdgeNeuralTtsEngine(
  private val context: Context,
  private val onSpeechStateChanged: ((Boolean) -> Unit)? = null
) {

  companion object {
    private const val TAG = "WEYKY_EDGE_TTS"
    private const val SYNTHESIS_TIMEOUT_MS = 3800L

    // High quality Arabic neural voices
    const val VOICE_SALMA_EGYPT = "ar-EG-SalmaNeural"    // Female - Warm, articulate Egyptian Classical
    const val VOICE_HAMED_SAUDI = "ar-SA-HamedNeural"    // Male - Prestigious Classical Arabic
    const val VOICE_SHAKIR_EGYPT = "ar-EG-ShakirNeural"  // Male - Energetic Classical Arabic
    const val VOICE_FATIMA_UAE = "ar-AE-FatimaNeural"    // Female - Expressive Gulf & Classical Arabic
    const val VOICE_HEDI_TUNISIA = "ar-TN-HediNeural"    // Male - Tunisian Classical Arabic
    const val VOICE_REEM_TUNISIA = "ar-TN-ReemNeural"    // Female - Tunisian Classical Arabic
    const val VOICE_ZARIYAH_SAUDI = "ar-SA-ZariyahNeural" // Female - Saudi Classical Arabic

    const val DEFAULT_VOICE = VOICE_SALMA_EGYPT
  }

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

  private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(3000, TimeUnit.MILLISECONDS)
    .readTimeout(7000, TimeUnit.MILLISECONDS)
    .writeTimeout(4000, TimeUnit.MILLISECONDS)
    .retryOnConnectionFailure(true)
    .build()

  private val cacheDir = File(context.cacheDir, "edge_tts_cache").apply { mkdirs() }

  @Volatile
  private var currentMediaPlayer: MediaPlayer? = null

  @Volatile
  private var activeSynthesisJob: Job? = null

  private val isPlayingAudio = AtomicBoolean(false)

  val isSpeaking: Boolean
    get() = isPlayingAudio.get() || (activeSynthesisJob?.isActive == true)

  var activeVoiceName: String = DEFAULT_VOICE
  var speechRatePercent: Int = 0   // e.g. 0% for normal, +10% faster
  var speechPitchHz: Int = 0       // e.g. 0Hz for standard

  /**
   * Fast verification of whether internet connectivity is currently available.
   */
  fun isOnline(): Boolean {
    val cm = connectivityManager ?: return false
    val network = cm.activeNetwork ?: return false
    val capabilities = cm.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
  }

  /**
   * Synthesizes and speaks text using Edge Cloud Neural TTS.
   * If offline or if synthesis encounters an error or timeout, seamlessly calls onFallbackRequired.
   */
  fun speak(
    text: String,
    flush: Boolean = true,
    volume: Float = 1.0f,
    voice: String = activeVoiceName,
    onFallbackRequired: (String) -> Unit
  ): SpeechResult {
    if (text.isBlank()) return SpeechResult.Skipped

    if (!isOnline()) {
      Log.i(TAG, "[HYBRID-TTS] Device is offline; routing directly to local fallback engine.")
      onFallbackRequired(text)
      return SpeechResult.Success
    }

    if (flush) {
      stop()
    }

    // Check disk cache first for instant playback (0ms network)
    val cacheFile = getCacheFileFor(text, voice)
    if (cacheFile.exists() && cacheFile.length() > 512) {
      Log.d(TAG, "[HYBRID-TTS] Playing audio from local cache: ${cacheFile.name}")
      playAudioFile(cacheFile, volume, text, onFallbackRequired)
      return SpeechResult.Success
    }

    activeSynthesisJob = scope.launch {
      try {
        val audioBytes = withTimeoutOrNull(SYNTHESIS_TIMEOUT_MS) {
          synthesizeAudioOverWebSocket(text, voice)
        }

        if (audioBytes == null || audioBytes.isEmpty()) {
          Log.w(TAG, "[HYBRID-TTS] Synthesis timed out (${SYNTHESIS_TIMEOUT_MS}ms) or returned empty. Invoking local fallback.")
          withContext(Dispatchers.Main) {
            onFallbackRequired(text)
          }
          return@launch
        }

        // Save to cache
        try {
          FileOutputStream(cacheFile).use { it.write(audioBytes) }
        } catch (e: Exception) {
          Log.w(TAG, "Failed to cache TTS audio file", e)
        }

        playAudioFile(cacheFile, volume, text, onFallbackRequired)
      } catch (ce: CancellationException) {
        Log.d(TAG, "[HYBRID-TTS] Synthesis job cancelled.")
      } catch (e: Exception) {
        Log.w(TAG, "[HYBRID-TTS] Cloud synthesis error: ${e.message}. Triggering fallback.", e)
        withContext(Dispatchers.Main) {
          onFallbackRequired(text)
        }
      }
    }

    return SpeechResult.Success
  }

  private fun playAudioFile(
    file: File,
    volume: Float,
    text: String,
    onFallbackRequired: (String) -> Unit
  ) {
    scope.launch(Dispatchers.Main) {
      try {
        stopMediaPlayerOnly()

        val mp = MediaPlayer()
        val speechAttrs = AudioAttributes.Builder()
          .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
          .apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
              setUsage(AudioAttributes.USAGE_ASSISTANT)
            } else {
              setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
            }
          }
          .build()

        mp.setAudioAttributes(speechAttrs)
        mp.setDataSource(file.absolutePath)
        val clampedVol = volume.coerceIn(0.0f, 1.0f)
        mp.setVolume(clampedVol, clampedVol)

        mp.setOnPreparedListener { player ->
          try {
            isPlayingAudio.set(true)
            onSpeechStateChanged?.invoke(true)
            player.start()
          } catch (e: Exception) {
            Log.e(TAG, "Error starting MediaPlayer", e)
            isPlayingAudio.set(false)
            onSpeechStateChanged?.invoke(false)
            onFallbackRequired(text)
          }
        }

        mp.setOnCompletionListener { player ->
          isPlayingAudio.set(false)
          onSpeechStateChanged?.invoke(false)
          try {
            player.release()
          } catch (_: Exception) {}
          if (currentMediaPlayer == player) {
            currentMediaPlayer = null
          }
        }

        mp.setOnErrorListener { player, what, extra ->
          Log.w(TAG, "MediaPlayer playback error: what=$what extra=$extra")
          isPlayingAudio.set(false)
          onSpeechStateChanged?.invoke(false)
          try {
            player.release()
          } catch (_: Exception) {}
          if (currentMediaPlayer == player) {
            currentMediaPlayer = null
          }
          onFallbackRequired(text)
          true
        }

        currentMediaPlayer = mp
        mp.prepareAsync()
      } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize audio playback: ${e.message}", e)
        isPlayingAudio.set(false)
        onSpeechStateChanged?.invoke(false)
        onFallbackRequired(text)
      }
    }
  }

  /**
   * Synthesizes audio using Edge TTS WebSocket endpoint and returns raw MP3 bytes.
   */
  private suspend fun synthesizeAudioOverWebSocket(text: String, voice: String): ByteArray? {
    val connectionId = UUID.randomUUID().toString().replace("-", "")
    val requestId = UUID.randomUUID().toString().replace("-", "")
    val gec = EdgeDrmTokenGenerator.generateSecMsGec()
    val muid = EdgeDrmTokenGenerator.generateMuid()
    val gecVersion = EdgeDrmTokenGenerator.SEC_MS_GEC_VERSION
    val clientToken = EdgeDrmTokenGenerator.TRUSTED_CLIENT_TOKEN

    val url = "wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1?" +
      "TrustedClientToken=$clientToken" +
      "&Sec-MS-GEC=$gec" +
      "&Sec-MS-GEC-Version=$gecVersion" +
      "&ConnectionId=$connectionId"

    val request = Request.Builder()
      .url(url)
      .header("Pragma", "no-cache")
      .header("Cache-Control", "no-cache")
      .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36 Edg/143.0.0.0")
      .header("Origin", "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold")
      .header("Accept-Encoding", "gzip, deflate, br, zstd")
      .header("Accept-Language", "en-US,en;q=0.9")
      .header("Cookie", "muid=$muid;")
      .build()

    val audioStream = ByteArrayOutputStream()
    val completionDeferred = CompletableDeferred<ByteArray?>()

    val rateStr = if (speechRatePercent >= 0) "+$speechRatePercent%" else "$speechRatePercent%"
    val pitchStr = if (speechPitchHz >= 0) "+${speechPitchHz}Hz" else "${speechPitchHz}Hz"

    val ssml = buildSsml(text = text, voice = voice, rate = rateStr, pitch = pitchStr)

    val listener = object : WebSocketListener() {
      override fun onOpen(webSocket: WebSocket, response: Response) {
        // Step 1: Send configuration frame
        val configPayload = "Content-Type:application/json; charset=utf-8\r\nPath:speech.config\r\n\r\n" +
          "{\"context\":{\"synthesis\":{\"audio\":{\"metadataoptions\":{\"sentenceBoundaryEnabled\":\"false\",\"wordBoundaryEnabled\":\"false\"},\"outputFormat\":\"audio-24khz-48kbitrate-mono-mp3\"}}}}\r\n"
        webSocket.send(configPayload)

        // Step 2: Send SSML speech request
        val timestamp = SimpleDateFormat("EEE MMM dd yyyy HH:mm:ss 'GMT+0000 (Coordinated Universal Time)'", Locale.US).apply {
          timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())

        val ssmlPayload = "X-RequestId:$requestId\r\nContent-Type:application/ssml+xml\r\nX-Timestamp:${timestamp}Z\r\nPath:ssml\r\n\r\n$ssml"
        webSocket.send(ssmlPayload)
      }

      override fun onMessage(webSocket: WebSocket, text: String) {
        if (text.contains("Path:turn.end")) {
          webSocket.close(1000, "Done")
          completionDeferred.complete(audioStream.toByteArray())
        }
      }

      override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
        val byteArray = bytes.toByteArray()
        if (byteArray.size < 2) return

        // First 2 bytes are a 16-bit big-endian unsigned integer indicating header length
        val headerLength = (byteArray[0].toInt() and 0xFF shl 8) or (byteArray[1].toInt() and 0xFF)
        val dataOffset = 2 + headerLength
        if (byteArray.size > dataOffset) {
          audioStream.write(byteArray, dataOffset, byteArray.size - dataOffset)
        }
      }

      override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        Log.w(TAG, "Edge TTS WebSocket failure: ${t.message} (code=${response?.code})")
        if (response != null) {
          val serverDate = response.header("Date")
          if (!serverDate.isNullOrBlank()) {
            try {
              val format = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US)
              val parsed = format.parse(serverDate)
              if (parsed != null) {
                val serverSec = parsed.time / 1000.0
                val clientSec = System.currentTimeMillis() / 1000.0
                EdgeDrmTokenGenerator.clockSkewSeconds = serverSec - clientSec
                Log.d(TAG, "Adjusted Edge TTS clock skew to: ${EdgeDrmTokenGenerator.clockSkewSeconds}s")
              }
            } catch (_: Exception) {}
          }
        }
        if (!completionDeferred.isCompleted) {
          completionDeferred.complete(null)
        }
      }

      override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        if (!completionDeferred.isCompleted) {
          completionDeferred.complete(audioStream.toByteArray())
        }
      }
    }

    val webSocket = okHttpClient.newWebSocket(request, listener)
    try {
      return completionDeferred.await()
    } finally {
      try {
        webSocket.cancel()
      } catch (_: Exception) {}
    }
  }

  private fun buildSsml(text: String, voice: String, rate: String, pitch: String): String {
    val escapedText = text
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;")
      .replace("'", "&apos;")

    return "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='en-US'>" +
      "<voice name='$voice'>" +
      "<prosody pitch='$pitch' rate='$rate' volume='+0%'>" +
      escapedText +
      "</prosody>" +
      "</voice>" +
      "</speak>"
  }

  private fun getCacheFileFor(text: String, voice: String): File {
    val key = "$voice|$speechRatePercent|$speechPitchHz|$text"
    val md5 = MessageDigest.getInstance("MD5").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
    return File(cacheDir, "edge_$md5.mp3")
  }

  private fun stopMediaPlayerOnly() {
    val mp = currentMediaPlayer
    currentMediaPlayer = null
    if (mp != null) {
      try {
        if (mp.isPlaying) {
          mp.stop()
        }
        mp.release()
      } catch (_: Exception) {}
    }
    isPlayingAudio.set(false)
  }

  fun stop() {
    activeSynthesisJob?.cancel()
    activeSynthesisJob = null
    stopMediaPlayerOnly()
    onSpeechStateChanged?.invoke(false)
  }

  fun shutdown() {
    stop()
    scope.cancel()
    try {
      okHttpClient.dispatcher.executorService.shutdown()
      okHttpClient.connectionPool.evictAll()
    } catch (_: Exception) {}
  }
}
