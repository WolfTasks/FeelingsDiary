package com.wolf.feelingsdiary.ui.navigation

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.wolf.feelingsdiary.MainActivity
import com.wolf.feelingsdiary.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/** Notification taps reach the real activity: once per tap, never again on recreation. */
@RunWith(RobolectricTestRunner::class)
class MainActivityNewEntryTest {
    @get:Rule
    val rule = createEmptyComposeRule()

    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val save = ctx.getString(R.string.entry_save)
    private val newEntry = Intent(ctx, MainActivity::class.java).setAction(MainActivity.ACTION_NEW_ENTRY)

    @Test
    fun launchFromNotificationOpensEntry() {
        Robolectric.buildActivity(MainActivity::class.java, newEntry).setup()
        rule.waitForIdle()
        rule.onNodeWithText(save).assertExists()
    }

    @Test
    fun notificationTapWhileRunningOpensEntry() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        rule.waitForIdle()
        rule.onNodeWithText(save).assertDoesNotExist()
        controller.newIntent(newEntry)
        rule.waitForIdle()
        rule.onNodeWithText(save).assertExists()
    }

    @Test
    fun rotationDoesNotReopenEntry() {
        val controller = Robolectric.buildActivity(MainActivity::class.java, newEntry).setup()
        rule.waitForIdle()
        rule.onNodeWithText(save).assertExists()
        controller.get().onBackPressedDispatcher.onBackPressed()
        rule.waitForIdle()
        rule.onNodeWithText(save).assertDoesNotExist()
        controller.recreate()
        rule.waitForIdle()
        rule.onNodeWithText(save).assertDoesNotExist()
    }
}
