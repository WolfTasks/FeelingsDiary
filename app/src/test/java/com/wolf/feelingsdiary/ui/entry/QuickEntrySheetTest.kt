package com.wolf.feelingsdiary.ui.entry

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.wolf.feelingsdiary.R
import com.wolf.feelingsdiary.domain.Feeling
import com.wolf.feelingsdiary.domain.Suggestions
import com.wolf.feelingsdiary.ui.theme.FeelingsDiaryTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class QuickEntrySheetTest {
    @get:Rule
    val rule = createComposeRule()

    private val ctx = ApplicationProvider.getApplicationContext<Context>()
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
        people: Int? = 0,
        canCreate: Boolean = false,
        others: List<Feeling> = emptyList(),
    ) = EntryUiState(
        query = query,
        suggestions = Suggestions(emptyList(), others, canCreate),
        selectedIds = selected,
        people = people,
        canSave = selected.isNotEmpty() && people != null,
        saved = false,
        quick = listOf(stressed),
    )

    private fun show(state: EntryUiState) =
        rule.setContent {
            FeelingsDiaryTheme { QuickEntrySheet(state, actions, onDismiss = {}, label = { it.baseKey.orEmpty() }) }
        }

    private fun minus() = rule.onNodeWithContentDescription(ctx.getString(R.string.quick_people_less_cd))

    private fun plus() = rule.onNodeWithContentDescription(ctx.getString(R.string.quick_people_more_cd))

    @Test
    fun showsTitleQuickChipsAndPeopleRow() {
        show(state(people = 4))
        rule.onNodeWithText(ctx.getString(R.string.entry_question)).assertExists()
        rule.onNodeWithText(ctx.getString(R.string.quick_people)).assertExists()
        rule.onNodeWithText("4").assertExists()
        rule.onNodeWithText("stressed").performClick()
        assertEquals(listOf("toggle:1"), calls)
    }

    @Test
    fun zeroShowsAlone() {
        show(state(people = 0))
        rule.onNodeWithText(ctx.getString(R.string.entry_people_alone)).assertExists()
    }

    @Test
    fun minusAtZeroStaysZero() {
        show(state(people = 0))
        minus().performClick()
        plus().performClick()
        assertEquals(listOf("people:1"), calls)
    }

    @Test
    fun plusAt999StaysAt999() {
        show(state(people = 999))
        plus().performClick()
        minus().performClick()
        assertEquals(listOf("people:998"), calls)
    }

    @Test
    fun saveDisabledWithoutFeeling() {
        show(state(selected = emptyList()))
        rule.onNodeWithText(ctx.getString(R.string.entry_save)).assertIsNotEnabled()
    }

    @Test
    fun saveEnabledWithFeelingAndCallsSave() {
        show(state(selected = listOf(1)))
        rule.onNodeWithText(ctx.getString(R.string.entry_save)).assertIsEnabled().performClick()
        assertEquals(listOf("save"), calls)
    }

    @Test
    fun saveWhilePeoplePendingStoresShownZero() {
        // The prefill lookup has not finished: the sheet shows "alone", so that is what gets saved.
        show(state(selected = listOf(1), people = null))
        rule.onNodeWithText(ctx.getString(R.string.entry_people_alone)).assertExists()
        rule.onNodeWithText(ctx.getString(R.string.entry_save)).performClick()
        assertEquals(listOf("people:0", "save"), calls)
    }

    @Test
    fun searchToggleShowsField() {
        val current = mutableStateOf(state())
        rule.setContent {
            FeelingsDiaryTheme { QuickEntrySheet(current.value, actions, onDismiss = {}, label = { it.baseKey.orEmpty() }) }
        }
        rule.onNode(hasSetTextAction()).assertDoesNotExist()
        rule.onNodeWithContentDescription(ctx.getString(R.string.quick_search_cd)).performClick()
        rule.onNode(hasSetTextAction()).performTextReplacement("ti")
        assertEquals(listOf("query:ti"), calls)
        current.value = state(query = "ti", others = listOf(tired), canCreate = true)
        rule.waitForIdle()
        rule.onNodeWithText("tired").assertExists()
        rule.onNodeWithText("stressed").assertDoesNotExist()
        rule.onNodeWithText(ctx.getString(R.string.entry_create_offer, "ti")).performClick()
        rule.onNodeWithText(ctx.getString(R.string.new_feeling_title)).assertExists()
    }

    @Test
    fun closingSearchClearsQuery() {
        show(state())
        rule.onNodeWithContentDescription(ctx.getString(R.string.quick_search_cd)).performClick()
        rule.onNodeWithContentDescription(ctx.getString(R.string.quick_search_close_cd)).performClick()
        rule.onNode(hasSetTextAction()).assertDoesNotExist()
        assertEquals(listOf("query:"), calls)
    }
}
