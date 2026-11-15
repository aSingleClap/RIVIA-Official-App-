package com.example.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Manifest-registered BroadcastReceiver for Google Play Services GeofencingClient transitions.
 */
class GeofenceBroadcastReceiver : BroadcastReceiver() {
  companion object {
    const val TAG = "GeofenceReceiver"
  }

  override fun onReceive(context: Context, intent: Intent?) {
    if (intent == null) return
    val appCtx = context.applicationContext
    val geofencingEvent = GeofencingEvent.fromIntent(intent) ?: return

    val pendingResult = goAsync()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    scope.launch {
      try {
        if (geofencingEvent.hasError()) {
          val errorCode = geofencingEvent.errorCode
          val errStr = GeofenceStatusCodes.getStatusCodeString(errorCode)
          Log.w(TAG, "GeofencingEvent error: $errorCode ($errStr)")
          if (errorCode == GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE) {
            ZoneLocationCoordinator.onGeofenceNotAvailable(appCtx)
          }
          return@launch
        }

        val transitionType = geofencingEvent.geofenceTransition
        val transitionLabel =
          when (transitionType) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> "ENTER"
            Geofence.GEOFENCE_TRANSITION_EXIT -> "EXIT"
            else -> return@launch
          }

        val triggering = geofencingEvent.triggeringGeofences.orEmpty()
        val eventTimeMs =
          geofencingEvent.triggeringLocation?.time?.takeIf { it > 0L }
            ?: System.currentTimeMillis()

        for (fence in triggering) {
          val placeId = fence.requestId?.trim().orEmpty()
          if (placeId.isNotBlank()) {
            ZoneLocationCoordinator.handleZoneTransition(
              context = appCtx,
              placeId = placeId,
              transition = transitionLabel,
              eventTimeMs = eventTimeMs,
              source = "geofence",
            )
          }
        }
      } catch (e: Throwable) {
        Log.e(TAG, "Error handling geofence transition: ${e.message}")
      } finally {
        runCatching { pendingResult.finish() }
      }
    }
  }
}
