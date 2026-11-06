package com.example.audio

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.os.SystemClock
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.FloatBuffer
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream
import kotlin.math.sqrt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

data class EmotionPrediction(
  val arousal: Float,
  val dominance: Float,
  val valence: Float,
  val inferenceMs: Long,
  val scoredWindowsCount: Int = 1,
)

sealed class EmotionModelState {
  data object NotDownloaded : EmotionModelState()

  data class Downloading(
    val progress: Float, // 0f..1f, or -1f if indeterminate
    val downloadedBytes: Long,
    val totalBytes: Long,
  ) : EmotionModelState()

  data object Extracting : EmotionModelState()

  data class Ready(
    val onnxFile: File,
    val yamlFile: File?,
    val onnxSizeBytes: Long,
    val yamlSummary: String,
    val yamlDeclaresInGraphNorm: Boolean,
  ) : EmotionModelState()

  data class Error(val message: String) : EmotionModelState()
}

/**
 * Pass 2: Manages downloading, extracting, and running audEERING's
 * wav2vec2-large-robust-12-ft-emotion-msp-dim ONNX export from Zenodo record 6221127.
 *
 * - Runs strictly on background threads (Dispatchers.IO / Dispatchers.Default); never blocks Live mode.
 * - Normalizes 16 kHz mono float32 [1, samples] (subtract mean, divide by std + 1e-7) unless
 *   the extracted .yaml explicitly declares the ONNX graph already normalizes raw waveform.
 * - Output order: arousal, dominance, valence.
 */
