package com.example.util

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

@Serializable
data class BotReplyEnvelope(
  val v: Int = 1,
  val kind: String? = null,
  val ok: Boolean = false,
  val ref: String? = null,
  val message: BotReplyMessage? = null,
  val data: JsonElement? = null,
  val alerts_set: Int? = null,
  val fallback_text: String? = null,
  val error: BotErrorObject? = null,
)

@Serializable
data class BotReplyMessage(
  val situation: String? = null,
  val facts: Map<String, JsonElement> = emptyMap(),
  val must_say: List<String> = emptyList(),
  val decisions_for_jordan: List<JsonElement> = emptyList(),
  val delivery: BotDeliveryConfig = BotDeliveryConfig(),
  val emotion: BotEmotionConfig? = null,
)

@Serializable
data class BotDeliveryConfig(
  val say: Boolean = true,
  val urgency: String = "normal",
  val hold_ok: Boolean = true,
  val max_spoken_sentences: Int = 2,
)

@Serializable
data class BotEmotionConfig(
  val tone: String = "neutral",
  val intensity: Double = 0.5,
  val note: String? = null,
)

@Serializable
data class BotErrorObject(
  val code: String? = null,
  val msg: String? = null,
  val step: String? = null,
)

@Serializable
data class BotDetailsCardMeta(
  val bot_details: Boolean = true,
  val bot: String = "",
  val ticket: String = "",
  val seconds: Double? = null,
  val rawJson: String = "",
  val failedCheck: String? = null,
  val errorCode: String? = null,
)

data class BotVerificationResult(
  val passed: Boolean,
  val failedCheck: String? = null,
  val detail: String? = failedCheck,
)

