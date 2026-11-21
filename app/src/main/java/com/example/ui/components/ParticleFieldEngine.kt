package com.example.ui.components

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Reference screen dimensions (390 x 844 dp) scaling helper.
 * Scales transition offsets and container dimensions proportionally to the actual pane/screen.
 */
data class ViewportScale(
  val widthScale: Float,
  val heightScale: Float,
  val geomScale: Float,
) {
  companion object {
    const val REF_WIDTH_DP = 390f
    const val REF_HEIGHT_DP = 844f

    fun fromPaneDp(widthDp: Float, heightDp: Float): ViewportScale {
      val ws =
        if (widthDp > 1f) (widthDp / REF_WIDTH_DP).coerceIn(0.65f, 1.85f) else 1f
      val hs =
        if (heightDp > 1f) (heightDp / REF_HEIGHT_DP).coerceIn(0.65f, 1.65f) else 1f
      val gs = sqrt(ws * hs).coerceIn(0.70f, 1.65f)
      return ViewportScale(widthScale = ws, heightScale = hs, geomScale = gs)
    }
  }
}

data class SimpleRectPx(
  val left: Float = 0f,
  val top: Float = 0f,
  val width: Float = 0f,
  val height: Float = 0f,
  val cornerRadius: Float = 0f,
) {
  val isValid: Boolean
    get() = width > 1f && height > 1f
}

data class RoundedRectOutlineSpec(
  val leftPx: Float,
  val topPx: Float,
  val widthPx: Float,
  val heightPx: Float,
  val cornerRadiusPx: Float,
  val insetPx: Float = 0f,
)

data class OutlinePoint(
  val x: Float,
  val y: Float,
)

enum class ParticleSceneMode {
  HOME,
  CHAT_BAR,
  CHAT_BAR_AFTER_DICTATION_ERROR,
  DICTATION_PANEL,
  LIVE,
  DRAWER_OUTLINE,
}

/**
 * Computes a point at normalized perimeter progress [normalizedU] in [0, 1) around a rounded
 * rectangle, starting at the top-left (where the top-left corner arc meets the top straight edge)
 * and sweeping strictly clockwise:
 * Top -> Top-Right arc -> Right -> Bottom-Right arc -> Bottom -> Bottom-Left arc -> Left -> Top-Left arc.
 */
fun computeRoundedRectOutlinePoint(
  spec: RoundedRectOutlineSpec,
  normalizedU: Float,
): OutlinePoint {
  val l = spec.leftPx + spec.insetPx
  val t = spec.topPx + spec.insetPx
  val w = (spec.widthPx - 2f * spec.insetPx).coerceAtLeast(2f)
  val h = (spec.heightPx - 2f * spec.insetPx).coerceAtLeast(2f)
  val r = (spec.cornerRadiusPx - spec.insetPx).coerceIn(0f, min(w, h) * 0.5f)
  val right = l + w
  val bottom = t + h

  val halfPi = (0.5 * PI).toFloat()
  val pi = PI.toFloat()
  val segTop = (w - 2f * r).coerceAtLeast(0f)
  val segSide = (h - 2f * r).coerceAtLeast(0f)
  val segArc = halfPi * r
  val perimeter = (2f * segTop + 2f * segSide + 4f * segArc).coerceAtLeast(1e-3f)

  val wrappedU = ((normalizedU % 1f) + 1f) % 1f
  var d = wrappedU * perimeter

  if (d < segTop) {
    return OutlinePoint(x = l + r + d, y = t)
  }
  d -= segTop

  if (d < segArc && segArc > 0f) {
    val ang = -halfPi + (d / segArc) * halfPi
    return OutlinePoint(
      x = (right - r) + r * cos(ang),
      y = (t + r) + r * sin(ang),
    )
  }
  d -= segArc

  if (d < segSide) {
    return OutlinePoint(x = right, y = t + r + d)
  }
  d -= segSide

  if (d < segArc && segArc > 0f) {
    val ang = (d / segArc) * halfPi
    return OutlinePoint(
      x = (right - r) + r * cos(ang),
      y = (bottom - r) + r * sin(ang),
    )
  }
  d -= segArc

  if (d < segTop) {
    return OutlinePoint(x = (right - r) - d, y = bottom)
  }
  d -= segTop

  if (d < segArc && segArc > 0f) {
    val ang = halfPi + (d / segArc) * halfPi
    return OutlinePoint(
      x = (l + r) + r * cos(ang),
      y = (bottom - r) + r * sin(ang),
    )
  }
  d -= segArc

  if (d < segSide) {
    return OutlinePoint(x = l, y = (bottom - r) - d)
  }
  d -= segSide

  val frac = if (segArc > 0f) (d / segArc).coerceIn(0f, 1f) else 0f
  val ang = pi + frac * halfPi
  return OutlinePoint(
    x = (l + r) + r * cos(ang),
    y = (t + r) + r * sin(ang),
  )
}

/**
 * Generates [count] clockwise outline points packed as [x0, y0, x1, y1, ...] starting at top-left.
 */
fun generateRoundedRectOutlinePoints(
  spec: RoundedRectOutlineSpec,
  count: Int,
  outXY: FloatArray = FloatArray((count.coerceAtLeast(0)) * 2),
): FloatArray {
  if (count <= 0) return outXY
  val denom = count.toFloat()
  for (i in 0 until count) {
    val pt = computeRoundedRectOutlinePoint(spec, i.toFloat() / denom)
    outXY[i * 2] = pt.x
    outXY[i * 2 + 1] = pt.y
  }
  return outXY
}

internal fun easeOutCubic(t: Float): Float {
  val clamped = t.coerceIn(0f, 1f)
  val inv = 1f - clamped
  return 1f - inv * inv * inv
}

/**
 * Pure-Kotlin, zero-per-frame-allocation particle field engine powering Rivia's shared Fibonacci
 * sphere and all mode transitions (Home, Chat bar, Dictation panel + scatter + 64-particle voice
 * waveform, Live mode with Listening/Thinking/Speaking modulators, and Chats Drawer outline).
 */
