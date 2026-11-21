package com.example.ui.components

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Paint as AndroidPaint
import android.graphics.RectF as AndroidRectF
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
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
import kotlin.math.roundToInt

private const val SPHERE_PREFS_NAME = "rivia_particle_sphere_prefs"
private const val PREF_KEY_PARTICLE_COUNT = "particle_count_v2"
private const val PREF_KEY_ROTATION_PERIOD_SEC = "rotation_period_sec"
private const val DEFAULT_PARTICLE_COUNT = 1350
private const val MIN_PARTICLE_COUNT = 400
private const val MAX_PARTICLE_COUNT = 2000
private const val DEFAULT_ROTATION_PERIOD_SEC = 40

class RiviaSphereSettingsState internal constructor(
  private val prefs: android.content.SharedPreferences,
  initialParticleCount: Int,
  initialRotationPeriodSec: Int,
) {
  var particleCount by mutableIntStateOf(initialParticleCount)
    private set

  var rotationPeriodSec by mutableIntStateOf(initialRotationPeriodSec)
    private set

  fun updateParticleCount(raw: Float) {
    val snapped = ((raw / 50f).roundToInt() * 50).coerceIn(MIN_PARTICLE_COUNT, MAX_PARTICLE_COUNT)
    if (snapped != particleCount) {
      particleCount = snapped
      prefs.edit().putInt(PREF_KEY_PARTICLE_COUNT, snapped).apply()
    }
  }

  fun updateRotationPeriodSec(raw: Float) {
    val snapped = ((raw / 5f).roundToInt() * 5).coerceIn(10, 90)
    if (snapped != rotationPeriodSec) {
      rotationPeriodSec = snapped
      prefs.edit().putInt(PREF_KEY_ROTATION_PERIOD_SEC, snapped).apply()
    }
  }

  fun resetToDefaults() {
    particleCount = DEFAULT_PARTICLE_COUNT
    rotationPeriodSec = DEFAULT_ROTATION_PERIOD_SEC
    prefs
      .edit()
      .putInt(PREF_KEY_PARTICLE_COUNT, DEFAULT_PARTICLE_COUNT)
      .putInt(PREF_KEY_ROTATION_PERIOD_SEC, DEFAULT_ROTATION_PERIOD_SEC)
      .apply()
  }
}

@Composable
fun rememberRiviaSphereSettingsState(): RiviaSphereSettingsState {
  val context = LocalContext.current
  val prefs =
    remember(context) { context.getSharedPreferences(SPHERE_PREFS_NAME, Context.MODE_PRIVATE) }
  return remember(prefs) {
    RiviaSphereSettingsState(
      prefs = prefs,
      initialParticleCount =
        prefs
          .getInt(PREF_KEY_PARTICLE_COUNT, DEFAULT_PARTICLE_COUNT)
          .coerceIn(MIN_PARTICLE_COUNT, MAX_PARTICLE_COUNT),
      initialRotationPeriodSec =
        prefs.getInt(PREF_KEY_ROTATION_PERIOD_SEC, DEFAULT_ROTATION_PERIOD_SEC).coerceIn(10, 90),
    )
  }
}

/**
 * Shared app-wide controller for Rivia's single persistent particle sphere living in RiviaAppRoot.
 * Screens configure position, scale/mode, surface bounds, and passive audio levels on this single
 * instance rather than creating separate sphere copies.
 */
