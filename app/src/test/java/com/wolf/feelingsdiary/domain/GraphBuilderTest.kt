package com.wolf.feelingsdiary.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

class GraphBuilderTest {
    private val monday = DayOfWeek.MONDAY
    private val a = 1L
    private val b = 2L

    private fun entry(
        id: Long,
        local: String,
        offsetMin: Int,
        people: Int = 0,
        feelings: List<Pair<Long, Int>> = listOf(a to 0),
    ): Entry {
        val epochMinute = (LocalDateTime.parse(local).toEpochSecond(ZoneOffset.UTC) / 60 - offsetMin).toInt()
        return Entry(id, EntryTime(epochMinute, offsetMin), people, feelings.map { it.first }, feelings.map { it.second })
    }

    private fun at(
        date: LocalDate,
        hour: Int,
    ) = "${date}T${hour.toString().padStart(2, '0')}:00"

    private val week = GraphPeriod.containing(PeriodKind.WEEK, LocalDate.of(2026, 11, 3), monday)

    @Test
    fun entryHeightWeightsLaterFeelingsLess() {
        val data = GraphBuilder.build(listOf(entry(1, "2026-11-03T08:00", 60, feelings = listOf(a to 2, b to -1))), week, monday)
        val point = data.points.single()
        // (2 * 1 + -1 * 0.5) / 1.5
        assertEquals(1f, point.valence, 1e-6f)
        assertEquals(a, point.mainFeelingId)
        assertEquals(listOf(b), point.otherFeelingIds)
        assertEquals(1L, point.entryId)
        assertEquals(setOf(a, b), data.feelingIds)
    }

    @Test
    fun xIsPositionInPeriod() {
        val data = GraphBuilder.build(listOf(entry(1, "2026-11-04T12:00", 60)), week, monday)
        assertEquals((2 * 1440 + 720) / (7 * 1440f), data.points.single().x, 0f)
    }

    @Test
    fun emptyAndSingle() {
        val empty = GraphBuilder.build(emptyList(), week, monday)
        assertTrue(empty.points.isEmpty())
        assertEquals(5, empty.peopleScaleMax)
        assertEquals(1, GraphBuilder.build(listOf(entry(1, "2026-11-03T08:00", 60)), week, monday).points.size)
    }

    @Test
    fun entriesOutsidePeriodAreIgnored() {
        val data = GraphBuilder.build(listOf(entry(1, "2026-11-09T08:00", 60), entry(2, "2026-11-01T23:59", 60)), week, monday)
        assertTrue(data.points.isEmpty())
        assertTrue(data.feelingIds.isEmpty())
    }

    @Test
    fun sameMinuteEntriesBothKept() {
        val data =
            GraphBuilder.build(
                listOf(entry(1, "2026-11-03T08:00", 60), entry(2, "2026-11-03T08:00", 60)),
                week,
                monday,
            )
        assertEquals(2, data.points.size)
        assertEquals(data.points[0].x, data.points[1].x, 0f)
    }

    @Test
    fun dstWeekIsMonotonic() {
        val period = GraphPeriod.containing(PeriodKind.WEEK, LocalDate.of(2026, 10, 25), monday)
        val entries = (0..23).map { hour -> entry(hour + 1L, at(LocalDate.of(2026, 10, 25), hour), if (hour < 3) 120 else 60) }
        val xs = GraphBuilder.build(entries, period, monday).points.map { it.x }
        assertEquals(24, xs.size)
        assertTrue(xs.zipWithNext().all { (p, q) -> p < q })
    }

    @Test
    fun yearAggregatesDaily() {
        val year = GraphPeriod.containing(PeriodKind.YEAR, LocalDate.of(2026, 6, 1), monday)
        var id = 0L
        val entries =
            (1..365).flatMap { dayOfYear ->
                val date = LocalDate.ofYearDay(2026, dayOfYear)
                (0 until 4).map { slot -> entry(++id, at(date, 6 + slot * 4), 60) }
            }
        val data = GraphBuilder.build(entries, year, monday)
        assertEquals(Aggregation.DAILY, data.aggregation)
        assertEquals(365, data.points.size)
        val point = data.points.single { it.bucketStart == LocalDate.of(2026, 3, 1) }
        assertNull(point.entryId)
        assertEquals((59 * 1440 + 720) / (365 * 1440f), point.x, 1e-6f)
    }

