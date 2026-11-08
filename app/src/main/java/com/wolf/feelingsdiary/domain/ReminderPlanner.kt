package com.wolf.feelingsdiary.domain

import java.time.Duration
import java.time.Instant
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
            // A slot in a spring-forward gap moves behind later slots, so all of the day are compared.
            slotsFor(date, s, salt)
                .map { ZonedDateTime.of(date, it, zone) }
                .filter { it.isAfter(after) }
                .minOrNull()
                ?.let { return it }
        }
        return null
    }

    /** An alarm this much before its slot or later counts as the reminder itself, not as a stage. */
    val STAGE_TOLERANCE: Duration = Duration.ofMinutes(1)

    // Android caps the delivery window of an inexact alarm at one hour.
    private const val MAX_WINDOW_MILLIS = 60 * 60_000L

    /**
     * When to set the alarm now for the reminder at [target], so it arrives at most [maxDelayMinutes] late.
     *
     * An inexact alarm set at `now` for `t` arrives up to `min(0.75 * (t - now), 60 min)` after `t` (measured).
     * Within `4/3 * maxDelay` of the target the alarm goes straight to the target, so its window is at most
     * `maxDelay`. Further out it goes to a stage `x` after now, as late as possible while the stage still arrives
     * by the target at the latest: `x + min(0.75 * x, 60 min) <= lead`, i.e. `x = lead - 60 min` once the window
     * is capped (lead >= 140 min), else `x = 4/7 * lead`. The stage plans again from wherever it lands, which is
     * never after the target and leaves at most `lead - x`: 60 min, or 3/7 of the lead.
     */
    fun nextWake(
        target: Instant,
        now: Instant,
        maxDelayMinutes: Int,
    ): Instant {
        val lead = Duration.between(now, target).toMillis()
        if (lead * 3 <= maxDelayMinutes * 60_000L * 4) return target
        val stage = if (lead * 3 >= MAX_WINDOW_MILLIS * 7) lead - MAX_WINDOW_MILLIS else lead * 4 / 7
        return now.plusMillis(stage)
    }

    /** Whether an alarm arriving at [now] for the reminder at [target] is a stage: only plan again, do not remind. */
    fun isStage(
        target: Instant,
        now: Instant,
    ): Boolean = now.isBefore(target - STAGE_TOLERANCE)

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
