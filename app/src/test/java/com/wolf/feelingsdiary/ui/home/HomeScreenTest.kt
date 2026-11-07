package com.wolf.feelingsdiary.ui.home

import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.wolf.feelingsdiary.ui.theme.FeelingsDiaryTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HomeScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private var adds = 0

    private fun show(entries: List<HomeEntryRow>) =
        rule.setContent { FeelingsDiaryTheme { HomeScreen(entries = entries, onAdd = { adds++ }) } }

    @Test
    fun showsEntriesNewestFirst() {
        show(
            listOf(
                HomeEntryRow(2, "NEWER-TIME", listOf("happy" to 0xFFFFC107.toInt()), 0),
                HomeEntryRow(1, "OLDER-TIME", listOf("tired" to 0xFF8A7FD0.toInt(), "sad" to 0xFF336699.toInt()), 3),
            ),
        )
        val newer =
            rule
                .onNodeWithText("NEWER-TIME")
                .fetchSemanticsNode()
                .positionInRoot.y
        val older =
            rule
                .onNodeWithText("OLDER-TIME")
                .fetchSemanticsNode()
                .positionInRoot.y
        assert(newer < older)
        rule.onNodeWithText("happy", substring = true).assertExists()
        rule.onNodeWithText("tired", substring = true).assertExists()
    }

    @Test
    fun addButtonNavigates() {
        show(emptyList())
        rule.onNodeWithText("+ How are you feeling?").performClick()
        assertEquals(1, adds)
    }

    @Test
    fun emptyStateShowsHint() {
        show(emptyList())
        rule.onNodeWithText("The history is coming in phase 2").assertExists()
    }
}
