package com.wolf.feelingsdiary.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.wolf.feelingsdiary.data.db.AppDatabase
import com.wolf.feelingsdiary.domain.EntryTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class DiaryRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: DiaryRepository
    private val clock = Clock.fixed(Instant.parse("2026-07-01T21:30:00Z"), ZoneId.of("Europe/Berlin"))

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
                .allowMainThreadQueries()
                .addCallback(AppDatabase.seedCallback())
                .build()
        repo = DiaryRepository(db.feelingDao(), db.entryDao(), clock)
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
    fun addEntryStoresValenceSnapshotAndTouchesFeelings() =
        runBlocking {
            val stressed = idOf("stressed")
            val tired = idOf("tired")
            val oldValence = db.feelingDao().getByIds(listOf(stressed)).single().valence
            val entryId = repo.addEntry(3, listOf(stressed, tired))

            // change valence afterwards: the entry must keep the old snapshot
            db.openHelper.writableDatabase.execSQL("UPDATE feelings SET valence = 2 WHERE id = $stressed")

            val entry = repo.observeRecentEntries().first().single()
            assertEquals(entryId, entry.id)
            assertEquals(3, entry.people)
            assertEquals(listOf(stressed, tired), entry.feelingIds)
            assertEquals(oldValence, entry.valences[0])
            assertEquals(EntryTime.now(clock), entry.time)
            val feelings = db.feelingDao().getByIds(listOf(stressed, tired))
            assertEquals(listOf(clock.millis(), clock.millis()), feelings.map { it.lastUsedAt })
            assertEquals(2, repo.observeFeelings().first().first { it.id == stressed }.valence)
        }

    @Test
    fun addEntryRejectsEmptyDuplicateOrOutOfRangePeople() =
        runBlocking {
            val happy = idOf("happy")
            assertThrows(IllegalArgumentException::class.java) { runBlocking { repo.addEntry(0, emptyList()) } }
            assertThrows(IllegalArgumentException::class.java) { runBlocking { repo.addEntry(0, listOf(happy, happy)) } }
            assertThrows(IllegalArgumentException::class.java) { runBlocking { repo.addEntry(1000, listOf(happy)) } }
            assertThrows(IllegalArgumentException::class.java) { runBlocking { repo.addEntry(-1, listOf(happy)) } }
            assertEquals(0, repo.observeRecentEntries().first().size)
        }

    @Test
    fun addCustomFeelingIsObservable() =
        runBlocking {
            val id = repo.addCustomFeeling("Wehmut", 0xFF123456.toInt(), -1)
            val f = repo.observeFeelings().first().first { it.id == id }
            assertEquals("Wehmut", f.customName)
            assertEquals(null, f.baseKey)
            assertEquals(-1, f.valence)
        }
}
