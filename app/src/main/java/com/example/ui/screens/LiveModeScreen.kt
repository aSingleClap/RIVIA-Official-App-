package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.service.LiveSessionManager
import com.example.service.LiveVoiceService
import com.example.ui.components.LocalRiviaParticleFieldController
import com.example.ui.components.OrbState
import com.example.ui.components.ParticleSceneMode
import com.example.ui.components.RiviaHomeParticleSphere
import com.example.ui.components.StatusShape
import com.example.ui.components.StatusType
import com.example.ui.components.ViewportScale
import com.example.ui.components.isSystemRemoveAnimationsEnabled
import com.example.ui.theme.ChivoMonoFontFamily
import com.example.ui.theme.RiviaBackground
import com.example.ui.theme.RiviaBorder
import com.example.ui.theme.RiviaErrorRed
import com.example.ui.theme.RiviaIconActive
import com.example.ui.theme.RiviaIconDefault
import com.example.ui.theme.RiviaMint
import com.example.ui.theme.RiviaMutedText
import com.example.ui.theme.RiviaOnAccent
import com.example.ui.theme.RiviaRaisedSurface
import com.example.ui.theme.RiviaSurface2
import com.example.ui.theme.RiviaSurface3
import com.example.ui.theme.RiviaText
import com.example.ui.theme.RiviaTextSecondary
import com.example.ui.theme.RiviaTextTertiary
import com.example.ui.theme.RiviaWarningAmber
import com.example.viewmodel.RiviaViewModel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LiveModeScreen(
  viewModel: RiviaViewModel,
  onEndLive: () -> Unit,
) {
  val context = LocalContext.current
  val view = LocalView.current
  val removeAnimations = isSystemRemoveAnimationsEnabled(context)
  val particleController = LocalRiviaParticleFieldController.current

  val liveState by LiveSessionManager.uiState.collectAsStateWithLifecycle()
  val liveMicLevel by LiveSessionManager.liveMicLevel.collectAsStateWithLifecycle()
  val playbackAudioLevel by
    viewModel.audioPlayerManager.playbackAudioLevel.collectAsStateWithLifecycle()
  val isAudioSpeaking by viewModel.audioPlayerManager.isSpeaking.collectAsStateWithLifecycle()
  val voicePrefs by viewModel.voicePreferences.collectAsStateWithLifecycle()
  val runSettings by viewModel.currentRunSettings.collectAsStateWithLifecycle()

  var micPermissionDeniedReason by remember { mutableStateOf<String?>(null) }

  // End Live cleanly on system Back
  BackHandler {
    LiveVoiceService.stop(context)
    onEndLive()
  }

  // Keep screen on in Live setting (on by default)
  DisposableEffect(voicePrefs.keepScreenOnInLive) {
    val prev = view.keepScreenOn
    if (voicePrefs.keepScreenOnInLive) {
      view.keepScreenOn = true
    }
    onDispose { view.keepScreenOn = prev }
  }

  // Subtle haptics on state transitions
  LaunchedEffect(liveState.orbState) {
    if (liveState.isActive) {
      view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }
  }

  val permissionsLauncher =
    rememberLauncherForActivityResult(
      ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
      val micGranted =
        result[Manifest.permission.RECORD_AUDIO] == true ||
          ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
          ) == PackageManager.PERMISSION_GRANTED

      if (micGranted) {
        micPermissionDeniedReason = null
        LiveVoiceService.start(context)
      } else {
        micPermissionDeniedReason =
          "Rivia needs the microphone to hear you in Live."
      }
    }

  LaunchedEffect(Unit) {
    val hasMic =
      ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECORD_AUDIO,
      ) == PackageManager.PERMISSION_GRANTED
    val hasNotif =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
          context,
          Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
      } else {
        true
      }

    if (hasMic && hasNotif) {
      micPermissionDeniedReason = null
      LiveVoiceService.start(context)
    } else {
      val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        perms.add(Manifest.permission.POST_NOTIFICATIONS)
      }
      permissionsLauncher.launch(perms.toTypedArray())
    }
  }

  // Staged Live UI reveal: ~500 ms state line + captions, ~750 ms Mute / Pause / End
  val captionsAlpha = remember { Animatable(if (removeAnimations) 1f else 0f) }
  val controlsAlpha = remember { Animatable(if (removeAnimations) 1f else 0f) }
  LaunchedEffect(removeAnimations) {
    if (removeAnimations) {
      captionsAlpha.snapTo(1f)
      controlsAlpha.snapTo(1f)
    } else {
      val easeOut = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f)
      coroutineScope {
        launch {
          delay(500L)
          captionsAlpha.animateTo(1f, tween(durationMillis = 220, easing = easeOut))
        }
        launch {
          delay(750L)
          controlsAlpha.animateTo(1f, tween(durationMillis = 220, easing = easeOut))
        }
      }
    }
  }

  val edgeGlowTransition = rememberInfiniteTransition(label = "edge_glow")
  val edgeGlowPulse by
    edgeGlowTransition.animateFloat(
      initialValue = 0.25f,
      targetValue = 0.70f,
      animationSpec =
        infiniteRepeatable(
          animation = tween(durationMillis = 800),
          repeatMode = RepeatMode.Reverse,
        ),
      label = "edge_glow_alpha",
    )

  val isSpeaking = liveState.orbState == OrbState.SPEAKING
  val edgeGlowEnvelope by
    animateFloatAsState(
      targetValue = if (isSpeaking) 1f else 0f,
      animationSpec =
        if (removeAnimations) snap()
        else tween(durationMillis = 320, easing = FastOutSlowInEasing),
      label = "live_edge_glow_envelope",
    )

  BoxWithConstraints(
    modifier =
      Modifier.fillMaxSize()
        .let { base ->
          if (particleController == null) base.background(RiviaBackground) else base
        }
        .drawBehind {
          val effAlpha = edgeGlowPulse * edgeGlowEnvelope
          if (effAlpha > 0.004f) {
            val strokePx = 14.dp.toPx()
            drawRect(
              brush =
                Brush.radialGradient(
                  colors =
                    listOf(
                      Color.Transparent,
                      RiviaMint.copy(alpha = effAlpha * 0.45f),
                    ),
                  radius = size.maxDimension * 0.72f,
                )
            )
            drawRect(
              color = RiviaMint.copy(alpha = effAlpha),
              style = Stroke(width = strokePx),
            )
          }
        }
        .statusBarsPadding()
        .navigationBarsPadding()
        .padding(20.dp)
  ) {
    val viewportScale =
      remember(maxWidth, maxHeight) {
        ViewportScale.fromPaneDp(maxWidth.value, maxHeight.value)
      }

    SideEffect {
      particleController?.let { ctrl ->
        ctrl.viewportScale = viewportScale
        ctrl.chatBarFillAlpha = 0f
        ctrl.dictationPanelFillAlpha = 0f
        ctrl.liveOrbState = liveState.orbState
        ctrl.liveMicLevel = liveMicLevel
        ctrl.livePlaybackLevel = playbackAudioLevel
        ctrl.isLiveSpeakingFallback = isAudioSpeaking || isSpeaking
        ctrl.updateWantDrawerOpen(false, removeAnimations)
        ctrl.updateDesiredMode(ParticleSceneMode.LIVE)
      }
    }

    // Top Header Info (~500 ms state line fade-in)
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .align(Alignment.TopCenter)
          .graphicsLayer { alpha = captionsAlpha.value },
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        StatusShape(
          status =
            when {
              micPermissionDeniedReason != null || liveState.errorCaption != null ->
                StatusType.ERROR
              liveState.isPaused || liveState.isMuted || liveState.pausedForPhoneCall ->
                StatusType.WARNING
              else -> StatusType.OK
            },
          size = 10.dp,
        )
        Text(
          text =
            when {
              micPermissionDeniedReason != null -> "Microphone needed"
              liveState.pausedForPhoneCall -> "Paused for your call"
              liveState.stoppedAfterThreeErrors -> "Paused after errors. Tap the orb to try again."
              liveState.isPaused -> "Paused"
              liveState.isMuted -> "Muted"
              liveState.orbState == OrbState.SPEAKING -> "Rivia is speaking"
              liveState.orbState == OrbState.THINKING -> "Rivia is thinking…"
              liveState.orbState == OrbState.LISTENING -> "Listening…"
              else -> "Live"
            },
          style = MaterialTheme.typography.titleMedium,
          color = RiviaText,
        )
      }

      Text(
        text = "${runSettings.liveModel} · ${voicePrefs.liveModelId}",
        style = MaterialTheme.typography.labelSmall,
        fontFamily = ChivoMonoFontFamily,
        color = RiviaTextTertiary,
      )
    }

    // Center Interactive Orb & Captions
    Column(
      modifier = Modifier.fillMaxWidth().align(Alignment.Center),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
      if (micPermissionDeniedReason != null) {
        Surface(
          shape = RoundedCornerShape(14.dp),
          color = RiviaRaisedSurface,
          border = BorderStroke(1.dp, RiviaErrorRed),
          modifier =
            Modifier.widthIn(max = 420.dp)
              .graphicsLayer { alpha = captionsAlpha.value },
        ) {
          Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            StatusShape(status = StatusType.ERROR, size = 14.dp)
            Text(
              text = micPermissionDeniedReason!!,
              style = MaterialTheme.typography.bodyMedium,
              color = RiviaErrorRed,
              textAlign = TextAlign.Center,
            )
            Button(
              onClick = {
                permissionsLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
              },
              colors =
                ButtonDefaults.buttonColors(
                  containerColor = RiviaMint,
                  contentColor = RiviaOnAccent,
                ),
            ) {
              Text("Grant Microphone Permission")
            }
          }
        }
      } else {
        // Shared global sphere slot: tapping while she speaks stops her and starts listening
        RiviaHomeParticleSphere(
          contentDesc = "Tap to stop Rivia and listen",
          onSphereClick = {
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            LiveSessionManager.onOrbTapped()
          },
        )

        Column(
          modifier =
            Modifier.widthIn(max = 520.dp)
              .fillMaxWidth()
              .graphicsLayer { alpha = captionsAlpha.value },
          verticalArrangement = Arrangement.spacedBy(12.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Text(
            text =
              if (isSpeaking) "Talk over her or tap the orb to cut in"
              else if (liveState.stoppedAfterThreeErrors) "Tap the orb to try again"
              else "Just talk",
            style = MaterialTheme.typography.labelMedium,
            color = RiviaMutedText,
          )

          if (liveState.userCaption.isNotBlank()) {
            Surface(
              shape = RoundedCornerShape(12.dp),
              color = RiviaSurface3,
              border = BorderStroke(1.dp, RiviaBorder),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                Text(
                  text = "YOU",
                  style = MaterialTheme.typography.labelSmall,
                  fontFamily = ChivoMonoFontFamily,
                  color = RiviaTextTertiary,
                )
                Text(
                  text = liveState.userCaption,
                  style = MaterialTheme.typography.bodyLarge,
                  color = RiviaText,
                )
              }
            }
          }

          if (liveState.riviaCaption.isNotBlank()) {
            Surface(
              shape = RoundedCornerShape(12.dp),
              color = RiviaSurface2,
              border = BorderStroke(1.dp, RiviaBorder),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                Text(
                  text = "RIVIA",
                  style = MaterialTheme.typography.labelSmall,
                  fontFamily = ChivoMonoFontFamily,
                  color = RiviaTextSecondary,
                )
                Text(
                  text = liveState.riviaCaption,
                  style = MaterialTheme.typography.bodyLarge,
                  color = RiviaText,
                )
              }
            }
          }

          if (!liveState.noticeCaption.isNullOrBlank()) {
            Surface(
              shape = RoundedCornerShape(12.dp),
              color = RiviaRaisedSurface,
              border = BorderStroke(1.dp, RiviaWarningAmber.copy(alpha = 0.6f)),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                StatusShape(status = StatusType.WARNING, size = 10.dp)
                Text(
                  text = liveState.noticeCaption!!,
                  style = MaterialTheme.typography.bodySmall,
                  color = RiviaWarningAmber,
                )
              }
            }
          }

          if (!liveState.errorCaption.isNullOrBlank()) {
            Surface(
              shape = RoundedCornerShape(12.dp),
              color = RiviaRaisedSurface,
              border = BorderStroke(1.dp, RiviaErrorRed),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                StatusShape(status = StatusType.ERROR, size = 10.dp)
                Text(
                  text = liveState.errorCaption!!,
                  style = MaterialTheme.typography.bodySmall,
                  color = RiviaErrorRed,
                )
              }
            }
          }
        }
      }
    }

    // Bottom Controls: Mute, Pause, and End buttons (~750 ms fade-in, usable as soon as fade starts)
    val controlsUsable = controlsAlpha.value > 0.01f
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .align(Alignment.BottomCenter)
          .graphicsLayer { alpha = controlsAlpha.value },
      horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      // Mute Button
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
          enabled = controlsUsable,
          onClick = {
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            LiveSessionManager.toggleMute()
          },
          modifier =
            Modifier.size(60.dp)
              .clip(CircleShape)
              .background(if (liveState.isMuted) RiviaWarningAmber else RiviaSurface2)
              .border(1.dp, RiviaBorder, CircleShape)
              .testTag("live_mute_button"),
        ) {
          Icon(
            imageVector =
              if (liveState.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
            contentDescription = if (liveState.isMuted) "Unmute microphone" else "Mute microphone",
            tint = if (liveState.isMuted) RiviaOnAccent else RiviaIconDefault,
          )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
          text = if (liveState.isMuted) "Unmute mic" else "Mute mic",
          style = MaterialTheme.typography.labelSmall,
          color = RiviaMutedText,
        )
      }

      // Pause Button
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
          enabled = controlsUsable,
          onClick = {
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            LiveSessionManager.togglePause()
          },
          modifier =
            Modifier.size(60.dp)
              .clip(CircleShape)
              .background(if (liveState.isPaused) RiviaSurface3 else RiviaSurface2)
              .border(1.dp, RiviaBorder, CircleShape)
              .testTag("live_pause_button"),
        ) {
          Icon(
            imageVector =
              if (liveState.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
            contentDescription = if (liveState.isPaused) "Resume Live session" else "Pause Live session",
            tint = if (liveState.isPaused) RiviaIconActive else RiviaIconDefault,
          )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
          text = if (liveState.isPaused) "Resume Live" else "Pause Live",
          style = MaterialTheme.typography.labelSmall,
          color = RiviaMutedText,
        )
      }

      // End Button
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
          enabled = controlsUsable,
          onClick = {
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            LiveVoiceService.stop(context)
            onEndLive()
          },
          modifier =
            Modifier.size(60.dp)
              .clip(CircleShape)
              .background(RiviaErrorRed)
              .testTag("live_end_button"),
        ) {
          Icon(
            imageVector = Icons.Default.CallEnd,
            contentDescription = "End Live",
            tint = RiviaOnAccent,
          )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
          text = "End",
          style = MaterialTheme.typography.labelSmall,
          color = RiviaErrorRed,
          fontWeight = FontWeight.SemiBold,
        )
      }
    }
  }
}