@Stable
class RiviaParticleFieldController(
  val settingsState: RiviaSphereSettingsState,
) {
  var hostVisible by mutableStateOf(true)
  var elevateAboveDrawerScrim by mutableStateOf(false)

  // Draw-invalidating version counter for static/accessibility mode or layout rect updates
  var invalidationTick by mutableIntStateOf(0)
    private set

  var desiredMode: ParticleSceneMode = ParticleSceneMode.HOME
    private set
  var wantDrawerOpen: Boolean = false
    private set

  var viewportScale: ViewportScale = ViewportScale(1f, 1f, 1f)
  var canvasOriginInWindow: Offset = Offset.Zero
  var slotCenterInWindow: Offset = Offset.Zero

  var chatBarWindowRect: SimpleRectPx = SimpleRectPx()
  var chatBarFillAlpha: Float = 0f

  var dictationPanelWindowRect: SimpleRectPx = SimpleRectPx()
  var dictationWaveformWindowRect: SimpleRectPx = SimpleRectPx()
  var dictationPanelFillAlpha: Float = 0f

  var drawerWindowRect: SimpleRectPx = SimpleRectPx()

  var dictateMicLevel: Float = 0f
  var liveOrbState: OrbState = OrbState.IDLE
  var liveMicLevel: Float = 0f
  var livePlaybackLevel: Float = 0f
  var isLiveSpeakingFallback: Boolean = false

  fun requestRedraw() {
    invalidationTick++
  }

  fun updateDesiredMode(mode: ParticleSceneMode) {
    if (desiredMode != mode) {
      desiredMode = mode
      invalidationTick++
    }
  }

  fun updateWantDrawerOpen(open: Boolean, removeAnimations: Boolean) {
    if (wantDrawerOpen != open) {
      wantDrawerOpen = open
      if (open && !removeAnimations) {
        elevateAboveDrawerScrim = true
      } else if (removeAnimations) {
        elevateAboveDrawerScrim = false
      }
      invalidationTick++
    }
  }

  fun localRect(windowRect: SimpleRectPx): SimpleRectPx {
    if (!windowRect.isValid) return windowRect
    return SimpleRectPx(
      left = windowRect.left - canvasOriginInWindow.x,
      top = windowRect.top - canvasOriginInWindow.y,
      width = windowRect.width,
      height = windowRect.height,
      cornerRadius = windowRect.cornerRadius,
    )
  }
}

val LocalRiviaParticleFieldController = compositionLocalOf<RiviaParticleFieldController?> { null }

@Composable
fun rememberRiviaParticleFieldController(
  settingsState: RiviaSphereSettingsState = rememberRiviaSphereSettingsState(),
): RiviaParticleFieldController {
  return remember(settingsState) { RiviaParticleFieldController(settingsState) }
}

