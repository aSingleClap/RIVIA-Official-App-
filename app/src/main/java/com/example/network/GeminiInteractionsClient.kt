package com.example.network

import com.example.data.CachedModelEntity
import com.example.data.MessageEntity
import com.example.model.RunSettings
import com.example.plugin.PluginRegistry
import com.example.plugin.RiviaChannel
import com.example.security.SecureKeyRepository
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

sealed class GeminiStreamEvent {
  data class InteractionCreated(val interactionId: String, val serviceTierHeader: String?) :
    GeminiStreamEvent()

  data class TextDelta(val text: String) : GeminiStreamEvent()

  data class ThoughtSummaryDelta(val thoughtText: String) : GeminiStreamEvent()

  data class ReplaceReplyText(val fullText: String, val fullThought: String) : GeminiStreamEvent()

  data class StatusUpdate(val status: String) : GeminiStreamEvent()

  data class StepCompleted(val rawStep: JSONObject) : GeminiStreamEvent()

  data class Completed(
    val interactionId: String?,
    val serviceTier: String,
    val inputTokens: Int,
    val outputTokens: Int,
    val thoughtTokens: Int,
    val rawSteps: JSONArray,
    val completionStatus: String = "completed",
    val finishReason: String? = null,
    val safetyBlockReason: String? = null,
  ) : GeminiStreamEvent()

  data class RequestDispatched(
    val requestSentEpochMs: Long,
    val systemInstructionChars: Int,
  ) : GeminiStreamEvent()

  data class FirstByteReceived(
    val firstByteEpochMs: Long,
  ) : GeminiStreamEvent()

  data class Error(val message: String, val httpCode: Int? = null) : GeminiStreamEvent()
}

class GeminiInteractionsClient {
  companion object {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    private val EXCLUDED_MODEL_SUBSTRINGS =
      listOf(
        "tts",
        "image",
        "banana",
        "embedding",
        "live",
        "transcribe",
        "lyria",
        "omni",
        "robotics",
      )
  }

  // Zero HTTP logging of headers or bodies so API keys are never logged.
  private val baseHttpClient: OkHttpClient =
    OkHttpClient.Builder()
      .connectTimeout(30, TimeUnit.SECONDS)
      .writeTimeout(60, TimeUnit.SECONDS)
      .readTimeout(5, TimeUnit.MINUTES)
      .pingInterval(15, TimeUnit.SECONDS)
      .retryOnConnectionFailure(true)
      .build()

  @Volatile private var activeCall: Call? = null

  fun cancelActiveRequest() {
    activeCall?.cancel()
    activeCall = null
  }

  private fun clientForTier(serviceTier: String): OkHttpClient {
    val readTimeoutMinutes = if (serviceTier.equals("flex", ignoreCase = true)) 16L else 5L
    return baseHttpClient
      .newBuilder()
      .readTimeout(readTimeoutMinutes, TimeUnit.MINUTES)
      .build()
  }

  /**
   * Builds the conversation input array from saved messages and their raw steps.
   * Used when store is off, or when the server no longer has the earlier interaction.
   */
  fun buildFullConversationSteps(
    priorMessages: List<MessageEntity>,
    newUserText: String,
  ): JSONArray {
    val steps = JSONArray()
    for (msg in priorMessages) {
      if (msg.status == "error") continue
      if (msg.role == "user") {
        steps.put(
          JSONObject().apply {
            put("type", "user_input")
            put("content", msg.content)
          }
        )
      } else if (msg.role == "assistant") {
        val rawSaved =
          try {
            JSONArray(msg.rawStepsJson)
          } catch (_: Exception) {
            JSONArray()
          }
        if (rawSaved.length() > 0) {
          for (i in 0 until rawSaved.length()) {
            val rawObj = rawSaved.optJSONObject(i) ?: continue
            steps.put(sanitizeStepObject(rawObj))
          }
        } else if (msg.content.isNotBlank()) {
          steps.put(
            JSONObject().apply {
              put("type", "model_output")
              put(
                "content",
                JSONArray().apply {
                  put(
                    JSONObject().apply {
                      put("type", "text")
                      put("text", msg.content)
                    }
                  )
                },
              )
            }
          )
        }
      }
    }
    steps.put(
      JSONObject().apply {
        put("type", "user_input")
        put("content", newUserText)
      }
    )
    return steps
  }

  fun buildSingleTurnInput(userText: String): JSONArray {
    return JSONArray().apply {
      put(
        JSONObject().apply {
          put("type", "user_input")
          put("content", userText)
        }
      )
    }
  }

