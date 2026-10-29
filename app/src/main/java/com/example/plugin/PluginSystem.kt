package com.example.plugin

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.json.JSONArray
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
   * Returns an array of function declarations (JSON objects) to offer the Gemini model.
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
 * Section 7: PluginRegistry that adds enabled plug-ins' tools to each request
 * and runs their function calls.
 */
object PluginRegistry {
  private val plugins = mutableMapOf<String, RiviaPlugin>()
  private val _enabledPluginIds = MutableStateFlow<Set<String>>(emptySet())
  val enabledPluginIds: StateFlow<Set<String>> = _enabledPluginIds.asStateFlow()

  fun registerPlugin(plugin: RiviaPlugin) {
    plugins[plugin.id] = plugin
  }

  fun unregisterPlugin(pluginId: String) {
    plugins.remove(pluginId)
    _enabledPluginIds.value = _enabledPluginIds.value - pluginId
  }

  fun setPluginEnabled(pluginId: String, enabled: Boolean) {
    _enabledPluginIds.value =
      if (enabled) _enabledPluginIds.value + pluginId else _enabledPluginIds.value - pluginId
  }

  fun getAllPlugins(): List<RiviaPlugin> = plugins.values.toList()

  fun getEnabledFunctionDeclarations(): JSONArray {
    val arr = JSONArray()
    val enabled = _enabledPluginIds.value
    for (id in enabled) {
      val plugin = plugins[id] ?: continue
      for (decl in plugin.getFunctionDeclarations()) {
        arr.put(decl)
      }
    }
    return arr
  }

  suspend fun dispatchToolCall(functionName: String, arguments: JSONObject): JSONObject {
    val enabled = _enabledPluginIds.value
    for (id in enabled) {
      val plugin = plugins[id] ?: continue
      val matches =
        plugin.getFunctionDeclarations().any { it.optString("name") == functionName }
      if (matches) {
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
