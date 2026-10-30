package com.example

import com.example.data.MessageEntity
import com.example.model.RunSettings
import com.example.network.GeminiInteractionsClient
import com.example.plugin.RiviaChannel
import com.example.security.SecureKeyRepository
import com.example.util.TextAudioTagProcessor
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ExampleUnitTest {
  @Test
  fun audioTagFilter_stripsValidTagsAndPreservesCodeLinksAndArrays() {
    val input =
      "[softly] Hello [docs](https://example.com) and `[softly]` with [x] and [1, 2]!"
    val stripped = TextAudioTagProcessor.stripAudioTagsForScreen(input)
    assertEquals(
      "Hello [docs](https://example.com) and `[softly]` with [x] and [1, 2]!",
      stripped,
    )
  }

  @Test
  fun speakableText_transformsMarkdownCodeAndBareUrls() {
    val md =
      "[warmly] Check [this guide](https://example.com/guide) or https://openai.com/test\n```kotlin\nval x = 1\n```"
    val speakableWithTags = TextAudioTagProcessor.toSpeakableText(md, keepAudioTags = true)
    assertTrue(speakableWithTags.contains("[warmly]"))
    assertTrue(speakableWithTags.contains("Check this guide or link"))
    assertTrue(speakableWithTags.contains("The code is on screen."))
    assertFalse(speakableWithTags.contains("https://"))

    val speakableWithoutTags = TextAudioTagProcessor.toSpeakableText(md, keepAudioTags = false)
    assertFalse(speakableWithoutTags.contains("[warmly]"))
  }

  @Test
  fun modelSupportsAudioTags_onlyAllowsV3AndV4AndV4Turbo() {
    assertTrue(TextAudioTagProcessor.modelSupportsAudioTags("eleven_v4"))
    assertTrue(TextAudioTagProcessor.modelSupportsAudioTags("eleven_v4_turbo"))
    assertTrue(TextAudioTagProcessor.modelSupportsAudioTags("eleven_v3"))
    assertTrue(TextAudioTagProcessor.modelSupportsAudioTags("eleven_v3_alpha"))
    assertFalse(TextAudioTagProcessor.modelSupportsAudioTags("eleven_flash_v2_5"))
    assertFalse(TextAudioTagProcessor.modelSupportsAudioTags("eleven_multilingual_v2"))
  }

  @Test
  fun splitIntoSentences_preservesDecimalNumbersLikeThreePointFive() {
    val text = "Version 3.5 is out now. Does it cost $9.99? Yes!"
    val sentences = TextAudioTagProcessor.splitIntoSentences(text)
    assertEquals(
      listOf("Version 3.5 is out now.", "Does it cost $9.99?", "Yes!"),
      sentences,
    )
  }

  @Test
  fun thinkingLevelsAndSampling_followModelRules() {
    assertEquals(
      listOf("model_default", "low", "medium", "high"),
      RunSettings.allowedThinkingLevels("gemini-4-argon"),
    )
    assertEquals(
      listOf("model_default", "minimal", "low", "medium", "high"),
      RunSettings.allowedThinkingLevels("gemini-3.5-flash-lite"),
    )
    assertFalse(RunSettings.isSamplingSupported("gemini-3.8-flash"))
    assertFalse(RunSettings.isSamplingSupported("gemini-4-argon"))
    assertTrue(RunSettings.isSamplingSupported("gemini-3.5-flash-lite"))
  }

  @Test
  fun requestBody_followsSafetyToolsServiceTierAndLiveStreamRules() {
    val client = GeminiInteractionsClient()
    val settings =
      RunSettings(
        model = "gemini-3.8-flash",
        liveModel = "gemini-3.5-flash-lite",
        thinkingLevel = "medium",
        liveThinkingLevel = "minimal",
        googleSearchEnabled = true,
        googleSearchMode = "image",
        fileSearchEnabled = true,
        fileSearchStoreNames = listOf("store_alpha"),
        safetyHateSpeech = "block_only_high",
        safetyMethod = "severity",
        serviceTier = "standard",
        store = true,
        background = true,
      )

    // Typed chat request with standard service_tier
    val typedBody =
      client.buildRequestBodyJson(
        settings = settings,
        channel = RiviaChannel.TYPED_CHAT,
        inputSteps = client.buildSingleTurnInput("Hello"),
        previousInteractionId = null,
        stream = true,
      )
    assertFalse("standard service_tier must be omitted", typedBody.has("service_tier"))
    assertEquals(
      "medium",
      typedBody.getJSONObject("generation_config").getString("thinking_level"),
    )

    val safetyArr = typedBody.getJSONArray("safety_settings")
    assertEquals(1, safetyArr.length())
    val hateObj = safetyArr.getJSONObject(0)
    assertEquals("hate_speech", hateObj.getString("type"))
    assertFalse(hateObj.has("category"))
    assertEquals("block_only_high", hateObj.getString("threshold"))
    assertEquals("severity", hateObj.getString("method"))

    val toolsArr: JSONArray = typedBody.getJSONArray("tools")
    val googleSearchTool = toolsArr.getJSONObject(0)
    assertEquals("google_search", googleSearchTool.getString("type"))
    assertFalse(googleSearchTool.has("mode"))
    assertEquals("image_search", googleSearchTool.getJSONArray("search_types").getString(0))

    val fileSearchTool = toolsArr.getJSONObject(1)
    assertEquals("file_search", fileSearchTool.getString("type"))
    assertFalse(fileSearchTool.has("store_names"))
    assertEquals(
      "store_alpha",
      fileSearchTool.getJSONArray("file_search_store_names").getString(0),
    )

    // Live request: uses liveThinkingLevel, ignores background, always streams, includes flex tier
    val liveBody =
      client.buildRequestBodyJson(
        settings = settings.copy(serviceTier = "flex"),
        channel = RiviaChannel.LIVE_VOICE,
        inputSteps = client.buildSingleTurnInput("Hi Rivia"),
        previousInteractionId = null,
        stream = true,
      )
    assertEquals("flex", liveBody.getString("service_tier"))
    assertEquals(true, liveBody.getBoolean("stream"))
    assertFalse(liveBody.has("background"))
    assertEquals(
      "minimal",
      liveBody.getJSONObject("generation_config").getString("thinking_level"),
    )

    // When gemini-3.8-flash is used with minimal thinking, it clamps to low instead of dropping thinking_level
    val flash38MinimalBody =
      client.buildRequestBodyJson(
        settings = settings.copy(liveModel = "gemini-3.8-flash", liveThinkingLevel = "minimal"),
        channel = RiviaChannel.LIVE_VOICE,
        inputSteps = client.buildSingleTurnInput("Hello"),
        previousInteractionId = null,
        stream = true,
      )
    assertEquals(
      "low",
      flash38MinimalBody.getJSONObject("generation_config").getString("thinking_level"),
    )
  }

  @Test
  fun buildFullConversationSteps_stripsTransientSseMetadataFromRawSteps() {
    val client = GeminiInteractionsClient()
    val rawStepWithSseMeta =
      """[{"type":"model_output","content":[{"type":"text","text":"Hi!"}],"index":1,"event_type":"step.stop","event_id":"ev_1"}]"""
    val prior =
      listOf(
        MessageEntity(
          id = "m1",
          chatId = "c1",
          role = "user",
          content = "Hello",
          requestId = "r-1111",
          previousInteractionId = null,
          model = "gemini-3.8-flash",
          serviceTier = "standard",
          status = "completed",
          createdAt = 1L,
        ),
        MessageEntity(
          id = "m2",
          chatId = "c1",
          role = "assistant",
          content = "Hi!",
          requestId = "r-1111",
          previousInteractionId = null,
          model = "gemini-3.8-flash",
          serviceTier = "standard",
          status = "completed",
          rawStepsJson = rawStepWithSseMeta,
          createdAt = 2L,
        ),
      )
    val steps = client.buildFullConversationSteps(prior, "Next")
    assertEquals(3, steps.length())
    val assistantStep = steps.getJSONObject(1)
    assertFalse(assistantStep.has("event_type"))
    assertFalse(assistantStep.has("event_id"))
    assertFalse(assistantStep.has("index"))
  }

  @Test
  fun keyRedaction_redactsBothKeysPlainly() {
    val raw = "Key1=my_secret_gemini_12345 and Key2=my_secret_eleven_67890"
    val redacted =
      SecureKeyRepository.redactText(raw, "my_secret_gemini_12345", "my_secret_eleven_67890")
    assertFalse(redacted.contains("my_secret_gemini_12345"))
    assertFalse(redacted.contains("my_secret_eleven_67890"))
    assertTrue(redacted.contains("[REDACTED_GEMINI_KEY]"))
    assertTrue(redacted.contains("[REDACTED_ELEVENLABS_KEY]"))
  }
}
