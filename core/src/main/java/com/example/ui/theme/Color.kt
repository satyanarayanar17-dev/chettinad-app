package com.example.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Chettinad Care Institutional Theme Colors
// Calm, precise, minimal and professional

val ChettinadPrimary = Color(0xFF124749)
val ChettinadPrimaryContainer = Color(0xFFEAF4F0)
val ChettinadOnPrimaryContainer = Color(0xFF124749)

val ChettinadSecondary = Color(0xFF16754B)
val ChettinadSecondaryContainer = Color(0xFFE9F5ED)
val ChettinadOnSecondaryContainer = Color(0xFF15553B)

val ChettinadError = Color(0xFFBA1A1A)
val ChettinadErrorContainer = Color(0xFFFFDAD6)

val ChettinadBackground = Color(0xFFF3F6F5)
val ChettinadSurface = Color(0xFFFFFFFF)
val ChettinadSurfaceVariant = Color(0xFFEAF4F0)

val ChettinadOnBackground = Color(0xFF203B3C)
val ChettinadOnSurface = Color(0xFF203B3C)
val ChettinadOnSurfaceVariant = Color(0xFF607575)

val LightColorScheme = lightColorScheme(
    primary = ChettinadPrimary,
    onPrimary = Color.White,
    primaryContainer = ChettinadPrimaryContainer,
    onPrimaryContainer = ChettinadOnPrimaryContainer,
    secondary = ChettinadSecondary,
    onSecondary = Color.White,
    secondaryContainer = ChettinadSecondaryContainer,
    onSecondaryContainer = ChettinadOnSecondaryContainer,
    error = ChettinadError,
    onError = Color.White,
    errorContainer = ChettinadErrorContainer,
    onErrorContainer = Color.Black,
    background = ChettinadBackground,
    onBackground = ChettinadOnBackground,
    surface = ChettinadSurface,
    onSurface = ChettinadOnSurface,
    surfaceVariant = ChettinadSurfaceVariant,
    outline = Color(0xFFCBDCD3),
    outlineVariant = Color(0xFFE0E9E5),
    onSurfaceVariant = ChettinadOnSurfaceVariant,
)

val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF64B5F6),
    onPrimary = Color(0xFF003258),
    primaryContainer = Color(0xFF00497D),
    onPrimaryContainer = Color(0xFFD1E4F9),
    secondary = Color(0xFF4DB6AC),
    onSecondary = Color(0xFF003730),
    secondaryContainer = Color(0xFF005047),
    onSecondaryContainer = Color(0xFFE9F5ED),
    error = Color(0xFFEF9A9A),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF121212),
    onBackground = Color(0xFFE0E0E0),
    surface = Color(0xFF203B3C),
    onSurface = Color(0xFFE0E0E0),
    surfaceVariant = Color(0xFF323232),
    onSurfaceVariant = Color(0xFFBDBDBD),
)