  /**
   * Builds the exact JSON body for POST /v1beta/interactions according to Section 3, Section 4,
   * and Fixes #4, #7, #11, #13.
   */
  fun buildRequestBodyJson(
    settings: RunSettings,
    channel: RiviaChannel,
    inputSteps: JSONArray,
    previousInteractionId: String?,
    stream: Boolean = true,
  ): JSONObject {
    val isLive = channel == RiviaChannel.LIVE_VOICE
    val chosenModel = if (isLive) settings.liveModel else settings.model
    val body = JSONObject()
    body.put("model", chosenModel.removePrefix("models/"))
    body.put("input", inputSteps)

    // System instruction + channel style sent on EVERY turn
    val channelStyle =
      when (channel) {
        RiviaChannel.LIVE_VOICE -> settings.styleForLiveMode
        RiviaChannel.TYPED_CHAT,
        RiviaChannel.EXTERNAL_BRIDGE -> settings.styleForTypedChat
      }
    val combinedSystemInstruction =
      buildString {
          if (settings.systemInstruction.isNotBlank()) {
            append(settings.systemInstruction.trim())
          }
          if (channelStyle.isNotBlank()) {
            if (isNotEmpty()) append("\n\n")
            append(channelStyle.trim())
          }
        }
        .trim()
    if (combinedSystemInstruction.isNotEmpty()) {
      body.put("system_instruction", combinedSystemInstruction)
    }

    // generation_config
    val genConfig = JSONObject()
    val allowedThinking = RunSettings.allowedThinkingLevels(chosenModel)
    // Fix #7: Use liveThinkingLevel for Live requests, thinkingLevel for typed chat
    val rawChosenThinkingLevel =
      if (isLive) settings.liveThinkingLevel else settings.thinkingLevel
    val effectiveThinkingLevel =
      if (
        rawChosenThinkingLevel == "minimal" &&
          !allowedThinking.contains("minimal") &&
          allowedThinking.contains("low")
      ) {
        "low"
      } else {
        rawChosenThinkingLevel
      }
    if (
      effectiveThinkingLevel != "model_default" &&
        allowedThinking.contains(effectiveThinkingLevel)
    ) {
      genConfig.put("thinking_level", effectiveThinkingLevel)
    }
    genConfig.put("thinking_summaries", settings.thoughtSummaries)

    if (settings.maxOutputTokens != null && settings.maxOutputTokens > 0) {
      genConfig.put("max_output_tokens", settings.maxOutputTokens)
    }
    if (settings.stopSequences.isNotEmpty()) {
      genConfig.put("stop_sequences", JSONArray(settings.stopSequences))
    }
    if (settings.seed != null) {
      genConfig.put("seed", settings.seed)
    }
    if (RunSettings.isSamplingSupported(chosenModel)) {
      if (settings.temperature != null) {
        genConfig.put("temperature", settings.temperature.toDouble())
      }
      if (settings.topP != null) {
        genConfig.put("top_p", settings.topP.toDouble())
      }
      if (settings.topK != null) {
        genConfig.put("top_k", settings.topK)
      }
    }

    // Tools (Fix #4 & Fix #13)
    val toolsArray = JSONArray()
    if (settings.googleSearchEnabled) {
      // Fix #4: Google Search is {"type":"google_search"}, plus "search_types":["image_search"] for image search; remove "mode".
      toolsArray.put(
        JSONObject().apply {
          put("type", "google_search")
          if (settings.googleSearchMode.equals("image", ignoreCase = true)) {
            put("search_types", JSONArray().apply { put("image_search") })
          }
        }
      )
    }
    if (settings.urlContextEnabled) {
      toolsArray.put(JSONObject().apply { put("type", "url_context") })
    }
    if (settings.codeExecutionEnabled) {
      toolsArray.put(JSONObject().apply { put("type", "code_execution") })
    }
    if (settings.googleMapsEnabled) {
      toolsArray.put(
        JSONObject().apply {
          put("type", "google_maps")
          val lat = settings.googleMapsLatitude.toDoubleOrNull()
          val lng = settings.googleMapsLongitude.toDoubleOrNull()
          if (lat != null) put("latitude", lat)
          if (lng != null) put("longitude", lng)
        }
      )
    }
    if (settings.fileSearchEnabled && settings.fileSearchStoreNames.isNotEmpty()) {
      // Fix #4: File search is {"type":"file_search","file_search_store_names":[...]}
      toolsArray.put(
        JSONObject().apply {
          put("type", "file_search")
          put("file_search_store_names", JSONArray(settings.fileSearchStoreNames))
        }
      )
    }
    // Fix #13: Each plug-in function goes in tools as its own {"type":"function","name":...,"description":...,"parameters":{...}} object
    for (fnToolObj in PluginRegistry.getEnabledFunctionToolObjects()) {
      toolsArray.put(fnToolObj)
    }

    if (toolsArray.length() > 0) {
      body.put("tools", toolsArray)
      genConfig.put("tool_choice", settings.toolChoice)
    }

    body.put("generation_config", genConfig)

    // Structured output
    if (settings.structuredOutputEnabled && settings.structuredOutputSchema.isNotBlank()) {
      try {
        val schemaObj = JSONObject(settings.structuredOutputSchema)
        body.put(
          "response_format",
          JSONObject().apply {
            put("type", "text")
            put("mime_type", "application/json")
            put("schema", schemaObj)
          },
        )
      } catch (_: Exception) {}
    }

    // Safety settings (Fix #4: items use "type" for the category: {"type":"hate_speech","threshold":"...","method":"..."})
    val safetyArray = JSONArray()
    val categories =
      listOf(
        "hate_speech" to settings.safetyHateSpeech,
        "dangerous_content" to settings.safetyDangerousContent,
        "harassment" to settings.safetyHarassment,
        "sexually_explicit" to settings.safetySexuallyExplicit,
        "jailbreak" to settings.safetyJailbreak,
      )
    for ((catType, threshold) in categories) {
      if (threshold != "model_default") {
        safetyArray.put(
          JSONObject().apply {
            put("type", catType)
            put("threshold", threshold)
            put("method", settings.safetyMethod)
          }
        )
      }
    }
    if (safetyArray.length() > 0) {
      body.put("safety_settings", safetyArray)
    }

    // Fix #4: Leave service_tier out of the body for standard; send it only for flex or priority.
    val tier = settings.serviceTier.lowercase()
    if (tier == "flex" || tier == "priority") {
      body.put("service_tier", tier)
    }

    body.put("store", settings.store)
    // Fix #11: Live always streams: ignore the background setting for Live turns.
    val effectiveBackground = !isLive && settings.store && settings.background
    if (effectiveBackground) {
      body.put("background", true)
    }

    if (settings.labels.isNotEmpty()) {
      val labelsObj = JSONObject()
      settings.labels.forEach { (k, v) ->
        if (k.isNotBlank()) labelsObj.put(k.trim(), v.trim())
      }
      if (labelsObj.length() > 0) {
        body.put("labels", labelsObj)
      }
    }

    if (settings.store && !previousInteractionId.isNullOrBlank()) {
      body.put("previous_interaction_id", previousInteractionId)
    }

    body.put("stream", if (effectiveBackground) false else stream)

    // Merge Extra JSON into top-level request body
    if (settings.extraJson.isNotBlank()) {
      try {
        val extraObj = JSONObject(settings.extraJson)
        val keys = extraObj.keys()
        while (keys.hasNext()) {
          val k = keys.next()
          body.put(k, extraObj.get(k))
        }
      } catch (_: Exception) {}
    }

    return body
  }

  /**
   * Builds a human-readable, redacted JSON preview for "Preview request" in Run Settings.
   */
  fun buildRedactedPreview(
    settings: RunSettings,
    channel: RiviaChannel,
    previousInteractionId: String?,
    geminiKey: String,
    elevenKey: String,
  ): String {
    val sampleInput = buildSingleTurnInput("<your next message>")
    val isBackground =
      channel != RiviaChannel.LIVE_VOICE && settings.store && settings.background
    val bodyObj =
      buildRequestBodyJson(
        settings = settings,
        channel = channel,
        inputSteps = sampleInput,
        previousInteractionId = previousInteractionId,
        stream = !isBackground,
      )
    val wrapper =
      JSONObject().apply {
        put(
          "endpoint",
          "POST $BASE_URL/interactions" + if (isBackground) "" else "?alt=sse",
        )
        put(
          "headers",
          JSONObject().apply {
            put("x-goog-api-key", "[REDACTED]")
            put("Content-Type", "application/json")
          },
        )
        put("body", bodyObj)
      }
    return SecureKeyRepository.redactText(wrapper.toString(2), geminiKey, elevenKey)
  }

