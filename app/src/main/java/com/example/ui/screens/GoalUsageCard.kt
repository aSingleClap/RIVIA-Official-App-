package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.network.Gauge
import com.example.network.TabletUsageClient
import com.example.network.UsageResult
import com.example.network.UsageSnapshot
import com.example.ui.theme.RiviaBorder
import com.example.ui.theme.RiviaErrorRed as RiviaDestructive
import com.example.ui.theme.RiviaMint as RiviaTeal
import com.example.ui.theme.RiviaMutedText as RiviaSecondaryText
import com.example.ui.theme.RiviaTextPrimary
import com.example.ui.theme.RiviaWarningAmber as RiviaAmber
import com.example.viewmodel.RiviaViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// Step 5.7: section 5.5's graphs and gauges, at the top of the 5.5 tile, above the 5.5 and
// Model limits cards where the numbers are adjusted. Read-only.
//   - Right now, per model: RPM, TPM and RPD against the number Jordan typed in, coloured ok / near (80%+) / at
//     limit / not set, and when RPD starts over in his own time.
//   - Per kind of goal: the most tokens a goal used each day (bars) against that kind's token limit (dashed line).
//   - Per model: requests each day (bars) against its RPD (dashed line).
// The tablet only sees the calls it makes; the note under the gauges says the phone's Live calls are left out.

internal class GoalUsageState(private val viewModel: RiviaViewModel, private val scope: CoroutineScope) {
  private val client = TabletUsageClient()
  var snapshot by mutableStateOf<UsageSnapshot?>(null)
  var isLoading by mutableStateOf(false)
  var error by mutableStateOf<String?>(null)

  fun loadIfNeeded() {
    if (snapshot == null && !isLoading) load()
  }

