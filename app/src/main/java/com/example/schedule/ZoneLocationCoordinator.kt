package com.example.schedule

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.audio.AudioPlayerManager
import com.example.data.PendingArrivalCallEntity
import com.example.data.RiviaDatabase
import com.example.data.ZoneConfigEntity
import com.example.data.ZoneEventEntity
import com.example.model.RunSettings
import com.example.network.GeminiInteractionsClient
import com.example.network.GeminiStreamEvent
import com.example.network.TabletCallOutcome
import com.example.network.TabletMcpClient
import com.example.network.TabletToolDef
import com.example.plugin.RiviaChannel
import com.example.security.SecureKeyRepository
import com.example.service.LiveSessionManager
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject

data class CanonicalPlaceSpec(
  val id: String,
  val defaultSpokenName: String,
)

data class ZoneSyncOutcome(
  val ok: Boolean,
  val registeredCount: Int,
  val missingPinIds: List<String>,
  val summary: String,
)

data class PinPlaceOutcome(
  val ok: Boolean,
  val status: String,
  val isPendingApproval: Boolean,
  val ticket: String?,
  val coords: String?,
  val targetLabel: String,
  val fallbackText: String,
  val summary: String,
  val callOutcome: TabletCallOutcome<JSONObject>? = null,
)

/**
 * Coordinates App Pass C & JOB-007:
 * - Permission and battery-optimization checks + intent builders
 * - Pulling all places (5 canonical + custom places) from app_places at runtime
 * - Registering/re-registering Circular Geofences via GeofencingClient (dropping removed place IDs)
 * - 10-minute GPS bounce filter on ENTER after EXIT of the same place
 * - Calling app_arrival on valid ENTER (with ride logging if prior non-bounce EXIT from A != B was within 3h)
 * - Calling app_pin_place with current phone GPS position to move an existing place or add a new place
 * - Applying alerts via ScheduleAlarmCoordinator.applyAlertsFromTabletReply
 * - Speaking the arrival message via Gemini (10s timeout) + AudioPlayerManager / ElevenLabs, or fallback_text
 * - Offline queueing of app_arrival calls in Room + WorkManager flush when connected
 * - Single-fix 15-minute WorkManager location fallback check
 */
object ZoneLocationCoordinator {
  const val TAG = "ZoneLocationCoord"
  const val ACTION_GEOFENCE_TRANSITION = "observer.rivia.app.action.GEOFENCE_TRANSITION"
  private const val GEOFENCE_PENDING_INTENT_REQUEST_CODE = 8410

  const val DEFAULT_RADIUS_METERS = 150f
  const val MIN_RADIUS_METERS = 50f
  const val MAX_RADIUS_METERS = 400f

  // 10-minute GPS bounce window (600,000 ms)
  const val GPS_BOUNCE_WINDOW_MS = 10L * 60L * 1000L

  // 3-hour ride pairing window (10,800,000 ms)
  const val RIDE_MAX_WINDOW_MS = 3L * 60L * 60L * 1000L

  const val WORK_NAME_FALLBACK_15M = "rivia_zone_fallback_15m"
  const val WORK_NAME_DAILY_REFRESH = "rivia_zone_daily_refresh"
  const val WORK_NAME_FLUSH_PENDING_ARRIVALS = "rivia_flush_pending_arrivals"

  const val SAMSUNG_NEVER_SLEEPING_NOTE =
    "Also go to Settings > Battery > Background usage limits > Never sleeping apps, and add Rivia."

  val CANONICAL_PLACES: List<CanonicalPlaceSpec> =
    listOf(
      CanonicalPlaceSpec(id = "home", defaultSpokenName = "home"),
      CanonicalPlaceSpec(id = "farm", defaultSpokenName = "the farm"),
      CanonicalPlaceSpec(id = "gym", defaultSpokenName = "the gym"),
      CanonicalPlaceSpec(id = "pf", defaultSpokenName = "Planet Fitness"),
      CanonicalPlaceSpec(id = "hmc", defaultSpokenName = "work"),
    )

  private val canonicalMap: Map<String, CanonicalPlaceSpec> =
    CANONICAL_PLACES.associateBy { it.id }

  private val tabletMcpClient = TabletMcpClient()
  private val geminiClient = GeminiInteractionsClient()
  private val toolSchemaCache = ConcurrentHashMap<String, JSONObject>()
  private val syncMutex = Mutex()
  private val transitionMutex = Mutex()

  fun canonicalSpokenName(placeId: String): String {
    return canonicalMap[placeId]?.defaultSpokenName ?: placeId
  }

  fun hasForegroundLocationPermission(context: Context): Boolean {
    val fine =
      ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    val coarse =
      ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    return fine && coarse
  }

