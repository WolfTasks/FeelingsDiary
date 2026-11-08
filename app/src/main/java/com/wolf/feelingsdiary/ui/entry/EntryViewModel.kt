package com.wolf.feelingsdiary.ui.entry

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wolf.feelingsdiary.data.DiaryRepository
import com.wolf.feelingsdiary.domain.Feeling
import com.wolf.feelingsdiary.domain.FeelingSuggester
import com.wolf.feelingsdiary.domain.Suggestions
import com.wolf.feelingsdiary.ui.FeelingLabeler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

@HiltViewModel
class EntryViewModel
    @Inject
    constructor(
        private val repo: DiaryRepository,
        val labeler: FeelingLabeler,
        private val savedState: SavedStateHandle,
    ) : ViewModel() {
        private val saved = MutableStateFlow(false)
        private var saving = false

        // Must be read before getStateFlow below, which registers its default under the same key.
        private val restoredSelection = savedState.contains(KEY_SELECTED)
        private val selection = savedState.getStateFlow(KEY_SELECTED, LongArray(0))
        private val peopleFlow = savedState.getStateFlow<Int?>(KEY_PEOPLE, null)
        private val queryFlow = savedState.getStateFlow(KEY_QUERY, "")
        private val editId: Long? = savedState.get<Long>(KEY_ENTRY_ID)?.takeIf { it > 0 }
        private val editTimeFlow = savedState.getStateFlow<String?>(KEY_EDIT_TIME, null)

        val state: StateFlow<EntryUiState> =
            combine(repo.observeFeelings(), queryFlow, selection, peopleFlow, saved, editTimeFlow) { values ->
                @Suppress("UNCHECKED_CAST")
                val feelings = values[0] as List<Feeling>
                val query = values[1] as String
                val selected = values[2] as LongArray
                val people = values[3] as Int?
                val saved = values[4] as Boolean
                val editTime = values[5] as String?
                val ids = selected.toList()
                EntryUiState(
                    query = query,
                    suggestions = FeelingSuggester.suggest(query, feelings, labeler::label),
                    selectedIds = ids,
                    people = people,
                    canSave = ids.isNotEmpty() && people != null,
                    saved = saved,
                    editingTime = editTime?.let(LocalDateTime::parse),
                )
            }.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                EntryUiState("", Suggestions(emptyList(), emptyList(), false), emptyList(), null, false, false),
            )

        init {
            // Only load when nothing was restored (process death keeps the user's edits).
            if (editId != null && !restoredSelection) {
                viewModelScope.launch {
                    val entry = repo.getEntry(editId)
                    if (entry == null) {
                        saved.value = true
                    } else {
                        savedState[KEY_EDIT_TIME] = entry.time.localDateTime().toString()
                        savedState[KEY_PEOPLE] = entry.people
                        select(entry.feelingIds)
                    }
                }
            }
        }

        fun onQueryChange(q: String) {
            savedState[KEY_QUERY] = q
        }

        fun toggle(feelingId: Long) {
            val current = selection.value.toList()
            select(if (feelingId in current) current - feelingId else current + feelingId)
        }

        fun setPeople(n: Int) {
            if (n in PEOPLE_RANGE) savedState[KEY_PEOPLE] = n
        }

        fun createFeeling(
            name: String,
            colorArgb: Int,
            valence: Int,
        ) {
            val trimmed = name.trim()
            if (trimmed.isEmpty()) return
            viewModelScope.launch {
                val wanted = FeelingSuggester.normalize(trimmed)
                // Includes hidden feelings: they are un-hidden only via settings, never by re-creating.
                val existing =
                    repo
                        .observeFeelings()
                        .first()
                        .firstOrNull { FeelingSuggester.normalize(labeler.label(it)) == wanted }
                val id = existing?.id ?: repo.addCustomFeeling(trimmed, colorArgb, valence)
                val current = selection.value.toList()
                if (id !in current) select(current + id)
                onQueryChange("")
            }
        }

        fun save() {
            if (saving || saved.value) return
            // Read the source flows directly: `state` is WhileSubscribed and stale without a collector.
            val ids = selection.value.toList()
            val people = peopleFlow.value
            if (ids.isEmpty() || people == null) return
            saving = true
            viewModelScope.launch {
                try {
                    if (editId != null) repo.updateEntry(editId, people, ids) else repo.addEntry(people, ids)
                    saved.value = true
                } catch (e: IllegalArgumentException) {
                    // Entry deleted meanwhile: nothing left to update, so close like a missing entry does.
                    if (editId == null) throw e
                    saved.value = true
                } finally {
                    if (!saved.value) saving = false
                }
            }
        }

        private fun select(ids: List<Long>) {
            savedState[KEY_SELECTED] = ids.toLongArray()
        }

        private companion object {
            const val KEY_SELECTED = "selected"
            const val KEY_PEOPLE = "people"
            const val KEY_QUERY = "query"
            const val KEY_ENTRY_ID = "entryId"
            const val KEY_EDIT_TIME = "editTime"
            const val STOP_TIMEOUT_MS = 5_000L
            val PEOPLE_RANGE = 0..999
        }
    }
