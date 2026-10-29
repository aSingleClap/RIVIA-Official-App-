package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.RiviaErrorRed
import com.example.ui.theme.RiviaMint
import com.example.ui.theme.RiviaText
import com.example.ui.theme.RiviaWarningAmber

enum class OrbState {
  IDLE,
  LISTENING,
  THINKING,
  SPEAKING,
}

enum class StatusType {
  OK,
  WARNING,
  ERROR,
}

/**
 * Rivia's abstract orb avatar:
 * - Mint radial glow (#5DCAA5)
 * - White core (#E4EBE8)
 * - Thin ring
 * - Pulses while she speaks and breathes slowly while she listens.
 */
@Composable
fun RiviaOrb(
  state: OrbState,
  modifier: Modifier = Modifier,
  size: Dp = 36.dp,
  contentDesc: String = "Rivia Orb",
) {
  val transition = rememberInfiniteTransition(label = "orb_transition")

  // Speaking pulse: lively 550ms pulse; Listening breath: slow 2400ms breath; Thinking: 1100ms shimmer
  val durationMs =
    when (state) {
      OrbState.SPEAKING -> 520
      OrbState.LISTENING -> 2400
      OrbState.THINKING -> 1000
      OrbState.IDLE -> 3200
    }

  val animProgress by
    transition.animateFloat(
      initialValue = 0f,
      targetValue = 1f,
      animationSpec =
        infiniteRepeatable(
          animation =
            tween(
              durationMillis = durationMs,
              easing = if (state == OrbState.THINKING) LinearEasing else FastOutSlowInEasing,
            ),
          repeatMode = RepeatMode.Reverse,
        ),
      label = "orb_scale",
    )

  Canvas(
    modifier =
      modifier.size(size).semantics {
        contentDescription = contentDesc
      }
  ) {
    val center = Offset(this.size.width / 2f, this.size.height / 2f)
    val maxRadius = this.size.minDimension / 2f

    val glowScale =
      when (state) {
        OrbState.SPEAKING -> 0.72f + (0.28f * animProgress)
        OrbState.LISTENING -> 0.65f + (0.20f * animProgress)
        OrbState.THINKING -> 0.68f + (0.16f * animProgress)
        OrbState.IDLE -> 0.68f + (0.06f * animProgress)
      }

    val ringScale =
      when (state) {
        OrbState.SPEAKING -> 0.56f + (0.22f * animProgress)
        OrbState.LISTENING -> 0.52f + (0.14f * animProgress)
        OrbState.THINKING -> 0.50f + (0.18f * animProgress)
        OrbState.IDLE -> 0.54f
      }

    val coreScale =
      when (state) {
        OrbState.SPEAKING -> 0.24f + (0.10f * animProgress)
        OrbState.LISTENING -> 0.22f + (0.06f * animProgress)
        OrbState.THINKING -> 0.20f + (0.08f * animProgress)
        OrbState.IDLE -> 0.22f
      }

    val glowRadius = (maxRadius * glowScale).coerceAtLeast(1f)
    // 1. Mint radial glow
    drawCircle(
      brush =
        Brush.radialGradient(
          colors =
            listOf(
              RiviaMint.copy(alpha = if (state == OrbState.SPEAKING) 0.75f else 0.50f),
              RiviaMint.copy(alpha = 0.18f),
              Color.Transparent,
            ),
          center = center,
          radius = glowRadius,
        ),
      radius = glowRadius,
      center = center,
    )

    // Optional outer ripple ring when speaking
    if (state == OrbState.SPEAKING) {
      drawCircle(
        color = RiviaMint.copy(alpha = (1f - animProgress) * 0.55f),
        radius = maxRadius * (0.62f + 0.36f * animProgress),
        center = center,
        style = Stroke(width = 1.2.dp.toPx()),
      )
    }

    // 2. Thin mint ring
    drawCircle(
      color = RiviaMint.copy(alpha = if (state == OrbState.IDLE) 0.75f else 0.95f),
      radius = maxRadius * ringScale,
      center = center,
      style = Stroke(width = (1.5.dp.toPx()).coerceAtLeast(1.5f)),
    )

    // 3. White core
    drawCircle(
      color = RiviaText,
      radius = maxRadius * coreScale,
      center = center,
    )
  }
}

/**
 * Status shapes per Section 1:
 * - ok = mint dot
 * - warning = amber ring
 * - error = red diamond
 */
@Composable
fun StatusShape(
  status: StatusType,
  modifier: Modifier = Modifier,
  size: Dp = 10.dp,
) {
  val desc =
    when (status) {
      StatusType.OK -> "Status OK"
      StatusType.WARNING -> "Status Warning"
      StatusType.ERROR -> "Status Error"
    }
  Canvas(
    modifier =
      modifier.size(size).semantics {
        contentDescription = desc
      }
  ) {
    val w = this.size.width
    val h = this.size.height
    val center = Offset(w / 2f, h / 2f)
    when (status) {
      StatusType.OK -> {
        drawCircle(
          color = RiviaMint,
          radius = this.size.minDimension / 2f,
          center = center,
        )
      }
      StatusType.WARNING -> {
        val stroke = (this.size.minDimension * 0.22f).coerceAtLeast(2f)
        drawCircle(
          color = RiviaWarningAmber,
          radius = (this.size.minDimension - stroke) / 2f,
          center = center,
          style = Stroke(width = stroke),
        )
      }
      StatusType.ERROR -> {
        val path =
          Path().apply {
            moveTo(w / 2f, 0f)
            lineTo(w, h / 2f)
            lineTo(w / 2f, h)
            lineTo(0f, h / 2f)
            close()
          }
        drawPath(path = path, color = RiviaErrorRed)
      }
    }
  }
}
