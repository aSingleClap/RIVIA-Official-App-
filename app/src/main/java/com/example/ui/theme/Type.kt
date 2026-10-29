package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font as GoogleFontImpl
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import com.example.R

val GoogleFontProvider =
  GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
  )

private val ChivoGoogleFont = GoogleFont("Chivo")
private val ChivoMonoGoogleFont = GoogleFont("Chivo Mono")

// Chivo for text with system sans-serif fallback
val ChivoFontFamily =
  FontFamily(
    GoogleFontImpl(
      googleFont = ChivoGoogleFont,
      fontProvider = GoogleFontProvider,
      weight = FontWeight.Normal,
    ),
    GoogleFontImpl(
      googleFont = ChivoGoogleFont,
      fontProvider = GoogleFontProvider,
      weight = FontWeight.Medium,
    ),
    GoogleFontImpl(
      googleFont = ChivoGoogleFont,
      fontProvider = GoogleFontProvider,
      weight = FontWeight.SemiBold,
    ),
    GoogleFontImpl(
      googleFont = ChivoGoogleFont,
      fontProvider = GoogleFontProvider,
      weight = FontWeight.Bold,
    ),
    Font(familyName = DeviceFontFamilyName("sans-serif"), weight = FontWeight.Normal),
    Font(familyName = DeviceFontFamilyName("sans-serif-medium"), weight = FontWeight.Medium),
  )

// Chivo Mono for times, IDs and numbers with system monospace fallback
val ChivoMonoFontFamily =
  FontFamily(
    GoogleFontImpl(
      googleFont = ChivoMonoGoogleFont,
      fontProvider = GoogleFontProvider,
      weight = FontWeight.Normal,
    ),
    GoogleFontImpl(
      googleFont = ChivoMonoGoogleFont,
      fontProvider = GoogleFontProvider,
      weight = FontWeight.Medium,
    ),
    GoogleFontImpl(
      googleFont = ChivoMonoGoogleFont,
      fontProvider = GoogleFontProvider,
      weight = FontWeight.Bold,
    ),
    Font(familyName = DeviceFontFamilyName("monospace"), weight = FontWeight.Normal),
  )

val Typography =
  Typography(
    headlineLarge =
      TextStyle(
        fontFamily = ChivoFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
      ),
    headlineMedium =
      TextStyle(
        fontFamily = ChivoFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
      ),
    titleLarge =
      TextStyle(
        fontFamily = ChivoFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
      ),
    titleMedium =
      TextStyle(
        fontFamily = ChivoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
      ),
    titleSmall =
      TextStyle(
        fontFamily = ChivoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
      ),
    bodyLarge =
      TextStyle(
        fontFamily = ChivoFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
      ),
    bodyMedium =
      TextStyle(
        fontFamily = ChivoFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
      ),
    bodySmall =
      TextStyle(
        fontFamily = ChivoFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
      ),
    labelLarge =
      TextStyle(
        fontFamily = ChivoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
      ),
    labelMedium =
      TextStyle(
        fontFamily = ChivoMonoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
      ),
    labelSmall =
      TextStyle(
        fontFamily = ChivoMonoFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 16.sp,
      ),
  )
