package com.example.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.audio.AudioPlayerManager
import com.example.data.RiviaDatabase
import com.example.service.LiveSessionManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Manifest-registered receiver that fires when a scheduled leave-by AlarmClock triggers.
 * 1. Posts a high-importance notification with sound on rivia_schedule_alerts.
 * 2. Removes the fired alert from Room.
 * 3. Speaks fallback_text via the existing AudioPlayerManager / ElevenLabs path.
 */
class ScheduleAlarmReceiver : BroadcastReceiver() {
  companion object {
    const val TAG = "ScheduleAlarmReceiver"
    const val ACTION_FIRE_SCHEDULE_ALERT = "observer.rivia.app.action.FIRE_SCHEDULE_ALERT"
    const val EXTRA_ALERT_ID = "extra_alert_id"
    const val EXTRA_FALLBACK_TEXT = "extra_fallback_text"
    const val EXTRA_LOCAL_TIME = "extra_local_time"
    const val EXTRA_SPOKEN_TIME = "extra_spoken_time"
  }

  override fun onReceive(context: Context, intent: Intent?) {
    if (intent?.action != ACTION_FIRE_SCHEDULE_ALERT) return

    val appCtx = context.applicationContext
    val alertId = intent.getStringExtra(EXTRA_ALERT_ID)?.trim().orEmpty().ifBlank { "schedule-alert" }
    val intentFallbackText = intent.getStringExtra(EXTRA_FALLBACK_TEXT)?.trim().orEmpty()
    val intentSpokenTime = intent.getStringExtra(EXTRA_SPOKEN_TIME)?.trim().orEmpty()

    val pendingResult = goAsync()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    scope.launch {
      try {
        var resolvedFallbackText = intentFallbackText
        var resolvedSpokenTime = intentSpokenTime

        if (ScheduleAlarmCoordinator.isUserUnlocked(appCtx)) {
          runCatching {
            val dao = RiviaDatabase.getInstance(appCtx).riviaDao()
            val stored = dao.getScheduleAlertById(alertId)
            if (stored != null) {
              if (resolvedFallbackText.isBlank()) {
                resolvedFallbackText = stored.fallbackText
              }
              if (resolvedSpokenTime.isBlank()) {
                resolvedSpokenTime = stored.spokenTime
              }
              dao.deleteScheduleAlertById(alertId)
            }
          }
        }

        val finalLine =
          resolvedFallbackText.ifBlank { "Leave-by alert from Rivia." }

        // Always post the high-importance notification immediately, even if speech fails
        ScheduleAlarmCoordinator.postAlertNotification(
          context = appCtx,
          alertId = alertId,
          fallbackText = finalLine,
          spokenTime = resolvedSpokenTime,
        )

        // Speak the fallback_text using the existing AudioPlayerManager / ElevenLabs stack
        if (ScheduleAlarmCoordinator.isUserUnlocked(appCtx)) {
          runCatching {
            val playerManager = AudioPlayerManager.getInstance(appCtx)
            val liveActive = LiveSessionManager.uiState.value.isActive
            val alreadySpeaking = playerManager.isSpeaking.value

            if (liveActive || alreadySpeaking) {
              playerManager.enqueueOrSpeakLineWithoutInterrupting(
                messageId = "alarm:$alertId",
                rawMarkdown = finalLine,
                isLiveChannel = liveActive,
              )
            } else {
              val speechDone = CompletableDeferred<Unit>()
              playerManager.speakMarkdownMessage(
                messageId = "alarm:$alertId",
                rawMarkdown = finalLine,
                isLiveChannel = false,
                onFinished = { speechDone.complete(Unit) },
                onError = { speechDone.complete(Unit) },
              )
              // Hold the BroadcastReceiver goAsync window briefly so ElevenLabs synthesis & playback can run
              withTimeoutOrNull(8_500L) {
                speechDone.await()
              }
            }
          }.onFailure { err ->
            Log.w(TAG, "ElevenLabs speech skipped/failed for alert $alertId: ${err.message}")
          }
        }
      } catch (e: Throwable) {
        Log.e(TAG, "Error handling fired schedule alarm $alertId: ${e.message}")
      } finally {
        runCatching { pendingResult.finish() }
      }
    }
  }
}