class EmotionModelManager(private val context: Context) {
  companion object {
    const val ZENODO_RECORD_ID = "6221127"
    const val DEFAULT_ARCHIVE_FILENAME = "w2v2-L-robust-12.6bc4a7fd-1.1.0.zip"
    val CANDIDATE_URLS =
      listOf(
        "https://zenodo.org/records/$ZENODO_RECORD_ID/files/$DEFAULT_ARCHIVE_FILENAME?download=1",
        "https://zenodo.org/record/$ZENODO_RECORD_ID/files/$DEFAULT_ARCHIVE_FILENAME?download=1",
        "https://zenodo.org/api/records/$ZENODO_RECORD_ID/files/$DEFAULT_ARCHIVE_FILENAME/content",
      )
    const val ZENODO_API_RECORD_URL = "https://zenodo.org/api/records/$ZENODO_RECORD_ID"

    /**
     * Normalizes a 16 kHz mono float32 clip by subtracting mean and dividing by (std + 1e-7)
     * unless skipNormalization is true.
     */
    fun normalizeClip(samples: FloatArray, skipNormalization: Boolean = false): FloatArray {
      if (samples.isEmpty() || skipNormalization) return samples.copyOf()
      var sum = 0.0
      for (s in samples) {
        sum += s.toDouble()
      }
      val mean = sum / samples.size.toDouble()

      var sumSqDiff = 0.0
      for (s in samples) {
        val diff = s.toDouble() - mean
        sumSqDiff += diff * diff
      }
      val variance = sumSqDiff / samples.size.toDouble()
      val std = sqrt(variance)
      val denom = (std + 1e-7).toFloat()
      val meanFloat = mean.toFloat()

      val out = FloatArray(samples.size)
      for (i in samples.indices) {
        out[i] = (samples[i] - meanFloat) / denom
      }
      return out
    }

    const val WINDOW_SECONDS = 4
    const val SAMPLE_RATE_HZ = 16000
    const val WINDOW_SAMPLES_4S = WINDOW_SECONDS * SAMPLE_RATE_HZ // 64,000 samples (4.0 s @ 16 kHz)
    const val MIN_REMAINDER_WINDOW_SAMPLES = 24000 // 1.5 s @ 16 kHz

    /**
     * Splits a 16 kHz mono clip into 4-second (64,000-sample) windows.
     * - If the clip is <= 4 s, returns a single window containing the clip.
     * - If the clip is > 4 s, returns all full 4 s windows plus any trailing remainder >= 1.5 s.
     */
    fun splitInto4sWindows(
      samples: FloatArray,
      windowSamples: Int = WINDOW_SAMPLES_4S,
      minRemainderSamples: Int = MIN_REMAINDER_WINDOW_SAMPLES,
    ): List<FloatArray> {
      if (samples.isEmpty()) return emptyList()
      if (samples.size <= windowSamples) return listOf(samples)

      val windows = mutableListOf<FloatArray>()
      var offset = 0
      while (offset + windowSamples <= samples.size) {
        windows.add(samples.copyOfRange(offset, offset + windowSamples))
        offset += windowSamples
      }
      val rem = samples.size - offset
      if (rem >= minRemainderSamples) {
        windows.add(samples.copyOfRange(offset, samples.size))
      }
      return windows
    }

    /**
     * Averages arousal, dominance, and valence across multiple 4-second window predictions.
     */
    fun averageWindowPredictions(predictions: List<EmotionPrediction>): EmotionPrediction {
      require(predictions.isNotEmpty()) { "predictions must not be empty" }
      if (predictions.size == 1) return predictions.first().copy(scoredWindowsCount = 1)
      var sumA = 0.0
      var sumD = 0.0
      var sumV = 0.0
      var totalMs = 0L
      for (p in predictions) {
        sumA += p.arousal.toDouble()
        sumD += p.dominance.toDouble()
        sumV += p.valence.toDouble()
        totalMs += p.inferenceMs
      }
      val count = predictions.size
      return EmotionPrediction(
        arousal = (sumA / count.toDouble()).toFloat(),
        dominance = (sumD / count.toDouble()).toFloat(),
        valence = (sumV / count.toDouble()).toFloat(),
        inferenceMs = totalMs,
        scoredWindowsCount = count,
      )
    }

    /**
     * Checks whether the extracted .yaml explicitly states that the ONNX graph itself already
     * normalizes raw waveform inputs.
     */
    fun doesYamlSayModelAlreadyNormalizes(yamlText: String): Boolean {
      if (yamlText.isBlank()) return false
      val lower = yamlText.lowercase()
      // Only skip host normalization if the YAML explicitly indicates the ONNX graph itself
      // already normalizes raw waveform (not audonnx host preprocessing).
      return lower.contains("onnx_normalizes: true") ||
        lower.contains("already_normalized: true") ||
        lower.contains("graph_normalizes: true")
    }
  }

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val modelDir = File(context.filesDir, "emotion_model")
  private val onnxTargetFile = File(modelDir, "model.onnx")
  private val yamlTargetFile = File(modelDir, "model.yaml")

  private val httpClient =
    OkHttpClient.Builder()
      .connectTimeout(30, TimeUnit.SECONDS)
      .readTimeout(120, TimeUnit.SECONDS)
      .followRedirects(true)
      .followSslRedirects(true)
      .build()

  private val _modelState = MutableStateFlow<EmotionModelState>(EmotionModelState.NotDownloaded)
  val modelState: StateFlow<EmotionModelState> = _modelState.asStateFlow()

  private val downloadMutex = Mutex()
  private val sessionMutex = Mutex()
  private var downloadJob: Job? = null
  private var cachedSession: OrtSession? = null

  init {
    refreshExistingModelState()
  }

