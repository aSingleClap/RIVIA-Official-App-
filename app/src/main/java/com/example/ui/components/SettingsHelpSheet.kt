package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ChivoMonoFontFamily
import com.example.ui.theme.RiviaBackground
import com.example.ui.theme.RiviaBorder
import com.example.ui.theme.RiviaIconDefault
import com.example.ui.theme.RiviaMutedText
import com.example.ui.theme.RiviaSurface1
import com.example.ui.theme.RiviaSurface2
import com.example.ui.theme.RiviaText
import com.example.ui.theme.RiviaTextPrimary
import com.example.ui.theme.RiviaTextSecondary
import com.example.ui.theme.RiviaTextTertiary

data class SettingHelpTopic(
  val id: String,
  val title: String,
  val summary: String,
  val whenToUse: String,
  val examples: List<String>,
)

object RiviaSettingsGuide {
  val runSettingsTopics: List<SettingHelpTopic> =
    listOf(
      SettingHelpTopic(
        id = "mode_presets",
        title = "Mode Presets (Fast, Balanced, Deep)",
        summary =
          "Presets let you switch both the Gemini model and its reasoning effort in a single tap. You can also customize what model and thinking level each preset uses.",
        whenToUse =
          "Use Fast for quick everyday questions, Balanced for general coding and writing, and Deep when tackling hard math, architecture, or multi-step analysis.",
        examples =
          listOf(
            "Fast → gemini-3.5-flash-lite with 'minimal' thinking for instant replies",
            "Balanced → gemini-3.8-flash with 'medium' thinking for daily tasks",
            "Deep → gemini-4-argon with 'high' thinking for complex problem solving",
          ),
      ),
      SettingHelpTopic(
        id = "models",
        title = "Typed Chat & Live Mode Models",
        summary =
          "Selects which Gemini model powers your typed conversations and which model powers hands-free Live Voice mode.",
        whenToUse =
          "Pick a fast Flash model for Live voice so spoken replies start with minimal delay, and a larger or reasoning-heavy model for typed chat when depth matters.",
        examples =
          listOf(
            "Typed Chat: gemini-3.8-flash or gemini-4-argon",
            "Live Mode: gemini-3.8-flash for low-latency spoken back-and-forth",
          ),
      ),
      SettingHelpTopic(
        id = "thinking",
        title = "Thinking Level & Thought Summaries",
        summary =
          "Controls how much internal reasoning Rivia performs before writing her final answer, and whether a collapsible 'Thinking' summary is shown above the reply.",
        whenToUse =
          "Lower levels ('minimal' or 'low') respond faster and use fewer tokens. Higher levels ('medium' or 'high') plan out edge cases before answering.",
        examples =
          listOf(
            "minimal / low: 'Translate this sentence' or 'Summarize this email'",
            "high: 'Find the concurrency bug in this Kotlin coroutine flow and prove why it deadlocks'",
            "Thought summaries = auto: Shows a collapsible 'Thinking' card above Rivia's answer",
          ),
      ),
      SettingHelpTopic(
        id = "system_instructions",
        title = "System Instructions & Channel Styles",
        summary =
          "System instructions define Rivia's persona, rules, and background knowledge. Typed and Live styles are appended automatically depending on how you talk to her.",
        whenToUse =
          "Put core persona or project rules in System Instructions, formatting preferences in Typed Style, and spoken brevity rules in Live Style.",
        examples =
          listOf(
            "System instruction: 'You are Rivia, a direct and pragmatic Android architecture partner.'",
            "Typed style: 'Use concise Markdown tables and Kotlin code blocks.'",
            "Live style: 'Keep spoken answers under 3 sentences and avoid reading code aloud.'",
          ),
      ),
      SettingHelpTopic(
        id = "tools",
        title = "Capabilities & Built-in Tools",
        summary =
          "Gives Rivia access to live web search, image search, Python code execution, web page reading, Google Maps places, or your own uploaded file stores.",
        whenToUse =
          "Enable the tools that match your workflow. With Tool Choice set to 'auto', Rivia only invokes a tool when your prompt needs it.",
        examples =
          listOf(
            "Google Search: 'What were the major announcements at Android Dev Summit this week?'",
            "URL Context: 'Read https://developer.android.com/... and list the breaking changes'",
            "Code Execution: 'Simulate 10,000 Monte Carlo runs for this portfolio and give the median'",
            "Google Maps: 'Find quiet coffee shops open after 8 PM near 47.6062, -122.3321'",
            "File Search: Searches your specified Gemini vector store IDs for private docs",
          ),
      ),
      SettingHelpTopic(
        id = "output_sampling",
        title = "Output Length, Seed & Sampling",
        summary =
          "Controls response length (Max Output Tokens), stop words, reproducibility (Seed), and randomness (Temperature, Top-P, Top-K on models prior to Gemini 3.6).",
        whenToUse =
          "Leave blank for model defaults, or set a lower Temperature for deterministic answers and a fixed Seed when comparing prompts.",
        examples =
          listOf(
            "Max output tokens = 4096: Caps total reply + reasoning tokens",
            "Temperature = 0.2: Focused, factual coding or data extraction",
            "Temperature = 1.1: Creative brainstorming and story writing",
            "Seed = 42: Produces repeatable outputs for the same prompt and settings",
          ),
      ),
      SettingHelpTopic(
        id = "structured_output",
        title = "Structured Output (JSON Schema)",
        summary =
          "Forces Rivia's reply to strictly follow a JSON Schema so the output can be parsed reliably by scripts or other apps.",
        whenToUse =
          "Turn this on when you want a guaranteed JSON object instead of free-form conversational Markdown.",
        examples =
          listOf(
            "{\"type\":\"object\",\"properties\":{\"summary\":{\"type\":\"string\"},\"score\":{\"type\":\"integer\"}},\"required\":[\"summary\",\"score\"]}",
          ),
      ),
      SettingHelpTopic(
        id = "safety_settings",
        title = "Safety Filters & Thresholds",
        summary =
          "Configures content filtering thresholds across categories (hate speech, dangerous content, harassment, sexually explicit, jailbreak) using severity or probability.",
        whenToUse =
          "Keep on 'Model default' for normal use, or adjust to 'block_only_high' if benign security/medical/creative discussions are overly filtered.",
        examples =
          listOf(
            "Model default: Uses standard balanced safety thresholds",
            "block_only_high: Blocks only high-confidence harmful content",
          ),
      ),
      SettingHelpTopic(
        id = "service_tier",
        title = "Service Tier, Server Storage & Background Mode",
        summary =
          "Controls API routing priority, whether interactions are stored on the server for multi-turn chaining, and background polling for long-running tasks.",
        whenToUse =
          "Use 'standard' for everyday chat, 'flex' for discounted batch-style queries that can take longer, or 'priority' for mission-critical low latency.",
        examples =
          listOf(
            "Store on server = On: Chains turns via previous_interaction_id so full history isn't re-uploaded",
            "Background execution = On: Continues polling if a deep research turn takes several minutes",
          ),
      ),
      SettingHelpTopic(
        id = "advanced_json",
        title = "Labels & Extra JSON",
        summary =
          "Attaches custom key-value metadata labels for billing/tracking and merges arbitrary top-level JSON fields into the request body.",
        whenToUse =
          "Use Extra JSON to test brand-new API fields before dedicated UI controls exist. Use 'Preview request' to inspect the exact JSON payload.",
        examples =
          listOf(
            "Label → project: rivia-mobile",
            "Extra JSON → {\"custom_parameter\": \"value\"}",
          ),
      ),
    )

