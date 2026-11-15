package com.example.schedule

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Re-arms pending schedule alerts from Room on:
 * - android.intent.action.BOOT_COMPLETED
 * - android.intent.action.LOCKED_BOOT_COMPLETED (registers dynamic unlock listener until unlocked)
 * - android.intent.action.USER_UNLOCKED
 * - android.intent.action.MY_PACKAGE_REPLACED
 * - android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
 */
class ScheduleBootReceiver : BroadcastReceiver() {
  companion object {
    const val TAG = "ScheduleBootReceiver"
    private val unlockReceiverRegistered = AtomicBoolean(false)

    fun registerDynamicUnlockReceiverIfLocked(context: Context) {
      val appCtx = context.applicationContext
      if (ScheduleAlarmCoordinator.isUserUnlocked(appCtx)) return
      if (!unlockReceiverRegistered.compareAndSet(false, true)) return

      runCatching {
        val unlockReceiver =
          object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent?) {
              if (intent?.action == Intent.ACTION_USER_UNLOCKED) {
                runCatching { appCtx.unregisterReceiver(this) }
                unlockReceiverRegistered.set(false)
                val pending = goAsync()
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                  try {
                    ScheduleAlarmCoordinator.rearmPendingAlertsFromRoom(appCtx)
                  } finally {
                    runCatching { pending.finish() }
                  }
                }
              }
            }
          }
        ContextCompat.registerReceiver(
          appCtx,
          unlockReceiver,
          IntentFilter(Intent.ACTION_USER_UNLOCKED),
          ContextCompat.RECEIVER_NOT_EXPORTED,
        )
      }
    }
  }

  override fun onReceive(context: Context, intent: Intent?) {
    val action = intent?.action ?: return
    val isRelevant =
      action == Intent.ACTION_BOOT_COMPLETED ||
        action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
        action == Intent.ACTION_USER_UNLOCKED ||
        action == Intent.ACTION_MY_PACKAGE_REPLACED ||
        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
          action == AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED)
    if (!isRelevant) return

    val appCtx = context.applicationContext
    if (!ScheduleAlarmCoordinator.isUserUnlocked(appCtx)) {
      Log.i(TAG, "Received $action while user is locked; waiting for ACTION_USER_UNLOCKED")
      registerDynamicUnlockReceiverIfLocked(appCtx)
      return
    }

    val pendingResult = goAsync()
    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
      try {
        ScheduleAlarmCoordinator.rearmPendingAlertsFromRoom(appCtx)
      } catch (e: Throwable) {
        Log.e(TAG, "Failed to re-arm alarms on $action: ${e.message}")
      } finally {
        runCatching { pendingResult.finish() }
      }
    }
  }
}