    @Test
    fun dayMajorityMainFeeling() {
        val year = GraphPeriod.containing(PeriodKind.YEAR, LocalDate.of(2026, 6, 1), monday)
        val day = LocalDate.of(2026, 3, 1)
        val entries = listOf(a, b, b).mapIndexed { i, main -> entry(i + 1L, at(day, 8 + i), 60, feelings = listOf(main to 1)) }
        assertEquals(
            b,
            GraphBuilder
                .build(entries, year, monday)
                .points
                .single()
                .mainFeelingId,
        )
    }

    @Test
    fun dayTieTakesLaterEntriesMainFeeling() {
        val year = GraphPeriod.containing(PeriodKind.YEAR, LocalDate.of(2026, 6, 1), monday)
        val day = LocalDate.of(2026, 3, 2)
        val entries = listOf(a, b).mapIndexed { i, main -> entry(i + 1L, at(day, 8 + i), 60, feelings = listOf(main to 1)) }
        assertEquals(
            b,
            GraphBuilder
                .build(entries, year, monday)
                .points
                .single()
                .mainFeelingId,
        )
    }

    @Test
    fun weightedValenceHalvesEachFurtherFeeling() {
        assertEquals(-1f, GraphBuilder.weightedValence(listOf(-1)), 0f)
        assertEquals(0f, GraphBuilder.weightedValence(listOf(-1, 2)), 1e-6f)
        // (2 + 2 * 0.5 - 1 * 0.25) / 1.75
        assertEquals(11f / 7f, GraphBuilder.weightedValence(listOf(2, 2, -1)), 1e-6f)
    }

    @Test
    fun aggregateAveragesAndLimitsOthers() {
        val year = GraphPeriod.containing(PeriodKind.YEAR, LocalDate.of(2026, 6, 1), monday)
        val entries =
            listOf(
                entry(1, "2026-02-01T08:00", 60, people = 2, feelings = listOf(a to 2, 3L to 0, 4L to 0, 5L to 0, 6L to 0)),
                entry(2, "2026-02-01T09:00", 60, people = 4, feelings = listOf(a to -2)),
            )
        val point = GraphBuilder.build(entries, year, monday).points.single()
        // entry 1: 2 / (1 + 1/2 + 1/4 + 1/8 + 1/16) = 32/31; mean with entry 2 (-2)
        assertEquals((32f / 31f - 2f) / 2f, point.valence, 1e-6f)
        assertEquals(3f, point.people, 0f)
        assertEquals(a, point.mainFeelingId)
        assertEquals(3, point.otherFeelingIds.size)
    }

    @Test
    fun longRangeAggregatesWeekly() {
        val range = GraphPeriod.range(LocalDate.of(2026, 1, 1), LocalDate.of(2027, 6, 30))
        val data = GraphBuilder.build(listOf(entry(1, "2026-01-02T08:00", 60), entry(2, "2026-01-04T08:00", 60)), range, monday)
        assertEquals(Aggregation.WEEKLY, data.aggregation)
        assertEquals(1, data.points.size)
        assertEquals(LocalDate.of(2025, 12, 29), data.points.single().bucketStart)
    }

    @Test
    fun rangeOverSevenYearsUsesMonthlyMeans() {
        val range = GraphPeriod.range(LocalDate.of(2020, 1, 1), LocalDate.of(2027, 12, 31))
        assertEquals(2922, range.days)
        val entries =
            listOf(
                entry(1, "2020-01-05T08:00", 60, feelings = listOf(a to 2)),
                entry(2, "2020-01-20T08:00", 60, feelings = listOf(a to 0)),
                entry(3, "2027-12-31T08:00", 60, feelings = listOf(a to -2)),
            )
        val data = GraphBuilder.build(entries, range, monday)
        assertEquals(Aggregation.MONTHLY, data.aggregation)
        assertTrue(data.points.size <= 96)
        assertEquals(2, data.points.size)
        assertEquals(LocalDate.of(2020, 1, 1), data.points[0].bucketStart)
        assertEquals(1f, data.points[0].valence, 0f)
        assertEquals(LocalDate.of(2027, 12, 1), data.points[1].bucketStart)
        assertEquals(-2f, data.points[1].valence, 0f)
        assertTrue(data.points.all { it.x in 0f..1f })
    }

    @Test
    fun rangeOfExactlySevenYearsStaysWeekly() {
        val start = LocalDate.of(2020, 1, 1)
        val range = GraphPeriod.range(start, start.plusDays(2556))
        assertEquals(2557, range.days)
        assertEquals(Aggregation.WEEKLY, GraphBuilder.build(emptyList(), range, monday).aggregation)
        val longer = GraphPeriod.range(start, start.plusDays(2557))
        assertEquals(Aggregation.MONTHLY, GraphBuilder.build(emptyList(), longer, monday).aggregation)
    }

