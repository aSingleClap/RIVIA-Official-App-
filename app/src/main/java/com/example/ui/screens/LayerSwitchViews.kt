package com.example.ui.screens

import android.app.Application
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.network.LayerSwitches
import com.example.network.TogglePiece
import com.example.network.TogglesResult
import com.example.ui.theme.RiviaBorder
import com.example.ui.theme.RiviaMutedText as RiviaSecondaryText
import com.example.ui.theme.RiviaTextPrimary
import com.example.ui.theme.RiviaWarningAmber as RiviaAmber
import com.example.viewmodel.RiviaViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// Layer switches (Nov 24). Jordan: "Each layer and layer piece stays where it is in the app but each piece gets a small
// label next to the toggle that says tablet or app", and "In the app layers/pieces that are dependent on another to
// function will be stated and clearly shown in the app".
// So each switch sits on its own layer or step row, with a "Tablet" or "App" label beside it. Every piece shows what it
// needs and what needs it, a piece whose parent is off is greyed with the reason, and turning off a parent first lists
// everything else that goes off. What needs what comes only from the tablet (GET /app/toggles).
// Tablet switches are saved on the tablet. App switches (1.5 dial, 2.4 controls, 6.3 thumbs) are saved on this phone.
// Nothing here is a Gemini tool.

/** The one set of switch actions while the Layers screen is open. LayerSwitchesHost sets it. */
internal object LayerSwitchHost {
  var actions by mutableStateOf<LayerSwitchActions?>(null)
}

internal class LayerSwitchActions(private val viewModel: RiviaViewModel, private val scope: CoroutineScope) {
  var isLoading by mutableStateOf(false)
  var isBusy by mutableStateOf(false)
  /** A switch waiting for your OK, because turning it off turns other pieces off too. */
  var confirm by mutableStateOf<TogglePiece?>(null)

  fun load() {
    LayerSwitches.loadPhone(viewModel.getApplication<Application>())
    if (isLoading) return
    isLoading = true
    scope.launch {
      try {
        val res = LayerSwitches.refresh(url(), token())
        if (res is TogglesResult.Failure) signOutIf401(res)
      } finally {
        isLoading = false
      }
    }
  }

  fun flip(p: TogglePiece, on: Boolean) {
    if (p.where == "phone") {
      LayerSwitches.setPhone(viewModel.getApplication<Application>(), p.id, on)
      return
    }
    if (!on && p.wouldAlsoTurnOff.isNotEmpty()) {
      confirm = p
      return
    }
    save(p.id, on)
  }

  fun confirmOff() {
    val p = confirm ?: return
    confirm = null
    save(p.id, false)
  }

  private fun save(id: String, on: Boolean) {
    if (isBusy) return
    isBusy = true
    scope.launch {
      try {
        val res = LayerSwitches.setTablet(url(), token(), id, on)
        if (res is TogglesResult.Failure && !signOutIf401(res)) {
          viewModel.showError("Switch not saved: ${res.message}")
        }
      } finally {
        isBusy = false
      }
    }
  }

  private suspend fun signOutIf401(res: TogglesResult.Failure): Boolean {
    if (res.errorCode == "http_401" && token().isNotBlank()) {
      viewModel.signOutGoogleAccount()
      viewModel.showError("Signed out, sign in again")
      return true
    }
    return false
  }

  private suspend fun url() = viewModel.keyRepository.getTabletServerUrl()

  private suspend fun token() = viewModel.keyRepository.getTabletAppToken()
}

/** Put once on the Layers screen: reads the switches when it opens, and asks before a switch turns others off. */
@Composable
internal fun LayerSwitchesHost(viewModel: RiviaViewModel) {
  val scope = rememberCoroutineScope()
  val actions = remember { LayerSwitchActions(viewModel, scope) }
  DisposableEffect(actions) {
    LayerSwitchHost.actions = actions
    actions.load()
    onDispose { if (LayerSwitchHost.actions === actions) LayerSwitchHost.actions = null }
  }

  actions.confirm?.let { p ->
    AlertDialog(
      onDismissRequest = { actions.confirm = null },
      title = { Text("Turn off ${p.title}?") },
      text = {
        Text("This also turns off:\n" + p.wouldAlsoTurnOff.joinToString("\n") { "• " + LayerSwitches.title(it) })
      },
      confirmButton = {
        TextButton(onClick = { actions.confirmOff() }, modifier = Modifier.testTag("switch_confirm_off")) {
          Text("Turn off")
        }
      },
      dismissButton = {
        TextButton(onClick = { actions.confirm = null }, modifier = Modifier.testTag("switch_confirm_cancel")) {
          Text("Cancel")
        }
      },
    )
  }
}

