package com.wolf.feelingsdiary.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

enum class PeriodKind { YEAR, MONTH, WEEK, DAY, RANGE }

/** Inclusive date span shown in the graph. */
data class GraphPeriod(
    val kind: PeriodKind,
    val start: LocalDate,
    val endInclusive: LocalDate,
) {
    val days: Int get() = ChronoUnit.DAYS.between(start, endInclusive).toInt() + 1

    /** Neighbouring calendar unit; for [PeriodKind.RANGE] shifted by its own length. */
    fun shift(
        steps: Int,
        firstDayOfWeek: DayOfWeek,
    ): GraphPeriod =
        when (kind) {
            PeriodKind.RANGE -> {
                val offset = days.toLong() * steps
                GraphPeriod(kind, start.plusDays(offset), endInclusive.plusDays(offset))
            }

            PeriodKind.YEAR -> {
                containing(kind, start.plusYears(steps.toLong()), firstDayOfWeek)
            }

            PeriodKind.MONTH -> {
                containing(kind, start.plusMonths(steps.toLong()), firstDayOfWeek)
            }

            PeriodKind.WEEK -> {
                containing(kind, start.plusWeeks(steps.toLong()), firstDayOfWeek)
            }

            PeriodKind.DAY -> {
                containing(kind, start.plusDays(steps.toLong()), firstDayOfWeek)
            }
        }

    companion object {
        fun containing(
            kind: PeriodKind,
            date: LocalDate,
            firstDayOfWeek: DayOfWeek,
        ): GraphPeriod =
            when (kind) {
                PeriodKind.YEAR -> {
                    GraphPeriod(kind, date.with(TemporalAdjusters.firstDayOfYear()), date.with(TemporalAdjusters.lastDayOfYear()))
                }

                PeriodKind.MONTH -> {
                    GraphPeriod(kind, date.with(TemporalAdjusters.firstDayOfMonth()), date.with(TemporalAdjusters.lastDayOfMonth()))
                }

                PeriodKind.WEEK -> {
                    val first = date.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
                    GraphPeriod(kind, first, first.plusDays(6))
                }

                PeriodKind.DAY -> {
                    GraphPeriod(kind, date, date)
                }

                PeriodKind.RANGE -> {
                    throw IllegalArgumentException("RANGE has no containing period; use GraphPeriod.range")
                }
            }

        fun range(
            a: LocalDate,
            b: LocalDate,
        ): GraphPeriod = if (a <= b) GraphPeriod(PeriodKind.RANGE, a, b) else GraphPeriod(PeriodKind.RANGE, b, a)
    }
}
