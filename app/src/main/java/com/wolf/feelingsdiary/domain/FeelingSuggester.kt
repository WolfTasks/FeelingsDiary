package com.wolf.feelingsdiary.domain

import java.text.Normalizer
import java.util.Locale

data class Suggestions(
    val recent: List<Feeling>,
    val others: List<Feeling>,
    val canCreate: Boolean,
)

object FeelingSuggester {
    /**
     * [alwaysInclude]: ids shown even when hidden (e.g. selected in the entry being edited); with an
     * empty query, such hidden feelings head the recent section.
     */
    fun suggest(
        query: String,
        feelings: List<Feeling>,
        label: (Feeling) -> String,
        recentCount: Int = 5,
        alwaysInclude: Set<Long> = emptySet(),
    ): Suggestions {
        val visible = feelings.filter { !it.hidden || it.id in alwaysInclude }
        val normalizedQuery = normalize(query.trim())
        val listOrder = baseListOrder(visible, label)
        return if (normalizedQuery.isEmpty()) {
            val recent =
                listOrder.filter { it.hidden } +
                    visible
                        .filter { !it.hidden && it.lastUsedAt != null }
                        .sortedByDescending { it.lastUsedAt }
                        .take(recentCount)
            val recentIds = recent.map { it.id }.toSet()
            Suggestions(recent, listOrder.filter { it.id !in recentIds }, canCreate = false)
        } else {
            val labels = visible.associate { it.id to normalize(label(it)) }
            val prefix = mutableListOf<Feeling>()
            val contains = mutableListOf<Feeling>()
            for (feeling in listOrder) {
                val text = labels.getValue(feeling.id)
                when {
                    text.startsWith(normalizedQuery) -> prefix += feeling
                    text.contains(normalizedQuery) -> contains += feeling
                }
            }
            val byRecency = compareByDescending<Feeling, Long?>(nullsFirst()) { it.lastUsedAt }
            // sortedWith is stable, so ties keep the base-list order.
            Suggestions(
                recent = emptyList(),
                others = prefix.sortedWith(byRecency) + contains.sortedWith(byRecency),
                canCreate = labels.values.none { it == normalizedQuery },
            )
        }
    }

    /**
     * Chips of the quick sheet: up to [count] visible feelings by last use, filled up to [count] with the
     * remaining visible ones in base-list order. [selected] feelings missing from that list follow in tap order.
     */
    fun quick(
        feelings: List<Feeling>,
        label: (Feeling) -> String,
        selected: List<Long>,
        count: Int = 8,
    ): List<Feeling> {
        val visible = feelings.filter { !it.hidden }
        val recent = visible.filter { it.lastUsedAt != null }.sortedByDescending { it.lastUsedAt }.take(count)
        val recentIds = recent.map { it.id }.toSet()
        val shown = recent + baseListOrder(visible.filter { it.id !in recentIds }, label).take(count - recent.size)
        val shownIds = shown.map { it.id }.toSet()
        val byId = feelings.associateBy { it.id }
        return shown + selected.filter { it !in shownIds }.mapNotNull { byId[it] }
    }

    /** Base feelings by ascending id, then custom feelings alphabetically by label. */
    fun baseListOrder(
        feelings: List<Feeling>,
        label: (Feeling) -> String,
    ): List<Feeling> {
        val (base, custom) = feelings.partition { it.baseKey != null }
        return base.sortedBy { it.id } + custom.sortedBy { normalize(label(it)) }
    }

    fun normalize(text: String): String =
        Normalizer
            .normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .filterNot { it.isCombiningMark() }

    private fun Char.isCombiningMark(): Boolean = Character.getType(this).toByte() in COMBINING_MARK_TYPES

    private val COMBINING_MARK_TYPES =
        setOf(
            Character.NON_SPACING_MARK,
            Character.COMBINING_SPACING_MARK,
            Character.ENCLOSING_MARK,
        )
}
