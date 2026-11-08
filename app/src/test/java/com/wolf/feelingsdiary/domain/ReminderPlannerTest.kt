package com.wolf.feelingsdiary.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.random.Random

class ReminderPlannerTest {
    private val berlin = ZoneId.of("Europe/Berlin")
    private val day = LocalDate.of(2026, 5, 4)

    private fun t(
        h: Int,
        m: Int = 0,
    ) = LocalTime.of(h, m)

    private fun w(
        a: LocalTime,
        b: LocalTime,
    ) = TimeWindow(a, b)

    @Test
    fun activeIndicesFollowTable() {
        assertEquals(listOf(2), ReminderPlanner.activeIndices(1))
        assertEquals(listOf(0, 3), ReminderPlanner.activeIndices(2))
        assertEquals(listOf(0, 2, 3), ReminderPlanner.activeIndices(3))
        assertEquals(listOf(0, 1, 2, 3), ReminderPlanner.activeIndices(4))
    }

    @Test
    fun randomSlotLiesInsideItsWindow() {
        val s = ReminderSettings(count = 4)
        for (i in 0 until 1000) {
            val slots = ReminderPlanner.slotsFor(day.plusDays(i.toLong()), s, 7L)
            assertEquals(4, slots.size)
            slots.forEachIndexed { k, slot ->
                val win = s.windows[k]
                assertTrue("$slot in $win", slot >= win.start && slot < win.end)
            }
        }
    }

    @Test
    fun sameDayAndSaltGiveSameSlots() {
        val s = ReminderSettings(count = 4)
        assertEquals(ReminderPlanner.slotsFor(day, s, 5L), ReminderPlanner.slotsFor(day, s, 5L))
    }

    @Test
    fun differentDaysDiffer() {
        val s = ReminderSettings(count = 4)
        val perDay = (0 until 30).map { ReminderPlanner.slotsFor(day.plusDays(it.toLong()), s, 5L) }
        for (k in 0 until 4) {
            assertTrue(perDay.map { it[k] }.toSet().size >= 2)
        }
    }

    @Test
    fun quietHoursClipWindow() {
        val s =
            ReminderSettings(
                count = 4,
                windows = listOf(w(t(7), t(9))) + ReminderSettings.DEFAULT_WINDOWS.drop(1),
                quietStart = t(22),
                quietEnd = t(8),
            )
        for (i in 0 until 200) {
            val first = ReminderPlanner.slotsFor(day.plusDays(i.toLong()), s, 1L).first()
            assertTrue("$first", first >= t(8) && first < t(9))
        }
    }

    @Test
    fun quietHoursInsideWindow() {
        val s =
            ReminderSettings(
                count = 1,
                windows = ReminderSettings.DEFAULT_WINDOWS.take(2) + w(t(12), t(15)) + ReminderSettings.DEFAULT_WINDOWS[3],
                quietStart = t(13),
                quietEnd = t(14),
            )
        for (i in 0 until 500) {
            val slot = ReminderPlanner.slotsFor(day.plusDays(i.toLong()), s, 2L).single()
            assertTrue("$slot", slot.hour != 13 && slot >= t(12) && slot < t(15))
        }
    }

    @Test
    fun windowFullyInQuietIsSkipped() {
        val s =
            ReminderSettings(
                count = 2,
                windows = listOf(w(t(23), t(23, 59))) + ReminderSettings.DEFAULT_WINDOWS.drop(1),
            )
        val slots = ReminderPlanner.slotsFor(day, s, 3L)
        assertEquals(1, slots.size)
        assertTrue(slots.single() >= s.windows[3].start && slots.single() < s.windows[3].end)
    }

    @Test
    fun fixedModeUsesActiveTimesAndSkipsQuiet() {
        val s =
            ReminderSettings(
                count = 3,
                mode = ReminderMode.FIXED,
                fixedTimes = listOf(t(9), t(12), t(16), t(23)),
            )
        assertEquals(listOf(t(9), t(16)), ReminderPlanner.slotsFor(day, s, 1L))
    }

    @Test
    fun fixedModeDuplicatesCollapse() {
        val s =
            ReminderSettings(
                count = 4,
                mode = ReminderMode.FIXED,
                fixedTimes = listOf(t(9), t(10), t(10), t(11)),
            )
        assertEquals(listOf(t(9), t(10), t(11)), ReminderPlanner.slotsFor(day, s, 1L))
    }

    @Test
    fun nextIsStrictlyAfter() {
        val s = ReminderSettings(count = 4, mode = ReminderMode.FIXED)
        val after = ZonedDateTime.of(day, t(10), berlin)
        assertEquals(ZonedDateTime.of(day, t(13), berlin), ReminderPlanner.next(s, after, 1L))
    }

