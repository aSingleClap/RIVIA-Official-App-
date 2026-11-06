package com.example.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

enum class AppUiMode(val displayName: String) {
  RIVIA("Rivia"),
  DEV("Dev"),
  LABS("Labs");

  companion object {
    fun fromKey(raw: String?): AppUiMode {
      return when (raw?.trim()?.uppercase()) {
        "DEV" -> DEV
        "LABS" -> LABS
        else -> RIVIA
      }
    }
  }
}

// Section 1: Exact Look Colors
val RiviaBackground = Color(0xFF0A0E0D)
val RiviaSurface = Color(0xFF121917)
val RiviaRaisedSurface = Color(0xFF18211E)
val RiviaBorder = Color(0xFF24302C)
val RiviaText = Color(0xFFE4EBE8)
val RiviaMutedText = Color(0xFF9AA8A2)

// Fixed per-mode signature colors
val RiviaModeMint = Color(0xFF5DCAA5)
val RiviaModeViolet = Color(0xFFA78BFA)
val RiviaModeBlue = Color(0xFF60A5FA)

// Reactive primary accent token (shifts between Mint in Rivia, Violet in Dev, and Blue in Labs)
var RiviaMint by mutableStateOf(RiviaModeMint)

// Reserved status-only colors (never change with the mode theme)
val RiviaWarningAmber = Color(0xFFF2C14E)
val RiviaErrorRed = Color(0xFFFF6B6B)

fun accentColorForMode(mode: AppUiMode): Color {
  return when (mode) {
    AppUiMode.RIVIA -> RiviaModeMint
    AppUiMode.DEV -> RiviaModeViolet
    AppUiMode.LABS -> RiviaModeBlue
  }
}


