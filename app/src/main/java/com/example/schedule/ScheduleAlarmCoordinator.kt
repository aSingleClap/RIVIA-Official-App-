package com.example.schedule

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.UserManager
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.RiviaDatabase
import com.example.data.ScheduleAlertEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

sealed class ParsedAlertsEnvelope {
  data object Untouched : ParsedAlertsEnvelope()

  data class ReplaceAll(val alerts: List<ScheduleAlertEntity>) : ParsedAlertsEnvelope()
}

data class ApplyAlertsResult(
  val changed: Boolean,
  val scheduledCount: Int,
  val exactAlarmPermissionMissing: Boolean,
  val alerts: List<ScheduleAlertEntity> = emptyList(),
  val summary: String,
)

/**
 * Coordinates exact leave-by alarms (AlarmManager.setAlarmClock), the high-importance
 * rivia_schedule_alerts notification channel, Room persistence, and reboot/update re-arming.
 */
object ScheduleAlarmCoordinator {
  const val TAG = "ScheduleAlarmCoord"
  const val SCHEDULE_ALERTS_CHANNEL_ID = "rivia_schedule_alerts"
  const val SCHEDULE_ALERTS_CHANNEL_NAME = "Leave-by alerts"
  private const val NOTIFICATION_BASE_ID = 7200

  fun isUserUnlocked(context: Context): Boolean {
    return runCatching {
      val um = context.getSystemService(UserManager::class.java)
      um?.isUserUnlocked ?: true
    }.getOrDefault(true)
  }

  fun canScheduleExactAlarms(context: Context): Boolean {
    return runCatching {
      val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
      am?.canScheduleExactAlarms() ?: false
    }.getOrDefault(false)
  }