    @Test
    fun nextRollsOverToTomorrow() {
        val s = ReminderSettings(count = 4, mode = ReminderMode.FIXED)
        val after = ZonedDateTime.of(day, t(21), berlin)
        assertEquals(ZonedDateTime.of(day.plusDays(1), t(10), berlin), ReminderPlanner.next(s, after, 1L))
    }

    @Test
    fun disabledGivesNull() {
        assertNull(ReminderPlanner.next(ReminderSettings(enabled = false), ZonedDateTime.of(day, t(8), berlin), 1L))
    }

    /** Successive reminders from [start] on, as the alarm chain plans them (each one after the previous). */
    private fun chain(
        s: ReminderSettings,
        start: ZonedDateTime,
        n: Int,
    ) = generateSequence(ReminderPlanner.next(s, start, 1L)) { ReminderPlanner.next(s, it, 1L) }.take(n).toList()

    private fun fixedNoQuiet(vararg times: LocalTime) =
        ReminderSettings(count = 4, mode = ReminderMode.FIXED, fixedTimes = times.toList(), quietStart = t(0), quietEnd = t(0))

    /** 29.03.2026, 02:00 CET jumps to 03:00 CEST: a slot in the gap moves to 03:59 and must not hide 03:10. */
    @Test
    fun springForwardGapLosesNoSlot() {
        val date = LocalDate.of(2026, 3, 29)
        val s = fixedNoQuiet(t(2, 59), t(3, 10), t(9), t(20))
        val after = ZonedDateTime.of(date, t(3), berlin)
        assertEquals(ZoneOffset.ofHours(2), after.offset)
        val expected =
            listOf(
                ZonedDateTime.of(date, t(3, 10), berlin),
                ZonedDateTime.of(date, t(3, 59), berlin),
                ZonedDateTime.of(date, t(9), berlin),
                ZonedDateTime.of(date, t(20), berlin),
            )
        assertEquals(expected, chain(s, after, 4))
        // Planned from before the jump: the same reminders, none lost, none twice.
        assertEquals(expected, chain(s, ZonedDateTime.of(date, t(1), berlin), 4))
    }

    /** 25.10.2026, 03:00 CEST falls back to 02:00 CET: 02:30 exists twice but reminds only once. */
    @Test
    fun fallBackSlotFiresOnce() {
        val date = LocalDate.of(2026, 10, 25)
        val s = fixedNoQuiet(t(2, 30), t(9), t(15), t(20))
        val plan = chain(s, ZonedDateTime.of(date, t(0), berlin), 4)
        assertEquals(listOf(t(2, 30), t(9), t(15), t(20)), plan.map { it.toLocalTime() })
        assertEquals(ZoneOffset.ofHours(2), plan.first().offset)
        assertTrue(plan.zipWithNext().all { (a, b) -> b.isAfter(a) })
    }

    @Test
    fun allWindowsInQuietGivesNull() {
        val s = ReminderSettings(quietStart = t(0, 1), quietEnd = t(0, 0))
        assertNull(ReminderPlanner.next(s, ZonedDateTime.of(day, t(8), berlin), 1L))
    }

    @Test
    fun isQuietHandlesWrapAndNoQuietHours() {
        val night = ReminderSettings(quietStart = t(22), quietEnd = t(8))
        assertTrue(ReminderPlanner.isQuiet(t(23, 30), night))
        assertTrue(ReminderPlanner.isQuiet(t(7, 59), night))
        assertFalse(ReminderPlanner.isQuiet(t(8), night))
        assertFalse(ReminderPlanner.isQuiet(t(21, 59), night))
        val noon = ReminderSettings(quietStart = t(12), quietEnd = t(14))
        assertTrue(ReminderPlanner.isQuiet(t(13), noon))
        assertFalse(ReminderPlanner.isQuiet(t(14), noon))
        assertFalse(ReminderPlanner.isQuiet(t(3), ReminderSettings(quietStart = t(3), quietEnd = t(3))))
    }

    @Test
    fun isFullyQuietOnlyWhenEveryMinuteIsQuiet() {
        val night = ReminderSettings(quietStart = t(22), quietEnd = t(8))
        assertTrue(ReminderPlanner.isFullyQuiet(w(t(22, 30), t(23)), night))
        // The window end is exclusive, like the planner's random minutes.
        assertTrue(ReminderPlanner.isFullyQuiet(w(t(7), t(8)), night))
        assertFalse(ReminderPlanner.isFullyQuiet(w(t(7), t(8, 1)), night))
        assertFalse(ReminderPlanner.isFullyQuiet(w(t(21), t(23)), night))
        assertFalse(ReminderPlanner.isFullyQuiet(w(t(1), t(2)), ReminderSettings(quietStart = t(5), quietEnd = t(5))))
    }

    // --- Staged scheduling (nextWake) ---

    private val start = Instant.parse("2026-11-08T08:30:00Z")

    private fun minutes(m: Long) = Duration.ofMinutes(m)

