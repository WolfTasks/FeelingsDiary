package com.wolf.feelingsdiary.domain

import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Time-zone-stable timestamp: UTC minute plus the UTC offset at entry time, so the
 * originally experienced local time survives later zone changes.
 */
data class EntryTime(
    val epochMinute: Int,
    val utcOffsetMinutes: Int,
) {
    /** Local date-time using the stored offset, never the current zone. */
    fun localDateTime(): LocalDateTime =
        LocalDateTime.ofEpochSecond(epochMinute * 60L, 0, ZoneOffset.ofTotalSeconds(utcOffsetMinutes * 60))

    companion object {
        fun now(clock: Clock): EntryTime {
            val instant = clock.instant()
            val offsetSeconds = clock.zone.rules.getOffset(instant).totalSeconds
            return EntryTime(Math.floorDiv(instant.epochSecond, 60L).toInt(), offsetSeconds / 60)
        }
    }
}
