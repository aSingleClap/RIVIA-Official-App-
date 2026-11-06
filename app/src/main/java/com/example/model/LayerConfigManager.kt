package com.example.model

import android.content.Context
import com.example.data.LayerDraftEntity
import com.example.data.LayerLockEntity
import com.example.data.LayerTestRunEntity
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object LayerConfigManager {
  val REQUIRED_PROFILES = listOf("fast", "balanced", "deep", "live")
  val SUPPORTED_CODE_RULE_HANDLERS = setOf("H7", "H9")
  val LAYER_FILES =
    mapOf(
      "persona" to "persona.json",
      "style" to "style.json",
      "engine" to "engine.json",
      "hard_rules" to "hard_rules.json",
      "tests" to "tests.json",
    )

  val jsonParser = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
  }

  val prettyJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = true
  }

  /**
   * Clarification #1: "live_typed" always maps to engine profile "live":
   * fingerprint, test-state chip, Log a run, Chat info and Request log.
   */
  fun normalizeProfileKey(profile: String): String {
    val clean = profile.trim().lowercase()
    return if (clean == "live_typed") "live" else clean
  }

  fun channelForProfile(profile: String): String {
    return if (normalizeProfileKey(profile) == "live") "voice" else "text"
  }

  fun displayProfileLabel(profile: String): String =
    when (profile.trim().lowercase()) {
      "fast" -> "Fast"
      "balanced" -> "Balanced"
      "deep" -> "Deep"
      "live_typed" -> "Live (typed)"
      "live" -> "Live"
      else -> profile
    }

  /**
   * Loads bundled JSON assets from app/src/main/assets/config/<file>.
   */
  fun readBundledAssetFiles(context: Context): Result<Map<String, String>> {
    return try {
      val map = mutableMapOf<String, String>()
      for ((key, fileName) in LAYER_FILES) {
        val text =
          context.assets.open("config/$fileName").use { input ->
            input.bufferedReader(Charsets.UTF_8).readText()
          }
        map[key] = text
      }
      Result.success(map)
    } catch (e: Throwable) {
      Result.failure(IllegalStateException("config: ${e.message ?: "missing config file"}"))
    }
  }

  /**
   * Validates and parses all 5 layer files, merging any valid drafts from Room.
   * If any bundled file fails to load or validate, fails with "<file>: <reason>".
   */
  fun loadAndValidateBundle(
    bundledFiles: Map<String, String>,
    drafts: List<LayerDraftEntity>,
  ): Result<LoadedLayersBundle> {
    val draftMap = drafts.associate { it.layerKey to it.jsonContent }

    // 1. Validate bundled files first so a broken bundled config always fails closed
    val bundledPersona =
      validateAndParsePersona(bundledFiles["persona"] ?: return failFile("persona.json", "File missing"))
        .getOrElse { return Result.failure(it) }
    val bundledStyle =
      validateAndParseStyle(bundledFiles["style"] ?: return failFile("style.json", "File missing"))
        .getOrElse { return Result.failure(it) }
    val bundledEngine =
      validateAndParseEngine(bundledFiles["engine"] ?: return failFile("engine.json", "File missing"))
        .getOrElse { return Result.failure(it) }
    val bundledHardRules =
      validateAndParseHardRules(
          bundledFiles["hard_rules"] ?: return failFile("hard_rules.json", "File missing")
        )
        .getOrElse { return Result.failure(it) }
    val bundledTests =
      validateAndParseTests(
          bundledFiles["tests"] ?: return failFile("tests.json", "File missing"),
          bundledEngine,
        )
        .getOrElse { return Result.failure(it) }

    // 2. Apply drafts if present and valid
    val effectiveRaw = bundledFiles.toMutableMap()
    val activeDraftKeys = mutableSetOf<String>()

    val effectivePersona =
      draftMap["persona"]?.let { raw ->
        val p = validateAndParsePersona(raw).getOrElse { return Result.failure(it) }
        effectiveRaw["persona"] = raw
        activeDraftKeys.add("persona")
        p
      } ?: bundledPersona

    val effectiveStyle =
      draftMap["style"]?.let { raw ->
        val s = validateAndParseStyle(raw).getOrElse { return Result.failure(it) }
        effectiveRaw["style"] = raw
        activeDraftKeys.add("style")
        s
      } ?: bundledStyle

    val effectiveEngine =
      draftMap["engine"]?.let { raw ->
        val e = validateAndParseEngine(raw).getOrElse { return Result.failure(it) }
        effectiveRaw["engine"] = raw
        activeDraftKeys.add("engine")
        e
      } ?: bundledEngine

    val effectiveHardRules =
      draftMap["hard_rules"]?.let { raw ->
        val h = validateAndParseHardRules(raw).getOrElse { return Result.failure(it) }
        effectiveRaw["hard_rules"] = raw
        activeDraftKeys.add("hard_rules")
        h
      } ?: bundledHardRules

    // Re-validate tests against effective engine
    validateAndParseTests(bundledFiles.getValue("tests"), effectiveEngine).getOrElse {
      return Result.failure(it)
    }

    return Result.success(
      LoadedLayersBundle(
        persona = effectivePersona,
        style = effectiveStyle,
        engine = effectiveEngine,
        hardRules = effectiveHardRules,
        tests = bundledTests,
        bundledPersona = bundledPersona,
        bundledStyle = bundledStyle,
        bundledEngine = bundledEngine,
        bundledHardRules = bundledHardRules,
        bundledRawJson = bundledFiles,
        effectiveRawJson = effectiveRaw,
        draftKeys = activeDraftKeys,
      )
    )
  }

  fun validateAndParsePersona(rawJson: String): Result<PersonaLayerConfig> {
    return try {
      val root = jsonParser.parseToJsonElement(rawJson).jsonObject
      val textEl =
        root["text"] ?: return failFile("persona.json", "\"text\" is missing")
      if (textEl !is JsonPrimitive || !textEl.isString) {
        return failFile("persona.json", "\"text\" must be a string")
      }
      val config = jsonParser.decodeFromString<PersonaLayerConfig>(rawJson)
      Result.success(config)
    } catch (e: Throwable) {
      failFile("persona.json", e.message ?: "Invalid JSON")
    }
  }

  fun validateAndParseStyle(rawJson: String): Result<StyleLayerConfig> {
    return try {
      val root = jsonParser.parseToJsonElement(rawJson).jsonObject
      val textEl =
        root["text"] ?: return failFile("style.json", "\"text\" is missing")
      if (textEl !is JsonPrimitive || !textEl.isString) {
        return failFile("style.json", "\"text\" must be a string")
      }
      val voiceEl =
        root["voice"] ?: return failFile("style.json", "\"voice\" is missing")
      if (voiceEl !is JsonPrimitive || !voiceEl.isString) {
        return failFile("style.json", "\"voice\" must be a string")
      }
      val config = jsonParser.decodeFromString<StyleLayerConfig>(rawJson)
      Result.success(config)
    } catch (e: Throwable) {
      failFile("style.json", e.message ?: "Invalid JSON")
    }
  }

  fun validateAndParseEngine(rawJson: String): Result<EngineLayerConfig> {
    return try {
      val root = jsonParser.parseToJsonElement(rawJson).jsonObject

      // Validate max_output_tokens strictly (must be null or a positive whole number)
      val maxOutEl = root["max_output_tokens"]
      if (maxOutEl != null && maxOutEl !is JsonNull) {
        if (maxOutEl !is JsonPrimitive || maxOutEl.isString) {
          return failFile(
            "engine.json",
            "max_output_tokens must be null or a positive whole number",
          )
        }
        val intVal = maxOutEl.intOrNull
        if (intVal == null || intVal < 1 || maxOutEl.content.contains('.')) {
          return failFile(
            "engine.json",
            "max_output_tokens must be null or a positive whole number",
          )
        }
      }

      val config = jsonParser.decodeFromString<EngineLayerConfig>(rawJson)

      // Validate required profiles: fast, balanced, deep, live
      for (reqProfile in REQUIRED_PROFILES) {
        val prof =
          config.profiles[reqProfile]
            ?: return failFile("engine.json", "Missing required profile \"$reqProfile\"")
        if (prof.model.isBlank()) {
          return failFile("engine.json", "Profile \"$reqProfile\" has an empty model")
        }
        val allowedLevels = RunSettings.allowedThinkingLevels(prof.model)
        if (prof.thinking_level !in allowedLevels) {
          return failFile(
            "engine.json",
            "Profile \"$reqProfile\" thinking_level \"${prof.thinking_level}\" is not allowed for model \"${prof.model}\" (allowed: ${allowedLevels.joinToString(", ")})",
          )
        }
      }

      // Validate default_profile names one of the profiles
      if (config.default_profile !in REQUIRED_PROFILES || config.default_profile !in config.profiles.keys) {
        return failFile(
          "engine.json",
          "default_profile \"${config.default_profile}\" must be one of ${REQUIRED_PROFILES.joinToString(", ")}",
        )
      }

      // Validate service_tier is standard or priority
      if (config.service_tier != "standard" && config.service_tier != "priority") {
        return failFile(
          "engine.json",
          "service_tier \"${config.service_tier}\" must be \"standard\" or \"priority\"",
        )
      }

      // Validate thought_summaries
      if (config.thought_summaries != "auto" && config.thought_summaries != "none") {
        return failFile(
          "engine.json",
          "thought_summaries \"${config.thought_summaries}\" must be \"auto\" or \"none\"",
        )
      }

      // Validate safety is "model_default" or a list in the shape Run settings sends
      val safetyEl = config.safety
      when (safetyEl) {
        is JsonPrimitive -> {
          if (!safetyEl.isString || safetyEl.content != "model_default") {
            return failFile(
              "engine.json",
              "safety string must be \"model_default\"",
            )
          }
        }
        is JsonArray -> {
          if (safetyEl.isEmpty()) {
            return failFile(
              "engine.json",
              "safety list cannot be empty (use \"model_default\" instead)",
            )
          }
          for (item in safetyEl) {
            if (item !is JsonObject) {
              return failFile("engine.json", "Each safety item must be an object")
            }
            val typeP = item["type"] as? JsonPrimitive
            val threshP = item["threshold"] as? JsonPrimitive
            val methodP = item["method"] as? JsonPrimitive
            if (
              typeP == null ||
                !typeP.isString ||
                typeP.content.isBlank() ||
                threshP == null ||
                !threshP.isString ||
                threshP.content.isBlank() ||
                (methodP != null && (!methodP.isString || methodP.content.isBlank()))
            ) {
              return failFile(
                "engine.json",
                "Each safety item must have string \"type\" and \"threshold\" (and optional \"method\")",
              )
            }
          }
        }
        else -> {
          return failFile(
            "engine.json",
            "safety must be \"model_default\" or a list of safety setting objects",
          )
        }
      }

      Result.success(config)
    } catch (e: Throwable) {
      failFile("engine.json", e.message ?: "Invalid JSON")
    }
  }

  fun validateAndParseHardRules(rawJson: String): Result<HardRulesLayerConfig> {
    return try {
      val config = jsonParser.decodeFromString<HardRulesLayerConfig>(rawJson)
      val seenIds = mutableSetOf<String>()
      val seenPriorities = mutableSetOf<Int>()

      for (rule in config.rules) {
        if (rule.id.isBlank()) {
          return failFile("hard_rules.json", "Rule id cannot be empty")
        }
        if (!seenIds.add(rule.id)) {
          return failFile("hard_rules.json", "Duplicate rule id \"${rule.id}\"")
        }
        if (!seenPriorities.add(rule.priority)) {
          return failFile(
            "hard_rules.json",
            "Duplicate priority ${rule.priority} on rule \"${rule.id}\"",
          )
        }
        if (rule.tunable) {
          val setting = rule.setting
          val def = rule.default
          val ceil = rule.ceiling
          if (setting.isNullOrBlank() || def == null || ceil == null || def < 1 || def > ceil) {
            return failFile(
              "hard_rules.json",
              "Tunable rule \"${rule.id}\" must have setting, default and ceiling with 1 <= default <= ceiling",
            )
          }
        }
        if (rule.active) {
          if (rule.enforced_by == "prompt" && rule.prompt.isNullOrBlank()) {
            return failFile(
              "hard_rules.json",
              "Active prompt rule \"${rule.id}\" is missing prompt text",
            )
          }
          if (rule.enforced_by == "code" && rule.id !in SUPPORTED_CODE_RULE_HANDLERS) {
            return failFile(
              "hard_rules.json",
              "Active code rule \"${rule.id}\" has no code handler in the app (supported: ${SUPPORTED_CODE_RULE_HANDLERS.joinToString(", ")})",
            )
          }
        }
      }
      Result.success(config)
    } catch (e: Throwable) {
      failFile("hard_rules.json", e.message ?: "Invalid JSON")
    }
  }

  fun validateAndParseTests(
    rawJson: String,
    engine: EngineLayerConfig,
  ): Result<TestsLayerConfig> {
    return try {
      val config = jsonParser.decodeFromString<TestsLayerConfig>(rawJson)
      val seenIds = mutableSetOf<String>()
      for (test in config.tests) {
        if (test.id.isBlank()) {
          return failFile("tests.json", "Test id cannot be empty")
        }
        if (!seenIds.add(test.id)) {
          return failFile("tests.json", "Duplicate test id \"${test.id}\"")
        }
        for (reqProf in test.required_profiles) {
          if (reqProf !in engine.profiles.keys) {
            return failFile(
              "tests.json",
              "Test \"${test.id}\" requires profile \"$reqProf\" which does not exist in engine.json",
            )
          }
        }
      }
      Result.success(config)
    } catch (e: Throwable) {
      failFile("tests.json", e.message ?: "Invalid JSON")
    }
  }

  private fun <T> failFile(fileName: String, reason: String): Result<T> {
    val cleanReason = reason.removePrefix("$fileName: ").trim()
    return Result.failure(IllegalArgumentException("$fileName: $cleanReason"))
  }

  /**
   * Computes the spoken_max_sentences setting bounded by H7's default and ceiling.
   */
  fun resolveSpokenMaxSentences(
    hardRules: HardRulesLayerConfig,
    savedValue: Int?,
  ): Int {
    val h7 = hardRules.rules.firstOrNull { it.id == "H7" }
    val defaultVal = h7?.default ?: 2
    val ceilingVal = h7?.ceiling ?: 4
    val chosen = savedValue ?: defaultVal
    return chosen.coerceIn(1, ceilingVal.coerceAtLeast(1))
  }

  /**
   * Clarification #1 & #3:
   * Computes the 8-hex-character SHA-256 fingerprint for a profile ("fast", "balanced", "deep", "live";
   * "live_typed" maps to "live") from canonical JSON (sorted keys, no whitespace):
   * - PROMPT_ASSEMBLY_VERSION
   * - profile's channel, model, thinking_level
   * - engine's thought_summaries, max_output_tokens (encoded as JSON null when null), service_tier, store, safety
   * - persona text trimmed exactly as the prompt assembler trims it
   * - style text for that channel trimmed
   * - all hard rules sorted by id with id, active, priority, channels, prompt
   * - spoken_max_sentences on the voice channel
   */
  fun computeProfileFingerprint(
    profileName: String,
    persona: PersonaLayerConfig,
    style: StyleLayerConfig,
    engine: EngineLayerConfig,
    hardRules: HardRulesLayerConfig,
    spokenMaxSentences: Int,
  ): String {
    val canonicalJson =
      buildProfileCanonicalFingerprintJson(
        profileName = profileName,
        persona = persona,
        style = style,
        engine = engine,
        hardRules = hardRules,
        spokenMaxSentences = spokenMaxSentences,
      )
    return sha256Hex(canonicalJson).take(8)
  }

  fun computeAllProfileFingerprints(
    persona: PersonaLayerConfig,
    style: StyleLayerConfig,
    engine: EngineLayerConfig,
    hardRules: HardRulesLayerConfig,
    spokenMaxSentences: Int,
  ): Map<String, String> {
    return REQUIRED_PROFILES.associateWith { prof ->
      computeProfileFingerprint(
        profileName = prof,
        persona = persona,
        style = style,
        engine = engine,
        hardRules = hardRules,
        spokenMaxSentences = spokenMaxSentences,
      )
    }
  }

  fun buildProfileCanonicalFingerprintJson(
    profileName: String,
    persona: PersonaLayerConfig,
    style: StyleLayerConfig,
    engine: EngineLayerConfig,
    hardRules: HardRulesLayerConfig,
    spokenMaxSentences: Int,
  ): String {
    val normProfile = normalizeProfileKey(profileName)
    val channel = channelForProfile(normProfile)
    val profConfig =
      engine.profiles[normProfile]
        ?: engine.profiles[engine.default_profile]
        ?: EngineProfileConfig("gemini-3.8-flash", "low")

    val styleText =
      if (channel == "voice") style.voice.trim() else style.text.trim()

    val rulesArray =
      JsonArray(
        hardRules.rules
          .sortedBy { it.id }
          .map { rule ->
            val ruleMap = sortedMapOf<String, JsonElement>()
            ruleMap["active"] = JsonPrimitive(rule.active)
            ruleMap["channels"] = JsonArray(rule.channels.map { JsonPrimitive(it) })
            ruleMap["id"] = JsonPrimitive(rule.id)
            ruleMap["priority"] = JsonPrimitive(rule.priority)
            ruleMap["prompt"] =
              if (rule.prompt == null) JsonNull else JsonPrimitive(rule.prompt)
            JsonObject(ruleMap)
          }
      )

    val topMap = sortedMapOf<String, JsonElement>()
    topMap["channel"] = JsonPrimitive(channel)
    topMap["hard_rules"] = rulesArray
    topMap["max_output_tokens"] =
      if (engine.max_output_tokens == null) JsonNull
      else JsonPrimitive(engine.max_output_tokens)
    topMap["model"] = JsonPrimitive(profConfig.model)
    topMap["persona_text"] = JsonPrimitive(persona.text.trim())
    topMap["prompt_assembly_version"] = JsonPrimitive(PROMPT_ASSEMBLY_VERSION)
    topMap["safety"] = canonicalizeJsonElement(engine.safety)
    topMap["service_tier"] = JsonPrimitive(engine.service_tier)
    if (channel == "voice") {
      topMap["spoken_max_sentences"] = JsonPrimitive(spokenMaxSentences)
    }
    topMap["store"] = JsonPrimitive(engine.store)
    topMap["style_text"] = JsonPrimitive(styleText)
    topMap["thinking_level"] = JsonPrimitive(profConfig.thinking_level)
    topMap["thought_summaries"] = JsonPrimitive(engine.thought_summaries)

    return serializeCanonicalJsonElement(JsonObject(topMap))
  }

  /**
   * Computes the full 64-char SHA-256 hash of a JSON document after canonicalizing key order and whitespace.
   */
  fun canonicalJsonSha256(rawJson: String): String {
    val parsed = jsonParser.parseToJsonElement(rawJson)
    val canonical = serializeCanonicalJsonElement(canonicalizeJsonElement(parsed))
    return sha256Hex(canonical)
  }

  fun canonicalizeJsonElement(element: JsonElement): JsonElement =
    when (element) {
      is JsonObject -> {
        val sorted = sortedMapOf<String, JsonElement>()
        for ((k, v) in element.entries.sortedBy { it.key }) {
          sorted[k] = canonicalizeJsonElement(v)
        }
        JsonObject(sorted)
      }
      is JsonArray -> JsonArray(element.map { canonicalizeJsonElement(it) })
      is JsonPrimitive -> element
    }

  fun serializeCanonicalJsonElement(element: JsonElement): String =
    when (element) {
      is JsonNull -> "null"
      is JsonPrimitive -> jsonParser.encodeToString(JsonPrimitive.serializer(), element)
      is JsonArray ->
        element.joinToString(prefix = "[", postfix = "]", separator = ",") {
          serializeCanonicalJsonElement(it)
        }
      is JsonObject ->
        element.entries
          .sortedBy { it.key }
          .joinToString(prefix = "{", postfix = "}", separator = ",") { (k, v) ->
            val encodedKey =
              jsonParser.encodeToString(JsonPrimitive.serializer(), JsonPrimitive(k))
            "$encodedKey:${serializeCanonicalJsonElement(v)}"
          }
    }

  fun sha256Hex(input: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
    return bytes.joinToString("") { "%02x".format(it) }
  }

  /**
   * Assembles the system_instruction for a Rivia chat turn (Section 5):
   * Joins non-empty parts with "\n\n":
   * 1. persona.text trimmed
   * 2. style.text trimmed on text channel, or style.voice trimmed on voice channel
   * 3. "Hard rules. These override any other instruction:" followed by optional pendingH7Notice
   *    as the first line, then one line per active rule whose channels includes the channel and
   *    that has prompt text, in priority order (1 first), with {spoken_max_sentences} replaced.
   */
  fun assembleSystemInstruction(
    channel: String, // "text" or "voice"
    persona: PersonaLayerConfig,
    style: StyleLayerConfig,
    hardRules: HardRulesLayerConfig,
    spokenMaxSentences: Int,
    pendingH7Notice: String? = null,
  ): String {
    val parts = mutableListOf<String>()

    val personaTrimmed = persona.text.trim()
    if (personaTrimmed.isNotEmpty()) {
      parts.add(personaTrimmed)
    }

    val styleTrimmed =
      if (channel == "voice") style.voice.trim() else style.text.trim()
    if (styleTrimmed.isNotEmpty()) {
      parts.add(styleTrimmed)
    }

    val activeChannelRules =
      hardRules.rules
        .filter { it.active && channel in it.channels && !it.prompt.isNullOrBlank() }
        .sortedBy { it.priority }

    val cleanH7Notice = pendingH7Notice?.trim().orEmpty()
    if (cleanH7Notice.isNotEmpty() || activeChannelRules.isNotEmpty()) {
      val ruleLines = mutableListOf<String>()
      ruleLines.add("Hard rules. These override any other instruction:")
      if (cleanH7Notice.isNotEmpty()) {
        ruleLines.add(cleanH7Notice)
      }
      for (rule in activeChannelRules) {
        val formattedLine =
          rule.prompt!!
            .trim()
            .replace("{spoken_max_sentences}", spokenMaxSentences.toString())
        if (formattedLine.isNotEmpty()) {
          ruleLines.add(formattedLine)
        }
      }
      parts.add(ruleLines.joinToString("\n"))
    }

    return parts.joinToString("\n\n")
  }

  /**
   * Determines whether a layer is Dormant:
   * - persona: persona.text is empty
   * - style: both style.text and style.voice are empty
   * - hard_rules: has no active rules
   * - engine: never dormant
   */
  fun isLayerDormant(
    layerKey: String,
    persona: PersonaLayerConfig,
    style: StyleLayerConfig,
    hardRules: HardRulesLayerConfig,
  ): Boolean =
    when (layerKey) {
      "persona" -> persona.text.trim().isEmpty()
      "style" -> style.text.trim().isEmpty() && style.voice.trim().isEmpty()
      "hard_rules" -> hardRules.rules.none { it.active }
      else -> false
    }

  /**
   * Computes the test state (passed / not run / failed) for a profile on its current fingerprint.
   */
  fun computeProfileTestState(
    profile: String,
    currentFingerprint: String,
    testsConfig: TestsLayerConfig,
    testRuns: List<LayerTestRunEntity>,
  ): ProfileTestState {
    val normProfile = normalizeProfileKey(profile)
    val requiredTests =
      testsConfig.tests.filter { req ->
        req.required_profiles.any { normalizeProfileKey(it) == normProfile }
      }

    if (requiredTests.isEmpty()) {
      val runsForProfileFp =
        testRuns.filter {
          normalizeProfileKey(it.profile) == normProfile && it.fingerprint == currentFingerprint
        }
      if (runsForProfileFp.isEmpty()) return ProfileTestState.NOT_RUN
      val latestByTest =
        runsForProfileFp
          .groupBy { it.testId }
          .mapValues { (_, list) -> list.maxByOrNull { it.timestamp }!! }
      if (latestByTest.values.any { !it.passed }) return ProfileTestState.FAILED
      return ProfileTestState.PASSED
    }

    var anyFailed = false
    var allPassed = true
    for (test in requiredTests) {
      val latestRun =
        testRuns
          .filter {
            it.testId == test.id &&
              normalizeProfileKey(it.profile) == normProfile &&
              it.fingerprint == currentFingerprint
          }
          .maxByOrNull { it.timestamp }
      if (latestRun == null) {
        allPassed = false
      } else if (!latestRun.passed) {
        anyFailed = true
        allPassed = false
      }
    }

    return when {
      anyFailed -> ProfileTestState.FAILED
      allPassed -> ProfileTestState.PASSED
      else -> ProfileTestState.NOT_RUN
    }
  }

  /**
   * Returns true if every test in tests.json has a passing run on the current fingerprint
   * for each of its required profiles.
   */
  fun areAllRequiredTestsPassing(
    fingerprints: Map<String, String>,
    testsConfig: TestsLayerConfig,
    testRuns: List<LayerTestRunEntity>,
  ): Boolean {
    if (testsConfig.tests.isEmpty()) return false
    for (test in testsConfig.tests) {
      for (reqProf in test.required_profiles) {
        val normProf = normalizeProfileKey(reqProf)
        val fp = fingerprints[normProf] ?: return false
        val latestRun =
          testRuns
            .filter {
              it.testId == test.id &&
                normalizeProfileKey(it.profile) == normProf &&
                it.fingerprint == fp
            }
            .maxByOrNull { it.timestamp }
        if (latestRun == null || !latestRun.passed) {
          return false
        }
      }
    }
    return true
  }

  /**
   * Computes the LayerStatus for a layer card according to Section 2:
   * - Dormant: the layer adds nothing
   * - Locked: the bundled file has "locked": true and there is no draft in Room
   * - Tested: not locked, and every test in tests.json has a passing run on the current fingerprint for each of its required profiles
   * - Draft: anything else
   */
  fun computeLayerStatus(
    layerKey: String,
    bundle: LoadedLayersBundle,
    fingerprints: Map<String, String>,
    testRuns: List<LayerTestRunEntity>,
  ): LayerStatus {
    if (isLayerDormant(layerKey, bundle.persona, bundle.style, bundle.hardRules)) {
      return LayerStatus.DORMANT
    }
    val hasDraft = layerKey in bundle.draftKeys
    val bundledLocked =
      when (layerKey) {
        "persona" -> bundle.bundledPersona.locked
        "style" -> bundle.bundledStyle.locked
        "engine" -> bundle.bundledEngine.locked
        "hard_rules" -> bundle.bundledHardRules.locked
        else -> false
      }
    if (bundledLocked && !hasDraft) {
      return LayerStatus.LOCKED
    }
    if (areAllRequiredTestsPassing(fingerprints, bundle.tests, testRuns)) {
      return LayerStatus.TESTED
    }
    return LayerStatus.DRAFT
  }

  /**
   * Generates the exported JSON string for "Lock it" on a layer:
   * sets "locked": true, increments "version" by 1, and sets "updated" to today's YYYY-MM-DD.
   */
  fun buildLockExportJson(
    layerKey: String,
    bundle: LoadedLayersBundle,
    todayDate: String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()),
  ): String {
    val raw = bundle.effectiveRawJson[layerKey] ?: "{}"
    val rootObj = jsonParser.parseToJsonElement(raw).jsonObject
    val currentVersion =
      when (layerKey) {
        "persona" -> bundle.persona.version
        "style" -> bundle.style.version
        "engine" -> bundle.engine.version
        "hard_rules" -> bundle.hardRules.version
        else -> 1
      }
    val newVersion = currentVersion + 1

    val updatedEntries = linkedMapOf<String, JsonElement>()
    if (layerKey != "hard_rules") {
      updatedEntries["layer"] = rootObj["layer"] ?: JsonPrimitive(layerKey)
    }
    updatedEntries["version"] = JsonPrimitive(newVersion)
    updatedEntries["locked"] = JsonPrimitive(true)
    updatedEntries["updated"] = JsonPrimitive(todayDate)

    for ((k, v) in rootObj) {
      if (k != "layer" && k != "version" && k != "locked" && k != "updated") {
        updatedEntries[k] = v
      }
    }
    return prettyJson.encodeToString(JsonObject.serializer(), JsonObject(updatedEntries))
  }

  /**
   * Clarification #5: The Lock it Copy button copies the instruction line and the JSON together, as one text.
   */
  fun formatLockCopyPayload(fileName: String, exportedJson: String): String {
    return "Replace app/src/main/assets/config/$fileName with exactly this JSON, verbatim:\n\n$exportedJson"
  }

  /**
   * Creates and saves a pending lock record in layer_locks ONLY if the layer's status is Tested
   * and no profile has failing tests on its current fingerprint. Returns the exported JSON string,
   * or null if the layer is not eligible to lock.
   */
  suspend fun tryCreatePendingLock(
    dao: com.example.data.RiviaDao,
    layerKey: String,
    bundle: LoadedLayersBundle,
    fingerprints: Map<String, String>,
    testRuns: List<LayerTestRunEntity>,
    todayDate: String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()),
  ): String? {
    val status = computeLayerStatus(layerKey, bundle, fingerprints, testRuns)
    if (status != LayerStatus.TESTED) {
      return null
    }
    val hasFailing =
      REQUIRED_PROFILES.any { prof ->
        val fp = fingerprints[prof] ?: ""
        computeProfileTestState(prof, fp, bundle.tests, testRuns) == ProfileTestState.FAILED
      }
    if (hasFailing) {
      return null
    }
    val exportedJson = buildLockExportJson(layerKey, bundle, todayDate)
    val canonicalSha = canonicalJsonSha256(exportedJson)
    val bundledRaw = bundle.bundledRawJson[layerKey] ?: ""
    val currentBundledSha =
      runCatching { canonicalJsonSha256(bundledRaw) }.getOrDefault("")

    dao.saveLayerLock(
      LayerLockEntity(
        layerKey = layerKey,
        pendingCanonicalSha256 = canonicalSha,
        lastSeenBundledSha256 = currentBundledSha,
        exportedJson = exportedJson,
        mismatchWarning = null,
      )
    )
    return exportedJson
  }

  /**
   * Deletes the pending lock record for layerKey from layer_locks and leaves any phone draft
   * in layer_drafts untouched.
   */
  suspend fun cancelPendingLock(
    dao: com.example.data.RiviaDao,
    layerKey: String,
  ) {
    dao.deleteLayerLock(layerKey)
  }

  /**
   * Formats specific H9 empty-reply reasons (never just "incomplete"):
   * - output tokens ran out and thought tokens were reported:
   *   "No reply: max output tokens used by thinking (<thought tokens> of <max>)"
   * - output tokens ran out otherwise:
   *   "No reply: max output tokens reached"
   * - safety block:
   *   "No reply: blocked for safety (<category>)"
   * - anything else:
   *   "No reply: empty completion", plus the API's status or reason if it gave one.
   */
  fun formatH9EmptyReplyReason(
    finishReason: String?,
    safetyBlockReason: String?,
    completionStatus: String?,
    thoughtTokens: Int,
    outputTokens: Int,
    configuredMaxOutputTokens: Int?,
  ): String {
    val cleanFinish = finishReason?.trim().orEmpty()
    val cleanSafety = safetyBlockReason?.trim().orEmpty()
    val cleanStatus = completionStatus?.trim().orEmpty()

    // 1. Safety block
    val isSafetyBlock =
      cleanSafety.isNotEmpty() ||
        cleanFinish.contains("safety", ignoreCase = true) ||
        cleanFinish.contains("blocklist", ignoreCase = true) ||
        cleanFinish.contains("prohibited_content", ignoreCase = true) ||
        cleanFinish.contains("spii", ignoreCase = true) ||
        cleanFinish.contains("civic_integrity", ignoreCase = true)

    if (isSafetyBlock) {
      val rawCat = cleanSafety.ifEmpty { cleanFinish }
      val category =
        if (rawCat.equals("SAFETY", ignoreCase = true) || rawCat.isBlank()) {
          "unspecified"
        } else {
          rawCat
        }
      return "No reply: blocked for safety ($category)"
    }

    // 2. Output tokens ran out
    val finishLower = cleanFinish.lowercase()
    val statusLower = cleanStatus.lowercase()
    val isFinishMaxTokens =
      finishLower == "max_tokens" ||
        finishLower == "max_output_tokens" ||
        finishLower == "length" ||
        finishLower.contains("max_token") ||
        finishLower.contains("max_output_token")
    val hitConfiguredCap =
      configuredMaxOutputTokens != null &&
        configuredMaxOutputTokens > 0 &&
        (thoughtTokens + outputTokens) >= configuredMaxOutputTokens
    val incompleteWithThinking =
      statusLower == "incomplete" && thoughtTokens > 0 && cleanFinish.isEmpty()

    if (isFinishMaxTokens || hitConfiguredCap || incompleteWithThinking) {
      return if (thoughtTokens > 0) {
        val maxVal =
          configuredMaxOutputTokens?.takeIf { it > 0 }
            ?: (thoughtTokens + outputTokens).takeIf { it > 0 }
            ?: thoughtTokens
        "No reply: max output tokens used by thinking ($thoughtTokens of $maxVal)"
      } else {
        "No reply: max output tokens reached"
      }
    }

    // 3. Anything else: "No reply: empty completion", plus the API's status or reason if it gave one
    val extraReason =
      cleanFinish.takeIf {
        it.isNotEmpty() &&
          !it.equals("stop", ignoreCase = true) &&
          !it.equals("completed", ignoreCase = true)
      }
        ?: cleanStatus.takeIf {
          it.isNotEmpty() && !it.equals("completed", ignoreCase = true)
        }

    return if (extraReason != null) {
      "No reply: empty completion ($extraReason)"
    } else {
      "No reply: empty completion"
    }
  }
}
