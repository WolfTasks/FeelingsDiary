package com.wolf.feelingsdiary.domain

import java.text.Normalizer
import java.util.Locale

data class Suggestions(
    val recent: List<Feeling>,
    val others: List<Feeling>,
    val canCreate: Boolean,
)

object FeelingSuggester {
    fun suggest(
        query: String,
        feelings: List<Feeling>,
        label: (Feeling) -> String,
        recentCount: Int = 5,
    ): Suggestions {
        val visible = feelings.filter { !it.hidden }
        val normalizedQuery = normalize(query.trim())
        val listOrder = listOrder(visible, label)
        return if (normalizedQuery.isEmpty()) {
            val recent =
                visible
                    .filter { it.lastUsedAt != null }
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

    /** Base feelings by ascending id, then custom feelings alphabetically by label. */
    private fun listOrder(
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
