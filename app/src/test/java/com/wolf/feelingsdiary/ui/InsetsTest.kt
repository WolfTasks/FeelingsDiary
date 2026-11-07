package com.wolf.feelingsdiary.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.wolf.feelingsdiary.domain.Feeling
import com.wolf.feelingsdiary.domain.Suggestions
import com.wolf.feelingsdiary.ui.entry.EntryActions
import com.wolf.feelingsdiary.ui.entry.EntryScreen
import com.wolf.feelingsdiary.ui.entry.EntryUiState
import com.wolf.feelingsdiary.ui.home.HomeScreen
import com.wolf.feelingsdiary.ui.theme.FeelingsDiaryTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The bottom buttons must stay clear of the navigation bar and the keyboard (edge-to-edge). */
@RunWith(RobolectricTestRunner::class)
class InsetsTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val stressed = Feeling(1, "stressed", null, 0xFFF07B1D.toInt(), -1, false, null)
    private val noOp = EntryActions({}, {}, {}, { _, _, _ -> }, {})
    private val entryState =
        EntryUiState("", Suggestions(emptyList(), listOf(stressed), false), listOf(1), 0, true, false)

    private fun show(
        bottomInset: WindowInsetsCompat,
        content: @Composable () -> Unit,
    ) {
        rule.runOnUiThread { WindowCompat.setDecorFitsSystemWindows(rule.activity.window, false) }
        rule.setContent { FeelingsDiaryTheme { content() } }
        rule.runOnUiThread { ViewCompat.dispatchApplyWindowInsets(rule.activity.window.decorView, bottomInset) }
        rule.waitForIdle()
    }

    private fun insets(
        type: Int,
        bottomPx: Int,
    ) = WindowInsetsCompat.Builder().setInsets(type, Insets.of(0, 0, 0, bottomPx)).build()

    private fun assertBottomAbove(
        text: String,
        reserved: Dp,
    ) {
        val root = rule.onRoot().getBoundsInRoot()
        val button = rule.onNodeWithText(text).getBoundsInRoot()
        assertTrue(
            "'$text' bottom ${button.bottom} overlaps the bottom $reserved of ${root.bottom}",
            button.bottom <= root.bottom - reserved,
        )
    }

    @Test
    fun homeAddButtonClearsNavigationBar() {
        show(insets(WindowInsetsCompat.Type.navigationBars(), NAV_BAR_PX)) { HomeScreen(emptyList(), onAdd = {}) }
        assertBottomAbove("+ How are you feeling?", NAV_BAR_PX.dp)
    }

    @Test
    fun entrySaveButtonClearsNavigationBar() {
        show(insets(WindowInsetsCompat.Type.navigationBars(), NAV_BAR_PX)) {
            EntryScreen(entryState, noOp, onClose = {}, label = { it.baseKey.orEmpty() }, time = "14:02")
        }
        assertBottomAbove("Save", NAV_BAR_PX.dp)
    }

    @Test
    fun entrySaveButtonClearsKeyboard() {
        show(insets(WindowInsetsCompat.Type.ime(), KEYBOARD_PX)) {
            EntryScreen(entryState, noOp, onClose = {}, label = { it.baseKey.orEmpty() }, time = "14:02")
        }
        assertBottomAbove("Save", KEYBOARD_PX.dp)
    }

    private companion object {
        // Robolectric's default screen is mdpi, so 1 px == 1 dp.
        const val NAV_BAR_PX = 48
        const val KEYBOARD_PX = 200
    }
}
