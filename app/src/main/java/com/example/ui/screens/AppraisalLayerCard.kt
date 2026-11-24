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
import com.example.network.AppraisalResult
import com.example.network.AppraisalSnapshot
import com.example.network.TabletAppraisalClient
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

// Step 3.9: Layer 3, Reactions, on the Layers screen. Replaces the text-only 3.1 to 3.9 tiles with live data from
// GET /app/appraisal (app token), same look as Layers 5 to 8: a header and one tile per sub-step, with the small
// "Adjust values" card (/app/appraisal) under the tiles that have values. Nothing here is a Gemini tool.
// Jordan (Nov 24): "Split" (the read and mood push ride on Step 7's Flash read on the tablet; rewording is tried on
// the tablet from a copy of her reply), "Keep it all" (her reply text kept for spot-checks), "The longer list" (14
// events), "Leave plans alone" (replies with times, plans, lists or directions are never reworded).
// Shadow: worked out and logged only. Her real mood and her replies don't change.
// Layer switches (Nov 24): Layer 3's whole-layer switch sits under its name and each piece's switch sits on its own
// tile (3.4 reading your message, 3.6 shadow rewording), with the small "Tablet" label, its Needs line, greyed with the
// reason when something it needs is off. 3.6 needs 3.8 fact check (always on): the tablet refuses rewording without it.

private data class AppraisalSubStep(
  val testTag: String,
  val title: String,
  val description: String,
  val pieces: List<String> = emptyList(),
  val shows: String = "",
)

private const val APPRAISAL_LAYER = 3

private val APPRAISAL_SUB_STEPS =
  listOf(
    AppraisalSubStep(
      "reactions_step_3_1",
      "3.1 What just happened",
      "A fixed list of events. Code spots her tool calls failing or working, your thumbs, her plan holding or missing, and her question going unanswered. From your typed messages: thanked, corrected, frustrated at something, rough words at her, something personal, good news, bad news. She never sounds afraid.",
      pieces = listOf("3.1"),
      shows = "counts",
    ),
    AppraisalSubStep(
      "reactions_step_3_2",
      "3.2 Set pushes",
      "Each event moves her three mood numbers a set amount and direction. Strength 1 to 3 picks how much of it.",
      pieces = listOf("3.2", "3.2s"),
      shows = "pushes",
    ),
    AppraisalSubStep(
      "reactions_step_3_3",
      "3.3 Per-message limit",
      "One message can move each mood number only so far. Her slower mood soaks up a small share of each push.",
      pieces = listOf("3.3"),
      shows = "mood",
    ),
    AppraisalSubStep(
      "reactions_step_3_4",
      "3.4 Reading your message",
      "Rides on Layer 7's Flash read of your typed message, so no extra call. Flash only names events and a strength, never numbers. Your text isn't kept.",
      shows = "skips",
    ),
    AppraisalSubStep(
      "reactions_step_3_5",
      "3.5 Caution flags",
      "Layers 4 and 6's double-check flags gathered into one line for her. Logged only.",
      shows = "caution",
    ),
    AppraisalSubStep(
      "reactions_step_3_6",
      "3.6 Rewording",
      "Flash rewrites her finished reply in her mood and Layer 8's style. Short replies, tool-only replies, and anything with times, plans, lists or directions are left alone.",
      pieces = listOf("3.6"),
      shows = "reword",
    ),
    AppraisalSubStep(
      "reactions_step_3_7",
      "3.7 Tone limits",
      "A rewrite fails if it sounds afraid, curses without banter on, aims anything at you, adds \"I understand\", or runs too long. Outward tone is capped even when her inner mood is stronger.",
      pieces = listOf("3.7"),
    ),
    AppraisalSubStep(
      "reactions_step_3_8",
      "3.8 Fact check",
      "Code compares the two versions: numbers, times, days, names, places, actions, \"not\"s and questions. Any change and the plain reply wins. Fixed on.",
    ),
    AppraisalSubStep(
      "reactions_step_3_9",
      "3.9 Shadow and spot-checks",
      "Both versions are kept so you can compare them. Her reply on your screen never changes yet.",
      shows = "samples",
    ),
  )

