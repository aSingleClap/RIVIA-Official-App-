package com.example.util

import java.security.SecureRandom

object TextAudioTagProcessor {
  // Audio-tag rule: only lowercase letters and spaces, 3 to 30 characters, inside square brackets
  // Never followed by (, [ or : so markdown links [text](url), [text][ref], [note]: stay intact.
  private val AUDIO_TAG_REGEX = Regex("""\[[a-z ]{3,30}\](?![(\[:])""")

  // Matches fenced code blocks ```...``` or inline code spans `...`
  private val CODE_SEGMENT_REGEX = Regex("""(```[\s\S]*?```|`[^`\n]+`)""")

  /**
   * Generates a 4-hex-character request ID like "r-8c41".
   */
  fun generateRequestId(): String {
    val bytes = ByteArray(2)
    SecureRandom().nextBytes(bytes)
    return "r-%02x%02x".format(bytes[0], bytes[1])
  }

  /**
   * Strips valid audio tags like [softly] or [laughs] for on-screen rendering,
   * while leaving code blocks, inline code spans, markdown links, checkboxes, and arrays intact.
   */
  fun stripAudioTagsForScreen(input: String): String {
    if (input.isEmpty()) return input
    val sb = StringBuilder()
    var lastIndex = 0
    for (match in CODE_SEGMENT_REGEX.findAll(input)) {
      if (match.range.first > lastIndex) {
        val outsideCode = input.substring(lastIndex, match.range.first)
        sb.append(removeAudioTagsFromPlainSegment(outsideCode))
      }
      // Keep code segment completely untouched
      sb.append(match.value)
      lastIndex = match.range.last + 1
    }
    if (lastIndex < input.length) {
      sb.append(removeAudioTagsFromPlainSegment(input.substring(lastIndex)))
    }
    return sb.toString()
  }

  private fun removeAudioTagsFromPlainSegment(segment: String): String {
    // Remove [tag] and up to one immediately following space if it was separating tag from word
    return segment.replace(Regex("""\[[a-z ]{3,30}\](?![(\[:]) ?"""), "")
  }

  /**
   * Converts markdown text into speakable text for ElevenLabs read-aloud:
   * - Replaces fenced code blocks with "The code is on screen."
   * - Replaces markdown tables with "The table is on screen."
   * - Reads link text instead of URLs for [label](url)
   * - Says "link" for bare URLs (https?://...)
   * - Drops markdown formatting marks
   * - Keeps audio tags unless sendAudioTags is false
   */
  fun toSpeakableText(markdown: String, sendAudioTags: Boolean): String {
    if (markdown.isBlank()) return ""

    var text = markdown

    // 1. Replace fenced code blocks with spoken note
    text =
      Regex("""```[\s\S]*?```""").replace(text) {
        " The code is on screen. "
      }

    // 2. Replace markdown tables (2 or more consecutive pipe-delimited lines) with spoken note
    text =
      Regex("""(?:^[ \t]*\|.+\|[ \t]*$\r?\n?){2,}""", RegexOption.MULTILINE).replace(text) {
        " The table is on screen. "
      }

    // 3. Inline code spans: strip backticks
    text = Regex("""`([^`\n]+)`""").replace(text, "$1")

    // 4. Markdown links [label](url) -> label
    text = Regex("""\[([^\]]+)\]\((https?://[^\s)]+|[^\s)]+)\)""").replace(text, "$1")

    // 5. Bare URLs -> "link"
    text = Regex("""https?://\S+""").replace(text, "link")

    // 6. Strip or keep audio tags
    if (!sendAudioTags) {
      text = AUDIO_TAG_REGEX.replace(text, "")
    }

    // 7. Strip markdown formatting marks (headings, bold, italic, strikethrough, blockquotes, list bullets)
    text = Regex("""(?m)^[ \t]*#{1,6}[ \t]+""").replace(text, "")
    text = Regex("""(?m)^[ \t]*>[ \t]*""").replace(text, "")
    text = Regex("""(?m)^[ \t]*[-*•][ \t]+""").replace(text, "")
    text = text.replace("**", "").replace("__", "").replace("~~", "")
    // Single * or _ around words
    text = Regex("""(?<!\w)\*([^*\n]+)\*(?!\w)""").replace(text, "$1")
    text = Regex("""(?<!\w)_([^_\n]+)_(?!\w)""").replace(text, "$1")

    // Normalize whitespace
    text = text.replace(Regex("""[ \t]+"""), " ").replace(Regex("""\n{2,}"""), "\n").trim()
    return text
  }

  /**
   * Splits speakable text into ordered sentences for streaming chunk playback with previous_text.
   * Ensures a trailing audio tag isn't orphaned at the end of the previous sentence.
   */
  fun splitIntoSentences(speakableText: String): List<String> {
    if (speakableText.isBlank()) return emptyList()
    val results = mutableListOf<String>()
    // Split after sentence-ending punctuation (. ! ?) followed by whitespace/newline, or on newlines
    val rawParts = speakableText.split(Regex("""(?<=[.!?])\s+|\n+"""))
    var carryOverTag = ""
    for (part in rawParts) {
      val trimmed = (if (carryOverTag.isNotEmpty()) "$carryOverTag $part" else part).trim()
      carryOverTag = ""
      if (trimmed.isEmpty()) continue
      // If a part is ONLY an audio tag, carry it over to the next sentence
      if (AUDIO_TAG_REGEX.matches(trimmed)) {
        carryOverTag = trimmed
      } else {
        results.add(trimmed)
      }
    }
    if (carryOverTag.isNotEmpty()) {
      if (results.isNotEmpty()) {
        results[results.lastIndex] = "${results.last()} $carryOverTag"
      } else {
        results.add(carryOverTag)
      }
    }
    return results
  }

  /**
   * Computes a chat title from the first 6 words of the user's first message.
   */
  fun computeChatTitle(firstMessage: String): String {
    val clean = stripAudioTagsForScreen(firstMessage).trim()
    val words = clean.split(Regex("""\s+""")).filter { it.isNotBlank() }
    if (words.isEmpty()) return "New chat"
    return words.take(6).joinToString(" ")
  }
}
