package com.example.model

import org.json.JSONArray
import org.json.JSONObject

data class ModePreset(
  val name: String,
  val model: String,
  val thinkingLevel: String,
) {
  fun toJson(): JSONObject =
    JSONObject().apply {
      put("name", name)
      put("model", model)
      put("thinkingLevel", thinkingLevel)
    }

  companion object {
    fun fromJson(obj: JSONObject): ModePreset =
      ModePreset(
        name = obj.optString("name", "Fast"),
        model = obj.optString("model", "gemini-3.5-flash-lite"),
        thinkingLevel = obj.optString("thinkingLevel", "minimal"),
      )

    fun defaultPresets(): List<ModePreset> =
      listOf(
        ModePreset(name = "Fast", model = "gemini-3.5-flash-lite", thinkingLevel = "minimal"),
        ModePreset(name = "Balanced", model = "gemini-3.8-flash", thinkingLevel = "medium"),
        ModePreset(name = "Deep", model = "gemini-4-argon", thinkingLevel = "high"),
      )

    fun listToJson(presets: List<ModePreset>): String {
      val arr = JSONArray()
      presets.forEach { arr.put(it.toJson()) }
      return arr.toString()
    }

    fun listFromJson(json: String): List<ModePreset> {
      if (json.isBlank()) return defaultPresets()
      return try {
        val arr = JSONArray(json)
        val list = mutableListOf<ModePreset>()
        for (i in 0 until arr.length()) {
          list.add(fromJson(arr.getJSONObject(i)))
        }
        if (list.size == 3) list else defaultPresets()
      } catch (_: Exception) {
        defaultPresets()
      }
    }
  }
}

