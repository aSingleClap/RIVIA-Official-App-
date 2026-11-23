package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.network.AdjustableDraft
import com.example.network.AdjustablePiece
import com.example.network.AdjustableSetting
import com.example.network.AdjustableSnapshot
import com.example.network.ProgressResult
import com.example.network.TabletProgressClient
import com.example.ui.theme.ChivoMonoFontFamily as MonoFontFamily
import com.example.ui.theme.RiviaBorder
import com.example.ui.theme.RiviaErrorRed as RiviaDestructive
import com.example.ui.theme.RiviaMint as RiviaTeal
import com.example.ui.theme.RiviaMutedText as RiviaSecondaryText
import com.example.ui.theme.RiviaOnAccent
import com.example.ui.theme.RiviaSurface1
import com.example.ui.theme.RiviaTextPrimary
import com.example.ui.theme.RiviaTextTertiary
import com.example.ui.theme.RiviaWarningAmber as RiviaAmber
import com.example.viewmodel.RiviaViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// Step 5.7: one small card of adjustable values for one step ("piece") of a layer.
// Jordan (Nov 23): "The card should be for adjusting values at the step they are at not all in one card. Eventually
// I'll add a screen where there are only the adjustable values for each layer".
//
// So this card knows nothing about Step 5. It draws whatever the tablet lists for one (layer, piece): a switch, a
// slider when the range is small, a number box otherwise, the code's value beside each, Save and "Put back" for that
// step only. Layer 5's sub-step tiles each hold one; a future "adjustable values" screen can list every piece of
// AdjustableSnapshot.pieces with the same card and the same AdjustableValuesState.
//
// The tablet keeps the code's values and checks every save (ranges, weights to 100%, locked limits). This card only
// shows its answer. Nothing here is a Gemini tool.

/** One GET for every card on the screen; each card saves only its own step. */
internal class AdjustableValuesState(
  private val viewModel: RiviaViewModel,
  private val scope: CoroutineScope,
) {
  private val client = TabletProgressClient()
  var snapshot by mutableStateOf<AdjustableSnapshot?>(null)
  var isLoading by mutableStateOf(false)
  var loadError by mutableStateOf<String?>(null)
  /** Per piece: the last message after a save or reset, and whether it was an error. */
  val messages = mutableStateMapOf<String, Pair<String, Boolean>>()
  /** Per piece: what the card has switched or typed. Cleared for that piece after a save. */
  val drafts = mutableStateMapOf<String, AdjustableDraft>()

  fun key(p: AdjustablePiece) = "${p.layer}/${p.piece}"

  fun draft(p: AdjustablePiece): AdjustableDraft = drafts[key(p)] ?: AdjustableDraft()

  fun setSwitch(p: AdjustablePiece, path: String, on: Boolean) {
    val d = draft(p)
    drafts[key(p)] = d.copy(switches = d.switches + (path to on))
  }

  fun setText(p: AdjustablePiece, path: String, text: String) {
    val d = draft(p)
    drafts[key(p)] = d.copy(texts = d.texts + (path to text))
  }

  fun loadIfNeeded() {
    if (snapshot == null && !isLoading) load()
  }

  fun load() {
    if (isLoading) return
    isLoading = true
    scope.launch {
      try {
        when (val res = client.getProgress(serverUrl(), token())) {
          is ProgressResult.Success -> {
            snapshot = res.snapshot
            loadError = null
          }
          is ProgressResult.Failure -> {
            loadError = res.message
            signOutOn401(res)
          }
        }
      } finally {
        isLoading = false
      }
    }
  }

  fun save(p: AdjustablePiece) {
    val (changes, err) = TabletProgressClient.buildChanges(p, draft(p))
    if (err != null || changes == null) {
      messages[key(p)] = (err ?: "Check the values") to true
      return
    }
    if (changes.length() == 0) {
      messages[key(p)] = "Nothing to save" to false
      return
    }
    post(p, TabletProgressClient.buildSetJson(changes))
  }

  fun putBack(p: AdjustablePiece) = post(p, TabletProgressClient.buildResetJson("piece:${p.piece}"))

  private fun post(p: AdjustablePiece, payload: org.json.JSONObject) {
    if (isLoading) return
    isLoading = true
    scope.launch {
      try {
        when (val res = client.post(serverUrl(), token(), payload)) {
          is ProgressResult.Success -> {
            snapshot = res.snapshot
            drafts.remove(key(p))
            val n = res.edited.size
            messages[key(p)] =
              (if (n == 0) "Nothing changed" else "Saved $n change${if (n == 1) "" else "s"}") to false
          }
          is ProgressResult.Failure -> {
            // The tablet's own plain reason, e.g. "the weights of the scores that are on add up to 105%, not 100%".
            messages[key(p)] = res.message to true
            signOutOn401(res)
          }
        }
      } finally {
        isLoading = false
      }
    }
  }

  private suspend fun serverUrl() = viewModel.keyRepository.getTabletServerUrl()

  private suspend fun token() = viewModel.keyRepository.getTabletAppToken()

  private suspend fun signOutOn401(res: ProgressResult.Failure) {
    if (res.errorCode == "http_401" && token().isNotBlank()) {
      viewModel.signOutGoogleAccount()
      viewModel.showError("Signed out, sign in again")
    }
  }
}

