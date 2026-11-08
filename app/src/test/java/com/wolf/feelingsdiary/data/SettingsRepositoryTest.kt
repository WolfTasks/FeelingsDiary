package com.wolf.feelingsdiary.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.wolf.feelingsdiary.data.settings.SettingsRepository
import com.wolf.feelingsdiary.domain.AppSettings
import com.wolf.feelingsdiary.domain.InputMode
import com.wolf.feelingsdiary.domain.ReminderMode
import com.wolf.feelingsdiary.domain.ReminderSettings
import com.wolf.feelingsdiary.domain.ThemeMode
import com.wolf.feelingsdiary.domain.TimeWindow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun TestScope.store() = PreferenceDataStoreFactory.create(scope = backgroundScope) { File(tmp.root, "t.preferences_pb") }

    @Test
    fun emptyStoreYieldsDefaults() =
        runTest(UnconfinedTestDispatcher()) {
            val repo = SettingsRepository(store())
            assertEquals(AppSettings(), repo.settings.first())
        }

    @Test
    fun updateRoundTrips() =
        runTest(UnconfinedTestDispatcher()) {
            val repo = SettingsRepository(store())
            val changed =
                AppSettings(
                    reminders =
                        ReminderSettings(
                            enabled = false,
                            count = 2,
                            mode = ReminderMode.FIXED,
                            windows =
                                ReminderSettings.DEFAULT_WINDOWS.toMutableList().also {
                                    it[0] = TimeWindow(LocalTime.of(7, 30), LocalTime.of(9, 15))
                                },
                            fixedTimes =
                                ReminderSettings.DEFAULT_TIMES.toMutableList().also {
                                    it[0] = LocalTime.of(6, 5)
                                },
                            quietStart = LocalTime.of(23, 30),
                            quietEnd = LocalTime.of(6, 45),
                        ),
                    theme = ThemeMode.LIGHT,
                    input = InputMode.QUICK_SHEET,
                    backupEnabled = true,
                    onboardingDone = true,
                )
            repo.update { changed }
            assertEquals(changed, repo.settings.first())
        }

    @Test
    fun corruptValuesFallBackPerField() =
        runTest(UnconfinedTestDispatcher()) {
            val store = store()
            store.edit {
                it[intPreferencesKey("reminder_count")] = 9
                it[stringPreferencesKey("reminder_windows")] = "abc"
                it[stringPreferencesKey("theme")] = "PURPLE"
                it[stringPreferencesKey("input_mode")] = "QUICK_SHEET"
            }
            val s = SettingsRepository(store).settings.first()
            assertEquals(3, s.reminders.count)
            assertEquals(ReminderSettings.DEFAULT_WINDOWS, s.reminders.windows)
            assertEquals(ThemeMode.DARK, s.theme)
            assertEquals(InputMode.QUICK_SHEET, s.input)
        }

    @Test
    fun wrongStoredTypeFallsBackPerField() =
        runTest(UnconfinedTestDispatcher()) {
            val store = store()
            store.edit {
                it[stringPreferencesKey("reminder_enabled")] = "yes"
                it[stringPreferencesKey("reminder_count")] = "two"
                it[intPreferencesKey("theme")] = 1
                it[stringPreferencesKey("quiet_start")] = "x"
                it[stringPreferencesKey("onboarding_done")] = "x"
                it[booleanPreferencesKey("input_mode")] = true
                it[intPreferencesKey("backup_enabled")] = 1
            }
            assertEquals(AppSettings(), SettingsRepository(store).settings.first())
        }

    @Test
    fun lastKnownFollowsEmittedSettings() =
        runTest(UnconfinedTestDispatcher()) {
            val repo = SettingsRepository(store())
            assertNull(repo.lastKnown)
            repo.update { it.copy(theme = ThemeMode.LIGHT) }
            val emitted = repo.settings.first()
            assertEquals(emitted, repo.lastKnown)
        }

    @Test
    fun saltIsStable() =
        runTest(UnconfinedTestDispatcher()) {
            val repo = SettingsRepository(store())
            assertEquals(repo.reminderSalt(), repo.reminderSalt())
        }
}
