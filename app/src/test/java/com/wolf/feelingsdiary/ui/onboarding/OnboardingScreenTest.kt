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

    // The pending "after the answer" of the last permission request.
    private var answer: (() -> Unit)? = null
    private var settingsOpened = 0
    private var batterySettingsOpened = 0

    private fun str(id: Int) = ctx.getString(id)

    private fun request(onAnswer: () -> Unit) {
        permissionRequests++
        answer = onAnswer
    }

    private fun show(allowed: Boolean = false) {
        rule.setContent {
            FeelingsDiaryTheme {
                OnboardingScreen(allowed, ::request, { settingsOpened++ }) { enabled, count -> starts += enabled to count }
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

    private fun showBattery(restricted: Boolean) {
        rule.setContent {
            FeelingsDiaryTheme {
                OnboardingScreen(
                    true,
                    ::request,
                    { settingsOpened++ },
                    batteryRestricted = restricted,
                    onOpenBatterySettings = { batterySettingsOpened++ },
                ) { enabled, count -> starts += enabled to count }
            }
        }
    }

    @Test
    fun batteryHintShownWhenRestrictedAndOpensSettings() {
        showBattery(restricted = true)
        rule.onNodeWithText(str(R.string.reminder_battery_title)).performScrollTo().assertExists()
        rule.onNodeWithText(str(R.string.reminder_battery_open)).performScrollTo().performClick()
        assertEquals(1, batterySettingsOpened)
    }

    @Test
    fun batteryHintHiddenWhenUnrestricted() {
        showBattery(restricted = false)
        rule.onNodeWithText(str(R.string.reminder_battery_title)).assertDoesNotExist()
    }

    @Test
    fun batteryHintHiddenWhenRemindersSwitchedOff() {
        showBattery(restricted = true)
        rule.onNodeWithText(str(R.string.reminder_switch)).performScrollTo().performClick()
        rule.onNodeWithText(str(R.string.reminder_battery_title)).assertDoesNotExist()
    }

    @Test
    fun startWithDefaultsStoresThreeReminders() {
        show(allowed = true)
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
                OnboardingScreen(allowed, ::request, { settingsOpened++ }) { enabled, count -> starts += enabled to count }
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

    /** Reminders on without permission: "Let's start" asks once and starts whatever the answer. */
    @Test
    @Config(sdk = [33])
    fun startAsksForPermissionThenStarts() {
        show(allowed = false)
        val start = rule.onNodeWithText(str(R.string.onboarding_start))
        start.performClick()
        assertEquals(1, permissionRequests)
        assertEquals(emptyList<Pair<Boolean, Int>>(), starts)
        answer!!()
        assertEquals(listOf(true to 3), starts)
        // Asked once: a further tap starts at once.
        start.performClick()
        assertEquals(1, permissionRequests)
        assertEquals(listOf(true to 3, true to 3), starts)
    }

    @Test
    @Config(sdk = [33])
    fun startDoesNotAskAgainAfterAllow() {
        show(allowed = false)
        rule.onNodeWithText(str(R.string.onboarding_allow)).performScrollTo().performClick()
        assertEquals(1, permissionRequests)
        rule.onNodeWithText(str(R.string.onboarding_start)).performClick()
        assertEquals(1, permissionRequests)
        assertEquals(listOf(true to 3), starts)
    }

    @Test
    @Config(sdk = [33])
    fun startWithoutRemindersDoesNotAsk() {
        show(allowed = false)
        rule.onNodeWithText(str(R.string.reminder_switch)).performScrollTo().performClick()
        rule.onNodeWithText(str(R.string.onboarding_start)).performClick()
        assertEquals(0, permissionRequests)
        assertEquals(listOf(false to 3), starts)
    }

    @Test
    @Config(sdk = [32])
    fun startNeedsNoPermissionBelowApi33() {
        show(allowed = false)
        rule.onNodeWithText(str(R.string.onboarding_start)).performClick()
        assertEquals(0, permissionRequests)
        assertEquals(listOf(true to 3), starts)
    }

    @Test
    @Config(sdk = [32])
    fun allowButtonOnlyOnApi33() {
        show(allowed = false)
        rule.onNodeWithText(str(R.string.onboarding_title)).assertExists()
        rule.onNodeWithText(str(R.string.onboarding_allow)).assertDoesNotExist()
    }
}