  fun refreshExistingModelState() {
    try {
      if (onnxTargetFile.exists() && onnxTargetFile.length() > 1024L) {
        val yamlText =
          if (yamlTargetFile.exists()) {
            runCatching { yamlTargetFile.readText(Charsets.UTF_8) }.getOrDefault("")
          } else {
            ""
          }
        val inGraphNorm = doesYamlSayModelAlreadyNormalizes(yamlText)
        val summary = buildYamlSummary(yamlText, inGraphNorm)
        val readyState =
          EmotionModelState.Ready(
            onnxFile = onnxTargetFile,
            yamlFile = yamlTargetFile.takeIf { it.exists() },
            onnxSizeBytes = onnxTargetFile.length(),
            yamlSummary = summary,
            yamlDeclaresInGraphNorm = inGraphNorm,
          )
        _modelState.value = readyState
        preloadSessionAsync(readyState.onnxFile)
      } else {
        _modelState.value = EmotionModelState.NotDownloaded
      }
    } catch (e: Throwable) {
      _modelState.value =
        EmotionModelState.Error("Failed to inspect emotion model files: ${e.message}")
    }
  }

  /**
   * Keeps the ONNX Runtime session loaded in memory across turns so inference starts immediately.
   */
  fun preloadSessionAsync(onnxFile: File = onnxTargetFile) {
    if (!onnxFile.exists() || onnxFile.length() <= 1024L) return
    scope.launch(Dispatchers.Default) {
      runCatching {
        sessionMutex.withLock {
          if (cachedSession == null) {
            val env = OrtEnvironment.getEnvironment()
            cachedSession = env.createSession(onnxFile.absolutePath, OrtSession.SessionOptions())
          }
        }
      }
    }
  }

  /**
   * Triggers background download of the Zenodo 6221127 model if not already downloaded or downloading.
   */
  fun ensureDownloadedAsync() {
    val current = _modelState.value
    if (current is EmotionModelState.Ready) {
      preloadSessionAsync(current.onnxFile)
      return
    }
    if (current is EmotionModelState.Downloading || current is EmotionModelState.Extracting) {
      return
    }
    startDownload(forceRedownload = false)
  }

  fun startDownload(forceRedownload: Boolean = false) {
    if (downloadJob?.isActive == true) return
    downloadJob =
      scope.launch {
        downloadAndExtractIfNeeded(forceRedownload)
      }
  }

  suspend fun downloadAndExtractIfNeeded(forceRedownload: Boolean = false): Result<EmotionModelState.Ready> =
    withContext(Dispatchers.IO) {
      downloadMutex.withLock {
        try {
          if (!forceRedownload) {
            val cur = _modelState.value
            if (cur is EmotionModelState.Ready && onnxTargetFile.exists() && onnxTargetFile.length() > 1024L) {
              preloadSessionAsync(cur.onnxFile)
              return@withLock Result.success(cur)
            }
          }

          if (!modelDir.exists()) {
            modelDir.mkdirs()
          }

          // Close any prior ONNX session before replacing files
          sessionMutex.withLock {
            runCatching { cachedSession?.close() }
            cachedSession = null
          }

          val urlsToTry = resolveDownloadUrls()
          var lastError: Throwable? = null
          val tempArchive = File(modelDir, "zenodo_6221127_download.tmp")

          var downloadedOk = false
          for (url in urlsToTry) {
            try {
              downloadFileWithProgress(url, tempArchive)
              if (tempArchive.exists() && tempArchive.length() > 1024L) {
                downloadedOk = true
                break
              }
            } catch (e: CancellationException) {
              throw e
            } catch (e: Throwable) {
              lastError = e
            }
          }

          if (!downloadedOk) {
            val msg =
              "Model download failed from Zenodo 6221127: ${lastError?.message ?: "unknown network error"}"
            _modelState.value = EmotionModelState.Error(msg)
            return@withLock Result.failure(Exception(msg))
          }

          _modelState.value = EmotionModelState.Extracting

          // Check magic bytes for ZIP ('P', 'K', 0x03, 0x04)
          val isZip = isZipArchive(tempArchive)
          if (isZip) {
            extractOnnxAndYamlFromZip(tempArchive, onnxTargetFile, yamlTargetFile)
            runCatching { tempArchive.delete() }
          } else {
            // Direct .onnx file fallback
            if (onnxTargetFile.exists()) onnxTargetFile.delete()
            tempArchive.renameTo(onnxTargetFile)
          }

          if (!onnxTargetFile.exists() || onnxTargetFile.length() <= 1024L) {
            val msg = "Extracted archive from Zenodo 6221127 did not contain a valid .onnx file."
            _modelState.value = EmotionModelState.Error(msg)
            return@withLock Result.failure(Exception(msg))
          }

          val yamlText =
            if (yamlTargetFile.exists()) {
              runCatching { yamlTargetFile.readText(Charsets.UTF_8) }.getOrDefault("")
            } else {
              ""
            }
          val inGraphNorm = doesYamlSayModelAlreadyNormalizes(yamlText)
          val readyState =
            EmotionModelState.Ready(
              onnxFile = onnxTargetFile,
              yamlFile = yamlTargetFile.takeIf { it.exists() },
              onnxSizeBytes = onnxTargetFile.length(),
              yamlSummary = buildYamlSummary(yamlText, inGraphNorm),
              yamlDeclaresInGraphNorm = inGraphNorm,
            )
          _modelState.value = readyState
          preloadSessionAsync(readyState.onnxFile)
          Result.success(readyState)
        } catch (e: CancellationException) {
          throw e
        } catch (e: Throwable) {
          val msg = "Emotion model setup error: ${e.message ?: e.javaClass.simpleName}"
          _modelState.value = EmotionModelState.Error(msg)
          Result.failure(e)
        }
      }
    }

