package com.wolf.feelingsdiary.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wolf.feelingsdiary.data.DiaryRepository
import com.wolf.feelingsdiary.ui.FeelingLabeler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject

/** One list row: [feelings] are (label, colorArgb) pairs, main feeling first. */
data class HomeEntryRow(
    val id: Long,
    val time: String,
    val feelings: List<Pair<String, Int>>,
    val people: Int,
)

@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        repo: DiaryRepository,
        labeler: FeelingLabeler,
    ) : ViewModel() {
        private val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)

        /** Newest entries first (the repository orders them). */
        val rows: StateFlow<List<HomeEntryRow>> =
            combine(repo.observeRecentEntries(), repo.observeFeelings()) { entries, feelings ->
                val byId = feelings.associateBy { it.id }
                entries.map { e ->
                    HomeEntryRow(
                        id = e.id,
                        time = e.time.localDateTime().format(formatter),
                        feelings = e.feelingIds.mapNotNull { byId[it] }.map { labeler.label(it) to it.colorArgb },
                        people = e.people,
                    )
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    }