internal fun isSystemRemoveAnimationsEnabled(context: Context): Boolean {
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
 * Single-pass, zero-allocation Canvas renderer for Rivia's persistent Fibonacci sphere and all
 * particle transitions (Chat bar border, Dictation panel border + 60 interior scatter + 64 voice
 * waveform, Live mode Listening/Thinking/Speaking modulators, and Chats Drawer border).
 */
@Composable
fun RiviaParticleFieldCanvas(
  controller: RiviaParticleFieldController,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val particleCount = controller.settingsState.particleCount
  val rotationPeriodSec = controller.settingsState.rotationPeriodSec
  val engine = remember(particleCount) { ParticleFieldEngine(count = particleCount) }

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

  LaunchedEffect(removeAnimations) {
    if (removeAnimations && controller.elevateAboveDrawerScrim) {
      controller.elevateAboveDrawerScrim = false
    }
  }

  // Frame clock read only inside Canvas draw scope -> zero recomposition per frame (60 fps)
  var frameTick by remember { mutableIntStateOf(0) }
  val pendingDtHolder = remember { FloatArray(1) }

  LaunchedEffect(isAppResumed, controller.hostVisible, removeAnimations, engine) {
    if (!isAppResumed || !controller.hostVisible || removeAnimations) {
      pendingDtHolder[0] = 0f
      return@LaunchedEffect
    }
    var lastFrameNanos = -1L
    while (true) {
      withFrameNanos { frameNanos ->
        val dt =
          if (lastFrameNanos >= 0L) {
            (((frameNanos - lastFrameNanos).coerceAtLeast(0L)) / 1_000_000_000f).coerceAtMost(0.1f)
          } else {
            1f / 60f
          }
        lastFrameNanos = frameNanos
        pendingDtHolder[0] += dt
        frameTick++

        val shouldElevate =
          !removeAnimations &&
            (controller.wantDrawerOpen || engine.hasActiveDrawerOverlayParticles())
        if (controller.elevateAboveDrawerScrim != shouldElevate) {
          controller.elevateAboveDrawerScrim = shouldElevate
        }
      }
    }
  }

  val fillPaint = remember {
    AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
      style = AndroidPaint.Style.FILL
      color = android.graphics.Color.WHITE
    }
  }
  val strokePaint = remember {
    AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
      style = AndroidPaint.Style.STROKE
      color = 0xFF262626.toInt()
    }
  }
  val scratchRectF = remember { AndroidRectF() }
  val coreGlowBaseColor = remember { Color(220, 235, 255) }

  if (!controller.hostVisible) return

  Canvas(
    modifier =
      modifier.onGloballyPositioned { coords ->
        controller.canvasOriginInWindow = coords.positionInWindow()
      }
  ) {
    // Subscribe only the DrawNode to frameTick & controller.invalidationTick
    @Suppress("UNUSED_VARIABLE") val tick = frameTick + controller.invalidationTick
    val dt = pendingDtHolder[0].coerceAtMost(0.1f)
    pendingDtHolder[0] = 0f

    val effectiveMode =
      if (controller.wantDrawerOpen && !removeAnimations) {
        ParticleSceneMode.DRAWER_OUTLINE
      } else {
        controller.desiredMode
      }
    if (engine.mode != effectiveMode) {
      engine.setMode(effectiveMode, removeAnimations = removeAnimations)
    }

    val pxPerDp = 1.dp.toPx()
    val slotWin = controller.slotCenterInWindow
    val localSlotX =
      if (slotWin != Offset.Zero) slotWin.x - controller.canvasOriginInWindow.x else 0f
    val localSlotY =
      if (slotWin != Offset.Zero) slotWin.y - controller.canvasOriginInWindow.y else 0f

    val localChatRect = controller.localRect(controller.chatBarWindowRect)
    val localDictateRect = controller.localRect(controller.dictationPanelWindowRect)
    val localWaveRect = controller.localRect(controller.dictationWaveformWindowRect)
    val localDrawerRect = controller.localRect(controller.drawerWindowRect)

    engine.step(
      dtSec = dt,
      canvasWidthPx = this.size.width,
      canvasHeightPx = this.size.height,
      pxPerDp = pxPerDp,
      viewportScale = controller.viewportScale,
      slotCenterXPx = localSlotX,
      slotCenterYPx = localSlotY,
      rotationPeriodSec = rotationPeriodSec.toFloat(),
      removeAnimations = removeAnimations,
      chatBarRect = localChatRect,
      dictationPanelRect = localDictateRect,
      dictationWaveformRect = localWaveRect,
      drawerRect = localDrawerRect,
      dictateMicLevel = controller.dictateMicLevel,
      liveOrbState = controller.liveOrbState,
      liveMicLevel = controller.liveMicLevel,
      livePlaybackLevel = controller.livePlaybackLevel,
      isLiveSpeakingFallback = controller.isLiveSpeakingFallback,
    )

    val n = engine.count
    val cxArr = engine.currX
    val cyArr = engine.currY
    val rArr = engine.currRadiusPx
    val aArr = engine.currAlpha
    val detArr = engine.detached
    val rzArr = engine.rotZ
    val blur = engine.sphereBlurFactor

    // 1. Pass 1: Back-facing on-sphere particles (rz < 0, not detached)
    if (engine.sphereBrightness > 0.001f) {
      drawIntoCanvas { composeCanvas ->
        val nc = composeCanvas.nativeCanvas
        fillPaint.color = android.graphics.Color.WHITE
        for (i in 0 until n) {
          if (detArr[i] <= 0.001f && rzArr[i] < 0f) {
            val alphaInt = (aArr[i] * 255f).toInt()
            if (alphaInt > 0) {
              fillPaint.alpha = alphaInt.coerceAtMost(255)
              nc.drawCircle(cxArr[i], cyArr[i], rArr[i], fillPaint)
            }
          }
        }
      }
    }

    // 2. Core pale-blue radial glow
    val peakCoreAlpha = engine.coreGlowAlpha
    if (peakCoreAlpha > 0.002f) {
      val glowCenter = Offset(engine.sphereCenterX, engine.sphereCenterY)
      val glowRadius = engine.coreGlowRadiusPx
      drawCircle(
        brush =
          Brush.radialGradient(
            0.0f to coreGlowBaseColor.copy(alpha = peakCoreAlpha),
            0.32f to coreGlowBaseColor.copy(alpha = peakCoreAlpha * 0.65f),
            0.62f to coreGlowBaseColor.copy(alpha = peakCoreAlpha * 0.26f),
            0.84f to coreGlowBaseColor.copy(alpha = peakCoreAlpha * 0.07f),
            1.0f to Color.Transparent,
            center = glowCenter,
            radius = glowRadius,
          ),
        radius = glowRadius,
        center = glowCenter,
      )
    }

    // 3. Pass 2A + Under-Particle Fills (#161616 / #121212) + Pass 2B Detached Particles
    drawIntoCanvas { composeCanvas ->
      val nc = composeCanvas.nativeCanvas

      // 3a. Front-facing on-sphere particles (with optical blur halo when sphereBlurFactor > 0)
      if (engine.sphereBrightness > 0.001f) {
        val sphereHaloMult = 2.2f + 2.0f * blur
        val sphereHaloAlphaScale = 0.187f + 0.14f * blur
        fillPaint.color = android.graphics.Color.WHITE
        for (i in 0 until n) {
          if (detArr[i] <= 0.001f && rzArr[i] >= 0f) {
            val a = aArr[i]
            if (a > 0.002f) {
              val r = rArr[i]
              val haloA = (a * sphereHaloAlphaScale * 255f).toInt().coerceIn(0, 255)
              if (haloA > 0) {
                fillPaint.alpha = haloA
                nc.drawCircle(cxArr[i], cyArr[i], r * sphereHaloMult, fillPaint)
              }
              val coreA = (a * 255f).toInt().coerceIn(0, 255)
              if (coreA > 0) {
                fillPaint.alpha = coreA
                nc.drawCircle(cxArr[i], cyArr[i], r, fillPaint)
              }
            }
          }
        }
      }

      // 3b. Under-particle surface fills (#161616 message bar, #121212 dictation panel)
      // Drawn strictly under the border/scatter/waveform particles when not elevated over the drawer
      if (!controller.elevateAboveDrawerScrim) {
        val chatFillA = controller.chatBarFillAlpha.coerceIn(0f, 1f)
        if (chatFillA > 0.002f && localChatRect.isValid) {
          scratchRectF.set(
            localChatRect.left,
            localChatRect.top,
            localChatRect.left + localChatRect.width,
            localChatRect.top + localChatRect.height,
          )
          fillPaint.color = 0xFF161616.toInt()
          fillPaint.alpha = (chatFillA * 255f).toInt().coerceIn(0, 255)
          nc.drawRoundRect(
            scratchRectF,
            localChatRect.cornerRadius,
            localChatRect.cornerRadius,
            fillPaint,
          )
          strokePaint.strokeWidth = pxPerDp
          strokePaint.color = 0xFF262626.toInt()
          strokePaint.alpha = (chatFillA * 0.55f * 255f).toInt().coerceIn(0, 255)
          nc.drawRoundRect(
            scratchRectF,
            localChatRect.cornerRadius,
            localChatRect.cornerRadius,
            strokePaint,
          )
        }

        val dictFillA = controller.dictationPanelFillAlpha.coerceIn(0f, 1f)
        if (dictFillA > 0.002f && localDictateRect.isValid) {
          scratchRectF.set(
            localDictateRect.left,
            localDictateRect.top,
            localDictateRect.left + localDictateRect.width,
            localDictateRect.top + localDictateRect.height,
          )
          fillPaint.color = 0xFF121212.toInt()
          fillPaint.alpha = (dictFillA * 255f).toInt().coerceIn(0, 255)
          nc.drawRoundRect(
            scratchRectF,
            localDictateRect.cornerRadius,
            localDictateRect.cornerRadius,
            fillPaint,
          )
          strokePaint.strokeWidth = pxPerDp
          strokePaint.color = 0xFF262626.toInt()
          strokePaint.alpha = (dictFillA * 0.45f * 255f).toInt().coerceIn(0, 255)
          nc.drawRoundRect(
            scratchRectF,
            localDictateRect.cornerRadius,
            localDictateRect.cornerRadius,
            strokePaint,
          )
        }
      }

      // 3c. Detached particles (in-flight, twinkling outline borders, interior scatter, 64-waveform)
      fillPaint.color = android.graphics.Color.WHITE
      for (i in 0 until n) {
        val det = detArr[i]
        if (det > 0.001f) {
          val a = aArr[i]
          if (a > 0.002f) {
            val r = rArr[i]
            val haloFactor = if (rzArr[i] >= 0f) 1f else det.coerceIn(0f, 1f)
            val haloA = (a * 0.187f * haloFactor * 255f).toInt().coerceIn(0, 255)
            if (haloA > 0) {
              fillPaint.alpha = haloA
              nc.drawCircle(cxArr[i], cyArr[i], r * 2.2f, fillPaint)
            }
            val coreA = (a * 255f).toInt().coerceIn(0, 255)
            if (coreA > 0) {
              fillPaint.alpha = coreA
              nc.drawCircle(cxArr[i], cyArr[i], r, fillPaint)
            }
          }
        }
      }
    }
  }
}

