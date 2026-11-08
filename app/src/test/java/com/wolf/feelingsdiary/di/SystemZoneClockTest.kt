package com.wolf.feelingsdiary.di

import com.wolf.feelingsdiary.domain.EntryTime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.util.TimeZone

class SystemZoneClockTest {
    private val original = TimeZone.getDefault()

    @After
    fun restoreZone() = TimeZone.setDefault(original)

    @Test
    fun zoneFollowsSystemDefaultAfterCreation() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"))
        val clock = SystemZoneClock()
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
        assertEquals(ZoneId.of("America/New_York"), clock.zone)
    }

    @Test
    fun providedClockTracksZoneChanges() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"))
        val clock = RepositoryModule.provideClock()
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
        assertEquals(ZoneId.of("Asia/Tokyo"), clock.zone)
    }

    @Test
    fun newEntriesUseTheCurrentOffset() {
        val instant = Instant.parse("2026-11-08T06:00:00Z")
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"))
        val clock = SystemZoneClock { instant }
        assertEquals(60, EntryTime.now(clock).utcOffsetMinutes)
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
        assertEquals(-300, EntryTime.now(clock).utcOffsetMinutes)
    }

    @Test
    fun instantComesFromTheSource() {
        val instant = Instant.parse("2026-11-08T06:00:00Z")
        assertEquals(instant, SystemZoneClock { instant }.instant())
        assertEquals(instant.toEpochMilli(), SystemZoneClock { instant }.millis())
    }
}
