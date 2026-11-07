package com.wolf.feelingsdiary.ui.entry

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wolf.feelingsdiary.data.DiaryRepository
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
        private val selection = savedState.getStateFlow(KEY_SELECTED, LongArray(0))
        private val peopleFlow = savedState.getStateFlow<Int?>(KEY_PEOPLE, null)
        private val queryFlow = savedState.getStateFlow(KEY_QUERY, "")

        val state: StateFlow<EntryUiState> =
            combine(repo.observeFeelings(), queryFlow, selection, peopleFlow, saved) { feelings, query, selected, people, saved ->
                val ids = selected.toList()
                EntryUiState(
                    query = query,
                    suggestions = FeelingSuggester.suggest(query, feelings, labeler::label),
                    selectedIds = ids,
                    people = people,
                    canSave = ids.isNotEmpty() && people != null,
                    saved = saved,
                )
            }.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                EntryUiState("", Suggestions(emptyList(), emptyList(), false), emptyList(), null, false, false),
            )

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
            val s = state.value
            val people = s.people
            if (!s.canSave || people == null) return
            viewModelScope.launch {
                repo.addEntry(people, s.selectedIds)
                saved.value = true
            }
        }

        private fun select(ids: List<Long>) {
            savedState[KEY_SELECTED] = ids.toLongArray()
        }

        private companion object {
            const val KEY_SELECTED = "selected"
            const val KEY_PEOPLE = "people"
            const val KEY_QUERY = "query"
            const val STOP_TIMEOUT_MS = 5_000L
            val PEOPLE_RANGE = 0..999
        }
    }
