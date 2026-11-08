package com.wolf.feelingsdiary.ui.entry

import com.wolf.feelingsdiary.domain.Suggestions
import java.time.LocalDateTime

data class EntryUiState(
    val query: String,
    val suggestions: Suggestions,
    /** Selection in tap order; the first id is the main feeling. */
    val selectedIds: List<Long>,
    /** Number of other people present; null until chosen, 0 = alone. */
    val people: Int?,
    val canSave: Boolean,
    val saved: Boolean,
    /** Local time of the entry being edited; null for a new entry. */
    val editingTime: LocalDateTime? = null,
)
