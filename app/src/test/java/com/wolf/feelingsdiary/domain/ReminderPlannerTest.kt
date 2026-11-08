package com.wolf.feelingsdiary.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

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
}
