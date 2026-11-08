package com.wolf.feelingsdiary.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlin.random.Random

object ReminderPlanner {
    private const val SEARCH_DAYS = 2L

    fun activeIndices(count: Int): List<Int> =
        when (count) {
            1 -> listOf(2)
            2 -> listOf(0, 3)
            3 -> listOf(0, 2, 3)
            else -> listOf(0, 1, 2, 3)
        }

    fun slotsFor(
        date: LocalDate,
        s: ReminderSettings,
        salt: Long,
    ): List<LocalTime> =
        activeIndices(s.count)
            .mapNotNull { index ->
                when (s.mode) {
                    ReminderMode.FIXED -> {
                        val time = s.fixedTimes[index].withSecond(0).withNano(0)
                        time.takeUnless { isQuiet(it, s) }
                    }

                    ReminderMode.RANDOM -> {
                        val window = s.windows[index]
                        val allowed = windowMinutes(window).filterNot { isQuiet(it, s) }
                        if (allowed.isEmpty()) {
                            null
                        } else {
                            val random = Random(salt xor (date.toEpochDay() * 31 + index))
                            val minute = allowed[random.nextInt(allowed.size)]
                            LocalTime.of(minute / 60, minute % 60)
                        }
                    }
                }
            }.distinct()
            .sorted()

    fun next(
        s: ReminderSettings,
        after: ZonedDateTime,
        salt: Long,
    ): ZonedDateTime? {
        if (!s.enabled) return null
        val zone = after.zone
        for (offset in 0..SEARCH_DAYS) {
            val date = after.toLocalDate().plusDays(offset)
            for (slot in slotsFor(date, s, salt)) {
                val candidate = ZonedDateTime.of(date, slot, zone)
                if (candidate.isAfter(after)) return candidate
            }
        }
        return null
    }

    /** Whether [time] (to the minute) lies in the quiet hours; quiet start == end means no quiet hours. */
    fun isQuiet(
        time: LocalTime,
        s: ReminderSettings,
    ): Boolean = isQuiet(time.toSecondOfDay() / 60, s)

    /** Whether every minute of [window] (end exclusive) is quiet, so the planner never picks a time in it. */
    fun isFullyQuiet(
        window: TimeWindow,
        s: ReminderSettings,
    ): Boolean = windowMinutes(window).all { isQuiet(it, s) }

    private fun windowMinutes(window: TimeWindow) = window.start.toSecondOfDay() / 60 until window.end.toSecondOfDay() / 60

    private fun isQuiet(
        minuteOfDay: Int,
        s: ReminderSettings,
    ): Boolean {
        val start = s.quietStart.toSecondOfDay() / 60
        val end = s.quietEnd.toSecondOfDay() / 60
        return when {
            start == end -> false
            start < end -> minuteOfDay in start until end
            else -> minuteOfDay >= start || minuteOfDay < end
        }
    }
}
