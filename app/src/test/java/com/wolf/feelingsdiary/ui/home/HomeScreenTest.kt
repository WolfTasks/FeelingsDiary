package com.wolf.feelingsdiary.ui.home

import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import com.wolf.feelingsdiary.domain.Aggregation
import com.wolf.feelingsdiary.domain.GraphData
import com.wolf.feelingsdiary.domain.GraphPeriod
import com.wolf.feelingsdiary.domain.PeriodKind
import com.wolf.feelingsdiary.ui.theme.FeelingsDiaryTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
// Tall screen: the list sits below graph and legend inside one LazyColumn.
@Config(qualifiers = "w360dp-h1400dp")
class HomeScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private var adds = 0
    private val kinds = mutableListOf<PeriodKind>()
    private val shifts = mutableListOf<Int>()
    private val deletes = mutableListOf<Long>()
    private val actions =
        HomeActions(
            onAdd = { adds++ },
            onKind = { kinds += it },
            onRange = { _, _ -> },
            onShift = { shifts += it },
            onPoint = {},
            onRow = {},
            onEdit = {},
            onDelete = { deletes += it },
        )
    private val week = GraphPeriod(PeriodKind.WEEK, LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 8))
    private val happy = 0xFFFFC107.toInt()
    private val tired = 0xFF8A7FD0.toInt()
    private val sad = 0xFF336699.toInt()
    private val time = LocalDateTime.of(2026, 11, 7, 19, 22)

    private fun state(rows: List<HomeEntryRow>) =
        HomeUiState(
            period = week,
            graph = GraphData(emptyList(), Aggregation.NONE, 5, emptySet()),
            rows = rows,
            colors = mapOf(1L to happy),
            legend = listOf("happy" to happy),
        )

    private val twoRows =
        listOf(
            HomeEntryRow(2, time.plusHours(1), listOf("happy" to happy), 0),
            HomeEntryRow(1, time, listOf("tired" to tired, "sad" to sad), 3),
        )

    private fun show(state: HomeUiState) = rule.setContent { FeelingsDiaryTheme { HomeScreen(state, actions) } }

    @Test
    fun showsEntriesNewestFirst() {
        show(state(twoRows))
        val newer =
            rule
                .onNodeWithText(formatEntryTime(time.plusHours(1), Locale.US), substring = true)
                .fetchSemanticsNode()
                .positionInRoot.y
        val older =
            rule
                .onNodeWithText(formatEntryTime(time, Locale.US), substring = true)
                .fetchSemanticsNode()
                .positionInRoot.y
        assert(newer < older)
        rule.onNodeWithText("tired", substring = true).assertExists()
    }

    @Test
    fun addButtonNavigates() {
        show(state(emptyList()))
        rule.onNodeWithText("+ How are you feeling?").performClick()
        assertEquals(1, adds)
    }

    @Test
    fun chipsAndArrowsCallActions() {
        show(state(emptyList()))
        rule.onNodeWithText("Month").performClick()
        rule.onNodeWithContentDescription("Previous period").performClick()
        rule.onNodeWithContentDescription("Next period").performClick()
        assertEquals(listOf(PeriodKind.MONTH), kinds)
        assertEquals(listOf(-1, 1), shifts)
    }

    @Test
    fun loadingStateShowsNoEmptyHint() {
        show(state(emptyList()).copy(loading = true))
        rule.onNodeWithText("No entries in this period").assertDoesNotExist()
    }

    @Test
    fun emptyPeriodShowsHint() {
        show(state(emptyList()))
        rule.onNodeWithText("No entries in this period").assertExists()
    }

    @Test
    fun rowOpensDetailsAndDeleteAsksFirst() {
        show(state(twoRows))
        rule.onNodeWithText("tired", substring = true).performClick()
        rule.onNodeWithText("Edit").assertExists()
        rule.onNodeWithText("Delete").performClick()
        rule.onNodeWithText("Cancel").performClick()
        assertEquals(emptyList<Long>(), deletes)
        rule.onNodeWithText("Delete").performClick()
        rule.onNodeWithText("Delete this entry?").assertExists()
        rule.onNode(hasText("Delete") and hasAnyAncestor(hasAnyDescendant(hasText("This cannot be undone.")))).performClick()
        assertEquals(listOf(1L), deletes)
    }

    @Test
    fun sheetClosesWhenEntryDisappears() {
        val current = mutableStateOf(state(twoRows))
        rule.setContent { FeelingsDiaryTheme { HomeScreen(current.value, actions) } }
        rule.onNodeWithText("tired", substring = true).performClick()
        rule.onNodeWithText("Edit").assertExists()
        current.value = state(twoRows.filter { it.id != 1L })
        rule.waitForIdle()
        rule.onNodeWithText("Edit").assertDoesNotExist()
    }

    @Test
    fun timeFollowsLocaleChange() {
        val locale = mutableStateOf(Locale.US)
        val s = state(twoRows)
        rule.setContent {
            val config = Configuration().apply { setLocale(locale.value) }
            CompositionLocalProvider(LocalConfiguration provides config) { FeelingsDiaryTheme { HomeScreen(s, actions) } }
        }
        rule.onNodeWithText(formatEntryTime(time, Locale.US), substring = true).assertExists()
        locale.value = Locale.GERMANY
        rule.waitForIdle()
        rule.onNodeWithText("07.11.26, 19:22", substring = true).assertExists()
    }

    @Test
    @Config(qualifiers = "w360dp-h1400dp-night")
    fun textFollowsDarkThemeInsteadOfBlack() {
        show(state(emptyList()))
        listOf("My history", "happy", "No entries in this period").forEach { text ->
            val results = mutableListOf<TextLayoutResult>()
            rule
                .onNodeWithText(text)
                .fetchSemanticsNode()
                .config[SemanticsActions.GetTextLayoutResult]
                .action
                ?.invoke(results)
            val color =
                results
                    .single()
                    .layoutInput.style.color
            assertNotEquals("$text must not render black", Color.Black, color)
            assertNotEquals("$text has no resolved color", Color.Unspecified, color)
        }
    }
}
