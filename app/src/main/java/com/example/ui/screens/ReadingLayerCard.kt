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
import com.example.network.ReadingResult
import com.example.network.ReadingSnapshot
import com.example.network.TabletReadingClient
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

// Step 7.11: Layer 7, Reading you, on the Layers screen. Same look as Layers 5 and 6: a header and one tile per
// sub-step. Data comes from GET /app/reading with the app token; Confirm and Reject on a pattern post to
// /app/reading/pattern. Nothing here is a Gemini tool.
// Jordan (Nov 24): his mood comes from "my vad scores and what I say for now", "or me telling her if she asks". No
// mood button. Situations come from his calendar (HMC shifts only, Giant dropped). In shadow she never actually
// asks: the questions she would have asked are listed here so you can see whether they'd have been fair.

private data class ReadingSubStep(
  val testTag: String,
  val title: String,
  val description: String,
  val pieces: List<String> = emptyList(),
  val shows: String = "",
)

private const val LAYER = 7

private val READING_SUB_STEPS =
  listOf(
    ReadingSubStep(
      "reading_step_7_1",
      "7.1 Message facts",
      "For each message you type to Rivia: how long it is, how quickly you answered her, whether it was clipped or a correction, and how your words read (a word list and a Flash read). Your text is read on the tablet and dropped; only numbers are kept.",
      pieces = listOf("7.1"),
      shows = "scores",
    ),
    ReadingSubStep(
      "reading_step_7_2",
      "7.2 Situation",
      "Which part of your day a message falls in, from the HMC shifts on your calendar: before a shift, after one, a day off, or other.",
      pieces = listOf("7.2"),
      shows = "calendar",
    ),
    ReadingSubStep(
      "reading_step_7_3",
      "7.3 Your normal",
      "What's usual for you in each situation, from your own messages. Nothing in a situation is judged until it has enough messages.",
      pieces = listOf("7.3"),
      shows = "normals",
    ),
    ReadingSubStep(
      "reading_step_7_4",
      "7.4 How far off",
      "Your last few messages against your normal there: normal, a bit off, or well off. Being short after a shift is normal if that's what you usually do.",
      pieces = listOf("7.4"),
      shows = "now",
    ),
    ReadingSubStep(
      "reading_step_7_7",
      "7.7 Pattern guesses",
      "Repeats the tablet notices, shown as guesses. Only ones you confirm would count. Reject the ones that are wrong.",
      pieces = listOf("7.7"),
      shows = "patterns",
    ),
    ReadingSubStep(
      "reading_step_7_8",
      "7.8 Ask or not",
      "Code decides, not her: a weak signal means nothing; an unclear one means she'd ask; a confirmed pattern means she'd quietly adjust. In shadow she never asks. These are what she would have asked.",
      pieces = listOf("7.8"),
      shows = "asks",
    ),
    ReadingSubStep(
      "reading_step_7_9",
      "7.9 Second opinion",
      "For personal messages, the Flash read also says if there's another way to read it. That's a reason she'd ask instead of assume.",
    ),
    ReadingSubStep(
      "reading_step_7_left",
      "Left out for now",
      "Not part of this step.",
      shows = "left_out",
    ),
  )

internal class ReadingCardState(private val viewModel: RiviaViewModel, private val scope: CoroutineScope) {
  private val client = TabletReadingClient()
  var snapshot by mutableStateOf<ReadingSnapshot?>(null)
  var isLoading by mutableStateOf(false)
  var message by mutableStateOf<Pair<String, Boolean>?>(null)

  fun loadIfNeeded() {
    if (snapshot == null && !isLoading) load()
  }

  fun load() {
    if (isLoading) return
    isLoading = true
    scope.launch {
      try {
        when (val res = client.getReading(url(), token())) {
          is ReadingResult.Success -> {
            snapshot = res.snapshot
            message = null
          }
          is ReadingResult.Failure -> fail(res)
        }
      } finally {
        isLoading = false
      }
    }
  }

  fun decide(id: Int, confirm: Boolean) {
    if (isLoading) return
    isLoading = true
    scope.launch {
      try {
        when (val res = client.decidePattern(url(), token(), id, confirm)) {
          is ReadingResult.Success -> {
            message = res.message.ifBlank { "Saved" }.replaceFirstChar { it.uppercase() } to false
            snapshot = (client.getReading(url(), token()) as? ReadingResult.Success)?.snapshot ?: snapshot
          }
          is ReadingResult.Failure -> fail(res)
        }
      } finally {
        isLoading = false
      }
    }
  }

