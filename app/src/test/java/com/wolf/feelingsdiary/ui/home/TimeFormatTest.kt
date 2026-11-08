package com.wolf.feelingsdiary.ui.home

import com.wolf.feelingsdiary.domain.GraphPeriod
import com.wolf.feelingsdiary.domain.PeriodKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class TimeFormatTest {
    private val time = LocalDateTime.of(2026, 11, 7, 19, 22)

    @Test
    fun utcOffsetIsFormattedCompactly() {
        assertEquals("UTC+1", formatUtcOffset(60))
        assertEquals("UTC+2", formatUtcOffset(120))
        assertEquals("UTC+0", formatUtcOffset(0))
        assertEquals("UTC-3", formatUtcOffset(-180))
        assertEquals("UTC+5:30", formatUtcOffset(330))
        assertEquals("UTC-9:30", formatUtcOffset(-570))
        assertEquals("UTC+14", formatUtcOffset(840))
    }

    /** The offset is a technical label: ASCII digits even when the default locale uses other ones. */
    @Test
    fun utcOffsetIgnoresDefaultLocaleDigits() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("ar-EG-u-nu-arab"))
            assertEquals("UTC+5:30", formatUtcOffset(330))
            assertEquals("UTC-9:45", formatUtcOffset(-585))
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun entryTimeFollowsLocale() {
        assertEquals("07.11.26, 19:22", formatEntryTime(time, Locale.GERMANY))
        val us = formatEntryTime(time, Locale.US)
        assertNotEquals("07.11.26, 19:22", us)
        assertTrue(us, us.contains("PM"))
    }

    @Test
    fun weekPeriodShowsBothDates() {
        val week = GraphPeriod(PeriodKind.WEEK, LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 8))
        val text = formatPeriod(week, Locale.GERMANY)
        assertTrue(text, text.contains("02.11.2026"))
        assertTrue(text, text.contains("08.11.2026"))
    }

    @Test
    fun yearAndMonthPeriods() {
        val year = GraphPeriod(PeriodKind.YEAR, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31))
        assertEquals("2026", formatPeriod(year, Locale.GERMANY))
        val month = GraphPeriod(PeriodKind.MONTH, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30))
        assertEquals("November 2026", formatPeriod(month, Locale.GERMANY))
    }

    @Test
    fun weekAxisStartsWithFirstWeekday() {
        val week = GraphPeriod(PeriodKind.WEEK, LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 8))
        val labels = xAxisLabels(week, Locale.GERMANY)
        assertEquals(7, labels.size)
        assertEquals(DayOfWeek.MONDAY.getDisplayName(TextStyle.SHORT, Locale.GERMANY), labels.first().text)
    }

    @Test
    fun dayMonthYearAndRangeAxes() {
        val day = GraphPeriod(PeriodKind.DAY, LocalDate.of(2026, 11, 7), LocalDate.of(2026, 11, 7))
        assertEquals(listOf(0f, 0.25f, 0.5f, 0.75f), xAxisLabels(day, Locale.GERMANY).map { it.x })
        val month = GraphPeriod(PeriodKind.MONTH, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30))
        assertEquals(listOf("1", "8", "15", "22", "29"), xAxisLabels(month, Locale.GERMANY).map { it.text })
        val year = GraphPeriod(PeriodKind.YEAR, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31))
        assertEquals(12, xAxisLabels(year, Locale.GERMANY).size)
        val range = GraphPeriod.range(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 21))
        assertEquals(5, xAxisLabels(range, Locale.GERMANY).size)
    }

    @Test
    fun rangeAxisIsLocalized() {
        val range = GraphPeriod.range(LocalDate.of(2026, 11, 7), LocalDate.of(2026, 11, 21))
        assertEquals("7.11.", xAxisLabels(range, Locale.GERMANY).first().text)
        assertEquals("11/7", xAxisLabels(range, Locale.US).first().text)
    }
}
