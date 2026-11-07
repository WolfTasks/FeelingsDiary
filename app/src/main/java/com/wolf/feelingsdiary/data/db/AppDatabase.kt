package com.wolf.feelingsdiary.data.db

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [FeelingEntity::class, EntryEntity::class, EntryFeelingEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun feelingDao(): FeelingDao

    abstract fun entryDao(): EntryDao

    companion object {
        /** Seeds the base feeling list in [BaseFeelings.all] order on first creation. */
        fun seedCallback(): Callback =
            object : Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    BaseFeelings.all.forEach { f ->
                        val values =
                            ContentValues().apply {
                                put("baseKey", f.key)
                                putNull("customName")
                                put("colorArgb", f.colorArgb)
                                put("valence", f.valence)
                                put("hidden", 0)
                                putNull("lastUsedAt")
                            }
                        db.insert("feelings", SQLiteDatabase.CONFLICT_NONE, values)
                    }
                }
            }
    }
}
