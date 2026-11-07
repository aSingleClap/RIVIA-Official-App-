package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color

private fun buildRiviaDarkColorScheme(accent: Color) =
  darkColorScheme(
    primary = accent,
    onPrimary = RiviaOnAccent,
    primaryContainer = RiviaSurface2,
    onPrimaryContainer = RiviaTextPrimary,
    secondary = accent,
    onSecondary = RiviaOnAccent,
    secondaryContainer = RiviaSurface2,
    onSecondaryContainer = RiviaTextPrimary,
    tertiary = RiviaWarningAmber,
    onTertiary = RiviaOnAccent,
    background = RiviaBackground,
    onBackground = RiviaTextPrimary,
    surface = RiviaSurface1,
    onSurface = RiviaTextPrimary,
    surfaceVariant = RiviaSurface2,
    onSurfaceVariant = RiviaTextSecondary,
    surfaceContainerLowest = RiviaBackground,
    surfaceContainerLow = RiviaSurface1,
    surfaceContainer = RiviaSurface1,
    surfaceContainerHigh = RiviaSurface2,
    surfaceContainerHighest = RiviaSurface3,
    outline = RiviaBorder,
    outlineVariant = RiviaBorder,
    error = RiviaErrorRed,
    onError = RiviaOnAccent,
  )

@Composable
fun RiviaTheme(
  appUiMode: AppUiMode = AppUiMode.RIVIA,
  content: @Composable () -> Unit,
) {
  val modeAccent = accentColorForMode(appUiMode)
  if (RiviaMint != modeAccent) {
    RiviaMint = modeAccent
  }
  SideEffect {
    RiviaMint = modeAccent
  }
  val scheme = buildRiviaDarkColorScheme(modeAccent)

  // Rivia is strictly dark theme only with mode-reactive accent tokens
  MaterialTheme(
    colorScheme = scheme,
    typography = Typography,
    content = content,
  )
}

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true,
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  RiviaTheme(content = content)
}
