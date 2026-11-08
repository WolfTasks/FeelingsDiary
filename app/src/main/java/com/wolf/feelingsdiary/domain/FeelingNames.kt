package com.wolf.feelingsdiary.domain

object FeelingNames {
    /**
     * The feeling whose displayed name equals [name], ignoring case, accents and surrounding spaces.
     * Hidden feelings count too: they are shown again via settings, never re-created.
     * [exceptId] skips the feeling being renamed, so it may keep its name in a different case.
     */
    fun findDuplicate(
        name: String,
        feelings: List<Feeling>,
        label: (Feeling) -> String,
        exceptId: Long? = null,
    ): Feeling? {
        val wanted = FeelingSuggester.normalize(name.trim())
        return feelings.firstOrNull { it.id != exceptId && FeelingSuggester.normalize(label(it).trim()) == wanted }
    }
}
