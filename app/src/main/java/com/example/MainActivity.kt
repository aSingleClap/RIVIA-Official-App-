package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.LiveModeScreen
import com.example.ui.screens.RunSettingsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.RiviaTheme
import com.example.viewmodel.RiviaViewModel

enum class RiviaDestination {
  CHAT,
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
      RiviaTheme {
        RiviaAppRoot(viewModel = viewModel)
      }
    }
  }
}

@Composable
fun RiviaAppRoot(viewModel: RiviaViewModel) {
  var currentDestination by rememberSaveable { mutableStateOf(RiviaDestination.CHAT) }

  when (currentDestination) {
    RiviaDestination.CHAT -> {
      ChatScreen(
        viewModel = viewModel,
        onOpenRunSettings = { currentDestination = RiviaDestination.RUN_SETTINGS_CHAT },
        onOpenGlobalSettings = { currentDestination = RiviaDestination.SETTINGS },
        onOpenLiveMode = { currentDestination = RiviaDestination.LIVE_MODE },
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
        onEndLive = { currentDestination = RiviaDestination.CHAT },
      )
    }
  }
}
