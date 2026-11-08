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

    @Transaction
    @Query("SELECT * FROM entries WHERE epochMinute BETWEEN :fromMinute AND :toMinute ORDER BY epochMinute, id")
    fun observeBetween(
        fromMinute: Int,
        toMinute: Int,
    ): Flow<List<EntryWithFeelings>>

    @Transaction
    @Query("SELECT * FROM entries WHERE id = :id")
    suspend fun getById(id: Long): EntryWithFeelings?

    @Query("SELECT people FROM entries ORDER BY epochMinute DESC, id DESC LIMIT 1")
    suspend fun latestPeople(): Int?

    @Query(
        "SELECT utcOffsetMinutes FROM entries WHERE epochMinute < :epochMinute OR (epochMinute = :epochMinute AND id < :id) " +
            "ORDER BY epochMinute DESC, id DESC LIMIT 1",
    )
    suspend fun previousOffsetMinutes(
        epochMinute: Int,
        id: Long,
    ): Int?

    @Query("UPDATE entries SET people = :people WHERE id = :id")
    suspend fun updatePeople(
        id: Long,
        people: Int,
    ): Int

    @Query("DELETE FROM entry_feelings WHERE entryId = :id")
    suspend fun deleteFeelingsOf(id: Long)

    @Transaction
    suspend fun replace(
        id: Long,
        people: Int,
        feelings: List<EntryFeelingEntity>,
    ) {
        updatePeople(id, people)
        deleteFeelingsOf(id)
        insertEntryFeelings(feelings)
    }

    @Query("DELETE FROM entries WHERE id = :id")
    suspend fun delete(id: Long)
}
