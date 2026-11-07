package com.example.ui.components

import android.animation.ValueAnimator
import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.theme.ChivoMonoFontFamily
import com.example.ui.theme.RiviaBorder
import com.example.ui.theme.RiviaErrorRed
import com.example.ui.theme.RiviaMint
import com.example.ui.theme.RiviaSurface1
import com.example.ui.theme.RiviaSurface2
import com.example.ui.theme.RiviaText
import com.example.ui.theme.RiviaTextPrimary
import com.example.ui.theme.RiviaTextSecondary
import com.example.ui.theme.RiviaWarningAmber
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

private const val SPHERE_PREFS_NAME = "rivia_particle_sphere_prefs"
private const val PREF_KEY_PARTICLE_COUNT = "particle_count"
private const val PREF_KEY_ROTATION_PERIOD_SEC = "rotation_period_sec"
private const val DEFAULT_PARTICLE_COUNT = 650
private const val DEFAULT_ROTATION_PERIOD_SEC = 40
private const val PARTICLE_RANDOM_SEED = 0x52495649L

private class PrecomputedSphereParticles(
  val count: Int,
  val ux: FloatArray,
  val uy: FloatArray,
  val uz: FloatArray,
  val radiusDp: FloatArray,
  val frontBaseOpacity: FloatArray,
  val twinkleOmega: FloatArray,
  val twinklePhase: FloatArray,
  val rotX: FloatArray,
  val rotY: FloatArray,
  val rotZ: FloatArray,
)

private fun buildFibonacciSphereParticles(count: Int): PrecomputedSphereParticles {
  val ux = FloatArray(count)
  val uy = FloatArray(count)
  val uz = FloatArray(count)
  val radiusDp = FloatArray(count)
  val frontBaseOpacity = FloatArray(count)
  val twinkleOmega = FloatArray(count)
  val twinklePhase = FloatArray(count)
  val rotX = FloatArray(count)
  val rotY = FloatArray(count)
  val rotZ = FloatArray(count)

  val rng = Random(PARTICLE_RANDOM_SEED)
  val goldenAngle = Math.PI * (3.0 - sqrt(5.0))
  val twoPi = (2.0 * Math.PI).toFloat()

  for (i in 0 until count) {
    val y = (1.0 - 2.0 * (i + 0.5) / count.toDouble()).coerceIn(-1.0, 1.0)
    val latitude = asin(y)
    val longitude = i.toDouble() * goldenAngle
    val cosLat = cos(latitude)

    ux[i] = (cosLat * cos(longitude)).toFloat()
    uy[i] = sin(latitude).toFloat()
    uz[i] = (cosLat * sin(longitude)).toFloat()

    // White (#FFFFFF), diameter 0.9-2.4 dp -> radius 0.45-1.2 dp
    val diameterDp = 0.9f + rng.nextFloat() * (2.4f - 0.9f)
    radiusDp[i] = diameterDp * 0.5f

    // Front-facing base opacity 0.55-1.0
    frontBaseOpacity[i] = 0.55f + rng.nextFloat() * (1.0f - 0.55f)

    // Twinkle period 1.6-4.2 s, random phase
    val periodSec = 1.6f + rng.nextFloat() * (4.2f - 1.6f)
    twinkleOmega[i] = twoPi / periodSec
    twinklePhase[i] = rng.nextFloat() * twoPi
  }

  return PrecomputedSphereParticles(
    count = count,
    ux = ux,
    uy = uy,
    uz = uz,
    radiusDp = radiusDp,
    frontBaseOpacity = frontBaseOpacity,
    twinkleOmega = twinkleOmega,
    twinklePhase = twinklePhase,
    rotX = rotX,
    rotY = rotY,
    rotZ = rotZ,
  )
}

private fun isSystemRemoveAnimationsEnabled(context: Context): Boolean {
  return try {
    val scale =
      Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
      )
    scale == 0f || !ValueAnimator.areAnimatorsEnabled()
  } catch (_: Throwable) {
    false
  }
}

