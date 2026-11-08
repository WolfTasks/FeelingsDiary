package com.wolf.feelingsdiary.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeelingSuggesterTest {
    private fun base(
        id: Long,
        key: String,
        lastUsedAt: Long? = null,
        hidden: Boolean = false,
    ) = Feeling(id, key, null, 0, 0, hidden, lastUsedAt)

    private fun custom(
        id: Long,
        name: String,
        lastUsedAt: Long? = null,
        hidden: Boolean = false,
    ) = Feeling(id, null, name, 0, 0, hidden, lastUsedAt)

    private fun label(f: Feeling) = f.customName ?: f.baseKey.orEmpty()

    private fun suggest(
        query: String,
        feelings: List<Feeling>,
    ) = FeelingSuggester.suggest(query, feelings, ::label)

    @Test
    fun emptyQueryPutsRecentFirst() {
        val a = custom(1, "a", lastUsedAt = 3)
        val b = custom(2, "b", lastUsedAt = 1)
        val c = custom(3, "c", lastUsedAt = 2)
        val result = suggest("", listOf(b, c, a))
        assertEquals(listOf(1L, 3L, 2L), result.recent.map { it.id })
        assertTrue(result.others.isEmpty())
        assertFalse(result.canCreate)
    }

    @Test
    fun emptyQueryOthersFollowBaseOrderThenCustomAlphabetical() {
        val feelings =
            listOf(
                custom(12, "zorn"),
                base(2, "sad"),
                custom(11, "Ärger"),
                base(1, "happy"),
                custom(10, "mutig", lastUsedAt = 5),
            )
        val result = suggest("  ", feelings)
        assertEquals(listOf(10L), result.recent.map { it.id })
        assertEquals(listOf(1L, 2L, 11L, 12L), result.others.map { it.id })
    }

    @Test
    fun recentIsLimitedToRecentCount() {
        val feelings = (1L..7L).map { custom(it, "f$it", lastUsedAt = it) }
        val result = FeelingSuggester.suggest("", feelings, ::label, recentCount = 5)
        assertEquals(listOf(7L, 6L, 5L, 4L, 3L), result.recent.map { it.id })
        assertEquals(listOf(1L, 2L), result.others.map { it.id })
    }

    @Test
    fun matchesCaseAndUmlautInsensitive() {
        val feelings = listOf(custom(1, "fröhlich"), custom(2, "traurig"))
        assertEquals(listOf(1L), suggest("FRÖH", feelings).others.map { it.id })
        assertEquals(listOf(1L), suggest("froh", feelings).others.map { it.id })
        val en = listOf(base(1, "happy"), base(2, "sad"))
        assertEquals(listOf(1L), suggest("happ", en).others.map { it.id })
    }

    @Test
    fun prefixBeforeContains() {
        val feelings =
            listOf(
                custom(1, "angespannt"),
                base(2, "gestresst"),
                base(3, "gelangweilt"),
            )
        val result = suggest("ge", feelings)
        assertEquals(listOf(2L, 3L, 1L), result.others.map { it.id })
        assertTrue(result.recent.isEmpty())
    }

    @Test
    fun withinGroupRecentlyUsedFirst() {
        val feelings =
            listOf(
                base(1, "gestresst", lastUsedAt = 1),
                base(2, "gelangweilt", lastUsedAt = 9),
                base(3, "gelassen"),
            )
        assertEquals(listOf(2L, 1L, 3L), suggest("ge", feelings).others.map { it.id })
    }

    @Test
    fun canCreateOnlyWithoutExactMatch() {
        val feelings = listOf(base(1, "gestresst"))
        assertFalse(suggest("Gestresst", feelings).canCreate)
        assertTrue(suggest("genervt", feelings).canCreate)
    }

    @Test
    fun hiddenFeelingsNeverSuggested() {
        val feelings = listOf(custom(1, "müde", hidden = true), custom(2, "wach"))
        assertEquals(listOf(2L), suggest("", feelings).others.map { it.id })
        assertTrue(suggest("mü", feelings).others.isEmpty())
    }

    @Test
    fun alwaysIncludeShowsHiddenFeelingInRecent() {
        val hidden = base(4, "bored", hidden = true)
        val other = base(5, "lonely", hidden = true)
        val used = custom(10, "a", lastUsedAt = 3)
        val feelings = listOf(base(1, "happy"), used, hidden, other)
        val result = FeelingSuggester.suggest("", feelings, ::label, alwaysInclude = setOf(4L))
        assertEquals(listOf(4L, 10L), result.recent.map { it.id })
        assertEquals(listOf(1L), result.others.map { it.id })
        val searched = FeelingSuggester.suggest("bor", feelings, ::label, alwaysInclude = setOf(4L))
        assertEquals(listOf(4L), searched.others.map { it.id })
    }

    @Test
    fun quickShowsUpToEightRecentlyUsedVisibleFeelings() {
        val used = (1L..10L).map { base(it, "k$it", lastUsedAt = it) }
        val hidden = base(11, "hidden", lastUsedAt = 100, hidden = true)
        val unused = base(12, "unused")
        val result = FeelingSuggester.quick(used + hidden + unused, ::label, selected = emptyList())
        assertEquals((10L downTo 3L).toList(), result.map { it.id })
    }

    @Test
    fun quickWithoutHistoryUsesFirstEightOfBaseList() {
        val customs = listOf(custom(20, "b"), custom(21, "a"))
        val bases = (9L downTo 1L).map { base(it, "k$it") }
        val hidden = base(0, "hidden", hidden = true)
        val result = FeelingSuggester.quick(customs + bases + hidden, ::label, selected = emptyList())
        assertEquals((1L..8L).toList(), result.map { it.id })
    }

    @Test
    fun quickKeepsSelectedFeelingsVisibleInTapOrder() {
        val recent = base(1, "recent", lastUsedAt = 5)
        val other = base(2, "other")
        val created = custom(3, "created")
        val hidden = base(4, "hidden", hidden = true)
        val result = FeelingSuggester.quick(listOf(recent, other, created, hidden), ::label, selected = listOf(4, 1, 3))
        assertEquals(listOf(1L, 4L, 3L), result.map { it.id })
    }
}