/**
 * Invisible layout/interaction anchor for Rivia's shared global sphere.
 * Reports its center to [RiviaParticleFieldController], handles tap gestures in Live mode, and
 * optionally shows the Dev-only sphere tuning card.
 */
@Composable
fun RiviaSharedSphereSlot(
  controller: RiviaParticleFieldController,
  modifier: Modifier = Modifier,
  sphereModifier: Modifier = Modifier,
  devTuningModifier: Modifier = Modifier,
  devTuningEnabled: Boolean = true,
  showDevTuning: Boolean = false,
  contentDesc: String = "Rivia Orb",
  onSphereClick: (() -> Unit)? = null,
) {
  Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Box(
      modifier =
        sphereModifier
          .size(300.dp)
          .onGloballyPositioned { coords ->
            if (coords.size.width > 0 && coords.size.height > 0) {
              val pos = coords.positionInWindow()
              val nextCenter =
                Offset(
                  pos.x + coords.size.width * 0.5f,
                  pos.y + coords.size.height * 0.5f,
                )
              if (controller.slotCenterInWindow != nextCenter) {
                controller.slotCenterInWindow = nextCenter
                controller.requestRedraw()
              }
            }
          }
          .let { base ->
            if (onSphereClick != null) {
              base.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onSphereClick,
              )
            } else {
              base
            }
          }
          .testTag("home_particle_sphere")
          .semantics { contentDescription = contentDesc },
      contentAlignment = Alignment.Center,
    ) {
      if (onSphereClick != null) {
        Box(modifier = Modifier.fillMaxSize().testTag("live_mode_orb"))
      }
    }

    if (showDevTuning) {
      RiviaSphereDevTuningCard(
        settingsState = controller.settingsState,
        enabled = devTuningEnabled,
        modifier = devTuningModifier,
      )
    }
  }
}

