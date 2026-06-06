package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val BentoColorScheme = lightColorScheme(
    primary = BentoAlertHigh,
    secondary = BentoPurpleOn,
    tertiary = BentoAlertMedium,
    background = BentoBackground,
    surface = BentoSurface,
    surfaceVariant = Color(0xFFE7E0EC),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = BentoTextPrimary,
    onSurface = BentoTextPrimary,
    error = BentoAlertHigh,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit,
) {
    // Application theme configured to represent the beautiful Material 3 "Bento Grid" Design Theme
    MaterialTheme(
        colorScheme = BentoColorScheme,
        typography = Typography,
        content = content
    )
}
