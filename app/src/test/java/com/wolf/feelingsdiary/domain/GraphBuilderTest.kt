package com.wolf.feelingsdiary.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

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
    fun entryHeightIsMeanOfStoredValences() {
        val data = GraphBuilder.build(listOf(entry(1, "2026-11-03T08:00", 60, feelings = listOf(a to 2, b to -1))), week, monday)
        val point = data.points.single()
        assertEquals(0.5f, point.valence, 0f)
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
    fun aggregateAveragesAndLimitsOthers() {
        val year = GraphPeriod.containing(PeriodKind.YEAR, LocalDate.of(2026, 6, 1), monday)
        val entries =
            listOf(
                entry(1, "2026-02-01T08:00", 60, people = 2, feelings = listOf(a to 2, 3L to 0, 4L to 0, 5L to 0, 6L to 0)),
                entry(2, "2026-02-01T09:00", 60, people = 4, feelings = listOf(a to -2)),
            )
        val point = GraphBuilder.build(entries, year, monday).points.single()
        assertEquals(-0.8f, point.valence, 1e-6f)
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
    fun peopleScale() {
        fun scale(people: Int) = GraphBuilder.build(listOf(entry(1, "2026-11-03T08:00", 60, people = people)), week, monday).peopleScaleMax
        assertEquals(5, scale(0))
        assertEquals(10, scale(7))
        assertEquals(1000, scale(999))
    }
}
