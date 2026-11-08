package com.wolf.feelingsdiary.ui.settings

import com.wolf.feelingsdiary.data.settings.SettingsRepository
import com.wolf.feelingsdiary.domain.AppSettings
import com.wolf.feelingsdiary.domain.InputMode
import com.wolf.feelingsdiary.domain.ReminderMode
import com.wolf.feelingsdiary.domain.ThemeMode
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    // A stand-in store: update applies the transform to the current value, like the DataStore does.
    private val stored = MutableStateFlow(AppSettings())
    private val repo = mockk<SettingsRepository>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { repo.settings } returns stored
        coEvery { repo.update(any()) } answers { stored.value = firstArg<(AppSettings) -> AppSettings>()(stored.value) }
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun setThemePersists() =
        runTest {
            val vm = SettingsViewModel(repo)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
            vm.setTheme(ThemeMode.LIGHT)
            assertEquals(ThemeMode.LIGHT, stored.value.theme)
            assertEquals(ThemeMode.LIGHT, vm.state.value.theme)
        }

    @Test
    fun otherSettersPersist() =
        runTest {
            val vm = SettingsViewModel(repo)
            vm.setInput(InputMode.QUICK_SHEET)
            vm.setBackup(true)
            vm.updateReminders { it.copy(count = 2) }
            assertEquals(
                AppSettings().copy(
                    input = InputMode.QUICK_SHEET,
                    backupEnabled = true,
                    reminders = AppSettings().reminders.copy(count = 2),
                ),
                stored.value,
            )
        }

    @Test
    fun successiveReminderUpdatesBothPersist() =
        runTest {
            val vm = SettingsViewModel(repo)
            vm.updateReminders { it.copy(mode = ReminderMode.FIXED) }
            vm.updateReminders { it.copy(quietStart = LocalTime.of(23, 0)) }
            assertEquals(ReminderMode.FIXED, stored.value.reminders.mode)
            assertEquals(LocalTime.of(23, 0), stored.value.reminders.quietStart)
        }
}