object BotReplyVerifier {
  val jsonParser = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
  }

  fun parseReplyEnvelope(rawReplyJson: String): BotReplyEnvelope? {
    return try {
      jsonParser.decodeFromString<BotReplyEnvelope>(rawReplyJson)
    } catch (_: Exception) {
      null
    }
  }

  fun encodeBotDetailsCard(meta: BotDetailsCardMeta): String {
    return try {
      jsonParser.encodeToString(meta)
    } catch (_: Exception) {
      ""
    }
  }

  fun isBotDetailsJson(ruleEventsSummary: String?): Boolean {
    if (ruleEventsSummary.isNullOrBlank()) return false
    val trimmed = ruleEventsSummary.trim()
    return trimmed.startsWith("{") && trimmed.contains("\"bot_details\"")
  }

  fun decodeBotDetailsCard(ruleEventsSummary: String?): BotDetailsCardMeta? {
    if (!isBotDetailsJson(ruleEventsSummary)) return null
    return try {
      jsonParser.decodeFromString<BotDetailsCardMeta>(ruleEventsSummary!!.trim())
    } catch (_: Exception) {
      null
    }
  }

  /**
   * Builds the exact word-for-word Section 6 answer-turn prompt.
   */
  fun buildAnswerTurnPrompt(messageJsonString: String): String {
    return "The job Jordan asked for earlier is done. Tell him the result as yourself, in first person; never mention bots, Ard, tickets or tools. Write one spoken line from this message only. Use only the facts given. Say every must_say fact. Say times exactly as their spoken value. Never compute a time. Say a day in full, like Wednesday, November 11. Respect max_spoken_sentences. Offer decisions_for_jordan; never choose one. Use a plain, even tone. No audio tags and no square brackets. Message: $messageJsonString"
  }

  fun buildAnswerTurnPrompt(message: BotReplyMessage): String {
    return buildAnswerTurnPrompt(jsonParser.encodeToString(message))
  }

  /**
   * Formats the per-chat prefix note for app-added lines when chaining via previous_interaction_id.
   * Format: (Earlier you told Jordan: "<line>" "<line>")
   */
  fun formatEarlierToldNote(lines: List<String>): String {
    val cleanLines = lines.map { it.trim() }.filter { it.isNotEmpty() }.takeLast(3)
    if (cleanLines.isEmpty()) return ""
    val quoted = cleanLines.joinToString(" ") { "\"$it\"" }
    return "(Earlier you told Jordan: $quoted)"
  }

  /**
   * Runs the 5 verification checks in code on Gemini's spoken line:
   * 1. Every clock time in the line (\d{1,2}:\d{2}) equals the "spoken" value of some time fact in message.facts.
   * 2. Every must_say key shows up: a time fact by its "spoken" value, a number by its digits or its English words,
   *    a place by its "name", a day label like "Tue Nov 10" by its full weekday name ("Tuesday") or its date number ("10").
   * 3. No number of minutes or hours appears that isn't a number in message.facts.
   * 4. Spoken sentence count is at most delivery.max_spoken_sentences.
   * 5. Whole-word case-insensitive match: the line doesn't contain "bot", "bots", "ticket", "tickets", "ard", or any configured bot's name.
   */
  fun verifySpokenLine(
    rawLine: String,
    message: BotReplyMessage,
    configuredBotNames: Set<String> = setOf("ard"),
  ): BotVerificationResult {
    val strippedLine = TextAudioTagProcessor.stripAudioTagsForScreen(rawLine).trim()
    if (strippedLine.isBlank()) {
      return BotVerificationResult(passed = false, failedCheck = "empty_line")
    }

    // Collect time facts' spoken values and numeric facts from message.facts
    val allowedSpokenTimes = mutableSetOf<String>()
    val allowedNumbers = mutableSetOf<Int>()

    for ((_, element) in message.facts) {
      when (element) {
        is JsonObject -> {
          val spoken = element["spoken"]?.jsonPrimitive?.contentOrNull?.trim()
          if (!spoken.isNullOrBlank()) {
            allowedSpokenTimes.add(spoken)
            // Also extract clock part if spoken has AM/PM
            val clockMatch = Regex("""\b(\d{1,2}:\d{2})\b""").find(spoken)
            if (clockMatch != null) {
              allowedSpokenTimes.add(clockMatch.groupValues[1])
            }
          }
        }
        is JsonPrimitive -> {
          if (!element.isString) {
            element.intOrNull?.let { allowedNumbers.add(it) }
              ?: element.longOrNull?.toInt()?.let { allowedNumbers.add(it) }
              ?: element.doubleOrNull?.toInt()?.let { allowedNumbers.add(it) }
          }
        }
        else -> {}
      }
    }

    // Check 1: Every clock time in the line (\d{1,2}:\d{2}) equals the "spoken" value of some time fact in message.facts
    val clockRegex = Regex("""\b\d{1,2}:\d{2}\b""")
    val clockMatches = clockRegex.findAll(strippedLine).map { it.value }.toList()
    for (clock in clockMatches) {
      if (clock !in allowedSpokenTimes) {
        return BotVerificationResult(passed = false, failedCheck = "check_1_clock_time ($clock)")
      }
    }

    // Check 2: Every must_say key shows up
    for (key in message.must_say) {
      val factElement =
        message.facts[key]
          ?: return BotVerificationResult(passed = false, failedCheck = "check_2_must_say ($key)")
      if (!factAppearsInLine(strippedLine, factElement)) {
        return BotVerificationResult(passed = false, failedCheck = "check_2_must_say ($key)")
      }
    }

    // Check 3: No number of minutes or hours appears that isn't a number in message.facts
    val foundDurationNumbers = extractDurationNumbersFromLine(strippedLine)
    for (num in foundDurationNumbers) {
      if (num !in allowedNumbers) {
        return BotVerificationResult(
          passed = false,
          failedCheck = "check_3_ungrounded_duration ($num)",
        )
      }
    }

    // Check 4: Spoken sentence count is at most delivery.max_spoken_sentences
    val sentences = TextAudioTagProcessor.splitIntoSentences(strippedLine)
    val maxSentences = message.delivery.max_spoken_sentences.coerceAtLeast(1)
    if (sentences.size > maxSentences) {
      return BotVerificationResult(
        passed = false,
        failedCheck = "check_4_max_sentences (${sentences.size} > $maxSentences)",
      )
    }

    // Check 5: Whole-word case-insensitive check for "bot", "bots", "ticket", "tickets", "ard", and configured bot names
    val forbiddenWords = mutableSetOf("bot", "bots", "ticket", "tickets", "ard")
    for (botName in configuredBotNames) {
      val clean = botName.trim().lowercase()
      if (clean.isNotBlank()) {
        forbiddenWords.add(clean)
      }
    }
    val alternation = forbiddenWords.joinToString("|") { Regex.escape(it) }
    val forbiddenRegex = Regex("""\b(?:$alternation)\b""", RegexOption.IGNORE_CASE)
    val match = forbiddenRegex.find(strippedLine) ?: forbiddenRegex.find(rawLine)
    if (match != null) {
      return BotVerificationResult(
        passed = false,
        failedCheck = "check_5_forbidden_word (${match.value.lowercase()})",
      )
    }

    return BotVerificationResult(passed = true, failedCheck = null)
  }

  private fun factAppearsInLine(line: String, factElement: JsonElement): Boolean {
    return when (factElement) {
      is JsonObject -> {
        val spoken = factElement["spoken"]?.jsonPrimitive?.contentOrNull?.trim()
        val name = factElement["name"]?.jsonPrimitive?.contentOrNull?.trim()
        when {
          !spoken.isNullOrBlank() -> {
            Regex("""\b${Regex.escape(spoken)}\b""", RegexOption.IGNORE_CASE).containsMatchIn(line)
          }
          !name.isNullOrBlank() -> {
            line.contains(name, ignoreCase = true)
          }
          else -> true
        }
      }
      is JsonPrimitive -> {
        val intVal =
          factElement.intOrNull
            ?: factElement.longOrNull?.toInt()
            ?: factElement.doubleOrNull?.toInt()
        if (!factElement.isString && intVal != null) {
          numberAppearsInLine(line, intVal)
        } else {
          val strVal = factElement.contentOrNull?.trim().orEmpty()
          if (strVal.isEmpty()) {
            true
          } else {
            dayLabelOrStringAppearsInLine(line, strVal)
          }
        }
      }
      is JsonArray -> {
        if (factElement.isEmpty()) true
        else factElement.any { factAppearsInLine(line, it) }
      }
    }
  }

  private fun dayLabelOrStringAppearsInLine(line: String, strVal: String): Boolean {
    if (line.contains(strVal, ignoreCase = true)) return true

    // Check if strVal looks like a day label such as "Tue Nov 10"
    val weekdayMap =
      mapOf(
        "mon" to "monday",
        "tue" to "tuesday",
        "tues" to "tuesday",
        "wed" to "wednesday",
        "thu" to "thursday",
        "thur" to "thursday",
        "thurs" to "thursday",
        "fri" to "friday",
        "sat" to "saturday",
        "sun" to "sunday",
      )
    val tokens = strVal.split(Regex("""[\s,]+""")).filter { it.isNotBlank() }
    var matchedDayOrDate = false
    for (tok in tokens) {
      val lower = tok.lowercase().trimEnd('.')
      val fullDay = weekdayMap[lower]
      if (fullDay != null) {
        if (Regex("""\b${Regex.escape(fullDay)}\b""", RegexOption.IGNORE_CASE).containsMatchIn(line)) {
          matchedDayOrDate = true
        }
      }
      val dateNum = tok.toIntOrNull()
      if (dateNum != null) {
        if (
          Regex("""\b$dateNum(?:st|nd|rd|th)?\b""", RegexOption.IGNORE_CASE).containsMatchIn(line) ||
            numberAppearsInLine(line, dateNum)
        ) {
          matchedDayOrDate = true
        }
      }
    }
    return matchedDayOrDate
  }

  private fun numberAppearsInLine(line: String, n: Int): Boolean {
    if (Regex("""\b$n\b""").containsMatchIn(line)) return true
    val wordsForms = numberToEnglishWordsForms(n)
    for (form in wordsForms) {
      if (Regex("""\b${Regex.escape(form)}\b""", RegexOption.IGNORE_CASE).containsMatchIn(line)) {
        return true
      }
    }
    return false
  }

  fun numberToEnglishWordsForms(n: Int): Set<String> {
    if (n < 0 || n > 999) return setOf(n.toString())
    val ones =
      arrayOf(
        "zero",
        "one",
        "two",
        "three",
        "four",
        "five",
        "six",
        "seven",
        "eight",
        "nine",
        "ten",
        "eleven",
        "twelve",
        "thirteen",
        "fourteen",
        "fifteen",
        "sixteen",
        "seventeen",
        "eighteen",
        "nineteen",
      )
    val tens =
      arrayOf(
        "",
        "",
        "twenty",
        "thirty",
        "forty",
        "fifty",
        "sixty",
        "seventy",
        "eighty",
        "ninety",
      )

    fun under100(x: Int): Set<String> {
      return when {
        x < 20 -> setOf(ones[x])
        x % 10 == 0 -> setOf(tens[x / 10])
        else -> {
          val t = tens[x / 10]
          val o = ones[x % 10]
          setOf("$t-$o", "$t $o")
        }
      }
    }

    return if (n < 100) {
      under100(n)
    } else {
      val h = ones[n / 100] + " hundred"
      val rem = n % 100
      if (rem == 0) {
        setOf(h)
      } else {
        val sub = under100(rem)
        sub.flatMap { listOf("$h $it", "$h and $it") }.toSet()
      }
    }
  }

  private val wordToNumberMap: Map<String, Int> by lazy {
    val map = mutableMapOf<String, Int>()
    for (i in 0..500) {
      for (form in numberToEnglishWordsForms(i)) {
        map[form.lowercase()] = i
      }
    }
    map["a"] = 1
    map["an"] = 1
    map
  }

  private fun extractDurationNumbersFromLine(line: String): List<Int> {
    val results = mutableListOf<Int>()
    // 1. Digit durations: e.g. "25 minutes", "2 hours", "15 mins"
    val digitDurationRegex =
      Regex("""\b(\d+)\s*(?:minutes|minute|mins|min|hours|hour|hrs|hr)\b""", RegexOption.IGNORE_CASE)
    for (m in digitDurationRegex.findAll(line)) {
      m.groupValues[1].toIntOrNull()?.let { results.add(it) }
    }

    // 2. Word durations: e.g. "twenty-five minutes", "twenty five minutes", "fifteen minutes", "two hours"
    val wordDurationRegex =
      Regex(
        """\b([a-zA-Z]+(?:[-\s]+[a-zA-Z]+)?)\s+(?:minutes|minute|mins|min|hours|hour|hrs|hr)\b""",
        RegexOption.IGNORE_CASE,
      )
    for (m in wordDurationRegex.findAll(line)) {
      val rawPhrase = m.groupValues[1].trim().lowercase().replace(Regex("""\s+"""), " ")
      val fullMatch = wordToNumberMap[rawPhrase]
      if (fullMatch != null) {
        results.add(fullMatch)
      } else {
        val lastWord = rawPhrase.substringAfterLast(' ')
        wordToNumberMap[lastWord]?.let { results.add(it) }
      }
    }
    return results
  }
}