  /**
   * Builds the exact JSON body for a Rivia chat turn from the active layers (Persona, Style, Engine, Hard rules).
   * Rivia chats take model settings only from engine.json:
   * - null max_output_tokens and "model_default" safety are left out of the request.
   * - standard service_tier is left out of the request; priority is sent.
   * - no tools, no response_format, no extraJson, no sampling parameters.
   */
  fun buildRiviaRequestBodyJson(
    engine: com.example.model.EngineLayerConfig,
    profileName: String,
    assembledSystemInstruction: String,
    inputSteps: JSONArray,
    previousInteractionId: String?,
    stream: Boolean = true,
  ): JSONObject {
    val normProfile = com.example.model.LayerConfigManager.normalizeProfileKey(profileName)
    val prof =
      engine.profiles[normProfile]
        ?: engine.profiles[engine.default_profile]
        ?: com.example.model.EngineProfileConfig("gemini-3.8-flash", "low")
    val chosenModel = prof.model.removePrefix("models/")

    val body = JSONObject()
    body.put("model", chosenModel)
    body.put("input", inputSteps)

    if (assembledSystemInstruction.isNotBlank()) {
      body.put("system_instruction", assembledSystemInstruction)
    }

    val genConfig = JSONObject()
    val allowedThinking = RunSettings.allowedThinkingLevels(chosenModel)
    val rawThinking = prof.thinking_level
    val effectiveThinking =
      if (
        rawThinking == "minimal" &&
          !allowedThinking.contains("minimal") &&
          allowedThinking.contains("low")
      ) {
        "low"
      } else {
        rawThinking
      }
    if (effectiveThinking != "model_default" && allowedThinking.contains(effectiveThinking)) {
      genConfig.put("thinking_level", effectiveThinking)
    }
    genConfig.put("thinking_summaries", engine.thought_summaries)

    if (engine.max_output_tokens != null && engine.max_output_tokens > 0) {
      genConfig.put("max_output_tokens", engine.max_output_tokens)
    }
    body.put("generation_config", genConfig)

    // Safety: "model_default" is left out of the request
    val safetyEl = engine.safety
    if (safetyEl is kotlinx.serialization.json.JsonArray && safetyEl.isNotEmpty()) {
      try {
        body.put("safety_settings", JSONArray(safetyEl.toString()))
      } catch (_: Exception) {}
    }

    // service_tier: standard is left out; priority is included
    val tier = engine.service_tier.lowercase()
    if (tier == "priority" || tier == "flex") {
      body.put("service_tier", tier)
    }

    body.put("store", engine.store)
    if (engine.store && !previousInteractionId.isNullOrBlank()) {
      body.put("previous_interaction_id", previousInteractionId)
    }
    body.put("stream", stream)
    return body
  }

  /**
   * Builds a redacted JSON preview for "Preview request" in Rivia Chat info (Text or Voice channel).
   */
  fun buildRiviaRedactedPreview(
    engine: com.example.model.EngineLayerConfig,
    profileName: String,
    assembledSystemInstruction: String,
    previousInteractionId: String?,
    geminiKey: String,
    elevenKey: String,
  ): String {
    val sampleInput = buildSingleTurnInput("<your next message>")
    val bodyObj =
      buildRiviaRequestBodyJson(
        engine = engine,
        profileName = profileName,
        assembledSystemInstruction = assembledSystemInstruction,
        inputSteps = sampleInput,
        previousInteractionId = previousInteractionId,
        stream = true,
      )
    val wrapper =
      JSONObject().apply {
        put("endpoint", "POST $BASE_URL/interactions?alt=sse")
        put(
          "headers",
          JSONObject().apply {
            put("x-goog-api-key", "[REDACTED]")
            put("Content-Type", "application/json")
          },
        )
        put("body", bodyObj)
      }
    return SecureKeyRepository.redactText(wrapper.toString(2), geminiKey, elevenKey)
  }

