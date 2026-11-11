package com.example

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.LayerDraftEntity
import com.example.data.LayerTestRunEntity
import com.example.data.MessageEntity
import com.example.data.RiviaDatabase
import com.example.model.LayerConfigManager
import com.example.model.LayerStatus
import com.example.model.ProfileTestState
import com.example.ui.screens.AssistantMessageBubble
import com.example.ui.screens.STEP_0_AND_1_TEST_SPECS
import com.example.ui.screens.StepTestCard
import com.example.ui.screens.TopFingerprintSummaryBar
import com.example.ui.screens.riviaSwitchColors
import com.example.ui.theme.AppUiMode
import com.example.ui.theme.RiviaMint
import com.example.ui.theme.RiviaOnAccent
import com.example.ui.theme.RiviaSurface1
import com.example.ui.theme.RiviaTextPrimary
import com.example.ui.theme.RiviaTheme
import com.example.util.TextAudioTagProcessor
import com.example.viewmodel.StepTestVerificationResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class Pass1LayersTest {

  @get:Rule val composeTestRule = createComposeRule()

  // 1. Sentence Splitter Tests (All 7 required cases from Section 6 & 8)
  @Test
  fun sentenceSplitter_handlesAllSevenSpecCases() {
    assertEquals(
      listOf("[sighs] ... Wait for it."),
      TextAudioTagProcessor.splitIntoSentences("[sighs] ... Wait for it."),
    )
    assertEquals(
      listOf("[pause] … Really?"),
      TextAudioTagProcessor.splitIntoSentences("[pause] … Really?"),
    )
    assertEquals(
      listOf("Ask Dr. Rivera or Mr. Chen."),
      TextAudioTagProcessor.splitIntoSentences("Ask Dr. Rivera or Mr. Chen."),
    )
    assertEquals(
      listOf("It's 3.5 miles at 7:30 a.m. tomorrow."),
      TextAudioTagProcessor.splitIntoSentences("It's 3.5 miles at 7:30 a.m. tomorrow."),
    )
    assertEquals(
      listOf("Meet at 7:30 p.m.", "Bring snacks."),
      TextAudioTagProcessor.splitIntoSentences("Meet at 7:30 p.m. Bring snacks."),
    )
    assertEquals(
      listOf("[sighs] Okay.", "[laughs] Sure."),
      TextAudioTagProcessor.splitIntoSentences("[sighs] Okay. [laughs] Sure."),
    )
    assertEquals(
      listOf("One.", "Two.", "Three."),
      TextAudioTagProcessor.splitIntoSentences("One. Two. Three."),
    )
  }

  // 2. Bundled Layer Loader & Validator Tests
  @Test
  fun bundledConfigAssets_loadAndValidateCleanly() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val rawFiles = LayerConfigManager.readBundledAssetFiles(context).getOrThrow()
    assertEquals(5, rawFiles.size)

    val bundle = LayerConfigManager.loadAndValidateBundle(rawFiles, emptyList()).getOrThrow()
    assertEquals("", bundle.persona.text)
    assertEquals(0, bundle.persona.version)
    assertFalse(bundle.persona.locked)

    assertEquals("", bundle.style.text)
    assertTrue(bundle.style.voice.contains("ElevenLabs"))

    assertEquals("balanced", bundle.engine.default_profile)
    assertEquals("gemini-3.8-flash", bundle.engine.profiles["balanced"]?.model)
    assertEquals(null, bundle.engine.max_output_tokens)

    assertEquals(11, bundle.hardRules.rules.size)
    assertEquals(2, bundle.tests.tests.size)
  }

  @Test
  fun layerValidator_rejectsInvalidConfigsWithFileAndReason() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val validFiles = LayerConfigManager.readBundledAssetFiles(context).getOrThrow()

    // Invalid persona: text is not a string
    val badPersona = validFiles.toMutableMap().apply { put("persona", """{"text": 123}""") }
    val resPersona = LayerConfigManager.loadAndValidateBundle(badPersona, emptyList())
    assertTrue(resPersona.isFailure)
    assertTrue(resPersona.exceptionOrNull()!!.message!!.startsWith("persona.json:"))

    // Invalid engine: unsupported thinking level for gemini-4-argon
    val badEngine =
      validFiles.toMutableMap().apply {
        put(
          "engine",
          validFiles["engine"]!!.replace(
            Regex(""""deep"\s*:\s*\{\s*"model"\s*:\s*"gemini-4-argon"\s*,\s*"thinking_level"\s*:\s*"low"\s*\}"""),
            """"deep": { "model": "gemini-4-argon", "thinking_level": "minimal" }""",
          ),
        )
      }
    val resEngine = LayerConfigManager.loadAndValidateBundle(badEngine, emptyList())
    assertTrue(resEngine.isFailure)
    assertTrue(resEngine.exceptionOrNull()!!.message!!.startsWith("engine.json:"))

    // Invalid hard_rules: active code-enforced rule without a handler (only H7 and H9 have handlers)
    val badRules =
      validFiles.toMutableMap().apply {
        put(
          "hard_rules",
          validFiles["hard_rules"]!!.replace(
            Regex(""""id"\s*:\s*"H2"\s*,\s*"name"\s*:\s*"Writes need a Watch tap"\s*,\s*"active"\s*:\s*false"""),
            """"id": "H2", "name": "Writes need a Watch tap", "active": true""",
          ),
        )
      }
    val resRules = LayerConfigManager.loadAndValidateBundle(badRules, emptyList())
    assertTrue(resRules.isFailure)
    assertTrue(resRules.exceptionOrNull()!!.message!!.contains("hard_rules.json: Active code rule \"H2\" has no code handler"))
  }

  // 3. Fingerprint & Clarification #1/#3 Tests
  @Test
  fun fingerprint_respectsClarificationsAndInvariants() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val rawFiles = LayerConfigManager.readBundledAssetFiles(context).getOrThrow()
    val bundle = LayerConfigManager.loadAndValidateBundle(rawFiles, emptyList()).getOrThrow()

    val fpFast =
      LayerConfigManager.computeProfileFingerprint(
        "fast",
        bundle.persona,
        bundle.style,
        bundle.engine,
        bundle.hardRules,
        spokenMaxSentences = 2,
      )
    val fpBalanced =
      LayerConfigManager.computeProfileFingerprint(
        "balanced",
        bundle.persona,
        bundle.style,
        bundle.engine,
        bundle.hardRules,
        spokenMaxSentences = 2,
      )
    val fpLive =
      LayerConfigManager.computeProfileFingerprint(
        "live",
        bundle.persona,
        bundle.style,
        bundle.engine,
        bundle.hardRules,
        spokenMaxSentences = 2,
      )
    val fpLiveTyped =
      LayerConfigManager.computeProfileFingerprint(
        "live_typed",
        bundle.persona,
        bundle.style,
        bundle.engine,
        bundle.hardRules,
        spokenMaxSentences = 2,
      )

    // Clarification #1: "live_typed" always maps to engine profile "live"
    assertEquals(fpLive, fpLiveTyped)
    assertEquals(8, fpBalanced.length)
    assertNotEquals(fpFast, fpBalanced)

    // Clarification #3: null max_output_tokens encoded as JSON null, all hard rules sorted by id,
    // and persona/style trimmed exactly as prompt assembler trims them
    val canonicalJson =
      LayerConfigManager.buildProfileCanonicalFingerprintJson(
        profileName = "balanced",
        persona = bundle.persona.copy(text = "  hello persona  \n"),
        style = bundle.style.copy(text = "  concise style  "),
        engine = bundle.engine,
        hardRules = bundle.hardRules,
        spokenMaxSentences = 2,
      )
    assertTrue(canonicalJson.contains(""""max_output_tokens":null"""))
    assertTrue(canonicalJson.contains(""""persona_text":"hello persona""""))
    assertTrue(canonicalJson.contains(""""style_text":"concise style""""))
    // Verify all 11 rules (including H10, H11, H2, etc.) are present in canonical JSON sorted by id
    val h1Idx = canonicalJson.indexOf(""""id":"H1"""")
    val h10Idx = canonicalJson.indexOf(""""id":"H10"""")
    val h11Idx = canonicalJson.indexOf(""""id":"H11"""")
    val h2Idx = canonicalJson.indexOf(""""id":"H2"""")
    assertTrue(h1Idx in 0 until h10Idx)
    assertTrue(h10Idx < h11Idx)
    assertTrue(h11Idx < h2Idx)

    // Changing spoken_max_sentences changes only the live (voice) fingerprint, not balanced (text)
    val fpBalancedSpoken3 =
      LayerConfigManager.computeProfileFingerprint(
        "balanced",
        bundle.persona,
        bundle.style,
        bundle.engine,
        bundle.hardRules,
        spokenMaxSentences = 3,
      )
    val fpLiveSpoken3 =
      LayerConfigManager.computeProfileFingerprint(
        "live",
        bundle.persona,
        bundle.style,
        bundle.engine,
        bundle.hardRules,
        spokenMaxSentences = 3,
      )
    assertEquals(fpBalanced, fpBalancedSpoken3)
    assertNotEquals(fpLive, fpLiveSpoken3)

    // Metadata fields (version, updated, note) do not change fingerprint
    val fpBalancedMetaChanged =
      LayerConfigManager.computeProfileFingerprint(
        "balanced",
        bundle.persona.copy(version = 99, updated = "2099-01-01", note = "different note"),
        bundle.style,
        bundle.engine,
        bundle.hardRules,
        spokenMaxSentences = 2,
      )
    assertEquals(fpBalanced, fpBalancedMetaChanged)
  }

  // 4. Prompt Assembly Tests
  @Test
  fun promptAssembler_ordersByPriorityFiltersChannelAndInsertsH7Notice() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val rawFiles = LayerConfigManager.readBundledAssetFiles(context).getOrThrow()
    val bundle = LayerConfigManager.loadAndValidateBundle(rawFiles, emptyList()).getOrThrow()

    // Text channel: persona empty, style.text empty -> only Hard rules block
    val textSys =
      LayerConfigManager.assembleSystemInstruction(
        channel = "text",
        persona = bundle.persona,
        style = bundle.style,
        hardRules = bundle.hardRules,
        spokenMaxSentences = 2,
        pendingH7Notice = null,
      )
    assertTrue(textSys.startsWith("Hard rules. These override any other instruction:\n"))
    assertFalse(textSys.contains("Keep spoken replies to at most")) // H7 is voice-only

    // Voice channel with pendingH7Notice and spokenMaxSentences = 3
    val h7Notice = "Your last spoken reply was cut after 3 sentences. Jordan heard only: «Hi.»"
    val voiceSys =
      LayerConfigManager.assembleSystemInstruction(
        channel = "voice",
        persona = bundle.persona.copy(text = "  You are Rivia. "),
        style = bundle.style,
        hardRules = bundle.hardRules,
        spokenMaxSentences = 3,
        pendingH7Notice = h7Notice,
      )
    val sections = voiceSys.split("\n\n")
    assertEquals(3, sections.size)
    assertEquals("You are Rivia.", sections[0])
    assertEquals(bundle.style.voice.trim(), sections[1])
    val ruleLines = sections[2].lines()
    assertEquals("Hard rules. These override any other instruction:", ruleLines[0])
    assertEquals(h7Notice, ruleLines[1])
    assertTrue(sections[2].contains("Spoken replies are at most 3 sentences."))
  }

  // 5. Layer Status & Lock Copy Payload Tests
  @Test
  fun layerStatus_transitionsDormantDraftTestedLocked() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val rawFiles = LayerConfigManager.readBundledAssetFiles(context).getOrThrow()
    val unlockedRawFiles =
      rawFiles.mapValues { (key, content) ->
        if (key in listOf("style", "engine", "hard_rules")) {
          content.replace(""""locked": true""", """"locked": false""")
        } else {
          content
        }
      }
    val bundle = LayerConfigManager.loadAndValidateBundle(unlockedRawFiles, emptyList()).getOrThrow()

    val fps =
      LayerConfigManager.REQUIRED_PROFILES.associateWith { prof ->
        LayerConfigManager.computeProfileFingerprint(
          prof,
          bundle.persona,
          bundle.style,
          bundle.engine,
          bundle.hardRules,
          2,
        )
      }

    // When unlocked: persona is Dormant, style/engine/hard_rules are Draft
    assertEquals(
      LayerStatus.DORMANT,
      LayerConfigManager.computeLayerStatus("persona", bundle, fps, emptyList()),
    )
    assertEquals(
      LayerStatus.DRAFT,
      LayerConfigManager.computeLayerStatus("style", bundle, fps, emptyList()),
    )
    assertEquals(
      LayerStatus.DRAFT,
      LayerConfigManager.computeLayerStatus("engine", bundle, fps, emptyList()),
    )

    // Log passing runs for all required tests & profiles on current fingerprints -> status becomes TESTED
    val passingRuns =
      listOf(
        LayerTestRunEntity(id = "run-1", testId = "h10_pushback", profile = "balanced", fingerprint = fps["balanced"]!!, passed = true, detailsJson = "{}", timestamp = 10L),
        LayerTestRunEntity(id = "run-2", testId = "h10_pushback", profile = "live", fingerprint = fps["live"]!!, passed = true, detailsJson = "{}", timestamp = 10L),
        LayerTestRunEntity(id = "run-3", testId = "h7_live", profile = "live", fingerprint = fps["live"]!!, passed = true, detailsJson = "{}", timestamp = 10L),
      )
    assertEquals(
      LayerStatus.TESTED,
      LayerConfigManager.computeLayerStatus("style", bundle, fps, passingRuns),
    )
    assertEquals(
      ProfileTestState.PASSED,
      LayerConfigManager.computeProfileTestState("live_typed", fps["live"]!!, bundle.tests, passingRuns),
    )

    // Locked in bundled file without draft -> LOCKED; with draft -> DRAFT
    val lockedEngineRaw =
      unlockedRawFiles.toMutableMap().apply {
        put("engine", unlockedRawFiles["engine"]!!.replace(""""locked": false""", """"locked": true"""))
      }
    val lockedBundle = LayerConfigManager.loadAndValidateBundle(lockedEngineRaw, emptyList()).getOrThrow()
    assertEquals(
      LayerStatus.LOCKED,
      LayerConfigManager.computeLayerStatus("engine", lockedBundle, fps, emptyList()),
    )

    val draftOverride =
      listOf(
        LayerDraftEntity(
          layerKey = "engine",
          jsonContent = unlockedRawFiles["engine"]!!,
          updatedAt = 20L,
        )
      )
    val bundleWithDraft =
      LayerConfigManager.loadAndValidateBundle(lockedEngineRaw, draftOverride).getOrThrow()
    assertEquals(
      LayerStatus.DRAFT,
      LayerConfigManager.computeLayerStatus("engine", bundleWithDraft, fps, emptyList()),
    )

    // Clarification #5: Lock it Copy button copies instruction line + JSON together
    val exported = LayerConfigManager.buildLockExportJson("engine", bundle, "2026-10-31")
    val copyPayload = LayerConfigManager.formatLockCopyPayload("engine.json", exported)
    assertTrue(
      copyPayload.startsWith(
        "Replace app/src/main/assets/config/engine.json with exactly this JSON, verbatim:\n\n{"
      )
    )
    assertTrue(exported.contains(""""locked": true"""))
    assertTrue(exported.contains(""""version": ${bundle.engine.version + 1}"""))
  }

  // 6. Room Migration 1 -> 2 Test
  @Test
  fun migration1To2_turnsExistingChatsIntoLabChatsUntouched() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val dbName = "migration_test_rivia.db"
    context.deleteDatabase(dbName)

    val callback =
      object : SupportSQLiteOpenHelper.Callback(1) {
        override fun onCreate(db: SupportSQLiteDatabase) {
          db.execSQL(
            "CREATE TABLE IF NOT EXISTS `chats` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `runSettingsJson` TEXT NOT NULL, `lastInteractionId` TEXT, PRIMARY KEY(`id`))"
          )
          db.execSQL(
            "CREATE TABLE IF NOT EXISTS `messages` (`id` TEXT NOT NULL, `chatId` TEXT NOT NULL, `role` TEXT NOT NULL, `content` TEXT NOT NULL, `thoughtSummary` TEXT NOT NULL, `requestId` TEXT NOT NULL, `interactionId` TEXT, `previousInteractionId` TEXT, `model` TEXT NOT NULL, `serviceTier` TEXT NOT NULL, `inputTokens` INTEGER NOT NULL, `outputTokens` INTEGER NOT NULL, `thoughtTokens` INTEGER NOT NULL, `ttftMs` INTEGER NOT NULL, `totalTimeMs` INTEGER NOT NULL, `status` TEXT NOT NULL, `errorMessage` TEXT, `voiceLabel` TEXT, `rawStepsJson` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`chatId`) REFERENCES `chats`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
          )
          db.execSQL("CREATE INDEX IF NOT EXISTS `index_messages_chatId` ON `messages` (`chatId`)")
          db.execSQL(
            "CREATE TABLE IF NOT EXISTS `request_logs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp` INTEGER NOT NULL, `requestId` TEXT NOT NULL, `model` TEXT NOT NULL, `status` TEXT NOT NULL, `serviceTier` TEXT NOT NULL, `inputTokens` INTEGER NOT NULL, `outputTokens` INTEGER NOT NULL, `thoughtTokens` INTEGER NOT NULL, `latencyMs` INTEGER NOT NULL, `error` TEXT)"
          )
          db.execSQL(
            "CREATE TABLE IF NOT EXISTS `cached_models` (`id` TEXT NOT NULL, `displayName` TEXT NOT NULL, `description` TEXT NOT NULL, `fetchedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
          )
          db.execSQL(
            "CREATE TABLE IF NOT EXISTS `default_settings` (`id` INTEGER NOT NULL, `runSettingsJson` TEXT NOT NULL, PRIMARY KEY(`id`))"
          )
        }

        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
      }

    val helper =
      FrameworkSQLiteOpenHelperFactory()
        .create(
          SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(callback)
            .build()
        )

    val db = helper.writableDatabase
    db.execSQL(
      """INSERT INTO `chats` (`id`, `title`, `createdAt`, `updatedAt`, `runSettingsJson`, `lastInteractionId`)
         VALUES ('chat-1', 'Existing v1 chat', 1000, 2000, '{"model":"gemini-4-argon"}', 'int-123')"""
    )
    db.execSQL(
      """INSERT INTO `messages` (`id`, `chatId`, `role`, `content`, `thoughtSummary`, `requestId`, `interactionId`, `previousInteractionId`, `model`, `serviceTier`, `inputTokens`, `outputTokens`, `thoughtTokens`, `ttftMs`, `totalTimeMs`, `status`, `errorMessage`, `voiceLabel`, `rawStepsJson`, `createdAt`)
         VALUES ('msg-1', 'chat-1', 'assistant', 'Hello from v1', '', 'r-1111', 'int-123', NULL, 'gemini-4-argon', 'standard', 10, 20, 5, 100, 250, 'completed', NULL, NULL, '[]', 1500)"""
    )

    // Run MIGRATION_1_2
    RiviaDatabase.MIGRATION_1_2.migrate(db)

    // Verify existing chat is now a Lab chat (isLabChat = 1) with its RunSettings untouched
    db.query("SELECT id, title, runSettingsJson, lastInteractionId, isLabChat, riviaProfile, pendingH7Notice FROM chats WHERE id = 'chat-1'").use { cursor ->
      assertTrue(cursor.moveToFirst())
      assertEquals("chat-1", cursor.getString(0))
      assertEquals("Existing v1 chat", cursor.getString(1))
      assertEquals("""{"model":"gemini-4-argon"}""", cursor.getString(2))
      assertEquals("int-123", cursor.getString(3))
      assertEquals(1, cursor.getInt(4)) // Lab chat!
      assertEquals("balanced", cursor.getString(5))
      assertTrue(cursor.isNull(6))
    }

    // Verify existing message is untouched and has v2 columns
    db.query("SELECT id, content, chatKind, channel FROM messages WHERE id = 'msg-1'").use { cursor ->
      assertTrue(cursor.moveToFirst())
      assertEquals("msg-1", cursor.getString(0))
      assertEquals("Hello from v1", cursor.getString(1))
      assertEquals("Lab", cursor.getString(2))
      assertEquals("text", cursor.getString(3))
    }

    helper.close()
    context.deleteDatabase(dbName)
  }

  // 7. Pending lock cannot be created when layer has no passing test runs; Cancel pending lock removes lock and keeps draft
  @Test
  fun pendingLock_cannotBeCreatedWhenLayerNotTested_andCancelPendingLockRemovesLockKeepingDraft() =
    runBlocking {
      val context = ApplicationProvider.getApplicationContext<Context>()
      val db =
        Room.inMemoryDatabaseBuilder(context, RiviaDatabase::class.java)
          .allowMainThreadQueries()
          .build()
      val dao = db.riviaDao()

      try {
        val rawFiles = LayerConfigManager.readBundledAssetFiles(context).getOrThrow()
        val newStyleJson =
          """{"layer":"style","version":1,"locked":false,"updated":"2026-10-31","text":"Terse replies.","voice":"Short voice."}"""
        val draftEntity =
          LayerDraftEntity(layerKey = "style", jsonContent = newStyleJson, updatedAt = 1000L)
        dao.saveLayerDraft(draftEntity)

        val bundleWithDraft =
          LayerConfigManager.loadAndValidateBundle(rawFiles, listOf(draftEntity)).getOrThrow()
        val fps =
          LayerConfigManager.REQUIRED_PROFILES.associateWith { prof ->
            LayerConfigManager.computeProfileFingerprint(
              prof,
              bundleWithDraft.persona,
              bundleWithDraft.style,
              bundleWithDraft.engine,
              bundleWithDraft.hardRules,
              2,
            )
          }

        // 1) A layer with no passing test runs (DRAFT status) cannot create a pending lock
        val rejectedExport =
          LayerConfigManager.tryCreatePendingLock(
            dao = dao,
            layerKey = "style",
            bundle = bundleWithDraft,
            fingerprints = fps,
            testRuns = emptyList(),
            todayDate = "2026-10-31",
          )
        assertNull(rejectedExport)
        assertNull(dao.getLayerLock("style"))

        // 2) Even if a failing test run exists, it still cannot create a pending lock
        val failingRuns =
          listOf(
            LayerTestRunEntity(
              id = "run-fail-1",
              testId = "h10_pushback",
              profile = "balanced",
              fingerprint = fps["balanced"]!!,
              passed = false,
              detailsJson = "{}",
              timestamp = 1500L,
            )
          )
        val rejectedWithFail =
          LayerConfigManager.tryCreatePendingLock(
            dao = dao,
            layerKey = "style",
            bundle = bundleWithDraft,
            fingerprints = fps,
            testRuns = failingRuns,
            todayDate = "2026-10-31",
          )
        assertNull(rejectedWithFail)
        assertNull(dao.getLayerLock("style"))

        // 3) Once the layer is Tested (all required tests passing on current fingerprints), Lock it succeeds
        val passingRuns =
          listOf(
            LayerTestRunEntity(
              id = "run-pass-1",
              testId = "h10_pushback",
              profile = "balanced",
              fingerprint = fps["balanced"]!!,
              passed = true,
              detailsJson = "{}",
              timestamp = 2000L,
            ),
            LayerTestRunEntity(
              id = "run-pass-2",
              testId = "h10_pushback",
              profile = "live",
              fingerprint = fps["live"]!!,
              passed = true,
              detailsJson = "{}",
              timestamp = 2000L,
            ),
            LayerTestRunEntity(
              id = "run-pass-3",
              testId = "h7_live",
              profile = "live",
              fingerprint = fps["live"]!!,
              passed = true,
              detailsJson = "{}",
              timestamp = 2000L,
            ),
          )
        assertEquals(
          LayerStatus.TESTED,
          LayerConfigManager.computeLayerStatus("style", bundleWithDraft, fps, passingRuns),
        )

        val acceptedExport =
          LayerConfigManager.tryCreatePendingLock(
            dao = dao,
            layerKey = "style",
            bundle = bundleWithDraft,
            fingerprints = fps,
            testRuns = passingRuns,
            todayDate = "2026-10-31",
          )
        assertNotNull(acceptedExport)
        val pendingLock = dao.getLayerLock("style")
        assertNotNull(pendingLock)
        assertNotNull(pendingLock!!.pendingCanonicalSha256)

        // 4) Cancel pending lock deletes the pending lock record and leaves the draft alone
        LayerConfigManager.cancelPendingLock(dao, "style")
        assertNull(dao.getLayerLock("style"))
        val remainingDraft = dao.getLayerDraft("style")
        assertNotNull(remainingDraft)
        assertEquals(newStyleJson, remainingDraft!!.jsonContent)
      } finally {
        db.close()
      }
    }

  // 8. Specific H9 Empty Reply Reasons
  @Test
  fun h9EmptyReplyReason_formatsSpecificReasonsNeverJustIncomplete() {
    // 1) Output tokens ran out and thought tokens were reported
    assertEquals(
      "No reply: max output tokens used by thinking (512 of 512)",
      LayerConfigManager.formatH9EmptyReplyReason(
        finishReason = "MAX_TOKENS",
        safetyBlockReason = null,
        completionStatus = "incomplete",
        thoughtTokens = 512,
        outputTokens = 0,
        configuredMaxOutputTokens = 512,
      ),
    )

    // 2) Output tokens ran out otherwise (no thought tokens)
    assertEquals(
      "No reply: max output tokens reached",
      LayerConfigManager.formatH9EmptyReplyReason(
        finishReason = "max_output_tokens",
        safetyBlockReason = null,
        completionStatus = "incomplete",
        thoughtTokens = 0,
        outputTokens = 512,
        configuredMaxOutputTokens = 512,
      ),
    )

    // 3) Safety block with category
    assertEquals(
      "No reply: blocked for safety (HARM_CATEGORY_HATE_SPEECH)",
      LayerConfigManager.formatH9EmptyReplyReason(
        finishReason = "SAFETY",
        safetyBlockReason = "HARM_CATEGORY_HATE_SPEECH",
        completionStatus = "completed",
        thoughtTokens = 0,
        outputTokens = 0,
        configuredMaxOutputTokens = 512,
      ),
    )

    // 4) Anything else: empty completion plus API status or reason if provided
    assertEquals(
      "No reply: empty completion (recitation)",
      LayerConfigManager.formatH9EmptyReplyReason(
        finishReason = "recitation",
        safetyBlockReason = null,
        completionStatus = "completed",
        thoughtTokens = 0,
        outputTokens = 0,
        configuredMaxOutputTokens = null,
      ),
    )
    assertEquals(
      "No reply: empty completion",
      LayerConfigManager.formatH9EmptyReplyReason(
        finishReason = null,
        safetyBlockReason = null,
        completionStatus = "completed",
        thoughtTokens = 0,
        outputTokens = 0,
        configuredMaxOutputTokens = null,
      ),
    )
  }

  // 9. Status Colors Rendering Test across Rivia (#F5F5F5), Dev (#A78BFA), and Labs (#60A5FA) themes
  @Test
  fun statusColors_renderRedAndAmberAcrossAllThreeModeThemes() {
    val expectedRed = Color(0xFFFF6B6B)
    val expectedAmber = Color(0xFFF2C14E)
    val expectedModeAccents =
      mapOf(
        AppUiMode.RIVIA to Color(0xFFF5F5F5),
        AppUiMode.DEV to Color(0xFFA78BFA),
        AppUiMode.LABS to Color(0xFF60A5FA),
      )

    var activeMode by mutableStateOf(AppUiMode.RIVIA)

    val fingerprints =
      mapOf(
        "fast" to "11111111",
        "balanced" to "22222222",
        "deep" to "33333333",
        "live" to "44444444",
      )
    val testStates =
      mapOf(
        "fast" to ProfileTestState.NOT_RUN,
        "balanced" to ProfileTestState.FAILED,
        "deep" to ProfileTestState.PASSED,
        "live" to ProfileTestState.NOT_RUN,
      )
    val h9Message =
      MessageEntity(
        id = "msg-h9",
        chatId = "chat-1",
        role = "assistant",
        content = "",
        thoughtSummary = "",
        requestId = "r-h9",
        model = "gemini-3.8-flash",
        serviceTier = "standard",
        status = "error",
        errorMessage = "No reply: empty completion",
        createdAt = 1000L,
      )
    val failedStepResult =
      StepTestVerificationResult(
        testId = "S0_T1",
        passed = false,
        headline = "Check failed",
        details = "Simulated failure",
        timestampMs = 1000L,
      )

    composeTestRule.setContent {
      RiviaTheme(appUiMode = activeMode) {
        Column {
          TopFingerprintSummaryBar(
            fingerprints = fingerprints,
            testStates = testStates,
          )
          AssistantMessageBubble(
            message = h9Message,
            isLastAssistantReply = true,
            isSpeaking = false,
            isStreaming = false,
            onListen = {},
            onRegenerate = {},
            onShowDetails = {},
          )
          StepTestCard(
            spec = STEP_0_AND_1_TEST_SPECS.first(),
            verificationResult = failedStepResult,
            isRunning = false,
            onRunVerification = {},
            onApplyAndLaunchLive = {},
          )
        }
      }
    }

    fun renderedTextColorForTag(tag: String): Color {
      val node = composeTestRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
      val layoutResults = mutableListOf<TextLayoutResult>()
      val gotLayout =
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layoutResults) ?: false
      assertTrue("Expected TextLayoutResult for tag=$tag", gotLayout && layoutResults.isNotEmpty())
      return layoutResults.first().layoutInput.style.color
    }

    for (mode in listOf(AppUiMode.RIVIA, AppUiMode.DEV, AppUiMode.LABS)) {
      activeMode = mode
      composeTestRule.waitForIdle()

      // Verify the mode theme accent itself shifted to the mode's signature color (#F5F5F5, #A78BFA, #60A5FA)
      assertEquals(
        "Passed profile dot should match mode accent in $mode",
        expectedModeAccents.getValue(mode),
        renderedTextColorForTag("profile_dot_deep"),
      )

      // 1) Profile test dots: NOT_RUN ("not tested") stays amber #F2C14E, FAILED stays red #FF6B6B
      assertEquals(
        "Not-tested profile dot (fast) must be #F2C14E in $mode",
        expectedAmber,
        renderedTextColorForTag("profile_dot_fast"),
      )
      assertEquals(
        "Not-tested profile dot (live) must be #F2C14E in $mode",
        expectedAmber,
        renderedTextColorForTag("profile_dot_live"),
      )
      assertEquals(
        "Failed profile dot (balanced) must be #FF6B6B in $mode",
        expectedRed,
        renderedTextColorForTag("profile_dot_balanced"),
      )

      // 2) H9 "No reply" notice stays amber #F2C14E
      assertEquals(
        "H9 No reply notice must be #F2C14E in $mode",
        expectedAmber,
        renderedTextColorForTag("h9_no_reply_notice"),
      )

      // 3) Failed test badge stays red #FF6B6B
      assertEquals(
        "Failed test badge must be #FF6B6B in $mode",
        expectedRed,
        renderedTextColorForTag("step_test_badge_S0_T1"),
      )
    }
  }

  // 10. Mode Accents Test: Switch, Primary Button, and Selected Tab Indicator across Rivia (#F5F5F5), Dev (#A78BFA), and Labs (#60A5FA)
  @Test
  fun modeAccents_renderSwitchPrimaryButtonAndTabIndicatorAcrossAllThreeModes() {
    val expectedModeAccents =
      mapOf(
        AppUiMode.RIVIA to Color(0xFFF5F5F5),
        AppUiMode.DEV to Color(0xFFA78BFA),
        AppUiMode.LABS to Color(0xFF60A5FA),
      )

    var activeMode by mutableStateOf(AppUiMode.RIVIA)
    var renderedSwitchTrackColor = Color.Unspecified
    var renderedSwitchThumbColor = Color.Unspecified
    var renderedSwitchUncheckedTrackColor = Color.Unspecified
    var renderedSwitchUncheckedThumbColor = Color.Unspecified
    var renderedPrimaryButtonContainerColor = Color.Unspecified
    var renderedPrimaryButtonContentColor = Color.Unspecified
    var renderedTabIndicatorColor = Color.Unspecified

    composeTestRule.setContent {
      RiviaTheme(appUiMode = activeMode) {
        val switchColors = riviaSwitchColors()
        renderedSwitchTrackColor = switchColors.checkedTrackColor
        renderedSwitchThumbColor = switchColors.checkedThumbColor
        renderedSwitchUncheckedTrackColor = switchColors.uncheckedTrackColor
        renderedSwitchUncheckedThumbColor = switchColors.uncheckedThumbColor
        renderedPrimaryButtonContainerColor = RiviaMint
        renderedPrimaryButtonContentColor = RiviaOnAccent
        renderedTabIndicatorColor = RiviaMint

        Column {
          Switch(
            checked = true,
            onCheckedChange = {},
            colors = switchColors,
            modifier = Modifier.testTag("mode_accent_switch"),
          )
          Text(
            text = "switch_track_probe",
            color = switchColors.checkedTrackColor,
            modifier = Modifier.testTag("switch_track_color_probe"),
          )

          Button(
            onClick = {},
            colors =
              ButtonDefaults.buttonColors(
                containerColor = RiviaMint,
                contentColor = RiviaOnAccent,
              ),
            modifier = Modifier.testTag("mode_accent_primary_button"),
          ) {
            Text(
              text = "Primary Action",
              color = RiviaMint,
              modifier = Modifier.testTag("primary_button_accent_probe"),
            )
          }

          TabRow(
            selectedTabIndex = 0,
            containerColor = RiviaSurface1,
            contentColor = RiviaTextPrimary,
            indicator = { tabPositions ->
              if (tabPositions.isNotEmpty()) {
                val indicatorColor = RiviaMint
                TabRowDefaults.SecondaryIndicator(
                  modifier =
                    Modifier.tabIndicatorOffset(tabPositions[0])
                      .testTag("mode_accent_tab_indicator"),
                  color = indicatorColor,
                )
                Text(
                  text = "tab_indicator_probe",
                  color = indicatorColor,
                  modifier = Modifier.testTag("tab_indicator_color_probe"),
                )
              }
            },
          ) {
            Tab(
              selected = true,
              onClick = {},
              text = { Text("Voice", color = RiviaTextPrimary) },
            )
            Tab(
              selected = false,
              onClick = {},
              text = { Text("Backup") },
            )
          }
        }
      }
    }

    fun renderedTextColorForTag(tag: String): Color {
      val node = composeTestRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
      val layoutResults = mutableListOf<TextLayoutResult>()
      val gotLayout =
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layoutResults) ?: false
      assertTrue("Expected TextLayoutResult for tag=$tag", gotLayout && layoutResults.isNotEmpty())
      return layoutResults.first().layoutInput.style.color
    }

    for (mode in listOf(AppUiMode.RIVIA, AppUiMode.DEV, AppUiMode.LABS)) {
      activeMode = mode
      composeTestRule.waitForIdle()

      val expectedAccent = expectedModeAccents.getValue(mode)

      // 1) Switch rendered & checked track matches mode accent; thumb is #000000; off track #262626; off thumb #8F8F8F
      composeTestRule.onNodeWithTag("mode_accent_switch").fetchSemanticsNode()
      assertEquals(
        "Switch checked track color must match mode accent in $mode",
        expectedAccent,
        renderedSwitchTrackColor,
      )
      assertEquals(
        "Switch checked track probe must render mode accent in $mode",
        expectedAccent,
        renderedTextColorForTag("switch_track_color_probe"),
      )
      assertEquals(
        "Switch checked thumb color must be #000000 in $mode",
        Color(0xFF000000),
        renderedSwitchThumbColor,
      )
      assertEquals(
        "Switch unchecked track color must be #262626 in $mode",
        Color(0xFF262626),
        renderedSwitchUncheckedTrackColor,
      )
      assertEquals(
        "Switch unchecked thumb color must be #8F8F8F in $mode",
        Color(0xFF8F8F8F),
        renderedSwitchUncheckedThumbColor,
      )

      // 2) Primary button rendered & uses mode accent with #000000 on-accent content
      composeTestRule.onNodeWithTag("mode_accent_primary_button").fetchSemanticsNode()
      assertEquals(
        "Primary button container color must match mode accent in $mode",
        expectedAccent,
        renderedPrimaryButtonContainerColor,
      )
      assertEquals(
        "Primary button content color must be #000000 in $mode",
        Color(0xFF000000),
        renderedPrimaryButtonContentColor,
      )
      assertEquals(
        "Primary button accent probe must render mode accent in $mode",
        expectedAccent,
        renderedTextColorForTag("primary_button_accent_probe"),
      )

      // 3) Selected tab indicator rendered & uses mode accent
      composeTestRule.onNodeWithTag("mode_accent_tab_indicator", useUnmergedTree = true).fetchSemanticsNode()
      assertEquals(
        "Selected tab indicator color must match mode accent in $mode",
        expectedAccent,
        renderedTabIndicatorColor,
      )
      assertEquals(
        "Selected tab indicator probe must render mode accent in $mode",
        expectedAccent,
        renderedTextColorForTag("tab_indicator_color_probe"),
      )
    }
  }

  // 11. Bots Pass 1: 5-Point Spoken-Line Verifier, Whole-Word Check 5, Answer Prompt, and Earlier-Told Note
  @Test
  fun botReplyVerifier_enforcesAllFiveChecksAndWholeWordForbiddenMatch() {
    val sampleReplyJson =
      """
      {
        "v": 1,
        "kind": "arrival",
        "ok": true,
        "ref": "t-GZUR8umpT55Qf2cv",
        "message": {
          "situation": "gym_late_cut",
          "facts": {
            "place": { "id": "gym", "name": "the gym" },
            "leave_for_work_at": { "epoch": 1794246300, "local": "12:45 PM", "spoken": "12:45", "iso": "2026-11-09T12:45:00-05:00" },
            "due_at_work": { "epoch": 1794246600, "local": "12:50 PM", "spoken": "12:50", "iso": "2026-11-09T12:50:00-05:00" },
            "late_minutes": 25,
            "prework_minutes": 15,
            "workout_minutes": 110,
            "day": "Tue Nov 10",
            "problems": ["Not enough morning: up at 7:10 but leaving at 7:55 leaves under 60 minutes to get ready."]
          },
          "must_say": ["late_minutes", "prework_minutes", "workout_minutes", "leave_for_work_at"],
          "decisions_for_jordan": [],
          "delivery": { "say": true, "urgency": "normal", "hold_ok": true, "max_spoken_sentences": 2 },
          "emotion": { "tone": "concerned", "intensity": 0.5, "note": "He is behind but it is recoverable." }
        },
        "data": {},
        "fallback_text": "You're 25 minutes behind at the gym. Leave for work by 12:45."
      }
      """.trimIndent()

    val envelope = com.example.util.BotReplyVerifier.parseReplyEnvelope(sampleReplyJson)
    assertNotNull(envelope)
    val msg = envelope!!.message!!

    // Exact word-for-word answer-turn prompt
    val prompt = com.example.util.BotReplyVerifier.buildAnswerTurnPrompt(msg)
    assertTrue(
      prompt.startsWith(
        "The job Jordan asked for earlier is done. Tell him the result as yourself, in first person; never mention bots, Ard, tickets or tools. Write one spoken line from this message only. Use only the facts given. Say every must_say fact. Say times exactly as their spoken value. Never compute a time. Say a day in full, like Wednesday, November 11. Respect max_spoken_sentences. Offer decisions_for_jordan; never choose one. Use a plain, even tone. No audio tags and no square brackets. Message: "
      )
    )

    // Valid line passes all 5 checks — and words "hard", "card", "regard" pass Check 5 whole-word match
    val validLine =
      "[sighs] You're 25 minutes behind, so with a hard 110 minute workout and 15 minutes of prework, regard 12:45 as your cutoff and bring your card."
    val passResult = com.example.util.BotReplyVerifier.verifySpokenLine(validLine, msg)
    assertTrue("Expected valid line to pass, got ${passResult.failedCheck}: ${passResult.detail}", passResult.passed)

    // Empty line check
    val emptyRes = com.example.util.BotReplyVerifier.verifySpokenLine("  [sighs]  ", msg)
    assertFalse(emptyRes.passed)
    assertEquals("empty_line", emptyRes.failedCheck)

    // Check 1: clock time (\d{1,2}:\d{2}) must equal spoken value of some time fact in message.facts
    val badClockRes =
      com.example.util.BotReplyVerifier.verifySpokenLine(
        "You're 25 minutes behind with 110 workout and 15 prework, so leave at 12:30.",
        msg,
      )
    assertFalse(badClockRes.passed)
    assertTrue(badClockRes.failedCheck!!.startsWith("check_1_clock_time"))

    // Check 2: missing must_say fact (missing 110 workout_minutes)
    val missingMustSayRes =
      com.example.util.BotReplyVerifier.verifySpokenLine(
        "You're 25 minutes behind with 15 minutes prework, so leave by 12:45.",
        msg,
      )
    assertFalse(missingMustSayRes.passed)
    assertTrue(missingMustSayRes.failedCheck!!.startsWith("check_2_must_say"))

    // Check 3: ungrounded duration number (99 minutes not in facts)
    val ungroundedRes =
      com.example.util.BotReplyVerifier.verifySpokenLine(
        "You're 25 minutes behind with 110 workout and 15 prework, so leave at 12:45 or 99 minutes.",
        msg,
      )
    assertFalse(ungroundedRes.passed)
    assertTrue(ungroundedRes.failedCheck!!.startsWith("check_3_ungrounded_duration"))

    // Check 4: max_spoken_sentences (max_spoken_sentences = 2)
    val threeSentences =
      "You're 25 minutes late. Your workout is 110 minutes with 15 minutes prework. Leave at 12:45."
    val capRes = com.example.util.BotReplyVerifier.verifySpokenLine(threeSentences, msg)
    assertFalse(capRes.passed)
    assertTrue(capRes.failedCheck!!.startsWith("check_4_max_sentences"))

    // Check 5: whole words only ("bot", "bots", "ticket", "tickets", "ard")
    for (forbidden in listOf("bot", "Bots", "ticket", "TICKETS", "Ard")) {
      val lineWithForbidden =
        "You're 25 minutes behind ($forbidden) with 110 workout and 15 prework, leave by 12:45."
      val check5Res = com.example.util.BotReplyVerifier.verifySpokenLine(lineWithForbidden, msg)
      assertFalse("Expected '$forbidden' to fail Check 5", check5Res.passed)
      assertTrue(check5Res.failedCheck!!.startsWith("check_5_forbidden_word"))
    }

    // Earlier-told note formatting
    assertEquals(
      """(Earlier you told Jordan: "I couldn't finish that." "I couldn't do that.")""",
      com.example.util.BotReplyVerifier.formatEarlierToldNote(
        listOf("I couldn't finish that.", "I couldn't do that.")
      ),
    )
  }
}

