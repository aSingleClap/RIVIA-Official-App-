package com.example.util

import com.example.audio.EmotionPrediction
import com.example.data.MessageEntity
import com.example.data.VoiceEmotionLogEntity
import com.example.viewmodel.EmotionBaselineSummary
import com.example.viewmodel.RiviaViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import java.util.Locale
import kotlin.math.roundToLong
import org.json.JSONArray
import org.json.JSONObject

data class TurnAcousticData(
  val arousal: Float,
  val dominance: Float,
  val valence: Float,
  val speakingRateWps: Float,
  val speechDurationSec: Float,
  val isShortSpeech: Boolean,
) {
  companion object {
    fun fromLogOrNull(log: VoiceEmotionLogEntity?): TurnAcousticData? {
      if (log == null) return null
      val a = log.arousal ?: return null
      val d = log.dominance ?: return null
      val v = log.valence ?: return null
      return TurnAcousticData(
        arousal = a,
        dominance = d,
        valence = v,
        speakingRateWps = log.speakingRateWps ?: 0f,
        speechDurationSec = log.speechDurationSec,
        isShortSpeech = log.isShortSpeech,
      )
    }

    fun fromPrediction(
      prediction: EmotionPrediction?,
      transcript: String,
      speechDurationSec: Float,
    ): TurnAcousticData? {
      if (prediction == null) return null
      val words = RiviaViewModel.countWords(transcript)
      val rate = RiviaViewModel.computeSpeakingRateWps(words, speechDurationSec) ?: 0f
      val isShort = RiviaViewModel.isShortSpeechClip(speechDurationSec)
      return TurnAcousticData(
        arousal = prediction.arousal,
        dominance = prediction.dominance,
        valence = prediction.valence,
        speakingRateWps = rate,
        speechDurationSec = speechDurationSec,
        isShortSpeech = isShort,
      )
    }
  }
}

data class RecentTurnMessage(
  val role: String,
  val text: String,
)

data class TurnDispatchTimings(
  val vadEndMs: Long? = null,
  val transcriptReadyMs: Long? = null,
  val emotionReadyMs: Long? = null,
  val emotionTimeoutMs: Long? = null,
  val requestSentMs: Long? = null,
  val firstByteMs: Long? = null,
  val systemInstructionChars: Int? = null,
  val replyFirstTokenMs: Long? = null,
  val ttsFirstAudioMs: Long? = null,
  val playbackDoneMs: Long? = null,
) {
  fun toJsonObject(): JSONObject {
    return JSONObject().apply {
      put("vad_end", vadEndMs ?: JSONObject.NULL)
      put("transcript_ready", transcriptReadyMs ?: JSONObject.NULL)
      if (emotionTimeoutMs != null && emotionReadyMs == null) {
        put("emotion_timeout", emotionTimeoutMs)
      } else {
        put("emotion_ready", emotionReadyMs ?: JSONObject.NULL)
      }
      put("request_sent", requestSentMs ?: JSONObject.NULL)
      put("first_byte", firstByteMs ?: JSONObject.NULL)
      put("system_instruction_chars", systemInstructionChars ?: JSONObject.NULL)
      put("reply_first_token", replyFirstTokenMs ?: JSONObject.NULL)
      put("tts_first_audio", ttsFirstAudioMs ?: JSONObject.NULL)
      put("playback_done", playbackDoneMs ?: JSONObject.NULL)
    }
  }

  fun toJsonString(): String = toJsonObject().toString()
}

/**
 * Step 2 & Pre-Step-3 Fixes: Pure Kotlin payload builder (no network, unit-testable) for Rivia live voice turns.
 */
object TurnPayloadBuilder {
  const val MIN_BASELINE_N_FOR_DELTA = 20
  const val MAX_RECENT_TURNS = 6
  const val MAX_RECENT_TURN_CHARS = 300

  private val isoOffsetFormatter =
    DateTimeFormatterBuilder()
      .appendPattern("yyyy-MM-dd'T'HH:mm:ss")
      .appendFraction(ChronoField.MILLI_OF_SECOND, 3, 3, true)
      .appendOffset("+HH:MM", "+00:00")
      .toFormatter(Locale.US)

  fun formatIso8601WithOffset(
    timestampMs: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
  ): String {
    return Instant.ofEpochMilli(timestampMs).atZone(zoneId).format(isoOffsetFormatter)
  }

  private fun round4(value: Float): Double {
    return (value.toDouble() * 10000.0).roundToLong().toDouble() / 10000.0
  }

