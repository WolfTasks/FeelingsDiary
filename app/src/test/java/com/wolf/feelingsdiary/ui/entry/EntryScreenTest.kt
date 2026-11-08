package com.wolf.feelingsdiary.ui.entry

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import com.wolf.feelingsdiary.domain.Feeling
import com.wolf.feelingsdiary.domain.Suggestions
import com.wolf.feelingsdiary.ui.theme.FeelingsDiaryTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
class EntryScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val stressed = Feeling(1, "stressed", null, 0xFFF07B1D.toInt(), -1, false, 100L)
    private val tired = Feeling(2, "tired", null, 0xFF8A7FD0.toInt(), -1, false, null)
    private val calls = mutableListOf<String>()

    private val actions =
        EntryActions(
            onQuery = { calls += "query:$it" },
            onToggle = { calls += "toggle:$it" },
            onPeople = { calls += "people:$it" },
            onCreate = { name, _, valence -> calls += "create:$name:$valence" },
            onSave = { calls += "save" },
        )

    private fun state(
        query: String = "",
        selected: List<Long> = emptyList(),
        people: Int? = null,
        canSave: Boolean = false,
        canCreate: Boolean = false,
        others: List<Feeling> = listOf(tired),
        recent: List<Feeling> = listOf(stressed),
        editingTime: LocalDateTime? = null,
    ) = EntryUiState(query, Suggestions(recent, others, canCreate), selected, people, canSave, false, editingTime)

    private fun show(state: EntryUiState) =
        rule.setContent {
            FeelingsDiaryTheme {
                EntryScreen(state, actions, onClose = {}, label = { it.baseKey.orEmpty() }, time = "14:02")
            }
        }

    @Test
    fun selectingChipsAndPeopleEnablesSave() {
        show(state())
        rule.onNodeWithText("stressed").performClick()
        rule.onNodeWithText("tired").performClick()
        rule.onNodeWithText("3").performScrollTo().performClick()
        assertEquals(listOf("toggle:1", "toggle:2", "people:3"), calls)
    }

    @Test
    fun editTitleShown() {
        show(state(editingTime = LocalDateTime.of(2026, 11, 7, 19, 22)))
        rule.onNode(hasText("Edit entry · ", substring = true)).assertExists()
    }

    @Test
    fun saveEnabledWhenStateAllows() {
        show(state(selected = listOf(1), people = 0, canSave = true))
        rule.onNodeWithText("Save").assertIsEnabled()
    }

    @Test
    fun saveDisabledWithoutSelection() {
        show(state())
        rule.onNodeWithText("Save").assertIsNotEnabled()
    }

    @Test
    fun saveIgnoresSecondTap() {
        show(state(selected = listOf(1), people = 0, canSave = true))
        rule.onNodeWithText("Save").performClick()
        rule.onNodeWithText("Save").performClick()
        assertEquals(listOf("save"), calls)
    }

    @Test
    fun sixPlusOpensNumberDialogAndRejectsInvalid() {
        show(state())
        rule.onNodeWithText("6+").performScrollTo().performClick()
        rule.onNode(hasSetTextAction() and hasText("Number")).performTextReplacement("3")
        rule.onNodeWithText("Enter a number from 6 to 999").assertExists()
        rule.onNodeWithText("OK").assertIsNotEnabled()
        assertEquals(emptyList<String>(), calls)
        rule.onNode(hasSetTextAction() and hasText("Number")).performTextReplacement("12")
        rule.onNodeWithText("OK").performClick()
        assertEquals(listOf("people:12"), calls)
    }

    @Test
    fun createOfferShownOnlyWhenAllowed() {
        show(state(query = "genervt", canCreate = true, recent = emptyList()))
        rule.onNodeWithText("+ \u201Cgenervt\u201D as new feeling").performClick()
        // Search field plus the dialog's name field, both holding the query.
        rule.onAllNodes(hasSetTextAction() and hasText("genervt")).assertCountEquals(2)
        rule.onNodeWithText("New feeling").assertExists()
    }

    @Test
    fun createOfferHiddenWhenNotAllowed() {
        show(state(query = "genervt", canCreate = false, recent = emptyList()))
        rule.onNodeWithText("+ \u201Cgenervt\u201D as new feeling").assertDoesNotExist()
    }
}
