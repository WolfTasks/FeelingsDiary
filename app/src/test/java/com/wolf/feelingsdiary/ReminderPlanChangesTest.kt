package com.wolf.feelingsdiary

import com.wolf.feelingsdiary.domain.AppSettings
import com.wolf.feelingsdiary.domain.ThemeMode
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** What makes the app-scope collector reschedule the reminders. */
class ReminderPlanChangesTest {
    @Test
    fun endOfOnboardingReschedules() =
        runTest {
            val changes =
                flowOf(
                    AppSettings(),
                    AppSettings(onboardingDone = true),
                    // Unrelated to reminders: no reschedule.
                    AppSettings(onboardingDone = true, theme = ThemeMode.LIGHT),
                    AppSettings(onboardingDone = true, reminders = AppSettings().reminders.copy(count = 2)),
                ).reminderPlanChanges()
            assertEquals(3, changes.count())
        }
}
