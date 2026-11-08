package com.wolf.feelingsdiary.ui.navigation

import android.content.Context
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.wolf.feelingsdiary.MainActivity
import com.wolf.feelingsdiary.R
import com.wolf.feelingsdiary.data.settings.SettingsRepository
import com.wolf.feelingsdiary.data.settings.settingsDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The onboarding end to end: real activity, ViewModel and settings store. */
@RunWith(RobolectricTestRunner::class)
// Tall screen: Home's empty hint sits below graph and legend inside the lazy list.
@Config(qualifiers = "w360dp-h1400dp")
class MainActivityOnboardingTest {
    @get:Rule
    val rule = createEmptyComposeRule()

    private val ctx = ApplicationProvider.getApplicationContext<Context>()

    // The DataStore delegate is process-wide, so the state is reset around each test.
    private val settings = SettingsRepository(ctx.settingsDataStore)

    @Before
    fun firstStart() = runBlocking { settings.update { it.copy(onboardingDone = false) } }

    @After
    fun done() = runBlocking { settings.update { it.copy(onboardingDone = true) } }

    private fun shows(text: String) = rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun firstStartShowsOnboardingAndLetsStartLeadsHome() {
        Robolectric.buildActivity(MainActivity::class.java).setup()
        rule.waitUntil(TIMEOUT_MS) { shows(ctx.getString(R.string.onboarding_title)) }
        rule.onNodeWithText("2").performClick()
        rule.onNodeWithText(ctx.getString(R.string.onboarding_start)).performClick()
        // The write runs on the DataStore thread; Home follows once it is done.
        rule.waitUntil(TIMEOUT_MS) { shows(ctx.getString(R.string.history_empty)) }
        rule.onNodeWithText(ctx.getString(R.string.onboarding_title)).assertDoesNotExist()
        val stored = runBlocking { settings.settings.first() }
        assertTrue(stored.onboardingDone)
        assertEquals(2, stored.reminders.count)
    }

    @Test
    fun recreationKeepsTheDecidedStart() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        rule.waitUntil(TIMEOUT_MS) { shows(ctx.getString(R.string.onboarding_title)) }
        // Rotation during the onboarding: still the onboarding, not Home.
        controller.recreate()
        rule.waitUntil(TIMEOUT_MS) { shows(ctx.getString(R.string.onboarding_title)) }
        rule.onNodeWithText(ctx.getString(R.string.onboarding_start)).performClick()
        rule.waitUntil(TIMEOUT_MS) { shows(ctx.getString(R.string.history_empty)) }
        // Rotation after it: the restored back stack holds only Home.
        controller.recreate()
        rule.waitUntil(TIMEOUT_MS) { shows(ctx.getString(R.string.history_empty)) }
        rule.onNodeWithText(ctx.getString(R.string.onboarding_title)).assertDoesNotExist()
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
