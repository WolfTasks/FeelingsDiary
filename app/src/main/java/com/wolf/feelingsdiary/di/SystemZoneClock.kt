package com.wolf.feelingsdiary.di

import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * System clock whose zone follows the device on every call. [Clock.systemDefaultZone] captures the
 * zone once, so long-lived singletons would keep planning reminders and stamping entries in the old
 * zone after a time-zone change. [instants] is only replaced in tests.
 */
class SystemZoneClock(
    private val instants: () -> Instant = Instant::now,
) : Clock() {
    override fun getZone(): ZoneId = ZoneId.systemDefault()

    override fun withZone(zone: ZoneId): Clock = Clock.system(zone)

    override fun instant(): Instant = instants()
}
