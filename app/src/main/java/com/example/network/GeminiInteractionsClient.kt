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
            steps.put(rawSaved.getJSONObject(i))
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
   * Builds the exact JSON body for POST /v1beta/interactions according to Section 3 & Section 4.
   */
  fun buildRequestBodyJson(
    settings: RunSettings,
    channel: RiviaChannel,
    inputSteps: JSONArray,
    previousInteractionId: String?,
    stream: Boolean = !settings.background,
  ): JSONObject {
    val chosenModel =
      if (channel == RiviaChannel.LIVE_VOICE) settings.liveModel else settings.model
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
    if (
      settings.thinkingLevel != "model_default" &&
        allowedThinking.contains(settings.thinkingLevel)
    ) {
      genConfig.put("thinking_level", settings.thinkingLevel)
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

    // Tools
    val toolsArray = JSONArray()
    if (settings.googleSearchEnabled) {
      toolsArray.put(
        JSONObject().apply {
          put("type", "google_search")
          put("mode", settings.googleSearchMode)
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
      toolsArray.put(
        JSONObject().apply {
          put("type", "file_search")
          put("store_names", JSONArray(settings.fileSearchStoreNames))
        }
      )
    }
    // Merge enabled plug-ins' tools
    val pluginDecls = PluginRegistry.getEnabledFunctionDeclarations()
    if (pluginDecls.length() > 0) {
      toolsArray.put(
        JSONObject().apply {
          put("type", "function_declarations")
          put("function_declarations", pluginDecls)
        }
      )
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
      } catch (_: Exception) {
        // Invalid JSON is caught by schema editor
      }
    }

    // Safety settings
    val safetyArray = JSONArray()
    val categories =
      listOf(
        "hate_speech" to settings.safetyHateSpeech,
        "dangerous_content" to settings.safetyDangerousContent,
        "harassment" to settings.safetyHarassment,
        "sexually_explicit" to settings.safetySexuallyExplicit,
        "jailbreak" to settings.safetyJailbreak,
      )
    for ((cat, threshold) in categories) {
      if (threshold != "model_default") {
        safetyArray.put(
          JSONObject().apply {
            put("category", cat)
            put("threshold", threshold)
            put("method", settings.safetyMethod)
          }
        )
      }
    }
    if (safetyArray.length() > 0) {
      body.put("safety_settings", safetyArray)
    }

    body.put("service_tier", settings.serviceTier)
    body.put("store", settings.store)
    val effectiveBackground = settings.store && settings.background
    body.put("background", effectiveBackground)

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
    val bodyObj =
      buildRequestBodyJson(
        settings = settings,
        channel = channel,
        inputSteps = sampleInput,
        previousInteractionId = previousInteractionId,
        stream = !(settings.store && settings.background),
      )
    val wrapper =
      JSONObject().apply {
        put(
          "endpoint",
          "POST $BASE_URL/interactions" +
            if (settings.store && settings.background) "" else "?alt=sse",
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
   * Streams or polls an interaction turn over REST, applying all retry, reconnect,
   * raw step rebuild, and background polling rules.
   */
  suspend fun sendTurn(
    apiKey: String,
    settings: RunSettings,
    channel: RiviaChannel,
    priorMessages: List<MessageEntity>,
    newUserText: String,
    previousInteractionId: String?,
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

      val useBackground = settings.store && settings.background
      var usePreviousInteractionId =
        if (settings.store && !previousInteractionId.isNullOrBlank()) {
          previousInteractionId
        } else {
          null
        }

      var interactionId: String? = null
      var lastEventId: String? = null
      var serviceTierHeader: String = settings.serviceTier
      var inputTokens = 0
      var outputTokens = 0
      var thoughtTokens = 0
      val rawSteps = JSONArray()
      var currentStepObj: JSONObject? = null
      var streamCompleted = false

      val client = clientForTier(settings.serviceTier)

      // 1. Background polling flow when background == true
      if (useBackground) {
        val inputSteps =
          if (usePreviousInteractionId != null) {
            buildSingleTurnInput(newUserText)
          } else {
            buildFullConversationSteps(priorMessages, newUserText)
          }
        val bodyJson =
          buildRequestBodyJson(
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

        val call = client.newCall(req)
        activeCall = call
        try {
          call.execute().use { resp ->
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
              onEvent(GeminiStreamEvent.Error("Background interaction returned no id: $respBody"))
              return@withContext
            }
            interactionId = createdId
            onEvent(GeminiStreamEvent.InteractionCreated(createdId, serviceTierHeader))
          }
          // Poll GET /v1beta/interactions/{id} until completed, failed, cancelled or incomplete
          pollInteractionUntilDone(
            client = client,
            apiKey = apiKey,
            interactionId = interactionId!!,
            defaultServiceTier = serviceTierHeader,
            onEvent = onEvent,
          )
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          if (call.isCanceled()) return@withContext
          onEvent(GeminiStreamEvent.Error(e.message ?: "Network error during background polling"))
        } finally {
          activeCall = null
        }
        return@withContext
      }

      // 2. Streaming SSE flow (POST /v1beta/interactions?alt=sse)
      // Retry POST with exponential backoff on 429, 500, 503 and timeouts ONLY before interaction.created arrives!
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
          buildRequestBodyJson(
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

        val call = client.newCall(request)
        activeCall = call

        try {
          call.execute().use { response ->
            val tierHdr = response.header("x-gemini-service-tier")
            if (!tierHdr.isNullOrBlank()) {
              serviceTierHeader = tierHdr
            }

            if (!response.isSuccessful) {
              val errorBody = response.body?.string().orEmpty()
              val code = response.code

              // If the server no longer has the earlier interaction (404 or 400 mentioning previous_interaction_id),
              // rebuild the whole conversation from saved raw steps and immediately retry POST!
              if (
                usePreviousInteractionId != null &&
                  (code == 404 ||
                    (code == 400 &&
                      errorBody.contains("previous_interaction", ignoreCase = true)))
              ) {
                usePreviousInteractionId = null
                return@use
              }

              // Retry on 429, 500, 503 before interaction.created
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

            // Stream SSE lines
            val reader =
              BufferedReader(
                InputStreamReader(response.body!!.byteStream(), Charsets.UTF_8)
              )
            processSseStream(
              reader = reader,
              serviceTierHeader = serviceTierHeader,
              onInteractionCreated = { id ->
                interactionId = id
                postSucceededOrCreated = true
                onEvent(GeminiStreamEvent.InteractionCreated(id, serviceTierHeader))
              },
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
                onEvent(GeminiStreamEvent.StatusUpdate(status))
              },
              onCompleted = { id, inTok, outTok, thTok, outputsArr ->
                if (!id.isNullOrBlank()) interactionId = id
                inputTokens = inTok
                outputTokens = outTok
                thoughtTokens = thTok
                if (rawSteps.length() == 0 && outputsArr != null && outputsArr.length() > 0) {
                  for (i in 0 until outputsArr.length()) {
                    rawSteps.put(outputsArr.getJSONObject(i))
                  }
                }
                streamCompleted = true
              },
              onStreamError = { errMsg ->
                onEvent(GeminiStreamEvent.Error(errMsg))
              },
            )
            postSucceededOrCreated = true
          }
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          if (call.isCanceled()) return@withContext
          if (interactionId == null) {
            // Failed BEFORE interaction.created arrived -> eligible for POST retry on timeout/IOException
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
            // Failed AFTER interaction.created arrived -> NEVER re-POST! Break out to reconnect/poll.
            postSucceededOrCreated = true
          }
        }
      }

      // 3. If interaction.created arrived, but stream dropped before completion:
      // - If lastEventId is present, reconnect with GET /v1beta/interactions/{id}?alt=sse&stream=true&last_event_id=<lastEventId>
      // - If lastEventId is null/blank (or reconnect fails), poll GET /v1beta/interactions/{id} until finished and replace partial reply with its outputs!
      if (!streamCompleted && interactionId != null) {
        val id = interactionId!!
        var reconnectAttempt = 0
        val maxReconnects = 3

        while (!streamCompleted && !lastEventId.isNullOrBlank() && reconnectAttempt < maxReconnects) {
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
                // Fall back to GET polling if stream reconnect returns non-200
                lastEventId = null
                return@use
              }
              val reader =
                BufferedReader(
                  InputStreamReader(resp.body!!.byteStream(), Charsets.UTF_8)
                )
              processSseStream(
                reader = reader,
                serviceTierHeader = serviceTierHeader,
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
                  onEvent(GeminiStreamEvent.StatusUpdate(status))
                },
                onCompleted = { _, inTok, outTok, thTok, _ ->
                  inputTokens = inTok
                  outputTokens = outTok
                  thoughtTokens = thTok
                  streamCompleted = true
                },
                onStreamError = { errMsg ->
                  onEvent(GeminiStreamEvent.Error(errMsg))
                },
              )
            }
          } catch (e: CancellationException) {
            throw e
          } catch (_: Exception) {
            if (call.isCanceled()) return@withContext
          }
        }

        // Fallback: If no event_id had arrived when the stream dropped (or reconnects didn't finish),
        // poll GET /v1beta/interactions/{id} until it finishes and replace the partial reply with its outputs.
        if (!streamCompleted) {
          pollInteractionUntilDone(
            client = client,
            apiKey = apiKey,
            interactionId = id,
            defaultServiceTier = serviceTierHeader,
            onEvent = onEvent,
          )
          activeCall = null
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
          )
        )
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
      call.execute().use { resp ->
        val tierHdr = resp.header("x-gemini-service-tier")
        if (!tierHdr.isNullOrBlank()) serviceTier = tierHdr
        val bodyStr = resp.body?.string().orEmpty()
        if (!resp.isSuccessful) {
          onEvent(GeminiStreamEvent.Error(extractApiErrorMessage(resp.code, bodyStr), resp.code))
          return
        }
        val json = JSONObject(bodyStr)
        val status = json.optString("status", "in_progress").lowercase()
        onEvent(GeminiStreamEvent.StatusUpdate(status))

        when (status) {
          "completed",
          "incomplete" -> {
            val outputs = json.optJSONArray("outputs") ?: JSONArray()
            val extracted = extractTextAndThoughtFromOutputs(outputs)
            onEvent(GeminiStreamEvent.ReplaceReplyText(extracted.first, extracted.second))
            val usage = json.optJSONObject("usage")
            val inTok = usage?.optInt("total_input_tokens", 0) ?: 0
            val outTok = usage?.optInt("total_output_tokens", 0) ?: 0
            val thTok = usage?.optInt("total_thought_tokens", 0) ?: 0
            onEvent(
              GeminiStreamEvent.Completed(
                interactionId = interactionId,
                serviceTier = serviceTier,
                inputTokens = inTok,
                outputTokens = outTok,
                thoughtTokens = thTok,
                rawSteps = outputs,
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
    serviceTierHeader: String,
    onInteractionCreated: suspend (String) -> Unit,
    onEventId: (String) -> Unit,
    onStepStart: (JSONObject) -> Unit,
    onTextDelta: suspend (String) -> Unit,
    onThoughtSummaryDelta: suspend (String) -> Unit,
    onThoughtSignature: (String) -> Unit,
    onStepStop: suspend (JSONObject) -> Unit,
    onStatusUpdate: suspend (String) -> Unit,
    onCompleted: suspend (String?, Int, Int, Int, JSONArray?) -> Unit,
    onStreamError: suspend (String) -> Unit,
  ) {
    var currentEventName: String? = null
    val dataBuffer = StringBuilder()

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

      // 1. Read event_id from each event's JSON
      val jsonEventId =
        json.optString("event_id").takeIf { it.isNotBlank() }
          ?: json.optJSONObject("event")?.optString("event_id")?.takeIf { it.isNotBlank() }
      if (!jsonEventId.isNullOrBlank()) {
        onEventId(jsonEventId)
      }

      val resolvedType =
        eventName?.takeIf { it.isNotBlank() && it != "message" }
          ?: json.optString("type").takeIf { it.isNotBlank() }
          ?: json.optString("event").takeIf { it.isNotBlank() }
          ?: ""

      when (resolvedType) {
        "interaction.created" -> {
          val id = extractInteractionId(json)
          if (!id.isNullOrBlank()) {
            onInteractionCreated(id)
          }
        }
        "step.start" -> {
          val step = json.optJSONObject("step") ?: JSONObject().apply { put("type", "model_output") }
          if (!step.has("content")) {
            step.put("content", JSONArray())
          }
          onStepStart(step)
        }
        "step.delta" -> {
          val delta = json.optJSONObject("delta") ?: json
          val deltaType =
            delta.optString("type").takeIf { it.isNotBlank() }
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
            "thought_summary" -> {
              val thoughtTxt =
                delta.optJSONObject("content")?.optString("text")?.takeIf { it.isNotEmpty() }
                  ?: delta.optString("text").orEmpty()
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
              // Check if thought_signature is attached alongside another field
              val sig =
                delta.optString("thought_signature").takeIf { it.isNotEmpty() }
                  ?: delta.optJSONObject("content")?.optString("thought_signature").orEmpty()
              if (sig.isNotEmpty()) {
                onThoughtSignature(sig)
              }
            }
          }
        }
        "step.stop" -> {
          val stopStep = json.optJSONObject("step") ?: json
          onStepStop(stopStep)
        }
        "interaction.status_update" -> {
          val status =
            json.optString("status").takeIf { it.isNotBlank() }
              ?: json.optJSONObject("interaction")?.optString("status").orEmpty()
          if (status.isNotBlank()) {
            onStatusUpdate(status)
          }
        }
        "interaction.completed" -> {
          val interactionObj = json.optJSONObject("interaction") ?: json
          val id = extractInteractionId(interactionObj) ?: extractInteractionId(json)
          val usage = interactionObj.optJSONObject("usage") ?: json.optJSONObject("usage")
          val inTok = usage?.optInt("total_input_tokens", 0) ?: 0
          val outTok = usage?.optInt("total_output_tokens", 0) ?: 0
          val thTok = usage?.optInt("total_thought_tokens", 0) ?: 0
          val outputs = interactionObj.optJSONArray("outputs") ?: json.optJSONArray("outputs")
          onCompleted(id, inTok, outTok, thTok, outputs)
        }
        "error" -> {
          val errObj = json.optJSONObject("error") ?: json
          val msg =
            errObj.optString("message").takeIf { it.isNotBlank() }
              ?: json.toString()
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

  private fun finalizeStep(currentStep: JSONObject?, stopStep: JSONObject): JSONObject {
    if (currentStep == null) return stopStep
    // Preserve any extra fields from stopStep (such as thought_signature or id)
    val keys = stopStep.keys()
    while (keys.hasNext()) {
      val k = keys.next()
      if (!currentStep.has(k)) {
        currentStep.put(k, stopStep.get(k))
      }
    }
    return currentStep
  }

  private fun extractInteractionId(json: JSONObject): String? {
    json.optJSONObject("interaction")?.optString("id")?.takeIf { it.isNotBlank() }?.let {
      return it
    }
    json.optString("interaction_id").takeIf { it.isNotBlank() }?.let { return it }
    json.optString("id").takeIf { it.isNotBlank() }?.let { return it }
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

  /**
   * Section 3: Model list:
   * GET https://generativelanguage.googleapis.com/v1beta/models?pageSize=1000
   * Keep models whose supportedGenerationMethods include generateContent,
   * and hide ids containing tts, image, banana, embedding, live, transcribe, lyria, omni or robotics.
   * Show displayName, id, inputTokenLimit and outputTokenLimit.
   */
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
