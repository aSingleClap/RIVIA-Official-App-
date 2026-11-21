package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.LocalRiviaParticleFieldController
import com.example.ui.components.LocationPermissionGate
import com.example.ui.components.RiviaParticleFieldCanvas
import com.example.ui.components.rememberRiviaParticleFieldController
import com.example.ui.screens.BotsScreen
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.ComingSoonScreen
import com.example.ui.screens.LayersScreen
import com.example.ui.screens.LiveModeScreen
import com.example.ui.screens.RunSettingsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TestsScreen
import com.example.ui.theme.RiviaBackground
import com.example.ui.theme.RiviaTheme
import com.example.viewmodel.RiviaViewModel

enum class RiviaDestination {
  CHAT,
  PIPELINE_COMING_SOON,
  LAYERS_COMING_SOON,
  LAYERS,
  TESTS,
  BOTS,
  RUN_SETTINGS_CHAT,
  RUN_SETTINGS_DEFAULT,
  SETTINGS,
  LIVE_MODE,
}

class MainActivity : ComponentActivity() {
  private val viewModel: RiviaViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.dark(0xFF0A0E0D.toInt()),
      navigationBarStyle = SystemBarStyle.dark(0xFF0A0E0D.toInt()),
    )
    setContent {
      val appUiMode by viewModel.appUiMode.collectAsStateWithLifecycle()
      RiviaTheme(appUiMode = appUiMode) {
        LocationPermissionGate(
          onPermissionsUpdated = { hasFg, hasBg, hasBattery ->
            viewModel.onLocationPermissionsUpdated(hasFg, hasBg, hasBattery)
          }
        ) {
          RiviaAppRoot(viewModel = viewModel)
        }
      }
    }
  }

  override fun onResume() {
    super.onResume()
    viewModel.rearmPendingScheduleAlertsIfPermitted()
    viewModel.refreshZonePermissionsState()
  }
}

@Composable
fun RiviaAppRoot(viewModel: RiviaViewModel) {
  val appUiMode by viewModel.appUiMode.collectAsStateWithLifecycle()
  var currentDestination by rememberSaveable { mutableStateOf(RiviaDestination.CHAT) }
  var previousDestinationBeforeLive by rememberSaveable { mutableStateOf(RiviaDestination.CHAT) }
  var layersFocusTarget by rememberSaveable { mutableStateOf<String?>(null) }

  val particleController = rememberRiviaParticleFieldController()
  val sphereVisibleOnDestination =
    currentDestination == RiviaDestination.CHAT || currentDestination == RiviaDestination.LIVE_MODE

  LaunchedEffect(sphereVisibleOnDestination) {
    particleController.hostVisible = sphereVisibleOnDestination
    if (!sphereVisibleOnDestination) {
      particleController.elevateAboveDrawerScrim = false
    }
  }

  RiviaTheme(appUiMode = appUiMode) {
    CompositionLocalProvider(LocalRiviaParticleFieldController provides particleController) {
      Box(
        modifier = Modifier.fillMaxSize().background(RiviaBackground),
      ) {
        // Single persistent sphere + transition particle system living above all screens
        RiviaParticleFieldCanvas(
          controller = particleController,
          modifier =
            Modifier.fillMaxSize()
              .zIndex(if (particleController.elevateAboveDrawerScrim) 2f else 0f),
        )

        Box(
          modifier = Modifier.fillMaxSize().zIndex(1f),
        ) {
          when (currentDestination) {
            RiviaDestination.CHAT -> {
              ChatScreen(
                viewModel = viewModel,
                onOpenRunSettings = { currentDestination = RiviaDestination.RUN_SETTINGS_CHAT },
                onOpenGlobalSettings = { currentDestination = RiviaDestination.SETTINGS },
                onOpenLiveMode = {
                  previousDestinationBeforeLive = RiviaDestination.CHAT
                  currentDestination = RiviaDestination.LIVE_MODE
                },
                onOpenPipeline = {
                  currentDestination = RiviaDestination.PIPELINE_COMING_SOON
                },
                onOpenLayers = { focusLayer ->
                  layersFocusTarget = focusLayer
                  currentDestination = RiviaDestination.LAYERS
                },
                onOpenTests = { currentDestination = RiviaDestination.TESTS },
                onOpenBots = { currentDestination = RiviaDestination.BOTS },
              )
            }
            RiviaDestination.PIPELINE_COMING_SOON -> {
              ComingSoonScreen(
                title = "Pipeline",
                subtitle =
                  "Multi-stage turn routing, triage, and voice orchestration pipeline controls are coming soon.",
                isPipeline = true,
                appUiMode = appUiMode,
                onBack = { currentDestination = RiviaDestination.CHAT },
              )
            }
            RiviaDestination.LAYERS_COMING_SOON -> {
              ComingSoonScreen(
                title = "Layers",
                subtitle =
                  "Consumer-friendly visual layer overview is coming soon. Switch to Dev or Labs mode to inspect or edit Persona, Style, Engine, and Hard Rules.",
                isPipeline = false,
                appUiMode = appUiMode,
                onBack = { currentDestination = RiviaDestination.CHAT },
              )
            }
            RiviaDestination.TESTS -> {
              BackHandler { currentDestination = RiviaDestination.CHAT }
              TestsScreen(
                viewModel = viewModel,
                onOpenLiveMode = {
                  previousDestinationBeforeLive = RiviaDestination.TESTS
                  currentDestination = RiviaDestination.LIVE_MODE
                },
                onOpenLabSettings = { currentDestination = RiviaDestination.SETTINGS },
                onBack = { currentDestination = RiviaDestination.CHAT },
              )
            }
            RiviaDestination.BOTS -> {
              BotsScreen(
                viewModel = viewModel,
                onBack = { currentDestination = RiviaDestination.CHAT },
              )
            }
            RiviaDestination.LAYERS -> {
              BackHandler { currentDestination = RiviaDestination.CHAT }
              LayersScreen(
                viewModel = viewModel,
                initialFocusLayer = layersFocusTarget,
                onNavigateBack = { currentDestination = RiviaDestination.CHAT },
              )
            }
            RiviaDestination.RUN_SETTINGS_CHAT -> {
              RunSettingsScreen(
                viewModel = viewModel,
                isEditingDefaultsOnly = false,
                onBack = { currentDestination = RiviaDestination.CHAT },
              )
            }
            RiviaDestination.RUN_SETTINGS_DEFAULT -> {
              RunSettingsScreen(
                viewModel = viewModel,
                isEditingDefaultsOnly = true,
                onBack = { currentDestination = RiviaDestination.SETTINGS },
              )
            }
            RiviaDestination.SETTINGS -> {
              SettingsScreen(
                viewModel = viewModel,
                onOpenDefaultRunSettings = {
                  currentDestination = RiviaDestination.RUN_SETTINGS_DEFAULT
                },
                onBack = { currentDestination = RiviaDestination.CHAT },
              )
            }
            RiviaDestination.LIVE_MODE -> {
              LiveModeScreen(
                viewModel = viewModel,
                onEndLive = { currentDestination = previousDestinationBeforeLive },
              )
            }
          }
        }
      }
    }
  }
}
