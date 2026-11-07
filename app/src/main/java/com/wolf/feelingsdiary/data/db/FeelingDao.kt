package com.wolf.feelingsdiary.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FeelingDao {
    @Query("SELECT * FROM feelings ORDER BY id")
    fun observeAll(): Flow<List<FeelingEntity>>

    @Query("SELECT * FROM feelings ORDER BY id")
    suspend fun getAll(): List<FeelingEntity>

    @Query("SELECT * FROM feelings WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<FeelingEntity>

    @Insert
    suspend fun insert(f: FeelingEntity): Long

    @Insert
    suspend fun insertAll(fs: List<FeelingEntity>)

    @Query("SELECT COUNT(*) FROM feelings")
    suspend fun count(): Int

    @Query("UPDATE feelings SET lastUsedAt = :at WHERE id IN (:ids)")
    suspend fun touch(
        ids: List<Long>,
        at: Long,
    )
}
