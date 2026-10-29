package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val RiviaDarkColorScheme =
  darkColorScheme(
    primary = RiviaMint,
    onPrimary = Color(0xFF0A0E0D),
    primaryContainer = RiviaRaisedSurface,
    onPrimaryContainer = RiviaMint,
    secondary = RiviaMint,
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
fun RiviaTheme(content: @Composable () -> Unit) {
  // Rivia is strictly dark theme only with exact palette tokens
  MaterialTheme(
    colorScheme = RiviaDarkColorScheme,
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
