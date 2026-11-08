package com.wolf.feelingsdiary.ui.onboarding

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.wolf.feelingsdiary.R
import com.wolf.feelingsdiary.ui.theme.FeelingsDiaryTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class OnboardingScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val starts = mutableListOf<Pair<Boolean, Int>>()
    private var permissionRequests = 0
    private var settingsOpened = 0

    private fun str(id: Int) = ctx.getString(id)

    private fun show(allowed: Boolean = false) {
        rule.setContent {
            FeelingsDiaryTheme {
                OnboardingScreen(allowed, { permissionRequests++ }, { settingsOpened++ }) { enabled, count -> starts += enabled to count }
            }
        }
    }

    @Test
    fun showsPurposeAndPrivacy() {
        show()
        rule.onNodeWithText(str(R.string.onboarding_title)).assertExists()
        rule.onNodeWithText(str(R.string.onboarding_text)).assertExists()
        rule.onNodeWithText(str(R.string.onboarding_settings_hint)).performScrollTo().assertExists()
    }

    @Test
    fun startWithDefaultsStoresThreeReminders() {
        show()
        rule.onNodeWithText(str(R.string.onboarding_start)).performClick()
        assertEquals(listOf(true to 3), starts)
    }

    @Test
    fun startPassesChosenCountAndSwitch() {
        show()
        rule.onNodeWithText("2").performScrollTo().performClick()
        rule.onNodeWithText(str(R.string.reminder_switch)).performScrollTo().performClick()
        // Off: the count stays as chosen but cannot be changed.
        rule.onNodeWithText("4").assertIsNotEnabled()
        rule.onNodeWithText(str(R.string.onboarding_start)).performClick()
        assertEquals(listOf(false to 2), starts)
    }

    @Test
    @Config(sdk = [33])
    fun allowButtonAsksOnceThenOpensSettings() {
        var allowed by mutableStateOf(false)
        rule.setContent {
            FeelingsDiaryTheme {
                OnboardingScreen(allowed, { permissionRequests++ }, { settingsOpened++ }) { enabled, count -> starts += enabled to count }
            }
        }
        val allow = rule.onNodeWithText(str(R.string.onboarding_allow))
        allow.performScrollTo().performClick()
        assertEquals(1, permissionRequests)
        // No loop of system dialogs: the second tap leads to the app's notification settings.
        allow.performScrollTo().performClick()
        assertEquals(1, permissionRequests)
        assertEquals(1, settingsOpened)
        allowed = true
        rule.onNodeWithText(str(R.string.onboarding_allow)).assertDoesNotExist()
    }

    @Test
    @Config(sdk = [32])
    fun allowButtonOnlyOnApi33() {
        show(allowed = false)
        rule.onNodeWithText(str(R.string.onboarding_title)).assertExists()
        rule.onNodeWithText(str(R.string.onboarding_allow)).assertDoesNotExist()
    }
}