  fun hasBackgroundLocationPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
      context,
      Manifest.permission.ACCESS_BACKGROUND_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED
  }

  fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    return runCatching {
      val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
      pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false
    }.getOrDefault(false)
  }

  fun isLocationServicesEnabled(context: Context): Boolean {
    return runCatching {
      val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
      lm?.isLocationEnabled ?: true
    }.getOrDefault(true)
  }

  fun buildAppDetailsSettingsIntent(context: Context): Intent {
    return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
      data = Uri.fromParts("package", context.packageName, null)
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
  }

  @SuppressLint("BatteryLife")
  fun buildRequestIgnoreBatteryOptimizationsIntent(context: Context): Intent {
    return Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
      data = Uri.parse("package:${context.packageName}")
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
  }

  private fun buildGeofencePendingIntent(context: Context): PendingIntent {
    val intent =
      Intent(context.applicationContext, GeofenceBroadcastReceiver::class.java).apply {
        action = ACTION_GEOFENCE_TRANSITION
      }
    return PendingIntent.getBroadcast(
      context.applicationContext,
      GEOFENCE_PENDING_INTENT_REQUEST_CODE,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
    )
  }

  /**
   * Ensures the 5 canonical place rows exist in Room so the Dev-mode Zones card
   * always displays all 5 places even before the first tablet sync.
   */
  suspend fun ensureDefaultZoneRowsInRoom(context: Context) =
    withContext(Dispatchers.IO) {
      val appCtx = context.applicationContext
      if (!ScheduleAlarmCoordinator.isUserUnlocked(appCtx)) return@withContext
      val dao = RiviaDatabase.getInstance(appCtx).riviaDao()
      val existing = dao.getAllZoneConfigs().associateBy { it.id }
      val nowMs = System.currentTimeMillis()
      val toInsert = mutableListOf<ZoneConfigEntity>()
      for (spec in CANONICAL_PLACES) {
        if (!existing.containsKey(spec.id)) {
          toInsert.add(
            ZoneConfigEntity(
              id = spec.id,
              spokenName = spec.defaultSpokenName,
              lat = null,
              lng = null,
              radiusM = DEFAULT_RADIUS_METERS,
              registered = false,
              statusNote = "Not synced yet",
              updatedAt = nowMs,
            )
          )
        }
      }
      if (toInsert.isNotEmpty()) {
        dao.upsertZoneConfigs(toInsert)
      }
    }

  /**
   * Ensures periodic WorkManager jobs are scheduled:
   * - 15-minute single-fix fallback worker (ZoneFallbackWorker)
   * - 24-hour daily app_places refresh worker (DailyZoneRefreshWorker)
   */
  fun ensurePeriodicWorkersScheduled(context: Context) {
    runCatching {
      val appCtx = context.applicationContext
      val wm = WorkManager.getInstance(appCtx)

      val fallbackReq =
        PeriodicWorkRequestBuilder<ZoneFallbackWorker>(15, TimeUnit.MINUTES)
          .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
          .build()
      wm.enqueueUniquePeriodicWork(
        WORK_NAME_FALLBACK_15M,
        ExistingPeriodicWorkPolicy.KEEP,
        fallbackReq,
      )

      val dailyRefreshConstraints =
        Constraints.Builder()
          .setRequiredNetworkType(NetworkType.CONNECTED)
          .build()
      val dailyRefreshReq =
        PeriodicWorkRequestBuilder<DailyZoneRefreshWorker>(24, TimeUnit.HOURS)
          .setConstraints(dailyRefreshConstraints)
          .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
          .build()
      wm.enqueueUniquePeriodicWork(
        WORK_NAME_DAILY_REFRESH,
        ExistingPeriodicWorkPolicy.KEEP,
        dailyRefreshReq,
      )
    }.onFailure { err ->
      Log.w(TAG, "Failed to schedule periodic zone workers: ${err.message}")
    }
  }

  fun schedulePendingArrivalsFlushWorker(context: Context) {
    runCatching {
      val appCtx = context.applicationContext
      val constraints =
        Constraints.Builder()
          .setRequiredNetworkType(NetworkType.CONNECTED)
          .build()
      val req =
        OneTimeWorkRequestBuilder<FlushPendingArrivalsWorker>()
          .setConstraints(constraints)
          .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
          .build()
      WorkManager.getInstance(appCtx).enqueueUniqueWork(
        WORK_NAME_FLUSH_PENDING_ARRIVALS,
        ExistingWorkPolicy.REPLACE,
        req,
      )
    }.onFailure { err ->
      Log.w(TAG, "Failed to enqueue pending arrivals flush worker: ${err.message}")
    }
  }

  private suspend fun ensureToolSchemasCached(serverUrl: String, appToken: String) {
    when (val listRes = tabletMcpClient.listTools(serverUrl, appToken)) {
      is TabletCallOutcome.Success -> {
        for (def: TabletToolDef in listRes.value) {
          runCatching {
            toolSchemaCache[def.name] = JSONObject(def.inputSchemaJson)
          }
        }
      }
      is TabletCallOutcome.Failure -> {
        // Keep any previously cached tool schemas
      }
    }
  }

  private fun parseSinglePlaceConfig(
    id: String,
    defaultSpokenName: String,
    defaultCustom: Boolean,
    item: JSONObject,
    defaultRadiusFromEnvelope: Float,
    nowMs: Long,
  ): ZoneConfigEntity {
    val spoken =
      item.optString("spoken_name", "").trim().ifBlank {
        item.optString("spoken", "").trim().ifBlank {
          item.optString("name", "").trim().ifBlank {
            item.optString("label", "").trim().ifBlank { defaultSpokenName }
          }
        }
      }
    val isCustom =
      if (item.has("custom") && !item.isNull("custom")) {
        item.optBoolean("custom", defaultCustom)
      } else {
        defaultCustom
      }
    val pinObj = item.optJSONObject("pin") ?: item.optJSONObject("coords") ?: item
    val rawLat =
      if (!pinObj.isNull("lat") && pinObj.has("lat")) pinObj.optDouble("lat", Double.NaN)
      else if (!pinObj.isNull("latitude") && pinObj.has("latitude"))
        pinObj.optDouble("latitude", Double.NaN)
      else Double.NaN
    val rawLng =
      if (!pinObj.isNull("lng") && pinObj.has("lng")) pinObj.optDouble("lng", Double.NaN)
      else if (!pinObj.isNull("lon") && pinObj.has("lon"))
        pinObj.optDouble("lon", Double.NaN)
      else if (!pinObj.isNull("longitude") && pinObj.has("longitude"))
        pinObj.optDouble("longitude", Double.NaN)
      else Double.NaN

    val hasValidPin =
      !rawLat.isNaN() &&
        !rawLng.isNaN() &&
        rawLat in -90.0..90.0 &&
        rawLng in -180.0..180.0 &&
        !(rawLat == 0.0 && rawLng == 0.0)

    val rawRadius =
      if (!item.isNull("radius_m") && item.has("radius_m"))
        item.optDouble("radius_m", Double.NaN)
      else if (!item.isNull("radius") && item.has("radius"))
        item.optDouble("radius", Double.NaN)
      else if (!pinObj.isNull("radius_m") && pinObj.has("radius_m"))
        pinObj.optDouble("radius_m", Double.NaN)
      else Double.NaN

    val radiusM =
      if (!rawRadius.isNaN() && rawRadius > 0.0) {
        rawRadius.toFloat().coerceIn(MIN_RADIUS_METERS, MAX_RADIUS_METERS)
      } else {
        defaultRadiusFromEnvelope
      }

    return if (hasValidPin) {
      ZoneConfigEntity(
        id = id,
        spokenName = spoken,
        lat = rawLat,
        lng = rawLng,
        radiusM = radiusM,
        custom = isCustom,
        registered = false,
        statusNote = "Pin loaded (${radiusM.toInt()} m)",
        updatedAt = nowMs,
      )
    } else {
      ZoneConfigEntity(
        id = id,
        spokenName = spoken,
        lat = null,
        lng = null,
        radiusM = radiusM,
        custom = isCustom,
        registered = false,
        statusNote = "Missing pin on tablet",
        updatedAt = nowMs,
      )
    }
  }

  private fun parsePlacesFromEnvelope(structured: JSONObject): List<ZoneConfigEntity> {
    val nowMs = System.currentTimeMillis()
    val dataObj = structured.optJSONObject("data") ?: structured
    val defaultRadiusFromEnvelope =
      dataObj
        .optDouble("geofence_radius_meters", Double.NaN)
        .takeIf { !it.isNaN() && it > 0.0 }
        ?.toFloat()
        ?.coerceIn(MIN_RADIUS_METERS, MAX_RADIUS_METERS)
        ?: DEFAULT_RADIUS_METERS

    val rawByPlaceId = LinkedHashMap<String, JSONObject>()

    // 1. Check array under data.places or structured.places or data.items
    val placesArr =
      dataObj.optJSONArray("places")
        ?: structured.optJSONArray("places")
        ?: dataObj.optJSONArray("items")
    if (placesArr != null) {
      for (i in 0 until placesArr.length()) {
        val item = placesArr.optJSONObject(i) ?: continue
        val id =
          item.optString("id", "").trim().ifBlank {
            item.optString("place_id", "").trim().ifBlank {
              item.optString("place", "").trim()
            }
          }
        if (id.isNotBlank()) {
          rawByPlaceId[id] = item
        }
      }
    }

    // 2. Check map under data.places or structured.places or direct canonical keys on dataObj
    val dedicatedPlacesMap =
      dataObj.optJSONObject("places") ?: structured.optJSONObject("places")
    if (dedicatedPlacesMap != null) {
      val keys = dedicatedPlacesMap.keys()
      while (keys.hasNext()) {
        val k = keys.next().trim()
        if (k.isNotBlank() && !rawByPlaceId.containsKey(k)) {
          dedicatedPlacesMap.optJSONObject(k)?.let { obj ->
            rawByPlaceId[k] = obj
          }
        }
      }
    } else {
      for (spec in CANONICAL_PLACES) {
        if (!rawByPlaceId.containsKey(spec.id)) {
          dataObj.optJSONObject(spec.id)?.let { obj ->
            rawByPlaceId[spec.id] = obj
          }
        }
      }
    }

    val results = mutableListOf<ZoneConfigEntity>()

    // Include the 5 canonical places first in canonical order
    for (spec in CANONICAL_PLACES) {
      val item = rawByPlaceId[spec.id]
      if (item == null) {
        results.add(
          ZoneConfigEntity(
            id = spec.id,
            spokenName = spec.defaultSpokenName,
            lat = null,
            lng = null,
            radiusM = defaultRadiusFromEnvelope,
            custom = false,
            registered = false,
            statusNote = "Missing from app_places",
            updatedAt = nowMs,
          )
        )
      } else {
        results.add(
          parseSinglePlaceConfig(
            id = spec.id,
            defaultSpokenName = spec.defaultSpokenName,
            defaultCustom = false,
            item = item,
            defaultRadiusFromEnvelope = defaultRadiusFromEnvelope,
            nowMs = nowMs,
          )
        )
      }
    }

    // Include every additional (custom) place returned by app_places
    for ((placeId, item) in rawByPlaceId) {
      if (canonicalMap.containsKey(placeId)) continue
      results.add(
        parseSinglePlaceConfig(
          id = placeId,
          defaultSpokenName = placeId,
          defaultCustom = true,
          item = item,
          defaultRadiusFromEnvelope = defaultRadiusFromEnvelope,
          nowMs = nowMs,
        )
      )
    }

    return results
  }

  /**
   * Pulls the 5 canonical places from `app_places` on the tablet (or falls back to cached Room pins
   * if offline during boot/location toggle), then registers circular geofences for all places that
   * have a pin when ACCESS_BACKGROUND_LOCATION is granted.
   */
  suspend fun syncPlacesAndRegisterGeofences(
    context: Context,
    reason: String = "manual",
  ): ZoneSyncOutcome =
    withContext(Dispatchers.IO) {
      syncMutex.withLock {
        val appCtx = context.applicationContext
        if (!ScheduleAlarmCoordinator.isUserUnlocked(appCtx)) {
          return@withLock ZoneSyncOutcome(
            ok = false,
            registeredCount = 0,
            missingPinIds = CANONICAL_PLACES.map { it.id },
            summary = "Device locked; deferring zone registration",
          )
        }

        ensureDefaultZoneRowsInRoom(appCtx)
        ensurePeriodicWorkersScheduled(appCtx)

        val dao = RiviaDatabase.getInstance(appCtx).riviaDao()
        val keyRepo = SecureKeyRepository(appCtx)
        val serverUrl = keyRepo.getTabletServerUrl()
        val appToken = keyRepo.getTabletAppToken()

        var zoneConfigs: List<ZoneConfigEntity> = dao.getAllZoneConfigs()
        var removedPlaceIds: List<String> = emptyList()
        var pullErrorNote: String? = null

        if (appToken.isNotBlank() && TabletMcpClient.isValidServerUrl(serverUrl)) {
          ensureToolSchemasCached(serverUrl, appToken)
          val placesArgs = JSONObject()
          when (
            val outcome =
              tabletMcpClient.callTool(
                serverUrl = serverUrl,
                appToken = appToken,
                toolName = "app_places",
                arguments = placesArgs,
              )
          ) {
            is TabletCallOutcome.Success -> {
              val structured = outcome.value
              if (outcome.ok && structured.optBoolean("ok", true)) {
                val previousIds = zoneConfigs.map { it.id }.toSet()
                zoneConfigs = parsePlacesFromEnvelope(structured)
                val newIds = zoneConfigs.map { it.id }.toSet()
                removedPlaceIds = (previousIds - newIds).toList()
                dao.replaceZoneConfigsFromSync(zoneConfigs)
              } else {
                val errMsg =
                  structured.optJSONObject("error")?.optString("msg", "")?.takeIf { it.isNotBlank() }
                    ?: "app_places returned ok=false"
                pullErrorNote = errMsg
              }
            }
            is TabletCallOutcome.Failure -> {
              pullErrorNote = outcome.message
            }
          }
        } else {
          pullErrorNote = "Tablet token or URL not configured"
        }

        val geofencingClient = LocationServices.getGeofencingClient(appCtx)
        if (removedPlaceIds.isNotEmpty()) {
          removeGeofencesByIdsAwait(geofencingClient, removedPlaceIds)
        }

        // Now register geofences for every place (canonical or custom) that has valid lat, lng, and radius_m
        val hasBgPerm = hasBackgroundLocationPermission(appCtx)
        val hasFgPerm = hasForegroundLocationPermission(appCtx)
        val locationOn = isLocationServicesEnabled(appCtx)

        val pinnable =
          zoneConfigs.filter {
            it.lat != null && it.lng != null && it.radiusM > 0f
          }
        val missingIds =
          zoneConfigs.filter { it.lat == null || it.lng == null }.map { it.id }

        if (!hasFgPerm || !hasBgPerm) {
          val updated =
            zoneConfigs.map { cfg ->
              val hasPin = cfg.lat != null && cfg.lng != null
              cfg.copy(
                registered = false,
                statusNote =
                  if (!hasPin) "Missing pin on tablet"
                  else "Waiting for 'Allow all the time' location permission",
                updatedAt = System.currentTimeMillis(),
              )
            }
          dao.upsertZoneConfigs(updated)
          return@withLock ZoneSyncOutcome(
            ok = false,
            registeredCount = 0,
            missingPinIds = missingIds,
            summary = "Background location ('Allow all the time') is required to register geofences.",
          )
        }

        if (!locationOn) {
          val updated =
            zoneConfigs.map { cfg ->
              val hasPin = cfg.lat != null && cfg.lng != null
              cfg.copy(
                registered = false,
                statusNote =
                  if (!hasPin) "Missing pin on tablet"
                  else "Location is turned off; will re-register when turned on",
                updatedAt = System.currentTimeMillis(),
              )
            }
          dao.upsertZoneConfigs(updated)
          return@withLock ZoneSyncOutcome(
            ok = false,
            registeredCount = 0,
            missingPinIds = missingIds,
            summary = "Device location is off; geofences will re-register when location turns back on.",
          )
        }

        val pendingIntent = buildGeofencePendingIntent(appCtx)

        // Remove old geofences first so radius/coordinate edits or removed places cleanly replace previous fences
        removeGeofencesAwait(geofencingClient, pendingIntent)

        if (pinnable.isEmpty()) {
          val noteSuffix = pullErrorNote?.let { " ($it)" }.orEmpty()
          val updated =
            zoneConfigs.map { cfg ->
              cfg.copy(
                registered = false,
                statusNote = pullErrorNote ?: "Missing pin on tablet",
                updatedAt = System.currentTimeMillis(),
              )
            }
          dao.upsertZoneConfigs(updated)
          return@withLock ZoneSyncOutcome(
            ok = false,
            registeredCount = 0,
            missingPinIds = missingIds,
            summary = "No pinned places returned by app_places$noteSuffix",
          )
        }

        val geofences =
          pinnable.map { cfg ->
            Geofence.Builder()
              .setRequestId(cfg.id)
              .setCircularRegion(
                cfg.lat!!,
                cfg.lng!!,
                cfg.radiusM.coerceIn(MIN_RADIUS_METERS, MAX_RADIUS_METERS),
              )
              .setExpirationDuration(Geofence.NEVER_EXPIRE)
              .setTransitionTypes(
                Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT
              )
              .build()
          }

        val request =
          GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofences(geofences)
            .build()

        val addResult = addGeofencesAwait(appCtx, geofencingClient, request, pendingIntent)
        val nowMs = System.currentTimeMillis()

        if (addResult.isSuccess) {
          val registeredIds = pinnable.map { it.id }.toSet()
          val updated =
            zoneConfigs.map { cfg ->
              if (registeredIds.contains(cfg.id)) {
                cfg.copy(
                  registered = true,
                  statusNote = "Registered (${cfg.radiusM.toInt()} m)",
                  updatedAt = nowMs,
                )
              } else {
                cfg.copy(
                  registered = false,
                  statusNote = "Missing pin on tablet",
                  updatedAt = nowMs,
                )
              }
            }
          dao.upsertZoneConfigs(updated)
          val totalZoneCount = zoneConfigs.size
          val customCount = zoneConfigs.count { it.custom }
          val customSuffix = if (customCount > 0) " ($customCount custom)" else ""
          val missingSuffix =
            if (missingIds.isNotEmpty()) " · Missing pins: ${missingIds.joinToString(", ")}"
            else ""
          Log.i(
            TAG,
            "Registered ${pinnable.size}/$totalZoneCount geofence(s)$customSuffix ($reason): ${pinnable.joinToString { it.id }}$missingSuffix",
          )
          ZoneSyncOutcome(
            ok = true,
            registeredCount = pinnable.size,
            missingPinIds = missingIds,
            summary = "Registered ${pinnable.size}/$totalZoneCount zones$customSuffix$missingSuffix",
          )
        } else {
          val errMsg = addResult.exceptionOrNull()?.message ?: "GeofencingClient error"
          val updated =
            zoneConfigs.map { cfg ->
              val hasPin = cfg.lat != null && cfg.lng != null
              cfg.copy(
                registered = false,
                statusNote = if (hasPin) "Geofence error: $errMsg" else "Missing pin on tablet",
                updatedAt = nowMs,
              )
            }
          dao.upsertZoneConfigs(updated)
          Log.w(TAG, "Failed to register geofences ($reason): $errMsg")
          ZoneSyncOutcome(
            ok = false,
            registeredCount = 0,
            missingPinIds = missingIds,
            summary = "Geofence registration failed: $errMsg",
          )
        }
      }
    }

  private suspend fun removeGeofencesByIdsAwait(
    client: com.google.android.gms.location.GeofencingClient,
    requestIds: List<String>,
  ) {
    if (requestIds.isEmpty()) return
    withTimeoutOrNull(5_000L) {
      suspendCancellableCoroutine { cont ->
        client
          .removeGeofences(requestIds)
          .addOnCompleteListener {
            if (cont.isActive) cont.resume(Unit)
          }
      }
    }
  }

  private suspend fun removeGeofencesAwait(
    client: com.google.android.gms.location.GeofencingClient,
    pendingIntent: PendingIntent,
  ) {
    withTimeoutOrNull(5_000L) {
      suspendCancellableCoroutine { cont ->
        client
          .removeGeofences(pendingIntent)
          .addOnCompleteListener {
            if (cont.isActive) cont.resume(Unit)
          }
      }
    }
  }

  @SuppressLint("MissingPermission")
  private suspend fun addGeofencesAwait(
    context: Context,
    client: com.google.android.gms.location.GeofencingClient,
    request: GeofencingRequest,
    pendingIntent: PendingIntent,
  ): Result<Unit> {
    if (!hasForegroundLocationPermission(context) || !hasBackgroundLocationPermission(context)) {
      return Result.failure(SecurityException("Missing location permissions"))
    }
    return withTimeoutOrNull(8_000L) {
      suspendCancellableCoroutine { cont ->
        client
          .addGeofences(request, pendingIntent)
          .addOnSuccessListener {
            if (cont.isActive) cont.resume(Result.success(Unit))
          }
          .addOnFailureListener { err ->
            if (cont.isActive) cont.resume(Result.failure(err))
          }
      }
    } ?: Result.failure( IllegalStateException("Timed out adding geofences"))
  }

  /**
   * Called by GeofenceBroadcastReceiver when GeofencingEvent reports GEOFENCE_NOT_AVAILABLE
   * (e.g. user turned off device location). Marks zones as waiting for location to turn back on.
   */
  suspend fun onGeofenceNotAvailable(context: Context) =
    withContext(Dispatchers.IO) {
      val appCtx = context.applicationContext
      if (!ScheduleAlarmCoordinator.isUserUnlocked(appCtx)) return@withContext
      val dao = RiviaDatabase.getInstance(appCtx).riviaDao()
      val existing = dao.getAllZoneConfigs()
      val nowMs = System.currentTimeMillis()
      val updated =
        existing.map { cfg ->
          val hasPin = cfg.lat != null && cfg.lng != null
          cfg.copy(
            registered = false,
            statusNote =
              if (hasPin) "Location off (GEOFENCE_NOT_AVAILABLE); waiting for location"
              else cfg.statusNote,
            updatedAt = nowMs,
          )
        }
      dao.upsertZoneConfigs(updated)
    }

  /**
   * Called on BOOT_COMPLETED, USER_UNLOCKED, MY_PACKAGE_REPLACED, and PROVIDERS_CHANGED / MODE_CHANGED.
   */
  suspend fun onBootOrPackageReplacedOrLocationChanged(context: Context, action: String) =
    withContext(Dispatchers.IO) {
      val appCtx = context.applicationContext
      if (!ScheduleAlarmCoordinator.isUserUnlocked(appCtx)) return@withContext
      ensurePeriodicWorkersScheduled(appCtx)
      if (isLocationServicesEnabled(appCtx)) {
        syncPlacesAndRegisterGeofences(appCtx, reason = action)
        flushPendingArrivalCalls(appCtx)
      } else {
        onGeofenceNotAvailable(appCtx)
      }
    }

  /**
   * Handles a geofence or 15-minute fallback transition (ENTER or EXIT) for a canonical place.
   * Implements:
   * - Step 7: 10-minute GPS bounce filter (ignores ENTER within 10 min of EXIT from the same place)
   * - Step 8: On valid ENTER of place B, pairs with last non-bounce EXIT from place A != B within 3 hours
   *   to build ride_id "<from>-<departed_epoch>", calls app_arrival, applies alerts, and speaks the arrival line.
   * - Queues app_arrival in Room if offline and flushes when network returns.
   */
  suspend fun handleZoneTransition(
    context: Context,
    placeId: String,
    transition: String, // "ENTER" or "EXIT"
    eventTimeMs: Long = System.currentTimeMillis(),
    source: String = "geofence", // "geofence" or "fallback"
  ) =
    withContext(Dispatchers.IO) {
      transitionMutex.withLock {
        val appCtx = context.applicationContext
        if (!ScheduleAlarmCoordinator.isUserUnlocked(appCtx)) return@withLock

        val cleanTransition = transition.uppercase().trim()
        if (cleanTransition != "ENTER" && cleanTransition != "EXIT") return@withLock

        val dao = RiviaDatabase.getInstance(appCtx).riviaDao()
        val configs = dao.getAllZoneConfigs().associateBy { it.id }
        val spokenName = configs[placeId]?.spokenName ?: canonicalSpokenName(placeId)

        val lastForSamePlace = dao.getLastNonBounceEventForPlace(placeId)

        if (cleanTransition == "EXIT") {
          val exitEvent =
            ZoneEventEntity(
              placeId = placeId,
              spokenName = spokenName,
              transition = "EXIT",
              eventTimeMs = eventTimeMs,
              source = source,
              isBounce = false,
              rideId = null,
              arrivalStatus = "recorded",
            )
          dao.insertZoneEvent(exitEvent)
          Log.i(TAG, "Recorded EXIT for $placeId ($spokenName) source=$source at $eventTimeMs")
          return@withLock
        }

        // cleanTransition == "ENTER"
        // Step 7: 10-minute GPS bounce rule — ignore an ENTER that comes within 10 minutes of an EXIT from the same place
        if (
          lastForSamePlace != null &&
            lastForSamePlace.transition == "EXIT" &&
            eventTimeMs >= lastForSamePlace.eventTimeMs &&
            (eventTimeMs - lastForSamePlace.eventTimeMs) <= GPS_BOUNCE_WINDOW_MS
        ) {
          val bounceEvent =
            ZoneEventEntity(
              placeId = placeId,
              spokenName = spokenName,
              transition = "ENTER",
              eventTimeMs = eventTimeMs,
              source = source,
              isBounce = true,
              rideId = null,
              arrivalStatus = "bounce_ignored",
            )
          dao.insertZoneEvent(bounceEvent)
          Log.i(
            TAG,
            "Ignored GPS bounce ENTER for $placeId within 10m of EXIT (${(eventTimeMs - lastForSamePlace.eventTimeMs) / 1000}s)",
          )
          return@withLock
        }

        // Avoid duplicate open ENTER if the last non-bounce event for this same place was already ENTER
        if (lastForSamePlace != null && lastForSamePlace.transition == "ENTER" && source == "fallback") {
          return@withLock
        }

        // Step 8: Check if the last non-bounce EXIT was from a different place A within the last 3 hours
        val lastExitAnyPlace = dao.getLastNonBounceExitEvent()
        val enterEpochSec = eventTimeMs / 1000L
        var rideId: String? = null
        var rideFromPlace: String? = null
        var rideDepartedEpochSec: Long? = null
        var rideArrivedEpochSec: Long? = null

        if (
          lastExitAnyPlace != null &&
            lastExitAnyPlace.placeId != placeId &&
            eventTimeMs >= lastExitAnyPlace.eventTimeMs &&
            (eventTimeMs - lastExitAnyPlace.eventTimeMs) <= RIDE_MAX_WINDOW_MS
        ) {
          val departedSec = lastExitAnyPlace.eventTimeMs / 1000L
          rideFromPlace = lastExitAnyPlace.placeId
          rideDepartedEpochSec = departedSec
          rideArrivedEpochSec = enterEpochSec
          rideId = "${lastExitAnyPlace.placeId}-$departedSec"
        }

        val initialEnterEvent =
          ZoneEventEntity(
            placeId = placeId,
            spokenName = spokenName,
            transition = "ENTER",
            eventTimeMs = eventTimeMs,
            source = source,
            isBounce = false,
            rideId = rideId,
            arrivalStatus = "sending",
          )
        val insertedEventId = dao.insertZoneEvent(initialEnterEvent)
        val savedEvent = initialEnterEvent.copy(id = insertedEventId)

        val pendingCall =
          PendingArrivalCallEntity(
            placeId = placeId,
            eventTimeMs = eventTimeMs,
            source = source,
            rideId = rideId,
            rideFromPlace = rideFromPlace,
            rideDepartedEpochSec = rideDepartedEpochSec,
            rideArrivedEpochSec = rideArrivedEpochSec,
            zoneEventId = insertedEventId,
          )

        val sent = executeArrivalCallOrQueue(appCtx, pendingCall, savedEvent, enqueueIfFailed = true)
        if (!sent) {
          schedulePendingArrivalsFlushWorker(appCtx)
        }
      }
    }

  private fun formatTimeForProperty(
    propSchema: JSONObject?,
    epochSeconds: Long,
    isoString: String,
  ): Any {
    if (propSchema == null) return isoString
    val typeStr = propSchema.optString("type", "").lowercase()
    return when (typeStr) {
      "integer",
      "number" -> epochSeconds
      "object" ->
        JSONObject().apply {
          put("epoch", epochSeconds)
          put("iso", isoString)
        }
      "string" -> isoString
      else -> {
        val anyOf = propSchema.optJSONArray("anyOf") ?: propSchema.optJSONArray("oneOf")
        if (anyOf != null) {
          for (i in 0 until anyOf.length()) {
            val subType = anyOf.optJSONObject(i)?.optString("type", "")?.lowercase().orEmpty()
            if (subType == "string") return isoString
            if (subType == "integer" || subType == "number") return epochSeconds
          }
        }
        val desc = propSchema.optString("description", "").lowercase()
        if (desc.contains("epoch") || desc.contains("unix") || desc.contains("seconds")) {
          epochSeconds
        } else {
          isoString
        }
      }
    }
  }

  private fun buildAppArrivalArguments(
    call: PendingArrivalCallEntity,
    schemaObj: JSONObject?,
  ): JSONObject {
    val props = schemaObj?.optJSONObject("properties")
    val enterEpochSec = call.eventTimeMs / 1000L
    val enterIso =
      Instant.ofEpochMilli(call.eventTimeMs)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

    val args = JSONObject()
    // Primary place field (never send coords or title="test")
    args.put("place", call.placeId)
    if (props?.has("place_id") == true) {
      args.put("place_id", call.placeId)
    }

    // Time field matched to schema type
    val timeVal = formatTimeForProperty(props?.optJSONObject("time"), enterEpochSec, enterIso)
    args.put("time", timeVal)
    if (props?.has("at") == true) {
      args.put("at", formatTimeForProperty(props.optJSONObject("at"), enterEpochSec, enterIso))
    }
    if (props?.has("arrived_at") == true) {
      args.put(
        "arrived_at",
        formatTimeForProperty(props.optJSONObject("arrived_at"), enterEpochSec, enterIso),
      )
    }

    // Include ride if a non-bounce EXIT from A != B occurred within 3 hours
    if (
      !call.rideId.isNullOrBlank() &&
        !call.rideFromPlace.isNullOrBlank() &&
        call.rideDepartedEpochSec != null &&
        call.rideArrivedEpochSec != null
    ) {
      val depSec = call.rideDepartedEpochSec
      val arrSec = call.rideArrivedEpochSec
      val depIso =
        Instant.ofEpochSecond(depSec)
          .atZone(ZoneId.systemDefault())
          .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
      val arrIso =
        Instant.ofEpochSecond(arrSec)
          .atZone(ZoneId.systemDefault())
          .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

      val ridePropSchema = props?.optJSONObject("ride")
      val rideSubProps = ridePropSchema?.optJSONObject("properties")

      val depForRide =
        formatTimeForProperty(
          rideSubProps?.optJSONObject("departed") ?: props?.optJSONObject("departed"),
          depSec,
          depIso,
        )
      val arrForRide =
        formatTimeForProperty(
          rideSubProps?.optJSONObject("arrived") ?: props?.optJSONObject("arrived"),
          arrSec,
          arrIso,
        )

      // Nested ride object (if app_arrival schema defines a `ride` object)
      val rideObj =
        JSONObject().apply {
          put("ride_id", call.rideId)
          if (rideSubProps?.has("id") == true) put("id", call.rideId)
          put("from", call.rideFromPlace)
          if (rideSubProps?.has("from_place") == true) put("from_place", call.rideFromPlace)
          put("to", call.placeId)
          if (rideSubProps?.has("to_place") == true) put("to_place", call.placeId)
          put("departed", depForRide)
          put("arrived", arrForRide)
        }
      args.put("ride", rideObj)

      // Top-level ride keys (TabletMcpClient.filterArgumentsForTool automatically keeps only
      // the keys present in app_arrival's properties schema)
      args.put("ride_id", call.rideId)
      args.put("from", call.rideFromPlace)
      args.put("from_place", call.rideFromPlace)
      args.put("to", call.placeId)
      args.put("to_place", call.placeId)
      args.put("departed", depForRide)
      args.put("arrived", arrForRide)
    }

    return args
  }

  private suspend fun executeArrivalCallOrQueue(
    context: Context,
    call: PendingArrivalCallEntity,
    zoneEvent: ZoneEventEntity?,
    enqueueIfFailed: Boolean,
  ): Boolean {
    val appCtx = context.applicationContext
    val dao = RiviaDatabase.getInstance(appCtx).riviaDao()
    val keyRepo = SecureKeyRepository(appCtx)
    val serverUrl = keyRepo.getTabletServerUrl()
    val appToken = keyRepo.getTabletAppToken()

    if (appToken.isBlank() || !TabletMcpClient.isValidServerUrl(serverUrl)) {
      if (enqueueIfFailed) {
        dao.insertPendingArrivalCall(call)
        if (zoneEvent != null) {
          dao.updateZoneEvent(zoneEvent.copy(arrivalStatus = "queued_offline"))
        }
      }
      return false
    }

    ensureToolSchemasCached(serverUrl, appToken)
    val arrivalSchema = toolSchemaCache["app_arrival"]
    val rawArgs = buildAppArrivalArguments(call, arrivalSchema)

    return when (
      val outcome =
        tabletMcpClient.callTool(
          serverUrl = serverUrl,
          appToken = appToken,
          toolName = "app_arrival",
          arguments = rawArgs,
        )
    ) {
      is TabletCallOutcome.Success -> {
        val structured = outcome.value
        val isOk = outcome.ok && structured.optBoolean("ok", true)
        if (isOk) {
          // 1. Apply leave-by alerts from the tablet reply using the shared Pass B function
          val alertRes = ScheduleAlarmCoordinator.applyAlertsFromTabletReply(appCtx, structured)
          if (zoneEvent != null) {
            dao.updateZoneEvent(
              zoneEvent.copy(
                arrivalStatus =
                  if (alertRes.scheduledCount > 0) "ok (${alertRes.scheduledCount} alert)"
                  else "ok",
              )
            )
          }
          // 2. Speak the arrival line (unless message.delivery.say is false)
          speakArrivalReplyIfRequested(
            context = appCtx,
            placeId = call.placeId,
            structured = structured,
          )
          true
        } else {
          val errCode =
            structured.optJSONObject("error")?.optString("code", "tool_error") ?: "tool_error"
          if (zoneEvent != null) {
            dao.updateZoneEvent(zoneEvent.copy(arrivalStatus = "error:$errCode"))
          }
          false
        }
      }
      is TabletCallOutcome.Failure -> {
        val isRetryableNetwork =
          outcome.errorCode == "tablet_unreachable" ||
            outcome.errorCode.startsWith("http_5") ||
            outcome.errorCode == "http_408" ||
            outcome.errorCode == "http_429"
        if (isRetryableNetwork) {
          if (enqueueIfFailed) {
            dao.insertPendingArrivalCall(call)
          }
          if (zoneEvent != null) {
            dao.updateZoneEvent(zoneEvent.copy(arrivalStatus = "queued_offline"))
          }
        } else {
          if (zoneEvent != null) {
            dao.updateZoneEvent(zoneEvent.copy(arrivalStatus = "failed:${outcome.errorCode}"))
          }
        }
        false
      }
    }
  }

  /**
   * Flushes any offline-queued app_arrival calls in chronological order once network returns.
   */
  suspend fun flushPendingArrivalCalls(context: Context): Int =
    withContext(Dispatchers.IO) {
      val appCtx = context.applicationContext
      if (!ScheduleAlarmCoordinator.isUserUnlocked(appCtx)) return@withContext 0
      val dao = RiviaDatabase.getInstance(appCtx).riviaDao()
      val pending = dao.getAllPendingArrivalCalls()
      if (pending.isEmpty()) return@withContext 0

      var flushed = 0
      for (item in pending) {
        val recentEvents = dao.getLastNonBounceEventForPlace(item.placeId)
        val matchingEvent =
          if (recentEvents != null && recentEvents.id == item.zoneEventId) recentEvents else null
        val ok =
          executeArrivalCallOrQueue(
            context = appCtx,
            call = item,
            zoneEvent = matchingEvent,
            enqueueIfFailed = false,
          )
        if (ok) {
          dao.deletePendingArrivalCallById(item.id)
          flushed++
        } else {
          // Stop flushing on first still-offline failure so ordering is preserved
          break
        }
      }
      flushed
    }

  /**
   * Speaks the arrival line from `app_arrival`:
   * - If `message.delivery.say` is `false`, does not speak.
   * - Otherwise uses the existing Gemini path with a 10-second timeout to word 1-2 short spoken
   *   sentences from `message` (respecting `message.delivery.max_spoken_sentences` when present)
   *   and speaks it with ElevenLabs.
   * - If Gemini fails or takes more than 10 seconds, speaks `fallback_text` instead.
   */
  private suspend fun speakArrivalReplyIfRequested(
    context: Context,
    placeId: String,
    structured: JSONObject,
  ) {
    val msgObj = structured.optJSONObject("message")
    val deliveryObj =
      msgObj?.optJSONObject("delivery") ?: structured.optJSONObject("delivery")

    // Check if message.delivery.say is explicitly false
    if (deliveryObj != null && deliveryObj.has("say") && !deliveryObj.optBoolean("say", true)) {
      Log.i(TAG, "Arrival at $placeId has delivery.say=false; skipping speech")
      return
    }

    val maxSentences =
      (deliveryObj?.optInt("max_spoken_sentences", 2) ?: 2).coerceIn(1, 3)
    val fallbackText =
      structured.optString("fallback_text", "").trim().ifBlank {
        msgObj?.optString("fallback_text", "")?.trim().orEmpty().ifBlank {
          msgObj?.optString("summary", "")?.trim().orEmpty()
        }
      }

    val isCustomPlace =
      runCatching {
        RiviaDatabase.getInstance(context)
          .riviaDao()
          .getAllZoneConfigs()
          .firstOrNull { it.id == placeId }
          ?.custom == true
      }.getOrDefault(!canonicalMap.containsKey(placeId))

    val messagePayloadStr = msgObj?.toString()?.takeIf { it != "{}" }.orEmpty()
    val wordedByGemini: String? =
      if (!isCustomPlace && messagePayloadStr.isNotBlank()) {
        wordArrivalLineWithGemini10s(
          context = context,
          messageJson = messagePayloadStr,
          fallbackText = fallbackText,
          maxSentences = maxSentences,
        )
      } else {
        null
      }

    val finalLineToSpeak =
      wordedByGemini?.trim()?.takeIf { it.isNotBlank() }
        ?: fallbackText

    if (finalLineToSpeak.isBlank()) return

    speakFallbackTextAloud(
      context = context,
      fallbackText = finalLineToSpeak,
      messageIdPrefix = "arrival:$placeId",
    )
  }

  /**
   * Speaks a tablet reply's `fallback_text` aloud via AudioPlayerManager.
   */
  suspend fun speakFallbackTextAloud(
    context: Context,
    fallbackText: String,
    messageIdPrefix: String = "tablet_fallback",
  ) {
    val clean = fallbackText.trim()
    if (clean.isBlank()) return
    runCatching {
      val appCtx = context.applicationContext
      val playerManager = AudioPlayerManager.getInstance(appCtx)
      val liveActive = LiveSessionManager.uiState.value.isActive
      val alreadySpeaking = playerManager.isSpeaking.value
      val msgId = "$messageIdPrefix:${System.currentTimeMillis()}"

      if (liveActive || alreadySpeaking) {
        playerManager.enqueueOrSpeakLineWithoutInterrupting(
          messageId = msgId,
          rawMarkdown = clean,
          isLiveChannel = liveActive,
        )
      } else {
        val done = CompletableDeferred<Unit>()
        playerManager.speakMarkdownMessage(
          messageId = msgId,
          rawMarkdown = clean,
          isLiveChannel = false,
          onFinished = { done.complete(Unit) },
          onError = { done.complete(Unit) },
        )
        withTimeoutOrNull(8_500L) {
          done.await()
        }
      }
    }.onFailure { err ->
      Log.w(TAG, "Failed to speak line ($messageIdPrefix): ${err.message}")
    }
  }

  /**
   * JOB-007 Step 3: "Pin my location" action.
   * Gets the phone's current GPS position and calls `app_pin_place` with:
   * - `coords`: "lat,lng"
   * - `place`: an existing place id or name, to move it (e.g. "home"), OR
   * - `name`: a new place's name, to add it (e.g. "Giant")
   * - `radius_m` (optional, 50-400) for a new place's zone size
   * Speaks the reply's `fallback_text`.
   */
  @SuppressLint("MissingPermission")
  suspend fun pinCurrentLocation(
    context: Context,
    existingPlace: String? = null,
    newPlaceName: String? = null,
    radiusMeters: Int? = null,
  ): PinPlaceOutcome =
    withContext(Dispatchers.IO) {
      val appCtx = context.applicationContext
      val cleanPlace = existingPlace?.trim()?.takeIf { it.isNotBlank() }
      val cleanName = newPlaceName?.trim()?.takeIf { it.isNotBlank() }
      val targetLabel = cleanPlace ?: cleanName ?: "place"

      if (cleanPlace == null && cleanName == null) {
        return@withContext PinPlaceOutcome(
          ok = false,
          status = "invalid_input",
          isPendingApproval = false,
          ticket = null,
          coords = null,
          targetLabel = targetLabel,
          fallbackText = "",
          summary = "Choose an existing place to move or enter a new place name.",
        )
      }

      if (!hasForegroundLocationPermission(appCtx)) {
        return@withContext PinPlaceOutcome(
          ok = false,
          status = "missing_permission",
          isPendingApproval = false,
          ticket = null,
          coords = null,
          targetLabel = targetLabel,
          fallbackText = "",
          summary = "Foreground location permission is required to pin your current position.",
        )
      }

      if (!isLocationServicesEnabled(appCtx)) {
        return@withContext PinPlaceOutcome(
          ok = false,
          status = "location_off",
          isPendingApproval = false,
          ticket = null,
          coords = null,
          targetLabel = targetLabel,
          fallbackText = "",
          summary = "Turn on device location to pin your current position.",
        )
      }

      val keyRepo = SecureKeyRepository(appCtx)
      val serverUrl = keyRepo.getTabletServerUrl()
      val appToken = keyRepo.getTabletAppToken()
      if (appToken.isBlank() || !TabletMcpClient.isValidServerUrl(serverUrl)) {
        return@withContext PinPlaceOutcome(
          ok = false,
          status = "not_configured",
          isPendingApproval = false,
          ticket = null,
          coords = null,
          targetLabel = targetLabel,
          fallbackText = "",
          summary = "Tablet Server URL or App token is not configured.",
        )
      }

      val fusedClient = LocationServices.getFusedLocationProviderClient(appCtx)
      val location =
        fetchHighAccuracyLocationFix(fusedClient)
          ?: fetchSingleCurrentLocationFix(fusedClient)
          ?: return@withContext PinPlaceOutcome(
            ok = false,
            status = "no_location_fix",
            isPendingApproval = false,
            ticket = null,
            coords = null,
            targetLabel = targetLabel,
            fallbackText = "",
            summary = "Could not get a current GPS fix from the phone. Try again outdoors or near a window.",
          )

      val coordsStr =
        String.format(java.util.Locale.US, "%.6f,%.6f", location.latitude, location.longitude)

      ensureToolSchemasCached(serverUrl, appToken)
      val args =
        JSONObject().apply {
          put("coords", coordsStr)
          if (cleanPlace != null) {
            put("place", cleanPlace)
          } else if (cleanName != null) {
            put("name", cleanName)
          }
          if (radiusMeters != null) {
            val clampedRadius =
              radiusMeters.coerceIn(MIN_RADIUS_METERS.toInt(), MAX_RADIUS_METERS.toInt())
            put("radius_m", clampedRadius)
          }
        }

      when (
        val outcome =
          tabletMcpClient.callTool(
            serverUrl = serverUrl,
            appToken = appToken,
            toolName = "app_pin_place",
            arguments = args,
          )
      ) {
        is TabletCallOutcome.Success -> {
          val structured = outcome.value
          val isOk = outcome.ok && structured.optBoolean("ok", true)
          val msgObj = structured.optJSONObject("message")
          val dataObj = structured.optJSONObject("data")
          val fallbackText =
            structured.optString("fallback_text", "").trim().ifBlank {
              msgObj?.optString("fallback_text", "")?.trim().orEmpty().ifBlank {
                dataObj?.optString("fallback_text", "")?.trim().orEmpty().ifBlank {
                  msgObj?.optString("summary", "")?.trim().orEmpty()
                }
              }
            }

          if (isOk) {
            if (fallbackText.isNotBlank()) {
              speakFallbackTextAloud(
                context = appCtx,
                fallbackText = fallbackText,
                messageIdPrefix = "pin_place:$targetLabel",
              )
            }
            val rawStatus =
              structured.optString("status", "").trim().ifBlank {
                dataObj?.optString("status", "")?.trim().orEmpty().ifBlank { outcome.status }
              }
            val isPending =
              rawStatus.equals("pending", ignoreCase = true) ||
                structured.optBoolean("pending", false) ||
                dataObj?.optBoolean("pending", false) == true ||
                !outcome.ticket.isNullOrBlank()

            if (!isPending) {
              syncPlacesAndRegisterGeofences(appCtx, reason = "pin_place_approved")
            }

            val summaryLine =
              fallbackText.ifBlank {
                if (isPending) {
                  "Pin sent ($coordsStr) · Waiting for approval in the hub"
                } else {
                  "Pinned $targetLabel at $coordsStr"
                }
              }

            PinPlaceOutcome(
              ok = true,
              status = if (isPending) "pending" else rawStatus.ifBlank { "ok" },
              isPendingApproval = isPending,
              ticket = outcome.ticket,
              coords = coordsStr,
              targetLabel = targetLabel,
              fallbackText = fallbackText,
              summary = summaryLine,
              callOutcome = outcome,
            )
          } else {
            val errMsg =
              structured.optJSONObject("error")?.optString("msg", "")?.takeIf { it.isNotBlank() }
                ?: fallbackText.ifBlank { "app_pin_place returned ok=false" }
            if (fallbackText.isNotBlank()) {
              speakFallbackTextAloud(
                context = appCtx,
                fallbackText = fallbackText,
                messageIdPrefix = "pin_place_err:$targetLabel",
              )
            }
            PinPlaceOutcome(
              ok = false,
              status = "error",
              isPendingApproval = false,
              ticket = outcome.ticket,
              coords = coordsStr,
              targetLabel = targetLabel,
              fallbackText = fallbackText,
              summary = errMsg,
              callOutcome = outcome,
            )
          }
        }
        is TabletCallOutcome.Failure -> {
          PinPlaceOutcome(
            ok = false,
            status = outcome.errorCode,
            isPendingApproval = false,
            ticket = outcome.ticket,
            coords = coordsStr,
            targetLabel = targetLabel,
            fallbackText = "",
            summary = outcome.message,
            callOutcome = outcome,
          )
        }
      }
    }

  @SuppressLint("MissingPermission")
  private suspend fun fetchHighAccuracyLocationFix(
    fusedClient: com.google.android.gms.location.FusedLocationProviderClient,
  ): Location? {
    return withTimeoutOrNull(8_000L) {
      suspendCancellableCoroutine<Location?> { cont ->
        val cts = CancellationTokenSource()
        cont.invokeOnCancellation { cts.cancel() }
        fusedClient
          .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
          .addOnSuccessListener { loc ->
            if (cont.isActive) cont.resume(loc)
          }
          .addOnFailureListener {
            if (cont.isActive) cont.resume(null)
          }
      }
    }
  }

  private suspend fun wordArrivalLineWithGemini10s(
    context: Context,
    messageJson: String,
    fallbackText: String,
    maxSentences: Int,
  ): String? {
    val keyRepo = SecureKeyRepository(context)
    val geminiKey = keyRepo.getGeminiKey().trim()
    if (geminiKey.isBlank()) return null

    return withTimeoutOrNull(10_000L) {
      val sb = StringBuilder()
      var hadError = false
      val settings =
        RunSettings(
          model = "gemini-3-flash-preview",
          thinkingLevel = "minimal",
          thoughtSummaries = "none",
          store = false,
          background = false,
          systemInstruction =
            "You are Rivia speaking aloud to Jordan upon arrival. " +
              "Turn the tablet arrival message JSON into 1 to $maxSentences short, natural spoken sentences. " +
              "Do not use markdown, lists, or bullet points. Keep every clock time and fact exact.",
          styleForLiveMode = "",
          styleForTypedChat = "",
        )
      val prompt =
        buildString {
          append("Word this arrival message from the tablet in at most ")
          append(maxSentences)
          append(if (maxSentences == 1) " short spoken sentence.\n" else " short spoken sentences.\n")
          append("Message JSON: ")
          append(messageJson)
          if (fallbackText.isNotBlank()) {
            append("\nFallback reference: ")
            append(fallbackText)
          }
        }
      geminiClient.sendTurn(
        apiKey = geminiKey,
        settings = settings,
        channel = RiviaChannel.LIVE_VOICE,
        priorMessages = emptyList(),
        newUserText = prompt,
        previousInteractionId = null,
        customStore = false,
        includeToolsOnTurn = false,
      ) { event ->
        when (event) {
          is GeminiStreamEvent.TextDelta -> sb.append(event.text)
          is GeminiStreamEvent.ReplaceReplyText -> {
            sb.clear()
            sb.append(event.fullText)
          }
          is GeminiStreamEvent.Error -> hadError = true
          else -> {}
        }
      }
      if (hadError) null else sb.toString().trim().takeIf { it.isNotBlank() }
    }
  }

  /**
   * Step 9: 15-minute WorkManager location fallback check.
   * - Takes a single location fix and stops (no location trail).
   * - If the fix is inside a registered zone and Room has no open ENTER for that zone since the last EXIT,
   *   records an ENTER at the fix time marked "fallback" and calls app_arrival the same way as Step 8.
   * - If the fix is outside a zone that still shows an open ENTER, records an EXIT at the fix time
   *   marked "fallback" (does not call app_arrival).
   */
  @SuppressLint("MissingPermission")
  suspend fun runSingleLocationFallbackCheck(context: Context) =
    withContext(Dispatchers.IO) {
      val appCtx = context.applicationContext
      if (!ScheduleAlarmCoordinator.isUserUnlocked(appCtx)) return@withContext
      if (!hasForegroundLocationPermission(appCtx) || !hasBackgroundLocationPermission(appCtx)) {
        return@withContext
      }
      if (!isLocationServicesEnabled(appCtx)) return@withContext

      // Also flush any pending offline arrival calls while we are awake
      flushPendingArrivalCalls(appCtx)

      val dao = RiviaDatabase.getInstance(appCtx).riviaDao()
      var configs = dao.getAllZoneConfigs()
      if (configs.none { it.registered && it.lat != null && it.lng != null }) {
        syncPlacesAndRegisterGeofences(appCtx, reason = "fallback_worker_init")
        configs = dao.getAllZoneConfigs()
      }

      val activeZones =
        configs.filter { it.lat != null && it.lng != null && it.radiusM > 0f }
      if (activeZones.isEmpty()) return@withContext

      val fusedClient = LocationServices.getFusedLocationProviderClient(appCtx)
      val location: Location =
        fetchSingleCurrentLocationFix(fusedClient) ?: return@withContext

      val fixTimeMs =
        location.time.takeIf { it > 0L } ?: System.currentTimeMillis()

      for (zone in activeZones) {
        val results = FloatArray(1)
        Location.distanceBetween(
          location.latitude,
          location.longitude,
          zone.lat!!,
          zone.lng!!,
          results,
        )
        val distanceMeters = results[0]
        val isInside = distanceMeters <= zone.radiusM

        val lastNonBounce = dao.getLastNonBounceEventForPlace(zone.id)
        val hasOpenEnter = lastNonBounce?.transition == "ENTER"

        if (isInside && !hasOpenEnter) {
          handleZoneTransition(
            context = appCtx,
            placeId = zone.id,
            transition = "ENTER",
            eventTimeMs = fixTimeMs,
            source = "fallback",
          )
        } else if (!isInside && hasOpenEnter) {
          handleZoneTransition(
            context = appCtx,
            placeId = zone.id,
            transition = "EXIT",
            eventTimeMs = fixTimeMs,
            source = "fallback",
          )
        }
      }
    }

  @SuppressLint("MissingPermission")
  private suspend fun fetchSingleCurrentLocationFix(
    fusedClient: com.google.android.gms.location.FusedLocationProviderClient,
  ): Location? {
    val currentFix =
      withTimeoutOrNull(10_000L) {
        suspendCancellableCoroutine<Location?> { cont ->
          val cts = CancellationTokenSource()
          cont.invokeOnCancellation { cts.cancel() }
          fusedClient
            .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
            .addOnSuccessListener { loc ->
              if (cont.isActive) cont.resume(loc)
            }
            .addOnFailureListener {
              if (cont.isActive) cont.resume(null)
            }
        }
      }
    if (currentFix != null) return currentFix

    return withTimeoutOrNull(4_000L) {
      suspendCancellableCoroutine<Location?> { cont ->
        fusedClient.lastLocation
          .addOnSuccessListener { loc ->
            if (cont.isActive) cont.resume(loc)
          }
          .addOnFailureListener {
            if (cont.isActive) cont.resume(null)
          }
      }
    }
  }
}