  private suspend fun fail(res: ReadingResult.Failure) {
    message = res.message to true
    if (res.errorCode == "http_401" && token().isNotBlank()) {
      viewModel.signOutGoogleAccount()
      viewModel.showError("Signed out, sign in again")
    }
  }

  private suspend fun url() = viewModel.keyRepository.getTabletServerUrl()

  private suspend fun token() = viewModel.keyRepository.getTabletAppToken()
}

private val READING_TIME = SimpleDateFormat("MMM d, h:mm a", Locale.US)

private val SITUATION_WORDS =
  mapOf(
    "before_shift" to "Before an HMC shift",
    "after_shift" to "After an HMC shift",
    "day_off" to "Day off",
    "other" to "Other",
  )

private fun zoneWords(zone: String): String =
  when (zone) {
    "normal" -> "normal for you"
    "bit_off" -> "a bit off"
    "well_off" -> "well off"
    else -> "not enough yet"
  }

private fun measureWords(m: String): String =
  when (m) {
    "words" -> "Message length"
    "gap_min" -> "Reply speed"
    "terse" -> "Clipped messages"
    "correction" -> "Corrections"
    "valence" -> "How pleasant your words read"
    "arousal" -> "How charged your words read"
    else -> m
  }

private fun middleWords(unit: String, x: Double?): String =
  when {
    x == null -> "not yet"
    unit == "share" -> "${(x * 100).toInt()}%"
    unit == "minutes" -> String.format(Locale.US, "%.1f min", x)
    unit == "words" -> String.format(Locale.US, "%.0f words", x)
    else -> String.format(Locale.US, "%.2f", x)
  }

@Composable
private fun ReadingLine(label: String, value: String) {
  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
    Text(label, style = MaterialTheme.typography.bodySmall, color = RiviaSecondaryText, modifier = Modifier.weight(1f))
    Text(value, style = MaterialTheme.typography.bodySmall, color = RiviaTextPrimary)
  }
}

@Composable
private fun ReadingReadout(shows: String, snap: ReadingSnapshot, onDecide: (Int, Boolean) -> Unit) {
  when (shows) {
    "scores" -> {
      ReadingLine("Word list", if (snap.wordList == "on") "On" else "Off")
      ReadingLine("Flash read", if (snap.flashOn) "On" else "Off")
      if (snap.flashToday.isNotEmpty()) {
        val parts = snap.flashToday.entries.joinToString(", ") { (k, v) ->
          val w = when (k) {
            "ok" -> "read"
            "no_key" -> "no key on the tablet"
            "at_limit" -> "skipped at your limit"
            "failed" -> "Google didn't answer"
            "unusable" -> "answer not usable"
            "off" -> "off"
            else -> k
          }
          "$v $w"
        }
        TrustHint("Today: $parts.")
      }
    }
    "calendar" ->
      when (snap.calendarOk) {
        null -> TrustHint("Not read yet.")
        true -> TrustHint("Calendar read fine.")
        false -> TrustHint(snap.calendarNote.ifBlank { "Calendar couldn't be read" }.replaceFirstChar { it.uppercase() } +
          ". Messages count as \"other\" until it can.", error = true)
      }
    "normals" ->
      for (s in snap.situations) {
        val name = SITUATION_WORDS[s.id] ?: s.words
        if (!s.enough) {
          val need = s.normals.firstOrNull()?.need
          ReadingLine(name, if (need != null) "${s.messages} of $need messages" else "${s.messages} messages")
        } else {
          Text(name, style = MaterialTheme.typography.labelMedium, color = RiviaTextPrimary)
          for (n in s.normals) ReadingLine("  ${measureWords(n.measure)}", middleWords(n.unit, n.middle))
        }
      }
    "now" ->
      if (snap.now.isEmpty()) TrustHint("No messages in the last few hours.")
      else {
        TrustHint("Right now: ${SITUATION_WORDS[snap.nowSituation] ?: snap.nowSituation}.")
        for (m in snap.now) ReadingLine(measureWords(m.measure), zoneWords(m.zone))
        if (snap.checksTotal > 0) {
          TrustHint("When you said how you were: matched ${snap.checksAgree}, didn't match ${snap.checksDisagree}, of ${snap.checksTotal}.")
        }
      }
    "patterns" ->
      if (snap.patterns.isEmpty()) TrustHint("No guesses yet. A repeat has to show up on enough separate days first.")
      else
        for (p in snap.patterns) {
          Column(modifier = Modifier.fillMaxWidth().testTag("reading_pattern_${p.id}")) {
            Text(p.words, style = MaterialTheme.typography.bodySmall, color = RiviaTextPrimary)
            Text(
              when (p.state) {
                "confirmed" -> "You confirmed this. Seen on ${p.daysSeen} days."
                "rejected" -> "You said this is wrong. Seen on ${p.daysSeen} days."
                else -> "A guess. Seen on ${p.daysSeen} days."
              },
              style = MaterialTheme.typography.bodySmall,
              color = RiviaSecondaryText,
            )
            Row {
              if (p.state != "confirmed") {
                TextButton(onClick = { onDecide(p.id, true) }, modifier = Modifier.testTag("reading_confirm_${p.id}")) {
                  Text("That's right", color = RiviaTeal)
                }
              }
              if (p.state != "rejected") {
                TextButton(onClick = { onDecide(p.id, false) }, modifier = Modifier.testTag("reading_reject_${p.id}")) {
                  Text("That's wrong", color = RiviaTeal)
                }
              }
            }
          }
        }
    "asks" -> {
      if (snap.wouldAsk.isEmpty()) TrustHint("She wouldn't have asked anything yet.")
      for (a in snap.wouldAsk.takeLast(5).reversed()) {
        ReadingLine(READING_TIME.format(Date(a.at)), "")
        Text("\"${a.question}\"", style = MaterialTheme.typography.bodySmall, color = RiviaTextPrimary)
      }
      if (snap.skipped.isNotEmpty()) TrustHint("Held back ${snap.skipped.size} more (daily cap or a topic you brushed off).")
      if (snap.wouldAdjust > 0) TrustHint("She'd have quietly adjusted to a confirmed pattern ${snap.wouldAdjust} times.")
      for (r in snap.wordingRules) TrustHint("• $r")
    }
    "left_out" -> for (x in snap.leftOut) TrustHint("• $x")
  }
}

