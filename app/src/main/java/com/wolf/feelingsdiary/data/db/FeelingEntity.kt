package com.wolf.feelingsdiary.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Exactly one of [baseKey] (built-in feeling) and [customName] (user-defined) is set. */
@Entity(tableName = "feelings", indices = [Index(value = ["baseKey"], unique = true)])
data class FeelingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val baseKey: String?,
    val customName: String?,
    val colorArgb: Int,
    val valence: Int,
    val hidden: Boolean = false,
    val lastUsedAt: Long? = null,
)
