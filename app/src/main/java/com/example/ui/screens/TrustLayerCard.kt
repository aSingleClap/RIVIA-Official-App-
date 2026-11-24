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
import androidx.compose.material3.AlertDialog
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
import com.example.network.TabletTrustClient
import com.example.network.TrustResult
import com.example.network.TrustSnapshot
import com.example.ui.theme.RiviaBorder
import com.example.ui.theme.RiviaMint as RiviaTeal
import com.example.ui.theme.RiviaMutedText as RiviaSecondaryText
import com.example.ui.theme.RiviaSurface2 as RiviaPanelSurface
import com.example.ui.theme.RiviaTextPrimary
import com.example.viewmodel.RiviaViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// Step 6.9: Layer 6, Trust, on the Layers screen. Same look as Layer 5: a header and one
// tile per sub-step. Data comes from GET /app/trust with the app token; switches post to /app/trust/unlock and
// /app/trust/kill. Nothing here is a Gemini tool.
// Jordan (Nov 23): "I will decide what she unlocks and unlock it by hand. If scores on accuracy and my satisfaction
// support earned trust then she can bring it up". So every unlock switch is yours, asks before it changes, and the
// hard limits are shown read-only with no switch at all.
// In shadow, switching an unlock on is recorded but nothing reads it yet: the card says so plainly.

private data class TrustSubStep(
  val testTag: String,
  val title: String,
  val description: String,
  /** The tablet's piece ids whose values sit in this tile. Empty: nothing to adjust. */
  val pieces: List<String> = emptyList(),
  val shows: String = "",
)

private const val LAYER = 6

private val TRUST_SUB_STEPS =
  listOf(
    TrustSubStep(
      "trust_step_6_1",
      "6.1 Predictions",
      "Every plan she makes turns into checkable guesses: when you'll arrive, how long a ride takes, where a pin goes, which rules hold.",
      shows = "days",
    ),
    TrustSubStep(
      "trust_step_6_2",
      "6.2 Was she right?",
      "After the day, each guess is marked right, wrong, or skipped. A miss is put on her, on you (you changed plans), or on chance (outage, traffic). Only her misses count.",
      pieces = listOf("6.2"),
    ),
    TrustSubStep(
      "trust_step_6_3",
      "6.3 Your satisfaction",
      "Thumbs on her replies, plus a quiet day where you followed the plan counts as half a good mark. Kept apart from accuracy.",
      pieces = listOf("6.3"),
      shows = "satisfaction",
    ),
    TrustSubStep(
      "trust_step_6_4",
      "6.4 Sure by area",
      "How sure she is per area (a ride, a place, a rule). Her misses there lower it; it recovers over about a week.",
      pieces = listOf("6.4"),
      shows = "areas",
    ),
    TrustSubStep(
      "trust_step_6_5",
      "6.5 Double-check flags",
      "When an area gets shaky she'd double-check it before planning around it. In shadow this is only logged.",
      pieces = listOf("6.5"),
      shows = "flags",
    ),
    TrustSubStep(
      "trust_step_6_6",
      "6.6 Trust",
      "One number from 0 to 1 built from her own hits and misses. Starts low, is lost faster than it's won, and old days fade.",
      pieces = listOf("6.6"),
      shows = "trust",
    ),
    TrustSubStep(
      "trust_step_6_7",
      "6.7 Unlocks",
      "When trust, your satisfaction and enough days all pass an unlock's lines, she'd bring it up. You switch it on here, by hand. It pauses itself if she falls under a line.",
      pieces = listOf("6.7"),
      shows = "unlocks",
    ),
    TrustSubStep(
      "trust_step_6_8",
      "6.8 Off switch and log",
      "One switch turns every unlock off and starts trust over. Every switch, pause and suggestion is logged with its reason. Hard limits can never be unlocked.",
      shows = "kill",
    ),
    TrustSubStep(
      "trust_step_6_9",
      "6.9 Shadow mode",
      "Everything is worked out and logged without changing what she does yet.",
    ),
  )

