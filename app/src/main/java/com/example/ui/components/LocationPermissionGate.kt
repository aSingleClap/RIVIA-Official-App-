package com.example.ui.components

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.schedule.ZoneLocationCoordinator

/**
 * Runs the ordered App Pass C location + background location explanation + battery exemption flow
 * in normal Rivia mode on first open and whenever a required permission was revoked.
 *
 * Order enforced:
 * 1. Request ACCESS_FINE_LOCATION + ACCESS_COARSE_LOCATION together ("While using the app").
 * 2. Show explanation screen for "Allow all the time" (ACCESS_BACKGROUND_LOCATION) with button to
 *    open system settings.
 * 3. Request battery optimization exemption (ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS) and show
 *    the Samsung Never sleeping apps note on the same screen.
 */
@Composable
fun LocationPermissionGate(
  onPermissionsUpdated: (hasFg: Boolean, hasBg: Boolean, hasBatteryExemption: Boolean) -> Unit,
  content: @Composable () -> Unit,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current

  var hasFg by rememberSaveable {
    mutableStateOf(ZoneLocationCoordinator.hasForegroundLocationPermission(context))
  }
  var hasBg by rememberSaveable {
    mutableStateOf(ZoneLocationCoordinator.hasBackgroundLocationPermission(context))
  }
  var hasBattery by rememberSaveable {
    mutableStateOf(ZoneLocationCoordinator.isIgnoringBatteryOptimizations(context))
  }

  var fgAutoPrompted by rememberSaveable { mutableStateOf(false) }
  var userDismissedGateForSession by rememberSaveable { mutableStateOf(false) }

  fun refreshPermissionsState() {
    val fgNow = ZoneLocationCoordinator.hasForegroundLocationPermission(context)
    val bgNow = ZoneLocationCoordinator.hasBackgroundLocationPermission(context)
    val batNow = ZoneLocationCoordinator.isIgnoringBatteryOptimizations(context)
    // If a permission was revoked while the app was in background, re-enable the gate
    if ((hasFg && !fgNow) || (hasBg && !bgNow)) {
      userDismissedGateForSession = false
    }
    hasFg = fgNow
    hasBg = bgNow
    hasBattery = batNow
    onPermissionsUpdated(fgNow, bgNow, batNow)
  }

  val fgPermissionLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
      refreshPermissionsState()
    }

  val bgPermissionLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.RequestPermission()
    ) {
      refreshPermissionsState()
    }

  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        refreshPermissionsState()
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  LaunchedEffect(Unit) {
    refreshPermissionsState()
  }

  // Step 1 auto-launch for foreground location ("While using the app")
  LaunchedEffect(hasFg, fgAutoPrompted, userDismissedGateForSession) {
    if (!hasFg && !fgAutoPrompted && !userDismissedGateForSession) {
      fgAutoPrompted = true
      fgPermissionLauncher.launch(
        arrayOf(
          Manifest.permission.ACCESS_FINE_LOCATION,
          Manifest.permission.ACCESS_COARSE_LOCATION,
        )
      )
    }
  }

  val needsGate = (!hasFg || !hasBg || !hasBattery) && !userDismissedGateForSession

  Box(modifier = Modifier.fillMaxSize()) {
    content()

    if (needsGate) {
      Surface(
        modifier = Modifier.fillMaxSize().testTag("location_permission_gate_surface"),
        color = Color(0xF50E1117),
      ) {
        Box(
          modifier = Modifier.fillMaxSize().padding(24.dp),
          contentAlignment = Alignment.Center,
        ) {
          Column(
            modifier =
              Modifier.fillMaxWidth()
                .widthIn(max = 520.dp)
                .background(Color(0xFF171B24), RoundedCornerShape(20.dp))
                .border(1.dp, Color(0xFF2D3548), RoundedCornerShape(20.dp))
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
          ) {
            when {
              // STEP 1: Foreground Location ("While using the app")
              !hasFg -> {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                  Icon(
                    imageVector = Icons.Outlined.LocationOn,
                    contentDescription = "Location permission",
                    tint = Color(0xFF8AB4F8),
                    modifier = Modifier.size(28.dp),
                  )
                  Text(
                    text = "Step 1 of 3 · Location while using Rivia",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFE8EAED),
                  )
                }

                Text(
                  text =
                    "Rivia first needs foreground location permission (\"While using the app\") before Android allows enabling background zone arrival detection.",
                  style = MaterialTheme.typography.bodyMedium,
                  color = Color(0xFFBDC1C6),
                )

                Button(
                  onClick = {
                    fgPermissionLauncher.launch(
                      arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                      )
                    )
                  },
                  modifier = Modifier.fillMaxWidth().testTag("grant_foreground_location_button"),
                  colors =
                    ButtonDefaults.buttonColors(
                      containerColor = Color(0xFF8AB4F8),
                      contentColor = Color(0xFF0E1117),
                    ),
                ) {
                  Text("Allow location while using the app", fontWeight = FontWeight.SemiBold)
                }

                OutlinedButton(
                  onClick = { userDismissedGateForSession = true },
                  modifier = Modifier.fillMaxWidth().testTag("skip_location_permission_button"),
                ) {
                  Text("Not now (zones will stay inactive)", color = Color(0xFF9AA0A6))
                }
              }

              // STEP 2: Background Location Explanation Screen ("Allow all the time")
              !hasBg -> {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                  Icon(
                    imageVector = Icons.Outlined.Security,
                    contentDescription = "Background location explanation",
                    tint = Color(0xFF81C995),
                    modifier = Modifier.size(28.dp),
                  )
                  Text(
                    text = "Step 2 of 3 · Why Rivia needs location all the time",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFE8EAED),
                  )
                }

                Text(
                  text =
                    "Rivia needs \"Allow all the time\" location access to notice when Jordan arrives at or leaves home, the farm, the gym, Planet Fitness, or work, even when the app is closed.",
                  style = MaterialTheme.typography.bodyMedium,
                  color = Color(0xFFE8EAED),
                )

                Column(
                  modifier =
                    Modifier.fillMaxWidth()
                      .background(Color(0xFF10131A), RoundedCornerShape(12.dp))
                      .border(1.dp, Color(0xFF262D3D), RoundedCornerShape(12.dp))
                      .padding(14.dp),
                  verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  Text(
                    text = "• Only arrive/leave events and arrival times go to the tablet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFBDC1C6),
                  )
                  Text(
                    text = "• No location trail is stored.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFBDC1C6),
                  )
                  Text(
                    text = "• Five places: home, the farm, the gym, Planet Fitness, and work.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFBDC1C6),
                  )
                }

                Button(
                  onClick = {
                    runCatching {
                      bgPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    }.onFailure {
                      runCatching {
                        context.startActivity(
                          ZoneLocationCoordinator.buildAppDetailsSettingsIntent(context)
                        )
                      }
                    }
                  },
                  modifier = Modifier.fillMaxWidth().testTag("open_background_location_settings_button"),
                  colors =
                    ButtonDefaults.buttonColors(
                      containerColor = Color(0xFF81C995),
                      contentColor = Color(0xFF0E1117),
                    ),
                ) {
                  Text("Open settings & choose \"Allow all the time\"", fontWeight = FontWeight.SemiBold)
                }

                OutlinedButton(
                  onClick = { userDismissedGateForSession = true },
                  modifier = Modifier.fillMaxWidth().testTag("skip_background_location_button"),
                ) {
                  Text("Not now", color = Color(0xFF9AA0A6))
                }
              }

              // STEP 3: Battery Optimization Exemption + Samsung Note
              else -> {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                  Icon(
                    imageVector = Icons.Outlined.BatteryChargingFull,
                    contentDescription = "Battery exemption",
                    tint = Color(0xFFFDD663),
                    modifier = Modifier.size(28.dp),
                  )
                  Text(
                    text = "Step 3 of 3 · Unrestricted battery for zone arrivals",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFE8EAED),
                  )
                }

                Text(
                  text =
                    "Allow Rivia (observer.rivia.app) to run unrestricted in the background so geofence arrivals, 15-minute fallback checks, and leave-by alarms are never delayed while your phone is asleep.",
                  style = MaterialTheme.typography.bodyMedium,
                  color = Color(0xFFBDC1C6),
                )

                Box(
                  modifier =
                    Modifier.fillMaxWidth()
                      .background(Color(0xFF1E1B14), RoundedCornerShape(12.dp))
                      .border(1.dp, Color(0xFF5A4B24), RoundedCornerShape(12.dp))
                      .padding(14.dp)
                ) {
                  Text(
                    text = ZoneLocationCoordinator.SAMSUNG_NEVER_SLEEPING_NOTE,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFFDD663),
                  )
                }

                Button(
                  onClick = {
                    runCatching {
                      context.startActivity(
                        ZoneLocationCoordinator.buildRequestIgnoreBatteryOptimizationsIntent(
                          context
                        )
                      )
                    }.onFailure {
                      runCatching {
                        context.startActivity(
                          ZoneLocationCoordinator.buildAppDetailsSettingsIntent(context)
                        )
                      }
                    }
                  },
                  modifier = Modifier.fillMaxWidth().testTag("request_battery_exemption_button"),
                  colors =
                    ButtonDefaults.buttonColors(
                      containerColor = Color(0xFFFDD663),
                      contentColor = Color(0xFF0E1117),
                    ),
                ) {
                  Text("Allow battery exemption", fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(2.dp))

                OutlinedButton(
                  onClick = { userDismissedGateForSession = true },
                  modifier = Modifier.fillMaxWidth().testTag("continue_after_battery_step_button"),
                ) {
                  Text("Continue to Rivia", color = Color(0xFFE8EAED))
                }
              }
            }
          }
        }
      }
    }
  }
}