data class RunSettings(
  val model: String = "gemini-3.8-flash",
  val liveModel: String = "gemini-3.5-flash-lite",
  val systemInstruction: String = DEFAULT_SYSTEM_INSTRUCTION,
  val styleForTypedChat: String = DEFAULT_TYPED_CHAT_STYLE,
  val styleForLiveMode: String = DEFAULT_LIVE_MODE_STYLE,
  val thinkingLevel: String = "medium", // model_default, minimal, low, medium, high
  val thoughtSummaries: String = "auto", // auto or none
  val maxOutputTokens: Int? = null,
  val stopSequences: List<String> = emptyList(),
  val seed: Int? = null,
  val temperature: Float? = null,
  val topP: Float? = null,
  val topK: Int? = null,
  // Tools
  val googleSearchEnabled: Boolean = false,
  val googleSearchMode: String = "web", // web or image
  val urlContextEnabled: Boolean = false,
  val codeExecutionEnabled: Boolean = false,
  val googleMapsEnabled: Boolean = false,
  val googleMapsLatitude: String = "",
  val googleMapsLongitude: String = "",
  val fileSearchEnabled: Boolean = false,
  val fileSearchStoreNames: List<String> = emptyList(),
  val toolChoice: String = "auto", // auto, any, none, validated
  // Structured output
  val structuredOutputEnabled: Boolean = false,
  val structuredOutputSchema: String = DEFAULT_SCHEMA_TEMPLATE,
  // Safety
  val safetyHateSpeech: String = "model_default",
  val safetyDangerousContent: String = "model_default",
  val safetyHarassment: String = "model_default",
  val safetySexuallyExplicit: String = "model_default",
  val safetyJailbreak: String = "model_default",
  val safetyMethod: String = "severity", // severity or probability
  // Service tier & storage
  val serviceTier: String = "standard", // standard, flex, priority
  val store: Boolean = true,
  val background: Boolean = false,
  val labels: Map<String, String> = emptyMap(),
  val extraJson: String = "",
  // UI toggles per chat
  val autoReadAloud: Boolean = false,
  val activeModeName: String = "Balanced", // Fast, Balanced, Deep, Custom
) {
  fun toJsonString(): String {
    val obj = JSONObject()
    obj.put("model", model)
    obj.put("liveModel", liveModel)
    obj.put("systemInstruction", systemInstruction)
    obj.put("styleForTypedChat", styleForTypedChat)
    obj.put("styleForLiveMode", styleForLiveMode)
    obj.put("thinkingLevel", thinkingLevel)
    obj.put("thoughtSummaries", thoughtSummaries)
    if (maxOutputTokens != null) obj.put("maxOutputTokens", maxOutputTokens)
    obj.put("stopSequences", JSONArray(stopSequences))
    if (seed != null) obj.put("seed", seed)
    if (temperature != null) obj.put("temperature", temperature.toDouble())
    if (topP != null) obj.put("topP", topP.toDouble())
    if (topK != null) obj.put("topK", topK)

    obj.put("googleSearchEnabled", googleSearchEnabled)
    obj.put("googleSearchMode", googleSearchMode)
    obj.put("urlContextEnabled", urlContextEnabled)
    obj.put("codeExecutionEnabled", codeExecutionEnabled)
    obj.put("googleMapsEnabled", googleMapsEnabled)
    obj.put("googleMapsLatitude", googleMapsLatitude)
    obj.put("googleMapsLongitude", googleMapsLongitude)
    obj.put("fileSearchEnabled", fileSearchEnabled)
    obj.put("fileSearchStoreNames", JSONArray(fileSearchStoreNames))
    obj.put("toolChoice", toolChoice)

    obj.put("structuredOutputEnabled", structuredOutputEnabled)
    obj.put("structuredOutputSchema", structuredOutputSchema)

    obj.put("safetyHateSpeech", safetyHateSpeech)
    obj.put("safetyDangerousContent", safetyDangerousContent)
    obj.put("safetyHarassment", safetyHarassment)
    obj.put("safetySexuallyExplicit", safetySexuallyExplicit)
    obj.put("safetyJailbreak", safetyJailbreak)
    obj.put("safetyMethod", safetyMethod)

    obj.put("serviceTier", serviceTier)
    obj.put("store", store)
    obj.put("background", if (store) background else false)

    val labelsObj = JSONObject()
    labels.forEach { (k, v) -> labelsObj.put(k, v) }
    obj.put("labels", labelsObj)
    obj.put("extraJson", extraJson)
    obj.put("autoReadAloud", autoReadAloud)
    obj.put("activeModeName", activeModeName)
    return obj.toString()
  }

  companion object {
    const val DEFAULT_SYSTEM_INSTRUCTION =
      "You are Rivia, Jordan's teammate. You never make up facts; when you don't know something, say so."

    const val DEFAULT_TYPED_CHAT_STYLE =
      "Markdown is fine. Add ElevenLabs audio tags in square brackets right before the words they affect, only where the emotion changes and at most one per clause, so replies sound right when read aloud."

    const val DEFAULT_LIVE_MODE_STYLE =
      "Your reply is spoken by ElevenLabs. Answer in one or two short sentences. Plain text only: no markdown, lists, emoji or URLs. Add audio tags in square brackets, such as [curious], [excited], [thoughtful], [softly], [sighs], [laughs] or [pause], right before the words they affect. Add a new tag only where the emotion changes, at most one per clause; never put contrasting tags on the same words, and use no accent or sound-effect tags. Use square brackets only for tags."

    const val DEFAULT_SCHEMA_TEMPLATE =
      """{"type":"object","properties":{"answer":{"type":"string"}},"required":["answer"]}"""

    fun allowedThinkingLevels(modelId: String): List<String> {
      val clean = modelId.removePrefix("models/").lowercase()
      return when {
        clean == "gemini-4-argon" || clean == "gemini-3.8-flash" ->
          listOf("model_default", "low", "medium", "high")
        clean == "gemini-3.6-flash" ||
          clean == "gemini-3.5-flash-lite" ||
          clean == "gemini-3-flash-preview" ->
          listOf("model_default", "minimal", "low", "medium", "high")
        clean == "gemini-3.1-pro-preview" ->
          listOf("model_default", "low", "medium", "high")
        else -> listOf("model_default", "minimal", "low", "medium", "high")
      }
    }

    /**
     * Temperature, top-p and top-k: only for models older than Gemini 3.6.
     * For Gemini 3.6 and later and for Gemini 4, show "Ignored by this model" instead.
     */
    fun isSamplingSupported(modelId: String): Boolean {
      val clean = modelId.removePrefix("models/").lowercase()
      if (clean.startsWith("gemini-4")) return false
      if (clean.startsWith("gemini-3.6") ||
        clean.startsWith("gemini-3.7") ||
        clean.startsWith("gemini-3.8") ||
        clean.startsWith("gemini-3.9")
      ) {
        return false
      }
      return true
    }

    fun isGemini3Model(modelId: String): Boolean {
      val clean = modelId.removePrefix("models/").lowercase()
      return clean.startsWith("gemini-3")
    }

    /**
     * Validates the JSON schema for Structured Output and returns any errors and warnings.
     */
    fun validateSchema(
      schemaJson: String,
      hasToolsEnabled: Boolean,
      modelId: String,
    ): SchemaValidationResult {
      val warnings = mutableListOf<String>()
      if (schemaJson.isBlank()) {
        return SchemaValidationResult(isValid = false, error = "Schema JSON cannot be empty", warnings = warnings)
      }
      try {
        JSONObject(schemaJson)
      } catch (e: Exception) {
        return SchemaValidationResult(
          isValid = false,
          error = "Invalid JSON: ${e.message}",
          warnings = warnings,
        )
      }
      val ignoredKeys = listOf("minLength", "maxLength", "uniqueItems").filter {
        Regex(""""$it"\s*:""").containsMatchIn(schemaJson)
      }
      if (ignoredKeys.isNotEmpty()) {
        warnings.add("Note: ${ignoredKeys.joinToString(", ")} are ignored by the Gemini API.")
      }
      if (hasToolsEnabled && !isGemini3Model(modelId)) {
        warnings.add("Warning: A response schema combined with tools works only on Gemini 3 models.")
      } else if (hasToolsEnabled) {
        warnings.add("Note: Combining a schema with tools is supported only on Gemini 3 models.")
      }
      return SchemaValidationResult(isValid = true, error = null, warnings = warnings)
    }

    fun fromJsonString(json: String): RunSettings {
      if (json.isBlank()) return RunSettings()
      return try {
        val obj = JSONObject(json)
        val stopSeqList = mutableListOf<String>()
        obj.optJSONArray("stopSequences")?.let { arr ->
          for (i in 0 until arr.length()) stopSeqList.add(arr.getString(i))
        }
        val storeNames = mutableListOf<String>()
        obj.optJSONArray("fileSearchStoreNames")?.let { arr ->
          for (i in 0 until arr.length()) storeNames.add(arr.getString(i))
        }
        val labelsMap = mutableMapOf<String, String>()
        obj.optJSONObject("labels")?.let { lObj ->
          val keys = lObj.keys()
          while (keys.hasNext()) {
            val k = keys.next()
            labelsMap[k] = lObj.optString(k, "")
          }
        }
        val storeVal = obj.optBoolean("store", true)
        RunSettings(
          model = obj.optString("model", "gemini-3.8-flash"),
          liveModel = obj.optString("liveModel", "gemini-3.5-flash-lite"),
          systemInstruction = obj.optString("systemInstruction", DEFAULT_SYSTEM_INSTRUCTION),
          styleForTypedChat = obj.optString("styleForTypedChat", DEFAULT_TYPED_CHAT_STYLE),
          styleForLiveMode = obj.optString("styleForLiveMode", DEFAULT_LIVE_MODE_STYLE),
          thinkingLevel = obj.optString("thinkingLevel", "medium"),
          thoughtSummaries = obj.optString("thoughtSummaries", "auto"),
          maxOutputTokens = if (obj.has("maxOutputTokens")) obj.optInt("maxOutputTokens") else null,
          stopSequences = stopSeqList,
          seed = if (obj.has("seed")) obj.optInt("seed") else null,
          temperature = if (obj.has("temperature")) obj.optDouble("temperature").toFloat() else null,
          topP = if (obj.has("topP")) obj.optDouble("topP").toFloat() else null,
          topK = if (obj.has("topK")) obj.optInt("topK") else null,
          googleSearchEnabled = obj.optBoolean("googleSearchEnabled", false),
          googleSearchMode = obj.optString("googleSearchMode", "web"),
          urlContextEnabled = obj.optBoolean("urlContextEnabled", false),
          codeExecutionEnabled = obj.optBoolean("codeExecutionEnabled", false),
          googleMapsEnabled = obj.optBoolean("googleMapsEnabled", false),
          googleMapsLatitude = obj.optString("googleMapsLatitude", ""),
          googleMapsLongitude = obj.optString("googleMapsLongitude", ""),
          fileSearchEnabled = obj.optBoolean("fileSearchEnabled", false),
          fileSearchStoreNames = storeNames,
          toolChoice = obj.optString("toolChoice", "auto"),
          structuredOutputEnabled = obj.optBoolean("structuredOutputEnabled", false),
          structuredOutputSchema = obj.optString("structuredOutputSchema", DEFAULT_SCHEMA_TEMPLATE),
          safetyHateSpeech = obj.optString("safetyHateSpeech", "model_default"),
          safetyDangerousContent = obj.optString("safetyDangerousContent", "model_default"),
          safetyHarassment = obj.optString("safetyHarassment", "model_default"),
          safetySexuallyExplicit = obj.optString("safetySexuallyExplicit", "model_default"),
          safetyJailbreak = obj.optString("safetyJailbreak", "model_default"),
          safetyMethod = obj.optString("safetyMethod", "severity"),
          serviceTier = obj.optString("serviceTier", "standard"),
          store = storeVal,
          background = if (storeVal) obj.optBoolean("background", false) else false,
          labels = labelsMap,
          extraJson = obj.optString("extraJson", ""),
          autoReadAloud = obj.optBoolean("autoReadAloud", false),
          activeModeName = obj.optString("activeModeName", "Balanced"),
        )
      } catch (_: Exception) {
        RunSettings()
      }
    }
  }

  fun hasAnyToolEnabled(): Boolean =
    googleSearchEnabled ||
      urlContextEnabled ||
      codeExecutionEnabled ||
      googleMapsEnabled ||
      fileSearchEnabled
}

data class SchemaValidationResult(
  val isValid: Boolean,
  val error: String?,
  val warnings: List<String>,
)