@Composable
fun RiviaSphereDevTuningCard(
  settingsState: RiviaSphereSettingsState,
  enabled: Boolean = true,
  modifier: Modifier = Modifier,
) {
  Surface(
    color = RiviaSurface1,
    border = BorderStroke(1.dp, RiviaBorder),
    shape = RoundedCornerShape(14.dp),
    modifier =
      modifier
        .widthIn(max = 320.dp)
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
          text = "${settingsState.particleCount}",
          style = MaterialTheme.typography.labelMedium,
          fontFamily = ChivoMonoFontFamily,
          color = RiviaTextPrimary,
          fontWeight = FontWeight.SemiBold,
        )
      }
      Slider(
        value = settingsState.particleCount.toFloat(),
        enabled = enabled,
        onValueChange = settingsState::updateParticleCount,
        valueRange = MIN_PARTICLE_COUNT.toFloat()..MAX_PARTICLE_COUNT.toFloat(),
        steps = 31,
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
          text = "${settingsState.rotationPeriodSec}s",
          style = MaterialTheme.typography.labelMedium,
          fontFamily = ChivoMonoFontFamily,
          color = RiviaTextPrimary,
          fontWeight = FontWeight.SemiBold,
        )
      }
      Slider(
        value = settingsState.rotationPeriodSec.toFloat(),
        enabled = enabled,
        onValueChange = settingsState::updateRotationPeriodSec,
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
          enabled = enabled,
          onClick = settingsState::resetToDefaults,
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

/**
 * Delegates to the global shared sphere slot if hosted under RiviaAppRoot, or renders a standalone
 * particle field canvas only in isolated component previews/tests.
 */
@Composable
fun RiviaHomeParticleSphere(
  modifier: Modifier = Modifier,
  sphereModifier: Modifier = Modifier,
  devTuningModifier: Modifier = Modifier,
  devTuningEnabled: Boolean = true,
  showDevTuning: Boolean = false,
  contentDesc: String = "Rivia Orb",
  onSphereClick: (() -> Unit)? = null,
) {
  val sharedController = LocalRiviaParticleFieldController.current
  if (sharedController != null) {
    RiviaSharedSphereSlot(
      controller = sharedController,
      modifier = modifier,
      sphereModifier = sphereModifier,
      devTuningModifier = devTuningModifier,
      devTuningEnabled = devTuningEnabled,
      showDevTuning = showDevTuning,
      contentDesc = contentDesc,
      onSphereClick = onSphereClick,
    )
    return
  }

  val fallbackController = rememberRiviaParticleFieldController()
  Box(modifier = modifier, contentAlignment = Alignment.Center) {
    RiviaParticleFieldCanvas(
      controller = fallbackController,
      modifier = Modifier.size(300.dp),
    )
    RiviaSharedSphereSlot(
      controller = fallbackController,
      sphereModifier = sphereModifier,
      devTuningModifier = devTuningModifier,
      devTuningEnabled = devTuningEnabled,
      showDevTuning = showDevTuning,
      contentDesc = contentDesc,
      onSphereClick = onSphereClick,
    )
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
 * Rivia's abstract small inline orb avatar used beside assistant replies and drawer headers.
 */
@Composable
fun RiviaOrb(
  state: OrbState,
  modifier: Modifier = Modifier,
  size: Dp = 36.dp,
  contentDesc: String = "Rivia Orb",
) {
  val transition = rememberInfiniteTransition(label = "orb_transition")

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

    if (state == OrbState.SPEAKING) {
      drawCircle(
        color = RiviaMint.copy(alpha = (1f - animProgress) * 0.55f),
        radius = maxRadius * (0.62f + 0.36f * animProgress),
        center = center,
        style = Stroke(width = 1.2.dp.toPx()),
      )
    }

    drawCircle(
      color = RiviaMint.copy(alpha = if (state == OrbState.IDLE) 0.75f else 0.95f),
      radius = maxRadius * ringScale,
      center = center,
      style = Stroke(width = (1.5.dp.toPx()).coerceAtLeast(1.5f)),
    )

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
