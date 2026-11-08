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

    /** [customName] null keeps the stored name; lastUsedAt and hidden stay untouched. */
    @Query(
        "UPDATE feelings SET customName = COALESCE(:customName, customName), colorArgb = :colorArgb, valence = :valence WHERE id = :id",
    )
    suspend fun update(
        id: Long,
        customName: String?,
        colorArgb: Int,
        valence: Int,
    )

    @Query("UPDATE feelings SET hidden = :hidden WHERE id = :id")
    suspend fun setHidden(
        id: Long,
        hidden: Boolean,
    )

    @Query("UPDATE feelings SET lastUsedAt = :at WHERE id IN (:ids)")
    suspend fun touch(
        ids: List<Long>,
        at: Long,
    )
}
