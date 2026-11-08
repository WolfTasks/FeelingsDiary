package com.wolf.feelingsdiary.ui.feelings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wolf.feelingsdiary.data.DiaryRepository
import com.wolf.feelingsdiary.domain.Feeling
import com.wolf.feelingsdiary.domain.FeelingNames
import com.wolf.feelingsdiary.domain.FeelingSuggester
import com.wolf.feelingsdiary.ui.FeelingLabeler
import com.wolf.feelingsdiary.ui.LocaleSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

data class FeelingRow(
    val feeling: Feeling,
    val label: String,
)

@HiltViewModel
class FeelingsViewModel
    @Inject
    constructor(
        private val repo: DiaryRepository,
        private val labeler: FeelingLabeler,
        localeSource: LocaleSource,
    ) : ViewModel() {
        private val hideLock = Mutex()

        /** All feelings in base-list order, hidden ones included. The locale only triggers relabelling. */
        val state: StateFlow<List<FeelingRow>> =
            combine(repo.observeFeelings(), localeSource.locale) { feelings, _ ->
                FeelingSuggester.baseListOrder(feelings, labeler::label).map { FeelingRow(it, labeler.label(it)) }
            }
                // Eagerly: save/add check names against this list synchronously, even without a collector.
                .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

        /**
         * Saves colour and valence; [name] only renames custom feelings and is ignored for base ones.
         * Returns false (and saves nothing) if another feeling already has that name.
         */
        fun save(
            id: Long,
            name: String,
            colorArgb: Int,
            valence: Int,
        ): Boolean {
            val feelings = state.value.map { it.feeling }
            val feeling = feelings.firstOrNull { it.id == id } ?: return false
            if (valence !in VALENCE_RANGE) return false
            val newName =
                if (feeling.baseKey == null) {
                    name.trim().takeIf { it.isNotEmpty() && !isTaken(it, feelings, exceptId = id) } ?: return false
                } else {
                    null
                }
            viewModelScope.launch { repo.updateFeeling(id, newName, colorArgb, valence) }
            return true
        }

        /** Adds a custom feeling; returns false if the name is blank or already taken. */
        fun add(
            name: String,
            colorArgb: Int,
            valence: Int,
        ): Boolean {
            val trimmed = name.trim()
            if (trimmed.isEmpty() || valence !in VALENCE_RANGE || isTaken(trimmed, state.value.map { it.feeling })) return false
            viewModelScope.launch { repo.addCustomFeeling(trimmed, colorArgb, valence) }
            return true
        }

        /** Hides or shows a feeling; the last visible feeling cannot be hidden. */
        fun toggleHidden(id: Long) {
            viewModelScope.launch {
                // Serialized and read fresh: quick taps must not hide the last two visible feelings at once.
                hideLock.withLock {
                    val feelings = repo.observeFeelings().first()
                    val feeling = feelings.firstOrNull { it.id == id } ?: return@withLock
                    if (!feeling.hidden && feelings.count { !it.hidden } <= 1) return@withLock
                    repo.setHidden(id, !feeling.hidden)
                }
            }
        }

        private fun isTaken(
            name: String,
            feelings: List<Feeling>,
            exceptId: Long? = null,
        ) = FeelingNames.findDuplicate(name, feelings, labeler::label, exceptId) != null

        private companion object {
            val VALENCE_RANGE = -2..2
        }
    }