  /**
   * Streams or polls an interaction turn over REST, applying all retry, reconnect,
   * raw step rebuild, and background polling rules.
   * Fix #3 & #11: Live always streams (ignores background), and polling fallback / all network steps
   * are wrapped in try/catch so losing signal never crashes the app.
   */
  suspend fun sendTurn(
    apiKey: String,
    settings: RunSettings,
    channel: RiviaChannel,
    priorMessages: List<MessageEntity>,
    newUserText: String,
    previousInteractionId: String?,
    customBodyBuilder:
      ((inputSteps: JSONArray, prevInteractionId: String?, stream: Boolean) -> JSONObject)? =
      null,
    customStore: Boolean? = null,
    customServiceTier: String? = null,
    onEvent: suspend (GeminiStreamEvent) -> Unit,
  ) =
    withContext(Dispatchers.IO) {
      if (apiKey.isBlank()) {
        onEvent(
          GeminiStreamEvent.Error(
            "Gemini API key is not set. Paste your Gemini key in Settings."
          )
        )
        return@withContext
      }

      val effectiveStore = customStore ?: settings.store
      val effectiveTier = customServiceTier ?: settings.serviceTier

      // Fix #11: Live always streams: ignore the background setting for Live turns.
      // Rivia chats also always stream (customBodyBuilder != null -> useBackground = false).
      val useBackground =
        customBodyBuilder == null &&
          channel != RiviaChannel.LIVE_VOICE &&
          effectiveStore &&
          settings.background
      var usePreviousInteractionId =
        if (effectiveStore && !previousInteractionId.isNullOrBlank()) {
          previousInteractionId
        } else {
          null
        }

      var interactionId: String? = null
      var lastEventId: String? = null
      var serviceTierHeader: String = effectiveTier
      var inputTokens = 0
      var outputTokens = 0
      var thoughtTokens = 0
      var completionStatus = "completed"
      var finishReason: String? = null
      var safetyBlockReason: String? = null
      val rawSteps = JSONArray()
      var currentStepObj: JSONObject? = null
      var streamCompleted = false

      val client = clientForTier(effectiveTier)

      try {
        // 1. Background polling flow when background == true (and not Live)
        if (useBackground) {
          val inputSteps =
            if (usePreviousInteractionId != null) {
              buildSingleTurnInput(newUserText)
            } else {
              buildFullConversationSteps(priorMessages, newUserText)
            }
          val bodyJson =
            customBodyBuilder?.invoke(inputSteps, usePreviousInteractionId, false)
              ?: buildRequestBodyJson(
                settings = settings,
                channel = channel,
                inputSteps = inputSteps,
                previousInteractionId = usePreviousInteractionId,
                stream = false,
              )
          val postUrl = "$BASE_URL/interactions"
          val req =
            Request.Builder()
              .url(postUrl)
              .addHeader("x-goog-api-key", apiKey)
              .addHeader("Content-Type", "application/json")
              .post(bodyJson.toString().toRequestBody(JSON_MEDIA_TYPE))
              .build()

          val sysChars = bodyJson.optString("system_instruction", "").length
          val call = client.newCall(req)
          activeCall = call
          try {
            onEvent(
              GeminiStreamEvent.RequestDispatched(
                requestSentEpochMs = System.currentTimeMillis(),
                systemInstructionChars = sysChars,
              )
            )
            call.execute().use { resp ->
              onEvent(GeminiStreamEvent.FirstByteReceived(System.currentTimeMillis()))
              val respBody = resp.body?.string().orEmpty()
              val tierHdr = resp.header("x-gemini-service-tier")
              if (!tierHdr.isNullOrBlank()) serviceTierHeader = tierHdr
              if (!resp.isSuccessful) {
                onEvent(
                  GeminiStreamEvent.Error(
                    extractApiErrorMessage(resp.code, respBody),
                    resp.code,
                  )
                )
                return@withContext
              }
              val json = JSONObject(respBody)
              val createdId = extractInteractionId(json)
              if (createdId.isNullOrBlank()) {
                onEvent(
                  GeminiStreamEvent.Error("Background interaction returned no id: $respBody")
                )
                return@withContext
              }
              interactionId = createdId
              onEvent(GeminiStreamEvent.InteractionCreated(createdId, serviceTierHeader))
            }
            pollInteractionUntilDone(
              client = client,
              apiKey = apiKey,
              interactionId = interactionId!!,
              defaultServiceTier = serviceTierHeader,
              onEvent = onEvent,
            )
          } catch (e: CancellationException) {
            throw e
          } catch (e: Throwable) {
            if (call.isCanceled()) return@withContext
            onEvent(
              GeminiStreamEvent.Error(
                e.message ?: "Network error during background polling"
              )
            )
          } finally {
            activeCall = null
          }
          return@withContext
        }

        // 2. Streaming SSE flow (POST /v1beta/interactions?alt=sse)
        var postAttempt = 0
        val maxPostRetries = 3
        var postSucceededOrCreated = false

        while (!postSucceededOrCreated && postAttempt <= maxPostRetries) {
          currentCoroutineContext().ensureActive()
          val inputSteps =
            if (usePreviousInteractionId != null) {
              buildSingleTurnInput(newUserText)
            } else {
              buildFullConversationSteps(priorMessages, newUserText)
            }
          val bodyJson =
            customBodyBuilder?.invoke(inputSteps, usePreviousInteractionId, true)
              ?: buildRequestBodyJson(
                settings = settings,
                channel = channel,
                inputSteps = inputSteps,
                previousInteractionId = usePreviousInteractionId,
                stream = true,
              )

          val request =
            Request.Builder()
              .url("$BASE_URL/interactions?alt=sse")
              .addHeader("x-goog-api-key", apiKey)
              .addHeader("Accept", "text/event-stream")
              .addHeader("Content-Type", "application/json")
              .post(bodyJson.toString().toRequestBody(JSON_MEDIA_TYPE))
              .build()

          val sysChars = bodyJson.optString("system_instruction", "").length
          val call = client.newCall(request)
          activeCall = call

          try {
            onEvent(
              GeminiStreamEvent.RequestDispatched(
                requestSentEpochMs = System.currentTimeMillis(),
                systemInstructionChars = sysChars,
              )
            )
            call.execute().use { response ->
              val tierHdr = response.header("x-gemini-service-tier")
              if (!tierHdr.isNullOrBlank()) {
                serviceTierHeader = tierHdr
              }

              if (!response.isSuccessful) {
                val errorBody = response.body?.string().orEmpty()
                val code = response.code

                if (
                  usePreviousInteractionId != null &&
                    (code == 404 ||
                      (code == 400 &&
                        errorBody.contains("previous_interaction", ignoreCase = true)))
                ) {
                  usePreviousInteractionId = null
                  return@use
                }

                if (
                  (code == 429 || code == 500 || code == 503) && postAttempt < maxPostRetries
                ) {
                  postAttempt++
                  val backoffMs = (1000L * (1 shl postAttempt)).coerceAtMost(10_000L)
                  delay(backoffMs)
                  return@use
                }

                onEvent(
                  GeminiStreamEvent.Error(extractApiErrorMessage(code, errorBody), code)
                )
                return@withContext
              }

              var anyTextDeltaEmitted = false
              var streamErrorEmitted = false
              val reader =
                BufferedReader(
                  InputStreamReader(response.body!!.byteStream(), Charsets.UTF_8)
                )
              processSseStream(
                reader = reader,
                onFirstByte = { firstByteEpochMs ->
                  onEvent(GeminiStreamEvent.FirstByteReceived(firstByteEpochMs))
                },
                onInteractionCreated = { id ->
                  interactionId = id
                  postSucceededOrCreated = true
                  onEvent(GeminiStreamEvent.InteractionCreated(id, serviceTierHeader))
                },
                onEventId = { evId -> lastEventId = evId },
                onStepStart = { stepObj -> currentStepObj = stepObj },
                onTextDelta = { text ->
                  anyTextDeltaEmitted = true
                  appendDeltaToStep(currentStepObj, "text", text, null)
                  onEvent(GeminiStreamEvent.TextDelta(text))
                },
                onThoughtSummaryDelta = { thought ->
                  appendDeltaToStep(currentStepObj, "thought_summary", thought, null)
                  onEvent(GeminiStreamEvent.ThoughtSummaryDelta(thought))
                },
                onThoughtSignature = { sig ->
                  appendDeltaToStep(currentStepObj, "thought_signature", null, sig)
                },
                onStepStop = { stopObj ->
                  val finalized = finalizeStep(currentStepObj, stopObj)
                  rawSteps.put(finalized)
                  currentStepObj = null
                  onEvent(GeminiStreamEvent.StepCompleted(finalized))
                },
                onStatusUpdate = { status ->
                  if (status.equals("incomplete", ignoreCase = true)) {
                    completionStatus = "incomplete"
                  }
                  onEvent(GeminiStreamEvent.StatusUpdate(status))
                },
                onCompleted = { id, inTok, outTok, thTok, outputsArr, cStatus, fReason, sReason ->
                  if (!id.isNullOrBlank()) interactionId = id
                  inputTokens = inTok
                  outputTokens = outTok
                  thoughtTokens = thTok
                  if (cStatus.isNotBlank()) completionStatus = cStatus
                  if (!fReason.isNullOrBlank()) finishReason = fReason
                  if (!sReason.isNullOrBlank()) safetyBlockReason = sReason
                  if (outputsArr != null && outputsArr.length() > 0) {
                    if (rawSteps.length() == 0) {
                      for (i in 0 until outputsArr.length()) {
                        outputsArr.optJSONObject(i)?.let { rawSteps.put(sanitizeStepObject(it)) }
                      }
                    }
                    if (!anyTextDeltaEmitted) {
                      val extracted = extractTextAndThoughtFromOutputs(outputsArr)
                      if (extracted.first.isNotEmpty() || extracted.second.isNotEmpty()) {
                        onEvent(
                          GeminiStreamEvent.ReplaceReplyText(extracted.first, extracted.second)
                        )
                      }
                    }
                  }
                  streamCompleted = true
                },
                onStreamError = { errMsg ->
                  streamErrorEmitted = true
                  onEvent(GeminiStreamEvent.Error(errMsg))
                },
              )
              if (streamErrorEmitted) {
                return@withContext
              }
              postSucceededOrCreated = true
            }
          } catch (e: CancellationException) {
            throw e
          } catch (e: Throwable) {
            if (call.isCanceled()) return@withContext
            if (interactionId == null) {
              if (
                (e is SocketTimeoutException || e is IOException) &&
                  postAttempt < maxPostRetries
              ) {
                postAttempt++
                val backoffMs = (1000L * (1 shl postAttempt)).coerceAtMost(10_000L)
                delay(backoffMs)
                continue
              } else {
                onEvent(
                  GeminiStreamEvent.Error(
                    e.message ?: "Connection failed before interaction started"
                  )
                )
                return@withContext
              }
            } else {
              postSucceededOrCreated = true
            }
          }
        }

        // 3. If interaction.created arrived, but stream dropped before completion:
        if (!streamCompleted && interactionId != null) {
          val id = interactionId!!
          var reconnectAttempt = 0
          val maxReconnects = 3

          while (
            !streamCompleted && !lastEventId.isNullOrBlank() && reconnectAttempt < maxReconnects
          ) {
            currentCoroutineContext().ensureActive()
            reconnectAttempt++
            delay((1000L * reconnectAttempt).coerceAtMost(5000L))

            val reconnectUrl =
              "$BASE_URL/interactions/$id?alt=sse&stream=true&last_event_id=$lastEventId"
            val req =
              Request.Builder()
                .url(reconnectUrl)
                .addHeader("x-goog-api-key", apiKey)
                .addHeader("Accept", "text/event-stream")
                .get()
                .build()
            val call = client.newCall(req)
            activeCall = call
            try {
              call.execute().use { resp ->
                if (!resp.isSuccessful) {
                  lastEventId = null
                  return@use
                }
                val reader =
                  BufferedReader(
                    InputStreamReader(resp.body!!.byteStream(), Charsets.UTF_8)
                  )
                processSseStream(
                  reader = reader,
                  onInteractionCreated = {},
                  onEventId = { evId -> lastEventId = evId },
                  onStepStart = { stepObj -> currentStepObj = stepObj },
                  onTextDelta = { text ->
                    appendDeltaToStep(currentStepObj, "text", text, null)
                    onEvent(GeminiStreamEvent.TextDelta(text))
                  },
                  onThoughtSummaryDelta = { thought ->
                    appendDeltaToStep(currentStepObj, "thought_summary", thought, null)
                    onEvent(GeminiStreamEvent.ThoughtSummaryDelta(thought))
                  },
                  onThoughtSignature = { sig ->
                    appendDeltaToStep(currentStepObj, "thought_signature", null, sig)
                  },
                  onStepStop = { stopObj ->
                    val finalized = finalizeStep(currentStepObj, stopObj)
                    rawSteps.put(finalized)
                    currentStepObj = null
                    onEvent(GeminiStreamEvent.StepCompleted(finalized))
                  },
                  onStatusUpdate = { status ->
                    if (status.equals("incomplete", ignoreCase = true)) {
                      completionStatus = "incomplete"
                    }
                    onEvent(GeminiStreamEvent.StatusUpdate(status))
                  },
                  onCompleted = { _, inTok, outTok, thTok, _, cStatus, fReason, sReason ->
                    inputTokens = inTok
                    outputTokens = outTok
                    thoughtTokens = thTok
                    if (cStatus.isNotBlank()) completionStatus = cStatus
                    if (!fReason.isNullOrBlank()) finishReason = fReason
                    if (!sReason.isNullOrBlank()) safetyBlockReason = sReason
                    streamCompleted = true
                  },
                  onStreamError = { errMsg ->
                    onEvent(GeminiStreamEvent.Error(errMsg))
                  },
                )
              }
            } catch (e: CancellationException) {
              throw e
            } catch (_: Throwable) {
              if (call.isCanceled()) return@withContext
            }
          }

          // Fix #3: Wrap the polling fallback after a dropped stream in try/catch so losing signal never crashes the app.
          if (!streamCompleted) {
            try {
              pollInteractionUntilDone(
                client = client,
                apiKey = apiKey,
                interactionId = id,
                defaultServiceTier = serviceTierHeader,
                onEvent = onEvent,
              )
            } catch (e: CancellationException) {
              throw e
            } catch (e: Throwable) {
              if (activeCall?.isCanceled() == true) return@withContext
              onEvent(
                GeminiStreamEvent.Error(
                  e.message ?: "Stream dropped and polling recovery failed"
                )
              )
            } finally {
              activeCall = null
            }
            return@withContext
          }
        }

        activeCall = null
        if (streamCompleted) {
          onEvent(
            GeminiStreamEvent.Completed(
              interactionId = interactionId,
              serviceTier = serviceTierHeader,
              inputTokens = inputTokens,
              outputTokens = outputTokens,
              thoughtTokens = thoughtTokens,
              rawSteps = rawSteps,
              completionStatus = completionStatus,
              finishReason = finishReason,
              safetyBlockReason = safetyBlockReason,
            )
          )
        } else if (interactionId == null) {
          onEvent(
            GeminiStreamEvent.Error(
              "Stream closed before Gemini returned a response."
            )
          )
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Throwable) {
        activeCall = null
        onEvent(GeminiStreamEvent.Error(e.message ?: "Unexpected network failure"))
      }
    }

  /**
   * Polls GET /v1beta/interactions/{id} until its status is completed, failed, cancelled or incomplete,
   * replacing the partial reply with its outputs and reporting usage.
   */
  private suspend fun pollInteractionUntilDone(
    client: OkHttpClient,
    apiKey: String,
    interactionId: String,
    defaultServiceTier: String,
    onEvent: suspend (GeminiStreamEvent) -> Unit,
  ) {
    var serviceTier = defaultServiceTier
    var pollCount = 0
    var transientErrors = 0
    while (true) {
      currentCoroutineContext().ensureActive()
      val req =
        Request.Builder()
          .url("$BASE_URL/interactions/$interactionId")
          .addHeader("x-goog-api-key", apiKey)
          .get()
          .build()

      val call = client.newCall(req)
      activeCall = call
      try {
        call.execute().use { resp ->
          val tierHdr = resp.header("x-gemini-service-tier")
          if (!tierHdr.isNullOrBlank()) serviceTier = tierHdr
          val bodyStr = resp.body?.string().orEmpty()
          if (!resp.isSuccessful) {
            if (
              (resp.code == 429 || resp.code == 500 || resp.code == 503) &&
                transientErrors < 3
            ) {
              transientErrors++
            } else {
              onEvent(
                GeminiStreamEvent.Error(
                  extractApiErrorMessage(resp.code, bodyStr),
                  resp.code,
                )
              )
              return
            }
          } else {
            transientErrors = 0
            val json = JSONObject(bodyStr)
            val status = json.optString("status", "in_progress").lowercase()
            onEvent(GeminiStreamEvent.StatusUpdate(status))

            when (status) {
              "completed",
              "incomplete" -> {
                val outputs = json.optJSONArray("outputs") ?: JSONArray()
                val extracted = extractTextAndThoughtFromOutputs(outputs)
                onEvent(
                  GeminiStreamEvent.ReplaceReplyText(extracted.first, extracted.second)
                )
                val usage = json.optJSONObject("usage")
                val inTok = usage?.optInt("total_input_tokens", 0) ?: 0
                val outTok = usage?.optInt("total_output_tokens", 0) ?: 0
                val thTok = usage?.optInt("total_thought_tokens", 0) ?: 0
                val fReason = extractFinishReason(json, outputs)
                val sReason = extractSafetyBlockReason(json, outputs)
                onEvent(
                  GeminiStreamEvent.Completed(
                    interactionId = interactionId,
                    serviceTier = serviceTier,
                    inputTokens = inTok,
                    outputTokens = outTok,
                    thoughtTokens = thTok,
                    rawSteps = outputs,
                    completionStatus = status,
                    finishReason = fReason,
                    safetyBlockReason = sReason,
                  )
                )
                return
              }
              "failed",
              "cancelled" -> {
                val errObj = json.optJSONObject("error")
                val errMsg =
                  errObj?.optString("message")?.takeIf { it.isNotBlank() }
                    ?: "Interaction $status"
                onEvent(GeminiStreamEvent.Error(errMsg))
                return
              }
            }
          }
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Throwable) {
        if (call.isCanceled()) return
        if (transientErrors < 3) {
          transientErrors++
        } else {
          onEvent(
            GeminiStreamEvent.Error(
              e.message ?: "Connection lost while polling interaction"
            )
          )
          return
        }
      }
      pollCount++
      delay(if (pollCount < 5) 1500L else 2500L)
    }
  }

  private fun extractTextAndThoughtFromOutputs(outputs: JSONArray): Pair<String, String> {
    val textSb = StringBuilder()
    val thoughtSb = StringBuilder()
    for (i in 0 until outputs.length()) {
      val step = outputs.optJSONObject(i) ?: continue
      val contentArr = step.optJSONArray("content")
      if (contentArr != null) {
        for (j in 0 until contentArr.length()) {
          val part = contentArr.optJSONObject(j) ?: continue
          when (part.optString("type")) {
            "text" -> textSb.append(part.optString("text"))
            "thought_summary" -> thoughtSb.append(part.optString("text"))
          }
        }
      } else {
        val contentObj = step.optJSONObject("content")
        if (contentObj != null) {
          when (contentObj.optString("type")) {
            "text" -> textSb.append(contentObj.optString("text"))
            "thought_summary" -> thoughtSb.append(contentObj.optString("text"))
          }
        } else if (step.optString("type") == "text") {
          textSb.append(step.optString("text"))
        }
      }
    }
    return textSb.toString() to thoughtSb.toString()
  }

  private suspend fun processSseStream(
    reader: BufferedReader,
    onFirstByte: (suspend (Long) -> Unit)? = null,
    onInteractionCreated: suspend (String) -> Unit,
    onEventId: (String) -> Unit,
    onStepStart: (JSONObject) -> Unit,
    onTextDelta: suspend (String) -> Unit,
    onThoughtSummaryDelta: suspend (String) -> Unit,
    onThoughtSignature: (String) -> Unit,
    onStepStop: suspend (JSONObject) -> Unit,
    onStatusUpdate: suspend (String) -> Unit,
    onCompleted: suspend (String?, Int, Int, Int, JSONArray?, String, String?, String?) -> Unit,
    onStreamError: suspend (String) -> Unit,
  ) {
    var currentEventName: String? = null
    val dataBuffer = StringBuilder()
    var firstByteEmitted = false

    suspend fun dispatchBufferedEvent(eventName: String?, rawData: String) {
      val trimmedData = rawData.trim()
      if (eventName.equals("done", ignoreCase = true) || trimmedData == "[DONE]") {
        return
      }
      if (trimmedData.isEmpty()) return

      val json =
        try {
          JSONObject(trimmedData)
        } catch (_: Exception) {
          return
        }

      val jsonEventId =
        json.optString("event_id").takeIf { it.isNotBlank() }
          ?: json.optJSONObject("event")?.optString("event_id")?.takeIf { it.isNotBlank() }
      if (!jsonEventId.isNullOrBlank()) {
        onEventId(jsonEventId)
      }

      val resolvedType =
        eventName?.takeIf { it.isNotBlank() && it != "message" }
          ?: json.optString("event_type").takeIf { it.isNotBlank() }
          ?: json.optJSONObject("event")?.optString("event_type")?.takeIf { it.isNotBlank() }
          ?: json.optString("event").takeIf { it.isNotBlank() }
          ?: json.optString("type").takeIf {
            it.contains(".") || it == "error" || it == "done"
          }
          ?: ""

      when (resolvedType) {
        "interaction.created",
        "interaction.start" -> {
          val id = extractInteractionId(json)
          if (!id.isNullOrBlank()) {
            onInteractionCreated(id)
          }
        }
        "step.start",
        "content.start" -> {
          val step =
            json.optJSONObject("step")
              ?: JSONObject().apply {
                val stepType =
                  json.optString("type").takeIf {
                    it.isNotBlank() && !it.contains(".")
                  } ?: "model_output"
                put("type", stepType)
              }
          if (!step.has("content")) {
            step.put("content", JSONArray())
          }
          onStepStart(step)
        }
        "step.delta",
        "content.delta" -> {
          val delta = json.optJSONObject("delta") ?: json
          val deltaType =
            delta.optString("type").takeIf { it.isNotBlank() && !it.contains(".") }
              ?: delta.optJSONObject("content")?.optString("type").orEmpty()

          when (deltaType) {
            "text" -> {
              val txt =
                delta.optString("text").takeIf { it.isNotEmpty() }
                  ?: delta.optJSONObject("content")?.optString("text").orEmpty()
              if (txt.isNotEmpty()) {
                onTextDelta(txt)
              }
            }
            "thought_summary",
            "thought" -> {
              val thoughtTxt =
                delta.optJSONObject("content")?.optString("text")?.takeIf {
                  it.isNotEmpty()
                } ?: delta.optString("text").orEmpty()
              if (thoughtTxt.isNotEmpty()) {
                onThoughtSummaryDelta(thoughtTxt)
              }
            }
            "thought_signature" -> {
              val sig =
                delta.optString("thought_signature").takeIf { it.isNotEmpty() }
                  ?: delta.optJSONObject("content")?.optString("thought_signature")
                  ?: delta.optString("signature").orEmpty()
              if (sig.isNotEmpty()) {
                onThoughtSignature(sig)
              }
            }
            else -> {
              val txt =
                delta.optString("text").takeIf { it.isNotEmpty() }
                  ?: delta.optJSONObject("content")?.optString("text").orEmpty()
              if (txt.isNotEmpty()) {
                onTextDelta(txt)
              }
              val sig =
                delta.optString("thought_signature").takeIf { it.isNotEmpty() }
                  ?: delta.optJSONObject("content")?.optString("thought_signature").orEmpty()
              if (sig.isNotEmpty()) {
                onThoughtSignature(sig)
              }
            }
          }
        }
        "step.stop",
        "content.stop" -> {
          val stopStep = json.optJSONObject("step") ?: json
          onStepStop(stopStep)
        }
        "interaction.status_update",
        "status_update" -> {
          val status =
            json.optString("status").takeIf { it.isNotBlank() }
              ?: json.optJSONObject("interaction")?.optString("status").orEmpty()
          if (status.isNotBlank()) {
            onStatusUpdate(status)
          }
        }
        "interaction.completed",
        "interaction.complete" -> {
          val interactionObj = json.optJSONObject("interaction") ?: json
          val id = extractInteractionId(interactionObj) ?: extractInteractionId(json)
          val usage = interactionObj.optJSONObject("usage") ?: json.optJSONObject("usage")
          val inTok = usage?.optInt("total_input_tokens", 0) ?: 0
          val outTok = usage?.optInt("total_output_tokens", 0) ?: 0
          val thTok = usage?.optInt("total_thought_tokens", 0) ?: 0
          val outputs =
            interactionObj.optJSONArray("outputs") ?: json.optJSONArray("outputs")
          val cStatus =
            interactionObj
              .optString("status")
              .ifBlank { json.optString("status", "completed") }
              .lowercase()
          val fReason = extractFinishReason(interactionObj, outputs) ?: extractFinishReason(json, outputs)
          val sReason =
            extractSafetyBlockReason(interactionObj, outputs)
              ?: extractSafetyBlockReason(json, outputs)
          onCompleted(id, inTok, outTok, thTok, outputs, cStatus, fReason, sReason)
        }
        "error",
        "interaction.failed" -> {
          val errObj = json.optJSONObject("error") ?: json
          val msg =
            errObj.optString("message").takeIf { it.isNotBlank() } ?: json.toString()
          onStreamError(msg)
        }
        else -> {
          // Unrecognized event: skip safely without throwing
        }
      }
    }

    var line: String?
    while (reader.readLine().also { line = it } != null) {
      currentCoroutineContext().ensureActive()
      if (!firstByteEmitted) {
        firstByteEmitted = true
        onFirstByte?.invoke(System.currentTimeMillis())
      }
      val l = line!!
      when {
        l.isEmpty() -> {
          if (dataBuffer.isNotEmpty() || currentEventName != null) {
            val ev = currentEventName
            val d = dataBuffer.toString()
            currentEventName = null
            dataBuffer.setLength(0)
            if (ev.equals("done", ignoreCase = true) || d.trim() == "[DONE]") {
              break
            }
            dispatchBufferedEvent(ev, d)
          }
        }
        l.startsWith("id:") -> {
          val sseId = l.removePrefix("id:").trim()
          if (sseId.isNotEmpty()) {
            onEventId(sseId)
          }
        }
        l.startsWith("event:") -> {
          currentEventName = l.removePrefix("event:").trim()
        }
        l.startsWith("data:") -> {
          val dataPart = l.removePrefix("data:").trimStart()
          if (dataPart == "[DONE]") {
            break
          }
          if (dataBuffer.isNotEmpty()) dataBuffer.append("\n")
          dataBuffer.append(dataPart)
        }
      }
    }
    if (dataBuffer.isNotEmpty()) {
      dispatchBufferedEvent(currentEventName, dataBuffer.toString())
    }
  }

  private fun appendDeltaToStep(
    stepObj: JSONObject?,
    deltaType: String,
    text: String?,
    signature: String?,
  ) {
    if (stepObj == null) return
    val contentArr =
      stepObj.optJSONArray("content")
        ?: JSONArray().also { stepObj.put("content", it) }

    when (deltaType) {
      "text" -> {
        if (text.isNullOrEmpty()) return
        val lastIdx = contentArr.length() - 1
        val lastItem = if (lastIdx >= 0) contentArr.optJSONObject(lastIdx) else null
        if (lastItem != null && lastItem.optString("type") == "text") {
          lastItem.put("text", lastItem.optString("text") + text)
        } else {
          contentArr.put(
            JSONObject().apply {
              put("type", "text")
              put("text", text)
            }
          )
        }
      }
      "thought_summary" -> {
        if (text.isNullOrEmpty()) return
        val lastIdx = contentArr.length() - 1
        val lastItem = if (lastIdx >= 0) contentArr.optJSONObject(lastIdx) else null
        if (lastItem != null && lastItem.optString("type") == "thought_summary") {
          lastItem.put("text", lastItem.optString("text") + text)
        } else {
          contentArr.put(
            JSONObject().apply {
              put("type", "thought_summary")
              put("text", text)
            }
          )
        }
      }
      "thought_signature" -> {
        if (signature.isNullOrEmpty()) return
        stepObj.put("thought_signature", signature)
        contentArr.put(
          JSONObject().apply {
            put("type", "thought_signature")
            put("thought_signature", signature)
          }
        )
      }
    }
  }

  private fun sanitizeStepObject(step: JSONObject): JSONObject {
    val clean = JSONObject(step.toString())
    clean.remove("event_type")
    clean.remove("event_id")
    clean.remove("index")
    return clean
  }

  private fun finalizeStep(currentStep: JSONObject?, stopStep: JSONObject): JSONObject {
    if (currentStep == null) return sanitizeStepObject(stopStep)
    val keys = stopStep.keys()
    while (keys.hasNext()) {
      val k = keys.next()
      if (k == "event_type" || k == "event_id" || k == "index") continue
      if (!currentStep.has(k)) {
        currentStep.put(k, stopStep.get(k))
      }
    }
    return sanitizeStepObject(currentStep)
  }

  private fun extractInteractionId(json: JSONObject): String? {
    json.optJSONObject("interaction")?.optString("id")?.takeIf { it.isNotBlank() }?.let {
      return it
    }
    json.optString("interaction_id").takeIf { it.isNotBlank() }?.let { return it }
    json.optString("id").takeIf { it.isNotBlank() }?.let { return it }
    return null
  }

  private fun extractFinishReason(json: JSONObject, outputs: JSONArray?): String? {
    json.optString("finish_reason").takeIf { it.isNotBlank() }?.let { return it }
    json.optString("finishReason").takeIf { it.isNotBlank() }?.let { return it }
    json.optString("incomplete_reason").takeIf { it.isNotBlank() }?.let { return it }
    json.optString("incompleteReason").takeIf { it.isNotBlank() }?.let { return it }
    json.optString("status_reason").takeIf { it.isNotBlank() }?.let { return it }
    json.optJSONObject("incomplete_details")?.optString("reason")?.takeIf { it.isNotBlank() }?.let {
      return it
    }
    if (outputs != null) {
      for (i in 0 until outputs.length()) {
        val step = outputs.optJSONObject(i) ?: continue
        step.optString("finish_reason").takeIf { it.isNotBlank() }?.let { return it }
        step.optString("finishReason").takeIf { it.isNotBlank() }?.let { return it }
        step.optString("incomplete_reason").takeIf { it.isNotBlank() }?.let { return it }
      }
    }
    return null
  }

  private fun extractSafetyBlockReason(json: JSONObject, outputs: JSONArray?): String? {
    fun findSafetyCategoryInRatings(arr: JSONArray?): String? {
      if (arr == null) return null
      var highCategory: String? = null
      for (i in 0 until arr.length()) {
        val item = arr.optJSONObject(i) ?: continue
        val cat =
          item.optString("category").ifBlank { item.optString("type") }.trim()
        val blocked = item.optBoolean("blocked", false)
        if (blocked && cat.isNotEmpty()) return cat
        val prob = item.optString("probability").uppercase()
        val sev = item.optString("severity").uppercase()
        if (
          cat.isNotEmpty() &&
            (prob == "HIGH" || prob == "MEDIUM" || sev.contains("HIGH") || sev.contains("MEDIUM"))
        ) {
          if (highCategory == null) highCategory = cat
        }
      }
      return highCategory
    }

    findSafetyCategoryInRatings(json.optJSONArray("safety_ratings"))?.let { return it }
    findSafetyCategoryInRatings(json.optJSONArray("safetyRatings"))?.let { return it }
    if (outputs != null) {
      for (i in 0 until outputs.length()) {
        val step = outputs.optJSONObject(i) ?: continue
        findSafetyCategoryInRatings(step.optJSONArray("safety_ratings"))?.let { return it }
        findSafetyCategoryInRatings(step.optJSONArray("safetyRatings"))?.let { return it }
      }
    }
    json.optString("safety_category").takeIf { it.isNotBlank() }?.let { return it }
    json.optString("block_reason").takeIf { it.isNotBlank() }?.let { return it }
    json.optString("blockReason").takeIf { it.isNotBlank() }?.let { return it }
    val fReason = extractFinishReason(json, outputs)
    if (fReason != null && fReason.contains("safety", ignoreCase = true)) {
      return fReason
    }
    return null
  }

  private fun extractApiErrorMessage(code: Int, rawBody: String): String {
    if (rawBody.isBlank()) return "HTTP $code Error"
    return try {
      val obj = JSONObject(rawBody)
      val err = obj.optJSONObject("error")
      if (err != null) {
        val msg = err.optString("message")
        val status = err.optString("status")
        if (msg.isNotBlank()) {
          if (status.isNotBlank()) "HTTP $code ($status): $msg" else "HTTP $code: $msg"
        } else {
          "HTTP $code: $rawBody"
        }
      } else {
        "HTTP $code: $rawBody"
      }
    } catch (_: Exception) {
      "HTTP $code: $rawBody"
    }
  }

  suspend fun fetchModels(apiKey: String): Result<List<CachedModelEntity>> =
    withContext(Dispatchers.IO) {
      if (apiKey.isBlank()) {
        return@withContext Result.failure(
          IllegalArgumentException("Gemini API key is empty. Enter your key in Settings.")
        )
      }
      val request =
        Request.Builder()
          .url("$BASE_URL/models?pageSize=1000")
          .addHeader("x-goog-api-key", apiKey)
          .get()
          .build()

      try {
        baseHttpClient.newCall(request).execute().use { response ->
          val bodyStr = response.body?.string().orEmpty()
          if (!response.isSuccessful) {
            return@withContext Result.failure(
              IOException(extractApiErrorMessage(response.code, bodyStr))
            )
          }
          val root = JSONObject(bodyStr)
          val modelsArray = root.optJSONArray("models") ?: JSONArray()
          val filtered = mutableListOf<CachedModelEntity>()

          for (i in 0 until modelsArray.length()) {
            val m = modelsArray.optJSONObject(i) ?: continue
            val rawName = m.optString("name", "")
            val id = rawName.removePrefix("models/")
            if (id.isBlank()) continue

            val lowerId = id.lowercase()
            if (EXCLUDED_MODEL_SUBSTRINGS.any { lowerId.contains(it) }) {
              continue
            }

            val methodsArr = m.optJSONArray("supportedGenerationMethods")
            var supportsGenerateContent = false
            if (methodsArr != null) {
              for (j in 0 until methodsArr.length()) {
                if (methodsArr.optString(j) == "generateContent") {
                  supportsGenerateContent = true
                  break
                }
              }
            }
            if (!supportsGenerateContent) continue

            val displayName = m.optString("displayName", id)
            val inputLimit = m.optInt("inputTokenLimit", 0)
            val outputLimit = m.optInt("outputTokenLimit", 0)
            val maxTemp =
              if (m.has("maxTemperature")) m.optDouble("maxTemperature").toFloat() else 2.0f

            filtered.add(
              CachedModelEntity(
                id = id,
                displayName = displayName,
                inputTokenLimit = inputLimit,
                outputTokenLimit = outputLimit,
                maxTemperature = maxTemp,
              )
            )
          }
          Result.success(filtered)
        }
      } catch (e: Exception) {
        Result.failure(e)
      }
    }
}
