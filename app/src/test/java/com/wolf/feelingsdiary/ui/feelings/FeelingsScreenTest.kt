package com.wolf.feelingsdiary.ui.feelings

import android.content.Context
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.wolf.feelingsdiary.R
import com.wolf.feelingsdiary.domain.Feeling
import com.wolf.feelingsdiary.ui.theme.FeelingsDiaryTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FeelingsScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val happy = FeelingRow(Feeling(1, "happy", null, 0xFFF2B705.toInt(), 2, false, null), "happy")
    private val sad = FeelingRow(Feeling(2, "sad", null, 0xFF4A84F0.toInt(), -2, true, null), "sad")
    private val wehmut = FeelingRow(Feeling(10, null, "Wehmut", 0xFF7E57C2.toInt(), -1, false, null), "Wehmut")
    private val calls = mutableListOf<String>()
    private var saveResult = true

    private val actions =
        FeelingsActions(
            onBack = { calls += "back" },
            onSave = { id, name, color, valence ->
                calls += "save:$id:$name:$color:$valence"
                saveResult
            },
            onToggleHidden = { calls += "toggle:$it" },
            onAdd = { name, _, valence ->
                calls += "add:$name:$valence"
                saveResult
            },
        )

    private fun show(rows: List<FeelingRow> = listOf(happy, sad, wehmut)) =
        rule.setContent { FeelingsDiaryTheme { FeelingsScreen(rows, actions) } }

    @Test
    fun eyeIconTogglesHidden() {
        show()
        rule.onAllNodesWithContentDescription(ctx.getString(R.string.feelings_hide_cd))[0].performClick()
        rule.onNodeWithContentDescription(ctx.getString(R.string.feelings_show_cd)).performClick()
        assertEquals(listOf("toggle:1", "toggle:2"), calls)
    }

    @Test
    fun lastVisibleFeelingEyeIsDisabled() {
        show(listOf(happy, sad))
        rule.onNodeWithContentDescription(ctx.getString(R.string.feelings_hide_cd)).assertIsNotEnabled()
    }

    @Test
    fun customRowOpensEditDialogAndDuplicateShowsError() {
        saveResult = false
        show()
        rule.onNodeWithText("Wehmut").performClick()
        rule.onNodeWithText(ctx.getString(R.string.feeling_edit_title)).assertExists()
        rule.onNode(hasSetTextAction() and hasText("Wehmut")).performTextReplacement("happy")
        rule.onNodeWithText(ctx.getString(R.string.dialog_ok)).performClick()
        assertEquals("save:10:happy:${0xFF7E57C2.toInt()}:-1", calls.single())
        rule.onNodeWithText(ctx.getString(R.string.feeling_name_exists)).assertExists()
        // Editing the name clears the error; a valid save closes the dialog.
        saveResult = true
        rule.onNode(hasSetTextAction() and hasText("happy")).performTextReplacement("Sehnsucht")
        rule.onNodeWithText(ctx.getString(R.string.feeling_name_exists)).assertDoesNotExist()
        rule.onNodeWithText(ctx.getString(R.string.dialog_ok)).performClick()
        rule.onNodeWithText(ctx.getString(R.string.feeling_edit_title)).assertDoesNotExist()
    }

    @Test
    fun baseRowNameIsNotEditableAndKeepsItsColour() {
        show()
        rule.onNodeWithText("happy").performClick()
        rule.onNode(hasSetTextAction()).assertDoesNotExist()
        rule.onNodeWithText(ctx.getString(R.string.dialog_ok)).performClick()
        assertEquals(listOf("save:1:happy:${0xFFF2B705.toInt()}:2"), calls)
    }

    @Test
    fun addButtonOpensNewFeelingDialog() {
        show()
        rule.onNodeWithText(ctx.getString(R.string.feelings_add)).performClick()
        rule.onNodeWithText(ctx.getString(R.string.new_feeling_title)).assertExists()
        rule.onNode(hasSetTextAction()).performTextReplacement("Mut")
        rule.onNodeWithText(ctx.getString(R.string.dialog_ok)).performClick()
        assertEquals(listOf("add:Mut:0"), calls)
    }

    @Test
    fun backArrowCallsAction() {
        show()
        rule.onNodeWithContentDescription(ctx.getString(R.string.settings_back_cd)).performClick()
        assertEquals(listOf("back"), calls)
    }

    @Test
    fun rowsShowValenceAndHiddenState() {
        show()
        rule.onNodeWithText("good (+2)").assertExists()
        rule.onNodeWithText("bad (-2) · hidden").assertExists()
        rule.onNodeWithText("bad (-1)").assertExists()
    }
}
