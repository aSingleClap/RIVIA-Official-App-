package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.network.TrustSnapshot
import com.example.network.TrustUnlock
import com.example.ui.theme.RiviaErrorRed as RiviaDestructive
import com.example.ui.theme.RiviaMutedText as RiviaSecondaryText
import com.example.ui.theme.RiviaTextPrimary
import com.example.ui.theme.RiviaWarningAmber as RiviaAmber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Step 6.9: the read-outs inside Layer 6's tiles (TrustLayerCard.kt). Read-only text and the two kinds of switch;
// the switches only ask TrustLayerCard to show its "are you sure" box, they never post anything themselves.

internal fun trustTwo(x: Double?): String = if (x == null) "not yet" else String.format(Locale.US, "%.2f", x)

private val TRUST_TIME = SimpleDateFormat("MMM d, h:mm a", Locale.US)

@Composable
internal fun TrustHint(text: String, error: Boolean = false) {
  Text(
    text,
    style = MaterialTheme.typography.bodySmall,
    color = if (error) RiviaDestructive else RiviaSecondaryText,
  )
}

@Composable
private fun TrustLine(label: String, value: String) {
  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
    Text(label, style = MaterialTheme.typography.bodySmall, color = RiviaSecondaryText, modifier = Modifier.weight(1f))
    Text(value, style = MaterialTheme.typography.bodySmall, color = RiviaTextPrimary)
  }
}

private fun trustStateWords(u: TrustUnlock): String =
  when (u.state) {
    "ready" -> "She'd bring this up"
    "active" -> "On"
    "paused" -> "On, but paused (under a line)"
    "killed" -> "Off switch is on"
    else -> "Earning"
  }

@Composable
internal fun TrustStepReadout(
  shows: String,
  snap: TrustSnapshot,
  onUnlock: (TrustUnlock, Boolean) -> Unit,
  onKill: (Boolean) -> Unit,
) {
  when (shows) {
    "days" ->
      if (snap.days.isEmpty()) TrustHint("No judged days yet.")
      else
        snap.days.take(7).forEach { d ->
          TrustLine(d.day, "${d.hits} right, ${d.herMisses} hers wrong" + if (d.quiet) ", quiet day" else "")
        }
    "satisfaction" ->
      TrustLine("Satisfaction", "${trustTwo(snap.satisfaction)} (from ${trustTwo(snap.satisfactionEvidence)} marks)")
    "areas" ->
      if (snap.areas.isEmpty()) TrustHint("Every area is at full confidence.")
      else snap.areas.take(6).forEach { TrustLine(it.words, trustTwo(it.value)) }
    "flags" ->
      if (snap.flags.isEmpty()) TrustHint("Nothing flagged.")
      else snap.flags.forEach { TrustHint(it.say, error = false) }
    "trust" -> {
      TrustLine("Trust", trustTwo(snap.trust))
      TrustLine("Starts at", trustTwo(snap.trustStart))
      TrustLine("Days counted", snap.trustDays.toString())
    }
    "unlocks" ->
      snap.unlocks.forEach { u ->
        Row(
          modifier = Modifier.fillMaxWidth().testTag("trust_unlock_${u.id}"),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(u.words, style = MaterialTheme.typography.bodySmall, color = RiviaTextPrimary)
            Text(
              "${trustStateWords(u)}. Needs trust ${trustTwo(u.trustLine)}, satisfaction ${trustTwo(u.satisfactionLine)}, ${u.daysLine ?: "?"} days.",
              style = MaterialTheme.typography.bodySmall,
              color = if (u.state == "ready") RiviaAmber else RiviaSecondaryText,
            )
          }
          Switch(
            checked = u.switchedOn,
            enabled = !snap.killed,
            onCheckedChange = { onUnlock(u, it) },
            modifier = Modifier.testTag("trust_unlock_switch_${u.id}"),
          )
        }
      }
    "kill" -> {
      Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
          if (snap.killed) "Off switch is ON: every unlock is off" else "Off switch",
          style = MaterialTheme.typography.bodySmall,
          color = if (snap.killed) RiviaDestructive else RiviaTextPrimary,
          modifier = Modifier.weight(1f),
        )
        Switch(checked = snap.killed, onCheckedChange = onKill, modifier = Modifier.testTag("trust_kill_switch"))
      }
      Text("Never unlockable", style = MaterialTheme.typography.labelMedium, color = RiviaTextPrimary)
      snap.hardLimits.forEach { (_, why) -> TrustHint(why) }
      if (snap.actions.isNotEmpty()) {
        Text("Recent log", style = MaterialTheme.typography.labelMedium, color = RiviaTextPrimary)
        snap.actions.take(8).forEach { a ->
          TrustLine("${TRUST_TIME.format(Date(a.at * 1000))}: ${a.what}", a.reason.take(60))
        }
      }
    }
  }
}
