package com.wolf.feelingsdiary.ui.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wolf.feelingsdiary.data.DiaryRepository
import com.wolf.feelingsdiary.data.settings.SettingsRepository
import com.wolf.feelingsdiary.domain.Aggregation
import com.wolf.feelingsdiary.domain.FeelingSuggester
import com.wolf.feelingsdiary.domain.GraphBuilder
import com.wolf.feelingsdiary.domain.GraphData
import com.wolf.feelingsdiary.domain.GraphPeriod
import com.wolf.feelingsdiary.domain.GraphPoint
import com.wolf.feelingsdiary.domain.InputMode
import com.wolf.feelingsdiary.domain.PeriodKind
import com.wolf.feelingsdiary.domain.formatUtcOffset
import com.wolf.feelingsdiary.export.DiaryExporter
import com.wolf.feelingsdiary.export.ExportResult
import com.wolf.feelingsdiary.ui.FeelingLabeler
import com.wolf.feelingsdiary.ui.LocaleSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.WeekFields
import java.util.Locale
import javax.inject.Inject

/** One list row: [feelings] are (label, colorArgb) pairs, main feeling first; the UI formats [time]. */
data class HomeEntryRow(
    val id: Long,
    val time: LocalDateTime,
    val feelings: List<Pair<String, Int>>,
    val people: Int,
    /** UTC offset such as "UTC+1", set when it differs from the chronologically previous entry; null = no hint. */
    val offsetLabel: String? = null,
)

/** [colors] maps feeling id to ARGB; [legend] is (label, ARGB) in base-list order. */
data class HomeUiState(
    val period: GraphPeriod,
    val graph: GraphData,
    val rows: List<HomeEntryRow>,
    val colors: Map<Long, Int>,
    val legend: List<Pair<String, Int>>,
    val loading: Boolean = false,
    /** Whether "+" opens the entry screen or the quick sheet. */
    val inputMode: InputMode = InputMode.SCREEN,
)

private const val KEY_KIND = "kind"
private const val KEY_START = "start"
private const val KEY_END = "end"
private const val STOP_TIMEOUT_MS = 5_000L
private const val DEFAULT_PEOPLE_SCALE = 5

sealed interface ExportEvent {
    data class Share(
        val result: ExportResult,
    ) : ExportEvent

