package com.wolf.feelingsdiary.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Relation

@Entity(
    tableName = "entry_feelings",
    primaryKeys = ["entryId", "position"],
    foreignKeys = [
        ForeignKey(
            entity = EntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["entryId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = FeelingEntity::class,
            parentColumns = ["id"],
            childColumns = ["feelingId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("feelingId")],
)
data class EntryFeelingEntity(
    val entryId: Long,
    val position: Int,
    val feelingId: Long,
    /** Valence snapshot at entry time. */
    val valence: Int,
)

data class EntryWithFeelings(
    @Embedded val entry: EntryEntity,
    @Relation(parentColumn = "id", entityColumn = "entryId") val feelings: List<EntryFeelingEntity>,
)
