package com.wolf.feelingsdiary.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Locale

class CsvExporterTest {
    private val headers = listOf("Datum", "Uhrzeit", "Zeitzone", "Hauptgefühl", "Gefühle", "Stimmung", "Personen")
    private val de = CsvFormat(';', ',')
    private val us = CsvFormat(',', '.')

    private fun entry(
        epochMinute: Int,
        offset: Int = 60,
        ids: List<Long> = listOf(1L),
        valences: List<Int> = listOf(2),
        people: Int = 0,
        id: Long = epochMinute.toLong(),
    ) = Entry(id, EntryTime(epochMinute, offset), people, ids, valences)

    private fun lines(csv: String) = csv.removeSuffix("\r\n").split("\r\n")

    @Test
    fun germanFormatUsesSemicolonAndComma() {
        assertEquals(CsvFormat(';', ','), CsvFormat.forLocale(Locale.GERMANY))
        assertEquals(CsvFormat(',', '.'), CsvFormat.forLocale(Locale.US))
    }

    @Test
    fun rowHasLocalTimeOffsetFeelingsMoodPeople() {
        val e = entry(29_000_000, offset = 330, ids = listOf(1L, 2L), valences = listOf(-1, 2), people = 3)
        val csv = CsvExporter.build(listOf(e), mapOf(1L to "traurig", 2L to "froh")::get, headers, de)
        val local = LocalDateTime.ofEpochSecond(29_000_000 * 60L, 0, ZoneOffset.ofTotalSeconds(330 * 60))
        val date = local.toLocalDate().toString()
        val time = "%02d:%02d".format(local.hour, local.minute)
        assertEquals("$date;$time;UTC+5:30;traurig;traurig (-1), froh (+2);0,00;3", lines(csv)[1])
    }

    @Test
    fun headerFirstAndCrlf() {
        val csv = CsvExporter.build(listOf(entry(1000)), { "x" }, headers, de)
        assertTrue(csv.startsWith(headers.joinToString(";") + "\r\n"))
        assertTrue(csv.endsWith("\r\n"))
        assertFalse(csv.removeSuffix("\r\n").contains(Regex("(?<!\r)\n")))
    }

    @Test
    fun sortsByEpochMinute() {
        val later = entry(2000, people = 7)
        val earlier = entry(1000, people = 4)
        val rows = lines(CsvExporter.build(listOf(later, earlier), { "x" }, headers, de))
        assertTrue(rows[1].endsWith(";4"))
        assertTrue(rows[2].endsWith(";7"))
    }

    @Test
    fun quotesDelimiterQuoteAndNewline() {
        val csv = CsvExporter.build(listOf(entry(1000)), { "a;\"b\"\nc" }, headers, de)
        assertTrue(csv, csv.contains(";\"a;\"\"b\"\"\nc\";"))
    }

    @Test
    fun escapesFormulaStart() {
        val e = entry(1000, ids = listOf(1L, 2L), valences = listOf(-2, 1))
        val row = lines(CsvExporter.build(listOf(e), mapOf(1L to "=SUM(A1)", 2L to "ok")::get, headers, de))[1]
        val cells = row.split(";")
        assertEquals("'=SUM(A1)", cells[3])
        assertEquals("'=SUM(A1) (-2), ok (+1)", cells[4])
        assertEquals("-1,50", cells[5])
    }

    @Test
    fun skipsUnknownFeeling() {
        val e = entry(1000, ids = listOf(1L, 9L), valences = listOf(1, -1))
        val row = lines(CsvExporter.build(listOf(e), mapOf(1L to "froh")::get, headers, de))[1]
        assertEquals("froh (+1)", row.split(";")[4])
    }

    @Test
    fun negativeOffsetAndEnglish() {
        val e = entry(29_000_000, offset = -570, ids = listOf(1L, 2L), valences = listOf(1, 1))
        val row = lines(CsvExporter.build(listOf(e), mapOf(1L to "happy", 2L to "calm")::get, headers, us))[1]
        assertTrue(row, row.contains(",UTC-9:30,happy,\"happy (+1), calm (+1)\",1.50,0"))
    }
}