    data object Failed : ExportEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        private val repo: DiaryRepository,
        private val labeler: FeelingLabeler,
        private val localeSource: LocaleSource,
        private val savedState: SavedStateHandle,
        private val clock: Clock,
        settings: SettingsRepository,
        private val exporter: DiaryExporter,
    ) : ViewModel() {
        private val exportChannel = Channel<ExportEvent>(Channel.BUFFERED)

        /** One event per [export] call, consumed once by the screen. */
        val exportEvents: Flow<ExportEvent> = exportChannel.receiveAsFlow()

        // A second export would clear the cache under the first one's share sheet, so taps during a run are ignored.
        private var exportJob: Job? = null

        private val inputMode = settings.settings.map { it.input }.distinctUntilChanged()

        private val period =
            MutableStateFlow(
                restorePeriod() ?: GraphPeriod.containing(PeriodKind.WEEK, LocalDate.now(clock), firstDayOfWeek()),
            )

        val state: StateFlow<HomeUiState> =
            period
                .flatMapLatest { p ->
                    // The locale is a trigger only: the labeler reads it itself, so a language change relabels without a new query.
                    combine(
                        repo.observeEntriesBetween(p.start, p.endInclusive),
                        repo.observeFeelings(),
                        localeSource.locale,
                        inputMode,
                    ) { entries, feelings, _, input ->
                        // One query per emission: only the earliest entry needs its predecessor from outside the period.
                        val firstPrevious = entries.firstOrNull()?.let { repo.previousOffsetMinutes(it.time.epochMinute, it.id) }
                        val byId = feelings.associateBy { it.id }
                        val graph = GraphBuilder.build(entries, p, firstDayOfWeek())
                        val used = feelings.filter { it.id in graph.feelingIds }
                        HomeUiState(
                            period = p,
                            graph = graph,
                            // The repository orders ascending; the list shows newest first.
                            rows =
                                entries.withIndex().reversed().map { (i, e) ->
                                    val previous = if (i == 0) firstPrevious else entries[i - 1].time.utcOffsetMinutes
                                    HomeEntryRow(
                                        id = e.id,
                                        time = e.time.localDateTime(),
                                        feelings = e.feelingIds.mapNotNull { byId[it] }.map { labeler.label(it) to it.colorArgb },
                                        people = e.people,
                                        offsetLabel =
                                            previous
                                                ?.takeIf { it != e.time.utcOffsetMinutes }
                                                ?.let { formatUtcOffset(e.time.utcOffsetMinutes) },
                                    )
                                },
                            colors = used.associate { it.id to it.colorArgb },
                            legend = FeelingSuggester.baseListOrder(used, labeler::label).map { labeler.label(it) to it.colorArgb },
                            loading = false,
                            inputMode = input,
                        )
                    }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyState(period.value))

        fun selectKind(kind: PeriodKind) {
            if (kind == PeriodKind.RANGE) return
            // Prefer today when it is visible, so e.g. switching the current week to "day" shows today.
            val current = period.value
            val today = LocalDate.now(clock)
            val anchor = if (today in current.start..current.endInclusive) today else current.start
            setPeriod(GraphPeriod.containing(kind, anchor, firstDayOfWeek()))
        }

        fun selectRange(
            start: LocalDate,
            end: LocalDate,
        ) = setPeriod(GraphPeriod.range(start, end))

        fun shift(steps: Int) = setPeriod(period.value.shift(steps, firstDayOfWeek()))

        /** Jumps from an aggregated point to the day (half-day, daily), week (weekly) or month (monthly buckets) it stands for. */
        fun openBucket(point: GraphPoint) {
            when (state.value.graph.aggregation) {
                Aggregation.HALF_DAY, Aggregation.DAILY -> {
                    setPeriod(
                        GraphPeriod.containing(PeriodKind.DAY, point.bucketStart, firstDayOfWeek()),
                    )
                }

                Aggregation.WEEKLY -> {
                    setPeriod(GraphPeriod.containing(PeriodKind.WEEK, point.bucketStart, firstDayOfWeek()))
                }

                Aggregation.MONTHLY -> {
                    setPeriod(GraphPeriod.containing(PeriodKind.MONTH, point.bucketStart, firstDayOfWeek()))
                }

                Aggregation.NONE -> {
                    Unit
                }
            }
        }

        fun export(
            csv: Boolean,
            graph: Boolean,
        ) {
            if (exportJob?.isActive == true) return
            val p = period.value
            exportJob =
                viewModelScope.launch {
                    val event =
                        try {
                            ExportEvent.Share(exporter.export(p, firstDayOfWeek(), csv, graph))
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            // Disk full, missing feeling data, ...: the screen reports it instead of crashing.
                            ExportEvent.Failed
                        }
                    exportChannel.send(event)
                }
        }

        fun delete(id: Long) {
            viewModelScope.launch { repo.deleteEntry(id) }
        }

        private fun setPeriod(p: GraphPeriod) {
            savedState[KEY_KIND] = p.kind.name
            savedState[KEY_START] = p.start.toString()
            savedState[KEY_END] = p.endInclusive.toString()
            period.value = p
        }

        private fun restorePeriod(): GraphPeriod? =
            runCatching {
                GraphPeriod(
                    PeriodKind.valueOf(savedState.get<String>(KEY_KIND)!!),
                    LocalDate.parse(savedState.get<String>(KEY_START)!!),
                    LocalDate.parse(savedState.get<String>(KEY_END)!!),
                )
            }.getOrNull()

        // Read on every use: the ViewModel outlives a language change, so a new week start applies from the next period change.
        private fun firstDayOfWeek(): DayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek

        private fun emptyState(p: GraphPeriod) =
            HomeUiState(
                p,
                GraphData(emptyList(), Aggregation.NONE, DEFAULT_PEOPLE_SCALE, emptySet()),
                emptyList(),
                emptyMap(),
                emptyList(),
                loading = true,
            )
    }