  /**
   * Runs ONNX inference on 16 kHz mono float32 audio samples on a background thread.
   * Output order is strictly: arousal, dominance, valence.
   */
  suspend fun runInference(rawSamples16kMono: FloatArray): Result<EmotionPrediction> =
    withContext(Dispatchers.Default) {
      try {
        if (rawSamples16kMono.isEmpty()) {
          return@withContext Result.failure(IllegalArgumentException("Audio clip is empty (0 samples)"))
        }

        // Ensure model is downloaded (triggers first-use download if needed)
        val readyState =
          when (val cur = _modelState.value) {
            is EmotionModelState.Ready -> cur
            else -> {
              val dlResult = downloadAndExtractIfNeeded(forceRedownload = false)
              dlResult.getOrElse { err ->
                return@withContext Result.failure(err)
              }
            }
          }

        val normalized =
          normalizeClip(
            samples = rawSamples16kMono,
            skipNormalization = readyState.yamlDeclaresInGraphNorm,
          )

        sessionMutex.withLock {
          val env = OrtEnvironment.getEnvironment()
          val session =
            cachedSession
              ?: env.createSession(readyState.onnxFile.absolutePath, OrtSession.SessionOptions())
                .also { cachedSession = it }

          val inputName =
            session.inputNames.firstOrNull()
              ?: return@withLock Result.failure(IllegalStateException("ONNX model has no input nodes"))

          val shape = longArrayOf(1L, normalized.size.toLong())
          val floatBuffer = FloatBuffer.wrap(normalized)

          val startMs = SystemClock.elapsedRealtime()
          OnnxTensor.createTensor(env, floatBuffer, shape).use { inputTensor ->
            session.run(mapOf(inputName to inputTensor)).use { results ->
              val inferenceMs = (SystemClock.elapsedRealtime() - startMs).coerceAtLeast(1L)
              val adv = extractArousalDominanceValence(session, results)
                ?: return@withLock Result.failure(
                  IllegalStateException("Could not find 3-dim [arousal, dominance, valence] output in ONNX result")
                )

              Result.success(
                EmotionPrediction(
                  arousal = adv[0],
                  dominance = adv[1],
                  valence = adv[2],
                  inferenceMs = inferenceMs,
                  scoredWindowsCount = 1,
                )
              )
            }
          }
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Throwable) {
        Result.failure(e)
      }
    }

  /**
   * Requirement 5: Score long turns in 4 s windows as they arrive and average them.
   * Accepts any [preScoredWindows] already scored during live capture while the user was speaking,
   * scores any eligible trailing remainder window (>= 1.5 s) or falls back to splitting [rawSamples16kMono]
   * into 4 s windows, and returns the averaged [EmotionPrediction].
   */
  suspend fun scoreTurnWithWindows(
    rawSamples16kMono: FloatArray,
    preScoredWindows: List<EmotionPrediction> = emptyList(),
    remainderSamples: FloatArray = FloatArray(0),
  ): Result<EmotionPrediction> =
    withContext(Dispatchers.Default) {
      try {
        if (preScoredWindows.isNotEmpty()) {
          val allPredictions = preScoredWindows.toMutableList()
          if (remainderSamples.size >= MIN_REMAINDER_WINDOW_SAMPLES) {
            runInference(remainderSamples).onSuccess { remPred ->
              allPredictions.add(remPred)
            }
          }
          return@withContext Result.success(averageWindowPredictions(allPredictions))
        }

        val windows = splitInto4sWindows(rawSamples16kMono)
        if (windows.isEmpty()) {
          return@withContext Result.failure(IllegalArgumentException("Audio clip is empty (0 samples)"))
        }
        val predictions = mutableListOf<EmotionPrediction>()
        var lastError: Throwable? = null
        for (win in windows) {
          val res = runInference(win)
          res.fold(
            onSuccess = { predictions.add(it) },
            onFailure = { lastError = it },
          )
        }
        if (predictions.isEmpty()) {
          Result.failure(lastError ?: IllegalStateException("Failed to score 4 s emotion windows"))
        } else {
          Result.success(averageWindowPredictions(predictions))
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Throwable) {
        Result.failure(e)
      }
    }

  private fun extractArousalDominanceValence(
    session: OrtSession,
    results: OrtSession.Result,
  ): FloatArray? {
    // 1. Prefer output explicitly named "logits" or containing "emotion" / "output"
    val outputNames = session.outputNames.toList()
    val prioritizedNames =
      outputNames.sortedBy { name ->
        val lower = name.lowercase()
        when {
          lower == "logits" -> 0
          lower.contains("logit") || lower.contains("emotion") || lower.contains("adv") -> 1
          lower.contains("hidden") || lower.contains("emb") -> 3
          else -> 2
        }
      }

    for (name in prioritizedNames) {
      val optValue = results.get(name)
      if (!optValue.isPresent) continue
      val onnxVal = optValue.get()
      val value = onnxVal.value
      val extracted = extractThreeFloats(value)
      if (extracted != null) return extracted
    }
    return null
  }

  private fun extractThreeFloats(value: Any?): FloatArray? {
    return when (value) {
      is FloatArray -> if (value.size == 3) value else null
      is Array<*> -> {
        val first = value.firstOrNull()
        when (first) {
          is FloatArray -> if (first.size == 3) first else null
          is Array<*> -> extractThreeFloats(first)
          else -> null
        }
      }
      else -> null
    }
  }

  private fun resolveDownloadUrls(): List<String> {
    val dynamicUrls = mutableListOf<String>()
    runCatching {
      val req =
        Request.Builder()
          .url(ZENODO_API_RECORD_URL)
          .header("Accept", "application/json")
          .build()
      httpClient.newCall(req).execute().use { resp ->
        if (resp.isSuccessful) {
          val bodyStr = resp.body?.string().orEmpty()
          val root = JSONObject(bodyStr)
          val filesArr = root.optJSONArray("files")
          if (filesArr != null) {
            for (i in 0 until filesArr.length()) {
              val fObj = filesArr.optJSONObject(i) ?: continue
              val key = fObj.optString("key", "")
              val links = fObj.optJSONObject("links")
              val selfLink = links?.optString("self", "").orEmpty()
              val contentLink = links?.optString("content", "").orEmpty()
              if (key.endsWith(".zip", ignoreCase = true) || key.endsWith(".onnx", ignoreCase = true)) {
                if (contentLink.isNotBlank()) dynamicUrls.add(contentLink)
                if (selfLink.isNotBlank()) dynamicUrls.add(selfLink)
              }
            }
          }
        }
      }
    }
    return (CANDIDATE_URLS + dynamicUrls).distinct()
  }

  private fun downloadFileWithProgress(url: String, destFile: File) {
    val request = Request.Builder().url(url).build()
    httpClient.newCall(request).execute().use { response ->
      if (!response.isSuccessful) {
        throw IllegalStateException("HTTP ${response.code} from $url")
      }
      val body = response.body ?: throw IllegalStateException("Empty response body from $url")
      val totalBytes = body.contentLength()
      _modelState.value =
        EmotionModelState.Downloading(
          progress = if (totalBytes > 0L) 0f else -1f,
          downloadedBytes = 0L,
          totalBytes = totalBytes,
        )

      body.byteStream().use { input ->
        FileOutputStream(destFile).use { output ->
          val buffer = ByteArray(64 * 1024)
          var downloaded = 0L
          var lastEmitMs = 0L
          while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            output.write(buffer, 0, read)
            downloaded += read
            val now = SystemClock.elapsedRealtime()
            if (now - lastEmitMs >= 120L) {
              val prog =
                if (totalBytes > 0L) {
                  (downloaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                } else {
                  -1f
                }
              _modelState.value =
                EmotionModelState.Downloading(
                  progress = prog,
                  downloadedBytes = downloaded,
                  totalBytes = totalBytes,
                )
              lastEmitMs = now
            }
          }
          output.flush()
          _modelState.value =
            EmotionModelState.Downloading(
              progress = 1f,
              downloadedBytes = downloaded,
              totalBytes = if (totalBytes > 0L) totalBytes else downloaded,
            )
        }
      }
    }
  }

  private fun isZipArchive(file: File): Boolean {
    if (!file.exists() || file.length() < 4L) return false
    return runCatching {
      FileInputStream(file).use { fis ->
        val header = ByteArray(4)
        val r = fis.read(header)
        r == 4 &&
          header[0] == 'P'.code.toByte() &&
          header[1] == 'K'.code.toByte() &&
          header[2] == 3.toByte() &&
          header[3] == 4.toByte()
      }
    }.getOrDefault(false)
  }

  private fun extractOnnxAndYamlFromZip(
    zipFile: File,
    onnxDest: File,
    yamlDest: File,
  ) {
    ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
      val buffer = ByteArray(64 * 1024)
      while (true) {
        val entry = zis.nextEntry ?: break
        if (!entry.isDirectory) {
          val entryName = File(entry.name).name.lowercase()
          val target =
            when {
              entryName.endsWith(".onnx") -> onnxDest
              entryName.endsWith(".yaml") || entryName.endsWith(".yml") -> yamlDest
              else -> null
            }
          if (target != null) {
            FileOutputStream(target).use { fos ->
              while (true) {
                val len = zis.read(buffer)
                if (len <= 0) break
                fos.write(buffer, 0, len)
              }
              fos.flush()
            }
          }
        }
        zis.closeEntry()
      }
    }
  }

  private fun buildYamlSummary(yamlText: String, inGraphNorm: Boolean): String {
    val normNote =
      if (inGraphNorm) "YAML: in-graph norm"
      else "Host norm: (x - mean) / (std + 1e-7)"
    if (yamlText.isBlank()) return "No .yaml found · $normNote"
    val srLine =
      yamlText
        .lineSequence()
        .map { it.trim() }
        .firstOrNull { it.startsWith("sampling_rate") || it.startsWith("sample_rate") }
    return if (srLine != null) "$srLine · $normNote" else "model.yaml loaded · $normNote"
  }
}
