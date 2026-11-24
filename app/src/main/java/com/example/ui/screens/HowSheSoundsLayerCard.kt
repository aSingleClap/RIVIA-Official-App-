package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.network.StyleResult
import com.example.network.StyleSnapshot
import com.example.network.TabletStyleClient
import com.example.ui.theme.RiviaBorder
import com.example.ui.theme.RiviaMint as RiviaTeal
import com.example.ui.theme.RiviaMutedText as RiviaSecondaryText
import com.example.ui.theme.RiviaSurface2 as RiviaPanelSurface
import com.example.ui.theme.RiviaTextPrimary
import com.example.viewmodel.RiviaViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// Step 8.11: Layer 8, How she sounds, on the Layers screen. Same look as Layers 5 to 7: a header and one tile per
// sub-step. Data comes from GET /app/style with the app token; values are adjusted on the same small card the other
// layers use (/app/style). Nothing here is a Gemini tool. Read-only apart from the values card.
// Jordan (Nov 24): when he's amped "she shouldn't get amped up with me but over 3 messages she can start to tone it
// down witha gradual drop"; a little warmer toward what he says, "but if im a cursing screaming mess then she
// shouldnt match that"; irritated = "Light sass is fine at the problem dry, no sass would be like a step above that".
// Shadow: worked out and logged only. Her replies don't change until the rewording pass (Step 3) is live.

private data class StyleSubStep(
  val testTag: String,
  val title: String,
  val description: String,
  val pieces: List<String> = emptyList(),
  val shows: String = "",
)

private const val LAYER = 8

private val STYLE_SUB_STEPS =
  listOf(
    StyleSubStep(
      "style_step_8_1",
      "8.1 Your energy",
      "From each message you type to Rivia: swearing, ALL-CAPS words, rapid-fire messages, and Layer 7's read against your normal. Only counts are kept; your text is dropped. Until a situation has enough messages, only you saying you're annoyed or stressed counts.",
      pieces = listOf("8.1"),
      shows = "energy",
    ),
    StyleSubStep(
      "style_step_8_2",
      "8.2 Steady, then calmer",
      "When you're worked up she never rises with you. She holds her own level, then eases down over a few messages to a bit calmer than her usual, never flat. Back to usual once you've settled.",
      pieces = listOf("8.2"),
      shows = "plan",
    ),
    StyleSubStep(
      "style_step_8_2b",
      "8.2b Warmth",
      "A little warmer toward what you're saying when you're stressed or it's personal. Never matching your energy, never cursing back.",
      pieces = listOf("8.2b"),
    ),
    StyleSubStep(
      "style_step_8_3",
      "8.3 Style",
      "Irritated: dry, with light sass at the problem; more irritated: dry, no sass, more focused. Happy: playful. Distressed or sad: deadpan. Focused: crisp. Anything else: plain.",
      pieces = listOf("8.3"),
      shows = "latest",
    ),
    StyleSubStep(
      "style_step_8_4",
      "8.4 Strength",
      "How strong each style can get. When you're stressed, every style is held to 1 and playful turns plain (8.5).",
      pieces = listOf("8.4"),
      shows = "counts",
    ),
    StyleSubStep(
      "style_step_8_6",
      "8.6 Banter",
      "Cursing is off unless you turn it on here. Even on, never while you're stressed or cursing.",
      pieces = listOf("8.6"),
    ),
    StyleSubStep(
      "style_step_8_7",
      "8.7 Learning from your reactions",
      "Laughing along or ignoring her styled replies nudges a style up or down a step after a streak. \"Knock it off\" turns that style off for a while. A correction never counts: that's about what she said, not how.",
      pieces = listOf("8.7"),
      shows = "learned",
    ),
    StyleSubStep(
      "style_step_8_8",
      "8.8 Joke cooldown",
      "A bit she's used can't come back for a while.",
      pieces = listOf("8.8"),
      shows = "bits",
    ),
    StyleSubStep(
      "style_step_8_9",
      "8.9 Pushback guard",
      "Fixed, not adjustable.",
      shows = "rules",
    ),
  )

internal class StyleCardState(private val viewModel: RiviaViewModel, private val scope: CoroutineScope) {
  private val client = TabletStyleClient()
  var snapshot by mutableStateOf<StyleSnapshot?>(null)
  var isLoading by mutableStateOf(false)
  var message by mutableStateOf<String?>(null)

  fun loadIfNeeded() {
    if (snapshot == null && !isLoading) load()
  }

  fun load() {
    if (isLoading) return
    isLoading = true
    scope.launch {
      try {
        when (val res = client.getStyle(viewModel.keyRepository.getTabletServerUrl(), token())) {
          is StyleResult.Success -> {
            snapshot = res.snapshot
            message = null
          }
          is StyleResult.Failure -> {
            message = res.message
            if (res.errorCode == "http_401" && token().isNotBlank()) {
              viewModel.signOutGoogleAccount()
              viewModel.showError("Signed out, sign in again")
            }
          }
        }
      } finally {
        isLoading = false
      }
    }
  }

  private suspend fun token() = viewModel.keyRepository.getTabletAppToken()
}

private val STYLE_TIME = SimpleDateFormat("MMM d, h:mm a", Locale.US)

private fun styleName(s: String): String =
  when (s) {
    "dry" -> "Dry"
    "playful" -> "Playful"
    "deadpan" -> "Deadpan"
    "crisp" -> "Crisp"
    else -> "Plain"
  }

@Composable
private fun StyleLine(label: String, value: String) {
  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
    Text(label, style = MaterialTheme.typography.bodySmall, color = RiviaSecondaryText, modifier = Modifier.weight(1f))
    Text(value, style = MaterialTheme.typography.bodySmall, color = RiviaTextPrimary)
  }
}

