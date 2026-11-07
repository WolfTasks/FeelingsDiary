package com.wolf.feelingsdiary.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class GraphPeriodTest {
    private val monday = DayOfWeek.MONDAY

    @Test
    fun weekStartsByLocale() {
        val date = LocalDate.of(2026, 11, 7)
        val mon = GraphPeriod.containing(PeriodKind.WEEK, date, DayOfWeek.MONDAY)
        assertEquals(LocalDate.of(2026, 11, 2), mon.start)
        assertEquals(LocalDate.of(2026, 11, 8), mon.endInclusive)
        val sun = GraphPeriod.containing(PeriodKind.WEEK, date, DayOfWeek.SUNDAY)
        assertEquals(LocalDate.of(2026, 11, 1), sun.start)
        assertEquals(LocalDate.of(2026, 11, 7), sun.endInclusive)
    }

    @Test
    fun shiftIsGapless() {
        val jan = GraphPeriod.containing(PeriodKind.MONTH, LocalDate.of(2026, 1, 15), monday)
        val feb = jan.shift(1, monday)
        assertEquals(LocalDate.of(2026, 2, 1), feb.start)
        assertEquals(LocalDate.of(2026, 2, 28), feb.endInclusive)
        val mar = feb.shift(1, monday)
        assertEquals(LocalDate.of(2026, 3, 1), mar.start)
        assertEquals(LocalDate.of(2026, 3, 31), mar.endInclusive)

        assertEquals(366, GraphPeriod.containing(PeriodKind.YEAR, LocalDate.of(2028, 5, 5), monday).days)

        val week = GraphPeriod(PeriodKind.WEEK, LocalDate.of(2026, 12, 28), LocalDate.of(2027, 1, 3)).shift(1, monday)
        assertEquals(LocalDate.of(2027, 1, 4), week.start)
        assertEquals(LocalDate.of(2027, 1, 10), week.endInclusive)

        val range = GraphPeriod.range(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 10)).shift(-1, monday)
        assertEquals(LocalDate.of(2026, 10, 22), range.start)
        assertEquals(LocalDate.of(2026, 10, 31), range.endInclusive)
    }

    @Test
    fun rangeSortsEnds() {
        val range = GraphPeriod.range(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 1))
        assertEquals(LocalDate.of(2026, 11, 1), range.start)
        assertEquals(LocalDate.of(2026, 11, 10), range.endInclusive)
    }

    @Test
    fun containingRangeThrows() {
        assertThrows(IllegalArgumentException::class.java) {
            GraphPeriod.containing(PeriodKind.RANGE, LocalDate.of(2026, 11, 1), monday)
        }
    }
}
