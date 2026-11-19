package com.wolf.feelingsdiary.domain

import java.text.DecimalFormatSymbols
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Byte order mark that makes Excel read the file as UTF-8 (umlauts). */
const val UTF8_BOM = "\uFEFF"

/** Separators as the spreadsheet app of [Locale] expects them: decimal comma goes with ';'. */
data class CsvFormat(
    val delimiter: Char,
    val decimalSeparator: Char,
) {
    companion object {
        fun forLocale(locale: Locale): CsvFormat {
            val decimal = DecimalFormatSymbols.getInstance(locale).decimalSeparator
            return CsvFormat(if (decimal == ',') ';' else ',', decimal)
        }
    }
}

/** One row per entry, oldest first: date, time, zone, main feeling, feelings with valence, mood, people. */
object CsvExporter {
    private const val LINE_END = "\r\n"
    private val DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT)
    private val TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)

    // OWASP CSV injection list; tab and CR because some importers trim them and then evaluate the rest.
    private val FORMULA_START = setOf('=', '+', '-', '@', '\t', '\r')

    /** [feelingLabel] returns null for unknown ids, which are left out. */
    fun build(
        entries: List<Entry>,
        feelingLabel: (Long) -> String?,
        headers: List<String>,
        format: CsvFormat,
    ): String {
        val out = StringBuilder()

        fun row(cells: List<String>) {
            cells.joinTo(out, format.delimiter.toString()) { quote(it, format.delimiter) }
            out.append(LINE_END)
        }
        row(headers)
        entries.sortedBy { it.time.epochMinute }.forEach { e ->
            val local = e.time.localDateTime()
            val labelled = e.feelingIds.zip(e.valences).mapNotNull { (id, v) -> feelingLabel(id)?.let { it to v } }
            row(
                listOf(
                    local.format(DATE),
                    local.format(TIME),
                    formatUtcOffset(e.time.utcOffsetMinutes),
                    text(
                        e.feelingIds
                            .firstOrNull()
                            ?.let(feelingLabel)
                            .orEmpty(),
                    ),
                    text(labelled.joinToString(", ") { (label, v) -> "$label (${signed(v)})" }),
                    "%.2f".format(Locale.ROOT, GraphBuilder.weightedValence(e.valences)).replace('.', format.decimalSeparator),
                    e.people.toString(),
                ),
            )
        }
        return out.toString()
    }

    private fun signed(v: Int) = if (v > 0) "+$v" else v.toString()

    /** Keeps spreadsheet apps from running a user's feeling name as a formula. */
    private fun text(s: String) = if (s.firstOrNull() in FORMULA_START) "'$s" else s

    private fun quote(
        cell: String,
        delimiter: Char,
    ) = if (cell.any { it == delimiter || it == '"' || it == '\r' || it == '\n' }) "\"${cell.replace("\"", "\"\"")}\"" else cell
}