  /**
   * Builds the turn payload JSON object for a Rivia live voice turn:
   * - `acoustic` is null if emotion scores are unavailable (timeout/error/Mode A).
   * - `words_vs_scores` is `"no_scores"` when `acoustic` is null.
   * - `baseline` is from `queryAndComputeEmotionBaseline()` (excluding the current turn and calibrated rows),
   *   or `null` when `baseline == null` or `baseline.n == 0` (Fix 4).
   * - `delta` is `acoustic minus baseline` for `{arousal, dominance, valence, speaking_rate_wps}`,
   *   or `null` if `acoustic` is null or `baseline.n < 20`.
   * - `recent_turns` is the last 6 messages of this chat as `[{role, text}]`, with `[audio tags]` stripped
   *   (Fix 5) and each `text` capped at 300 chars.
   */
  fun buildPayloadJsonObject(
    timestampMs: Long,
    requestId: String,
    transcript: String,
    transcriptSource: String,
    acoustic: TurnAcousticData?,
    baseline: EmotionBaselineSummary?,
    recentTurns: List<RecentTurnMessage>,
    zoneId: ZoneId = ZoneId.systemDefault(),
    wordsVsScoresOverride: String? = null,
  ): JSONObject {
    val root = JSONObject()
    root.put("ts", formatIso8601WithOffset(timestampMs, zoneId))
    root.put("request_id", requestId)
    root.put("transcript", transcript)
    root.put("transcript_source", transcriptSource)

    if (acoustic != null) {
      root.put(
        "acoustic",
        JSONObject().apply {
          put("arousal", round4(acoustic.arousal))
          put("dominance", round4(acoustic.dominance))
          put("valence", round4(acoustic.valence))
          put("speaking_rate_wps", round4(acoustic.speakingRateWps))
          put("speech_duration_sec", round4(acoustic.speechDurationSec))
          put("is_short_speech", acoustic.isShortSpeech)
        },
      )
      if (!wordsVsScoresOverride.isNullOrBlank()) {
        root.put("words_vs_scores", wordsVsScoresOverride)
      }
    } else {
      root.put("acoustic", JSONObject.NULL)
      root.put("words_vs_scores", wordsVsScoresOverride ?: "no_scores")
    }

    // Fix 4: When baseline n = 0 (or baseline is null), the payload's baseline must be null, not zeros.
    if (baseline != null && baseline.eligibleCount > 0) {
      val baselineObj =
        JSONObject().apply {
          put("mean_arousal", baseline.meanArousal?.let { round4(it) } ?: JSONObject.NULL)
          put("mean_dominance", baseline.meanDominance?.let { round4(it) } ?: JSONObject.NULL)
          put("mean_valence", baseline.meanValence?.let { round4(it) } ?: JSONObject.NULL)
          put("mean_speaking_rate_wps", baseline.meanSpeakingRateWps?.let { round4(it) } ?: JSONObject.NULL)
          put("n", baseline.eligibleCount)
        }
      root.put("baseline", baselineObj)
    } else {
      root.put("baseline", JSONObject.NULL)
    }

    val canComputeDelta =
      acoustic != null &&
        baseline != null &&
        baseline.eligibleCount >= MIN_BASELINE_N_FOR_DELTA &&
        baseline.meanArousal != null &&
        baseline.meanDominance != null &&
        baseline.meanValence != null
    if (canComputeDelta && acoustic != null && baseline != null) {
      val bArousal = baseline.meanArousal ?: 0f
      val bDominance = baseline.meanDominance ?: 0f
      val bValence = baseline.meanValence ?: 0f
      val bSpeakingRate = baseline.meanSpeakingRateWps ?: 0f
      root.put(
        "delta",
        JSONObject().apply {
          put("arousal", round4(acoustic.arousal - bArousal))
          put("dominance", round4(acoustic.dominance - bDominance))
          put("valence", round4(acoustic.valence - bValence))
          put("speaking_rate_wps", round4(acoustic.speakingRateWps - bSpeakingRate))
        },
      )
    } else {
      root.put("delta", JSONObject.NULL)
    }

    // Fix 5: Strip [audio tags] from recent_turns text in the payload. Keep them in messages and TTS.
    val recentTurnsArr = JSONArray()
    for (msg in recentTurns.takeLast(MAX_RECENT_TURNS)) {
      val strippedText =
        TextAudioTagProcessor.stripAudioTagsForScreen(msg.text).take(MAX_RECENT_TURN_CHARS)
      recentTurnsArr.put(
        JSONObject().apply {
          put("role", msg.role)
          put("text", strippedText)
        }
      )
    }
    root.put("recent_turns", recentTurnsArr)

    return root
  }

