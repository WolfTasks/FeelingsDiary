package com.wolf.feelingsdiary.domain

import java.time.LocalTime

enum class ThemeMode { SYSTEM, DARK, LIGHT }

enum class InputMode { SCREEN, QUICK_SHEET }

enum class ReminderMode { RANDOM, FIXED }

data class TimeWindow(
    val start: LocalTime,
    val end: LocalTime,
) {
    init {
        require(start < end) { "Window start must be before end" }
    }
}

data class ReminderSettings(
    val enabled: Boolean = true,
    val count: Int = 3,
    val mode: ReminderMode = ReminderMode.RANDOM,
    val windows: List<TimeWindow> = DEFAULT_WINDOWS,
    val fixedTimes: List<LocalTime> = DEFAULT_TIMES,
    val quietStart: LocalTime = LocalTime.of(22, 0),
    val quietEnd: LocalTime = LocalTime.of(8, 0),
    val alarmSound: Boolean = false,
    /** How much later than planned a reminder may arrive at most; one of [MAX_DELAY_OPTIONS]. */
    val maxDelayMinutes: Int = 15,
) {
    init {
        require(count in 1..4) { "count must be 1..4" }
        require(windows.size == 4 && fixedTimes.size == 4) { "exactly 4 windows and 4 fixed times" }
        require(maxDelayMinutes in MAX_DELAY_OPTIONS) { "maxDelayMinutes must be one of $MAX_DELAY_OPTIONS" }
    }

    companion object {
        val DEFAULT_WINDOWS: List<TimeWindow> =
            listOf(
                TimeWindow(LocalTime.of(9, 0), LocalTime.of(11, 0)),
                TimeWindow(LocalTime.of(12, 0), LocalTime.of(14, 0)),
                TimeWindow(LocalTime.of(15, 0), LocalTime.of(17, 0)),
                TimeWindow(LocalTime.of(19, 0), LocalTime.of(21, 0)),
            )
        val DEFAULT_TIMES: List<LocalTime> =
            listOf(LocalTime.of(10, 0), LocalTime.of(13, 0), LocalTime.of(16, 0), LocalTime.of(20, 0))

        /** The selectable maximum delays of a reminder, in minutes. */
        val MAX_DELAY_OPTIONS: List<Int> = listOf(5, 15, 60)
    }
}

data class AppSettings(
    val reminders: ReminderSettings = ReminderSettings(),
    val theme: ThemeMode = ThemeMode.DARK,
    val input: InputMode = InputMode.SCREEN,
    val backupEnabled: Boolean = false,
    val onboardingDone: Boolean = false,
)
