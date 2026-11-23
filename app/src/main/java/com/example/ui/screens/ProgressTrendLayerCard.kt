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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.RiviaBorder
import com.example.ui.theme.RiviaMint as RiviaTeal
import com.example.ui.theme.RiviaMutedText as RiviaSecondaryText
import com.example.ui.theme.RiviaSurface2 as RiviaPanelSurface
import com.example.ui.theme.RiviaTextPrimary
import com.example.viewmodel.RiviaViewModel

// Layer 5, Progress Trend, on the Layers screen. Same look as Layers 1 to 4: a header and one tile per sub-step.
// A tile whose step has adjustable values gets an "Adjust values" button that opens a small card for that step only
// (AdjustableValuesCard). The values live on the tablet and are read from GET /app/progress with the app token.
// Nothing here is a Gemini tool. Fixed limits are shown only as read-only notes, never as controls.

private data class ProgressSubStep(
  val testTag: String,
  val title: String,
  val description: String,
  /** The tablet's piece ids whose values sit in this tile, in order. Empty: nothing to adjust. */
  val pieces: List<String> = emptyList(),
  /** Shows section 5.5's use graphs and model gauges above its cards. */
  val showsUsage: Boolean = false,
)

private const val LAYER = 5

private val PROGRESS_SUB_STEPS =
  listOf(
    ProgressSubStep(
      "progress_step_5_1",
      "5.1 Plan scores",
      "Scores each day: arrival time against her plan comes first, then pins, how much of the plan held, and rule breaks. Which of these count is set in 5.2.",
    ),
    ProgressSubStep(
      "progress_step_5_2",
      "5.2 Daily gap",
      "Turns a day into one number from 0 (plan matched) to 1 (way off). Any broken rule puts the day at 0.5 or worse.",
      pieces = listOf("5.2"),
    ),
    ProgressSubStep(
      "progress_step_5_3",
      "5.3 Trend",
      "Looks at the last two weeks: is the gap shrinking, growing or flat?",
    ),
    ProgressSubStep(
      "progress_step_5_4",
      "5.4 Mood pull",
      "Turns the trend into a small mood pull: hopeful, worried, content or focused. Server outages don't count against her plan.",
      pieces = listOf("5.4"),
    ),
    ProgressSubStep(
      "progress_step_5_5",
      "5.5 Goal limits",
      "Every goal gets a time limit, usage limits, a try limit and a finish line. She only reports, never fixes. Graphs show real use against your limits, and each model against Google's limits.",
      pieces = listOf("5.5", "models", "goals"),
      showsUsage = true,
    ),
    ProgressSubStep(
      "progress_step_5_6",
      "5.6 Shadow mode",
      "Everything is worked out and logged without changing her replies yet.",
    ),
    ProgressSubStep(
      "progress_step_5_7",
      "5.7 Adjust from the app",
      "The values in 5.2, 5.4 and 5.5 can be changed here. The tablet's code stays the starting point, every save is checked on the tablet, and Put back returns a step to the code's values.",
    ),
  )

@Composable
internal fun ProgressTrendLayerCard(
  expanded: Boolean,
  onToggleExpanded: () -> Unit,
  viewModel: RiviaViewModel,
) {
  val scope = rememberCoroutineScope()
  val adjust = remember { AdjustableValuesState(viewModel, scope) }
  val usage = remember { GoalUsageState(viewModel, scope) }
  val open = remember { mutableStateMapOf<String, Boolean>() }

  Surface(
    color = RiviaPanelSurface,
    shape = RoundedCornerShape(14.dp),
    modifier = Modifier.fillMaxWidth().testTag("layer_card_progress_trend"),
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Row(
        modifier =
          Modifier.fillMaxWidth().clickable(onClick = onToggleExpanded).testTag("layer_header_progress_trend"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Layer 5: Progress Trend",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = RiviaTextPrimary,
          )
          Text(
            text =
              "How she feels about how well her plans match your real day: is the gap shrinking or growing? Shadow mode: worked out and logged, but it doesn't change her replies yet.",
            style = MaterialTheme.typography.bodySmall,
            color = RiviaSecondaryText,
            modifier = Modifier.testTag("progress_trend_description"),
          )
        }
        IconButton(onClick = onToggleExpanded, modifier = Modifier.testTag("toggle_layer_progress_trend")) {
          Icon(
            imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription =
              if (expanded) "Collapse Layer 5: Progress Trend" else "Expand Layer 5: Progress Trend",
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
          modifier = Modifier.fillMaxWidth().testTag("progress_trend_sublayers_container"),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          for (step in PROGRESS_SUB_STEPS) {
            Surface(
              color = MaterialTheme.colorScheme.background,
              shape = RoundedCornerShape(10.dp),
              border = BorderStroke(1.dp, RiviaBorder),
              modifier = Modifier.fillMaxWidth().testTag(step.testTag),
            ) {
              Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                  text = step.title,
                  style = MaterialTheme.typography.labelLarge,
                  color = RiviaTextPrimary,
                  fontWeight = FontWeight.SemiBold,
                )
                Text(text = step.description, style = MaterialTheme.typography.bodySmall, color = RiviaSecondaryText)
                if (step.pieces.isNotEmpty()) {
                  val isOpen = open[step.testTag] == true
                  TextButton(
                    onClick = { open[step.testTag] = !isOpen },
                    modifier = Modifier.testTag("${step.testTag}_adjust_toggle"),
                  ) {
                    Text(if (isOpen) "Hide values" else "Adjust values", color = RiviaTeal)
                  }
                  if (isOpen) {
                    if (step.showsUsage) GoalUsageCard(state = usage)
                    for (piece in step.pieces) {
                      AdjustableValuesCard(state = adjust, layer = LAYER, piece = piece)
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
}