/** The small card for one step. Draws nothing but a short line until the tablet has answered. */
@Composable
internal fun AdjustableValuesCard(
  state: AdjustableValuesState,
  layer: Int,
  piece: String,
  modifier: Modifier = Modifier,
) {
  LaunchedEffect(Unit) { state.loadIfNeeded() }
  var confirmPutBack by remember { mutableStateOf(false) }
  val snap = state.snapshot
  val p = snap?.piece(layer, piece)

  Surface(
    color = RiviaSurface1,
    shape = RoundedCornerShape(10.dp),
    border = BorderStroke(1.dp, RiviaBorder),
    modifier = modifier.fillMaxWidth().testTag("adjust_${layer}_${piece}"),
  ) {
    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      if (p == null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          if (state.isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = RiviaTeal)
            Text("  Loading from the tablet", style = MaterialTheme.typography.bodySmall, color = RiviaSecondaryText)
          } else {
            Text(
              state.loadError ?: "This step has no adjustable values on the tablet",
              style = MaterialTheme.typography.bodySmall,
              color = if (state.loadError != null) RiviaDestructive else RiviaSecondaryText,
              modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { state.load() }) { Text("Retry", color = RiviaTeal) }
          }
        }
        return@Column
      }

      Text(p.title, style = MaterialTheme.typography.labelLarge, color = RiviaTextPrimary, fontWeight = FontWeight.SemiBold)
      if (p.about.isNotBlank()) {
        Text(p.about, style = MaterialTheme.typography.bodySmall, color = RiviaSecondaryText)
      }
      for (problem in snap?.problems.orEmpty()) {
        Text(problem, style = MaterialTheme.typography.bodySmall, color = RiviaAmber)
      }

      val draft = state.draft(p)
      val enabled = !state.isLoading
      for (s in p.settings) {
        SettingRow(s = s, draft = draft, enabled = enabled, onSwitch = { state.setSwitch(p, s.path, it) },
          onText = { state.setText(p, s.path, it) })
      }

      TabletProgressClient.weightTotal(p, draft)?.let { total ->
        Text(
          "Total of the scores that are on: $total%" + if (total == 100) "" else " (needs to be 100%)",
          style = MaterialTheme.typography.bodySmall,
          color = if (total == 100) RiviaSecondaryText else RiviaAmber,
          modifier = Modifier.testTag("adjust_${layer}_${piece}_total"),
        )
      }
      for (n in p.notes) {
        Text(n, style = MaterialTheme.typography.bodySmall, color = RiviaTextTertiary)
      }

      state.messages[state.key(p)]?.let { (msg, isError) ->
        Text(
          msg,
          style = MaterialTheme.typography.bodySmall,
          color = if (isError) RiviaDestructive else RiviaTeal,
          modifier = Modifier.testTag("adjust_${layer}_${piece}_message"),
        )
      }

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Button(
          onClick = { state.save(p) },
          enabled = enabled && (draft.switches.isNotEmpty() || draft.texts.isNotEmpty()),
          colors = ButtonDefaults.buttonColors(containerColor = RiviaTeal, contentColor = RiviaOnAccent),
          modifier = Modifier.testTag("adjust_${layer}_${piece}_save"),
        ) {
          Text("Save")
        }
        OutlinedButton(
          onClick = { confirmPutBack = true },
          enabled = enabled && p.settings.any { it.changed },
          modifier = Modifier.testTag("adjust_${layer}_${piece}_put_back"),
        ) {
          Text("Put back code values", color = RiviaTextPrimary)
        }
        if (state.isLoading) {
          CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = RiviaTeal)
        }
      }
    }
  }

  if (confirmPutBack && p != null) {
    AlertDialog(
      onDismissRequest = { confirmPutBack = false },
      title = { Text("Put back ${p.title}?") },
      text = { Text("Every value in this step goes back to what the tablet's code says. Other steps keep your edits.") },
      confirmButton = {
        TextButton(onClick = { confirmPutBack = false; state.putBack(p) }) { Text("Put back", color = RiviaTeal) }
      },
      dismissButton = { TextButton(onClick = { confirmPutBack = false }) { Text("Cancel", color = RiviaSecondaryText) } },
    )
  }
}