  fun load() {
    if (isLoading) return
    isLoading = true
    scope.launch {
      try {
        val url = viewModel.keyRepository.getTabletServerUrl()
        val token = viewModel.keyRepository.getTabletAppToken()
        when (val res = client.getUsage(url, token)) {
          is UsageResult.Success -> {
            snapshot = res.snapshot
            error = null
          }
          is UsageResult.Failure -> {
            error = res.message
            if (res.errorCode == "http_401" && token.isNotBlank()) {
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
}

private fun statusColor(status: String): Color =
  when (status) {
    "at" -> RiviaDestructive
    "near" -> RiviaAmber
    "ok" -> RiviaTeal
    else -> RiviaSecondaryText
  }

@Composable
internal fun GoalUsageCard(state: GoalUsageState) {
  LaunchedEffect(Unit) { state.loadIfNeeded() }
  Surface(
    color = MaterialTheme.colorScheme.background,
    shape = RoundedCornerShape(10.dp),
    border = BorderStroke(1.dp, RiviaBorder),
    modifier = Modifier.fillMaxWidth().testTag("goal_usage_card"),
  ) {
    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          "Use and limits",
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.SemiBold,
          color = RiviaTextPrimary,
          modifier = Modifier.weight(1f),
        )
        if (state.isLoading) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        else TextButton(onClick = { state.load() }, modifier = Modifier.testTag("goal_usage_refresh")) {
          Text("Refresh", color = RiviaTeal)
        }
      }
      Text(
        "For now these read close to empty: your phone calls Google directly, and the tablet doesn't record its own Gemini calls yet. 3.8 Flash reads lowest, because Live calls never reach the tablet.",
        style = MaterialTheme.typography.bodySmall,
        color = RiviaSecondaryText,
        modifier = Modifier.testTag("goal_usage_near_empty_note"),
      )
      val snap = state.snapshot
      state.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = RiviaDestructive) }
      if (snap == null) {
        if (state.error == null) Text("Loading…", style = MaterialTheme.typography.bodySmall, color = RiviaSecondaryText)
        return@Column
      }
      if (snap.mode == "measure") {
        Text(
          "Measuring: token and call limits are logged, not enforced yet. Bars over the line are goals that would have been stopped.",
          style = MaterialTheme.typography.bodySmall,
          color = RiviaSecondaryText,
        )
      }
      for (p in snap.problems) Text(p, style = MaterialTheme.typography.bodySmall, color = RiviaAmber)

      // Right now: each model against its limits.
      Text("Models right now", style = MaterialTheme.typography.labelMedium, color = RiviaTextPrimary)
      for ((model, g) in snap.gauges) {
        Column(modifier = Modifier.testTag("gauge_$model"), verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(
            TabletUsageClient.modelName(model),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = statusColor(g.worst),
          )
          GaugeRow("RPM", g.rpm)
          GaugeRow("TPM", g.tpm)
          GaugeRow("RPD", g.rpd)
        }
      }
      val reset = TabletUsageClient.resetLine(snap.rpdResetsAt)
      if (reset.isNotEmpty()) Text(reset, style = MaterialTheme.typography.bodySmall, color = RiviaSecondaryText)
      if (snap.seenNote.isNotBlank()) {
        Text(snap.seenNote, style = MaterialTheme.typography.bodySmall, color = RiviaSecondaryText)
      }

      // Tokens per goal, per day, per kind.
      Text(
        "Most tokens a goal used each day (dashed line: that kind's limit)",
        style = MaterialTheme.typography.labelMedium,
        color = RiviaTextPrimary,
      )
      for ((kind, days) in snap.kinds) {
        val line = snap.goalLimits[kind]?.tokens
        val values = days.map { it.tokensMax }
        val goals = days.sumOf { it.goals }
        val over = days.sumOf { it.wouldStop }
        Text(
          TabletUsageClient.kindName(kind) +
            " · limit " + (line?.let { TabletUsageClient.count(it) } ?: "none") +
            " · $goals goal${if (goals == 1) "" else "s"}" +
            if (over > 0) " · $over would have stopped" else "",
          style = MaterialTheme.typography.bodySmall,
          color = if (over > 0) RiviaAmber else RiviaSecondaryText,
        )
        BarGraph(
          values = values,
          line = line,
          days = snap.days,
          tag = "graph_tokens_$kind",
          description =
            "${TabletUsageClient.kindName(kind)}: most tokens per goal by day, ${values.joinToString()}, limit ${line ?: "none"}",
        )
      }

      // Requests per model per day against RPD.
      Text(
        "Requests each day (Google's day; dashed line: your RPD)",
        style = MaterialTheme.typography.labelMedium,
        color = RiviaTextPrimary,
      )
      for ((model, days) in snap.models) {
        val line = snap.gauges[model]?.rpd?.limit
        val values = days.map { it.requests }
        val peak = days.maxOfOrNull { it.peakRpm } ?: 0L
        Text(
          TabletUsageClient.modelName(model) +
            " · RPD " + (line?.let { TabletUsageClient.count(it) } ?: "not set") +
            " · busiest minute $peak request${if (peak == 1L) "" else "s"}",
          style = MaterialTheme.typography.bodySmall,
          color = RiviaSecondaryText,
        )
        BarGraph(
          values = values,
          line = line,
          days = snap.modelDays,
          tag = "graph_requests_$model",
          description = "${TabletUsageClient.modelName(model)}: requests by day, ${values.joinToString()}, RPD ${line ?: "not set"}",
        )
      }
    }
  }
}

@Composable
private fun GaugeRow(label: String, g: Gauge) {
  val color = statusColor(g.status)
  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(label, style = MaterialTheme.typography.bodySmall, color = RiviaSecondaryText, modifier = Modifier.size(width = 36.dp, height = 18.dp))
    Box(modifier = Modifier.weight(1f).height(8.dp)) {
      Canvas(
        modifier =
          Modifier.fillMaxWidth().height(8.dp).semantics {
            contentDescription = "$label ${TabletUsageClient.usedWords(g)}, ${TabletUsageClient.statusWords(g)}"
          }
      ) {
        drawRoundRect(color = RiviaBorder, size = size, cornerRadius = CornerRadius(4f, 4f))
        val share = (g.share ?: 0.0).coerceIn(0.0, 1.0).toFloat()
        if (g.limit != null && share > 0f) {
          drawRoundRect(color = color, size = Size(size.width * share, size.height), cornerRadius = CornerRadius(4f, 4f))
        }
        // The 80% mark, where "near" starts.
        val x = size.width * 0.8f
        drawLine(RiviaSecondaryText, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.5f)
      }
    }
    Text(
      TabletUsageClient.usedWords(g) + " · " + TabletUsageClient.statusWords(g),
      style = MaterialTheme.typography.bodySmall,
      color = color,
    )
  }
}

/** Bars oldest to newest, a dashed line for the limit (when there is one), first and last day under it. */
@Composable
private fun BarGraph(values: List<Long>, line: Long?, days: List<String>, tag: String, description: String) {
  val top = TabletUsageClient.graphTop(values, line)
  Column(modifier = Modifier.fillMaxWidth().testTag(tag)) {
    Canvas(modifier = Modifier.fillMaxWidth().height(64.dp).semantics { contentDescription = description }) {
      val n = values.size.coerceAtLeast(1)
      val slot = size.width / n
      val gap = slot * 0.2f
      values.forEachIndexed { i, v ->
        val h = size.height * (v.toFloat() / top.toFloat())
        val over = line != null && v >= line
        drawRoundRect(
          color = if (over) RiviaAmber else RiviaTeal,
          topLeft = Offset(i * slot + gap / 2, size.height - h),
          size = Size(slot - gap, h),
          cornerRadius = CornerRadius(3f, 3f),
        )
      }
      if (line != null) {
        val y = size.height * (1f - line.toFloat() / top.toFloat())
        drawLine(
          RiviaDestructive,
          Offset(0f, y),
          Offset(size.width, y),
          strokeWidth = 2f,
          pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
        )
      }
      drawLine(RiviaBorder, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1f)
    }
    if (days.isNotEmpty()) {
      Row(modifier = Modifier.fillMaxWidth()) {
        Text(TabletUsageClient.shortDay(days.first()), style = MaterialTheme.typography.labelSmall, color = RiviaSecondaryText, modifier = Modifier.weight(1f))
        Text("Today", style = MaterialTheme.typography.labelSmall, color = RiviaSecondaryText)
      }
    }
  }
}
