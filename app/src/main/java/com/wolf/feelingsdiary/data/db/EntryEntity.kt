package com.wolf.feelingsdiary.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "entries", indices = [Index("epochMinute")])
data class EntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochMinute: Int,
    val utcOffsetMinutes: Int,
    val people: Int,
)
