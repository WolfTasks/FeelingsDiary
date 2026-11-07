package com.wolf.feelingsdiary.domain

/** [feelingIds] and [valences] are sorted by position; index 0 is the main feeling. */
data class Entry(
    val id: Long,
    val time: EntryTime,
    val people: Int,
    val feelingIds: List<Long>,
    val valences: List<Int>,
)
