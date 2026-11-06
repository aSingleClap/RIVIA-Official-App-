package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color

private fun buildRiviaDarkColorScheme(accent: Color) =
  darkColorScheme(
    primary = accent,
    onPrimary = Color(0xFF0A0E0D),
    primaryContainer = RiviaRaisedSurface,
    onPrimaryContainer = accent,
    secondary = accent,
    onSecondary = Color(0xFF0A0E0D),
    secondaryContainer = RiviaRaisedSurface,
    onSecondaryContainer = RiviaText,
    tertiary = RiviaWarningAmber,
    onTertiary = Color(0xFF0A0E0D),
    background = RiviaBackground,
    onBackground = RiviaText,
    surface = RiviaSurface,
    onSurface = RiviaText,
    surfaceVariant = RiviaRaisedSurface,
    onSurfaceVariant = RiviaMutedText,
    surfaceContainerLowest = RiviaBackground,
    surfaceContainerLow = RiviaSurface,
    surfaceContainer = RiviaSurface,
    surfaceContainerHigh = RiviaRaisedSurface,
    surfaceContainerHighest = RiviaRaisedSurface,
    outline = RiviaBorder,
    outlineVariant = RiviaBorder,
    error = RiviaErrorRed,
    onError = Color(0xFF0A0E0D),
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
