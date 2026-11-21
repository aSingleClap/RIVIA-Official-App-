package com.example

import com.example.ui.components.OrbState
import com.example.ui.components.ParticleFieldEngine
import com.example.ui.components.ParticleSceneMode
import com.example.ui.components.RoundedRectOutlineSpec
import com.example.ui.components.SimpleRectPx
import com.example.ui.components.ViewportScale
import com.example.ui.components.computeRoundedRectOutlinePoint
import com.example.ui.components.generateRoundedRectOutlinePoints
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ParticleFieldEngineTest {

  private fun boundaryDistanceToRoundedRect(
    x: Float,
    y: Float,
    spec: RoundedRectOutlineSpec,
  ): Float {
    val l = spec.leftPx + spec.insetPx
    val t = spec.topPx + spec.insetPx
    val w = (spec.widthPx - 2f * spec.insetPx).coerceAtLeast(2f)
    val h = (spec.heightPx - 2f * spec.insetPx).coerceAtLeast(2f)
    val r = (spec.cornerRadiusPx - spec.insetPx).coerceIn(0f, min(w, h) * 0.5f)

    val cx = l + w * 0.5f
    val cy = t + h * 0.5f
    val halfInnerW = w * 0.5f - r
    val halfInnerH = h * 0.5f - r

    val qx = abs(x - cx) - halfInnerW
    val qy = abs(y - cy) - halfInnerH
    val outsideDist = hypot(max(qx, 0f), max(qy, 0f))
    val insideDist = min(max(qx, qy), 0f)
    val signedDistance = outsideDist + insideDist - r
    return abs(signedDistance)
  }

  @Test
  fun roundedRectOutline_liesOnStraightEdgesAndRoundedCornerArcs() {
    // 1. 180 dp Dictation panel (4 straight edges + 4 rounded corner arcs)
    val panelSpec =
      RoundedRectOutlineSpec(
        leftPx = 8f,
        topPx = 656f,
        widthPx = 374f,
        heightPx = 180f,
        cornerRadiusPx = 26f,
        insetPx = 0.75f,
      )
    val panelXY = generateRoundedRectOutlinePoints(panelSpec, count = 600)
    assertEquals(1200, panelXY.size)

    val l = panelSpec.leftPx + panelSpec.insetPx
    val t = panelSpec.topPx + panelSpec.insetPx
    val w = panelSpec.widthPx - 2f * panelSpec.insetPx
    val h = panelSpec.heightPx - 2f * panelSpec.insetPx
    val r = panelSpec.cornerRadiusPx - panelSpec.insetPx
    val right = l + w
    val bottom = t + h

    var topEdgeHits = 0
    var rightEdgeHits = 0
    var bottomEdgeHits = 0
    var leftEdgeHits = 0
    var topRightArcHits = 0
    var bottomRightArcHits = 0
    var bottomLeftArcHits = 0
    var topLeftArcHits = 0

    for (i in 0 until 600) {
      val x = panelXY[i * 2]
      val y = panelXY[i * 2 + 1]
      assertFalse("Point $i must be finite", x.isNaN() || y.isNaN() || x.isInfinite() || y.isInfinite())
      val dist = boundaryDistanceToRoundedRect(x, y, panelSpec)
      assertTrue(
        "Panel point $i ($x, $y) must lie on rounded-rect boundary, got dist=$dist",
        dist < 1e-2f,
      )

      when {
        abs(y - t) < 1e-2f && x >= l + r - 1e-2f && x <= right - r + 1e-2f -> topEdgeHits++
        abs(x - right) < 1e-2f && y >= t + r - 1e-2f && y <= bottom - r + 1e-2f -> rightEdgeHits++
        abs(y - bottom) < 1e-2f && x >= l + r - 1e-2f && x <= right - r + 1e-2f -> bottomEdgeHits++
        abs(x - l) < 1e-2f && y >= t + r - 1e-2f && y <= bottom - r + 1e-2f -> leftEdgeHits++
        x > right - r && y < t + r -> topRightArcHits++
        x > right - r && y > bottom - r -> bottomRightArcHits++
        x < l + r && y > bottom - r -> bottomLeftArcHits++
        x < l + r && y < t + r -> topLeftArcHits++
      }
    }

    assertTrue("Expected points on top straight edge", topEdgeHits > 20)
    assertTrue("Expected points on right straight edge", rightEdgeHits > 20)
    assertTrue("Expected points on bottom straight edge", bottomEdgeHits > 20)
    assertTrue("Expected points on left straight edge", leftEdgeHits > 20)
    assertTrue("Expected points on top-right corner arc", topRightArcHits > 10)
    assertTrue("Expected points on bottom-right corner arc", bottomRightArcHits > 10)
    assertTrue("Expected points on bottom-left corner arc", bottomLeftArcHits > 10)
    assertTrue("Expected points on top-left corner arc", topLeftArcHits > 10)

    // 2. 52 dp Chat message bar (stadium pill: height == 2 * cornerRadius)
    val pillSpec =
      RoundedRectOutlineSpec(
        leftPx = 8f,
        topPx = 784f,
        widthPx = 374f,
        heightPx = 52f,
        cornerRadiusPx = 26f,
        insetPx = 0.75f,
      )
    val pillXY = generateRoundedRectOutlinePoints(pillSpec, count = 460)
    assertEquals(920, pillXY.size)
    for (i in 0 until 460) {
      val x = pillXY[i * 2]
      val y = pillXY[i * 2 + 1]
      val dist = boundaryDistanceToRoundedRect(x, y, pillSpec)
      assertTrue(
        "Pill point $i ($x, $y) must lie on stadium boundary, got dist=$dist",
        dist < 1e-2f,
      )
    }
  }

  @Test
  fun roundedRectOutline_sweepsClockwiseStartingAtTopLeft() {
    val spec =
      RoundedRectOutlineSpec(
        leftPx = 16f,
        topPx = 620f,
        widthPx = 358f,
        heightPx = 180f,
        cornerRadiusPx = 26f,
        insetPx = 0f,
      )
    val count = 600
    val xy = generateRoundedRectOutlinePoints(spec, count)

    // Point 0 starts at top-left where top-left corner arc meets the top straight edge
    val p0 = computeRoundedRectOutlinePoint(spec, 0f)
    assertEquals(spec.leftPx + spec.cornerRadiusPx, p0.x, 1e-3f)
    assertEquals(spec.topPx, p0.y, 1e-3f)
    assertEquals(p0.x, xy[0], 1e-3f)
    assertEquals(p0.y, xy[1], 1e-3f)

    // Early points move right along the top edge
    assertTrue("Second point should move right along top edge", xy[2] > xy[0])
    assertEquals(spec.topPx, xy[3], 1e-3f)

    // Unwrapped polar angle around center must strictly increase clockwise (screen Y-down)
    val cx = spec.leftPx + spec.widthPx * 0.5f
    val cy = spec.topPx + spec.heightPx * 0.5f
    val twoPi = (2.0 * PI).toFloat()

    var unwrappedAngle = atan2(xy[1] - cy, xy[0] - cx)
    val startAngle = unwrappedAngle

    for (i in 1 until count) {
      val x = xy[i * 2]
      val y = xy[i * 2 + 1]
      var raw = atan2(y - cy, x - cx)
      while (raw < unwrappedAngle - Math.PI.toFloat()) {
        raw += twoPi
      }
      while (raw > unwrappedAngle + Math.PI.toFloat()) {
        raw -= twoPi
      }
      assertTrue(
        "Clockwise sweep angle must increase from point ${i - 1} ($unwrappedAngle) to $i ($raw)",
        raw > unwrappedAngle,
      )
      unwrappedAngle = raw
    }

    val totalSweep = unwrappedAngle - startAngle
    assertTrue(
      "Full perimeter sweep should span ~2*PI radians clockwise, got $totalSweep",
      totalSweep > twoPi * 0.98f && totalSweep <= twoPi * 1.01f,
    )
  }

  @Test
  fun midTransitionInterruption_neverSnapsOrStrandsParticles_andDictationErrorReleasesPanel() {
    val engine = ParticleFieldEngine(count = 1350)
    val viewport = ViewportScale.fromPaneDp(390f, 844f)
    val pxPerDp = 2f
    val wPx = 390f * pxPerDp
    val hPx = 844f * pxPerDp
    val chatRect =
      SimpleRectPx(
        left = 8f * pxPerDp,
        top = (844f - 60f) * pxPerDp,
        width = 374f * pxPerDp,
        height = 52f * pxPerDp,
        cornerRadius = 26f * pxPerDp,
      )
    val dictateRect =
      SimpleRectPx(
        left = 8f * pxPerDp,
        top = (844f - 188f) * pxPerDp,
        width = 374f * pxPerDp,
        height = 180f * pxPerDp,
        cornerRadius = 26f * pxPerDp,
      )

    fun stepFrame(dtSec: Float) {
      engine.step(
        dtSec = dtSec,
        canvasWidthPx = wPx,
        canvasHeightPx = hPx,
        pxPerDp = pxPerDp,
        viewportScale = viewport,
        slotCenterXPx = wPx * 0.5f,
        slotCenterYPx = hPx * 0.44f,
        chatBarRect = chatRect,
        dictationPanelRect = dictateRect,
        liveOrbState = OrbState.LISTENING,
      )
    }

    // Initialize in HOME
    stepFrame(0.016f)

    // 1. Start HOME -> CHAT_BAR and step 250 ms (some particles mid-flight/landed, some still on sphere)
    engine.setMode(ParticleSceneMode.CHAT_BAR)
    repeat(15) { stepFrame(0.016f) }

    var midFlightCount = 0
    var stillOnSphereCount = 0
    for (i in 0 until engine.count) {
      if (engine.detached[i] > 0.01f) midFlightCount++ else stillOnSphereCount++
    }
    assertTrue("Expected detached particles mid-sweep at ~240ms", midFlightCount > 50)
    assertTrue("Expected un-departed particles still on sphere at ~240ms", stillOnSphereCount > 900)

    // Snapshot positions right before interrupting CHAT_BAR -> DICTATION_PANEL
    val preX = engine.currX.clone()
    val preY = engine.currY.clone()

    engine.setMode(ParticleSceneMode.DICTATION_PANEL)
    stepFrame(0f) // Zero-dt check immediately after retargeting

    for (i in 0 until engine.count) {
      assertEquals("Particle $i X must not snap on interruption", preX[i], engine.currX[i], 1e-3f)
      assertEquals("Particle $i Y must not snap on interruption", preY[i], engine.currY[i], 1e-3f)
      val hasTarget = engine.targetKind[i] != ParticleFieldEngine.TARGET_NONE
      if (hasTarget) {
        assertTrue("Targeted particle $i must have a valid slot", engine.targetSlot[i] >= 0)
      } else {
        assertEquals(-1, engine.targetSlot[i])
      }
    }

    // Advance 300 ms into DICTATION_PANEL, then interrupt midway into LIVE
    repeat(18) { stepFrame(0.016f) }
    val preLiveX = engine.currX.clone()
    val preLiveY = engine.currY.clone()

    engine.setMode(ParticleSceneMode.LIVE)
    stepFrame(0f)

    for (i in 0 until engine.count) {
      assertEquals("Particle $i X must not snap on LIVE interruption", preLiveX[i], engine.currX[i], 1e-3f)
      assertEquals("Particle $i Y must not snap on LIVE interruption", preLiveY[i], engine.currY[i], 1e-3f)
      assertEquals(
        "LIVE mode must clear all outline/panel targets back to sphere",
        ParticleFieldEngine.TARGET_NONE,
        engine.targetKind[i],
      )
    }

    // Step to completion in LIVE and verify zero stranded detached particles
    repeat(75) { stepFrame(0.016f) }
    for (i in 0 until engine.count) {
      assertEquals(
        "Particle $i must return completely to sphere in LIVE (not stranded)",
        0f,
        engine.detached[i],
        1e-3f,
      )
    }

    // 2. Enter DICTATION_PANEL, let all 724 panel particles (600 edge + 60 scatter + 64 waveform)
    // settle, then trigger CHAT_BAR_AFTER_DICTATION_ERROR and verify all 724 return to sphere
    // while 460 disjoint particles form the Chat bar outline!
    engine.setMode(ParticleSceneMode.DICTATION_PANEL)
    repeat(75) { stepFrame(0.016f) }

    val dictateTotal =
      engine.dictateOutlineCount + engine.dictateScatterCount + engine.dictateWaveformCount
    assertEquals(724, dictateTotal)
    for (i in 0 until dictateTotal) {
      assertEquals("Dictation particle $i should be settled on panel", 1f, engine.detached[i], 1e-3f)
    }

    val preErrX = engine.currX.clone()
    val preErrY = engine.currY.clone()
    engine.setMode(ParticleSceneMode.CHAT_BAR_AFTER_DICTATION_ERROR)
    stepFrame(0f)

    for (i in 0 until engine.count) {
      assertEquals("Particle $i X must not snap on dictation error", preErrX[i], engine.currX[i], 1e-3f)
      assertEquals("Particle $i Y must not snap on dictation error", preErrY[i], engine.currY[i], 1e-3f)
    }
    for (i in 0 until dictateTotal) {
      assertEquals(
        "Former dictation panel particle $i must be released back to sphere on dictation error",
        ParticleFieldEngine.TARGET_NONE,
        engine.targetKind[i],
      )
    }

    // Step until settled on Chat end state
    repeat(80) { stepFrame(0.016f) }
    var settledChatOutlineCount = 0
    for (i in 0 until engine.count) {
      if (i < dictateTotal) {
        assertEquals(
          "Released dictation particle $i must finish back on sphere",
          0f,
          engine.detached[i],
          1e-3f,
        )
      }
      if (engine.targetKind[i] == ParticleFieldEngine.TARGET_CHAT_OUTLINE) {
        settledChatOutlineCount++
        assertEquals(1f, engine.detached[i], 1e-3f)
      } else {
        assertEquals(0f, engine.detached[i], 1e-3f)
      }
    }
    assertEquals(460, settledChatOutlineCount)
    assertEquals(0.12f, engine.sphereBrightness, 0.015f)
  }

  @Test
  fun animationsOff_jumpsImmediatelyToEndStatesWithZeroParticleMovement() {
    val engine = ParticleFieldEngine(count = 1350)
    val viewport = ViewportScale.fromPaneDp(390f, 844f)
    val pxPerDp = 2f
    val wPx = 390f * pxPerDp
    val hPx = 844f * pxPerDp

    engine.setMode(ParticleSceneMode.CHAT_BAR, removeAnimations = true)
    engine.step(
      dtSec = 0.016f,
      canvasWidthPx = wPx,
      canvasHeightPx = hPx,
      pxPerDp = pxPerDp,
      viewportScale = viewport,
      slotCenterXPx = wPx * 0.5f,
      slotCenterYPx = hPx * 0.44f,
      removeAnimations = true,
    )

    assertEquals(0.75f, engine.sphereVisualScale, 1e-3f)
    assertEquals(0.12f, engine.sphereBrightness, 1e-3f)

    val snapX = engine.currX.clone()
    val snapY = engine.currY.clone()

    // Step again with animations off: zero particle movement
    engine.step(
      dtSec = 0.5f,
      canvasWidthPx = wPx,
      canvasHeightPx = hPx,
      pxPerDp = pxPerDp,
      viewportScale = viewport,
      slotCenterXPx = wPx * 0.5f,
      slotCenterYPx = hPx * 0.44f,
      removeAnimations = true,
    )
    for (i in 0 until engine.count) {
      assertEquals(snapX[i], engine.currX[i], 0f)
      assertEquals(snapY[i], engine.currY[i], 0f)
    }
  }
}