/** One GET for the whole card; the switches post and then reload. */
internal class TrustCardState(private val viewModel: RiviaViewModel, private val scope: CoroutineScope) {
  private val client = TabletTrustClient()
  var snapshot by mutableStateOf<TrustSnapshot?>(null)
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
        when (val res = client.getTrust(url(), token())) {
          is TrustResult.Success -> {
            snapshot = res.snapshot
            message = null
          }
          is TrustResult.Failure -> fail(res)
        }
      } finally {
        isLoading = false
      }
    }
  }

  fun setUnlock(id: String, on: Boolean) = write { client.setUnlock(url(), token(), id, on) }

  fun setKill(kill: Boolean) = write { client.setKill(url(), token(), kill) }

  private fun write(call: suspend () -> TrustResult) {
    if (isLoading) return
    isLoading = true
    scope.launch {
      try {
        when (val res = call()) {
          is TrustResult.Success -> {
            message = res.message.ifBlank { "Saved" }.replaceFirstChar { it.uppercase() } to false
            snapshot = (client.getTrust(url(), token()) as? TrustResult.Success)?.snapshot ?: snapshot
          }
          is TrustResult.Failure -> fail(res)
        }
      } finally {
        isLoading = false
      }
    }
  }

  private suspend fun fail(res: TrustResult.Failure) {
    message = res.message to true
    if (res.errorCode == "http_401" && token().isNotBlank()) {
      viewModel.signOutGoogleAccount()
      viewModel.showError("Signed out, sign in again")
    }
  }

  private suspend fun url() = viewModel.keyRepository.getTabletServerUrl()

  private suspend fun token() = viewModel.keyRepository.getTabletAppToken()
}

@Composable
internal fun TrustLayerCard(expanded: Boolean, onToggleExpanded: () -> Unit, viewModel: RiviaViewModel) {
  val scope = rememberCoroutineScope()
  val card = remember { TrustCardState(viewModel, scope) }
  val adjust = remember { AdjustableValuesState(viewModel, scope, "/app/trust") }
  val open = remember { mutableStateMapOf<String, Boolean>() }
  // Asks before any switch changes: (title, words, what to do on yes).
  var confirm by remember { mutableStateOf<Triple<String, String, () -> Unit>?>(null) }

  LaunchedEffect(expanded) { if (expanded) card.loadIfNeeded() }

  Surface(
    color = RiviaPanelSurface,
    shape = RoundedCornerShape(14.dp),
    modifier = Modifier.fillMaxWidth().testTag("layer_card_trust"),
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggleExpanded).testTag("layer_header_trust"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Layer 6: Trust",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = RiviaTextPrimary,
          )
          LayerSwitchLine(6)
          Text(
            text =
              "Whether her plans have earned more freedom: her accuracy, your satisfaction, and what you've unlocked by hand. Shadow mode: worked out and logged, but it doesn't change what she does yet.",
            style = MaterialTheme.typography.bodySmall,
            color = RiviaSecondaryText,
          )
        }
        IconButton(onClick = onToggleExpanded, modifier = Modifier.testTag("toggle_layer_trust")) {
          Icon(
            imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = if (expanded) "Collapse Layer 6: Trust" else "Expand Layer 6: Trust",
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
          modifier = Modifier.fillMaxWidth().testTag("trust_sublayers_container"),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          val snap = card.snapshot
          when {
            card.isLoading && snap == null -> TrustHint("Asking the tablet…")
            snap == null && card.message != null -> TrustHint(card.message!!.first, error = true)
            snap != null && snap.note.isNotBlank() -> TrustHint(snap.note.replaceFirstChar { it.uppercase() } + ".")
          }
          card.message?.takeIf { snap != null }?.let { (m, err) -> TrustHint(m, error = err) }
          snap?.problems?.forEach { TrustHint(it, error = true) }

          for (step in TRUST_SUB_STEPS) {
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
                if (snap != null) {
                  TrustStepReadout(step.shows, snap, onUnlock = { u, on ->
                    confirm =
                      Triple(
                        if (on) "Switch on: ${u.words}?" else "Switch off: ${u.words}?",
                        if (on)
                          "This is recorded now. In shadow mode nothing reads it yet, so she won't act differently until a later step uses it."
                        else "She'd go back to asking you first for this.",
                      ) {
                        card.setUnlock(u.id, on)
                      }
                  }, onKill = { kill ->
                    confirm =
                      Triple(
                        if (kill) "Turn the off switch on?" else "Turn the off switch off?",
                        if (kill)
                          "Every unlock goes off and trust starts over from now. Her old logs are kept."
                        else "Unlocks can be switched on again, but trust keeps the fresh start.",
                      ) {
                        card.setKill(kill)
                      }
                  })
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

  confirm?.let { (title, words, onYes) ->
    AlertDialog(
      onDismissRequest = { confirm = null },
      title = { Text(title) },
      text = { Text(words) },
      confirmButton = {
        TextButton(
          onClick = {
            onYes()
            confirm = null
          },
          modifier = Modifier.testTag("trust_confirm_yes"),
        ) {
          Text("Yes")
        }
      },
      dismissButton = { TextButton(onClick = { confirm = null }) { Text("Not now") } },
    )
  }
}
