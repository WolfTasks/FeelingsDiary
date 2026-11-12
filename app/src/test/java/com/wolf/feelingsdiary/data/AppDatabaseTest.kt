package com.wolf.feelingsdiary.data

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.wolf.feelingsdiary.data.db.AppDatabase
import com.wolf.feelingsdiary.data.db.BaseFeelings
import com.wolf.feelingsdiary.data.db.EntryEntity
import com.wolf.feelingsdiary.data.db.EntryFeelingEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppDatabaseTest {
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
                .allowMainThreadQueries()
                .addCallback(AppDatabase.seedCallback())
                .build()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun idOf(key: String) =
        db
            .feelingDao()
            .getAll()
            .first { it.baseKey == key }
            .id

    @Test
    fun seedCreatesAllBaseFeelings() =
        runBlocking {
            assertEquals(24, db.feelingDao().count())
            val keys = db.feelingDao().getAll().map { it.baseKey }
            assertEquals(BaseFeelings.all.map { it.key }, keys)
            assertEquals(
                listOf(
                    "happy",
                    "grateful",
                    "in_love",
                    "relaxed",
                    "neutral",
                    "tired",
                    "bored",
                    "stressed",
                    "anxious",
                    "lonely",
                    "sad",
                    "angry",
                    "proud",
                    "excited",
                    "content",
                    "motivated",
                    "hopeful",
                    "curious",
                    "thoughtful",
                    "disappointed",
                    "frustrated",
                    "annoyed",
                    "overwhelmed",
                    "exhausted",
                ),
                keys,
            )
        }

    @Test
    fun insertWithFeelingsKeepsOrderAndValenceSnapshot() =
        runBlocking {
            val stressed = idOf("stressed")
            val tired = idOf("tired")
            db.entryDao().insertWithFeelings(
                EntryEntity(epochMinute = 100, utcOffsetMinutes = 60, people = 2),
                listOf(
                    EntryFeelingEntity(0, 0, stressed, -1),
                    EntryFeelingEntity(0, 1, tired, -1),
                ),
            )
            val result = db.entryDao().observeRecent(10).first()[0]
            assertEquals(
                listOf(stressed to -1, tired to -1),
                result.feelings.sortedBy { it.position }.map { it.feelingId to it.valence },
            )
        }

    @Test
    fun observeRecentReturnsNewestFirst() =
        runBlocking {
            val happy = idOf("happy")
            for (m in listOf(10, 30, 20)) {
                db.entryDao().insertWithFeelings(
                    EntryEntity(epochMinute = m, utcOffsetMinutes = 0, people = 0),
                    listOf(EntryFeelingEntity(0, 0, happy, 2)),
                )
            }
            assertEquals(
                listOf(30, 20, 10),
                db
                    .entryDao()
                    .observeRecent(10)
                    .first()
                    .map { it.entry.epochMinute },
            )
        }

    @Test
    fun deletingEntryCascadesFeelings() =
        runBlocking {
            val happy = idOf("happy")
            val id =
                db.entryDao().insertWithFeelings(
                    EntryEntity(epochMinute = 1, utcOffsetMinutes = 0, people = 0),
                    listOf(EntryFeelingEntity(0, 0, happy, 2)),
                )
            db.entryDao().delete(id)
            assertTrue(
                db
                    .entryDao()
                    .observeRecent(10)
                    .first()
                    .isEmpty(),
            )
            val c = db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM entry_feelings")
            c.moveToFirst()
            assertEquals(0, c.getInt(0))
            c.close()
        }

    @Test(expected = SQLiteConstraintException::class)
    fun deletingUsedFeelingIsRejected() {
        runBlocking {
            val happy = db.feelingDao().getAll().first { it.baseKey == "happy" }
            db.entryDao().insertWithFeelings(
                EntryEntity(epochMinute = 1, utcOffsetMinutes = 0, people = 0),
                listOf(EntryFeelingEntity(0, 0, happy.id, 2)),
            )
            db.openHelper.writableDatabase.execSQL("DELETE FROM feelings WHERE id = ${happy.id}")
        }
    }
}
