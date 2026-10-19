package com.example.core.voice.engine

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.util.Log
import com.example.core.intent.SpeechResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.LongBuffer
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToInt

/**
 * Configuration metadata parsed from a Piper VITS `.onnx.json` or `config.json` file.
 */
data class NeuralTtsModelConfig(
  val sampleRate: Int = 22050,
  val noiseScale: Float = 0.667f,
  val lengthScale: Float = 1.0f,
  val noiseW: Float = 0.8f,
  val numSpeakers: Int = 1,
  val defaultSpeakerId: Long = 0L,
  val phonemeType: String = "espeak",
  val espeakVoice: String = "ar",
  val phonemeMap: Map<String, List<String>> = emptyMap(),
  val phonemeIdMap: Map<String, List<Long>> = emptyMap()
)

/**
 * Pure Offline Arabic Neural Text-to-Speech Engine powered by ONNX Runtime Mobile (Piper VITS architecture).
 *
 * Key responsibilities & guarantees:
 * 1. Runs 100% locally on Android ARM64/ARMv7/x86_64 without any network connection or cloud API after one-time model download.
 * 2. Reuses a single [OrtSession] safely on a background inference dispatcher to avoid reloading 63MB weights per utterance.
 * 3. Preprocesses Arabic text via [ArabicTextDiacritizer] (numbers, decimals, dates, clock times, percentages, units, tashkeel)
 *    and converts to IPA phoneme IDs matching the Piper `.onnx.json` `phoneme_id_map`.
 * 4. Splits long responses into bounded clauses (`splitIntoSynthesisChunks`) to prevent OutOfMemoryError on mid-range phones
 *    (Huawei P40 Lite E 4GB RAM & Samsung A24) and streams synthesized 16-bit PCM smoothly through a single [AudioTrack].
 * 5. Deduplicates identical rapid UI re-triggers while queuing or flushing intentional user speech requests cleanly.
 */