/**
 * Home-screen rotating particle sphere (Compose Canvas, no images/video/GIF):
 * - 650 particles (default) on a Fibonacci / golden-angle sphere, radius 115 dp in a 300 dp box
 * - Spins around vertical axis (40 s default, linear), tilted -16 deg on horizontal axis
 * - Perspective projection with camera distance 700 dp
 * - White (#FFFFFF) particles, diameter 0.9-2.4 dp, front glow 2.2x size, back opacity 0.22
 * - Twinkle down to 40% of base opacity and back over 1.6-4.2 s
 * - Core pale-blue radial glow rgba(220,235,255,0.9), 160 dp across, breathing 0.16-0.24 over 5 s
 * - Whole sphere breathing scale 1.0 -> 1.015 -> 1.0 over 6 s ease-in-out
 * - Dev-only tuning sliders (particle count 200-1000 step 50, rotation period 10-90 s step 5, Reset to defaults)
 */
@Composable
fun RiviaHomeParticleSphere(
  modifier: Modifier = Modifier,
  showDevTuning: Boolean = false,
  contentDesc: String = "Rivia Orb",
) {
  val context = LocalContext.current
  val prefs = remember(context) { context.getSharedPreferences(SPHERE_PREFS_NAME, Context.MODE_PRIVATE) }

  var particleCount by remember {
    mutableIntStateOf(
      prefs.getInt(PREF_KEY_PARTICLE_COUNT, DEFAULT_PARTICLE_COUNT).coerceIn(200, 1000)
    )
  }
  var rotationPeriodSec by remember {
    mutableIntStateOf(
      prefs.getInt(PREF_KEY_ROTATION_PERIOD_SEC, DEFAULT_ROTATION_PERIOD_SEC).coerceIn(10, 90)
    )
  }

  val particles = remember(particleCount) { buildFibonacciSphereParticles(particleCount) }

  val lifecycleOwner = LocalLifecycleOwner.current
  var isAppResumed by remember {
    mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
  }
  var removeAnimations by remember { mutableStateOf(isSystemRemoveAnimationsEnabled(context)) }

  DisposableEffect(lifecycleOwner, context) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_RESUME -> {
          isAppResumed = true
          removeAnimations = isSystemRemoveAnimationsEnabled(context)
        }
        Lifecycle.Event.ON_PAUSE,
        Lifecycle.Event.ON_STOP -> {
          isAppResumed = false
        }
        else -> {}
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  var elapsedSec by remember { mutableFloatStateOf(0f) }

  LaunchedEffect(isAppResumed, removeAnimations) {
    if (!isAppResumed || removeAnimations) return@LaunchedEffect
    var lastFrameNanos = -1L
    while (true) {
      withFrameNanos { frameNanos ->
        if (lastFrameNanos >= 0L) {
          val deltaSec = ((frameNanos - lastFrameNanos).coerceAtLeast(0L)) / 1_000_000_000f
          elapsedSec += deltaSec.coerceAtMost(0.1f)
        }
        lastFrameNanos = frameNanos
      }
    }
  }

  val tiltRad = remember { Math.toRadians(-16.0).toFloat() }
  val cosTilt = remember { cos(tiltRad) }
  val sinTilt = remember { sin(tiltRad) }
  val coreGlowBaseColor = remember { Color(220, 235, 255) }

  Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Box(
      modifier =
        Modifier.size(300.dp)
          .testTag("home_particle_sphere")
          .semantics { contentDescription = contentDesc },
      contentAlignment = Alignment.Center,
    ) {
      Canvas(modifier = Modifier.fillMaxSize()) {
        val t = if (removeAnimations) 0f else elapsedSec
        val twoPi = (2.0 * Math.PI).toFloat()

        // Whole sphere breathing: 1.0 -> 1.015 -> 1.0 over 6 s, ease-in-out
        val breathEase =
          if (removeAnimations) {
            0f
          } else {
            0.5f - 0.5f * cos(twoPi * (t / 6.0f))
          }
        val sphereScale = 1.0f + 0.015f * breathEase

        // Core glow opacity breathing: 0.16 -> 0.24 over 5 s, ease-in-out
        val coreGlowBreath =
          if (removeAnimations) {
            0.20f
          } else {
            0.16f + 0.08f * (0.5f - 0.5f * cos(twoPi * (t / 5.0f)))
          }

        // Linear spin around vertical axis, 1 full turn every rotationPeriodSec
        val spinAngle =
          if (removeAnimations) {
            0f
          } else {
            twoPi * ((t / rotationPeriodSec.toFloat()) % 1.0f)
          }
        val cosSpin = cos(spinAngle)
        val sinSpin = sin(spinAngle)

        val centerX = this.size.width * 0.5f
        val centerY = this.size.height * 0.5f
        val centerOffset = Offset(centerX, centerY)

        val sphereRadiusPx = 115.dp.toPx() * sphereScale
        val cameraDistancePx = 700.dp.toPx()
        val pxPerDp = 1.dp.toPx()

        val n = particles.count
        val ux = particles.ux
        val uy = particles.uy
        val uz = particles.uz
        val radiusDp = particles.radiusDp
        val frontBaseOpacity = particles.frontBaseOpacity
        val twinkleOmega = particles.twinkleOmega
        val twinklePhase = particles.twinklePhase
        val rotX = particles.rotX
        val rotY = particles.rotY
        val rotZ = particles.rotZ

        // Rotate all precomputed unit vectors once for this frame
        for (i in 0 until n) {
          val x1 = ux[i] * cosSpin + uz[i] * sinSpin
          val y1 = uy[i]
          val z1 = -ux[i] * sinSpin + uz[i] * cosSpin

          rotX[i] = x1
          rotY[i] = y1 * cosTilt - z1 * sinTilt
          rotZ[i] = y1 * sinTilt + z1 * cosTilt
        }

        // Pass 1: Back-facing particles (no glow, base opacity 0.22)
        for (i in 0 until n) {
          val rz = rotZ[i]
          if (rz < 0f) {
            val wx = rotX[i] * sphereRadiusPx
            val wy = rotY[i] * sphereRadiusPx
            val wz = rz * sphereRadiusPx
            val persp = cameraDistancePx / (cameraDistancePx - wz)

            val sx = centerX + wx * persp
            val sy = centerY - wy * persp
            val rPx = radiusDp[i] * pxPerDp * sphereScale * persp

            val twinkleFactor =
              if (removeAnimations) {
                1.0f
              } else {
                // Eases down to 40% of base and back (range 0.40..1.00)
                0.70f + 0.30f * sin(t * twinkleOmega[i] + twinklePhase[i])
              }
            val alpha = (0.22f * twinkleFactor).coerceIn(0f, 1f)

            drawCircle(
              color = Color.White.copy(alpha = alpha),
              radius = rPx,
              center = Offset(sx, sy),
            )
          }
        }

        // Core Glow: 160 dp across (80 dp radius), pale-blue rgba(220,235,255,0.9) fading to transparent
        val coreGlowRadiusPx = (80.dp.toPx() * sphereScale).coerceAtLeast(1f)
        val peakCoreAlpha = (0.9f * coreGlowBreath).coerceIn(0f, 1f)
        drawCircle(
          brush =
            Brush.radialGradient(
              0.0f to coreGlowBaseColor.copy(alpha = peakCoreAlpha),
              0.32f to coreGlowBaseColor.copy(alpha = peakCoreAlpha * 0.65f),
              0.62f to coreGlowBaseColor.copy(alpha = peakCoreAlpha * 0.26f),
              0.84f to coreGlowBaseColor.copy(alpha = peakCoreAlpha * 0.07f),
              1.0f to Color.Transparent,
              center = centerOffset,
              radius = coreGlowRadiusPx,
            ),
          radius = coreGlowRadiusPx,
          center = centerOffset,
        )

        // Pass 2: Front-facing particles (base opacity 0.55-1.0, soft glow of radius 2.2x size)
        for (i in 0 until n) {
          val rz = rotZ[i]
          if (rz >= 0f) {
            val wx = rotX[i] * sphereRadiusPx
            val wy = rotY[i] * sphereRadiusPx
            val wz = rz * sphereRadiusPx
            val persp = cameraDistancePx / (cameraDistancePx - wz)

            val sx = centerX + wx * persp
            val sy = centerY - wy * persp
            val pos = Offset(sx, sy)
            val rPx = radiusDp[i] * pxPerDp * sphereScale * persp

            val twinkleFactor =
              if (removeAnimations) {
                1.0f
              } else {
                // Eases down to 40% of base and back (range 0.40..1.00)
                0.70f + 0.30f * sin(t * twinkleOmega[i] + twinklePhase[i])
              }
            val particleAlpha = (frontBaseOpacity[i] * twinkleFactor).coerceIn(0f, 1f)

            // Second, larger, low-alpha circle for the 2.2x radius soft glow (white, 85% alpha scaled for halo)
            val glowAlpha = (particleAlpha * 0.85f * 0.22f).coerceIn(0f, 1f)
            drawCircle(
              color = Color.White.copy(alpha = glowAlpha),
              radius = rPx * 2.2f,
              center = pos,
            )

            // Crisp white particle core
            drawCircle(
              color = Color.White.copy(alpha = particleAlpha),
              radius = rPx,
              center = pos,
            )
          }
        }
      }
    }

    if (showDevTuning) {
      Surface(
        color = RiviaSurface1,
        border = BorderStroke(1.dp, RiviaBorder),
        shape = RoundedCornerShape(14.dp),
        modifier =
          Modifier.widthIn(max = 320.dp)
            .fillMaxWidth()
            .testTag("sphere_dev_tuning_card"),
      ) {
        Column(
          modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = "Particles",
              style = MaterialTheme.typography.labelMedium,
              color = RiviaTextSecondary,
            )
            Text(
              text = "$particleCount",
              style = MaterialTheme.typography.labelMedium,
              fontFamily = ChivoMonoFontFamily,
              color = RiviaTextPrimary,
              fontWeight = FontWeight.SemiBold,
            )
          }
          Slider(
            value = particleCount.toFloat(),
            onValueChange = { raw ->
              val snapped = ((raw / 50f).roundToInt() * 50).coerceIn(200, 1000)
              if (snapped != particleCount) {
                particleCount = snapped
                prefs.edit().putInt(PREF_KEY_PARTICLE_COUNT, snapped).apply()
              }
            },
            valueRange = 200f..1000f,
            steps = 15,
            colors =
              SliderDefaults.colors(
                thumbColor = RiviaMint,
                activeTrackColor = RiviaMint,
                inactiveTrackColor = RiviaSurface2,
              ),
            modifier =
              Modifier.fillMaxWidth()
                .height(28.dp)
                .testTag("sphere_particle_count_slider"),
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = "Rotation period",
              style = MaterialTheme.typography.labelMedium,
              color = RiviaTextSecondary,
            )
            Text(
              text = "${rotationPeriodSec}s",
              style = MaterialTheme.typography.labelMedium,
              fontFamily = ChivoMonoFontFamily,
              color = RiviaTextPrimary,
              fontWeight = FontWeight.SemiBold,
            )
          }
          Slider(
            value = rotationPeriodSec.toFloat(),
            onValueChange = { raw ->
              val snapped = ((raw / 5f).roundToInt() * 5).coerceIn(10, 90)
              if (snapped != rotationPeriodSec) {
                rotationPeriodSec = snapped
                prefs.edit().putInt(PREF_KEY_ROTATION_PERIOD_SEC, snapped).apply()
              }
            },
            valueRange = 10f..90f,
            steps = 15,
            colors =
              SliderDefaults.colors(
                thumbColor = RiviaMint,
                activeTrackColor = RiviaMint,
                inactiveTrackColor = RiviaSurface2,
              ),
            modifier =
              Modifier.fillMaxWidth()
                .height(28.dp)
                .testTag("sphere_rotation_period_slider"),
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
          ) {
            TextButton(
              onClick = {
                particleCount = DEFAULT_PARTICLE_COUNT
                rotationPeriodSec = DEFAULT_ROTATION_PERIOD_SEC
                prefs
                  .edit()
                  .putInt(PREF_KEY_PARTICLE_COUNT, DEFAULT_PARTICLE_COUNT)
                  .putInt(PREF_KEY_ROTATION_PERIOD_SEC, DEFAULT_ROTATION_PERIOD_SEC)
                  .apply()
              },
              modifier = Modifier.testTag("sphere_reset_defaults_button"),
            ) {
              Text(
                text = "Reset to defaults",
                color = RiviaTextSecondary,
                fontSize = 12.sp,
              )
            }
          }
        }
      }
    }
  }
}

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
 * - Mode accent radial glow (#F5F5F5 in Rivia, #A78BFA in Dev, #60A5FA in Labs)
 * - Primary core (#EDEDED)
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
