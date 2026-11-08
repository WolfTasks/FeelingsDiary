package com.wolf.feelingsdiary.ui.onboarding

import com.wolf.feelingsdiary.data.settings.SettingsRepository
import com.wolf.feelingsdiary.domain.AppSettings
import com.wolf.feelingsdiary.domain.ReminderMode
import com.wolf.feelingsdiary.domain.ReminderSettings
import com.wolf.feelingsdiary.domain.ThemeMode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {
    // A stand-in store: update applies the transform to the current value, like the DataStore does.
    private val stored =
        MutableStateFlow(AppSettings(theme = ThemeMode.LIGHT, reminders = ReminderSettings(mode = ReminderMode.FIXED)))
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
    fun finishStoresChoicesAndDoneInOneWrite() {
        val vm = OnboardingViewModel(repo)
        assertFalse(vm.finished.value)
        vm.finish(enabled = false, count = 2)
        val saved = stored.value
        assertEquals(false, saved.reminders.enabled)
        assertEquals(2, saved.reminders.count)
        assertTrue(saved.onboardingDone)
        // Everything else is kept.
        assertEquals(ThemeMode.LIGHT, saved.theme)
        assertEquals(ReminderMode.FIXED, saved.reminders.mode)
        assertTrue(vm.finished.value)
        coVerify(exactly = 1) { repo.update(any()) }
    }

    @Test
    fun secondFinishIsIgnored() {
        val vm = OnboardingViewModel(repo)
        vm.finish(enabled = true, count = 4)
        vm.finish(enabled = true, count = 1)
        assertEquals(4, stored.value.reminders.count)
        coVerify(exactly = 1) { repo.update(any()) }
    }
}