@Composable
internal fun ReadingLayerCard(expanded: Boolean, onToggleExpanded: () -> Unit, viewModel: RiviaViewModel) {
  val scope = rememberCoroutineScope()
  val card = remember { ReadingCardState(viewModel, scope) }
  val adjust = remember { AdjustableValuesState(viewModel, scope, "/app/reading") }
  val open = remember { mutableStateMapOf<String, Boolean>() }

  LaunchedEffect(expanded) { if (expanded) card.loadIfNeeded() }

  Surface(
    color = RiviaPanelSurface,
    shape = RoundedCornerShape(14.dp),
    modifier = Modifier.fillMaxWidth().testTag("layer_card_reading"),
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggleExpanded).testTag("layer_header_reading"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Layer 7: Reading you",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = RiviaTextPrimary,
          )
          LayerSwitchLine(7)
          Text(
            text =
              "How your messages compare with what's normal for you at that time of day. Shadow mode: worked out and logged, she never asks or changes how she talks yet.",
            style = MaterialTheme.typography.bodySmall,
            color = RiviaSecondaryText,
          )
        }
        IconButton(onClick = onToggleExpanded, modifier = Modifier.testTag("toggle_layer_reading")) {
          Icon(
            imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = if (expanded) "Collapse Layer 7: Reading you" else "Expand Layer 7: Reading you",
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
          modifier = Modifier.fillMaxWidth().testTag("reading_sublayers_container"),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          val snap = card.snapshot
          when {
            card.isLoading && snap == null -> TrustHint("Asking the tablet…")
            snap == null && card.message != null -> TrustHint(card.message!!.first, error = true)
            snap != null && snap.note.isNotBlank() -> TrustHint(snap.note.replaceFirstChar { it.uppercase() } + ".")
          }
          card.message?.takeIf { snap != null }?.let { (m, err) -> TrustHint(m, error = err) }
          snap?.problems?.forEach { TrustHint(it.replaceFirstChar { c -> c.uppercase() } + ".", error = true) }

          for (step in READING_SUB_STEPS) {
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
                if (snap != null && step.shows.isNotEmpty()) {
                  ReadingReadout(step.shows, snap, onDecide = { id, yes -> card.decide(id, yes) })
                }
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
