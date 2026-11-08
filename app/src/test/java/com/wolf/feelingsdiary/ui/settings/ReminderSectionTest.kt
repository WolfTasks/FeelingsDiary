package com.wolf.feelingsdiary.ui.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.wolf.feelingsdiary.R
import com.wolf.feelingsdiary.domain.ReminderMode
import com.wolf.feelingsdiary.domain.ReminderSettings
import com.wolf.feelingsdiary.domain.TimeWindow
import com.wolf.feelingsdiary.ui.theme.FeelingsDiaryTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalTime
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class ReminderSectionTest {
    @get:Rule
    val rule = createComposeRule()

    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val changes = mutableListOf<(ReminderSettings) -> ReminderSettings>()
    private var permissionRequests = 0
    private var settingsOpened = 0
    private var batterySettingsOpened = 0

    private fun str(id: Int) = ctx.getString(id)

    private fun time(
        h: Int,
        m: Int = 0,
    ) = formatTime(LocalTime.of(h, m), Locale.getDefault())

    private fun range(
        a: Int,
        b: Int,
    ) = ctx.getString(R.string.reminder_time_range, time(a), time(b))

    private fun show(
        s: ReminderSettings,
        allowed: Boolean = true,
    ) {
        rule.setContent {
            FeelingsDiaryTheme {
                ReminderSection(s, allowed, { changes += it }, { permissionRequests++ }, { settingsOpened++ })
            }
        }
    }

    @Test
    fun countTwoShowsMorningAndEvening() {
        show(ReminderSettings(count = 2))
        rule.onNodeWithText(str(R.string.reminder_window_morning)).assertExists()
        rule.onNodeWithText(str(R.string.reminder_window_evening)).assertExists()
        rule.onNodeWithText(str(R.string.reminder_window_midday)).assertDoesNotExist()
        rule.onNodeWithText(str(R.string.reminder_window_afternoon)).assertDoesNotExist()
        rule.onNodeWithText(range(9, 11)).assertExists()
        rule.onNodeWithText(range(19, 21)).assertExists()
        rule.onNodeWithText(range(22, 8)).assertExists()
    }

    @Test
    fun countButtonChangesCount() {
        show(ReminderSettings(count = 2))
        rule.onNodeWithText("4").performClick()
        assertEquals(4, changes.single()(ReminderSettings(count = 2)).count)
    }

    @Test
    fun fixedModeShowsTimes() {
        show(ReminderSettings(count = 1, mode = ReminderMode.FIXED))
        rule.onNodeWithText(str(R.string.reminder_window_afternoon)).assertExists()
        rule.onNodeWithText(time(16)).assertExists()
        rule.onNodeWithText(range(15, 17)).assertDoesNotExist()
    }

    @Test
    fun endBeforeStartIsRejected() {
        show(ReminderSettings(count = 2))
        rule.onNodeWithText(str(R.string.reminder_window_morning)).performClick()
        // Start dialog: keep 09:00.
        rule.onNodeWithText(str(R.string.dialog_ok)).performClick()
        // End dialog: switch to keyboard input and enter 08:00.
        rule.onNodeWithTag(TIME_DIALOG_MODE_TAG).performClick()
        rule.onAllNodes(hasSetTextAction())[0].performTextReplacement("8")
        rule.onNodeWithText(str(R.string.dialog_ok)).performClick()
        rule.onNodeWithText(str(R.string.reminder_end_before_start)).assertExists()
        assertTrue(changes.isEmpty())
    }

    @Test
    fun validWindowEditCallsOnChange() {
        show(ReminderSettings(count = 2))
        rule.onNodeWithText(str(R.string.reminder_window_morning)).performClick()
        rule.onNodeWithText(str(R.string.dialog_ok)).performClick()
        rule.onNodeWithText(str(R.string.dialog_ok)).performClick()
        val result = changes.single()(ReminderSettings())
        assertEquals(TimeWindow(LocalTime.of(9, 0), LocalTime.of(11, 0)), result.windows[0])
    }

    @Test
    fun windowInQuietShowsHint() {
        show(ReminderSettings(count = 2, quietStart = LocalTime.of(18, 0)))
        // Only the evening window (19–21) lies entirely in quiet hours 18:00–08:00.
        rule.onAllNodesWithText(str(R.string.reminder_quiet_hint)).assertCountEquals(1)
    }

    @Test
    fun permissionWarningOnlyWhenDenied() {
        var allowed by mutableStateOf(false)
        rule.setContent {
            FeelingsDiaryTheme {
                ReminderSection(ReminderSettings(), allowed, { changes += it }, { permissionRequests++ }, { settingsOpened++ })
            }
        }
        rule.onNodeWithText(str(R.string.reminder_notifications_off)).assertExists()
        rule.onNodeWithText(str(R.string.reminder_allow)).performClick()
        assertEquals(1, permissionRequests)
        // After the first request the button leads to the system settings.
        rule.onNodeWithText(str(R.string.reminder_allow)).performClick()
        assertEquals(1, permissionRequests)
        assertEquals(1, settingsOpened)
        allowed = true
        rule.onNodeWithText(str(R.string.reminder_notifications_off)).assertDoesNotExist()
        rule.onNodeWithText(str(R.string.reminder_allow)).assertDoesNotExist()
    }

    private fun showBattery(
        s: ReminderSettings,
        restricted: Boolean,
    ) {
        rule.setContent {
            FeelingsDiaryTheme {
                ReminderSection(
                    s,
                    true,
                    { changes += it },
                    { permissionRequests++ },
                    { settingsOpened++ },
                    batteryRestricted = restricted,
                    onOpenBatterySettings = { batterySettingsOpened++ },
                )
            }
        }
    }

    @Test
    fun batteryHintShownWhenRemindersOnAndRestricted() {
        showBattery(ReminderSettings(), restricted = true)
        rule.onNodeWithText(str(R.string.reminder_battery_title)).assertExists()
        rule.onNodeWithText(str(R.string.reminder_battery_text)).assertExists()
        rule.onNodeWithText(str(R.string.reminder_battery_open)).performClick()
        assertEquals(1, batterySettingsOpened)
    }

    @Test
    fun batteryHintHiddenWhenUnrestrictedOrRemindersOff() {
        showBattery(ReminderSettings(), restricted = false)
        rule.onNodeWithText(str(R.string.reminder_battery_title)).assertDoesNotExist()
        rule.onNodeWithText(str(R.string.reminder_battery_open)).assertDoesNotExist()
    }

    @Test
    fun batteryHintHiddenWhenRemindersOff() {
        showBattery(ReminderSettings(enabled = false), restricted = true)
        rule.onNodeWithText(str(R.string.reminder_battery_title)).assertDoesNotExist()
    }

    @Test
    fun alarmSoundSwitchCallsChange() {
        show(ReminderSettings())
        rule.onNodeWithText(str(R.string.reminder_alarm_sound)).performClick()
        assertTrue(changes.single()(ReminderSettings()).alarmSound)
        rule.onNodeWithText(str(R.string.reminder_alarm_sound_hint)).assertExists()
    }

    @Test
    fun alarmSoundDisabledWhenRemindersOff() {
        show(ReminderSettings(enabled = false))
        rule.onNodeWithText(str(R.string.reminder_alarm_sound)).assertIsNotEnabled()
    }

    @Test
    fun switchOffDisablesRows() {
        show(ReminderSettings(enabled = false, count = 2))
        rule.onNodeWithText(str(R.string.reminder_window_morning)).assertIsNotEnabled()
        rule.onNodeWithText(str(R.string.reminder_window_evening)).assertIsNotEnabled()
        rule.onNodeWithText(str(R.string.reminder_quiet_hours)).assertIsNotEnabled()
        rule.onNodeWithText(str(R.string.reminder_mode_fixed)).assertIsNotEnabled()
        rule.onNodeWithText("3").assertIsNotEnabled()
    }

    @Test
    fun switchTogglesEnabled() {
        show(ReminderSettings())
        rule.onAllNodesWithText(str(R.string.reminder_switch)).onFirst().performClick()
        assertEquals(false, changes.single()(ReminderSettings()).enabled)
    }

    @Test
    fun quietHoursMayWrapMidnight() {
        show(ReminderSettings())
        rule.onNodeWithText(str(R.string.reminder_quiet_hours)).performClick()
        rule.onNodeWithText(str(R.string.dialog_ok)).performClick()
        rule.onNodeWithText(str(R.string.dialog_ok)).performClick()
        val result = changes.single()(ReminderSettings(quietStart = LocalTime.of(1, 0), quietEnd = LocalTime.of(2, 0)))
        assertEquals(LocalTime.of(22, 0), result.quietStart)
        assertEquals(LocalTime.of(8, 0), result.quietEnd)
    }

    // Tall screen: the section is not scrollable here and the options sit at its end.
    @Test
    @Config(qualifiers = "w360dp-h1400dp")
    fun accuracyShowsOptionsAndCallsChange() {
        show(ReminderSettings())
        rule.onNodeWithText(str(R.string.reminder_accuracy)).assertExists()
        rule.onNodeWithText(str(R.string.reminder_accuracy_hint)).assertExists()
        rule.onNodeWithText(str(R.string.reminder_accuracy_15)).assertIsSelected()
        rule.onNodeWithText(str(R.string.reminder_accuracy_60)).assertExists()
        rule.onNodeWithText(str(R.string.reminder_accuracy_5)).performClick()
        assertEquals(5, changes.single()(ReminderSettings()).maxDelayMinutes)
    }

    @Test
    fun accuracyDisabledWhenRemindersOff() {
        show(ReminderSettings(enabled = false))
        rule.onNodeWithText(str(R.string.reminder_accuracy_5)).assertIsNotEnabled()
        rule.onNodeWithText(str(R.string.reminder_accuracy_60)).assertIsNotEnabled()
    }
}
