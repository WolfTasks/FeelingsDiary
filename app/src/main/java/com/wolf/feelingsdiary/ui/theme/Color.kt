package com.wolf.feelingsdiary.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

val DarkColors: ColorScheme =
    darkColorScheme(
        background = Color(0xFF0D1830),
        surface = Color(0xFF1A2744),
        surfaceVariant = Color(0xFF243559),
        primary = Color(0xFFC8D8E8),
        onPrimary = Color(0xFF0D1830),
        onSurfaceVariant = Color(0xFF9BB0CC),
        onBackground = Color(0xFFFFFFFF),
        onSurface = Color(0xFFFFFFFF),
    )

val LightColors: ColorScheme =
    lightColorScheme(
        background = Color(0xFFF4F7FB),
        surface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFFE3EAF3),
        primary = Color(0xFF243559),
        onPrimary = Color(0xFFFFFFFF),
        onSurfaceVariant = Color(0xFF4F6488),
        onBackground = Color(0xFF0D1830),
        onSurface = Color(0xFF0D1830),
    )
