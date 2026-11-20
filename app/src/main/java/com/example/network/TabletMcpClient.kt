package com.example.network

import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class TabletToolDef(
  val name: String,
  val description: String,
  val inputSchemaJson: String,
) {
  fun toGeminiFunctionToolJson(): JSONObject {
    val schemaObj =
      try {
        JSONObject(inputSchemaJson)
      } catch (_: Exception) {
        JSONObject().apply { put("type", "object") }
      }
    return JSONObject().apply {
      put("type", "function")
      put("name", name)
      put("description", description)
      put("parameters", schemaObj)
    }
  }

  fun allowedArgumentKeys(): Set<String> {
    return try {
      val schemaObj = JSONObject(inputSchemaJson)
      val props = schemaObj.optJSONObject("properties") ?: return emptySet()
      val keys = mutableSetOf<String>()
      val iter = props.keys()
      while (iter.hasNext()) {
        keys.add(iter.next())
      }
      keys
    } catch (_: Exception) {
      emptySet()
    }
  }
}

sealed class TabletCallOutcome<out T> {
  data class Success<T>(
    val value: T,
    val latencyMs: Long,
    val ticket: String? = null,
    val ok: Boolean = true,
    val status: String = "ok",
    val errorCode: String? = null,
  ) : TabletCallOutcome<T>()

  data class Failure(
    val message: String,
    val errorCode: String,
    val latencyMs: Long,
    val ticket: String? = null,
  ) : TabletCallOutcome<Nothing>()
}

sealed class TabletLoginResult {
  data class Success(
    val appToken: String,
    val expiresInSeconds: Long? = null,
  ) : TabletLoginResult()

  data class Failure(
    val errorMessage: String,
    val isTimeout: Boolean = false,
    val httpStatusCode: Int? = null,
  ) : TabletLoginResult()
}

/**
 * Standalone JSON-RPC 2.0 over HTTP client for Jordan's tablet tool server.
 * Kept strictly outside PluginRegistry.
 *
 * Ground rules:
 * - Connect timeout 10 s, read timeout 30 s.
 * - Retries once on network error or timeout.
 * - Never logs the app token, request text, or bot reply.
 */
