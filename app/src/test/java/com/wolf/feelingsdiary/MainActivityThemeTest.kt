package com.wolf.feelingsdiary

import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.core.view.WindowCompat
import com.wolf.feelingsdiary.domain.ThemeMode
import com.wolf.feelingsdiary.testutil.resetSettings
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
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

    // Onboarding done: the app starts at Home.
    @Before
    fun onboardingDone() = resetSettings { it.copy(onboardingDone = true) }

    @After
    fun defaults() = resetSettings()

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
    }

    /** A recreation (rotation, language change) starts with the known light theme, not with a dark flash. */
    @Test
    fun recreationStartsLight() {
        resetSettings { it.copy(theme = ThemeMode.LIGHT, onboardingDone = true) }
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        rule.waitUntil(TIMEOUT_MS) { controller.get().lightBarIcons() }
        controller.recreate()
        // Checked before the store had a chance to emit to the new activity.
        assertTrue(controller.get().lightBarIcons())
        rule.waitForIdle()
        assertTrue(controller.get().lightBarIcons())
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
