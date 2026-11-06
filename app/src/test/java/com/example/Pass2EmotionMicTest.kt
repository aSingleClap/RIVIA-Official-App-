package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.audio.EmotionModelManager
import com.example.audio.MicSharingRecorder
import com.example.audio.SileroVadConfig
import com.example.audio.SileroVadTurnTracker
import com.example.data.RiviaDatabase
import com.example.data.VoiceEmotionLogEntity
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sqrt
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class Pass2EmotionMicTest {

  @Test
  fun normalizeClip_subtractsMeanAndDividesByStd() {
    val raw = floatArrayOf(1.0f, 2.0f, 3.0f, 4.0f, 5.0f)
    val norm = EmotionModelManager.normalizeClip(raw, skipNormalization = false)
    assertEquals(raw.size, norm.size)

    val mean = norm.average()
    var variance = 0.0
    for (x in norm) {
      val d = x - mean
      variance += d * d
    }
    val std = sqrt(variance / norm.size)

    assertTrue("Normalized mean should be ~0, was $mean", abs(mean) < 1e-5)
    assertTrue("Normalized std should be ~1, was $std", abs(std - 1.0) < 1e-4)

    val unnorm = EmotionModelManager.normalizeClip(raw, skipNormalization = true)
    assertEquals(3.0f, unnorm[2], 1e-6f)
  }

  @Test
  fun computeClipMetrics_flagsSilentCaptureWhenZerosAndRecognizerHeardSpeech() {
    val silentSamples = FloatArray(16000) { 0f } // 1.0s of silence
    val metricsWhenHeard =
      MicSharingRecorder.computeClipMetrics(
        samples = silentSamples,
        sampleRateHz = 16000,
        recognizerHeardSpeech = true,
      )
    assertEquals(1.0f, metricsWhenHeard.clipLengthSec, 1e-4f)
    assertEquals(0.0f, metricsWhenHeard.rms, 1e-6f)
    assertEquals(0.0f, metricsWhenHeard.peak, 1e-6f)
    assertTrue("Should flag SILENT CAPTURE when RMS is 0 and recognizer heard speech", metricsWhenHeard.silentCapture)

    val nonSilentSamples = FloatArray(8000) { if (it % 2 == 0) 0.25f else -0.25f } // 0.5s
    val activeMetrics =
      MicSharingRecorder.computeClipMetrics(
        samples = nonSilentSamples,
        sampleRateHz = 16000,
        recognizerHeardSpeech = true,
      )
    assertEquals(0.5f, activeMetrics.clipLengthSec, 1e-4f)
    assertEquals(0.25f, activeMetrics.rms, 1e-4f)
    assertEquals(0.25f, activeMetrics.peak, 1e-4f)
    assertFalse("Should not flag SILENT CAPTURE on real audio", activeMetrics.silentCapture)
  }

  @Test
  fun sileroVadTurnTracker_startsAtProb05For250msAndEndsAfter700msBelow035() {
    val config =
      SileroVadConfig(
        startProbThreshold = 0.50f,
        startSpeechMs = 250,
        endProbThreshold = 0.35f,
        endSilenceMs = 700,
        sampleRateHz = 16000,
        chunkSamples = 512, // 32 ms per chunk
      )
    assertEquals(32, config.chunkDurationMs)

    val tracker = SileroVadTurnTracker(config)

    // Initial silence should not trigger speechStarted or turnEnded
    repeat(30) {
      val snap = tracker.onChunkProbability(0.10f)
      assertFalse(snap.speechStarted)
      assertFalse(snap.turnEnded)
    }

    // 7 chunks of prob 0.60 = 224 ms (< 250 ms) -> not started yet
    repeat(7) {
      val snap = tracker.onChunkProbability(0.60f)
      assertFalse(snap.speechStarted)
      assertFalse(snap.turnEnded)
    }

    // 8th chunk of prob 0.60 = 256 ms (>= 250 ms) -> speechStarted becomes true!
    val startSnap = tracker.onChunkProbability(0.60f)
    assertTrue(startSnap.speechStarted)
    assertFalse(startSnap.turnEnded)

    // 10 chunks of low silence (prob 0.20 = 320 ms < 700 ms), then user speaks again (prob 0.75)
    repeat(10) {
      val snap = tracker.onChunkProbability(0.20f)
      assertTrue(snap.speechStarted)
      assertFalse(snap.turnEnded)
    }
    val resumeSnap = tracker.onChunkProbability(0.75f)
    assertEquals(0, resumeSnap.consecutiveSilenceMs)
    assertFalse(resumeSnap.turnEnded)

    // Now 21 chunks of prob 0.20 = 672 ms (< 700 ms) -> not ended yet
    repeat(21) {
      val snap = tracker.onChunkProbability(0.20f)
      assertFalse(snap.turnEnded)
    }

    // 22nd chunk of prob 0.20 = 704 ms (>= 700 ms below 0.35) -> turnEnded becomes true!
    val endSnap = tracker.onChunkProbability(0.20f)
    assertTrue(endSnap.speechStarted)
    assertTrue(endSnap.turnEnded)
    assertEquals(0.75f, endSnap.maxProb, 1e-4f)
  }

  @Test
  fun wavEncodingAndClipPruning_keepsLast10WavClips() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val clipsDir = File(context.filesDir, "emotion_clips")
    clipsDir.deleteRecursively()

    val pcmBytes = ByteArray(3200) { (it % 127).toByte() }
    val wavBytes = MicSharingRecorder.encodePcm16MonoToWavBytes(pcmBytes, 16000)
    assertEquals(44 + pcmBytes.size, wavBytes.size)

    val headerStr = String(wavBytes, 0, 4, Charsets.US_ASCII)
    val waveStr = String(wavBytes, 8, 4, Charsets.US_ASCII)
    assertEquals("RIFF", headerStr)
    assertEquals("WAVE", waveStr)

    val sr = ByteBuffer.wrap(wavBytes, 24, 4).order(ByteOrder.LITTLE_ENDIAN).int
    assertEquals(16000, sr)

    for (i in 1..14) {
      val f =
        MicSharingRecorder.saveWavClipAndPrune(
          context = context,
          requestId = "r-test-$i",
          pcm16LeBytes = pcmBytes,
          timestampMs = 1000L + i * 100L,
        )
      assertNotNull(f)
      f?.setLastModified(1000L + i * 1000L)
    }

    val remaining =
      clipsDir.listFiles { file -> file.name.endsWith(".wav") }?.toList().orEmpty()
    assertEquals("Should prune and keep only the last 10 WAV clips", 10, remaining.size)
  }

  @Test
  fun displayQueriesReturn50And100_whileExportQueriesReturnAllRows() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db =
      Room.inMemoryDatabaseBuilder(context, RiviaDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    val dao = db.riviaDao()

    for (i in 1..65) {
      dao.logVoiceEmotionAndPrune(
        VoiceEmotionLogEntity(
          requestId = "r-turn-$i",
          mode = if (i % 2 == 0) "B" else "A",
          recognizerOk = true,
          recognizerErrorCode = null,
          recognizerPackage = "com.google.android.tts",
          clipLengthSec = 2.4f,
          rms = 0.052f,
          peak = 0.41f,
          silentCapture = false,
          arousal = 0.45f,
          dominance = 0.62f,
          valence = 0.58f,
          inferenceMs = 84L,
          captureStatus = "OK (used partial)",
          timestamp = 10_000L + i,
          audioRecordStarted = true,
          pipeBytesWritten = 76800L,
          partialResultsCount = 6,
          callbacksFired = "used partial",
          usedPartialFallback = true,
          vadSpeechDetected = true,
          vadEndedTurn = true,
          vadMaxProb = 0.94f,
          transcriptPreview = "Testing Mode B with Silero VAD",
        )
      )
    }

    val displayEmotionLogs = dao.observeVoiceEmotionLogs().first()
    val totalEmotionCount = dao.observeTotalVoiceEmotionLogsCount().first()
    val exportEmotionLogs = dao.getAllVoiceEmotionLogsForExport()
    assertEquals("Display query should cap at 50 rows", 50, displayEmotionLogs.size)
    assertEquals("Total count should be 65", 65, totalEmotionCount)
    assertEquals("Export query should return all 65 rows", 65, exportEmotionLogs.size)
    assertEquals("r-turn-65", exportEmotionLogs.first().requestId)
    assertEquals("r-turn-1", exportEmotionLogs.last().requestId)

    for (i in 1..115) {
      dao.logRequestAndPrune(
        com.example.data.RequestLogEntity(
          timestamp = 20_000L + i,
          requestId = "r-req-$i",
          model = "gemini-3.8-flash",
          status = "ok",
          serviceTier = "standard",
          inputTokens = 10,
          outputTokens = 20,
          thoughtTokens = 5,
          latencyMs = 300L,
        )
      )
    }

    val displayReqLogs = dao.observeRequestLogs().first()
    val totalReqCount = dao.observeTotalRequestLogsCount().first()
    val exportReqLogs = dao.getAllRequestLogsForExport()
    assertEquals("Display query should cap at 100 rows", 100, displayReqLogs.size)
    assertEquals("Total request count should be 115", 115, totalReqCount)
    assertEquals("Export query should return all 115 rows", 115, exportReqLogs.size)
    assertEquals("r-req-115", exportReqLogs.first().requestId)
    assertEquals("r-req-1", exportReqLogs.last().requestId)

    for (i in 1..25) {
      dao.insertRuleEvent(
        com.example.data.RuleEventEntity(
          timestamp = 30_000L + i,
          requestId = "r-rule-$i",
          chatId = "chat-1",
          ruleId = "H11",
          eventType = "voice",
          detail = "detail-$i",
        )
      )
    }
    val displayRuleEvents = dao.observeRecentRuleEvents().first()
    val exportRuleEvents = dao.getAllRuleEventsForExport()
    assertEquals("Display rule events query should cap at 20 rows", 20, displayRuleEvents.size)
    assertEquals("Export rule events query should return all 25 rows", 25, exportRuleEvents.size)

    val byReq = dao.getVoiceEmotionLogByRequestId("r-turn-1")
    assertNotNull(byReq)
    assertEquals(0.45f, byReq!!.arousal!!, 1e-4f)
    assertEquals(0.62f, byReq.dominance!!, 1e-4f)
    assertEquals(0.58f, byReq.valence!!, 1e-4f)

    db.close()
  }

  @Test
  fun emotionBaselineDaoQuery_enforces14DayWindow_500Cap_andExcludedCounts() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db =
      Room.inMemoryDatabaseBuilder(context, RiviaDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    val dao = db.riviaDao()

    val nowMs = 100_000_000_000L
    val dayMs = 24L * 60L * 60L * 1000L

    // 1. Eligible Mode B row outside the 14-day window (15 days ago) -> should not be in baseline or window counts
    dao.insertVoiceEmotionLog(
      VoiceEmotionLogEntity(
        requestId = "r-old-15d",
        mode = "B",
        recognizerOk = true,
        recognizerPackage = "pkg",
        clipLengthSec = 3.0f,
        rms = 0.05f,
        peak = 0.30f,
        silentCapture = false,
        arousal = 0.99f,
        dominance = 0.99f,
        valence = 0.99f,
        timestamp = nowMs - 15L * dayMs,
        speechDurationSec = 2.5f,
        isShortSpeech = false,
      )
    )

    // 2. Excluded rows inside the 14-day window: 1 Mode A, 1 Mode B short (<1.5s), 1 Mode B silent
    dao.insertVoiceEmotionLog(
      VoiceEmotionLogEntity(
        requestId = "r-mode-a",
        mode = "A",
        recognizerOk = true,
        recognizerPackage = "pkg",
        clipLengthSec = 3.0f,
        rms = 0.05f,
        peak = 0.30f,
        silentCapture = false,
        arousal = 0.90f,
        dominance = 0.90f,
        valence = 0.90f,
        timestamp = nowMs - 2L * dayMs,
        speechDurationSec = 2.5f,
        isShortSpeech = false,
      )
    )
    dao.insertVoiceEmotionLog(
      VoiceEmotionLogEntity(
        requestId = "r-short",
        mode = "B",
        recognizerOk = true,
        recognizerPackage = "pkg",
        clipLengthSec = 2.0f,
        rms = 0.05f,
        peak = 0.30f,
        silentCapture = false,
        arousal = 0.20f,
        dominance = 0.20f,
        valence = 0.20f,
        timestamp = nowMs - 2L * dayMs + 100L,
        speechDurationSec = 1.1f,
        isShortSpeech = true,
      )
    )
    dao.insertVoiceEmotionLog(
      VoiceEmotionLogEntity(
        requestId = "r-silent",
        mode = "B",
        recognizerOk = true,
        recognizerPackage = "pkg",
        clipLengthSec = 3.0f,
        rms = 0.0001f,
        peak = 0.0005f,
        silentCapture = true,
        arousal = 0.10f,
        dominance = 0.10f,
        valence = 0.10f,
        timestamp = nowMs - 2L * dayMs + 200L,
        speechDurationSec = 2.0f,
        isShortSpeech = false,
      )
    )

    // 3. 510 eligible Mode B rows inside the 14-day window:
    // oldest 10 have A/D/V = 0.10, newest 500 have A/D/V = 0.50 -> cap of 500 newest should yield mean 0.50
    for (i in 1..510) {
      val adv = if (i <= 10) 0.10f else 0.50f
      dao.insertVoiceEmotionLog(
        VoiceEmotionLogEntity(
          requestId = "r-elig-$i",
          mode = "B",
          recognizerOk = true,
          recognizerPackage = "pkg",
          clipLengthSec = 3.0f,
          rms = 0.05f,
          peak = 0.30f,
          silentCapture = false,
          arousal = adv,
          dominance = adv,
          valence = adv,
          timestamp = (nowMs - 10L * dayMs) + i * 1000L,
          speechDurationSec = 2.2f,
          speakingRateWps = 2.5f,
          isShortSpeech = false,
        )
      )
    }

    val summary =
      com.example.viewmodel.RiviaViewModel.queryAndComputeEmotionBaseline(
        dao = dao,
        resetAtMs = 0L,
        nowMs = nowMs,
      )

    assertEquals(14, summary.windowDays)
    assertEquals(nowMs - 14L * dayMs, summary.windowStart)
    assertEquals(nowMs, summary.windowEnd)
    assertEquals("Should cap at BASELINE_MAX_ROWS = 500", 500, summary.eligibleCount)
    assertEquals(1, summary.excludedModeACount)
    assertEquals(1, summary.excludedShortCount)
    assertEquals(1, summary.excludedSilentCount)
    assertEquals("Mean should reflect newest 500 rows (0.50)", 0.50f, summary.meanArousal!!, 1e-4f)
    assertEquals(0.50f, summary.meanDominance!!, 1e-4f)
    assertEquals(0.50f, summary.meanValence!!, 1e-4f)

    db.close()
  }

  @Test
  fun backupImport_sameBackupTwiceLeavesCountsUnchanged_andOlderBackupNeverDeletesNewerMessages() =
    runBlocking {
      val context = ApplicationProvider.getApplicationContext<Context>()
      val db =
        Room.inMemoryDatabaseBuilder(context, RiviaDatabase::class.java)
          .allowMainThreadQueries()
          .build()
      val dao = db.riviaDao()

      val backupJson =
        """
        {
          "version": 2,
          "exportedAt": 50000,
          "chats": [
            {
              "id": "chat-uuid-1",
              "title": "Original Backup Title",
              "createdAt": 1000,
              "updatedAt": 2000,
              "runSettingsJson": "{}",
              "isLabChat": false,
              "riviaProfile": "balanced",
              "messages": [
                {
                  "id": "msg-uuid-1",
                  "chatId": "chat-uuid-1",
                  "role": "user",
                  "content": "First user turn",
                  "requestId": "r-1001",
                  "createdAt": 1000
                },
                {
                  "id": "msg-uuid-2",
                  "chatId": "chat-uuid-1",
                  "role": "assistant",
                  "content": "First assistant reply",
                  "requestId": "r-1001",
                  "createdAt": 1001
                }
              ]
            }
          ],
          "layerDrafts": [
            {
              "layerKey": "persona",
              "jsonContent": "{\"version\":1,\"locked\":false,\"text\":\"Backup draft\"}",
              "updatedAt": 2000
            }
          ],
          "layerLocks": [
            {
              "layerKey": "persona",
              "pendingCanonicalSha256": "sha-backup"
            }
          ],
          "layerTestRuns": [
            {
              "id": "test-run-uuid-1",
              "testId": "T1",
              "profile": "balanced",
              "fingerprint": "fp1",
              "passed": true,
              "detailsJson": "{}",
              "timestamp": 2000
            }
          ],
          "ruleEvents": [
            {
              "chatId": "chat-uuid-1",
              "requestId": "r-1001",
              "ruleId": "H11",
              "eventType": "voice",
              "detail": "watch word",
              "timestamp": 2000
            },
            {
              "chatId": "chat-uuid-1",
              "requestId": null,
              "ruleId": "H7",
              "eventType": "text",
              "detail": "null requestId rule event",
              "timestamp": 2005
            }
          ],
          "requestLogs": [
            {
              "timestamp": 2000,
              "requestId": "r-1001",
              "model": "gemini-3.8-flash",
              "status": "ok",
              "serviceTier": "standard",
              "inputTokens": 12,
              "outputTokens": 24,
              "thoughtTokens": 4,
              "latencyMs": 220
            },
            {
              "timestamp": 2005,
              "requestId": null,
              "model": "gemini-3.8-flash-lite",
              "status": "ok",
              "serviceTier": "standard",
              "inputTokens": 5,
              "outputTokens": 10,
              "thoughtTokens": 0,
              "latencyMs": 110
            }
          ],
          "voiceEmotionLogs": [
            {
              "requestId": "r-1001",
              "timestamp": 2000,
              "mode": "B",
              "recognizerOk": true,
              "recognizerPackage": "pkg",
              "clipLengthSec": 2.5,
              "rms": 0.05,
              "peak": 0.30,
              "silentCapture": false,
              "arousal": 0.45,
              "dominance": 0.55,
              "valence": 0.65,
              "speechDurationSec": 2.0
            },
            {
              "requestId": null,
              "timestamp": 2005,
              "mode": "B",
              "recognizerOk": true,
              "recognizerPackage": "pkg",
              "clipLengthSec": 2.1,
              "rms": 0.04,
              "peak": 0.25,
              "silentCapture": false,
              "arousal": 0.40,
              "dominance": 0.50,
              "valence": 0.60,
              "speechDurationSec": 1.8
            }
          ],
          "layerSettings": {
            "spokenMaxSentences": 3,
            "h11WatchWordsJson": "[\"test\"]"
          }
        }
        """.trimIndent()

      // First import into empty DB
      val payload = com.example.viewmodel.RiviaViewModel.parseBackupJsonToPayload(backupJson)
      dao.importBackupInTransaction(payload)

      assertEquals(1, dao.getAllChats().size)
      assertEquals(2, dao.getMessagesForChat("chat-uuid-1").size)
      assertEquals(1, dao.getLayerDrafts().size)
      assertEquals(1, dao.getLayerLocks().size)
      assertEquals(1, dao.getLayerTestRuns().size)
      assertEquals(2, dao.getAllRuleEventsForExport().size)
      assertEquals(2, dao.getAllRequestLogsForExport().size)
      assertEquals(2, dao.getAllVoiceEmotionLogsForExport().size)
      assertEquals(3, dao.getLayerSettings()?.spokenMaxSentences)

      // Add a newer local message ("msg-uuid-3") to chat-uuid-1 and update local chat title
      dao.updateChat(
        dao.getChatById("chat-uuid-1")!!.copy(title = "Newer Local Title", updatedAt = 9000L)
      )
      dao.insertMessage(
        com.example.data.MessageEntity(
          id = "msg-uuid-3",
          chatId = "chat-uuid-1",
          role = "user",
          content = "Newer local message after backup was taken",
          requestId = "r-2002",
          createdAt = 9000L,
        )
      )

      // Import the older backup a second time
      dao.importBackupInTransaction(payload)

      // Verify:
      // 1. Newer local message msg-uuid-3 survived (no ON DELETE CASCADE wipe!) and total messages = 3
      val msgsAfterSecondImport = dao.getMessagesForChat("chat-uuid-1")
      assertEquals(
        "Importing older backup must keep existing 2 messages + 1 newer local message = 3",
        3,
        msgsAfterSecondImport.size,
      )
      assertTrue(msgsAfterSecondImport.any { it.id == "msg-uuid-3" })
      assertEquals("Newer Local Title", dao.getChatById("chat-uuid-1")?.title)

      // 2. Every other table's row count is unchanged (no duplicate logs even for null requestId)
      assertEquals(1, dao.getAllChats().size)
      assertEquals(1, dao.getLayerDrafts().size)
      assertEquals(1, dao.getLayerLocks().size)
      assertEquals(1, dao.getLayerTestRuns().size)
      assertEquals(2, dao.getAllRuleEventsForExport().size)
      assertEquals(2, dao.getAllRequestLogsForExport().size)
      assertEquals(2, dao.getAllVoiceEmotionLogsForExport().size)

      db.close()
    }

  @Test
  fun sileroSpeechDuration_speakingRate_shortClipFlag_andBaselineFiltering() {
    val config =
      SileroVadConfig(
        startProbThreshold = 0.50f,
        startSpeechMs = 250,
        endProbThreshold = 0.35f,
        endSilenceMs = 700,
        sampleRateHz = 16000,
        chunkSamples = 512, // 32 ms per chunk
      )
    val tracker = SileroVadTurnTracker(config)

    // 10 chunks of leading silence (320 ms) -> 0 ms of speech
    repeat(10) { tracker.onChunkProbability(0.10f) }
    // 50 chunks of active speech (50 * 32 ms = 1600 ms = 1.60 s of speech)
    var lastSnap = tracker.onChunkProbability(0.80f)
    repeat(49) { lastSnap = tracker.onChunkProbability(0.80f) }
    // 22 chunks of trailing silence (< 0.35) to end the turn (704 ms) -> not counted in speech time
    repeat(22) { lastSnap = tracker.onChunkProbability(0.10f) }

    assertTrue(lastSnap.turnEnded)
    assertEquals(1600, lastSnap.totalSpeechMs)
    assertEquals(1.60f, lastSnap.speechDurationSec, 1e-3f)

    // Speaking rate = words / seconds of Silero speech (not total clip length)
    val transcript = "One two three four"
    val words = com.example.viewmodel.RiviaViewModel.countWords(transcript)
    assertEquals(4, words)
    val rateWps = com.example.viewmodel.RiviaViewModel.computeSpeakingRateWps(words, lastSnap.speechDurationSec)
    assertNotNull(rateWps)
    assertEquals(2.50f, rateWps!!, 1e-3f)

    // Short clip flag (< 1.5 s of speech)
    assertTrue(com.example.viewmodel.RiviaViewModel.isShortSpeechClip(1.49f))
    assertFalse(com.example.viewmodel.RiviaViewModel.isShortSpeechClip(1.50f))

    // Baseline computation: excludes Mode A, excludes short (< 1.5s) clips, respects resetAt
    val sampleLogs =
      listOf(
        // Eligible Mode B turn #1 (2.0s speech)
        VoiceEmotionLogEntity(
          requestId = "r-b1",
          mode = "B",
          recognizerOk = true,
          recognizerErrorCode = null,
          recognizerPackage = "com.google.android.tts",
          clipLengthSec = 3.2f,
          rms = 0.05f,
          peak = 0.30f,
          silentCapture = false,
          arousal = 0.40f,
          dominance = 0.50f,
          valence = 0.60f,
          inferenceMs = 90L,
          captureStatus = "OK (onResults)",
          timestamp = 2000L,
          audioSource = "VOICE_RECOGNITION",
          inputDevice = "BUILTIN_MIC (Pixel)",
          speechDurationSec = 2.0f,
          wordCount = 6,
          speakingRateWps = 3.0f,
          isShortSpeech = false,
        ),
        // Eligible Mode B turn #2 (3.0s speech)
        VoiceEmotionLogEntity(
          requestId = "r-b2",
          mode = "B",
          recognizerOk = true,
          recognizerErrorCode = null,
          recognizerPackage = "com.google.android.tts",
          clipLengthSec = 4.1f,
          rms = 0.06f,
          peak = 0.35f,
          silentCapture = false,
          arousal = 0.60f,
          dominance = 0.70f,
          valence = 0.80f,
          inferenceMs = 95L,
          captureStatus = "OK (onResults)",
          timestamp = 3000L,
          audioSource = "VOICE_RECOGNITION",
          inputDevice = "BUILTIN_MIC (Pixel)",
          speechDurationSec = 3.0f,
          wordCount = 6,
          speakingRateWps = 2.0f,
          isShortSpeech = false,
        ),
        // Excluded Mode A turn
        VoiceEmotionLogEntity(
          requestId = "r-a1",
          mode = "A",
          recognizerOk = true,
          recognizerErrorCode = null,
          recognizerPackage = "com.google.android.tts",
          clipLengthSec = 3.5f,
          rms = 0.05f,
          peak = 0.30f,
          silentCapture = false,
          arousal = 0.99f,
          dominance = 0.99f,
          valence = 0.99f,
          inferenceMs = 80L,
          captureStatus = "OK",
          timestamp = 2500L,
          speechDurationSec = 2.5f,
          wordCount = 5,
          speakingRateWps = 2.0f,
          isShortSpeech = false,
        ),
        // Excluded Short Mode B turn (1.1s < 1.5s)
        VoiceEmotionLogEntity(
          requestId = "r-b-short",
          mode = "B",
          recognizerOk = true,
          recognizerErrorCode = null,
          recognizerPackage = "com.google.android.tts",
          clipLengthSec = 2.2f,
          rms = 0.04f,
          peak = 0.25f,
          silentCapture = false,
          arousal = 0.10f,
          dominance = 0.10f,
          valence = 0.10f,
          inferenceMs = 70L,
          captureStatus = "OK (onResults, short)",
          timestamp = 2800L,
          speechDurationSec = 1.1f,
          wordCount = 2,
          speakingRateWps = 1.82f,
          isShortSpeech = true,
        ),
      )

    val baseline = com.example.viewmodel.RiviaViewModel.computeEmotionBaseline(sampleLogs, resetAtMs = 1000L)
    assertEquals(2, baseline.eligibleCount)
    assertEquals(1, baseline.excludedModeACount)
    assertEquals(1, baseline.excludedShortCount)
    assertEquals(0.50f, baseline.meanArousal!!, 1e-4f)
    assertEquals(0.60f, baseline.meanDominance!!, 1e-4f)
    assertEquals(0.70f, baseline.meanValence!!, 1e-4f)
    assertEquals(2.50f, baseline.meanSpeakingRateWps!!, 1e-4f)
  }

  @Test
  fun fourSecondWindowSplittingAndAveraging_scoresLongTurnsAndAveragesAdv() {
    // 10.0 seconds @ 16 kHz = 160,000 samples -> two 4s windows (64,000 each) + 2.0s remainder (32,000 >= 24,000) = 3 windows
    val tenSecSamples = FloatArray(160_000) { 0.1f }
    val windows10s = EmotionModelManager.splitInto4sWindows(tenSecSamples)
    assertEquals(3, windows10s.size)
    assertEquals(64_000, windows10s[0].size)
    assertEquals(64_000, windows10s[1].size)
    assertEquals(32_000, windows10s[2].size)

    // 8.5 seconds @ 16 kHz = 136,000 samples -> two 4s windows (64,000 each) + 0.5s remainder (8,000 < 24,000 dropped) = 2 windows
    val eightHalfSecSamples = FloatArray(136_000) { 0.1f }
    val windows8_5s = EmotionModelManager.splitInto4sWindows(eightHalfSecSamples)
    assertEquals(2, windows8_5s.size)

    val averaged =
      EmotionModelManager.averageWindowPredictions(
        listOf(
          com.example.audio.EmotionPrediction(arousal = 0.30f, dominance = 0.40f, valence = 0.50f, inferenceMs = 100L),
          com.example.audio.EmotionPrediction(arousal = 0.50f, dominance = 0.60f, valence = 0.70f, inferenceMs = 120L),
          com.example.audio.EmotionPrediction(arousal = 0.70f, dominance = 0.80f, valence = 0.90f, inferenceMs = 110L),
        )
      )
    assertNotNull(averaged)
    assertEquals(0.50f, averaged!!.arousal, 1e-4f)
    assertEquals(0.60f, averaged.dominance, 1e-4f)
    assertEquals(0.70f, averaged.valence, 1e-4f)
    assertEquals(330L, averaged.inferenceMs)
    assertEquals(3, averaged.scoredWindowsCount)
  }

  @Test
  fun edgeSilenceTrimmer_keeps200msPreRoll_preservesMidTurnSilence_andKeeps200msPostRollOfTrailingSilence() {
    val config =
      SileroVadConfig(
        startProbThreshold = 0.50f,
        startSpeechMs = 250,
        endProbThreshold = 0.35f,
        endSilenceMs = 700,
        sampleRateHz = 16000,
        chunkSamples = 512, // 32 ms per chunk
      )
    val tracker = SileroVadTurnTracker(config)
    val trimmer = com.example.audio.EdgeSilenceTrimmer()

    // 1. Leading silence: 30 chunks (15,360 samples = 960 ms).
    // First 12,160 samples = 0.01f (early leading silence -> should be dropped).
    // Last 3,200 samples (exact 200 ms pre-roll) = 0.02f (-> should be kept).
    val totalLeadingSilenceSamples = 30 * 512
    val earlyLeadingSilenceSamples = totalLeadingSilenceSamples - MicSharingRecorder.PRE_ROLL_SAMPLES // 12,160
    for (chunkIdx in 0 until 30) {
      val chunk =
        FloatArray(512) { i ->
          val sampleGlobalIdx = chunkIdx * 512 + i
          if (sampleGlobalIdx < earlyLeadingSilenceSamples) 0.01f else 0.02f
        }
      val snap = tracker.onChunkProbability(0.05f)
      trimmer.onChunk512(chunk, snap)
    }
    assertFalse(trimmer.speechHasStarted)

    // 2. Speech segment 1: 40 chunks (20,480 samples = 1,280 ms) with value 0.50f
    repeat(40) {
      val chunk = FloatArray(512) { 0.50f }
      val snap = tracker.onChunkProbability(0.85f)
      trimmer.onChunk512(chunk, snap)
    }
    assertTrue(trimmer.speechHasStarted)

    // 3. Mid-turn pause: 10 chunks (5,120 samples = 320 ms < 700 ms) with value 0.03f -> MUST stay!
    repeat(10) {
      val chunk = FloatArray(512) { 0.03f }
      val snap = tracker.onChunkProbability(0.15f)
      assertFalse(snap.turnEnded)
      trimmer.onChunk512(chunk, snap)
    }

    // 4. Speech segment 2: 30 chunks (15,360 samples = 960 ms) with value 0.60f
    repeat(30) {
      val chunk = FloatArray(512) { 0.60f }
      val snap = tracker.onChunkProbability(0.80f)
      trimmer.onChunk512(chunk, snap)
    }

    // 5. Trailing end-of-turn silence: 22 chunks (11,264 samples = 704 ms >= 700 ms).
    // First 3,200 samples (200 ms post-roll) have value 0.04f -> MUST be kept!
    // Remaining 8,064 samples (~504 ms) have value 0.05f -> MUST be dropped!
    var endSnap = tracker.onChunkProbability(0.10f)
    for (chunkIdx in 0 until 22) {
      if (chunkIdx > 0) {
        endSnap = tracker.onChunkProbability(0.10f)
      }
      val chunk =
        FloatArray(512) { i ->
          val trailingIdx = chunkIdx * 512 + i
          if (trailingIdx < MicSharingRecorder.POST_ROLL_SAMPLES) 0.04f else 0.05f
        }
      trimmer.onChunk512(chunk, endSnap)
    }
    assertTrue("Turn should have ended after 704 ms below 0.35", endSnap.turnEnded)

    val trimmed = trimmer.finishTurn()
    // 3,200 (pre-roll) + 20,480 (speech 1) + 5,120 (mid-turn pause) + 15,360 (speech 2) + 3,200 (200 ms post-roll) = 47,360
    val expectedSamplesCount = 3200 + 20480 + 5120 + 15360 + 3200
    assertEquals(expectedSamplesCount, trimmed.trimmedSamples.size)
    assertEquals(expectedSamplesCount, trimmed.remainderWindowSamples.size)
    assertEquals(expectedSamplesCount * 2, trimmed.trimmedPcm16LeBytes.size)

    // Verify 200 ms pre-roll (0.02f) is at the very beginning, and 0.01f leading silence was dropped
    assertEquals(0.02f, trimmed.trimmedSamples[0], 1e-3f)
    assertEquals(0.02f, trimmed.trimmedSamples[3199], 1e-3f)
    // Verify Speech 1 (0.50f)
    assertEquals(0.50f, trimmed.trimmedSamples[3200], 1e-3f)
    assertEquals(0.50f, trimmed.trimmedSamples[3200 + 20479], 1e-3f)
    // Verify mid-turn pause (0.03f) was preserved
    assertEquals(0.03f, trimmed.trimmedSamples[3200 + 20480], 1e-3f)
    assertEquals(0.03f, trimmed.trimmedSamples[3200 + 20480 + 5119], 1e-3f)
    // Verify Speech 2 (0.60f)
    val speech2Start = 3200 + 20480 + 5120
    assertEquals(0.60f, trimmed.trimmedSamples[speech2Start], 1e-3f)
    assertEquals(0.60f, trimmed.trimmedSamples[speech2Start + 15359], 1e-3f)
    // Verify 200 ms post-roll (0.04f) is at the end, and remaining 504 ms trailing silence (0.05f) was dropped
    val postRollStart = speech2Start + 15360
    assertEquals(0.04f, trimmed.trimmedSamples[postRollStart], 1e-3f)
    assertEquals(0.04f, trimmed.trimmedSamples.last(), 1e-3f)
  }

  @Test
  fun edgeSilenceTrimmer_recognizerTriggeredStart_uses1_5sRingBuffer_earliestProb035Minus200msPreRoll_orFullBuffer() {
    val config =
      SileroVadConfig(
        startProbThreshold = 0.50f,
        startSpeechMs = 250,
        endProbThreshold = 0.35f,
        endSilenceMs = 700,
        sampleRateHz = 16000,
        chunkSamples = 512,
      )

    // Case A: Recognizer triggers start; ring buffer has 60 chunks (30,720 samples > 24,000 cap).
    // After capping to the last 24,000 samples (46 full chunks + 448 tail samples):
    // We place a chunk with prob = 0.40 (>= 0.35, < 0.50 so VAD didn't self-start) 15 chunks before recognizer trigger,
    // preceded by > 3,200 samples of 0.12f pre-roll and earlier 0.11f silence.
    val trackerA = SileroVadTurnTracker(config)
    val trimmerA = com.example.audio.EdgeSilenceTrimmer()

    // 40 chunks of low-prob background (0.10 prob) -> 20,480 samples
    // First 25 chunks have sample value 0.11f (early silence), next 15 chunks (7,680 samples) have sample value 0.12f.
    for (idx in 0 until 40) {
      val sampleVal = if (idx < 25) 0.11f else 0.12f
      val snap = trackerA.onChunkProbability(0.10f)
      trimmerA.onChunk512(FloatArray(512) { sampleVal }, snap)
    }
    // 1 chunk with prob = 0.40f (>= 0.35f) and sample value 0.35f (earliest speech chunk!)
    val snapEarlyWord = trackerA.onChunkProbability(0.40f)
    assertFalse("Single 32ms chunk at 0.40 should not trigger VAD 250ms start", snapEarlyWord.speechStarted)
    trimmerA.onChunk512(FloatArray(512) { 0.35f }, snapEarlyWord)

    // 5 more chunks of prob = 0.25f and sample value 0.36f before recognizer callback arrives (lag = 6 chunks = 192 ms)
    repeat(5) {
      val snap = trackerA.onChunkProbability(0.25f)
      trimmerA.onChunk512(FloatArray(512) { 0.36f }, snap)
    }

    // Now SpeechRecognizer fires notifyExternalSpeechConfirmed()
    trackerA.notifyExternalSpeechConfirmed()
    val snapRecTrigger = trackerA.onChunkProbability(0.60f)
    assertTrue(snapRecTrigger.speechStarted)
    assertTrue(snapRecTrigger.speechStartedByExternalConfirm)
    trimmerA.onChunk512(FloatArray(512) { 0.70f }, snapRecTrigger)

    val resA = trimmerA.finishTurn()
    // Expected kept samples:
    // 3,200 samples of 0.12f (200 ms pre-roll before the prob=0.40 chunk)
    // + 512 samples of 0.35f (the earliest prob>=0.35 chunk)
    // + 5 * 512 (2,560) samples of 0.36f (lagged chunks)
    // + 512 samples of 0.70f (trigger chunk)
    // = 6,784 samples
    assertEquals(3200 + 512 + 2560 + 512, resA.trimmedSamples.size)
    assertEquals(0.12f, resA.trimmedSamples[0], 1e-3f)
    assertEquals(0.12f, resA.trimmedSamples[3199], 1e-3f)
    assertEquals(0.35f, resA.trimmedSamples[3200], 1e-3f)
    assertEquals(0.36f, resA.trimmedSamples[3200 + 512], 1e-3f)
    assertEquals(0.70f, resA.trimmedSamples.last(), 1e-3f)

    // Case B: Recognizer triggers start, and NO buffered chunk had prob >= 0.35 -> keeps the entire 1.5s (24,000-sample) ring buffer!
    val trackerB = SileroVadTurnTracker(config)
    val trimmerB = com.example.audio.EdgeSilenceTrimmer()
    // Push 60 chunks (30,720 samples) all with prob = 0.10 (< 0.35)
    repeat(60) {
      val snap = trackerB.onChunkProbability(0.10f)
      trimmerB.onChunk512(FloatArray(512) { 0.22f }, snap)
    }
    // Recognizer triggers speech start directly
    trimmerB.onRecognizerTriggeredStart()
    val resB = trimmerB.finishTurn()
    assertEquals(
      "When no chunk in 1.5s buffer has prob >= 0.35, whole 1.5s (24,000 samples) buffer must be kept",
      MicSharingRecorder.RECOGNIZER_RING_BUFFER_SAMPLES,
      resB.trimmedSamples.size,
    )
  }

  @Test
  fun emotionSyncBarrier_awaitsScoresWithin300ms_andLateTimeoutStillWritesAdvWithEmotionLateAndTotalMs() =
    runBlocking {
      val context = ApplicationProvider.getApplicationContext<Context>()
      val db =
        Room.inMemoryDatabaseBuilder(context, RiviaDatabase::class.java)
          .allowMainThreadQueries()
          .build()
      val dao = db.riviaDao()

      val dummyCapture =
        com.example.audio.MicCaptureResult(
          mode = "B",
          samples16kMono = FloatArray(32000) { 0.2f },
          pcm16Bytes = ByteArray(64000),
          clipLengthSec = 2.0f,
          rms = 0.2f,
          peak = 0.2f,
          silentCapture = false,
          recognizerPackage = "pkg",
          speechDurationSec = 1.8f,
        )

      // 1. Fast scoring (< 300 ms): succeeds and returns acoustic scores
      val okRes =
        com.example.service.LiveSessionManager.awaitEmotionScoresWithBarrier(
          turnRequestId = "r-fast",
          timeoutMs = 300L,
          finishCaptureBlock = { dummyCapture },
          scoreTurnBlock = {
            kotlinx.coroutines.delay(30L)
            Result.success(
              com.example.audio.EmotionPrediction(
                arousal = 0.42f,
                dominance = 0.58f,
                valence = 0.64f,
                inferenceMs = 28L,
              )
            )
          },
        )
      assertNotNull(okRes.prediction)
      assertEquals(0.42f, okRes.prediction!!.arousal, 1e-4f)
      assertEquals(null, okRes.failureReason)
      assertTrue("Wait time should be < 300 ms, was ${okRes.waitMs}", okRes.waitMs < 300L)

      // 2. Slow scoring (> 300 ms):
      // - Barrier times out at ~300 ms and returns immediately with "emotion_timeout" so the reply goes ahead.
      // - Initial row in voice_emotion_logs is written with null A/D/V and "emotion_timeout".
      // - Background scoring finishes at ~550 ms and updates the SAME voice_emotion_logs row with A/D/V,
      //   marked "emotion_late" and totalMs (>= 300 ms).
      val lateCompleted = kotlinx.coroutines.CompletableDeferred<Pair<String?, Long>>()
      val dummyDiag =
        com.example.service.TurnRecognizerDiagnostics(
          partialResultsCount = 3,
          callbacksFired = "onResults",
          usedPartialFallback = false,
          finalText = "Testing late emotion completion",
          pipeCloseToFinalMs = 40L,
          lastPartialText = "Testing late",
          transcriptSource = "onResults",
        )
      val t0 = android.os.SystemClock.elapsedRealtime()
      val timeoutRes =
        com.example.service.LiveSessionManager.awaitEmotionScoresWithBarrier(
          turnRequestId = "r-slow-late",
          timeoutMs = 300L,
          finishCaptureBlock = { dummyCapture },
          scoreTurnBlock = {
            kotlinx.coroutines.delay(550L)
            Result.success(
              com.example.audio.EmotionPrediction(
                arousal = 0.51f,
                dominance = 0.61f,
                valence = 0.71f,
                inferenceMs = 540L,
              )
            )
          },
          onCaptureAndScoreCompletedAsync = { capture, scoreResult, totalMs, failureReason ->
            runBlocking {
              com.example.viewmodel.RiviaViewModel.persistVoiceEmotionTurnLog(
                dao = dao,
                requestId = "r-slow-late",
                recognizerOk = true,
                recognizerErrorCode = null,
                recognizedText = "Testing late emotion completion",
                diagnostics = dummyDiag,
                captureResult = capture,
                precomputedEmotionResult = scoreResult,
                emotionWaitMs = totalMs,
                emotionBarrierFailureReason = failureReason,
                saveWavClipBlock = null,
              )
            }
            if (failureReason == com.example.service.LiveSessionManager.EMOTION_REASON_LATE) {
              lateCompleted.complete(failureReason to totalMs)
            }
          },
        )
      val elapsed = android.os.SystemClock.elapsedRealtime() - t0
      assertEquals(null, timeoutRes.prediction)
      assertEquals("emotion_timeout", timeoutRes.failureReason)
      assertTrue("Barrier must not hold reply for 550 ms (elapsed=${elapsed}ms)", elapsed < 500L)

      // Simulate the initial voice_emotion_logs row written right when the 300ms barrier timed out
      com.example.viewmodel.RiviaViewModel.persistVoiceEmotionTurnLog(
        dao = dao,
        requestId = "r-slow-late",
        recognizerOk = true,
        recognizerErrorCode = null,
        recognizedText = "Testing late emotion completion",
        diagnostics = dummyDiag,
        captureResult = dummyCapture.copy(samples16kMono = FloatArray(0)),
        precomputedEmotionResult = null,
        emotionWaitMs = timeoutRes.waitMs,
        emotionBarrierFailureReason = timeoutRes.failureReason,
        saveWavClipBlock = null,
      )

      // Wait for the background scoring coroutine to finish and update the DB row
      val (lateReason, lateTotalMs) = lateCompleted.await()
      assertEquals("emotion_late", lateReason)
      assertTrue("Late totalMs should reflect full scoring time (>= 300 ms), was $lateTotalMs", lateTotalMs >= 300L)

      val updatedRow = dao.getVoiceEmotionLogByRequestId("r-slow-late")
      assertNotNull("voice_emotion_logs row for r-slow-late must exist", updatedRow)
      assertEquals(0.51f, updatedRow!!.arousal!!, 1e-4f)
      assertEquals(0.61f, updatedRow.dominance!!, 1e-4f)
      assertEquals(0.71f, updatedRow.valence!!, 1e-4f)
      assertTrue(
        "captureStatus should include emotion_late and totalMs, was: ${updatedRow.captureStatus}",
        updatedRow.captureStatus.contains("emotion_late") && updatedRow.captureStatus.contains("${lateTotalMs}ms"),
      )
      // Verify no duplicate row was created for r-slow-late
      val allForSlowLate = dao.getAllVoiceEmotionLogsForExport().filter { it.requestId == "r-slow-late" }
      assertEquals("Late update must update the existing row, not insert a second row", 1, allForSlowLate.size)

      // 3. Scoring failure: continues without scores, records "emotion_error"
      val errRes =
        com.example.service.LiveSessionManager.awaitEmotionScoresWithBarrier(
          turnRequestId = "r-err",
          timeoutMs = 300L,
          finishCaptureBlock = { dummyCapture },
          scoreTurnBlock = { Result.failure(IllegalStateException("ONNX session error")) },
        )
      assertEquals(null, errRes.prediction)
      assertEquals("emotion_error", errRes.failureReason)

      db.close()
    }

  @Test
  fun turnPayloadBuilder_handlesNullAcoustic_baselineUnder20_normalDelta_andExcludesCurrentTurnFromBaseline() =
    runBlocking {
      val context = ApplicationProvider.getApplicationContext<Context>()
      val db =
        Room.inMemoryDatabaseBuilder(context, RiviaDatabase::class.java)
          .allowMainThreadQueries()
          .build()
      val dao = db.riviaDao()

      val nowMs = 170_000_000_000L
      // Insert 19 eligible Mode B turns before nowMs
      for (i in 1..19) {
        dao.insertVoiceEmotionLog(
          VoiceEmotionLogEntity(
            requestId = "r-prev-$i",
            mode = "B",
            recognizerOk = true,
            recognizerPackage = "pkg",
            clipLengthSec = 3.0f,
            rms = 0.05f,
            peak = 0.30f,
            silentCapture = false,
            arousal = 0.40f,
            dominance = 0.50f,
            valence = 0.60f,
            timestamp = nowMs - (20 - i) * 1000L,
            speechDurationSec = 2.0f,
            speakingRateWps = 2.0f,
            isShortSpeech = false,
          )
        )
      }
      // Also insert the CURRENT turn's row ("r-current") at nowMs with extreme values (0.99)
      // to verify that excluding the current turn prevents it from skewing its own baseline or making n=20!
      dao.insertVoiceEmotionLog(
        VoiceEmotionLogEntity(
          requestId = "r-current",
          mode = "B",
          recognizerOk = true,
          recognizerPackage = "pkg",
          clipLengthSec = 3.0f,
          rms = 0.05f,
          peak = 0.30f,
          silentCapture = false,
          arousal = 0.99f,
          dominance = 0.99f,
          valence = 0.99f,
          timestamp = nowMs,
          speechDurationSec = 2.5f,
          speakingRateWps = 4.0f,
          isShortSpeech = false,
        )
      )

      val baselineUnder20 =
        com.example.viewmodel.RiviaViewModel.queryAndComputeEmotionBaseline(
          dao = dao,
          resetAtMs = 0L,
          nowMs = nowMs,
          excludeRequestId = "r-current",
          excludeAtOrAfterNow = true,
        )
      assertEquals("Current turn must be excluded from its own baseline (leaving n=19)", 19, baselineUnder20.eligibleCount)

      val acoustic =
        com.example.util.TurnAcousticData(
          arousal = 0.55f,
          dominance = 0.65f,
          valence = 0.75f,
          speakingRateWps = 2.5f,
          speechDurationSec = 2.5f,
          isShortSpeech = false,
        )

      val messages8 =
        (1..8).map { idx ->
          com.example.data.MessageEntity(
            id = "m-$idx",
            chatId = "c-1",
            role = if (idx % 2 == 1) "user" else "assistant",
            content = "M$idx-" + "x".repeat(350), // > 300 chars to test 300-char cap
            createdAt = 1000L + idx,
          )
        }

      // Case 1: acoustic is null -> acoustic = null and delta = null
      val jsonNullAcoustic =
        org.json.JSONObject(
          com.example.util.TurnPayloadBuilder.buildPayloadJson(
            timestampMs = nowMs,
            requestId = "r-current",
            transcript = "Hello Rivia",
            transcriptSource = "onResults",
            acoustic = null,
            baseline = baselineUnder20,
            recentMessages = messages8,
          )
        )
      assertTrue("acoustic should be JSON null when unavailable", jsonNullAcoustic.isNull("acoustic"))
      assertTrue("delta should be JSON null when acoustic is null", jsonNullAcoustic.isNull("delta"))

      // Case 2: acoustic is present, but baseline.n == 19 (< 20) -> delta is null
      val jsonUnder20 =
        org.json.JSONObject(
          com.example.util.TurnPayloadBuilder.buildPayloadJson(
            timestampMs = nowMs,
            requestId = "r-current",
            transcript = "Hello Rivia",
            transcriptSource = "used partial",
            acoustic = acoustic,
            baseline = baselineUnder20,
            recentMessages = messages8,
          )
        )
      assertFalse(jsonUnder20.isNull("acoustic"))
      assertEquals(19, jsonUnder20.getJSONObject("baseline").getInt("n"))
      assertTrue("delta must be null when baseline.n < 20", jsonUnder20.isNull("delta"))

      // Case 3: Insert 20th prior eligible turn -> baseline.n == 20 (still excluding r-current!) -> delta is populated
      dao.insertVoiceEmotionLog(
        VoiceEmotionLogEntity(
          requestId = "r-prev-20",
          mode = "B",
          recognizerOk = true,
          recognizerPackage = "pkg",
          clipLengthSec = 3.0f,
          rms = 0.05f,
          peak = 0.30f,
          silentCapture = false,
          arousal = 0.40f,
          dominance = 0.50f,
          valence = 0.60f,
          timestamp = nowMs - 500L,
          speechDurationSec = 2.0f,
          speakingRateWps = 2.0f,
          isShortSpeech = false,
        )
      )
      val baseline20 =
        com.example.viewmodel.RiviaViewModel.queryAndComputeEmotionBaseline(
          dao = dao,
          resetAtMs = 0L,
          nowMs = nowMs,
          excludeRequestId = "r-current",
          excludeAtOrAfterNow = true,
        )
      assertEquals(20, baseline20.eligibleCount)
      assertEquals(0.40f, baseline20.meanArousal!!, 1e-4f)

      val jsonNormal =
        org.json.JSONObject(
          com.example.util.TurnPayloadBuilder.buildPayloadJson(
            timestampMs = nowMs,
            requestId = "r-current",
            transcript = "Hello Rivia",
            transcriptSource = "onResults",
            acoustic = acoustic,
            baseline = baseline20,
            recentMessages = messages8,
          )
        )
      assertEquals("r-current", jsonNormal.getString("request_id"))
      assertEquals("Hello Rivia", jsonNormal.getString("transcript"))
      assertEquals("onResults", jsonNormal.getString("transcript_source"))
      assertFalse("delta must be present when acoustic != null and baseline.n >= 20", jsonNormal.isNull("delta"))

      val deltaObj = jsonNormal.getJSONObject("delta")
      assertEquals(0.15, deltaObj.getDouble("arousal"), 1e-3) // 0.55 - 0.40
      assertEquals(0.15, deltaObj.getDouble("dominance"), 1e-3) // 0.65 - 0.50
      assertEquals(0.15, deltaObj.getDouble("valence"), 1e-3) // 0.75 - 0.60
      assertEquals(0.50, deltaObj.getDouble("speaking_rate_wps"), 1e-3) // 2.50 - 2.00

      val turnsArr = jsonNormal.getJSONArray("recent_turns")
      assertEquals(6, turnsArr.length())
      assertEquals(300, turnsArr.getJSONObject(0).getString("text").length)
      assertTrue(turnsArr.getJSONObject(0).getString("text").startsWith("M3-"))
      assertTrue(turnsArr.getJSONObject(5).getString("text").startsWith("M8-"))

      db.close()
    }

  @Test
  fun migration5To6_preservesExistingData_andCreatesTurnDispatchLogsTable_andBackupImportDeduplicatesByRequestId() =
    runBlocking {
      val context = ApplicationProvider.getApplicationContext<Context>()
      val dbName = "test_migration_5_6.db"
      context.deleteDatabase(dbName)

      // 1. Create a v5 SQLite database with existing chats and voice_emotion_logs rows, then run MIGRATION_5_6
      val openHelper =
        androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory()
          .create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
              .name(dbName)
              .callback(
                object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(5) {
                  override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL(
                      "CREATE TABLE IF NOT EXISTS `chats` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `runSettingsJson` TEXT NOT NULL, `isLabChat` INTEGER NOT NULL DEFAULT 0, `riviaProfile` TEXT NOT NULL DEFAULT 'balanced', PRIMARY KEY(`id`))"
                    )
                    db.execSQL(
                      "INSERT INTO `chats` (`id`, `title`, `createdAt`, `updatedAt`, `runSettingsJson`, `isLabChat`, `riviaProfile`) VALUES ('c-v5', 'Survived V5 Chat', 100, 200, '{}', 0, 'balanced')"
                    )
                  }

                  override fun onUpgrade(
                    db: androidx.sqlite.db.SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int,
                  ) {}
                }
              )
              .build()
          )
      val sqlDb = openHelper.writableDatabase
      RiviaDatabase.MIGRATION_5_6.migrate(sqlDb)

      // Verify v5 row survived and turn_dispatch_logs table exists and accepts inserts
      sqlDb.query("SELECT id, title FROM chats WHERE id = 'c-v5'").use { cursor ->
        assertTrue(cursor.moveToFirst())
        assertEquals("Survived V5 Chat", cursor.getString(1))
      }
      sqlDb.execSQL(
        "INSERT INTO `turn_dispatch_logs` (`request_id`, `created_at`, `payload_json`, `status`, `timings_json`) VALUES ('r-mig-1', 12345, '{\"ok\":true}', 'payload_logged', '{\"vad_end\":12000}')"
      )
      sqlDb.query("SELECT request_id, status FROM turn_dispatch_logs WHERE request_id = 'r-mig-1'").use { cursor ->
        assertTrue(cursor.moveToFirst())
        assertEquals("r-mig-1", cursor.getString(0))
        assertEquals("payload_logged", cursor.getString(1))
      }
      openHelper.close()
      context.deleteDatabase(dbName)

      // 2. Verify backup import deduplicates turn_dispatch_logs by request_id and export returns all rows
      val roomDb =
        Room.inMemoryDatabaseBuilder(context, RiviaDatabase::class.java)
          .allowMainThreadQueries()
          .build()
      val dao = roomDb.riviaDao()

      val backupWithDispatchJson =
        """
        {
          "version": 3,
          "exportedAt": 60000,
          "chats": [],
          "turnDispatchLogs": [
            {
              "requestId": "r-disp-1",
              "createdAt": 10000,
              "payloadJson": "{\"request_id\":\"r-disp-1\"}",
              "triageJson": null,
              "correctedJson": null,
              "toolName": null,
              "toolArgsJson": null,
              "toolResultJson": null,
              "replyText": "Hello back",
              "status": "completed",
              "error": null,
              "timingsJson": "{\"vad_end\":9800,\"transcript_ready\":9900,\"emotion_ready\":9950,\"reply_first_token\":10200,\"tts_first_audio\":10300,\"playback_done\":11500}"
            },
            {
              "requestId": "r-disp-2",
              "createdAt": 20000,
              "payloadJson": "{\"request_id\":\"r-disp-2\"}",
              "replyText": "Second reply",
              "status": "completed",
              "timingsJson": "{\"vad_end\":19800}"
            }
          ]
        }
        """.trimIndent()

      val parsed = com.example.viewmodel.RiviaViewModel.parseBackupJsonToPayload(backupWithDispatchJson)
      assertEquals(2, parsed.turnDispatchLogs.size)

      dao.importBackupInTransaction(parsed)
      assertEquals(2, dao.getAllTurnDispatchLogsForExport().size)

      // Importing the exact same backup a second time must not duplicate turn_dispatch_logs rows
      dao.importBackupInTransaction(parsed)
      val exportedAfterSecondImport = dao.getAllTurnDispatchLogsForExport()
      assertEquals(
        "Re-importing the same backup must skip existing request_ids in turn_dispatch_logs",
        2,
        exportedAfterSecondImport.size,
      )
      assertEquals("r-disp-2", exportedAfterSecondImport[0].requestId)
      assertEquals("r-disp-1", exportedAfterSecondImport[1].requestId)

      roomDb.close()
    }

  @Test
  fun fix1_terminalCallbackTranscript_endsWaitImmediatelyOnEmptyOnResultsOrOnError_andLogsCallbackTimesRelativeToPipeClose() {
    // 1. Empty onResults with non-empty lastPartial -> resolves immediately as "used partial" without waiting 3,000 ms
    val emptyResultsWithPartial =
      com.example.service.LiveSessionManager.resolveTerminalCallbackTranscript(
        directResult = "",
        lastPartialText = "hello rivia how are you",
        isErrorCallback = false,
      )
    assertTrue("Empty onResults with partial is a valid terminal resolution", emptyResultsWithPartial.recognizerOk)
    assertEquals("hello rivia how are you", emptyResultsWithPartial.finalSpoken)
    assertEquals("used partial", emptyResultsWithPartial.chosenSource)
    assertTrue(emptyResultsWithPartial.usedPartial)

    // 2. onError(5) (ERROR_CLIENT) with non-empty lastPartial -> resolves immediately as "used partial"
    val error5WithPartial =
      com.example.service.LiveSessionManager.resolveTerminalCallbackTranscript(
        directResult = "",
        lastPartialText = "testing immediate partial on error 5",
        isErrorCallback = true,
      )
    assertTrue("onError(5) with partial is a valid terminal resolution", error5WithPartial.recognizerOk)
    assertEquals("testing immediate partial on error 5", error5WithPartial.finalSpoken)
    assertEquals("used partial", error5WithPartial.chosenSource)
    assertTrue(error5WithPartial.usedPartial)

    // 3. Non-empty onResults -> prefers directResult ("onResults")
    val nonEmptyFinal =
      com.example.service.LiveSessionManager.resolveTerminalCallbackTranscript(
        directResult = "Final recognized sentence.",
        lastPartialText = "Final recognized",
        isErrorCallback = false,
      )
    assertTrue(nonEmptyFinal.recognizerOk)
    assertEquals("Final recognized sentence.", nonEmptyFinal.finalSpoken)
    assertEquals("onResults", nonEmptyFinal.chosenSource)
    assertFalse(nonEmptyFinal.usedPartial)

    // 4. Relative callback timestamps wrt pipeCloseAtMs
    val pipeCloseAt = 10_000L
    val events =
      listOf(
        com.example.service.LiveSessionManager.RecognizerCallbackEvent("onReadyForSpeech", 9_100L),
        com.example.service.LiveSessionManager.RecognizerCallbackEvent("onBeginningOfSpeech", 9_300L),
        com.example.service.LiveSessionManager.RecognizerCallbackEvent("onPartialResults#1", 9_650L),
        com.example.service.LiveSessionManager.RecognizerCallbackEvent("onEndOfSpeech", 10_018L),
        com.example.service.LiveSessionManager.RecognizerCallbackEvent("onResults(empty)", 10_042L),
      )
    val formatted =
      com.example.service.LiveSessionManager.formatCallbackEventsList(
        events = events,
        pipeCloseAtMs = pipeCloseAt,
      )
    assertEquals(
      "onReadyForSpeech(-900ms), onBeginningOfSpeech(-700ms), onPartialResults#1(-350ms), onEndOfSpeech(+18ms), onResults(empty)(+42ms)",
      formatted,
    )
  }

  @Test
  fun fix2_3_4_5_timingsJson_overlapScoreDeferred_nullBaselineWhenZero_andStripAudioTagsFromRecentTurns() =
    runBlocking {
      val context = ApplicationProvider.getApplicationContext<Context>()
      val db =
        Room.inMemoryDatabaseBuilder(context, RiviaDatabase::class.java)
          .allowMainThreadQueries()
          .build()
      val dao = db.riviaDao()

      val nowMs = 180_000_000_000L

      // Fix 4: When baseline n = 0, payload's baseline must be null (JSONObject.NULL), not zeros
      val zeroBaseline =
        com.example.viewmodel.RiviaViewModel.queryAndComputeEmotionBaseline(
          dao = dao,
          resetAtMs = 0L,
          nowMs = nowMs,
        )
      assertEquals(0, zeroBaseline.eligibleCount)

      // Fix 5: Recent turns contain [audio tags] which must be stripped in payload recent_turns,
      // while kept intact in the original MessageEntity rows.
      val messagesWithAudioTags =
        listOf(
          com.example.data.MessageEntity(
            id = "m-tag-1",
            chatId = "c-1",
            role = "user",
            content = "Tell me a joke",
            createdAt = 1000L,
          ),
          com.example.data.MessageEntity(
            id = "m-tag-2",
            chatId = "c-1",
            role = "assistant",
            content = "[warmly] Sure thing! [laughs] Why did the developer cross the road? [sighs]",
            createdAt = 2000L,
          ),
        )

      // Fix 8 + Fix 4 + Fix 5 in TurnPayloadBuilder:
      val payloadNullAcousticAndZeroBaseline =
        org.json.JSONObject(
          com.example.util.TurnPayloadBuilder.buildPayloadJson(
            timestampMs = nowMs,
            requestId = "r-fix458",
            transcript = "Testing null baseline and stripped tags",
            transcriptSource = "used partial",
            acoustic = null,
            baseline = zeroBaseline,
            recentMessages = messagesWithAudioTags,
          )
        )

      assertTrue(
        "Fix 4: When baseline n = 0, payload baseline must be JSON null, not zeros",
        payloadNullAcousticAndZeroBaseline.isNull("baseline"),
      )
      assertTrue(payloadNullAcousticAndZeroBaseline.isNull("acoustic"))
      assertEquals(
        "Fix 8: When acoustic is null (e.g. Mode A), words_vs_scores must be 'no_scores'",
        "no_scores",
        payloadNullAcousticAndZeroBaseline.optString("words_vs_scores"),
      )

      val recentTurns = payloadNullAcousticAndZeroBaseline.getJSONArray("recent_turns")
      assertEquals(2, recentTurns.length())
      val assistantPayloadTurnText = recentTurns.getJSONObject(1).getString("text")
      assertEquals(
        "Fix 5: [audio tags] must be stripped from recent_turns in payload",
        "Sure thing! Why did the developer cross the road?",
        assistantPayloadTurnText,
      )
      assertTrue(
        "Original MessageEntity must still retain [audio tags] for screen/TTS",
        messagesWithAudioTags[1].content.contains("[warmly]") &&
          messagesWithAudioTags[1].content.contains("[laughs]"),
      )

      // Fix 2: timings_json records request_sent, first_byte, and system_instruction_chars
      val mergedTimings =
        com.example.util.TurnPayloadBuilder.mergeTimingsJson(
          existingTimingsJson = null,
          vadEndMs = 1000L,
          transcriptReadyMs = 1040L,
          emotionReadyMs = 1085L,
          requestSentMs = 1090L,
          firstByteMs = 1310L,
          systemInstructionChars = 4280,
          replyFirstTokenMs = 1345L,
          ttsFirstAudioMs = 1520L,
          playbackDoneMs = 3100L,
        )
      val timingsObj = org.json.JSONObject(mergedTimings)
      assertEquals(1000L, timingsObj.getLong("vad_end"))
      assertEquals(1040L, timingsObj.getLong("transcript_ready"))
      assertEquals(1085L, timingsObj.getLong("emotion_ready"))
      assertEquals(1090L, timingsObj.getLong("request_sent"))
      assertEquals(1310L, timingsObj.getLong("first_byte"))
      assertEquals(4280, timingsObj.getInt("system_instruction_chars"))
      assertEquals(1345L, timingsObj.getLong("reply_first_token"))
      assertEquals(1520L, timingsObj.getLong("tts_first_audio"))
      assertEquals(3100L, timingsObj.getLong("playback_done"))

      // Fix 3: Final emotion window scoring starts at VAD end-of-turn (preStartedScoreDeferred)
      // and overlaps with transcript wait so awaitEmotionScoresWithBarrier reuses it.
      val preStartedDeferred =
        kotlinx.coroutines.CompletableDeferred(
          Result.success(
            com.example.audio.EmotionPrediction(
              arousal = 0.61f,
              dominance = 0.62f,
              valence = 0.63f,
              inferenceMs = 45L,
            )
          )
        )
      val captureWithPreStartedScore =
        com.example.audio.MicCaptureResult(
          mode = "B",
          samples16kMono = FloatArray(32000) { 0.1f },
          pcm16Bytes = ByteArray(64000),
          clipLengthSec = 2.0f,
          rms = 0.1f,
          peak = 0.2f,
          silentCapture = false,
          recognizerPackage = "pkg",
          speechDurationSec = 1.8f,
          preStartedScoreDeferred = preStartedDeferred,
        )
      var fallbackScoreCalled = false
      val barrierRes =
        com.example.service.LiveSessionManager.awaitEmotionScoresWithBarrier(
          turnRequestId = "r-overlap",
          timeoutMs = 300L,
          finishCaptureBlock = { captureWithPreStartedScore },
          scoreTurnBlock = {
            fallbackScoreCalled = true
            Result.failure(IllegalStateException("Should not call scoreTurnBlock when preStartedScoreDeferred is present"))
          },
        )
      assertFalse("Must await preStartedScoreDeferred started at VAD end-of-turn instead of starting scoring late", fallbackScoreCalled)
      assertNotNull(barrierRes.prediction)
      assertEquals(0.61f, barrierRes.prediction!!.arousal, 1e-4f)

      db.close()
    }

  @Test
  fun fix6_migration6To7_addsCalibrationLabel_andBaselineExcludesCalibratedRows() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val dbName = "test_migration_6_7.db"
    context.deleteDatabase(dbName)

    val openHelper =
      androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory()
        .create(
          androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(
              object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(6) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                  db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `voice_emotion_logs` (
                      `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                      `requestId` TEXT NOT NULL,
                      `mode` TEXT NOT NULL,
                      `recognizerOk` INTEGER NOT NULL,
                      `recognizerErrorCode` INTEGER,
                      `recognizerPackage` TEXT NOT NULL,
                      `clipLengthSec` REAL NOT NULL,
                      `rms` REAL NOT NULL,
                      `peak` REAL NOT NULL,
                      `silentCapture` INTEGER NOT NULL,
                      `arousal` REAL,
                      `dominance` REAL,
                      `valence` REAL,
                      `inferenceMs` INTEGER,
                      `captureStatus` TEXT NOT NULL,
                      `wavFilePath` TEXT,
                      `errorMessage` TEXT,
                      `timestamp` INTEGER NOT NULL,
                      `audioRecordStarted` INTEGER NOT NULL DEFAULT 1,
                      `pipeBytesWritten` INTEGER NOT NULL DEFAULT 0,
                      `partialResultsCount` INTEGER NOT NULL DEFAULT 0,
                      `callbacksFired` TEXT NOT NULL DEFAULT '',
                      `usedPartialFallback` INTEGER NOT NULL DEFAULT 0,
                      `vadSpeechDetected` INTEGER NOT NULL DEFAULT 0,
                      `vadEndedTurn` INTEGER NOT NULL DEFAULT 0,
                      `vadMaxProb` REAL NOT NULL DEFAULT 0.0,
                      `transcriptPreview` TEXT NOT NULL DEFAULT '',
                      `finalText` TEXT NOT NULL DEFAULT '',
                      `pipeCloseToFinalMs` INTEGER,
                      `lastPartialText` TEXT NOT NULL DEFAULT '',
                      `transcriptSource` TEXT NOT NULL DEFAULT '',
                      `audioSource` TEXT NOT NULL DEFAULT 'VOICE_RECOGNITION',
                      `inputDevice` TEXT NOT NULL DEFAULT 'unknown',
                      `speechDurationSec` REAL NOT NULL DEFAULT 0.0,
                      `wordCount` INTEGER NOT NULL DEFAULT 0,
                      `speakingRateWps` REAL,
                      `isShortSpeech` INTEGER NOT NULL DEFAULT 0,
                      `scoredWindowsCount` INTEGER NOT NULL DEFAULT 1
                    )
                    """.trimIndent()
                  )
                  db.execSQL(
                    "INSERT INTO `voice_emotion_logs` (`requestId`, `mode`, `recognizerOk`, `recognizerPackage`, `clipLengthSec`, `rms`, `peak`, `silentCapture`, `arousal`, `dominance`, `valence`, `captureStatus`, `timestamp`, `speechDurationSec`) VALUES ('r-v6-row', 'B', 1, 'pkg', 2.5, 0.04, 0.25, 0, 0.45, 0.55, 0.65, 'OK', 100000, 2.1)"
                  )
                }

                override fun onUpgrade(
                  db: androidx.sqlite.db.SupportSQLiteDatabase,
                  oldVersion: Int,
                  newVersion: Int,
                ) {}
              }
            )
            .build()
        )

    val sqlDb = openHelper.writableDatabase
    RiviaDatabase.MIGRATION_6_7.migrate(sqlDb)

    sqlDb.execSQL(
      "UPDATE `voice_emotion_logs` SET `calibration_label` = 'excited', `rms_after_gain` = 0.10, `applied_gain_db` = 8.0 WHERE `requestId` = 'r-v6-row'"
    )
    sqlDb.query(
      "SELECT requestId, calibration_label, rms_after_gain, applied_gain_db FROM voice_emotion_logs WHERE requestId = 'r-v6-row'"
    ).use { cursor ->
      assertTrue(cursor.moveToFirst())
      assertEquals("r-v6-row", cursor.getString(0))
      assertEquals("excited", cursor.getString(1))
      assertEquals(0.10f, cursor.getFloat(2), 1e-4f)
      assertEquals(8.0f, cursor.getFloat(3), 1e-4f)
    }
    openHelper.close()
    context.deleteDatabase(dbName)

    // Verify DAO baseline query excludes rows with any calibration_label
    val roomDb =
      Room.inMemoryDatabaseBuilder(context, RiviaDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    val dao = roomDb.riviaDao()
    val nowMs = 200_000_000_000L

    val id1 =
      dao.insertVoiceEmotionLog(
        VoiceEmotionLogEntity(
          requestId = "r-cal-1",
          mode = "B",
          recognizerOk = true,
          recognizerPackage = "pkg",
          clipLengthSec = 3.0f,
          rms = 0.05f,
          peak = 0.30f,
          silentCapture = false,
          arousal = 0.40f,
          dominance = 0.50f,
          valence = 0.60f,
          timestamp = nowMs - 2000L,
          speechDurationSec = 2.2f,
          isShortSpeech = false,
        )
      )
    val id2 =
      dao.insertVoiceEmotionLog(
        VoiceEmotionLogEntity(
          requestId = "r-cal-2",
          mode = "B",
          recognizerOk = true,
          recognizerPackage = "pkg",
          clipLengthSec = 3.0f,
          rms = 0.05f,
          peak = 0.30f,
          silentCapture = false,
          arousal = 0.90f,
          dominance = 0.90f,
          valence = 0.90f,
          timestamp = nowMs - 1000L,
          speechDurationSec = 2.4f,
          isShortSpeech = false,
        )
      )

    // Initially both rows are eligible (n = 2)
    val beforeCal =
      com.example.viewmodel.RiviaViewModel.queryAndComputeEmotionBaseline(
        dao = dao,
        resetAtMs = 0L,
        nowMs = nowMs,
      )
    assertEquals(2, beforeCal.eligibleCount)
    assertEquals(0, beforeCal.excludedCalibratedCount)

    // Set calibration_label on id2 to each allowed label and confirm exclusion
    for (label in com.example.viewmodel.RiviaViewModel.CALIBRATION_LABELS) {
      dao.updateVoiceEmotionCalibrationLabel(id2, label)
      val afterCal =
        com.example.viewmodel.RiviaViewModel.queryAndComputeEmotionBaseline(
          dao = dao,
          resetAtMs = 0L,
          nowMs = nowMs,
        )
      assertEquals("Row with calibration_label='$label' must be excluded from baseline", 1, afterCal.eligibleCount)
      assertEquals(1, afterCal.excludedCalibratedCount)
      assertEquals(0.40f, afterCal.meanArousal!!, 1e-4f)
    }

    // Clearing calibration_label makes id2 eligible again
    dao.updateVoiceEmotionCalibrationLabel(id2, null)
    val afterClear =
      com.example.viewmodel.RiviaViewModel.queryAndComputeEmotionBaseline(
        dao = dao,
        resetAtMs = 0L,
        nowMs = nowMs,
      )
    assertEquals(2, afterClear.eligibleCount)
    assertEquals(0, afterClear.excludedCalibratedCount)
    assertTrue(id1 > 0L)

    roomDb.close()
  }

  @Test
  fun fix7_pipeAutoGainProcessor_targetsMinus20dbfs_capsAtPlus20db_limitsPeaks_andKeepsRawAudioUntouched() {
    val agc = com.example.audio.PipeAutoGainProcessor()

    // Create 1 second (16,000 samples) of quiet sine-like speech at RMS = 0.015 (-36.5 dBFS)
    // With max +20 dB (10.0x) gain, RMS after gain should boost by ~10x (or towards 0.10 target) and never clip.
    val rawShortChunk = ShortArray(512) { i -> if (i % 2 == 0) 491 else -491 } // 491 / 32768 ~= 0.0150
    val rawCopyBefore = rawShortChunk.copyOf()

    var lastPipeBytes = ByteArray(0)
    repeat(32) {
      lastPipeBytes = agc.processChunkToPcm16Bytes(rawShortChunk, rawShortChunk.size)
    }

    // 1. Raw audio array must be 100% untouched (VAD and emotion model use raw audio)
    assertTrue("Raw audio buffer must not be mutated by PipeAutoGainProcessor", rawShortChunk.contentEquals(rawCopyBefore))

    // 2. RMS before gain should be ~0.015, RMS after gain should be boosted toward ~0.10 (-20 dBFS), gain capped <= +20 dB
    val rmsBefore = agc.rmsBeforeGain
    val rmsAfter = agc.rmsAfterGain
    val gainDb = agc.appliedGainDb
    assertEquals(0.015f, rmsBefore, 0.002f)
    assertTrue("RMS after gain ($rmsAfter) must be significantly higher than RMS before ($rmsBefore)", rmsAfter > rmsBefore * 4f)
    assertTrue("Applied gain ($gainDb dB) must not exceed +20 dB", gainDb <= 20.05f)
    assertEquals(rawShortChunk.size * 2, lastPipeBytes.size)

    // 3. Limiter check: even with extreme transient peaks, output samples never clip outside [-32767, 32767]
    val loudPeakChunk = ShortArray(512) { 30000 }
    val limitedBytes = agc.processChunkToPcm16Bytes(loudPeakChunk, loudPeakChunk.size)
    val bb = ByteBuffer.wrap(limitedBytes).order(ByteOrder.LITTLE_ENDIAN)
    for (i in 0 until 512) {
      val s = bb.short.toInt()
      assertTrue("Sample $s must be within [-32767, 32767]", s in -32767..32767)
    }
  }

  @Test
  fun fix8_modeAGoogleCapture_doesNotStartAudioRecord_andLogsNullAcousticAndNoScores() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db =
      Room.inMemoryDatabaseBuilder(context, RiviaDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    val dao = db.riviaDao()

    // Mode A capture result when AudioRecord is not started at all
    val modeACapture =
      com.example.audio.MicCaptureResult(
        mode = "A",
        samples16kMono = FloatArray(0),
        pcm16Bytes = ByteArray(0),
        clipLengthSec = 0f,
        rms = 0f,
        peak = 0f,
        silentCapture = false,
        recognizerPackage = "com.google.android.tts",
        audioRecordStarted = false,
        pipeBytesWritten = 0L,
        audioSource = "GOOGLE_RECOGNIZER",
        inputDevice = "SpeechRecognizer (direct mic)",
      )

    val barrierRes =
      com.example.service.LiveSessionManager.awaitEmotionScoresWithBarrier(
        turnRequestId = "r-mode-a-direct",
        timeoutMs = 300L,
        finishCaptureBlock = { modeACapture },
        scoreTurnBlock = {
          Result.failure(IllegalStateException("Mode A must not run emotion model"))
        },
      )
    assertEquals(null, barrierRes.prediction)
    assertEquals(com.example.service.LiveSessionManager.EMOTION_REASON_NO_SCORES, barrierRes.failureReason)

    val diag =
      com.example.service.TurnRecognizerDiagnostics(
        partialResultsCount = 2,
        callbacksFired = "onReadyForSpeech, onBeginningOfSpeech, onResults",
        usedPartialFallback = false,
        finalText = "Mode A direct recognizer capture works",
        pipeCloseToFinalMs = null,
        lastPartialText = "Mode A direct",
        transcriptSource = "onResults",
      )

    val savedLog =
      com.example.viewmodel.RiviaViewModel.persistVoiceEmotionTurnLog(
        dao = dao,
        requestId = "r-mode-a-direct",
        recognizerOk = true,
        recognizerErrorCode = null,
        recognizedText = "Mode A direct recognizer capture works",
        diagnostics = diag,
        captureResult = modeACapture,
        precomputedEmotionResult = null,
        emotionWaitMs = barrierRes.waitMs,
        emotionBarrierFailureReason = barrierRes.failureReason,
      )

    assertEquals("A", savedLog.mode)
    assertFalse("Mode A must have audioRecordStarted = false", savedLog.audioRecordStarted)
    assertEquals(null, savedLog.arousal)
    assertEquals(null, savedLog.dominance)
    assertEquals(null, savedLog.valence)
    assertEquals("OK (Mode A · no_scores)", savedLog.captureStatus)
    assertEquals(null, savedLog.errorMessage)

    db.close()
  }
}