class TabletMcpClient(
  baseClient: OkHttpClient = OkHttpClient(),
) {
  companion object {
    const val DEFAULT_SERVER_URL = "https://mcp.rivia.observer/app/mcp"
    private val JSON_MEDIA_TYPE = "application/json".toMediaType()

    fun normalizeServerUrl(raw: String): String {
      return raw.replace("\r", "").replace("\n", "").trim()
    }

    fun normalizeAppToken(raw: String): String {
      return raw.replace("\r", "").replace("\n", "").trim()
    }

    fun validateServerUrl(raw: String): Result<String> {
      val clean = normalizeServerUrl(raw)
      if (!clean.startsWith("https://") || !clean.endsWith("/app/mcp") || clean.length <= "https:///app/mcp".length) {
        return Result.failure(
          IllegalArgumentException("Server URL must start with https:// and end in /app/mcp")
        )
      }
      return Result.success(clean)
    }

    fun isValidServerUrl(raw: String): Boolean = validateServerUrl(raw).isSuccess

    fun buildLoginUrl(rawServerUrl: String): Result<String> {
      return validateServerUrl(rawServerUrl).map { cleanMcpUrl ->
        cleanMcpUrl.removeSuffix("/app/mcp") + "/app/login"
      }
    }
  }

  private val rpcIdCounter = AtomicInteger(1)
  private val schemaAllowedKeysByTool = java.util.concurrent.ConcurrentHashMap<String, Set<String>>()

  fun filterArgumentsForTool(toolName: String, rawArguments: JSONObject): JSONObject {
    val allowedKeys: Set<String> =
      when (toolName) {
        "get_tablet_battery" -> emptySet()
        else ->
          schemaAllowedKeysByTool[toolName]
            ?: run {
              val keys = mutableSetOf<String>()
              val iter = rawArguments.keys()
              while (iter.hasNext()) keys.add(iter.next())
              keys
            }
      }
    val filtered = JSONObject()
    for (key in allowedKeys) {
      if (rawArguments.has(key) && !rawArguments.isNull(key)) {
        filtered.put(key, rawArguments.get(key))
      }
    }
    return filtered
  }

  private val httpClient: OkHttpClient =
    baseClient
      .newBuilder()
      .connectTimeout(10, TimeUnit.SECONDS)
      .readTimeout(30, TimeUnit.SECONDS)
      .build()

  /**
   * Exchanges a Google server_auth_code with the tablet's /app/login endpoint for a Rivia app_token.
   * Reads the JSON body on every HTTP status code (including 400, 403, 429, 500) and never logs
   * or exposes the server auth code or returned app token.
   */
  suspend fun loginWithGoogleAuthCode(
    serverUrl: String,
    serverAuthCode: String,
  ): TabletLoginResult =
    withContext(Dispatchers.IO) {
      val loginUrl =
        buildLoginUrl(serverUrl).getOrElse { err ->
          return@withContext TabletLoginResult.Failure(
            errorMessage = err.message ?: "Server URL must start with https:// and end in /app/mcp"
          )
        }
      val cleanCode = serverAuthCode.trim()
      if (cleanCode.isBlank()) {
        return@withContext TabletLoginResult.Failure(errorMessage = "Missing server auth code.")
      }

      val payload = JSONObject().apply { put("server_auth_code", cleanCode) }
      val reqBody = payload.toString().toRequestBody(JSON_MEDIA_TYPE)
      val req =
        Request.Builder()
          .url(loginUrl)
          .post(reqBody)
          .header("Content-Type", "application/json")
          .build()

      try {
        httpClient.newCall(req).execute().use { resp ->
          val statusCode = resp.code
          val rawBody = resp.body?.string().orEmpty()
          val parsed =
            try {
              if (rawBody.trim().startsWith("{")) JSONObject(rawBody.trim()) else null
            } catch (_: Exception) {
              null
            }

          if (parsed != null) {
            val ok = parsed.optBoolean("ok", false)
            if (ok) {
              val appToken = normalizeAppToken(parsed.optString("app_token", ""))
              if (appToken.isNotBlank()) {
                val expiresIn = if (parsed.has("expires_in")) parsed.optLong("expires_in") else null
                return@withContext TabletLoginResult.Success(
                  appToken = appToken,
                  expiresInSeconds = expiresIn,
                )
              }
            }
            val rawErr = parsed.opt("error")
            val errStr =
              when (rawErr) {
                is String -> rawErr.trim()
                is JSONObject ->
                  rawErr.optString("message", "").trim()
                    .ifBlank { rawErr.optString("msg", "").trim() }
                    .ifBlank { rawErr.optString("code", "").trim() }
                null,
                JSONObject.NULL -> ""
                else -> rawErr.toString().trim()
              }
            if (statusCode == 429 || errStr.equals("rate_limited", ignoreCase = true)) {
              return@withContext TabletLoginResult.Failure(
                errorMessage = "Too many tries, wait a minute and try again",
                httpStatusCode = statusCode,
              )
            }
            if (errStr.isNotBlank()) {
              val sanitized =
                if (cleanCode.length >= 4) errStr.replace(cleanCode, "[REDACTED]") else errStr
              return@withContext TabletLoginResult.Failure(
                errorMessage = sanitized,
                httpStatusCode = statusCode,
              )
            }
          }

          if (statusCode == 429) {
            return@withContext TabletLoginResult.Failure(
              errorMessage = "Too many tries, wait a minute and try again",
              httpStatusCode = statusCode,
            )
          }

          return@withContext TabletLoginResult.Failure(
            errorMessage = "HTTP $statusCode",
            httpStatusCode = statusCode,
          )
        }
      } catch (_: SocketTimeoutException) {
        return@withContext TabletLoginResult.Failure(
          errorMessage = "Sign-in may not have finished. Tap Sign in with Google again.",
          isTimeout = true,
        )
      } catch (_: InterruptedIOException) {
        return@withContext TabletLoginResult.Failure(
          errorMessage = "Sign-in may not have finished. Tap Sign in with Google again.",
          isTimeout = true,
        )
      } catch (e: Throwable) {
        val msg = e.message?.takeIf { it.isNotBlank() } ?: "Could not reach the tablet server."
        val sanitized = if (cleanCode.length >= 4) msg.replace(cleanCode, "[REDACTED]") else msg
        return@withContext TabletLoginResult.Failure(errorMessage = sanitized)
      }
    }

  suspend fun listTools(
    serverUrl: String,
    appToken: String,
  ): TabletCallOutcome<List<TabletToolDef>> =
    withContext(Dispatchers.IO) {
      val startMs = System.currentTimeMillis()
      val cleanUrl =
        validateServerUrl(serverUrl).getOrElse { err ->
          return@withContext TabletCallOutcome.Failure(
            message = err.message ?: "Invalid tablet Server URL.",
            errorCode = "invalid_url",
            latencyMs = System.currentTimeMillis() - startMs,
          )
        }
      val cleanToken = normalizeAppToken(appToken)
      if (cleanToken.isBlank()) {
        return@withContext TabletCallOutcome.Failure(
          message = "Tablet app token is not set.",
          errorCode = "missing_token",
          latencyMs = System.currentTimeMillis() - startMs,
        )
      }

      val payload =
        JSONObject().apply {
          put("jsonrpc", "2.0")
          put("id", rpcIdCounter.getAndIncrement())
          put("method", "tools/list")
          put("params", JSONObject())
        }

      when (val rawResp = postJsonRpcWithRetry(cleanUrl, cleanToken, payload, startMs)) {
        is RawRpcResult.HttpOrNetworkFailure ->
          TabletCallOutcome.Failure(
            message = rawResp.message,
            errorCode = rawResp.errorCode,
            latencyMs = System.currentTimeMillis() - startMs,
          )
        is RawRpcResult.RpcJson -> {
          val latency = System.currentTimeMillis() - startMs
          val json = rawResp.json
          val errObj = json.optJSONObject("error")
          if (errObj != null) {
            val code = errObj.opt("code")?.toString()?.ifBlank { "rpc_error" } ?: "rpc_error"
            val msg = errObj.optString("message", "JSON-RPC error from tablet")
            return@withContext TabletCallOutcome.Failure(
              message = msg,
              errorCode = code,
              latencyMs = latency,
            )
          }
          val resultObj =
            json.optJSONObject("result")
              ?: return@withContext TabletCallOutcome.Failure(
                message = "Missing result in tools/list response.",
                errorCode = "invalid_response",
                latencyMs = latency,
              )
          if (resultObj.optBoolean("isError", false)) {
            return@withContext TabletCallOutcome.Failure(
              message = "Tablet returned isError: true for tools/list.",
              errorCode = "is_error",
              latencyMs = latency,
            )
          }
          val toolsArr = resultObj.optJSONArray("tools") ?: JSONArray()
          val list = mutableListOf<TabletToolDef>()
          for (i in 0 until toolsArr.length()) {
            val tObj = toolsArr.optJSONObject(i) ?: continue
            val name = tObj.optString("name", "").trim()
            if (name.isBlank()) continue
            val desc = tObj.optString("description", "")
            val schema =
              tObj.optJSONObject("inputSchema")?.toString()
                ?: """{"type":"object","properties":{}}"""
            val def =
              TabletToolDef(
                name = name,
                description = desc,
                inputSchemaJson = schema,
              )
            schemaAllowedKeysByTool[name] = def.allowedArgumentKeys()
            list.add(def)
          }
          TabletCallOutcome.Success(
            value = list,
            latencyMs = latency,
            ok = true,
            status = "ok",
          )
        }
      }
    }

  suspend fun callTool(
    serverUrl: String,
    appToken: String,
    toolName: String,
    arguments: JSONObject,
  ): TabletCallOutcome<JSONObject> =
    withContext(Dispatchers.IO) {
      val startMs = System.currentTimeMillis()
      val cleanUrl =
        validateServerUrl(serverUrl).getOrElse { err ->
          return@withContext TabletCallOutcome.Failure(
            message = err.message ?: "Invalid tablet Server URL.",
            errorCode = "invalid_url",
            latencyMs = System.currentTimeMillis() - startMs,
          )
        }
      val cleanToken = normalizeAppToken(appToken)
      if (cleanToken.isBlank()) {
        return@withContext TabletCallOutcome.Failure(
          message = "Tablet app token is not set.",
          errorCode = "missing_token",
          latencyMs = System.currentTimeMillis() - startMs,
        )
      }

      val sanitizedArguments = filterArgumentsForTool(toolName, arguments)

      val payload =
        JSONObject().apply {
          put("jsonrpc", "2.0")
          put("id", rpcIdCounter.getAndIncrement())
          put("method", "tools/call")
          put(
            "params",
            JSONObject().apply {
              put("name", toolName)
              put("arguments", sanitizedArguments)
            },
          )
        }

      val fallbackTicket = sanitizedArguments.optString("ticket", "").takeIf { it.isNotBlank() }

      when (val rawResp = postJsonRpcWithRetry(cleanUrl, cleanToken, payload, startMs)) {
        is RawRpcResult.HttpOrNetworkFailure ->
          TabletCallOutcome.Failure(
            message = rawResp.message,
            errorCode = rawResp.errorCode,
            latencyMs = System.currentTimeMillis() - startMs,
            ticket = fallbackTicket,
          )
        is RawRpcResult.RpcJson -> {
          val latency = System.currentTimeMillis() - startMs
          val json = rawResp.json
          val rpcErr = json.optJSONObject("error")
          if (rpcErr != null) {
            val code = rpcErr.opt("code")?.toString()?.ifBlank { "rpc_error" } ?: "rpc_error"
            val msg = rpcErr.optString("message", "JSON-RPC error from tablet")
            return@withContext TabletCallOutcome.Failure(
              message = msg,
              errorCode = code,
              latencyMs = latency,
              ticket = fallbackTicket,
            )
          }

          val resultObj =
            json.optJSONObject("result")
              ?: return@withContext TabletCallOutcome.Failure(
                message = "Missing result in tools/call response.",
                errorCode = "invalid_response",
                latencyMs = latency,
                ticket = fallbackTicket,
              )

          val structured = extractStructuredContent(resultObj)
          val isErrorFlag = resultObj.optBoolean("isError", false)

          if (structured == null) {
            return@withContext TabletCallOutcome.Failure(
              message =
                if (isErrorFlag) extractErrorTextFromResult(resultObj)
                else "Missing structuredContent in tools/call response.",
              errorCode = if (isErrorFlag) "tool_error" else "invalid_response",
              latencyMs = latency,
              ticket = fallbackTicket,
            )
          }

          val ticket =
            structured.optString("ticket", "").takeIf { it.isNotBlank() } ?: fallbackTicket
          val okField =
            if (structured.has("ok")) structured.optBoolean("ok", !isErrorFlag)
            else !isErrorFlag
          val errObj = structured.optJSONObject("error")
          val errCode =
            errObj?.optString("code", "")?.takeIf { it.isNotBlank() }
              ?: if (!okField || isErrorFlag) "tool_error" else null
          val statusStr =
            structured.optString("status", "").ifBlank {
              if (okField && !isErrorFlag) "ok" else "failed"
            }

          if (isErrorFlag && !structured.has("ok")) {
            return@withContext TabletCallOutcome.Failure(
              message = errObj?.optString("msg", "Tool call failed") ?: "Tool call failed",
              errorCode = errCode ?: "tool_error",
              latencyMs = latency,
              ticket = ticket,
            )
          }

          TabletCallOutcome.Success(
            value = structured,
            latencyMs = latency,
            ticket = ticket,
            ok = okField && !isErrorFlag,
            status = statusStr,
            errorCode = errCode,
          )
        }
      }
    }

  private fun extractStructuredContent(resultObj: JSONObject): JSONObject? {
    resultObj.optJSONObject("structuredContent")?.let { return it }
    val contentArr = resultObj.optJSONArray("content") ?: return null
    for (i in 0 until contentArr.length()) {
      val item = contentArr.optJSONObject(i) ?: continue
      val text = item.optString("text", "").trim()
      if (text.startsWith("{")) {
        try {
          return JSONObject(text)
        } catch (_: Exception) {}
      }
    }
    return null
  }

  private fun extractErrorTextFromResult(resultObj: JSONObject): String {
    val contentArr = resultObj.optJSONArray("content")
    if (contentArr != null) {
      for (i in 0 until contentArr.length()) {
        val item = contentArr.optJSONObject(i) ?: continue
        val text = item.optString("text", "").trim()
        if (text.isNotBlank()) return text.take(200)
      }
    }
    return "Tablet tool returned isError: true"
  }

  private sealed class RawRpcResult {
    data class RpcJson(val json: JSONObject) : RawRpcResult()

    data class HttpOrNetworkFailure(val message: String, val errorCode: String) : RawRpcResult()
  }

  private fun postJsonRpcWithRetry(
    url: String,
    token: String,
    payload: JSONObject,
    startMs: Long,
  ): RawRpcResult {
    var lastIOException: Throwable? = null
    for (attempt in 0..1) {
      try {
        val reqBody = payload.toString().toRequestBody(JSON_MEDIA_TYPE)
        val req =
          Request.Builder()
            .url(url)
            .post(reqBody)
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .header("Accept", "application/json, text/event-stream")
            .build()

        httpClient.newCall(req).execute().use { resp ->
          val code = resp.code
          val rawBody = resp.body?.string().orEmpty()
          if (code == 401) {
            return RawRpcResult.HttpOrNetworkFailure(
              message = "The tablet refused the app token.",
              errorCode = "http_401",
            )
          }
          if (code == 403) {
            return RawRpcResult.HttpOrNetworkFailure(
              message = "This token isn't for the app door.",
              errorCode = "http_403",
            )
          }
          if (code != 200) {
            val safeSnippet =
              if (token.isNotBlank() && token.length >= 4) {
                rawBody.replace(token, "[REDACTED]")
              } else {
                rawBody
              }.take(200)
            return RawRpcResult.HttpOrNetworkFailure(
              message = "HTTP $code: $safeSnippet",
              errorCode = "http_$code",
            )
          }

          val parsed = parseJsonOrSseBody(rawBody)
          if (parsed == null) {
            return RawRpcResult.HttpOrNetworkFailure(
              message = "Invalid JSON response from tablet.",
              errorCode = "invalid_json",
            )
          }
          return RawRpcResult.RpcJson(parsed)
        }
      } catch (e: IOException) {
        lastIOException = e
        // Retry once on network error or timeout (attempt 0 -> attempt 1)
      } catch (e: Throwable) {
        val safeMsg =
          (e.message ?: "Tablet request failed").let {
            if (token.isNotBlank() && token.length >= 4) it.replace(token, "[REDACTED]") else it
          }
        return RawRpcResult.HttpOrNetworkFailure(
          message = safeMsg,
          errorCode = "tablet_unreachable",
        )
      }
    }
    val errMsg =
      (lastIOException?.message ?: "Could not reach the tablet server.").let {
        if (token.isNotBlank() && token.length >= 4) it.replace(token, "[REDACTED]") else it
      }
    return RawRpcResult.HttpOrNetworkFailure(
      message = errMsg,
      errorCode = "tablet_unreachable",
    )
  }

  private fun parseJsonOrSseBody(rawBody: String): JSONObject? {
    val trimmed = rawBody.trim()
    if (trimmed.startsWith("{")) {
      return try {
        JSONObject(trimmed)
      } catch (_: Exception) {
        null
      }
    }
    var lastJson: JSONObject? = null
    for (line in trimmed.lines()) {
      val cleanLine = line.trim()
      if (cleanLine.startsWith("data:")) {
        val dataPart = cleanLine.removePrefix("data:").trim()
        if (dataPart.startsWith("{")) {
          try {
            lastJson = JSONObject(dataPart)
          } catch (_: Exception) {}
        }
      }
    }
    return lastJson
  }
}
