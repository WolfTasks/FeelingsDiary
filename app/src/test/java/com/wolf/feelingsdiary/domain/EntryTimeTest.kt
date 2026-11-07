package com.wolf.feelingsdiary.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class EntryTimeTest {
    private val instant = Instant.parse("2026-07-01T21:30:00Z")

    @Test
    fun keepsOriginalLocalTimeAcrossZoneChange() {
        val time = EntryTime.now(Clock.fixed(instant, ZoneId.of("Europe/Berlin")))
        val expected = LocalDateTime.of(2026, 7, 1, 23, 30)
        assertEquals(expected, time.localDateTime())
        val originalDefault = java.util.TimeZone.getDefault()
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"))
            assertEquals(expected, time.localDateTime())
        } finally {
            java.util.TimeZone.setDefault(originalDefault)
        }
    }

    @Test
    fun epochMinuteRoundTrip() {
        val time = EntryTime.now(Clock.fixed(instant, ZoneId.of("UTC")))
        assertEquals(29_715_690, time.epochMinute)
        assertEquals(instant.epochSecond / 60, time.epochMinute.toLong())
        assertEquals(0, time.utcOffsetMinutes)
    }
}
