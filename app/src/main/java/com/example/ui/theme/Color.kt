package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Bento Grid Base Palette (Material 3 Light foundation)
val BentoBackground = Color(0xFFFEF7FF)
val BentoTextPrimary = Color(0xFF1D1B20)
val BentoTextMuted = Color(0xFF49454F)
val BentoSurface = Color(0xFFF3EDF7)
val BentoBorder = Color(0xFFCAC4D0)

// Bento Cell Accent Color Combinations
val BentoBlueBg = Color(0xFFDBE1FF)
val BentoBlueOn = Color(0xFF00174B)

val BentoPurpleBg = Color(0xFFE8DEF8)
val BentoPurpleOn = Color(0xFF21005D)

val BentoCyanBg = Color(0xFFDAFBFF)
val BentoCyanOn = Color(0xFF001F24)

val BentoRedBg = Color(0xFFFFE0DB)
val BentoRedOn = Color(0xFF410002)
val BentoRedMuted = Color(0xFF93000A)

// Threat Level / Alert Colors
val BentoAlertHigh = Color(0xFFB3261E)     // Red
val BentoAlertMedium = Color(0xFFB26A00)   // Amber/Orange
val BentoAlertLow = Color(0xFF15803D)      // M3 light theme compatible green

// Retain legacy variables as backups to prevent compilation errors in un-refactored code
val CyberBackground = BentoBackground
val CyberSurface = BentoSurface
val CyberSurfaceVariant = Color(0xFFE7E0EC)
val CyberPrimary = BentoAlertHigh
val CyberSecondary = BentoAlertHigh
val CyberTertiary = BentoAlertMedium
val CyberOnBackground = BentoTextPrimary
val CyberOnSurface = BentoTextPrimary
val CyberBorder = BentoBorder
val CyberTextMuted = BentoTextMuted
