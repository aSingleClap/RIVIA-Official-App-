package com.example.schedule

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * 15-minute WorkManager fallback check (Step 9):
 * Takes a single location fix (no location trail), checks registered zones, and logs
 * missed ENTER/EXIT events marked "fallback".
 */
class ZoneFallbackWorker(
  appContext: Context,
  params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
  override suspend fun doWork(): Result {
    return try {
      ZoneLocationCoordinator.runSingleLocationFallbackCheck(applicationContext)
      Result.success()
    } catch (e: Throwable) {
      Log.w("ZoneFallbackWorker", "Fallback location check failed: ${e.message}")
      Result.retry()
    }
  }
}

/**
 * Daily WorkManager job (Step 5):
 * Re-pulls app_places from the tablet once a day and re-registers geofences so a radius or
 * pin edit on the tablet takes effect without opening the app.
 */
class DailyZoneRefreshWorker(
  appContext: Context,
  params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
  override suspend fun doWork(): Result {
    return try {
      ZoneLocationCoordinator.syncPlacesAndRegisterGeofences(
        context = applicationContext,
        reason = "daily_worker",
      )
      ZoneLocationCoordinator.flushPendingArrivalCalls(applicationContext)
      Result.success()
    } catch (e: Throwable) {
      Log.w("DailyZoneRefreshWorker", "Daily zone refresh failed: ${e.message}")
      Result.retry()
    }
  }
}

/**
 * One-shot network-constrained WorkManager job (Step 8):
 * Flushes offline-queued app_arrival calls as soon as network connectivity is available.
 */
class FlushPendingArrivalsWorker(
  appContext: Context,
  params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
  override suspend fun doWork(): Result {
    return try {
      ZoneLocationCoordinator.flushPendingArrivalCalls(applicationContext)
      val remaining =
        com.example.data.RiviaDatabase
          .getInstance(applicationContext)
          .riviaDao()
          .getAllPendingArrivalCalls()
      if (remaining.isEmpty()) Result.success() else Result.retry()
    } catch (e: Throwable) {
      Log.w("FlushArrivalsWorker", "Flush pending arrivals failed: ${e.message}")
      Result.retry()
    }
  }
}
