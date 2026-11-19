package com.wolf.feelingsdiary.domain

import java.util.Locale

private const val MINUTES_PER_HOUR = 60

/** "UTC+1", "UTC-9:30": hours without leading zero, minutes only when non-zero; always ASCII digits. */
fun formatUtcOffset(minutes: Int): String {
    val abs = Math.abs(minutes)
    val sign = if (minutes < 0) "-" else "+"
    val rest = abs % MINUTES_PER_HOUR
    return "UTC$sign${abs / MINUTES_PER_HOUR}" + if (rest != 0) ":%02d".format(Locale.ROOT, rest) else ""
}
