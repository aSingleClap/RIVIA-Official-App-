package com.example.util

import java.security.SecureRandom

object TextAudioTagProcessor {
  // Audio-tag rule: only lowercase letters and spaces, 3 to 30 characters, inside square brackets
  // Never followed by (, [ or : so markdown links [text](url), [text][ref], [note]: stay intact.
  private val AUDIO_TAG_REGEX = Regex("""\[[a-z ]{3,30}\](?![(\[:])""")

  // Matches fenced code blocks ```...``` or inline code spans `...`
  private val CODE_SEGMENT_REGEX = Regex("""(```[\s\S]*?```|`[^`\n]+`)""")

  // Abbreviations that never end a sentence
  private val NO_SPLIT_ABBREVIATIONS = setOf("dr.", "mr.", "mrs.", "ms.", "st.")

  /**
   * Fix #9: Only send audio tags to eleven_v3 models, eleven_v4 and eleven_v4_turbo.
   * Strip them for every other model, including the eleven_flash_v2_5 fallback.
   */
  fun modelSupportsAudioTags(modelId: String): Boolean {
    val clean = modelId.trim().lowercase()
    return clean.startsWith("eleven_v3") || clean == "eleven_v4" || clean == "eleven_v4_turbo"
  }

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
    return sb.toString().trim()
  }

  fun removeAudioTagsFromPlainSegment(segment: String): String {
    return segment.replace(Regex("""\[[a-z ]{3,30}\](?![(\[:]) ?"""), "")
  }

  /**
   * Converts markdown text into speakable text for ElevenLabs read-aloud:
   * - Replaces fenced code blocks with "The code is on screen."
   * - Replaces markdown tables with "The table is on screen."
   * - Reads link text instead of URLs for [label](url)
   * - Says "link" for bare URLs (https?://...)
   * - Drops markdown formatting marks
   * - Keeps audio tags only when keepAudioTags is true
   */
  fun toSpeakableText(markdown: String, keepAudioTags: Boolean): String {
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
    if (!keepAudioTags) {
      text = Regex("""\[[a-z ]{3,30}\](?![(\[:]) ?""").replace(text, "")
    }

    // 7. Strip markdown formatting marks (headings, bold, italic, strikethrough, blockquotes, list bullets)
    text = Regex("""(?m)^[ \t]*#{1,6}[ \t]+""").replace(text, "")
    text = Regex("""(?m)^[ \t]*>[ \t]*""").replace(text, "")
    text = Regex("""(?m)^[ \t]*[-*•][ \t]+""").replace(text, "")
    text = text.replace("**", "").replace("__", "").replace("~~", "")
    text = Regex("""(?<!\w)\*([^*\n]+)\*(?!\w)""").replace(text, "$1")
    text = Regex("""(?<!\w)_([^_\n]+)_(?!\w)""").replace(text, "$1")

    // Normalize whitespace
    text = text.replace(Regex("""[ \t]+"""), " ").replace(Regex("""\n{2,}"""), "\n").trim()
    return text
  }

  /**
   * Returns true if a fragment consists solely of one or more [audio tags] and whitespace.
   */
  fun isOnlyAudioTags(fragment: String): Boolean {
    val trimmed = fragment.trim()
    if (trimmed.isEmpty()) return false
    return AUDIO_TAG_REGEX.replace(trimmed, "").isBlank()
  }

  /**
   * Splits speakable text into ordered sentences for ElevenLabs playback and H7 sentence counting:
   * - "..." and "…" never end a sentence.
   * - "Dr.", "Mr.", "Mrs.", "Ms." and "St." never end a sentence.
   * - "a.m." and "p.m." end a sentence only when the next word starts with a capital letter.
   * - Decimals (3.5) and times (12.20) never split.
   * - A fragment that is only audio tags ([laughs], [softly]) is never counted as a sentence:
   *   it joins the next sentence, or the previous sentence when at the end of the reply.
   */
  fun splitIntoSentences(speakableText: String): List<String> {
    if (speakableText.isBlank()) return emptyList()
    val rawParts = splitRawFragments(speakableText)
    val results = mutableListOf<String>()
    var carryOverTag = ""
    for (part in rawParts) {
      val trimmedPart = part.trim()
      if (trimmedPart.isEmpty()) continue
      val combined = if (carryOverTag.isNotEmpty()) "$carryOverTag $trimmedPart" else trimmedPart
      carryOverTag = ""
      if (isOnlyAudioTags(combined)) {
        carryOverTag = combined
      } else {
        results.add(combined)
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

  private fun splitRawFragments(text: String): List<String> {
    val parts = mutableListOf<String>()
    var segmentStart = 0
    var i = 0
    val len = text.length

    while (i < len) {
      val ch = text[i]
      if (ch == '\n' || ch == '\r') {
        if (i > segmentStart) {
          val piece = text.substring(segmentStart, i).trim()
          if (piece.isNotEmpty()) parts.add(piece)
        }
        while (i < len && (text[i] == '\n' || text[i] == '\r' || text[i] == ' ' || text[i] == '\t')) {
          i++
        }
        segmentStart = i
        continue
      }

      if (ch == '.' || ch == '!' || ch == '?') {
        if (isSentenceEndAt(text, i)) {
          val cutEnd = i + 1
          val piece = text.substring(segmentStart, cutEnd).trim()
          if (piece.isNotEmpty()) parts.add(piece)
          var nextIdx = cutEnd
          while (nextIdx < len && (text[nextIdx] == ' ' || text[nextIdx] == '\t')) {
            nextIdx++
          }
          segmentStart = nextIdx
          i = nextIdx
          continue
        }
      }
      i++
    }

    if (segmentStart < len) {
      val tail = text.substring(segmentStart, len).trim()
      if (tail.isNotEmpty()) parts.add(tail)
    }
    return parts
  }

  private fun isSentenceEndAt(text: String, idx: Int): Boolean {
    val ch = text[idx]
    val len = text.length

    // Must be at end of text or followed by whitespace
    if (idx + 1 < len && !text[idx + 1].isWhitespace()) {
      return false
    }

    if (ch == '.') {
      // 1. Ellipsis "..." or ".." never ends a sentence
      if ((idx > 0 && text[idx - 1] == '.') || (idx + 1 < len && text[idx + 1] == '.')) {
        return false
      }

      // Extract the token ending at idx (inclusive)
      var tokenStart = idx
      while (tokenStart > 0 && !text[tokenStart - 1].isWhitespace()) {
        tokenStart--
      }
      val rawToken =
        text
          .substring(tokenStart, idx + 1)
          .trimStart('(', '[', '"', '\'', '«', '“', '‘')
          .lowercase()

      // 2. "Dr.", "Mr.", "Mrs.", "Ms." and "St." never end a sentence
      if (rawToken in NO_SPLIT_ABBREVIATIONS) {
        return false
      }

      // 3. "a.m." and "p.m." end a sentence only when the next word starts with a capital letter
      if (rawToken == "a.m." || rawToken == "p.m.") {
        val nextWordFirstChar = findFirstCharOfNextWord(text, idx + 1)
        return nextWordFirstChar != null && nextWordFirstChar in 'A'..'Z'
      }
    }

    return true
  }

  private fun findFirstCharOfNextWord(text: String, fromIndex: Int): Char? {
    var i = fromIndex
    val len = text.length
    while (i < len) {
      // Skip whitespace
      while (i < len && text[i].isWhitespace()) i++
      if (i >= len) return null
      // If an audio tag like [softly] appears before the next word, skip past it
      val sub = text.substring(i)
      val tagMatch = AUDIO_TAG_REGEX.find(sub)
      if (tagMatch != null && tagMatch.range.first == 0) {
        i += tagMatch.range.last + 1
        continue
      }
      // Skip opening quotes/parens if any
      while (
        i < len &&
          (text[i] == '"' ||
            text[i] == '\'' ||
            text[i] == '“' ||
            text[i] == '‘' ||
            text[i] == '(')
      ) {
        i++
      }
      return if (i < len) text[i] else null
    }
    return null
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
