package com.wolf.feelingsdiary

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wolf.feelingsdiary.data.settings.SettingsRepository
import com.wolf.feelingsdiary.domain.ThemeMode
import com.wolf.feelingsdiary.ui.LocaleSource
import com.wolf.feelingsdiary.ui.navigation.AppNavHost
import com.wolf.feelingsdiary.ui.theme.FeelingsDiaryTheme
import com.wolf.feelingsdiary.ui.theme.isDark
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    // Buffered until the nav graph collects, consumed once; a recreated activity starts empty.
    private val newEntryRequests = Channel<Unit>(Channel.CONFLATED)

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var localeSource: LocaleSource

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Until the settings are loaded: the last known theme (activity recreation), or dark on a cold start,
        // so neither dark nor light users see a flash of the other theme.
        val initialTheme = settingsRepository.lastKnown?.theme ?: ThemeMode.DARK
        // Same edge-to-edge layout on every API level.
        applySystemBars(initialTheme.isDark(resources.configuration))
        // A language change recreates the activity: the new locale relabels feelings in the surviving ViewModels.
        localeSource.update(resources.configuration.locales[0])
        // On recreation (rotation, process death) the launch intent was already handled.
        if (savedInstanceState == null) handle(intent)
        val requests = newEntryRequests.receiveAsFlow()
        val themes = settingsRepository.settings.map { it.theme }.distinctUntilChanged()
        setContent {
            val theme by themes.collectAsStateWithLifecycle(initialTheme)
            val dark = theme.isDark()
            LaunchedEffect(dark) { applySystemBars(dark) }
            FeelingsDiaryTheme(theme) {
                AppNavHost(
                    requests,
                    inputMode = { settingsRepository.settings.first().input },
                    onboardingDone = { settingsRepository.settings.first().onboardingDone },
                )
            }
        }
    }

    /** Bar icons contrast with the effective theme: light icons on dark, dark icons on light. */
    private fun applySystemBars(dark: Boolean) {
        val style =
            if (dark) {
                SystemBarStyle.dark(Color.TRANSPARENT)
            } else {
                // The scrim only shows where dark icons are unsupported (navigation bar on API 26).
                SystemBarStyle.light(Color.TRANSPARENT, LIGHT_THEME_FALLBACK_SCRIM)
            }
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        // Reopening from Recents replays the last launch intent; that must not open another entry.
        val fromHistory = (intent?.flags ?: 0) and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0
        if (intent?.action == ACTION_NEW_ENTRY && !fromHistory) newEntryRequests.trySend(Unit)
    }

    companion object {
        const val ACTION_NEW_ENTRY = "com.wolf.feelingsdiary.NEW_ENTRY"

        // Same value as androidx.activity's default dark scrim.
        private val LIGHT_THEME_FALLBACK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}
