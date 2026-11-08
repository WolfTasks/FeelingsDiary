package com.wolf.feelingsdiary.ui.home

import androidx.lifecycle.SavedStateHandle
import com.wolf.feelingsdiary.data.DiaryRepository
import com.wolf.feelingsdiary.data.settings.SettingsRepository
import com.wolf.feelingsdiary.domain.Aggregation
import com.wolf.feelingsdiary.domain.AppSettings
import com.wolf.feelingsdiary.domain.Entry
import com.wolf.feelingsdiary.domain.EntryTime
import com.wolf.feelingsdiary.domain.Feeling
import com.wolf.feelingsdiary.domain.GraphPeriod
import com.wolf.feelingsdiary.domain.GraphPoint
import com.wolf.feelingsdiary.domain.InputMode
import com.wolf.feelingsdiary.domain.PeriodKind
import com.wolf.feelingsdiary.ui.FeelingLabeler
import com.wolf.feelingsdiary.ui.LocaleSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val happy = Feeling(1, "happy", null, 0x111111, 2, false, null)
    private val repo = mockk<DiaryRepository>()
    private val labeler = FeelingLabeler { it.baseKey!! }
    private val localeSource = LocaleSource().apply { update(Locale.ENGLISH) }
    private val clock = Clock.fixed(Instant.parse("2026-11-07T10:00:00Z"), ZoneId.of("Europe/Berlin"))
    private val entries = MutableStateFlow<List<Entry>>(emptyList())
    private val appSettings = MutableStateFlow(AppSettings())
    private val settingsRepo = mockk<SettingsRepository> { every { settings } returns appSettings }
    private lateinit var oldLocale: Locale

    @Before
    fun setUp() {
        oldLocale = Locale.getDefault()
        Locale.setDefault(Locale.GERMANY)
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { repo.observeFeelings() } returns MutableStateFlow(listOf(happy))
        every { repo.observeEntriesBetween(any(), any()) } returns entries
        coEvery { repo.deleteEntry(any()) } returns Unit
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        Locale.setDefault(oldLocale)
    }

    private fun vm(saved: SavedStateHandle = SavedStateHandle()) = HomeViewModel(repo, labeler, localeSource, saved, clock, settingsRepo)

    private fun TestScope.observe(vm: HomeViewModel) =
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }

    private fun point(
        entryId: Long?,
        bucket: LocalDate,
    ) = GraphPoint(0.5f, 1f, 1, emptyList(), 0f, entryId, bucket)

    @Test
    fun startsWithCurrentWeek() =
        runTest {
            val vm = vm()
            observe(vm)
            assertEquals(LocalDate.of(2026, 11, 2), vm.state.value.period.start)
            assertEquals(LocalDate.of(2026, 11, 8), vm.state.value.period.endInclusive)
            verify { repo.observeEntriesBetween(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 8)) }
        }

    @Test
    fun inputModeFollowsSettings() =
        runTest {
            val vm = vm()
            observe(vm)
            assertEquals(InputMode.SCREEN, vm.state.value.inputMode)
            appSettings.value = AppSettings(input = InputMode.QUICK_SHEET)
            assertEquals(InputMode.QUICK_SHEET, vm.state.value.inputMode)
        }

    @Test
    fun shiftLoadsNeighbour() =
        runTest {
            val vm = vm()
            observe(vm)
            vm.shift(-1)
            verify { repo.observeEntriesBetween(LocalDate.of(2026, 10, 26), LocalDate.of(2026, 11, 1)) }
            assertEquals(LocalDate.of(2026, 10, 26), vm.state.value.period.start)
        }

    @Test
    fun selectKindKeepsAnchor() =
        runTest {
            val vm = vm()
            observe(vm)
            vm.selectKind(PeriodKind.MONTH)
            assertEquals(GraphPeriod(PeriodKind.MONTH, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)), vm.state.value.period)
        }

    @Test
    fun selectRangeBecomesRangePeriod() =
        runTest {
            val vm = vm()
            observe(vm)
            vm.selectRange(LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 1))
            assertEquals(GraphPeriod.range(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 5)), vm.state.value.period)
        }

    @Test
    fun openBucketOfDailyPointShowsDay() =
        runTest {
            val vm = vm()
            observe(vm)
            vm.selectKind(PeriodKind.YEAR)
            vm.openBucket(point(null, LocalDate.of(2026, 3, 4)))
            val p = vm.state.value.period
            assertEquals(PeriodKind.DAY, p.kind)
            assertEquals(LocalDate.of(2026, 3, 4), p.start)
        }

    @Test
    fun openBucketOfWeeklyPointShowsWeek() =
        runTest {
            val vm = vm()
            observe(vm)
            vm.selectRange(LocalDate.of(2024, 1, 1), LocalDate.of(2026, 1, 1))
            assertEquals(Aggregation.WEEKLY, vm.state.value.graph.aggregation)
            vm.openBucket(point(null, LocalDate.of(2025, 3, 3)))
            assertEquals(GraphPeriod(PeriodKind.WEEK, LocalDate.of(2025, 3, 3), LocalDate.of(2025, 3, 9)), vm.state.value.period)
        }

    @Test
    fun initialStateIsLoading() =
        runTest {
            val source = MutableSharedFlow<List<Entry>>()
            every { repo.observeEntriesBetween(any(), any()) } returns source
            val vm = vm()
            observe(vm)
            assertTrue(vm.state.value.loading)
            source.emit(emptyList())
            assertFalse(vm.state.value.loading)
        }

    @Test
    fun openBucketOfMonthlyPointShowsMonth() =
        runTest {
            val vm = vm()
            observe(vm)
            vm.selectRange(LocalDate.of(2018, 1, 1), LocalDate.of(2026, 1, 1))
            assertEquals(Aggregation.MONTHLY, vm.state.value.graph.aggregation)
            vm.openBucket(point(null, LocalDate.of(2025, 3, 1)))
            assertEquals(GraphPeriod(PeriodKind.MONTH, LocalDate.of(2025, 3, 1), LocalDate.of(2025, 3, 31)), vm.state.value.period)
        }

    @Test
    fun periodSurvivesProcessDeath() =
        runTest {
            val vm = vm(SavedStateHandle(mapOf("kind" to "MONTH", "start" to "2026-03-01", "end" to "2026-03-31")))
            observe(vm)
            assertEquals(GraphPeriod(PeriodKind.MONTH, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)), vm.state.value.period)
        }

    @Test
    fun rowsCarryLocalDateTimeAndColors() =
        runTest {
            val vm = vm()
            observe(vm)
            val minute = (Instant.parse("2026-11-04T12:00:00Z").epochSecond / 60).toInt()
            entries.value = listOf(Entry(5, EntryTime(minute, 60), 2, listOf(1), listOf(2)))
            val row =
                vm.state.value.rows
                    .single()
            assertEquals(5L, row.id)
            assertEquals(LocalDateTime.of(2026, 11, 4, 13, 0), row.time)
            assertEquals(listOf("happy" to 0x111111), row.feelings)
            assertEquals(mapOf(1L to 0x111111), vm.state.value.colors)
            assertEquals(listOf("happy" to 0x111111), vm.state.value.legend)
        }

    @Test
    fun deleteDelegates() =
        runTest {
            val vm = vm()
            vm.delete(7)
            coVerify { repo.deleteEntry(7) }
        }

    @Test
    fun labelsFollowLocaleChange() =
        runTest {
            val localized = FeelingLabeler { if (localeSource.locale.value.language == "de") "Glücklich" else "Happy" }
            val vm = HomeViewModel(repo, localized, localeSource, SavedStateHandle(), clock, settingsRepo)
            observe(vm)
            val minute = (Instant.parse("2026-11-04T12:00:00Z").epochSecond / 60).toInt()
            entries.value = listOf(Entry(5, EntryTime(minute, 60), 2, listOf(1), listOf(2)))
            assertEquals(
                listOf("Happy" to 0x111111),
                vm.state.value.rows
                    .single()
                    .feelings,
            )
            // No new repository emission: the locale change alone must refresh the labels.
            localeSource.update(Locale.GERMAN)
            assertEquals(
                listOf("Glücklich" to 0x111111),
                vm.state.value.rows
                    .single()
                    .feelings,
            )
            assertEquals(listOf("Glücklich" to 0x111111), vm.state.value.legend)
        }
}