    @Test
    fun singleEntriesAreOrderedByRealTime() {
        // Entry 1 is earlier in UTC (06:00Z) but later on the wall clock (23:00 at +17h) than entry 2 (07:00Z, 08:00 at +1h); real time wins.
        val entries = listOf(entry(1, "2026-11-03T23:00", 17 * 60), entry(2, "2026-11-03T08:00", 60))
        val points = GraphBuilder.build(entries, week, monday).points
        assertEquals(listOf(1L, 2L), points.map { it.entryId })
        assertTrue(points[0].x < points[1].x)
    }

    @Test
    fun rangeAggregationBoundaries() {
        val start = LocalDate.of(2026, 1, 1)

        fun agg(days: Long) = GraphBuilder.build(emptyList(), GraphPeriod.range(start, start.plusDays(days - 1)), monday).aggregation
        assertEquals(Aggregation.NONE, agg(31))
        assertEquals(Aggregation.DAILY, agg(32))
        assertEquals(Aggregation.DAILY, agg(366))
        assertEquals(Aggregation.WEEKLY, agg(367))
    }

    @Test
    fun peopleScale() {
        fun scale(people: Int) = GraphBuilder.build(listOf(entry(1, "2026-11-03T08:00", 60, people = people)), week, monday).peopleScaleMax
        assertEquals(5, scale(0))
        assertEquals(10, scale(7))
        assertEquals(1000, scale(999))
    }

    private fun xs(
        entries: List<Entry>,
        period: GraphPeriod,
    ) = GraphBuilder.build(entries, period, monday).points

    @Test
    fun fallBackKeepsRealOrder() {
        val day = GraphPeriod.containing(PeriodKind.DAY, LocalDate.of(2026, 10, 25), monday)
        val first = entry(1, "2026-10-25T02:50", 120)
        val second = entry(2, "2026-10-25T02:10", 60)
        assertEquals(20, second.time.epochMinute - first.time.epochMinute)
        val points = xs(listOf(second, first), day)
        assertEquals(listOf(1L, 2L), points.map { it.entryId })
        assertTrue(points[1].x > points[0].x)
        assertEquals(20 / 1500f, points[1].x - points[0].x, 1e-5f)
        assertTrue(points.all { it.x in 0f..1f })
    }

    @Test
    fun springForwardKeepsRealOrder() {
        val day = GraphPeriod.containing(PeriodKind.DAY, LocalDate.of(2026, 3, 29), monday)
        val first = entry(1, "2026-03-29T01:50", 60)
        val second = entry(2, "2026-03-29T03:10", 120)
        assertEquals(20, second.time.epochMinute - first.time.epochMinute)
        val points = xs(listOf(first, second), day)
        assertEquals(listOf(1L, 2L), points.map { it.entryId })
        assertTrue(points[1].x > points[0].x)
        assertTrue(points.all { it.x in 0f..1f })
    }

    @Test
    fun westwardTravelKeepsRealOrder() {
        val first = entry(1, "2026-11-04T18:00", 60)
        val second = entry(2, "2026-11-04T14:00", -300)
        assertEquals(120, second.time.epochMinute - first.time.epochMinute)
        val points = xs(listOf(second, first), week)
        assertEquals(listOf(1L, 2L), points.map { it.entryId })
        assertTrue(points[1].x > points[0].x)
        assertTrue(points.all { it.x in 0f..1f })
    }

    @Test
    fun sameOffsetPositionsUnchanged() {
        val entries = listOf("2026-11-03T00:00", "2026-11-05T13:37", "2026-11-08T23:59").mapIndexed { i, t -> entry(i + 1L, t, 60) }
        val points = xs(entries, week)
        val expected =
            entries.map {
                ChronoUnit.MINUTES.between(week.start.atStartOfDay(), it.time.localDateTime()) / (7 * 1440f)
            }
        assertEquals(expected, points.map { it.x })
    }

    @Test
    fun extremeOffsetJumpFallsBackToWallTime() {
        val day = GraphPeriod.containing(PeriodKind.DAY, LocalDate.of(2026, 11, 4), monday)
        val first = entry(1, "2026-11-04T00:10", 0)
        val second = entry(2, "2026-11-04T23:50", 840)
        assertTrue(second.time.epochMinute > first.time.epochMinute)
        val points = xs(listOf(first, second), day)
        assertEquals(listOf(10 / 1440f, 1430 / 1440f), points.map { it.x })
    }
}
