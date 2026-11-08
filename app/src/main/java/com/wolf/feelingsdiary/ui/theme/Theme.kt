package com.wolf.feelingsdiary.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.wolf.feelingsdiary.domain.ThemeMode

/** Whether [ThemeMode] resolves to the dark colour scheme right now. */
@Composable
fun ThemeMode.isDark(): Boolean =
    when (this) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

@Composable
fun FeelingsDiaryTheme(
    theme: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (theme.isDark()) DarkColors else LightColors,
        typography = Typography,
        content = content,
    )
}
