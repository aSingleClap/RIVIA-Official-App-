package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.service.LiveSessionManager
import com.example.service.LiveVoiceService
import com.example.ui.components.OrbState
import com.example.ui.components.RiviaOrb
import com.example.ui.components.StatusShape
import com.example.ui.components.StatusType
import com.example.ui.theme.ChivoMonoFontFamily
import com.example.ui.theme.RiviaBackground
import com.example.ui.theme.RiviaBorder
import com.example.ui.theme.RiviaErrorRed
import com.example.ui.theme.RiviaMint
import com.example.ui.theme.RiviaMutedText
import com.example.ui.theme.RiviaRaisedSurface
import com.example.ui.theme.RiviaSurface
import com.example.ui.theme.RiviaText
import com.example.ui.theme.RiviaWarningAmber
import com.example.viewmodel.RiviaViewModel

@Composable
fun LiveModeScreen(
  viewModel: RiviaViewModel,
  onEndLive: () -> Unit,
) {
  val context = LocalContext.current
  val view = LocalView.current
  val liveState by LiveSessionManager.uiState.collectAsStateWithLifecycle()
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

  // Subtle haptics on state transitions (Confirmed Decision 2)
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
        // Correction 8: if RECORD_AUDIO is denied, don't start LiveVoiceService and say why.
        micPermissionDeniedReason =
          "Microphone permission (RECORD_AUDIO) was denied. Live voice mode cannot start without microphone access to hear you."
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

  val edgeGlowTransition = rememberInfiniteTransition(label = "edge_glow")
  val edgeGlowAlpha by
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

  // Full screen with a faint mint glow tracing the screen edges while Rivia speaks
  Box(
    modifier =
      Modifier.fillMaxSize()
        .background(RiviaBackground)
        .drawBehind {
          if (isSpeaking) {
            val strokePx = 14.dp.toPx()
            drawRect(
              brush =
                Brush.radialGradient(
                  colors =
                    listOf(
                      Color.Transparent,
                      RiviaMint.copy(alpha = edgeGlowAlpha * 0.45f),
                    ),
                  radius = size.maxDimension * 0.72f,
                )
            )
            drawRect(
              color = RiviaMint.copy(alpha = edgeGlowAlpha),
              style = Stroke(width = strokePx),
            )
          }
        }
        .statusBarsPadding()
        .navigationBarsPadding()
        .padding(20.dp)
  ) {
    // Top Header Info
    Row(
      modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
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
              micPermissionDeniedReason != null -> "Permission Required"
              liveState.pausedForPhoneCall -> "Paused (Phone Call Active)"
              liveState.stoppedAfterThreeErrors -> "Paused (Tap Orb to Resume)"
              liveState.isPaused -> "Paused"
              liveState.isMuted -> "Muted"
              liveState.orbState == OrbState.SPEAKING -> "Rivia Speaking"
              liveState.orbState == OrbState.THINKING -> "Rivia Thinking..."
              liveState.orbState == OrbState.LISTENING -> "Listening..."
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
        color = RiviaMutedText,
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
          modifier =
            Modifier.widthIn(max = 420.dp)
              .border(1.dp, RiviaErrorRed, RoundedCornerShape(14.dp))
              .padding(18.dp),
        ) {
          Column(
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
                  contentColor = RiviaBackground,
                ),
            ) {
              Text("Grant Microphone Permission")
            }
          }
        }
      } else {
        // Tapping the orb while she speaks stops her and starts listening
        Box(
          contentAlignment = Alignment.Center,
          modifier =
            Modifier.size(210.dp)
              .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
              ) {
                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                LiveSessionManager.onOrbTapped()
              }
              .testTag("live_mode_orb"),
        ) {
          RiviaOrb(
            state = liveState.orbState,
            size = 190.dp,
            contentDesc = "Tap orb to interrupt Rivia and start listening",
          )
        }

        Text(
          text =
            if (isSpeaking) "Tap the orb to interrupt and speak"
            else if (liveState.stoppedAfterThreeErrors) "Tap the orb to resume listening"
            else "Hands-free Live Voice",
          style = MaterialTheme.typography.labelMedium,
          color = RiviaMutedText,
        )

        // Live Captions of my words and hers
        Column(
          modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(12.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          if (liveState.userCaption.isNotBlank()) {
            Surface(
              shape = RoundedCornerShape(12.dp),
              color = RiviaRaisedSurface,
              modifier =
                Modifier.fillMaxWidth()
                  .border(1.dp, RiviaBorder, RoundedCornerShape(12.dp))
                  .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
              Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                  text = "YOU",
                  style = MaterialTheme.typography.labelSmall,
                  fontFamily = ChivoMonoFontFamily,
                  color = RiviaMutedText,
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
              color = RiviaSurface,
              modifier =
                Modifier.fillMaxWidth()
                  .border(1.dp, RiviaMint.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                  .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
              Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                  text = "RIVIA",
                  style = MaterialTheme.typography.labelSmall,
                  fontFamily = ChivoMonoFontFamily,
                  color = RiviaMint,
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
              modifier =
                Modifier.fillMaxWidth()
                  .border(1.dp, RiviaWarningAmber.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                  .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
              Row(
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
              modifier =
                Modifier.fillMaxWidth()
                  .border(1.dp, RiviaErrorRed, RoundedCornerShape(12.dp))
                  .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
              Row(
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

    // Bottom Controls: Mute, Pause, and End buttons
    Row(
      modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
      horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      // Mute Button
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
          onClick = {
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            LiveSessionManager.toggleMute()
          },
          modifier =
            Modifier.size(60.dp)
              .clip(CircleShape)
              .background(if (liveState.isMuted) RiviaWarningAmber else RiviaRaisedSurface)
              .border(1.dp, RiviaBorder, CircleShape)
              .testTag("live_mute_button"),
        ) {
          Icon(
            imageVector =
              if (liveState.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
            contentDescription = if (liveState.isMuted) "Unmute microphone" else "Mute microphone",
            tint = if (liveState.isMuted) RiviaBackground else RiviaText,
          )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
          text = if (liveState.isMuted) "Unmute" else "Mute",
          style = MaterialTheme.typography.labelSmall,
          color = RiviaMutedText,
        )
      }

      // Pause Button
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
          onClick = {
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            LiveSessionManager.togglePause()
          },
          modifier =
            Modifier.size(60.dp)
              .clip(CircleShape)
              .background(if (liveState.isPaused) RiviaMint else RiviaRaisedSurface)
              .border(1.dp, RiviaBorder, CircleShape)
              .testTag("live_pause_button"),
        ) {
          Icon(
            imageVector =
              if (liveState.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
            contentDescription = if (liveState.isPaused) "Resume Live session" else "Pause Live session",
            tint = if (liveState.isPaused) RiviaBackground else RiviaText,
          )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
          text = if (liveState.isPaused) "Resume" else "Pause",
          style = MaterialTheme.typography.labelSmall,
          color = RiviaMutedText,
        )
      }

      // End Button
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
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
            contentDescription = "End Live voice mode",
            tint = RiviaBackground,
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
