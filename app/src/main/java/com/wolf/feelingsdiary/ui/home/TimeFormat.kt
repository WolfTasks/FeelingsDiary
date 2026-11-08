package com.wolf.feelingsdiary.ui.home

import android.text.format.DateFormat
import com.wolf.feelingsdiary.domain.GraphPeriod
import com.wolf.feelingsdiary.domain.PeriodKind
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

private val DAY_LABEL_HOURS = listOf(0, 6, 12, 18)
private val MONTH_LABEL_DAYS = listOf(1, 8, 15, 22, 29)
private const val RANGE_LABEL_COUNT = 5

fun formatEntryTime(
    t: LocalDateTime,
    locale: Locale,
): String = t.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(locale))

fun formatPeriod(
    p: GraphPeriod,
    locale: Locale,
): String =
    when (p.kind) {
        PeriodKind.YEAR -> {
            p.start.year.toString()
        }

        PeriodKind.MONTH -> {
            p.start.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))
        }

        PeriodKind.DAY -> {
            p.start.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale))
        }

        PeriodKind.WEEK, PeriodKind.RANGE -> {
            val f = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
            "${p.start.format(f)} – ${p.endInclusive.format(f)}"
        }
    }

/** Label positions use the graph's 0..1 x scale (a day is labelled at its middle). */
fun xAxisLabels(
    p: GraphPeriod,
    locale: Locale,
): List<AxisLabel> {
    val days = p.days.toFloat()

    fun dayX(date: LocalDate) = (ChronoUnit.DAYS.between(p.start, date) + 0.5f) / days
    return when (p.kind) {
        PeriodKind.DAY -> {
            val f = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
            DAY_LABEL_HOURS.map { AxisLabel(it / 24f, LocalTime.of(it, 0).format(f)) }
        }

        PeriodKind.WEEK -> {
            (0 until p.days).map {
                val date = p.start.plusDays(it.toLong())
                AxisLabel(dayX(date), date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale))
            }
        }

        PeriodKind.MONTH -> {
            MONTH_LABEL_DAYS.filter { it <= p.days }.map { AxisLabel(dayX(p.start.withDayOfMonth(it)), it.toString()) }
        }

        PeriodKind.YEAR -> {
            (1..12).map { m ->
                val first = LocalDate.of(p.start.year, m, 1)
                val middle = ChronoUnit.DAYS.between(p.start, first) + first.lengthOfMonth() / 2f
                AxisLabel(middle / days, first.month.getDisplayName(TextStyle.SHORT, locale))
            }
        }

        PeriodKind.RANGE -> {
            val f = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "dM"), locale)
            (0 until RANGE_LABEL_COUNT).map { i ->
                val date = p.start.plusDays(Math.round((p.days - 1) * i / (RANGE_LABEL_COUNT - 1f)).toLong())
                AxisLabel(dayX(date), date.format(f))
            }
        }
    }
}

private const val MINUTES_PER_HOUR = 60

/** "UTC+1", "UTC-9:30": hours without leading zero, minutes only when non-zero. */
fun formatUtcOffset(minutes: Int): String {
    val abs = Math.abs(minutes)
    val sign = if (minutes < 0) "-" else "+"
    val rest = abs % MINUTES_PER_HOUR
    return "UTC$sign${abs / MINUTES_PER_HOUR}" + if (rest != 0) ":%02d".format(rest) else ""
}