@Composable
private fun SettingRow(
  s: AdjustableSetting,
  draft: AdjustableDraft,
  enabled: Boolean,
  onSwitch: (Boolean) -> Unit,
  onText: (String) -> Unit,
) {
  val codeLine =
    "code: " +
      (if (s.isSwitch) TabletProgressClient.show(s.switchCode)
      else if (s.numberCode == null) s.blankWord
      else TabletProgressClient.show(s.numberCode)) +
      (if (s.unit.isNotBlank() && !s.isSwitch && s.numberCode != null) " ${s.unit}" else "") + if (s.changed) "  · your edit in use" else ""

  if (s.isSwitch) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
      Column(modifier = Modifier.weight(1f)) {
        Text(s.words, style = MaterialTheme.typography.bodySmall, color = RiviaTextPrimary)
        Text(codeLine, fontFamily = MonoFontFamily, fontSize = 10.sp, color = RiviaTextTertiary)
      }
      Switch(
        checked = draft.switches[s.path] ?: (s.switchValue ?: false),
        onCheckedChange = onSwitch,
        enabled = enabled,
        modifier = Modifier.testTag("adjust_${s.path}"),
      )
    }
    return
  }

  val text = draft.texts[s.path] ?: if (s.numberValue == null) "" else TabletProgressClient.show(s.numberValue)
  if (s.usesSlider) {
    val lo = s.min ?: 0.0
    val hi = s.max ?: 1.0
    val st = s.step ?: 1.0
    val current = text.trim().toDoubleOrNull()?.coerceIn(lo, hi) ?: (s.numberValue ?: lo)
    val stops = ((hi - lo) / st).toInt() - 1
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(s.words, style = MaterialTheme.typography.bodySmall, color = RiviaTextPrimary, modifier = Modifier.weight(1f))
        Text(
          TabletProgressClient.show(current) + if (s.unit.isNotBlank()) " ${s.unit}" else "",
          fontFamily = MonoFontFamily,
          fontSize = 12.sp,
          color = RiviaTextPrimary,
        )
      }
      Slider(
        value = current.toFloat(),
        onValueChange = { onText(TabletProgressClient.show(TabletProgressClient.snap(s, it.toDouble()))) },
        valueRange = lo.toFloat()..hi.toFloat(),
        steps = if (stops in 1..60) stops else 0,
        enabled = enabled,
        colors =
          SliderDefaults.colors(
            thumbColor = RiviaTeal,
            activeTrackColor = RiviaTeal,
            inactiveTrackColor = RiviaBorder,
            activeTickColor = RiviaBorder,
            inactiveTickColor = RiviaBorder,
          ),
        modifier = Modifier.fillMaxWidth().testTag("adjust_${s.path}"),
      )
      Text(
        codeLine + "  · range ${TabletProgressClient.show(lo)} to ${TabletProgressClient.show(hi)}",
        fontFamily = MonoFontFamily,
        fontSize = 10.sp,
        color = RiviaTextTertiary,
      )
    }
    return
  }

  Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.weight(1f)) {
      Text(s.words, style = MaterialTheme.typography.bodySmall, color = RiviaTextPrimary)
      Text(
        codeLine + (if (s.max != null) "  · up to ${TabletProgressClient.show(s.max)}" else ""),
        fontFamily = MonoFontFamily,
        fontSize = 10.sp,
        color = RiviaTextTertiary,
      )
    }
    OutlinedTextField(
      value = text,
      onValueChange = onText,
      enabled = enabled,
      singleLine = true,
      placeholder = { if (s.blankMeansNone) Text(s.blankWord) },
      keyboardOptions =
        KeyboardOptions(keyboardType = if (s.isWhole) KeyboardType.Number else KeyboardType.Decimal),
      modifier = Modifier.width(120.dp).testTag("adjust_${s.path}"),
    )
  }
}