/** The small "Tablet" or "App" label next to a switch: where that switch is saved and does its work. */
@Composable
private fun WhereLabel(where: String) {
  Surface(
    color = Color.Transparent,
    shape = RoundedCornerShape(6.dp),
    border = BorderStroke(1.dp, RiviaBorder),
  ) {
    Text(
      if (where == "phone") "App" else "Tablet",
      style = MaterialTheme.typography.labelSmall,
      color = RiviaSecondaryText,
      modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
    )
  }
}

/** One piece: its switch with the Tablet/App label, or its state text, then what it needs and why it's off. */
@Composable
private fun PieceSwitchRow(p: TogglePiece, label: String?) {
  val actions = LayerSwitchHost.actions
  val isPhone = p.where == "phone"
  val parent = LayerSwitches.offParent(p.id)
  val forcedOff = parent != null || p.state == "off_by_parent" || p.state == "off_by_env"
  val saved = if (isPhone) LayerSwitches.phoneValue(p.id) else (p.value ?: (p.state == "on" || p.state == "waiting"))
  val running = saved && !forcedOff
  val enabled = p.switchable && !forcedOff && actions != null && !actions.isBusy
  Column(
    modifier = Modifier.fillMaxWidth().testTag("switch_row_${p.id}"),
    verticalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      val lead =
        when {
          !p.switchable -> label ?: ""
          label != null -> label + (if (running) ": on" else ": off")
          else -> if (running) "On" else "Off"
        }
      Text(
        lead,
        style = MaterialTheme.typography.bodySmall,
        color = if (forcedOff) RiviaSecondaryText else RiviaTextPrimary,
        modifier = Modifier.weight(1f),
      )
      when {
        p.switchable -> {
          WhereLabel(p.where)
          Switch(
            checked = running,
            onCheckedChange = { on -> actions?.flip(p, on) },
            enabled = enabled,
            modifier = Modifier.testTag("switch_${p.id}"),
          )
        }
        p.state == "always_on" ->
          Text("Always on (safety)", style = MaterialTheme.typography.bodySmall, color = RiviaSecondaryText)
        else -> Text("Nothing to switch", style = MaterialTheme.typography.bodySmall, color = RiviaSecondaryText)
      }
    }
    val needsHard = p.needs.filter { it.hard }.map { LayerSwitches.title(it.id) }
    val needsSoft = p.needs.filter { !it.hard }.map { LayerSwitches.title(it.id) }
    val neededBy = p.neededBy.filter { it.hard }.map { LayerSwitches.title(it.id) }
    if (needsHard.isNotEmpty()) {
      Text("Needs " + needsHard.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = RiviaSecondaryText)
    }
    if (needsSoft.isNotEmpty()) {
      Text(
        "Uses " + needsSoft.joinToString(", ") + " (keeps running without it, with nothing new)",
        style = MaterialTheme.typography.bodySmall,
        color = RiviaSecondaryText,
      )
    }
    if (neededBy.isNotEmpty()) {
      Text(
        "Needed by " + neededBy.joinToString(", "),
        style = MaterialTheme.typography.bodySmall,
        color = RiviaSecondaryText,
      )
    }
    val why =
      when {
        parent != null -> "Off because ${parent.title} is off"
        p.why.isNotBlank() -> p.why
        else -> ""
      }
    if (why.isNotBlank()) {
      Text(
        why,
        style = MaterialTheme.typography.bodySmall,
        color = if (forcedOff || p.state == "waiting") RiviaAmber else RiviaSecondaryText,
      )
    }
  }
}

/** The switches that sit on one step row (by the row's test tag). Shows nothing when the row has none. */
@Composable
internal fun PieceSwitches(testTag: String) {
  val pieces = LayerSwitches.piecesForRow(testTag)
  if (pieces.isEmpty()) return
  Column(
    modifier = Modifier.fillMaxWidth().padding(top = 4.dp).testTag("${testTag}_switches"),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    for (p in pieces) PieceSwitchRow(p, label = if (pieces.size > 1) p.title else null)
  }
}

/** A layer's whole-layer switch, under the layer's name. Layer 1 also says when the tablet's switches can't be read. */
@Composable
internal fun LayerSwitchLine(layer: Int) {
  val pieces = LayerSwitches.layerPieces(layer)
  val note: Pair<String, Boolean>? =
    when {
      layer != 1 -> null
      LayerSwitches.notInstalled ->
        "The tablet's layer switches aren't installed yet, so only the App switches work for now." to false
      LayerSwitches.tabletMessage.isNotBlank() -> "Layer switches: ${LayerSwitches.tabletMessage}" to true
      else -> null
    }
  if (pieces.isEmpty() && note == null) return
  Column(
    modifier = Modifier.fillMaxWidth().padding(top = 4.dp).testTag("layer_${layer}_switches"),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    note?.let { (text, error) -> TrustHint(text, error = error) }
    for (p in pieces) PieceSwitchRow(p, label = "Whole layer")
  }
}