class LocalNeuralTtsEngine(
  private val context: Context,
  private val onSpeechStateChanged: ((Boolean) -> Unit)? = null
) {
  companion object {
    private const val TAG = "WEYKY_NEURAL_TTS"
    const val DEFAULT_SAMPLE_RATE = 22050
    private const val DEDUP_WINDOW_MS = 900L
  }

  private data class QueuedSpeechRequest(
    val requestId: Long,
    val rawText: String,
    val modelDir: File,
    val volume: Float
  )

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private val sessionLock = Any()

  private var ortEnv: OrtEnvironment? = null
  private var ortSession: OrtSession? = null
  private var loadedModelDir: File? = null
  private var loadedOnnxFile: File? = null
  private var activeConfig: NeuralTtsModelConfig? = null

  @Volatile
  var isSpeaking: Boolean = false
    private set

  @Volatile
  private var currentAudioTrack: AudioTrack? = null

  private val requestCounter = AtomicLong(0L)

  @Volatile
  private var activeRequestId: Long = 0L

  @Volatile
  private var lastSpokenNormalizedText: String = ""

  @Volatile
  private var lastSpokenTimestampMs: Long = 0L

  private var activePlaybackJob: Job? = null
  private val speechQueue = ArrayDeque<QueuedSpeechRequest>()
  private val queueLock = Any()

  // Optional test/custom inference hook so unit tests on JVM (where native android libonnxruntime4j_jni.so may not be loaded)
  // can verify end-to-end neural pipeline, chunking, queuing, cancellation, and fallback deterministically.
  internal var customWaveformSynthesizer: ((phonemeIds: LongArray, config: NeuralTtsModelConfig) -> FloatArray)? = null

  val isReady: Boolean
    get() = synchronized(sessionLock) {
      loadedModelDir != null && activeConfig != null && (ortSession != null || customWaveformSynthesizer != null)
    }

  /**
   * Checks whether [modelDir] contains a valid `.onnx` file and `.onnx.json` config.
   */
  fun isModelDirectoryValid(modelDir: File): Boolean {
    if (!modelDir.exists() || !modelDir.isDirectory) return false
    val onnxFile = findOnnxFile(modelDir) ?: return false
    val configFile = findConfigFile(modelDir) ?: return false
    return onnxFile.length() > 128L && configFile.length() > 16L
  }

  private fun findOnnxFile(dir: File): File? {
    val files = dir.listFiles() ?: return null
    return files.firstOrNull { it.isFile && it.name.endsWith(".onnx", ignoreCase = true) && it.length() > 128L }
  }

  private fun findConfigFile(dir: File): File? {
    val files = dir.listFiles() ?: return null
    return files.firstOrNull {
      it.isFile && it.length() > 16L && (
        it.name.endsWith(".onnx.json", ignoreCase = true) ||
          it.name.equals("config.json", ignoreCase = true) ||
          it.name.equals("vocab.json", ignoreCase = true)
        )
    }
  }

  /**
   * Loads and initializes the ONNX Runtime session and Piper phoneme configuration from [modelDir].
   * Safe to call multiple times: if the exact model directory is already loaded, reuses the active session.
   */
  fun loadModel(modelDir: File): Boolean {
    synchronized(sessionLock) {
      ArabicTextDiacritizer.ensureLexiconLoaded(context)
      if (!isModelDirectoryValid(modelDir)) {
        Log.w(TAG, "[TTS-DIAG] Neural TTS model directory invalid or incomplete: ${modelDir.absolutePath}")
        return false
      }

      if (loadedModelDir?.absolutePath == modelDir.absolutePath &&
        activeConfig != null &&
        (ortSession != null || customWaveformSynthesizer != null)
      ) {
        return true
      }

      val onnxFile = findOnnxFile(modelDir) ?: return false
      val configFile = findConfigFile(modelDir) ?: return false

      val t0 = System.currentTimeMillis()
      return try {
        val parsedConfig = parseModelConfig(configFile)
        if (parsedConfig.phonemeIdMap.isEmpty()) {
          Log.w(TAG, "[TTS-DIAG] Neural TTS config phoneme_id_map is empty in ${configFile.name}")
          return false
        }

        closeOrtSessionLocked()

        if (customWaveformSynthesizer == null) {
          val env = ortEnv ?: OrtEnvironment.getEnvironment().also { ortEnv = it }
          val opts = OrtSession.SessionOptions().apply {
            // Limit intra-op threads to 2 so mid-range CPUs (Kirin 710F / Helio G99) keep UI & 60fps core smooth
            setIntraOpNumThreads(2)
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
          }
          ortSession = env.createSession(onnxFile.absolutePath, opts)
        }

        loadedModelDir = modelDir
        loadedOnnxFile = onnxFile
        activeConfig = parsedConfig
        val elapsed = System.currentTimeMillis() - t0
        Log.i(
          TAG,
          "[TTS-DIAG] Loaded Arabic Neural TTS model (${onnxFile.name}, sampleRate=${parsedConfig.sampleRate}Hz, " +
            "phonemes=${parsedConfig.phonemeIdMap.size}, elapsed=${elapsed}ms)"
        )
        true
      } catch (oom: OutOfMemoryError) {
        Log.e(TAG, "[TTS-DIAG] OutOfMemoryError loading Neural TTS ONNX model", oom)
        closeOrtSessionLocked()
        false
      } catch (ule: UnsatisfiedLinkError) {
        Log.w(TAG, "[TTS-DIAG] ONNX Runtime native JNI unavailable in current environment: ${ule.message}")
        closeOrtSessionLocked()
        false
      } catch (t: Throwable) {
        Log.e(TAG, "[TTS-DIAG] Failed to load Neural TTS ONNX model: ${t.message}", t)
        closeOrtSessionLocked()
        false
      }
    }
  }

  /**
   * Parses a Piper VITS `.onnx.json` configuration file into [NeuralTtsModelConfig].
   */
  internal fun parseModelConfig(configFile: File): NeuralTtsModelConfig {
    val jsonText = configFile.readText(Charsets.UTF_8)
    val root = JSONObject(jsonText)

    val audioObj = root.optJSONObject("audio")
    val sampleRate = audioObj?.optInt("sample_rate", DEFAULT_SAMPLE_RATE)
      ?: root.optInt("sample_rate", DEFAULT_SAMPLE_RATE)

    val inferenceObj = root.optJSONObject("inference")
    val noiseScale = inferenceObj?.optDouble("noise_scale", 0.667)?.toFloat() ?: 0.667f
    val lengthScale = inferenceObj?.optDouble("length_scale", 1.0)?.toFloat() ?: 1.0f
    val noiseW = inferenceObj?.optDouble("noise_w", 0.8)?.toFloat() ?: 0.8f

    val numSpeakers = root.optInt("num_speakers", 1).coerceAtLeast(1)
    val phonemeType = root.optString("phoneme_type", "espeak")
    val espeakObj = root.optJSONObject("espeak")
    val espeakVoice = espeakObj?.optString("voice", "ar") ?: "ar"

    val phMap = mutableMapOf<String, List<String>>()
    val phMapObj = root.optJSONObject("phoneme_map")
    if (phMapObj != null) {
      val keys = phMapObj.keys()
      while (keys.hasNext()) {
        val key = keys.next()
        val rawVal = phMapObj.opt(key)
        when (rawVal) {
          is JSONArray -> {
            val list = mutableListOf<String>()
            for (i in 0 until rawVal.length()) {
              val s = rawVal.optString(i)
              if (s.isNotEmpty()) list.add(s)
            }
            if (list.isNotEmpty()) phMap[key] = list
          }
          is String -> {
            if (rawVal.isNotEmpty()) phMap[key] = listOf(rawVal)
          }
        }
      }
    }

    val idMap = mutableMapOf<String, List<Long>>()
    val mapObj = root.optJSONObject("phoneme_id_map") ?: root.optJSONObject("vocab")
    if (mapObj != null) {
      val keys = mapObj.keys()
      while (keys.hasNext()) {
        val key = keys.next()
        val rawVal = mapObj.opt(key)
        when (rawVal) {
          is JSONArray -> {
            val list = mutableListOf<Long>()
            for (i in 0 until rawVal.length()) {
              list.add(rawVal.optLong(i))
            }
            if (list.isNotEmpty()) {
              idMap[key] = list
            }
          }
          is Number -> {
            idMap[key] = listOf(rawVal.toLong())
          }
        }
      }
    }

    return NeuralTtsModelConfig(
      sampleRate = sampleRate,
      noiseScale = noiseScale,
      lengthScale = lengthScale,
      noiseW = noiseW,
      numSpeakers = numSpeakers,
      defaultSpeakerId = 0L,
      phonemeType = phonemeType,
      espeakVoice = espeakVoice,
      phonemeMap = phMap,
      phonemeIdMap = idMap
    )
  }

  /**
   * Converts an Arabic clause into a Piper VITS phoneme ID sequence (`^`, `_`, phoneme IDs, `$`).
   */
  internal fun textToPhonemeIds(clause: String, config: NeuralTtsModelConfig): LongArray {
    val map = config.phonemeIdMap
    if (map.isEmpty()) return LongArray(0)

    val padIds = map["_"] ?: listOf(0L)
    val bosIds = map["^"] ?: emptyList()
    val eosIds = map["$"] ?: emptyList()

    val rawPhonemes = if (config.phonemeType.equals("text", ignoreCase = true)) {
      val vocalized = ArabicTextDiacritizer.prepareAndDiacritize(clause)
      vocalized.map { it.toString() }
    } else {
      ArabicTextDiacritizer.arabicToIpaPhonemes(clause)
    }

    val phonemes = if (config.phonemeMap.isEmpty()) {
      rawPhonemes
    } else {
      val substituted = ArrayList<String>(rawPhonemes.size)
      for (sym in rawPhonemes) {
        val mappedSyms = config.phonemeMap[sym]
        if (mappedSyms != null) {
          substituted.addAll(mappedSyms)
        } else {
          substituted.add(sym)
        }
      }
      substituted
    }

    val resultIds = ArrayList<Long>(phonemes.size * 2 + 4)
    if (bosIds.isNotEmpty()) {
      resultIds.addAll(bosIds)
      resultIds.addAll(padIds)
    }

    for (symbol in phonemes) {
      val mapped = map[symbol]
      if (mapped != null) {
        resultIds.addAll(mapped)
        resultIds.addAll(padIds)
      } else if (symbol.length > 1) {
        // Decompose multi-char IPA cluster if stored as individual codepoints in phoneme_id_map
        for (ch in symbol) {
          val subMapped = map[ch.toString()]
          if (subMapped != null) {
            resultIds.addAll(subMapped)
            resultIds.addAll(padIds)
          } else {
            Log.w(TAG, "[TTS-DIAG] Unmapped phoneme sub-symbol '$ch' (U+${ch.code.toString(16)}) in '$symbol'")
          }
        }
      } else {
        val codeHex = if (symbol.isNotEmpty()) symbol[0].code.toString(16) else "empty"
        Log.w(TAG, "[TTS-DIAG] Unmapped phoneme symbol '$symbol' (U+$codeHex) dropped")
      }
    }

    if (eosIds.isNotEmpty()) {
      resultIds.addAll(eosIds)
    }

    return resultIds.toLongArray()
  }

  /**
   * Enqueues or flushes a neural Arabic speech request using the installed model in [modelDir].
   */
  fun speak(
    text: String,
    modelDir: File,
    flush: Boolean = true,
    volume: Float = 1.0f,
    onFallbackRequired: ((String) -> Unit)? = null
  ): SpeechResult {
    val clean = text.trim()
    if (clean.isBlank()) return SpeechResult.Skipped

    if (!isModelDirectoryValid(modelDir)) {
      return SpeechResult.Failed("Neural TTS model files are missing or invalid")
    }

    val now = System.currentTimeMillis()
    val normalizedKey = clean.replace(Regex("\\s+"), " ")
    if (normalizedKey == lastSpokenNormalizedText && (now - lastSpokenTimestampMs) < DEDUP_WINDOW_MS && isSpeaking) {
      Log.d(TAG, "[TTS-DIAG] Ignored duplicate rapid neural TTS request within ${DEDUP_WINDOW_MS}ms")
      return SpeechResult.Success
    }
    lastSpokenNormalizedText = normalizedKey
    lastSpokenTimestampMs = now

    val reqId = requestCounter.incrementAndGet()
    val request = QueuedSpeechRequest(
      requestId = reqId,
      rawText = clean,
      modelDir = modelDir,
      volume = volume.coerceIn(0f, 1f)
    )

    synchronized(queueLock) {
      if (flush) {
        speechQueue.clear()
        activeRequestId = reqId
        stopCurrentTrackInternal()
        activePlaybackJob?.cancel()
      } else {
        speechQueue.addLast(request)
        if (activePlaybackJob?.isActive == true) {
          return SpeechResult.Success
        }
        activeRequestId = reqId
      }

      if (flush) {
        speechQueue.addLast(request)
      }

      activePlaybackJob = scope.launch {
        processSpeechQueue(onFallbackRequired)
      }
    }

    return SpeechResult.Success
  }

  private suspend fun processSpeechQueue(onFallbackRequired: ((String) -> Unit)?) {
    while (scope.isActive) {
      val nextReq = synchronized(queueLock) {
        if (speechQueue.isEmpty()) null else speechQueue.removeFirst()
      } ?: break

      activeRequestId = nextReq.requestId
      val success = synthesizeAndPlayRequest(nextReq)
      if (!success && nextReq.requestId == activeRequestId) {
        onFallbackRequired?.invoke(nextReq.rawText)
      }
    }
  }

  private suspend fun synthesizeAndPlayRequest(req: QueuedSpeechRequest): Boolean = withContext(Dispatchers.Default) {
    try {
      if (!loadModel(req.modelDir)) {
        return@withContext false
      }

      val config = synchronized(sessionLock) { activeConfig } ?: return@withContext false
      val vocalized = ArabicTextDiacritizer.prepareAndDiacritize(req.rawText)
      val clauses = ArabicTextDiacritizer.splitIntoSynthesisChunks(vocalized, maxCharsPerChunk = 110)
      if (clauses.isEmpty()) return@withContext false

      isSpeaking = true
      onSpeechStateChanged?.invoke(true)

      val sampleRate = config.sampleRate.coerceIn(8000, 48000)
      val minBufferBytes = AudioTrack.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT
      ).coerceAtLeast(sampleRate)

      val audioAttributes = AudioAttributes.Builder()
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .apply {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            setUsage(AudioAttributes.USAGE_ASSISTANT)
          } else {
            setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
          }
        }
        .build()

      val audioFormat = AudioFormat.Builder()
        .setSampleRate(sampleRate)
        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
        .build()

      val track = AudioTrack.Builder()
        .setAudioAttributes(audioAttributes)
        .setAudioFormat(audioFormat)
        .setBufferSizeInBytes(minBufferBytes)
        .setTransferMode(AudioTrack.MODE_STREAM)
        .build()

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        track.setVolume(req.volume)
      }

      currentAudioTrack = track
      track.play()

      var anyPcmWritten = false
      for ((idx, clause) in clauses.withIndex()) {
        if (!isActive || req.requestId != activeRequestId) break

        val tInferStart = System.currentTimeMillis()
        val phonemeIds = textToPhonemeIds(clause, config)
        if (phonemeIds.isEmpty()) continue

        val floatWave = runVitsOnnxInference(phonemeIds, config)
        val inferMs = System.currentTimeMillis() - tInferStart
        if (floatWave.isEmpty()) continue

        val pcmBytes = floatWaveformToPcm16(floatWave)
        val audioDurationMs = (floatWave.size * 1000L) / sampleRate
        Log.i(
          TAG,
          "[TTS-DIAG] Synthesized neural clause ${idx + 1}/${clauses.size} " +
            "(phonemes=${phonemeIds.size}, inferMs=${inferMs}ms, audioMs=${audioDurationMs}ms, sampleRate=${sampleRate}Hz)"
        )

        var offset = 0
        val chunkSize = 4096
        while (offset < pcmBytes.size && isActive && req.requestId == activeRequestId) {
          val toWrite = (pcmBytes.size - offset).coerceAtMost(chunkSize)
          val written = track.write(pcmBytes, offset, toWrite)
          if (written <= 0) break
          offset += written
          anyPcmWritten = true
        }
      }

      anyPcmWritten
    } catch (ce: CancellationException) {
      throw ce
    } catch (oom: OutOfMemoryError) {
      Log.e(TAG, "[TTS-DIAG] OutOfMemoryError during neural TTS synthesis", oom)
      synchronized(sessionLock) { closeOrtSessionLocked() }
      false
    } catch (t: Throwable) {
      Log.e(TAG, "[TTS-DIAG] Error during neural TTS synthesis: ${t.message}", t)
      false
    } finally {
      stopCurrentTrackInternal()
      val hasMoreQueued = synchronized(queueLock) { speechQueue.isNotEmpty() }
      if (!hasMoreQueued) {
        isSpeaking = false
        onSpeechStateChanged?.invoke(false)
      }
    }
  }

  /**
   * Executes Piper VITS ONNX graph inference:
   * Inputs:
   * - `input`: int64[1, N] phoneme IDs
   * - `input_lengths`: int64[1] = N
   * - `scales`: float[3] = [noise_scale, length_scale, noise_w]
   * - `sid` (optional if multi-speaker): int64[1] = defaultSpeakerId
   * Output:
   * - `output`: float waveform in [-1.0, 1.0]
   */
  internal fun runVitsOnnxInference(phonemeIds: LongArray, config: NeuralTtsModelConfig): FloatArray {
    val customHook = customWaveformSynthesizer
    if (customHook != null) {
      return customHook.invoke(phonemeIds, config)
    }

    synchronized(sessionLock) {
      val env = ortEnv ?: return FloatArray(0)
      val session = ortSession ?: return FloatArray(0)

      var inputTensor: OnnxTensor? = null
      var lengthTensor: OnnxTensor? = null
      var scalesTensor: OnnxTensor? = null
      var sidTensor: OnnxTensor? = null
      var result: OrtSession.Result? = null

      try {
        val seqLen = phonemeIds.size.toLong()
        inputTensor = OnnxTensor.createTensor(
          env,
          LongBuffer.wrap(phonemeIds),
          longArrayOf(1L, seqLen)
        )
        lengthTensor = OnnxTensor.createTensor(
          env,
          LongBuffer.wrap(longArrayOf(seqLen)),
          longArrayOf(1L)
        )
        scalesTensor = OnnxTensor.createTensor(
          env,
          FloatBuffer.wrap(floatArrayOf(config.noiseScale, config.lengthScale, config.noiseW)),
          longArrayOf(3L)
        )

        val inputs = mutableMapOf<String, OnnxTensor>()
        val inputNames = session.inputNames
        when {
          inputNames.contains("input") -> inputs["input"] = inputTensor
          inputNames.isNotEmpty() -> inputs[inputNames.first()] = inputTensor
        }
        if (inputNames.contains("input_lengths")) {
          inputs["input_lengths"] = lengthTensor
        }
        if (inputNames.contains("scales")) {
          inputs["scales"] = scalesTensor
        }
        if (inputNames.contains("sid")) {
          sidTensor = OnnxTensor.createTensor(
            env,
            LongBuffer.wrap(longArrayOf(config.defaultSpeakerId)),
            longArrayOf(1L)
          )
          inputs["sid"] = sidTensor
        }

        result = session.run(inputs)
        if (result.size() == 0) return FloatArray(0)

        val outTensor = result.get(0) as? OnnxTensor ?: return FloatArray(0)
        val floatBuf = outTensor.floatBuffer ?: return FloatArray(0)
        val outArray = FloatArray(floatBuf.remaining())
        floatBuf.get(outArray)
        return outArray
      } finally {
        try { result?.close() } catch (_: Exception) {}
        try { sidTensor?.close() } catch (_: Exception) {}
        try { scalesTensor?.close() } catch (_: Exception) {}
        try { lengthTensor?.close() } catch (_: Exception) {}
        try { inputTensor?.close() } catch (_: Exception) {}
      }
    }
  }

  internal fun floatWaveformToPcm16(samples: FloatArray): ByteArray {
    if (samples.isEmpty()) return ByteArray(0)
    val buffer = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
    val fadeLen = 128.coerceAtMost(samples.size / 4)
    for (i in samples.indices) {
      val env = when {
        fadeLen > 0 && i < fadeLen -> i.toFloat() / fadeLen.toFloat()
        fadeLen > 0 && i > samples.size - fadeLen -> (samples.size - i).toFloat() / fadeLen.toFloat()
        else -> 1.0f
      }
      val clamped = (samples[i] * env).coerceIn(-1.0f, 1.0f)
      val pcmShort = (clamped * Short.MAX_VALUE).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
      buffer.putShort(pcmShort)
    }
    return buffer.array()
  }

  private fun stopCurrentTrackInternal() {
    val track = currentAudioTrack
    currentAudioTrack = null
    if (track != null) {
      try {
        if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
          track.pause()
          track.flush()
          track.stop()
        }
      } catch (_: Exception) {}
      try {
        track.release()
      } catch (_: Exception) {}
    }
  }

  /**
   * Immediately cancels any ongoing or queued neural TTS playback.
   */
  fun stop() {
    synchronized(queueLock) {
      speechQueue.clear()
      activeRequestId = requestCounter.incrementAndGet()
      activePlaybackJob?.cancel()
      activePlaybackJob = null
    }
    stopCurrentTrackInternal()
    if (isSpeaking) {
      isSpeaking = false
      onSpeechStateChanged?.invoke(false)
    }
  }

  private fun closeOrtSessionLocked() {
    try {
      ortSession?.close()
    } catch (_: Exception) {}
    ortSession = null
    loadedModelDir = null
    loadedOnnxFile = null
    activeConfig = null
  }

  fun shutdown() {
    stop()
    synchronized(sessionLock) {
      closeOrtSessionLocked()
    }
  }
}
