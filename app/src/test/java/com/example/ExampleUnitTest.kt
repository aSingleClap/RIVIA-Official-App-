package com.example

import com.example.model.RunSettings
import com.example.security.SecureKeyRepository
import com.example.util.TextAudioTagProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
    val speakableWithTags = TextAudioTagProcessor.toSpeakableText(md, sendAudioTags = true)
    assertTrue(speakableWithTags.contains("[warmly]"))
    assertTrue(speakableWithTags.contains("Check this guide or link"))
    assertTrue(speakableWithTags.contains("The code is on screen."))
    assertFalse(speakableWithTags.contains("https://"))

    val speakableWithoutTags = TextAudioTagProcessor.toSpeakableText(md, sendAudioTags = false)
    assertFalse(speakableWithoutTags.contains("[warmly]"))
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
