package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R

/**
 * Section 6: Single foreground service declared with
 * android:foregroundServiceType="microphone|mediaPlayback",
 * started from the Live screen with an ongoing notification that has Mute and End buttons.
 */
class LiveVoiceService : Service() {
  companion object {
    const val CHANNEL_ID = "rivia_live_voice_channel"
    const val NOTIFICATION_ID = 4101
    const val ACTION_START = "observer.rivia.app.action.START_LIVE"
    const val ACTION_TOGGLE_MUTE = "observer.rivia.app.action.TOGGLE_MUTE"
    const val ACTION_END = "observer.rivia.app.action.END_LIVE"

    fun start(context: Context) {
      try {
        val intent =
          Intent(context, LiveVoiceService::class.java).apply {
            action = ACTION_START
          }
        context.startForegroundService(intent)
      } catch (_: Throwable) {
        // If OS blocks background FGS start, still start the in-process LiveSessionManager
        LiveSessionManager.onServiceStarted()
      }
    }

    fun stop(context: Context) {
      try {
        val intent = Intent(context, LiveVoiceService::class.java)
        context.stopService(intent)
      } catch (_: Throwable) {}
      LiveSessionManager.onServiceStopped()
    }
  }

  override fun onCreate() {
    super.onCreate()
    createNotificationChannel()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_TOGGLE_MUTE -> {
        LiveSessionManager.toggleMute()
        val isMuted = LiveSessionManager.uiState.value.isMuted
        runCatching {
          val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
          nm.notify(NOTIFICATION_ID, buildNotification(isMuted))
        }
      }
      ACTION_END -> {
        LiveSessionManager.onServiceStopped()
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        stopSelf()
      }
      else -> {
        val notification = buildNotification(isMuted = false)
        var startedForeground = false
        try {
          startForeground(
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
              ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
          )
          startedForeground = true
        } catch (_: Throwable) {
          try {
            startForeground(
              NOTIFICATION_ID,
              notification,
              ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
            startedForeground = true
          } catch (_: Throwable) {
            runCatching { stopSelf() }
          }
        }
        LiveSessionManager.onServiceStarted()
        if (!startedForeground) {
          return START_NOT_STICKY
        }
      }
    }
    return START_NOT_STICKY
  }

  override fun onDestroy() {
    LiveSessionManager.onServiceStopped()
    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder? = null

  private fun createNotificationChannel() {
    runCatching {
      val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      val channel =
        NotificationChannel(
          CHANNEL_ID,
          "Rivia Live Voice",
          NotificationManager.IMPORTANCE_LOW,
        ).apply {
          description = "Ongoing hands-free voice session with Rivia"
          setShowBadge(false)
        }
      nm.createNotificationChannel(channel)
    }
  }

  private fun buildNotification(isMuted: Boolean): Notification {
    val openAppIntent =
      Intent(this, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
      }
    val contentPendingIntent =
      PendingIntent.getActivity(
        this,
        0,
        openAppIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val muteIntent =
      Intent(this, LiveVoiceService::class.java).apply {
        action = ACTION_TOGGLE_MUTE
      }
    val mutePendingIntent =
      PendingIntent.getService(
        this,
        1,
        muteIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val endIntent =
      Intent(this, LiveVoiceService::class.java).apply {
        action = ACTION_END
      }
    val endPendingIntent =
      PendingIntent.getService(
        this,
        2,
        endIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val muteLabel = if (isMuted) "Unmute" else "Mute"
    val statusText = if (isMuted) "Microphone muted" else "Listening & speaking hands-free"

    return NotificationCompat.Builder(this, CHANNEL_ID)
      .setSmallIcon(R.drawable.ic_notification_orb)
      .setContentTitle("Rivia Live")
      .setContentText(statusText)
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .setCategory(NotificationCompat.CATEGORY_SERVICE)
      .setContentIntent(contentPendingIntent)
      .addAction(0, muteLabel, mutePendingIntent)
      .addAction(0, "End", endPendingIntent)
      .build()
  }
}
