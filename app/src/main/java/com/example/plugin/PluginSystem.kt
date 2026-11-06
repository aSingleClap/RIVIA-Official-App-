package com.example.plugin

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.json.JSONObject

data class PluginEvent(
  val pluginId: String,
  val eventType: String,
  val payload: JSONObject,
  val timestamp: Long = System.currentTimeMillis(),
)

/**
 * Section 7: Plugin interface:
 * - id
 * - name
 * - the tools it offers the model (function declarations)
 * - how it runs a tool call
 * - the events it can send in
 */
interface RiviaPlugin {
  val id: String
  val name: String
  val description: String

  /**
   * Returns a list of function declaration objects to offer the Gemini model.
   * Fix #13: Each function goes in tools as its own
   * {"type":"function","name":...,"description":...,"parameters":{...}} object.
   */
  fun getFunctionDeclarations(): List<JSONObject>

  /**
   * Runs a tool call invoked by the model and returns the JSON result payload.
   */
  suspend fun executeToolCall(functionName: String, arguments: JSONObject): JSONObject

  /**
   * Stream of external events this plug-in can send into Rivia.
   */
  fun incomingEvents(): Flow<PluginEvent> = emptyFlow()
}

/**
 * Section 7 & Fix #13: PluginRegistry that adds enabled plug-ins' tools directly to each request
 * as {"type":"function","name":...,"description":...,"parameters":{...}} object (no wrapper)
 * and runs their function calls.
 *
 * Pass 1 H2 Guard: Refuses to register any tool with kind in ['send', 'delete', 'contact']
 * while H2 is inactive or has no H2 handler in the app, and exposes the refusals in Settings > Plug-ins.
 */
object PluginRegistry {
  private val WRITE_TOOL_KINDS = setOf("send", "delete", "contact")

  private val plugins = mutableMapOf<String, RiviaPlugin>()
  private val _enabledPluginIds = MutableStateFlow<Set<String>>(emptySet())
  val enabledPluginIds: StateFlow<Set<String>> = _enabledPluginIds.asStateFlow()

  @Volatile private var isH2ActiveWithHandler: Boolean = false

  private val _refusedWriteTools = MutableStateFlow<List<String>>(emptyList())
  val refusedWriteTools: StateFlow<List<String>> = _refusedWriteTools.asStateFlow()

  fun updateH2State(activeWithCodeHandler: Boolean) {
    isH2ActiveWithHandler = activeWithCodeHandler
    recomputeRefusals()
  }

  /**
   * Registers a plug-in and checks its tools against the H2 write-tool guard.
   * Returns false if any write tool was refused while H2 is inactive/unhandled.
   */
  fun registerPlugin(plugin: RiviaPlugin): Boolean {
    plugins[plugin.id] = plugin
    val hadRefusal = recomputeRefusalsForPlugin(plugin)
    recomputeRefusals()
    return !hadRefusal
  }

  fun unregisterPlugin(pluginId: String) {
    plugins.remove(pluginId)
    _enabledPluginIds.value = _enabledPluginIds.value - pluginId
    recomputeRefusals()
  }

  fun setPluginEnabled(pluginId: String, enabled: Boolean) {
    _enabledPluginIds.value =
      if (enabled) _enabledPluginIds.value + pluginId else _enabledPluginIds.value - pluginId
    recomputeRefusals()
  }

  fun getAllPlugins(): List<RiviaPlugin> = plugins.values.toList()

  private fun isWriteToolBlocked(decl: JSONObject): Boolean {
    val kind = decl.optString("kind", "").trim().lowercase()
    return kind in WRITE_TOOL_KINDS && !isH2ActiveWithHandler
  }

  private fun recomputeRefusalsForPlugin(plugin: RiviaPlugin): Boolean {
    var blocked = false
    for (decl in plugin.getFunctionDeclarations()) {
      if (isWriteToolBlocked(decl)) {
        blocked = true
      }
    }
    return blocked
  }

  private fun recomputeRefusals() {
    val messages = mutableListOf<String>()
    for (plugin in plugins.values) {
      for (decl in plugin.getFunctionDeclarations()) {
        if (isWriteToolBlocked(decl)) {
          val toolName = decl.optString("name", "unnamed")
          val kind = decl.optString("kind", "").trim().lowercase()
          messages.add(
            "Refused write tool \"$toolName\" (kind=\"$kind\") from ${plugin.name}: H2 (Writes need a Watch tap) is inactive."
          )
        }
      }
    }
    _refusedWriteTools.value = messages
  }

  /**
   * Fix #13 & Pass 1 H2 Guard: Returns each enabled plug-in function as its own
   * {"type":"function","name":...,"description":...,"parameters":{...}} object,
   * excluding any write tool refused by H2.
   */
  fun getEnabledFunctionToolObjects(): List<JSONObject> {
    val list = mutableListOf<JSONObject>()
    val enabled = _enabledPluginIds.value
    for (id in enabled) {
      val plugin = plugins[id] ?: continue
      for (decl in plugin.getFunctionDeclarations()) {
        if (isWriteToolBlocked(decl)) continue
        val toolObj =
          JSONObject().apply {
            put("type", "function")
            put("name", decl.optString("name"))
            if (decl.has("description")) {
              put("description", decl.optString("description"))
            }
            if (decl.has("parameters")) {
              put("parameters", decl.get("parameters"))
            }
          }
        list.add(toolObj)
      }
    }
    return list
  }

  suspend fun dispatchToolCall(functionName: String, arguments: JSONObject): JSONObject {
    val enabled = _enabledPluginIds.value
    for (id in enabled) {
      val plugin = plugins[id] ?: continue
      val matchingDecl =
        plugin.getFunctionDeclarations().firstOrNull { it.optString("name") == functionName }
      if (matchingDecl != null) {
        if (isWriteToolBlocked(matchingDecl)) {
          return JSONObject().apply {
            put(
              "error",
              "Refused write tool '$functionName': H2 (Writes need a Watch tap) is inactive.",
            )
          }
        }
        return plugin.executeToolCall(functionName, arguments)
      }
    }
    return JSONObject().apply {
      put("error", "No enabled plug-in registered for function '$functionName'")
    }
  }
}

enum class RiviaChannel {
  TYPED_CHAT,
  LIVE_VOICE,
  EXTERNAL_BRIDGE,
}

/**
 * Section 7: One internal entry point for asking Rivia (text, channel, request ID)
 * and one for speaking a line, so a future broadcast-intent bridge for Tasker can call them.
 */
interface RiviaInternalBridge {
  suspend fun askRivia(
    text: String,
    channel: RiviaChannel = RiviaChannel.EXTERNAL_BRIDGE,
    requestId: String? = null,
  ): String

  suspend fun speakLine(text: String)
}

object RiviaBridgeHolder {
  @Volatile var bridge: RiviaInternalBridge? = null
}
