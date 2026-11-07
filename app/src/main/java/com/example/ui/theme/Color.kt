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

// 1. NEUTRALS (all modes)
val RiviaBackground = Color(0xFF000000) // True black
val RiviaSurface1 = Color(0xFF0E0E0E) // Drawer, sheets, dialogs
val RiviaSurface2 = Color(0xFF161616) // Cards, inputs, buttons, chips
val RiviaSurface3 = Color(0xFF1C1C1C) // My message bubbles, selected rows
val RiviaBorder = Color(0xFF262626) // Borders and dividers
val RiviaTextPrimary = Color(0xFFEDEDED) // Text primary
val RiviaTextSecondary = Color(0xFFA3A3A3) // Text secondary
val RiviaTextTertiary = Color(0xFF8F8F8F) // Timestamps, hints, placeholders, info icons
val RiviaIconDefault = Color(0xFFA3A3A3) // Icons default
val RiviaIconActive = Color(0xFFEDEDED) // Icons active

// Legacy aliases mapped to the new neutral hierarchy
val RiviaSurface = RiviaSurface1
val RiviaRaisedSurface = RiviaSurface2
val RiviaText = RiviaTextPrimary
val RiviaMutedText = RiviaTextSecondary

// 2. MODE ACCENTS
val RiviaModeWhite = Color(0xFFF5F5F5)
val RiviaModeMint = RiviaModeWhite // Replaces mint #5DCAA5 everywhere with white #F5F5F5
val RiviaModeViolet = Color(0xFFA78BFA)
val RiviaModeBlue = Color(0xFF60A5FA)
val RiviaOnAccent = Color(0xFF000000) // Black for text and icons drawn on top of mode accents

// Reactive primary accent token (shifts between White #F5F5F5 in Rivia, Violet #A78BFA in Dev, and Blue #60A5FA in Labs)
var RiviaMint by mutableStateOf(RiviaModeWhite)
var RiviaAccent: Color
  get() = RiviaMint
  set(value) {
    RiviaMint = value
  }

// Reserved status-only colors (never change with the mode theme)
val RiviaWarningAmber = Color(0xFFF2C14E)
val RiviaErrorRed = Color(0xFFFF6B6B)

fun accentColorForMode(mode: AppUiMode): Color {
  return when (mode) {
    AppUiMode.RIVIA -> RiviaModeWhite
    AppUiMode.DEV -> RiviaModeViolet
    AppUiMode.LABS -> RiviaModeBlue
  }
}
