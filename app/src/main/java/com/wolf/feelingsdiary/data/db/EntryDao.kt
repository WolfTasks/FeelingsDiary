package com.wolf.feelingsdiary.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface EntryDao {
    @Insert
    suspend fun insertEntry(entry: EntryEntity): Long

    @Insert
    suspend fun insertEntryFeelings(feelings: List<EntryFeelingEntity>)

    @Transaction
    suspend fun insertWithFeelings(
        entry: EntryEntity,
        feelings: List<EntryFeelingEntity>,
    ): Long {
        val id = insertEntry(entry)
        insertEntryFeelings(feelings.map { it.copy(entryId = id) })
        return id
    }

    @Transaction
    @Query("SELECT * FROM entries ORDER BY epochMinute DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<EntryWithFeelings>>

    @Query("DELETE FROM entries WHERE id = :id")
    suspend fun delete(id: Long)
}
