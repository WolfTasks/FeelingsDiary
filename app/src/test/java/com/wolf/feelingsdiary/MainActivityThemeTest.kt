package com.wolf.feelingsdiary

import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.core.view.WindowCompat
import com.wolf.feelingsdiary.domain.ThemeMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLooper

/** System bar icons follow the effective theme. */
@RunWith(RobolectricTestRunner::class)
class MainActivityThemeTest {
    @get:Rule
    val rule = createEmptyComposeRule()

    private fun MainActivity.lightBarIcons() = WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars

    @Test
    fun lightThemeSwitchesToDarkBarIcons() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        rule.waitForIdle()
        assertFalse(activity.lightBarIcons())
        runBlocking { activity.settingsRepository.update { it.copy(theme = ThemeMode.LIGHT) } }
        // The DataStore emits from its own thread; let the main looper deliver it.
        repeat(50) {
            if (activity.lightBarIcons()) return@repeat
            Thread.sleep(20)
            ShadowLooper.idleMainLooper()
            rule.waitForIdle()
        }
        assertTrue(activity.lightBarIcons())
        runBlocking { activity.settingsRepository.update { it.copy(theme = ThemeMode.DARK) } }
    }
}
