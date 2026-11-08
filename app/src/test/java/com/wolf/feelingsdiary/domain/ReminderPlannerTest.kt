package com.wolf.feelingsdiary.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
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

    @Test
    fun dstDaysKeepWallClock() {
        val s = ReminderSettings(count = 3)
        for (date in listOf(LocalDate.of(2026, 3, 29), LocalDate.of(2026, 10, 25))) {
            val after = ZonedDateTime.of(date, t(0), berlin)
            val result = ReminderPlanner.next(s, after, 9L)
            assertNotNull(result)
            assertEquals(ReminderPlanner.slotsFor(date, s, 9L).first(), result!!.toLocalTime())
            assertTrue(result.isAfter(after))
        }
    }

    @Test
    fun allWindowsInQuietGivesNull() {
        val s = ReminderSettings(quietStart = t(0, 1), quietEnd = t(0, 0))
        assertNull(ReminderPlanner.next(s, ZonedDateTime.of(day, t(8), berlin), 1L))
    }
}