class ParticleFieldEngine(
  val count: Int = DEFAULT_POOL_COUNT,
  seed: Long = PARTICLE_SEED,
) {
  companion object {
    const val DEFAULT_POOL_COUNT = 1350
    const val CHAT_OUTLINE_TARGET_COUNT = 460
    const val DICTATE_OUTLINE_TARGET_COUNT = 600
    const val DICTATE_SCATTER_TARGET_COUNT = 60
    const val DICTATE_WAVEFORM_TARGET_COUNT = 64
    const val DRAWER_OUTLINE_TARGET_COUNT = 1000
    const val PARTICLE_SEED = 0x52495649L

    const val TARGET_NONE = 0
    const val TARGET_CHAT_OUTLINE = 1
    const val TARGET_DICTATE_OUTLINE = 2
    const val TARGET_DICTATE_SCATTER = 3
    const val TARGET_DICTATE_WAVEFORM = 4
    const val TARGET_DRAWER_OUTLINE = 5

    private val TWO_PI = (2.0 * PI).toFloat()
    private val PI_F = PI.toFloat()
    private val TILT_RAD = Math.toRadians(-16.0).toFloat()
    private val COS_TILT = cos(TILT_RAD)
    private val SIN_TILT = sin(TILT_RAD)
  }

  val chatOutlineCount: Int = min(CHAT_OUTLINE_TARGET_COUNT, count)
  val dictateOutlineCount: Int =
    if (count >= 724) {
      DICTATE_OUTLINE_TARGET_COUNT
    } else {
      (count * 0.72f).roundToInt().coerceIn(0, count)
    }
  val dictateScatterCount: Int =
    if (count >= 724) {
      DICTATE_SCATTER_TARGET_COUNT
    } else {
      min(DICTATE_SCATTER_TARGET_COUNT, (count - dictateOutlineCount).coerceAtLeast(0) / 2)
    }
  val dictateWaveformCount: Int =
    if (count >= 724) {
      DICTATE_WAVEFORM_TARGET_COUNT
    } else {
      min(
        DICTATE_WAVEFORM_TARGET_COUNT,
        (count - dictateOutlineCount - dictateScatterCount).coerceAtLeast(0),
      )
    }
  val drawerOutlineCount: Int =
    if (count >= 1300) {
      min(DRAWER_OUTLINE_TARGET_COUNT, count)
    } else {
      (count * 0.741f).roundToInt().coerceIn(0, min(DRAWER_OUTLINE_TARGET_COUNT, count))
    }

  // Sphere unit vectors & appearance (permuted so every index range is uniform across all latitudes)
  val ux = FloatArray(count)
  val uy = FloatArray(count)
  val uz = FloatArray(count)
  val radiusDp = FloatArray(count)
  val frontBaseOpacity = FloatArray(count)
  val twinkleOmega = FloatArray(count)
  val twinklePhase = FloatArray(count)

  // Deterministic normalized interior scatter coordinates (0..1) for Dictation panel
  private val scatterNormX = FloatArray(dictateScatterCount.coerceAtLeast(1))
  private val scatterNormY = FloatArray(dictateScatterCount.coerceAtLeast(1))

  // Per-frame rotated 3D vectors & sphere projections
  val rotX = FloatArray(count)
  val rotY = FloatArray(count)
  val rotZ = FloatArray(count)

  // Live rendered particle state (read directly by the single Canvas pass & unit tests)
  val currX = FloatArray(count)
  val currY = FloatArray(count)
  val currRadiusPx = FloatArray(count)
  val currAlpha = FloatArray(count)
  val detached = FloatArray(count) // 0f = on sphere, >0f = in flight or on target

  // Per-particle retargeting state
  val targetKind = IntArray(count) { TARGET_NONE }
  val targetSlot = IntArray(count) { -1 }
  private val startX = FloatArray(count)
  private val startY = FloatArray(count)
  private val startRadiusPx = FloatArray(count)
  private val startAlpha = FloatArray(count)
  private val startDetached = FloatArray(count)
  private val wasDetachedOnRetarget = BooleanArray(count)
  private val particleElapsed = FloatArray(count)
  private val particleDelay = FloatArray(count)
  private val particleDuration = FloatArray(count) { 0.3f }

  // Cached outline lookup tables (recomputed only when measured Rect changes)
  private val chatOutlineXY = FloatArray((chatOutlineCount.coerceAtLeast(1)) * 2)
  private var cachedChatRect = SimpleRectPx()

  private val dictateOutlineXY = FloatArray((dictateOutlineCount.coerceAtLeast(1)) * 2)
  private var cachedDictateRect = SimpleRectPx()

  private val drawerOutlineXY = FloatArray((drawerOutlineCount.coerceAtLeast(1)) * 2)
  private var cachedDrawerRect = SimpleRectPx()

  // Whole-sphere state & smooth interpolation
  var mode: ParticleSceneMode = ParticleSceneMode.HOME
    private set
  private var underlyingModeBeforeDrawer: ParticleSceneMode = ParticleSceneMode.HOME

  var sphereCenterX: Float = 0f
    private set
  var sphereCenterY: Float = 0f
    private set
  var sphereVisualScale: Float = 1f
    private set
  var sphereBrightness: Float = 1f
    private set
  var sphereBlurFactor: Float = 0f
    private set
  var coreGlowAlpha: Float = 0.2f
    private set
  var coreGlowRadiusPx: Float = 80f
    private set

  private var sphereCurrNormOffsetY = 0f // in reference dp before viewport heightScale
  private var sphereCurrModeScale = 1f
  private var sphereCurrModeAlpha = 1f
  private var sphereCurrBlur = 0f
  private var sphereCurrDrawerBlend = 0f

  private var sphereStartNormOffsetY = 0f
  private var sphereStartModeScale = 1f
  private var sphereStartModeAlpha = 1f
  private var sphereStartBlur = 0f
  private var sphereStartDrawerBlend = 0f
  private var sphereTransitionElapsed = 1f
  private var sphereTransitionDuration = 1f

  // Integrated continuous clocks (prevent phase jumps when speed changes in Live Speaking)
  private var spinAngleRad = 0f
  private var twinkleClockSec = 0f
  private var breathClockSec = 0f
  private var liveCycleSec = 0f
  private var ripplePhaseRad = 0f
  private var waveformPhaseRad = 0f

  private var smoothedSpinSpeedMult = 1f
  private var smoothedTwinkleSpeedMult = 1f
  private var smoothedLivePulseScale = 1f
  private var smoothedLiveBrightnessMult = 1f
  private var smoothedIdleBreathWeight = 1f
  private var smoothedLiveMicLevel = 0f
  private var smoothedPlaybackLevel = 0f
  private var smoothedDictateMicLevel = 0f

  private var hasInitializedPositions = false

  init {
    val rng = Random(seed)
    val goldenAngle = PI * (3.0 - sqrt(5.0))
    // Coprime stride so contiguous index slices [0..K) sample all latitudes uniformly
    val stride = findCoprimeStride(count)

    for (i in 0 until count) {
      val fibIdx = ((i.toLong() * stride.toLong()) % count.toLong()).toInt()
      val y = (1.0 - 2.0 * (fibIdx + 0.5) / count.toDouble()).coerceIn(-1.0, 1.0)
      val latitude = asin(y)
      val longitude = fibIdx.toDouble() * goldenAngle
      val cosLat = cos(latitude)

      ux[i] = (cosLat * cos(longitude)).toFloat()
      uy[i] = sin(latitude).toFloat()
      uz[i] = (cosLat * sin(longitude)).toFloat()

      val diameterDp = 0.9f + rng.nextFloat() * (2.4f - 0.9f)
      radiusDp[i] = diameterDp * 0.5f
      frontBaseOpacity[i] = 0.55f + rng.nextFloat() * (1.0f - 0.55f)

      val periodSec = 1.6f + rng.nextFloat() * (4.2f - 1.6f)
      twinkleOmega[i] = TWO_PI / periodSec
      twinklePhase[i] = rng.nextFloat() * TWO_PI
    }

    // Halton (2, 3) sequence for clean, non-clumping interior scatter inside Dictation panel
    for (s in 0 until dictateScatterCount) {
      scatterNormX[s] = halton(s + 1, 2)
      scatterNormY[s] = halton(s + 1, 3)
    }
  }

  private fun findCoprimeStride(n: Int): Int {
    if (n <= 1) return 1
    var candidate = (n * 0.61803398875).roundToInt().coerceIn(1, n - 1)
    while (gcd(candidate, n) != 1) {
      candidate = (candidate + 1) % n
      if (candidate == 0) candidate = 1
    }
    return candidate
  }

  private fun gcd(a: Int, b: Int): Int {
    var x = a
    var y = b
    while (y != 0) {
      val t = x % y
      x = y
      y = t
    }
    return x
  }

  private fun halton(index: Int, base: Int): Float {
    var result = 0f
    var f = 1f / base.toFloat()
    var i = index
    while (i > 0) {
      result += f * (i % base).toFloat()
      i /= base
      f /= base.toFloat()
    }
    return result.coerceIn(0.04f, 0.96f)
  }

  /**
   * Returns true if any particle is still detached from the sphere in DRAWER_OUTLINE or releasing
   * from the drawer outline (used to keep the Canvas above ModalNavigationDrawer's scrim until
   * closing completes).
   */
  fun hasActiveDrawerOverlayParticles(): Boolean {
    if (mode == ParticleSceneMode.DRAWER_OUTLINE) return true
    if (sphereCurrDrawerBlend > 0.002f) return true
    for (i in 0 until count) {
      if (detached[i] > 0.002f && targetKind[i] == TARGET_NONE && sphereStartDrawerBlend > 0.01f) {
        return true
      }
    }
    return false
  }

  /**
   * Transitions to [newMode], snapshotting every particle's exact current rendered position so
   * mid-transition interruptions and mode-to-mode transitions never snap or strand any particle.
   */
  fun setMode(newMode: ParticleSceneMode, removeAnimations: Boolean = false) {
    if (newMode == mode && !removeAnimations) return
    val prevMode = mode
    if (newMode == ParticleSceneMode.DRAWER_OUTLINE && prevMode != ParticleSceneMode.DRAWER_OUTLINE) {
      underlyingModeBeforeDrawer = prevMode
    }
    mode = newMode

    // Snapshot sphere whole-body state for smooth C0-continuous easing
    sphereStartNormOffsetY = sphereCurrNormOffsetY
    sphereStartModeScale = sphereCurrModeScale
    sphereStartModeAlpha = sphereCurrModeAlpha
    sphereStartBlur = sphereCurrBlur
    sphereStartDrawerBlend = sphereCurrDrawerBlend
    sphereTransitionElapsed = 0f
    sphereTransitionDuration =
      when {
        removeAnimations -> 0.0001f
        newMode == ParticleSceneMode.DRAWER_OUTLINE -> 0.75f
        prevMode == ParticleSceneMode.DRAWER_OUTLINE -> 0.52f
        prevMode == ParticleSceneMode.DICTATION_PANEL && newMode == ParticleSceneMode.CHAT_BAR ->
          0.45f
        else -> 1.00f
      }

    // Snapshot every particle's exact current coordinate, radius, alpha, and detached state
    for (i in 0 until count) {
      startX[i] = currX[i]
      startY[i] = currY[i]
      startRadiusPx[i] = currRadiusPx[i]
      startAlpha[i] = currAlpha[i]
      startDetached[i] = detached[i]
      wasDetachedOnRetarget[i] = detached[i] > 0f
      particleElapsed[i] = 0f
    }

    // Clear all targets first; particles not given a target below ease directly back to the sphere
    val prevTargetKind = targetKind.clone()
    val prevTargetSlot = targetSlot.clone()
    targetKind.fill(TARGET_NONE)
    targetSlot.fill(-1)

    when (newMode) {
      ParticleSceneMode.HOME,
      ParticleSceneMode.LIVE -> {
        assignReleaseTimings(prevTargetKind, prevTargetSlot)
      }

      ParticleSceneMode.CHAT_BAR -> {
        val cCount = chatOutlineCount
        if (prevMode == ParticleSceneMode.DICTATION_PANEL && dictateOutlineCount >= cCount && cCount > 0) {
          // Done from Dictation: slide 460 evenly spaced dictation-edge particles straight down
          // into the corresponding perimeter positions on the 52 dp message bar outline!
          for (m in 0 until cCount) {
            val idx = ((m * dictateOutlineCount) / cCount).coerceIn(0, count - 1)
            val u = m.toFloat() / cCount.toFloat()
            targetKind[idx] = TARGET_CHAT_OUTLINE
            targetSlot[idx] = m
            particleDelay[idx] = u * 0.10f
            particleDuration[idx] = 0.36f
          }
        } else {
          // Sweep ~460 particles clockwise starting at top-left over ~700 ms
          val denom = cCount.coerceAtLeast(1).toFloat()
          for (m in 0 until cCount) {
            val u = m.toFloat() / denom
            targetKind[m] = TARGET_CHAT_OUTLINE
            targetSlot[m] = m
            particleDelay[m] = u * 0.44f
            particleDuration[m] = 0.26f
          }
        }
        assignReleaseTimingsForClearedParticles(prevTargetKind, prevTargetSlot)
      }

      ParticleSceneMode.CHAT_BAR_AFTER_DICTATION_ERROR -> {
        // Dictation error: release ALL dictation panel particles [0 .. dictateTotal) back to the
        // sphere while landing on the Chat end state using a disjoint slice of sphere particles!
        val dictateTotal = dictateOutlineCount + dictateScatterCount + dictateWaveformCount
        val cCount = chatOutlineCount
        val denom = cCount.coerceAtLeast(1).toFloat()
        val canBeDisjoint = (dictateTotal + cCount) <= count
        val baseOffset = if (canBeDisjoint) dictateTotal else (count - cCount).coerceAtLeast(0)
        for (m in 0 until cCount) {
          val idx = (baseOffset + m) % count
          val u = m.toFloat() / denom
          targetKind[idx] = TARGET_CHAT_OUTLINE
          targetSlot[idx] = m
          particleDelay[idx] = u * 0.44f
          particleDuration[idx] = 0.26f
        }
        assignReleaseTimingsForClearedParticles(prevTargetKind, prevTargetSlot)
      }

      ParticleSceneMode.DICTATION_PANEL -> {
        // 1. ~600 clockwise edge outline particles over ~750 ms
        val eCount = dictateOutlineCount
        val eDenom = eCount.coerceAtLeast(1).toFloat()
        for (m in 0 until eCount) {
          val u = m.toFloat() / eDenom
          targetKind[m] = TARGET_DICTATE_OUTLINE
          targetSlot[m] = m
          particleDelay[m] = u * 0.47f
          particleDuration[m] = 0.28f
        }
        // 2. ~60 interior scatter particles at 30% brightness
        val sStart = eCount
        val sDenom = dictateScatterCount.coerceAtLeast(1).toFloat()
        for (s in 0 until dictateScatterCount) {
          val idx = sStart + s
          if (idx >= count) break
          val u = s.toFloat() / sDenom
          targetKind[idx] = TARGET_DICTATE_SCATTER
          targetSlot[idx] = s
          particleDelay[idx] = 0.12f + u * 0.35f
          particleDuration[idx] = 0.28f
        }
        // 3. 64 horizontal voice-waveform particles across the panel
        val wStart = sStart + dictateScatterCount
        val wDenom = (dictateWaveformCount - 1).coerceAtLeast(1).toFloat()
        for (w in 0 until dictateWaveformCount) {
          val idx = wStart + w
          if (idx >= count) break
          val u = w.toFloat() / wDenom
          targetKind[idx] = TARGET_DICTATE_WAVEFORM
          targetSlot[idx] = w
          particleDelay[idx] = 0.14f + u * 0.33f
          particleDuration[idx] = 0.28f
        }
        assignReleaseTimingsForClearedParticles(prevTargetKind, prevTargetSlot)
      }

      ParticleSceneMode.DRAWER_OUTLINE -> {
        // ~1,000 particles sweep clockwise around the drawer's rounded rectangle over ~750 ms
        val dCount = drawerOutlineCount
        val dDenom = dCount.coerceAtLeast(1).toFloat()
        for (m in 0 until dCount) {
          val u = m.toFloat() / dDenom
          targetKind[m] = TARGET_DRAWER_OUTLINE
          targetSlot[m] = m
          particleDelay[m] = u * 0.48f
          particleDuration[m] = 0.27f
        }
        assignReleaseTimingsForClearedParticles(prevTargetKind, prevTargetSlot)
      }
    }

    if (removeAnimations) {
      sphereTransitionElapsed = sphereTransitionDuration
    }
  }

  private fun assignReleaseTimings(prevKind: IntArray, prevSlot: IntArray) {
    for (i in 0 until count) {
      if (startDetached[i] > 0.001f) {
        val norm = normalizedSlotForRelease(prevKind[i], prevSlot[i], i)
        particleDelay[i] = (1f - norm) * 0.14f
        particleDuration[i] = 0.38f
      } else {
        particleDelay[i] = 0f
        particleDuration[i] = 0.01f
      }
    }
  }

  private fun assignReleaseTimingsForClearedParticles(prevKind: IntArray, prevSlot: IntArray) {
    for (i in 0 until count) {
      if (targetKind[i] == TARGET_NONE) {
        if (startDetached[i] > 0.001f) {
          val norm = normalizedSlotForRelease(prevKind[i], prevSlot[i], i)
          particleDelay[i] = (1f - norm) * 0.14f
          particleDuration[i] = 0.38f
        } else {
          particleDelay[i] = 0f
          particleDuration[i] = 0.01f
        }
      }
    }
  }

  private fun normalizedSlotForRelease(kind: Int, slot: Int, fallbackIdx: Int): Float {
    if (slot < 0) return (fallbackIdx.toFloat() / count.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
    val denom =
      when (kind) {
        TARGET_CHAT_OUTLINE -> chatOutlineCount
        TARGET_DICTATE_OUTLINE -> dictateOutlineCount
        TARGET_DICTATE_SCATTER -> dictateScatterCount
        TARGET_DICTATE_WAVEFORM -> dictateWaveformCount
        TARGET_DRAWER_OUTLINE -> drawerOutlineCount
        else -> count
      }.coerceAtLeast(1)
    return (slot.toFloat() / denom.toFloat()).coerceIn(0f, 1f)
  }

  private fun targetSphereParamsForMode(m: ParticleSceneMode): FloatArray {
    // [normOffsetYDp, modeScale, modeAlpha, blurFactor]
    return when (m) {
      ParticleSceneMode.HOME -> floatArrayOf(0f, 1.00f, 1.00f, 0.0f)
      ParticleSceneMode.CHAT_BAR,
      ParticleSceneMode.CHAT_BAR_AFTER_DICTATION_ERROR ->
        floatArrayOf(-110f, 0.75f, 0.12f, 1.0f)
      ParticleSceneMode.DICTATION_PANEL -> floatArrayOf(-110f, 0.75f, 0.22f, 0.85f)
      ParticleSceneMode.LIVE -> floatArrayOf(-50f, 1.05f, 1.00f, 0.0f)
      ParticleSceneMode.DRAWER_OUTLINE -> targetSphereParamsForMode(underlyingModeBeforeDrawer)
    }
  }

  /**
   * Advances simulation by [dtSec] and computes every particle's screen position, radius, and
   * opacity for the current frame.
   */
  fun step(
    dtSec: Float,
    canvasWidthPx: Float,
    canvasHeightPx: Float,
    pxPerDp: Float,
    viewportScale: ViewportScale,
    slotCenterXPx: Float,
    slotCenterYPx: Float,
    rotationPeriodSec: Float = 40f,
    removeAnimations: Boolean = false,
    chatBarRect: SimpleRectPx = SimpleRectPx(),
    dictationPanelRect: SimpleRectPx = SimpleRectPx(),
    dictationWaveformRect: SimpleRectPx = SimpleRectPx(),
    drawerRect: SimpleRectPx = SimpleRectPx(),
    dictateMicLevel: Float = 0f,
    liveOrbState: OrbState = OrbState.IDLE,
    liveMicLevel: Float = 0f,
    livePlaybackLevel: Float = 0f,
    isLiveSpeakingFallback: Boolean = false,
  ) {
    val dt = if (removeAnimations) 0f else dtSec.coerceIn(0f, 0.1f)
    val safePxPerDp = pxPerDp.coerceAtLeast(0.5f)
    val wPx = canvasWidthPx.coerceAtLeast(1f)
    val hPx = canvasHeightPx.coerceAtLeast(1f)

    // 1. Update whole-sphere transition state
    val baseTargets = targetSphereParamsForMode(mode)
    val wantDrawerBlend = if (mode == ParticleSceneMode.DRAWER_OUTLINE && !removeAnimations) 1f else 0f

    if (removeAnimations) {
      sphereCurrNormOffsetY = baseTargets[0]
      sphereCurrModeScale = baseTargets[1]
      sphereCurrModeAlpha = baseTargets[2]
      sphereCurrBlur = baseTargets[3]
      sphereCurrDrawerBlend = 0f
    } else {
      sphereTransitionElapsed = (sphereTransitionElapsed + dt).coerceAtMost(sphereTransitionDuration)
      val sp = (sphereTransitionElapsed / sphereTransitionDuration.coerceAtLeast(1e-4f)).coerceIn(0f, 1f)
      val se = easeOutCubic(sp)
      sphereCurrNormOffsetY =
        sphereStartNormOffsetY + (baseTargets[0] - sphereStartNormOffsetY) * se
      sphereCurrModeScale =
        sphereStartModeScale + (baseTargets[1] - sphereStartModeScale) * se
      sphereCurrModeAlpha =
        sphereStartModeAlpha + (baseTargets[2] - sphereStartModeAlpha) * se
      sphereCurrBlur =
        sphereStartBlur + (baseTargets[3] - sphereStartBlur) * se
      sphereCurrDrawerBlend =
        sphereStartDrawerBlend + (wantDrawerBlend - sphereStartDrawerBlend) * se
    }

    // 2. Live mode state modulators (Listening / Thinking / Speaking)
    val inLive = mode == ParticleSceneMode.LIVE
    val targetSpinMult =
      if (inLive && liveOrbState == OrbState.SPEAKING) 1.40f else 1.00f
    val targetTwinkleMult =
      if (inLive && liveOrbState == OrbState.SPEAKING) 1.85f else 1.00f

    if (removeAnimations) {
      smoothedSpinSpeedMult = 1f
      smoothedTwinkleSpeedMult = 1f
      smoothedLiveMicLevel = 0f
      smoothedPlaybackLevel = 0f
      smoothedDictateMicLevel = dictateMicLevel.coerceIn(0f, 1f)
    } else {
      val smoothRate = (dt * 8f).coerceIn(0f, 1f)
      val audioAttack = (dt * 18f).coerceIn(0f, 1f)
      val audioRelease = (dt * 8f).coerceIn(0f, 1f)

      smoothedSpinSpeedMult += (targetSpinMult - smoothedSpinSpeedMult) * smoothRate
      smoothedTwinkleSpeedMult += (targetTwinkleMult - smoothedTwinkleSpeedMult) * smoothRate

      val clampedLiveMic = if (inLive && liveOrbState == OrbState.LISTENING) liveMicLevel.coerceIn(0f, 1f) else 0f
      smoothedLiveMicLevel +=
        (clampedLiveMic - smoothedLiveMicLevel) *
          (if (clampedLiveMic > smoothedLiveMicLevel) audioAttack else audioRelease)

      val rawPlayback =
        if (inLive && liveOrbState == OrbState.SPEAKING) {
          val p = livePlaybackLevel.coerceIn(0f, 1f)
          if (p > 0.02f) {
            p
          } else if (isLiveSpeakingFallback) {
            // Gentle fallback organic cadence if audio decoder hasn't emitted PCM yet
            0.35f + 0.25f * (0.5f - 0.5f * cos(liveCycleSec * 7.2f))
          } else {
            0f
          }
        } else {
          0f
        }
      smoothedPlaybackLevel +=
        (rawPlayback - smoothedPlaybackLevel) *
          (if (rawPlayback > smoothedPlaybackLevel) audioAttack else audioRelease)

      val clampedDictateMic = dictateMicLevel.coerceIn(0f, 1f)
      smoothedDictateMicLevel +=
        (clampedDictateMic - smoothedDictateMicLevel) *
          (if (clampedDictateMic > smoothedDictateMicLevel) audioAttack else audioRelease)
    }

    // Advance continuous clocks
    if (!removeAnimations) {
      val baseOmega = TWO_PI / rotationPeriodSec.coerceAtLeast(1f)
      spinAngleRad = (spinAngleRad + dt * baseOmega * smoothedSpinSpeedMult) % TWO_PI
      twinkleClockSec += dt * smoothedTwinkleSpeedMult
      breathClockSec += dt
      liveCycleSec += dt
      ripplePhaseRad = (ripplePhaseRad + dt * 6.5f) % TWO_PI
      waveformPhaseRad = (waveformPhaseRad + dt * 7.8f) % TWO_PI
    }

    val targetLivePulse: Float
    val targetLiveBright: Float
    if (inLive && !removeAnimations) {
      when (liveOrbState) {
        OrbState.LISTENING -> {
          // 1.00 - 1.03 scale, 2.4 s cycle
          val wave = 0.5f - 0.5f * cos(TWO_PI * (liveCycleSec / 2.4f))
          targetLivePulse = 1.00f + 0.03f * wave
          targetLiveBright = 1.00f
        }
        OrbState.THINKING -> {
          // 0.97 - 1.00 scale, 1.4 s cycle, particles dim slightly
          val wave = 0.5f - 0.5f * cos(TWO_PI * (liveCycleSec / 1.4f))
          targetLivePulse = 1.00f - 0.03f * wave
          targetLiveBright = 0.76f
        }
        OrbState.SPEAKING -> {
          // Rhythmic swell up to 1.06 driven by Rivia's playback level
          targetLivePulse = 1.00f + (0.06f * smoothedPlaybackLevel).coerceIn(0f, 0.06f)
          targetLiveBright = 1.00f
        }
        OrbState.IDLE -> {
          targetLivePulse = 1.00f
          targetLiveBright = 1.00f
        }
      }
    } else {
      targetLivePulse = 1.00f
      targetLiveBright = 1.00f
    }

    val targetIdleBreathWeight = if (inLive) 0f else 1f
    if (removeAnimations) {
      smoothedLivePulseScale = 1f
      smoothedLiveBrightnessMult = 1f
      smoothedIdleBreathWeight = targetIdleBreathWeight
    } else {
      val modBlend = (dt * 10f).coerceIn(0f, 1f)
      smoothedLivePulseScale += (targetLivePulse - smoothedLivePulseScale) * modBlend
      smoothedLiveBrightnessMult += (targetLiveBright - smoothedLiveBrightnessMult) * modBlend
      smoothedIdleBreathWeight += (targetIdleBreathWeight - smoothedIdleBreathWeight) * modBlend
    }

    // Whole sphere idle breathing (smoothly attenuated in Live where state pulse governs scale)
    val breathEase =
      if (removeAnimations) {
        0f
      } else {
        (0.5f - 0.5f * cos(TWO_PI * (breathClockSec / 6.0f))) * smoothedIdleBreathWeight
      }
    val breathScale = 1.0f + 0.015f * breathEase

    val coreGlowBreath =
      if (removeAnimations) {
        0.20f
      } else {
        0.16f + 0.08f * (0.5f - 0.5f * cos(TWO_PI * (breathClockSec / 5.0f)))
      }

    // 3. Compute sphere center, scale, brightness, and optical blur
    val baseCx = if (slotCenterXPx > 0f) slotCenterXPx else wPx * 0.5f
    val baseSlotCy = if (slotCenterYPx > 0f) slotCenterYPx else hPx * 0.45f
    val modeOffsetYPx = sphereCurrNormOffsetY * viewportScale.heightScale * safePxPerDp
    val baseCy = baseSlotCy + modeOffsetYPx

    val dsp = sphereCurrDrawerBlend.coerceIn(0f, 1f)
    val drawerRightPx =
      if (drawerRect.isValid) {
        (drawerRect.left + drawerRect.width).coerceAtMost(wPx)
      } else {
        min(300f * safePxPerDp, wPx)
      }
    val uncoveredStripCx = drawerRightPx + (wPx - drawerRightPx) * 0.5f

    sphereCenterX = baseCx + (uncoveredStripCx - baseCx) * dsp
    sphereCenterY = baseCy + (baseSlotCy - baseCy) * dsp

    val modeAndLiveScale = sphereCurrModeScale * smoothedLivePulseScale
    val effectiveModeScale = modeAndLiveScale + (1f - modeAndLiveScale) * dsp
    val radiusViewportFactor = viewportScale.geomScale.coerceIn(0.84f, 1.22f)
    sphereVisualScale = breathScale * effectiveModeScale * (1f - 0.65f * dsp)

    val modeAndLiveAlpha = (sphereCurrModeAlpha * smoothedLiveBrightnessMult).coerceIn(0f, 1f)
    sphereBrightness = (modeAndLiveAlpha + (0.35f - modeAndLiveAlpha) * dsp).coerceIn(0f, 1f)
    sphereBlurFactor = (sphereCurrBlur * (1f - dsp)).coerceIn(0f, 1f)

    val sphereRadiusPx = 115f * safePxPerDp * radiusViewportFactor * sphereVisualScale
    val cameraDistancePx = 700f * safePxPerDp
    val sphereDensityScale = if (count > 650) sqrt(650f / count.toFloat()) else 1f

    coreGlowRadiusPx =
      (80f * safePxPerDp * radiusViewportFactor * sphereVisualScale * (1f + 0.25f * sphereBlurFactor))
        .coerceAtLeast(1f)
    coreGlowAlpha =
      (0.9f * coreGlowBreath * sphereBrightness * (1f - 0.18f * sphereBlurFactor)).coerceIn(0f, 1f)

    // 4. Refresh outline lookup tables when active / fallback geometry changes
    val effectiveChatRect =
      if (chatBarRect.isValid) {
        chatBarRect
      } else {
        defaultBottomRect(
          canvasWidthPx = wPx,
          canvasHeightPx = hPx,
          pxPerDp = safePxPerDp,
          viewportScale = viewportScale,
          refHeightDp = 52f,
          refCornerDp = 26f,
        )
      }
    ensureOutlineCache(
      rect = effectiveChatRect,
      cached = cachedChatRect,
      insetPx = 0.75f * safePxPerDp,
      targetCount = chatOutlineCount,
      outXY = chatOutlineXY,
    )?.let { cachedChatRect = it }

    val effectiveDictateRect =
      if (dictationPanelRect.isValid) {
        dictationPanelRect
      } else {
        defaultBottomRect(
          canvasWidthPx = wPx,
          canvasHeightPx = hPx,
          pxPerDp = safePxPerDp,
          viewportScale = viewportScale,
          refHeightDp = 180f,
          refCornerDp = 26f,
        )
      }
    ensureOutlineCache(
      rect = effectiveDictateRect,
      cached = cachedDictateRect,
      insetPx = 0.75f * safePxPerDp,
      targetCount = dictateOutlineCount,
      outXY = dictateOutlineXY,
    )?.let { cachedDictateRect = it }

    val effectiveDrawerRect =
      if (drawerRect.isValid) {
        drawerRect
      } else {
        SimpleRectPx(
          left = 0f,
          top = 0f,
          width = min(300f * safePxPerDp, wPx),
          height = hPx,
          cornerRadius = 22f * safePxPerDp,
        )
      }
    ensureOutlineCache(
      rect = effectiveDrawerRect,
      cached = cachedDrawerRect,
      insetPx = 1.25f * safePxPerDp,
      targetCount = drawerOutlineCount,
      outXY = drawerOutlineXY,
    )?.let { cachedDrawerRect = it }

    // Effective Dictation waveform band
    val waveLeft: Float
    val waveWidth: Float
    val waveCenterY: Float
    if (dictationWaveformRect.isValid) {
      val pad = 6f * safePxPerDp
      waveLeft = dictationWaveformRect.left + pad
      waveWidth = (dictationWaveformRect.width - 2f * pad).coerceAtLeast(1f)
      waveCenterY = dictationWaveformRect.top + dictationWaveformRect.height * 0.5f
    } else {
      val pad = 24f * safePxPerDp
      waveLeft = effectiveDictateRect.left + pad
      waveWidth = (effectiveDictateRect.width - 2f * pad).coerceAtLeast(1f)
      waveCenterY = effectiveDictateRect.top + effectiveDictateRect.height * 0.54f
    }
    val maxWaveAmpPx = 10.5f * viewportScale.heightScale * safePxPerDp
    val silentWaveAmpPx = 0.42f * safePxPerDp

    // Dictation interior scatter padded bounds
    val scatterInset = (effectiveDictateRect.cornerRadius * 0.55f).coerceAtLeast(12f * safePxPerDp)
    val scatterLeft = effectiveDictateRect.left + scatterInset
    val scatterTop = effectiveDictateRect.top + scatterInset * 0.75f
    val scatterW = (effectiveDictateRect.width - 2f * scatterInset).coerceAtLeast(4f)
    val scatterH = (effectiveDictateRect.height - 1.5f * scatterInset).coerceAtLeast(4f)

    // 5. Rotate all sphere unit vectors & update every particle's position
    val cosSpin = if (removeAnimations) 1f else cos(spinAngleRad)
    val sinSpin = if (removeAnimations) 0f else sin(spinAngleRad)
    val hasMicRipple = smoothedLiveMicLevel > 0.005f && !removeAnimations
    val defocusRadiusMult = 1f + 0.62f * sphereBlurFactor
    val defocusCoreAlphaMult = 1f - 0.34f * sphereBlurFactor

    val firstInit = !hasInitializedPositions
    hasInitializedPositions = true

    for (i in 0 until count) {
      val x1 = ux[i] * cosSpin + uz[i] * sinSpin
      val y1 = uy[i]
      val z1 = -ux[i] * sinSpin + uz[i] * cosSpin

      val rx = x1
      val ry = y1 * COS_TILT - z1 * SIN_TILT
      val rz = y1 * SIN_TILT + z1 * COS_TILT
      rotX[i] = rx
      rotY[i] = ry
      rotZ[i] = rz

      val rippleMult =
        if (hasMicRipple) {
          1f +
            smoothedLiveMicLevel *
              (0.032f + 0.030f * sin(ripplePhaseRad - uy[i] * 5.2f))
        } else {
          1f
        }

      val effRadius = sphereRadiusPx * rippleMult
      val wx = rx * effRadius
      val wy = ry * effRadius
      val wz = rz * effRadius
      val persp = cameraDistancePx / (cameraDistancePx - wz).coerceAtLeast(50f)

      val sphereSx = sphereCenterX + wx * persp
      val sphereSy = sphereCenterY - wy * persp
      val sphereRPx = radiusDp[i] * safePxPerDp * sphereVisualScale * persp * defocusRadiusMult

      val twinkleFactor =
        if (removeAnimations) {
          1.0f
        } else {
          0.70f + 0.30f * sin(twinkleClockSec * twinkleOmega[i] + twinklePhase[i])
        }

      val rawSphereAlpha =
        if (rz >= 0f) {
          frontBaseOpacity[i] *
            twinkleFactor *
            sphereDensityScale *
            sphereBrightness *
            defocusCoreAlphaMult
        } else {
          0.22f *
            twinkleFactor *
            sphereDensityScale *
            sphereBrightness *
            defocusCoreAlphaMult
        }.coerceIn(0f, 1f)

      val kind = targetKind[i]
      val slot = targetSlot[i]

      if (kind == TARGET_NONE) {
        if (firstInit || removeAnimations || !wasDetachedOnRetarget[i]) {
          currX[i] = sphereSx
          currY[i] = sphereSy
          currRadiusPx[i] = sphereRPx
          currAlpha[i] = rawSphereAlpha
          detached[i] = 0f
        } else {
          particleElapsed[i] += dt
          val p =
            ((particleElapsed[i] - particleDelay[i]) / particleDuration[i].coerceAtLeast(1e-4f))
              .coerceIn(0f, 1f)
          val e = easeOutCubic(p)
          currX[i] = startX[i] + (sphereSx - startX[i]) * e
          currY[i] = startY[i] + (sphereSy - startY[i]) * e
          currRadiusPx[i] = startRadiusPx[i] + (sphereRPx - startRadiusPx[i]) * e
          currAlpha[i] = (startAlpha[i] + (rawSphereAlpha - startAlpha[i]) * e).coerceIn(0f, 1f)
          detached[i] = if (p >= 1f) 0f else startDetached[i] * (1f - e)
        }
      } else {
        var destX = sphereSx
        var destY = sphereSy
        var destRadius = radiusDp[i] * safePxPerDp
        var destAlpha = (frontBaseOpacity[i] * twinkleFactor).coerceIn(0f, 1f)

        when (kind) {
          TARGET_CHAT_OUTLINE -> {
            val s = slot.coerceIn(0, (chatOutlineCount - 1).coerceAtLeast(0))
            destX = chatOutlineXY[s * 2]
            destY = chatOutlineXY[s * 2 + 1]
            // Faint twinkling border on the message bar
            destAlpha = (frontBaseOpacity[i] * twinkleFactor * 0.58f).coerceIn(0f, 1f)
            destRadius = radiusDp[i] * safePxPerDp * 0.95f
          }
          TARGET_DICTATE_OUTLINE -> {
            val s = slot.coerceIn(0, (dictateOutlineCount - 1).coerceAtLeast(0))
            destX = dictateOutlineXY[s * 2]
            destY = dictateOutlineXY[s * 2 + 1]
            destAlpha = (frontBaseOpacity[i] * twinkleFactor * 0.72f).coerceIn(0f, 1f)
            destRadius = radiusDp[i] * safePxPerDp
          }
          TARGET_DICTATE_SCATTER -> {
            val s = slot.coerceIn(0, (dictateScatterCount - 1).coerceAtLeast(0))
            destX = scatterLeft + scatterNormX[s] * scatterW
            destY = scatterTop + scatterNormY[s] * scatterH
            // Scattered faintly inside the panel at 30% brightness
            destAlpha = (frontBaseOpacity[i] * twinkleFactor * 0.30f).coerceIn(0f, 1f)
            destRadius = radiusDp[i] * safePxPerDp * 0.92f
          }
          TARGET_DICTATE_WAVEFORM -> {
            val w = slot.coerceIn(0, (dictateWaveformCount - 1).coerceAtLeast(0))
            val u =
              if (dictateWaveformCount > 1) w.toFloat() / (dictateWaveformCount - 1).toFloat()
              else 0.5f
            val centerEnvelope = sin(PI_F * u).coerceIn(0f, 1f)
            val waveVal =
              if (removeAnimations) {
                0f
              } else {
                sin(
                  waveformPhaseRad - w * 0.34f +
                    0.32f * sin(waveformPhaseRad * 0.43f + w * 0.19f)
                )
              }
            val amp =
              centerEnvelope * (silentWaveAmpPx + smoothedDictateMicLevel * maxWaveAmpPx)
            destX = waveLeft + u * waveWidth
            destY = waveCenterY + waveVal * amp
            destRadius =
              (1.35f + 0.55f * centerEnvelope * smoothedDictateMicLevel) * safePxPerDp
            destAlpha =
              (0.54f + 0.46f * (0.35f * centerEnvelope + 0.65f * smoothedDictateMicLevel))
                .coerceIn(0.42f, 1f)
          }
          TARGET_DRAWER_OUTLINE -> {
            val s = slot.coerceIn(0, (drawerOutlineCount - 1).coerceAtLeast(0))
            destX = drawerOutlineXY[s * 2]
            destY = drawerOutlineXY[s * 2 + 1]
            destAlpha = (frontBaseOpacity[i] * twinkleFactor).coerceIn(0f, 1f)
            destRadius = radiusDp[i] * safePxPerDp
          }
        }

        if (firstInit) {
          startX[i] = sphereSx
          startY[i] = sphereSy
          startRadiusPx[i] = sphereRPx
          startAlpha[i] = rawSphereAlpha
          startDetached[i] = 0f
          wasDetachedOnRetarget[i] = false
        }

        if (removeAnimations) {
          currX[i] = destX
          currY[i] = destY
          currRadiusPx[i] = destRadius
          currAlpha[i] = destAlpha
          detached[i] = 1f
        } else {
          particleElapsed[i] += dt
          val p =
            ((particleElapsed[i] - particleDelay[i]) / particleDuration[i].coerceAtLeast(1e-4f))
              .coerceIn(0f, 1f)
          val e = easeOutCubic(p)
          val originX = if (wasDetachedOnRetarget[i]) startX[i] else sphereSx
          val originY = if (wasDetachedOnRetarget[i]) startY[i] else sphereSy
          val originR = if (wasDetachedOnRetarget[i]) startRadiusPx[i] else sphereRPx
          val originA = if (wasDetachedOnRetarget[i]) startAlpha[i] else rawSphereAlpha
          currX[i] = originX + (destX - originX) * e
          currY[i] = originY + (destY - originY) * e
          currRadiusPx[i] = originR + (destRadius - originR) * e
          currAlpha[i] = (originA + (destAlpha - originA) * e).coerceIn(0f, 1f)
          detached[i] = startDetached[i] + (1f - startDetached[i]) * e
        }
      }
    }
  }

  private fun defaultBottomRect(
    canvasWidthPx: Float,
    canvasHeightPx: Float,
    pxPerDp: Float,
    viewportScale: ViewportScale,
    refHeightDp: Float,
    refCornerDp: Float,
  ): SimpleRectPx {
    val marginPx = 8f * viewportScale.widthScale * pxPerDp
    val vertPadPx = 8f * viewportScale.heightScale * pxPerDp
    val hPx = refHeightDp * viewportScale.heightScale * pxPerDp
    val wPx = (canvasWidthPx - 2f * marginPx).coerceAtLeast(40f)
    val topPx = (canvasHeightPx - vertPadPx - hPx).coerceAtLeast(0f)
    return SimpleRectPx(
      left = marginPx,
      top = topPx,
      width = wPx,
      height = hPx,
      cornerRadius = refCornerDp * viewportScale.geomScale * pxPerDp,
    )
  }

  private fun ensureOutlineCache(
    rect: SimpleRectPx,
    cached: SimpleRectPx,
    insetPx: Float,
    targetCount: Int,
    outXY: FloatArray,
  ): SimpleRectPx? {
    if (!rect.isValid || targetCount <= 0) return null
    if (rect == cached) return null
    generateRoundedRectOutlinePoints(
      spec =
        RoundedRectOutlineSpec(
          leftPx = rect.left,
          topPx = rect.top,
          widthPx = rect.width,
          heightPx = rect.height,
          cornerRadiusPx = rect.cornerRadius,
          insetPx = insetPx,
        ),
      count = targetCount,
      outXY = outXY,
    )
    return rect
  }
}