@Composable
private fun StyleReadout(shows: String, snap: StyleSnapshot) {
  when (shows) {
    "energy" -> {
      StyleLine("Messages, last 14 days", "${snap.messages}")
      StyleLine("Worked up", "${snap.workedUp}")
      StyleLine("With swearing", "${snap.swearing}")
      StyleLine("Rapid-fire", "${snap.rapidFire}")
      snap.latest?.takeIf { it.workedUp && it.why.isNotBlank() }?.let { TrustHint("Last time: ${it.why}.") }
    }
    "plan" ->
      if (snap.energyPhase == "easing") StyleLine("Right now", snap.latest?.energy ?: "easing down")
      else StyleLine("Right now", "her usual energy")
    "latest" -> {
      val l = snap.latest
      if (l == null) TrustHint("Nothing worked out yet.")
      else {
        StyleLine("Would have been", "${styleName(l.style)}${if (l.style != "plain") ", strength ${l.strength}" else ""}")
        StyleLine("Her state", l.herLabel)
        if (l.style == "dry") StyleLine("Sass", if (l.sass) "light, at the problem" else "none")
        if (l.warmth > 0) StyleLine("Warmth", "a little warmer")
        StyleLine("Cursing", if (l.cursingOk) "allowed (banter)" else "no")
        for (n in l.notes) TrustHint("• $n")
        TrustHint("At ${STYLE_TIME.format(Date(l.at))}. Shadow: logged only, her reply wasn't changed.")
      }
    }
    "counts" ->
      if (snap.styleCounts.isEmpty()) TrustHint("No styles worked out yet.")
      else for ((k, v) in snap.styleCounts.entries.sortedByDescending { it.value }) StyleLine(styleName(k), "$v")
    "learned" -> {
      if (snap.learned.isEmpty()) TrustHint("Nothing learned yet. Her replies aren't styled in shadow, so there's nothing to react to.")
      for (x in snap.learned) {
        val shift = if (x.adj == 0) "no change" else if (x.adj > 0) "up ${x.adj}" else "down ${-x.adj}"
        StyleLine(styleName(x.style), shift + (x.mutedUntil?.let { ", off until ${STYLE_TIME.format(Date(it))}" } ?: ""))
      }
    }
    "bits" -> StyleLine("Bits cooling down", "${snap.bitsCooling}")
    "rules" -> for (r in snap.fixedRules) TrustHint("• $r")
  }
}

@Composable
internal fun HowSheSoundsLayerCard(expanded: Boolean, onToggleExpanded: () -> Unit, viewModel: RiviaViewModel) {
  val scope = rememberCoroutineScope()
  val card = remember { StyleCardState(viewModel, scope) }
  val adjust = remember { AdjustableValuesState(viewModel, scope, "/app/style") }
  val open = remember { mutableStateMapOf<String, Boolean>() }

  LaunchedEffect(expanded) { if (expanded) card.loadIfNeeded() }

  Surface(
    color = RiviaPanelSurface,
    shape = RoundedCornerShape(14.dp),
    modifier = Modifier.fillMaxWidth().testTag("layer_card_style"),
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggleExpanded).testTag("layer_header_style"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Layer 8: How she sounds",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = RiviaTextPrimary,
          )
          LayerSwitchLine(8)
          Text(
            text =
              "The tone she would use with you right now. Shadow mode: worked out and logged, her replies don't change yet.",
            style = MaterialTheme.typography.bodySmall,
            color = RiviaSecondaryText,
          )
        }
        IconButton(onClick = onToggleExpanded, modifier = Modifier.testTag("toggle_layer_style")) {
          Icon(
            imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = if (expanded) "Collapse Layer 8: How she sounds" else "Expand Layer 8: How she sounds",
            tint = RiviaSecondaryText,
          )
        }
      }

      AnimatedVisibility(
        visible = expanded,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
      ) {
        Column(
          modifier = Modifier.fillMaxWidth().testTag("style_sublayers_container"),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          val snap = card.snapshot
          when {
            card.isLoading && snap == null -> TrustHint("Asking the tablet…")
            snap == null && card.message != null -> TrustHint(card.message!!, error = true)
            snap != null && snap.note.isNotBlank() -> TrustHint(snap.note.replaceFirstChar { it.uppercase() } + ".")
          }
          snap?.problems?.forEach { TrustHint(it.replaceFirstChar { c -> c.uppercase() } + ".", error = true) }
          snap?.waitsOn?.forEach { TrustHint("• $it") }

          for (step in STYLE_SUB_STEPS) {
            Surface(
              color = MaterialTheme.colorScheme.background,
              shape = RoundedCornerShape(10.dp),
              border = BorderStroke(1.dp, RiviaBorder),
              modifier = Modifier.fillMaxWidth().testTag(step.testTag),
            ) {
              Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                  step.title,
                  style = MaterialTheme.typography.labelLarge,
                  color = RiviaTextPrimary,
                  fontWeight = FontWeight.SemiBold,
                )
                Text(step.description, style = MaterialTheme.typography.bodySmall, color = RiviaSecondaryText)
                PieceSwitches(step.testTag)
                if (snap != null && step.shows.isNotEmpty()) StyleReadout(step.shows, snap)
                if (step.pieces.isNotEmpty()) {
                  val isOpen = open[step.testTag] == true
                  TextButton(
                    onClick = { open[step.testTag] = !isOpen },
                    modifier = Modifier.testTag("${step.testTag}_adjust_toggle"),
                  ) {
                    Text(if (isOpen) "Hide values" else "Adjust values", color = RiviaTeal)
                  }
                  if (isOpen) {
                    for (piece in step.pieces) AdjustableValuesCard(state = adjust, layer = LAYER, piece = piece)
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
