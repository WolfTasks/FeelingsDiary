package com.wolf.feelingsdiary.ui.navigation

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.wolf.feelingsdiary.MainActivity
import com.wolf.feelingsdiary.R
import com.wolf.feelingsdiary.testutil.resetSettings
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/**
 * Notification taps reach the real activity: once per tap, never again on recreation.
 *
 * The start destination and each request's routing wait for a settings read on the DataStore's IO thread,
 * which waitForIdle does not track: every step waits for its visible result instead.
 */
@RunWith(RobolectricTestRunner::class)
class MainActivityNewEntryTest {
    @get:Rule
    val rule = createEmptyComposeRule()

    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val save = ctx.getString(R.string.entry_save)
    private val add = ctx.getString(R.string.home_add)
    private val newEntry = Intent(ctx, MainActivity::class.java).setAction(MainActivity.ACTION_NEW_ENTRY)

    // Onboarding done and the entry screen as input mode: the app starts at Home.
    @Before
    fun onboardingDone() = resetSettings { it.copy(onboardingDone = true) }

    @After
    fun defaults() = resetSettings()

    private fun shows(text: String) = rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    private fun awaitEntry() = rule.waitUntil(TIMEOUT_MS) { shows(save) }

    /** Home is up and no entry screen on top of it. */
    private fun awaitHomeOnly() {
        rule.waitUntil(TIMEOUT_MS) { shows(add) && !shows(save) }
        rule.waitForIdle()
        rule.onNodeWithText(save).assertDoesNotExist()
    }

    @Test
    fun launchFromNotificationOpensEntry() {
        Robolectric.buildActivity(MainActivity::class.java, newEntry).setup()
        awaitEntry()
    }

    @Test
    fun notificationTapWhileRunningOpensEntry() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        awaitHomeOnly()
        controller.newIntent(newEntry)
        awaitEntry()
    }

    @Test
    fun rotationDoesNotReopenEntry() {
        val controller = Robolectric.buildActivity(MainActivity::class.java, newEntry).setup()
        awaitEntry()
        controller.get().onBackPressedDispatcher.onBackPressed()
        awaitHomeOnly()
        controller.recreate()
        awaitHomeOnly()
    }

    @Test
    fun relaunchFromRecentsDoesNotReopenEntry() {
        val replayed = Intent(newEntry).addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)
        Robolectric.buildActivity(MainActivity::class.java, replayed).setup()
        awaitHomeOnly()
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