internal class AppraisalCardState(private val viewModel: RiviaViewModel, private val scope: CoroutineScope) {
  private val client = TabletAppraisalClient()
  var snapshot by mutableStateOf<AppraisalSnapshot?>(null)
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
        when (val res = client.getAppraisal(viewModel.keyRepository.getTabletServerUrl(), token())) {
          is AppraisalResult.Success -> {
            snapshot = res.snapshot
            message = null
          }
          is AppraisalResult.Failure -> {
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

private val APPRAISAL_TIME = SimpleDateFormat("MMM d, h:mm a", Locale.US)

private fun outcomeWords(o: String): String =
  when (o) {
    "passed" -> "Passed"
    "facts_failed" -> "A fact changed (plain kept)"
    "tone_failed" -> "Broke a tone limit (plain kept)"
    "skipped_short" -> "Short, left alone"
    "skipped_tool" -> "Tool-only, left alone"
    "skipped_plan" -> "Plan or times, left alone"
    "waiting" -> "Waiting"
    "off" -> "Rewording off"
    "no_key" -> "No Gemini key"
    "at_limit" -> "At your Layer 5 limit"
    "failed" -> "Flash didn't answer"
    "unusable" -> "Unusable answer"
    else -> o
  }

@Composable
private fun AppraisalLine(label: String, value: String) {
  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
    Text(label, style = MaterialTheme.typography.bodySmall, color = RiviaSecondaryText, modifier = Modifier.weight(1f))
    Text(value, style = MaterialTheme.typography.bodySmall, color = RiviaTextPrimary)
  }
}

@Composable
private fun AppraisalReadout(shows: String, snap: AppraisalSnapshot) {
  when (shows) {
    "counts" ->
      if (snap.eventCounts.isEmpty()) TrustHint("No events counted in the last 14 days.")
      else for ((k, v) in snap.eventCounts.entries.sortedByDescending { it.value }) AppraisalLine(k, "$v")
    "pushes" -> {
      if (snap.pushes.isEmpty()) TrustHint("No pushes yet.")
      for (p in snap.pushes.take(3)) {
        TrustHint("${APPRAISAL_TIME.format(Date(p.at))}: ${p.events.joinToString(", ")}")
        AppraisalLine("Would-be mood", "${p.before} → ${p.after}${if (p.limited) " (held at the limit)" else ""}")
      }
    }
    "mood" -> {
      AppraisalLine("Would-be mood (shadow)", snap.wouldBe + if (snap.wouldBeCapped) ", shown milder" else "")
      AppraisalLine("Her real mood", snap.realMood)
    }
    "skips" ->
      if (snap.skips.isEmpty()) TrustHint("Nothing skipped.")
      else for (s in snap.skips.take(3)) TrustHint("${APPRAISAL_TIME.format(Date(s.at))}: ${s.why}")
    "caution" ->
      if (snap.caution.isEmpty()) TrustHint("No caution flags right now.")
      else for (c in snap.caution) TrustHint("• $c")
    "reword" -> {
      if (snap.rewordNote.isNotBlank()) TrustHint(snap.rewordNote.replaceFirstChar { it.uppercase() } + ".")
      AppraisalLine("Replies kept", "${snap.keptReplies}")
      snap.passRate?.let { AppraisalLine("Passed both checks", "${(it * 100).toInt()}%") }
      snap.medianWaitMs?.let { AppraisalLine("Wait it would add (typical)", "${"%.1f".format(it / 1000.0)} s") }
      snap.slowestWaitMs?.let { AppraisalLine("Slowest", "${"%.1f".format(it / 1000.0)} s") }
      for ((k, v) in snap.outcomes.entries.sortedByDescending { it.value }) AppraisalLine(outcomeWords(k), "$v")
    }
    "samples" ->
      if (snap.samples.isEmpty()) TrustHint("Nothing to compare yet.")
      else
        for (s in snap.samples.take(3)) {
          TrustHint("${APPRAISAL_TIME.format(Date(s.at))}: ${outcomeWords(s.outcome)}${if (s.why.isNotBlank()) ", ${s.why}" else ""}")
          AppraisalLine("She said", s.plain)
          if (s.reworded.isNotBlank()) AppraisalLine("Reworded", s.reworded)
        }
  }
}

@Composable
internal fun AppraisalLayerCard(expanded: Boolean, onToggleExpanded: () -> Unit, viewModel: RiviaViewModel) {
  val scope = rememberCoroutineScope()
  val card = remember { AppraisalCardState(viewModel, scope) }
  val adjust = remember { AdjustableValuesState(viewModel, scope, "/app/appraisal") }
  val open = remember { mutableStateMapOf<String, Boolean>() }

  LaunchedEffect(expanded) { if (expanded) card.loadIfNeeded() }

  Surface(
    color = RiviaPanelSurface,
    shape = RoundedCornerShape(14.dp),
    modifier = Modifier.fillMaxWidth().testTag("layer_card_reactions"),
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggleExpanded).testTag("layer_header_reactions"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Layer 3: Reactions",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = RiviaTextPrimary,
          )
          LayerSwitchLine(3)
          Text(
            text =
              "What just happened and how far it would move her mood, plus a quiet test of rewording her replies. Shadow mode: logged only, her mood and replies don't change yet.",
            style = MaterialTheme.typography.bodySmall,
            color = RiviaSecondaryText,
            modifier = Modifier.testTag("reactions_description"),
          )
        }
        IconButton(onClick = onToggleExpanded, modifier = Modifier.testTag("toggle_layer_reactions")) {
          Icon(
            imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = if (expanded) "Collapse Layer 3: Reactions" else "Expand Layer 3: Reactions",
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
          modifier = Modifier.fillMaxWidth().testTag("reactions_sublayers_container"),
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

          for (step in APPRAISAL_SUB_STEPS) {
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
                if (snap != null && step.shows.isNotEmpty()) AppraisalReadout(step.shows, snap)
                if (step.pieces.isNotEmpty()) {
                  val isOpen = open[step.testTag] == true
                  TextButton(
                    onClick = { open[step.testTag] = !isOpen },
                    modifier = Modifier.testTag("${step.testTag}_adjust_toggle"),
                  ) {
                    Text(if (isOpen) "Hide values" else "Adjust values", color = RiviaTeal)
                  }
                  if (isOpen) {
                    for (piece in step.pieces) AdjustableValuesCard(state = adjust, layer = APPRAISAL_LAYER, piece = piece)
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