  fun buildPayloadJson(
    timestampMs: Long,
    requestId: String,
    transcript: String,
    transcriptSource: String,
    acoustic: TurnAcousticData?,
    baseline: EmotionBaselineSummary?,
    recentMessages: List<MessageEntity>,
    zoneId: ZoneId = ZoneId.systemDefault(),
    wordsVsScoresOverride: String? = null,
  ): String {
    val mappedRecent =
      recentMessages.takeLast(MAX_RECENT_TURNS).map { m ->
        val stripped =
          TextAudioTagProcessor.stripAudioTagsForScreen(m.content).take(MAX_RECENT_TURN_CHARS)
        RecentTurnMessage(
          role = m.role,
          text = stripped,
        )
      }
    return buildPayloadJsonObject(
        timestampMs = timestampMs,
        requestId = requestId,
        transcript = transcript,
        transcriptSource = transcriptSource,
        acoustic = acoustic,
        baseline = baseline,
        recentTurns = mappedRecent,
        zoneId = zoneId,
        wordsVsScoresOverride = wordsVsScoresOverride,
      )
      .toString()
  }

  /**
   * Updates one or more epoch-ms fields (plus `system_instruction_chars`) in an existing `timings_json` string.
   */
  fun mergeTimingsJson(
    existingTimingsJson: String?,
    vadEndMs: Long? = null,
    transcriptReadyMs: Long? = null,
    emotionReadyMs: Long? = null,
    emotionTimeoutMs: Long? = null,
    requestSentMs: Long? = null,
    firstByteMs: Long? = null,
    systemInstructionChars: Int? = null,
    replyFirstTokenMs: Long? = null,
    ttsFirstAudioMs: Long? = null,
    playbackDoneMs: Long? = null,
  ): String {
    val obj =
      runCatching {
          if (existingTimingsJson.isNullOrBlank()) JSONObject() else JSONObject(existingTimingsJson)
        }
        .getOrElse { JSONObject() }

    if (!obj.has("vad_end")) obj.put("vad_end", JSONObject.NULL)
    if (!obj.has("transcript_ready")) obj.put("transcript_ready", JSONObject.NULL)
    if (!obj.has("emotion_ready") && !obj.has("emotion_timeout")) {
      obj.put("emotion_ready", JSONObject.NULL)
    }
    if (!obj.has("request_sent")) obj.put("request_sent", JSONObject.NULL)
    if (!obj.has("first_byte")) obj.put("first_byte", JSONObject.NULL)
    if (!obj.has("system_instruction_chars")) obj.put("system_instruction_chars", JSONObject.NULL)
    if (!obj.has("reply_first_token")) obj.put("reply_first_token", JSONObject.NULL)
    if (!obj.has("tts_first_audio")) obj.put("tts_first_audio", JSONObject.NULL)
    if (!obj.has("playback_done")) obj.put("playback_done", JSONObject.NULL)

    if (vadEndMs != null) obj.put("vad_end", vadEndMs)
    if (transcriptReadyMs != null) obj.put("transcript_ready", transcriptReadyMs)
    if (emotionReadyMs != null) {
      obj.remove("emotion_timeout")
      obj.put("emotion_ready", emotionReadyMs)
    }
    if (emotionTimeoutMs != null && emotionReadyMs == null) {
      obj.remove("emotion_ready")
      obj.put("emotion_timeout", emotionTimeoutMs)
    }
    if (requestSentMs != null) obj.put("request_sent", requestSentMs)
    if (firstByteMs != null && (obj.isNull("first_byte") || obj.optLong("first_byte", 0L) <= 0L)) {
      obj.put("first_byte", firstByteMs)
    }
    if (systemInstructionChars != null) {
      obj.put("system_instruction_chars", systemInstructionChars)
    }
    if (replyFirstTokenMs != null && (obj.isNull("reply_first_token") || obj.optLong("reply_first_token", 0L) <= 0L)) {
      obj.put("reply_first_token", replyFirstTokenMs)
    }
    if (ttsFirstAudioMs != null && (obj.isNull("tts_first_audio") || obj.optLong("tts_first_audio", 0L) <= 0L)) {
      obj.put("tts_first_audio", ttsFirstAudioMs)
    }
    if (playbackDoneMs != null) {
      obj.put("playback_done", playbackDoneMs)
    }
    return obj.toString()
  }
}