  val appSettingsTopics: List<SettingHelpTopic> =
    listOf(
      SettingHelpTopic(
        id = "api_keys",
        title = "Gemini & ElevenLabs API Keys",
        summary =
          "Your personal API keys authenticate directly with Google's Gemini API and ElevenLabs' Voice API. Keys are encrypted with Android Keystore AES-256.",
        whenToUse =
          "Paste your keys on first launch and tap 'Test' to verify connectivity and cache your available models and personal voices.",
        examples =
          listOf(
            "Test (List Models): Verifies your Gemini key and caches model token limits",
            "Test (List Voices): Verifies your ElevenLabs key and loads your personal cloned voices",
          ),
      ),
      SettingHelpTopic(
        id = "chat_backup",
        title = "Chat History Backup (Export / Import JSON)",
        summary =
          "Saves all of your conversations, messages, token stats, and per-chat Run Settings to a portable JSON file on your phone.",
        whenToUse =
          "Export periodically to back up your chats or move them to another device. API keys are never included in exported files.",
        examples =
          listOf(
            "Export JSON → Saves rivia_chats_export.json to Downloads or Google Drive",
            "Import JSON → Restores and merges chats from a backup file without wiping existing chats",
          ),
      ),
      SettingHelpTopic(
        id = "custom_voice",
        title = "Custom ElevenLabs Voice & Clone Strength",
        summary =
          "Selects which ElevenLabs personal voice Rivia uses when reading replies aloud or speaking in Live Mode.",
        whenToUse =
          "Tap 'Load Voices' to pick from your ElevenLabs account, or paste any Voice ID directly. Enable 'Professional voice clone' if using a Pro clone on eleven_v4.",
        examples =
          listOf(
            "Tap the Play icon next to any loaded voice to hear a sample preview",
            "Clone Strength (0.0 – 1.0): Higher values adhere more strictly to the original clone recording",
          ),
      ),
      SettingHelpTopic(
        id = "tts_models",
        title = "ElevenLabs TTS Models",
        summary =
          "Chooses separate synthesis models for Chat Read-Aloud (quality-first) and Live Voice Mode (latency-first).",
        whenToUse =
          "Use eleven_v4 for expressive read-aloud in chat, and eleven_v4_turbo or eleven_flash_v2_5 for rapid response in hands-free Live conversations.",
        examples =
          listOf(
            "Read-aloud default: eleven_v4 (richest emotional expression)",
            "Live mode default: eleven_v4_turbo (lowest time-to-first-audio)",
          ),
      ),
      SettingHelpTopic(
        id = "voice_tuning",
        title = "Voice Stability, Similarity & Speaker Boost",
        summary =
          "Fine-tunes how expressive vs. consistent Rivia's voice sounds.",
        whenToUse =
          "Lower stability makes the voice more dynamic and emotional; higher stability makes it steadier and more consistent.",
        examples =
          listOf(
            "Stability = 0.45: Natural, expressive conversational delivery",
            "Stability = 0.80: Steady, newsreader-style delivery",
            "Similarity Boost = 0.75: Keeps timbre close to the source voice without artifacts",
          ),
      ),
      SettingHelpTopic(
        id = "audio_tags_format",
        title = "Audio Tags, Normalization & Output Format",
        summary =
          "Controls MP3 bitrate/sample rate, number/date text normalization, and whether expressive tags like [laughs] or [softly] are sent to ElevenLabs.",
        whenToUse =
          "Keep 'Send audio tags' on so v4/v3 models perform bracketed cues vocally while Rivia strips them from the visible chat bubble.",
        examples =
          listOf(
            "Send audio tags = On: '[softly] Here is the secret' is spoken softly, but displays as 'Here is the secret'",
            "Output format = mp3_44100_128: High-clarity 44.1kHz stereo/mono MP3 stream",
          ),
      ),
      SettingHelpTopic(
        id = "stt_live_options",
        title = "Speech-to-Text & Live Mode Audio Routing",
        summary =
          "Configures recognition language (BCP-47 tag), on-device recognition preference, screen wake lock, and Bluetooth earbud mic behavior.",
        whenToUse =
          "Leave 'Use earbud mic in Live' off if you want high-bandwidth stereo output in your earbuds while using the phone's built-in mic.",
        examples =
          listOf(
            "Speech-to-text language: 'en-US', 'en-GB', 'es-ES', 'ja-JP'",
            "Use earbud mic = Off: Prevents Bluetooth headphones from dropping into low-bitrate call mode",
          ),
      ),
      SettingHelpTopic(
        id = "defaults_logs",
        title = "Default Run Settings & Request Log",
        summary =
          "Defaults let you configure the starting template for every new chat. Request Log records the last 100 API calls with latency and token breakdowns.",
        whenToUse =
          "Check Request Log whenever you want to inspect token usage, service tier headers, or troubleshoot an API error.",
        examples =
          listOf(
            "Edit Default Run Settings: Changes apply to future chats without altering existing chats",
            "Request Log: Shows In/Out/Thought tokens and exact millisecond latency per turn",
          ),
      ),
    )

