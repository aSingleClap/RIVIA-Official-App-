package com.example.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

const val PROMPT_ASSEMBLY_VERSION = 1

@Serializable
data class PersonaLayerConfig(
  val layer: String = "persona",
  val version: Int = 0,
  val locked: Boolean = false,
  val updated: String = "",
  val note: String? = null,
  val text: String,
)

@Serializable
data class StyleLayerConfig(
  val layer: String = "style",
  val version: Int = 1,
  val locked: Boolean = false,
  val updated: String = "",
  val note: String? = null,
  val text: String,
  val voice: String,
)

@Serializable
data class EngineProfileConfig(
  val model: String,
  val thinking_level: String,
)

@Serializable
data class EngineSafetyItem(
  val type: String,
  val threshold: String,
  val method: String = "severity",
)

@Serializable
data class EngineLayerConfig(
  val layer: String = "engine",
  val version: Int = 1,
  val locked: Boolean = false,
  val updated: String = "",
  val note: String? = null,
  val default_profile: String,
  val profiles: Map<String, EngineProfileConfig>,
  val thought_summaries: String = "auto",
  val max_output_tokens: Int? = null,
  val service_tier: String = "standard",
  val store: Boolean = true,
  val safety: JsonElement = JsonPrimitive("model_default"),
)

@Serializable
data class HardRuleConfig(
  val id: String,
  val name: String,
  val active: Boolean,
  val status: String = "",
  val priority: Int,
  val tunable: Boolean = false,
  val setting: String? = null,
  val default: Int? = null,
  val ceiling: Int? = null,
  val enforced_by: String,
  val channels: List<String> = emptyList(),
  @SerialName("when") val whenCondition: String = "",
  val effect: String = "",
  val prompt: String? = null,
  val test: String? = null,
  val note: String? = null,
)

@Serializable
data class HardRulesLayerConfig(
  val version: Int = 2,
  val locked: Boolean = false,
  val updated: String = "",
  val note: String? = null,
  val rules: List<HardRuleConfig>,
)

@Serializable
data class TestDefConfig(
  val id: String,
  val name: String,
  val kind: String,
  val doc: String? = null,
  val required_profiles: List<String>,
  val pass: String? = null,
  val checks: List<String> = emptyList(),
)

@Serializable
data class TestsLayerConfig(
  val version: Int = 1,
  val updated: String = "",
  val note: String? = null,
  val tests: List<TestDefConfig>,
)

enum class LayerStatus(val label: String) {
  DORMANT("Dormant"),
  LOCKED("Locked"),
  TESTED("Tested"),
  DRAFT("Draft"),
}

enum class ProfileTestState(val label: String) {
  PASSED("passed"),
  NOT_RUN("not run"),
  FAILED("failed"),
}

data class LoadedLayersBundle(
  val persona: PersonaLayerConfig,
  val style: StyleLayerConfig,
  val engine: EngineLayerConfig,
  val hardRules: HardRulesLayerConfig,
  val tests: TestsLayerConfig,
  val bundledPersona: PersonaLayerConfig,
  val bundledStyle: StyleLayerConfig,
  val bundledEngine: EngineLayerConfig,
  val bundledHardRules: HardRulesLayerConfig,
  val bundledRawJson: Map<String, String>,
  val effectiveRawJson: Map<String, String>,
  val draftKeys: Set<String>,
)

data class LayerCardUiMeta(
  val layerKey: String,
  val fileName: String,
  val version: Int,
  val updated: String,
  val status: LayerStatus,
  val sourceSubtitle: String, // "from build" or "edited on phone"
  val hasDraft: Boolean,
  val bundledLocked: Boolean,
  val lockPendingSha256: String? = null,
  val lockMismatchWarning: String? = null,
  val exportedLockJson: String? = null,
)