  fun hasPostNotificationsPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS,
      ) == PackageManager.PERMISSION_GRANTED
    } else {
      true
    }
  }

  fun buildRequestExactAlarmPermissionIntent(context: Context): Intent {
    return Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
      data = Uri.parse("package:${context.packageName}")
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
  }

  fun ensureNotificationChannel(context: Context) {
    runCatching {
      val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
      val soundUri =
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
          ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
      val audioAttrs =
        AudioAttributes.Builder()
          .setUsage(AudioAttributes.USAGE_ALARM)
          .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
          .build()
      val channel =
        NotificationChannel(
          SCHEDULE_ALERTS_CHANNEL_ID,
          SCHEDULE_ALERTS_CHANNEL_NAME,
          NotificationManager.IMPORTANCE_HIGH,
        ).apply {
          description = "Leave-by and schedule alerts from Rivia"
          enableVibration(true)
          setShowBadge(true)
          lockscreenVisibility = Notification.VISIBILITY_PUBLIC
          if (soundUri != null) {
            setSound(soundUri, audioAttrs)
          }
        }
      nm.createNotificationChannel(channel)
    }
  }

  fun stableRequestCodeForAlertId(alertId: String): Int {
    return NOTIFICATION_BASE_ID + ((alertId.hashCode() and 0x7FFFFFFF) % 1_000_000)
  }

  private fun buildAlarmReceiverIntent(
    context: Context,
    alertId: String,
    fallbackText: String = "",
    localTime: String = "",
    spokenTime: String = "",
  ): Intent {
    return Intent(context.applicationContext, ScheduleAlarmReceiver::class.java).apply {
      action = ScheduleAlarmReceiver.ACTION_FIRE_SCHEDULE_ALERT
      data = Uri.parse("rivia://schedule_alert/${Uri.encode(alertId)}")
      putExtra(ScheduleAlarmReceiver.EXTRA_ALERT_ID, alertId)
      putExtra(ScheduleAlarmReceiver.EXTRA_FALLBACK_TEXT, fallbackText)
      putExtra(ScheduleAlarmReceiver.EXTRA_LOCAL_TIME, localTime)
      putExtra(ScheduleAlarmReceiver.EXTRA_SPOKEN_TIME, spokenTime)
    }
  }

  fun cancelAlarmById(context: Context, alertId: String) {
    runCatching {
      val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
      val intent = buildAlarmReceiverIntent(context, alertId)
      val pi =
        PendingIntent.getBroadcast(
          context.applicationContext,
          stableRequestCodeForAlertId(alertId),
          intent,
          PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
      am.cancel(pi)
      pi.cancel()
    }
  }

  fun armSingleAlertWithAlarmClock(context: Context, alert: ScheduleAlertEntity): Boolean {
    return runCatching {
      ensureNotificationChannel(context)
      val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
      if (!am.canScheduleExactAlarms()) {
        Log.w(TAG, "Cannot schedule exact alarm for ${alert.id}: exact alarm permission not granted")
        return false
      }

      val reqCode = stableRequestCodeForAlertId(alert.id)
      val showAppIntent =
        Intent(context.applicationContext, MainActivity::class.java).apply {
          flags =
            Intent.FLAG_ACTIVITY_NEW_TASK or
              Intent.FLAG_ACTIVITY_CLEAR_TOP or
              Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
      val showPendingIntent =
        PendingIntent.getActivity(
          context.applicationContext,
          reqCode,
          showAppIntent,
          PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

      val fireIntent =
        buildAlarmReceiverIntent(
          context = context,
          alertId = alert.id,
          fallbackText = alert.fallbackText,
          localTime = alert.localTime,
          spokenTime = alert.spokenTime,
        )
      val operationPendingIntent =
        PendingIntent.getBroadcast(
          context.applicationContext,
          reqCode,
          fireIntent,
          PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

      val clockInfo = AlarmManager.AlarmClockInfo(alert.triggerAtMs, showPendingIntent)
      am.setAlarmClock(clockInfo, operationPendingIntent)
      Log.i(
        TAG,
        "Armed exact AlarmClock id=${alert.id} triggerAtMs=${alert.triggerAtMs} local=${alert.localTime}",
      )
      true
    }.getOrElse { err ->
      Log.e(TAG, "Failed to arm AlarmClock for ${alert.id}: ${err.message}")
      false
    }
  }

  fun postAlertNotification(
    context: Context,
    alertId: String,
    fallbackText: String,
    spokenTime: String = "",
  ) {
    runCatching {
      ensureNotificationChannel(context)
      if (!hasPostNotificationsPermission(context)) {
        Log.w(TAG, "POST_NOTIFICATIONS not granted; skipping visible alert notification for $alertId")
        return
      }
      val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
      val reqCode = stableRequestCodeForAlertId(alertId)
      val openIntent =
        Intent(context.applicationContext, MainActivity::class.java).apply {
          flags =
            Intent.FLAG_ACTIVITY_NEW_TASK or
              Intent.FLAG_ACTIVITY_CLEAR_TOP or
              Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
      val contentPendingIntent =
        PendingIntent.getActivity(
          context.applicationContext,
          reqCode,
          openIntent,
          PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
      val soundUri =
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
          ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
      val title =
        if (spokenTime.isNotBlank()) "Rivia · Leave-by ($spokenTime)"
        else "Rivia · Leave-by alert"
      val text = fallbackText.ifBlank { "Leave-by alert from Rivia." }

      val notification =
        NotificationCompat.Builder(context.applicationContext, SCHEDULE_ALERTS_CHANNEL_ID)
          .setSmallIcon(R.mipmap.ic_launcher)
          .setContentTitle(title)
          .setContentText(text)
          .setStyle(NotificationCompat.BigTextStyle().bigText(text))
          .setPriority(NotificationCompat.PRIORITY_HIGH)
          .setCategory(NotificationCompat.CATEGORY_ALARM)
          .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
          .setDefaults(NotificationCompat.DEFAULT_ALL)
          .apply {
            if (soundUri != null) {
              setSound(soundUri)
            }
          }
          .setContentIntent(contentPendingIntent)
          .setAutoCancel(true)
          .build()

      nm.notify(reqCode, notification)
    }
  }

  /**
   * Parses the tablet structuredContent envelope according to App Pass B rules:
   * - If ok is false, do not change alarms -> Untouched
   * - If alerts field is missing or JSON null, leave current alarms alone -> Untouched
   * - If alerts is an empty list, cancel every Rivia leave-by alarm and clear from Room -> ReplaceAll(emptyList())
   * - If alerts is a list, replace all current Rivia leave-by alarms with that list -> ReplaceAll(parsedList)
   */
  fun parseAlertsFromStructuredEnvelope(structured: JSONObject): ParsedAlertsEnvelope {
    if (structured.has("ok") && !structured.optBoolean("ok", false)) {
      return ParsedAlertsEnvelope.Untouched
    }
    if (!structured.has("alerts") || structured.isNull("alerts")) {
      return ParsedAlertsEnvelope.Untouched
    }
    val alertsArr = structured.optJSONArray("alerts") ?: return ParsedAlertsEnvelope.Untouched
    if (alertsArr.length() == 0) {
      return ParsedAlertsEnvelope.ReplaceAll(emptyList())
    }

    val topFallback = structured.optString("fallback_text", "").trim()
    val parsed = mutableListOf<ScheduleAlertEntity>()
    val nowMs = System.currentTimeMillis()

    for (i in 0 until alertsArr.length()) {
      val item = alertsArr.optJSONObject(i) ?: continue
      val id = item.optString("id", "").trim()
      if (id.isBlank()) continue

      val atObj = item.optJSONObject("at")
      val rawEpoch = atObj?.optDouble("epoch", 0.0) ?: 0.0
      if (rawEpoch <= 0.0 || rawEpoch.isNaN()) continue

      val epochSeconds: Long
      val triggerAtMs: Long
      if (rawEpoch >= 100_000_000_000.0) {
        triggerAtMs = rawEpoch.toLong()
        epochSeconds = triggerAtMs / 1000L
      } else {
        epochSeconds = atObj?.optLong("epoch", 0L) ?: rawEpoch.toLong()
        triggerAtMs = (rawEpoch * 1000.0).toLong().takeIf { it > 0L } ?: (epochSeconds * 1000L)
      }
      if (triggerAtMs <= 0L) continue

      val localTime = atObj?.optString("local", "")?.trim().orEmpty()
      val spokenTime = atObj?.optString("spoken", "")?.trim().orEmpty()
      val isoTime = atObj?.optString("iso", "")?.trim().orEmpty()
      val fallbackText =
        item.optString("fallback_text", "").trim().ifBlank {
          topFallback.ifBlank { "Leave-by alert from Rivia." }
        }
      val messageJson =
        item.optJSONObject("message")?.toString()
          ?: item.optString("message", "").takeIf { it.isNotBlank() }
          ?: "{}"

      parsed.add(
        ScheduleAlertEntity(
          id = id,
          epochSeconds = epochSeconds,
          triggerAtMs = triggerAtMs,
          localTime = localTime,
          spokenTime = spokenTime,
          isoTime = isoTime,
          fallbackText = fallbackText,
          messageJson = messageJson,
          createdAt = nowMs,
        )
      )
    }

    return ParsedAlertsEnvelope.ReplaceAll(parsed)
  }

  /**
   * Shared function to apply the `alerts` field from any successful tablet tool reply.
   */
  suspend fun applyAlertsFromTabletReply(
    context: Context,
    structured: JSONObject,
  ): ApplyAlertsResult =
    withContext(Dispatchers.IO) {
      val appCtx = context.applicationContext
      ensureNotificationChannel(appCtx)

      when (val parsed = parseAlertsFromStructuredEnvelope(structured)) {
        is ParsedAlertsEnvelope.Untouched -> {
          ApplyAlertsResult(
            changed = false,
            scheduledCount = 0,
            exactAlarmPermissionMissing = false,
            summary = "Alarms unchanged",
          )
        }
        is ParsedAlertsEnvelope.ReplaceAll -> {
          if (!isUserUnlocked(appCtx)) {
            return@withContext ApplyAlertsResult(
              changed = false,
              scheduledCount = 0,
              exactAlarmPermissionMissing = false,
              summary = "Device locked; cannot update alarms yet",
            )
          }
          val dao = RiviaDatabase.getInstance(appCtx).riviaDao()
          val existing = dao.getAllScheduleAlerts()

          // Cancel every existing Rivia leave-by alarm
          for (oldAlert in existing) {
            cancelAlarmById(appCtx, oldAlert.id)
          }

          val newAlerts = parsed.alerts
          if (newAlerts.isEmpty()) {
            dao.clearAllScheduleAlerts()
            return@withContext ApplyAlertsResult(
              changed = true,
              scheduledCount = 0,
              exactAlarmPermissionMissing = false,
              alerts = emptyList(),
              summary = "Cleared all leave-by alarms (0 active)",
            )
          }

          // Cancel any matching IDs in newAlerts before setting fresh ones
          for (alert in newAlerts) {
            cancelAlarmById(appCtx, alert.id)
          }

          // Replace in Room so pending alerts survive process death, reboot, or app update
          dao.replaceAllScheduleAlerts(newAlerts)

          if (!canScheduleExactAlarms(appCtx)) {
            return@withContext ApplyAlertsResult(
              changed = true,
              scheduledCount = 0,
              exactAlarmPermissionMissing = true,
              alerts = newAlerts,
              summary = "Exact alarm permission required in system settings",
            )
          }

          val nowMs = System.currentTimeMillis()
          var armedCount = 0
          for (alert in newAlerts) {
            if (alert.triggerAtMs > nowMs - 5_000L) {
              if (armSingleAlertWithAlarmClock(appCtx, alert)) {
                armedCount++
              }
            } else {
              dao.deleteScheduleAlertById(alert.id)
            }
          }

          val detail =
            newAlerts.joinToString(", ") { a ->
              val label = a.spokenTime.ifBlank { a.localTime.ifBlank { "${a.epochSeconds}" } }
              "${a.id} ($label)"
            }
          ApplyAlertsResult(
            changed = true,
            scheduledCount = armedCount,
            exactAlarmPermissionMissing = false,
            alerts = newAlerts,
            summary = "Armed $armedCount alert(s): $detail",
          )
        }
      }
    }

  /**
   * Re-arms all future alerts stored in Room after BOOT_COMPLETED, USER_UNLOCKED, MY_PACKAGE_REPLACED,
   * or after exact alarm permission is granted. Drops alerts whose time has already passed.
   */
  suspend fun rearmPendingAlertsFromRoom(context: Context): Int =
    withContext(Dispatchers.IO) {
      val appCtx = context.applicationContext
      if (!isUserUnlocked(appCtx)) {
        Log.i(TAG, "User is still locked; deferring alarm re-arm until ACTION_USER_UNLOCKED")
        return@withContext 0
      }

      ensureNotificationChannel(appCtx)
      val dao = RiviaDatabase.getInstance(appCtx).riviaDao()
      val nowMs = System.currentTimeMillis()
      val allSaved = dao.getAllScheduleAlerts()
      if (allSaved.isEmpty()) return@withContext 0

      // Cancel and drop alerts whose time has already passed
      for (alert in allSaved) {
        if (alert.triggerAtMs <= nowMs) {
          cancelAlarmById(appCtx, alert.id)
        }
      }
      dao.deleteExpiredScheduleAlerts(nowMs)

      if (!canScheduleExactAlarms(appCtx)) {
        Log.w(TAG, "Exact alarm permission missing during re-arm; keeping future alerts in Room")
        return@withContext 0
      }

      val futureAlerts = dao.getAllScheduleAlerts()
      var armedCount = 0
      for (alert in futureAlerts) {
        if (alert.triggerAtMs > nowMs) {
          if (armSingleAlertWithAlarmClock(appCtx, alert)) {
            armedCount++
          }
        }
      }
      Log.i(TAG, "Re-armed $armedCount future schedule alert(s) from Room")
      armedCount
    }
}