    /** Latest delivery of an inexact alarm, as measured on the A54: trigger + min(0.75 × lead, 60 min). */
    private fun latest(
        setAt: Instant,
        trigger: Instant,
    ): Instant {
        val lead = Duration.between(setAt, trigger).toMillis()
        return trigger.plusMillis(minOf(lead * 3 / 4, minutes(60).toMillis()))
    }

    private class Run(
        val stages: Int,
        val delivered: Instant,
    )

    /**
     * Plays the alarm chain like the scheduler does: set the alarm for nextWake, let [deliver] pick a moment in
     * the model window, and stop at the first delivery that is no stage. Checks every alarm on the way.
     */
    private fun simulate(
        target: Instant,
        maxDelay: Int,
        deliver: (trigger: Instant, latest: Instant) -> Instant,
    ): Run {
        var now = start
        var stages = 0
        while (true) {
            val wake = ReminderPlanner.nextWake(target, now, maxDelay)
            val last = latest(now, wake)
            assertFalse("wake $wake before now $now", wake.isBefore(now))
            if (wake == target) {
                assertFalse(
                    "final alarm set ${Duration.between(now, target)} ahead (max $maxDelay)",
                    Duration.between(now, target).toMillis() * 3 > maxDelay * 4 * 60_000L,
                )
            } else {
                assertTrue("stage $wake not before target $target", wake.isBefore(target))
                assertFalse("stage may land at $last after target $target", last.isAfter(target))
            }
            val at = deliver(wake, last)
            if (!ReminderPlanner.isStage(target, at)) return Run(stages, at)
            stages++
            assertTrue("too many stages", stages <= 50)
            now = at
        }
    }

    @Test
    fun nearTargetIsSetDirectly() {
        assertEquals(start + minutes(2), ReminderPlanner.nextWake(start + minutes(2), start, 5))
        assertEquals(start + minutes(20), ReminderPlanner.nextWake(start + minutes(20), start, 15))
        assertEquals(start + minutes(80), ReminderPlanner.nextWake(start + minutes(80), start, 60))
    }

    @Test
    fun farTargetGetsAStageThatLandsBeforeIt() {
        val target = start + minutes(5 * 60)
        val wake = ReminderPlanner.nextWake(target, start, 15)
        assertTrue(wake.isAfter(start))
        assertTrue(wake.isBefore(target))
        assertFalse(latest(start, wake).isAfter(target))
    }

    @Test
    fun pastTargetIsSetDirectly() {
        assertEquals(start, ReminderPlanner.nextWake(start, start + minutes(1), 15))
    }

    @Test
    fun stageIsMoreThanAMinuteBeforeTheTarget() {
        val target = start + minutes(10)
        assertTrue(ReminderPlanner.isStage(target, target - minutes(2)))
        assertFalse(ReminderPlanner.isStage(target, target - minutes(1)))
        assertFalse(ReminderPlanner.isStage(target, target + minutes(5)))
    }

    /** Worst case for the delay: every alarm, stages included, comes at the latest moment the model allows. */
    @Test
    fun latestDeliveriesStayWithinTheMaxDelay() {
        for (maxDelay in ReminderSettings.MAX_DELAY_OPTIONS) {
            for (leadMinutes in 1L..48 * 60 step 7) {
                val target = start + minutes(leadMinutes)
                val run = simulate(target, maxDelay) { _, last -> last }
                assertFalse("late for lead $leadMinutes, max $maxDelay", run.delivered.isAfter(target + minutes(maxDelay.toLong())))
                assertFalse(run.delivered.isBefore(target - minutes(1)))
            }
        }
    }

    /** Worst case for the number of stages: every alarm comes at its trigger time, as early as possible. */
    @Test
    fun earliestDeliveriesNeedFewStages() {
        for (maxDelay in ReminderSettings.MAX_DELAY_OPTIONS) {
            for (leadMinutes in 1L..24 * 60) {
                val target = start + minutes(leadMinutes)
                val run = simulate(target, maxDelay) { trigger, _ -> trigger }
                assertTrue("${run.stages} stages for lead $leadMinutes, max $maxDelay", run.stages <= 6)
                assertFalse(run.delivered.isAfter(target + minutes(maxDelay.toLong())))
            }
        }
    }

    @Test
    fun randomDeliveriesStayWithinTheMaxDelay() {
        val random = Random(42)
        repeat(5000) {
            val maxDelay = ReminderSettings.MAX_DELAY_OPTIONS.random(random)
            val target = start + Duration.ofSeconds(random.nextLong(1, 48 * 3600L))
            val run =
                simulate(target, maxDelay) { trigger, last ->
                    trigger.plusMillis(random.nextLong(Duration.between(trigger, last).toMillis() + 1))
                }
            assertFalse(run.delivered.isAfter(target + minutes(maxDelay.toLong())))
            assertTrue(run.stages <= 6)
        }
    }
}