  fun findTopic(id: String): SettingHelpTopic? =
    (runSettingsTopics + appSettingsTopics).firstOrNull { it.id == id }
}

@Composable
fun InfoHelpIconButton(
  contentDescription: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  IconButton(
    onClick = onClick,
    modifier = modifier.size(36.dp).minimumInteractiveComponentSize(),
  ) {
    Icon(
      imageVector = Icons.Outlined.Info,
      contentDescription = contentDescription,
      tint = RiviaTextTertiary,
      modifier = Modifier.size(18.dp),
    )
  }
}

@Composable
fun GuideHeaderButton(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    shape = RoundedCornerShape(18.dp),
    color = RiviaSurface2,
    modifier =
      modifier
        .minimumInteractiveComponentSize()
        .clip(RoundedCornerShape(18.dp))
        .clickable(onClick = onClick)
        .testTag("settings_guide_button"),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Icon(
        imageVector = Icons.Outlined.HelpOutline,
        contentDescription = "Open settings guide and examples",
        tint = RiviaIconDefault,
        modifier = Modifier.size(16.dp),
      )
      Text(
        text = "Guide",
        style = MaterialTheme.typography.labelLarge,
        color = RiviaTextPrimary,
        fontWeight = FontWeight.SemiBold,
      )
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingHelpBottomSheet(
  topic: SettingHelpTopic?,
  allTopics: List<SettingHelpTopic>? = null,
  sheetTitle: String = "Settings Guide & Examples",
  onDismiss: () -> Unit,
) {
  if (topic == null && allTopics == null) return
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = RiviaSurface1,
    contentColor = RiviaTextPrimary,
  ) {
    if (topic != null) {
      Column(
        modifier =
          Modifier.fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Surface(
            shape = CircleShape,
            color = RiviaSurface2,
            modifier = Modifier.size(36.dp),
          ) {
            Icon(
              imageVector = Icons.Outlined.Info,
              contentDescription = null,
              tint = RiviaTextTertiary,
              modifier = Modifier.padding(8.dp),
            )
          }
          Text(
            text = topic.title,
            style = MaterialTheme.typography.titleLarge,
            color = RiviaTextPrimary,
          )
        }

        Text(
          text = topic.summary,
          style = MaterialTheme.typography.bodyMedium,
          color = RiviaTextPrimary,
        )

        Surface(
          shape = RoundedCornerShape(14.dp),
          color = RiviaSurface2,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Text(
              text = "When to adjust",
              style = MaterialTheme.typography.labelLarge,
              color = RiviaTextPrimary,
            )
            Text(
              text = topic.whenToUse,
              style = MaterialTheme.typography.bodySmall,
              color = RiviaTextSecondary,
            )
          }
        }

        if (topic.examples.isNotEmpty()) {
          Surface(
            shape = RoundedCornerShape(14.dp),
            color = RiviaSurface2,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(
              modifier = Modifier.padding(14.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                Icon(
                  imageVector = Icons.Outlined.Lightbulb,
                  contentDescription = null,
                  tint = RiviaIconDefault,
                  modifier = Modifier.size(16.dp),
                )
                Text(
                  text = "Examples",
                  style = MaterialTheme.typography.labelLarge,
                  color = RiviaTextPrimary,
                )
              }
              topic.examples.forEach { ex ->
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  Text(
                    text = "•",
                    color = RiviaTextSecondary,
                    fontWeight = FontWeight.Bold,
                  )
                  Text(
                    text = ex,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = ChivoMonoFontFamily,
                    color = RiviaTextPrimary,
                    fontSize = 12.sp,
                  )
                }
              }
            }
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.End,
        ) {
          TextButton(onClick = onDismiss) {
            Text("Got it", color = RiviaTextPrimary, fontWeight = FontWeight.SemiBold)
          }
        }
      }
    } else if (allTopics != null) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = sheetTitle,
            style = MaterialTheme.typography.titleLarge,
            color = RiviaTextPrimary,
          )
          TextButton(onClick = onDismiss) {
            Text("Done", color = RiviaTextPrimary, fontWeight = FontWeight.SemiBold)
          }
        }
        HorizontalDivider(color = RiviaBorder)
        LazyColumn(
          modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
          contentPadding = PaddingValues(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          items(allTopics, key = { it.id }) { item ->
            Surface(
              shape = RoundedCornerShape(16.dp),
              color = RiviaSurface2,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Text(
                  text = item.title,
                  style = MaterialTheme.typography.titleMedium,
                  color = RiviaTextPrimary,
                )
                Text(
                  text = item.summary,
                  style = MaterialTheme.typography.bodySmall,
                  color = RiviaTextPrimary,
                )
                Text(
                  text = item.whenToUse,
                  style = MaterialTheme.typography.bodySmall,
                  color = RiviaTextSecondary,
                )
                if (item.examples.isNotEmpty()) {
                  Spacer(modifier = Modifier.height(2.dp))
                  item.examples.forEach { ex ->
                    Text(
                      text = "• $ex",
                      style = MaterialTheme.typography.labelSmall,
                      fontFamily = ChivoMonoFontFamily,
                      color = RiviaTextPrimary,
                      modifier =
                        Modifier.fillMaxWidth()
                          .background(RiviaBackground, RoundedCornerShape(8.dp))
                          .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}
