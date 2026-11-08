package com.wolf.feelingsdiary.ui.navigation

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.wolf.feelingsdiary.MainActivity
import com.wolf.feelingsdiary.R
import com.wolf.feelingsdiary.domain.InputMode
import com.wolf.feelingsdiary.testutil.resetSettings
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Quick-sheet mode end to end: real activity, ViewModels, database and settings. */
@RunWith(RobolectricTestRunner::class)
// Tall screen: the empty hint sits below graph and legend inside the lazy list.
@Config(qualifiers = "w360dp-h1400dp")
class MainActivityQuickSheetTest {
    @get:Rule
    val rule = createEmptyComposeRule()

    private val ctx = ApplicationProvider.getApplicationContext<Context>()

    private val question = ctx.getString(R.string.entry_question)
    private val add = ctx.getString(R.string.home_add)
    private val happy = ctx.getString(R.string.feeling_happy)

    // Onboarding done: the app starts at Home.
    @Before
    fun quickMode() = resetSettings { it.copy(input = InputMode.QUICK_SHEET, onboardingDone = true) }

    @After
    fun defaults() = resetSettings()

    /** Home has loaded once the empty hint shows; the mode is part of that same state. */
    private fun awaitHome() = rule.waitUntil(TIMEOUT_MS) { shows(ctx.getString(R.string.history_empty)) }

    private fun shows(text: String) = rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun reopeningSheetStartsEmpty() {
        Robolectric.buildActivity(MainActivity::class.java).setup()
        awaitHome()
        rule.onNodeWithText(add).performClick()
        rule.waitForIdle()
        rule.onNodeWithText(question).assertExists()
        rule.onNodeWithText(ctx.getString(R.string.entry_people_alone)).assertExists()
        rule.onNodeWithContentDescription(ctx.getString(R.string.chip_cd_unselected, happy)).performClick()
        rule.onNodeWithContentDescription(ctx.getString(R.string.chip_cd_main, happy)).assertExists()
        rule.onNodeWithText(ctx.getString(R.string.entry_save)).performClick()
        // Saving runs on the database thread; the sheet closes once it is done.
        rule.waitUntil(TIMEOUT_MS) { !shows(question) }

        rule.onNodeWithText(add).performClick()
        rule.waitForIdle()
        rule.onNodeWithText(question).assertExists()
        rule.onNodeWithContentDescription(ctx.getString(R.string.chip_cd_unselected, happy)).assertExists()
        rule.onNodeWithContentDescription(ctx.getString(R.string.chip_cd_main, happy)).assertDoesNotExist()
    }

    @Test
    fun notificationOpensSheet() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        awaitHome()
        controller.newIntent(Intent(ctx, MainActivity::class.java).setAction(MainActivity.ACTION_NEW_ENTRY))
        rule.waitUntil(TIMEOUT_MS) { shows(question) }
        // The sheet, not the entry screen: the screen's close button is absent.
        rule.onNodeWithContentDescription(ctx.getString(R.string.entry_close)).assertDoesNotExist()
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
