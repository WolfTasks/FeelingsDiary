package com.wolf.feelingsdiary.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.wolf.feelingsdiary.data.db.AppDatabase
import com.wolf.feelingsdiary.domain.EntryTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
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
            val oldValence =
                db
                    .feelingDao()
                    .getByIds(listOf(stressed))
                    .single()
                    .valence
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
            assertEquals(
                2,
                repo
                    .observeFeelings()
                    .first()
                    .first { it.id == stressed }
                    .valence,
            )
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

    private fun repoAt(
        instant: String,
        zone: String,
    ) = DiaryRepository(db.feelingDao(), db.entryDao(), Clock.fixed(Instant.parse(instant), ZoneId.of(zone)))

    @Test
    fun rangeUsesEntryLocalDate() =
        runBlocking {
            val happy = idOf("happy")
            val r = repoAt("2026-07-01T22:30:00Z", "Europe/Berlin")
            r.addEntry(0, listOf(happy))
            assertEquals(1, r.observeEntriesBetween(LocalDate.of(2026, 7, 2), LocalDate.of(2026, 7, 2)).first().size)
            assertEquals(0, r.observeEntriesBetween(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 1)).first().size)
        }

    @Test
    fun rangeKeepsExtremeOffsets() =
        runBlocking {
            val happy = idOf("happy")
            // local 2026-07-02 23:50 at -12 (= 07-03 11:50Z) and 00:10 at +14 (= 07-01 10:10Z)
            val late = repoAt("2026-07-03T11:50:00Z", "Etc/GMT+12")
            val early = repoAt("2026-07-01T10:10:00Z", "Pacific/Kiritimati")
            val lateId = late.addEntry(0, listOf(happy))
            val earlyId = early.addEntry(0, listOf(happy))
            val day = LocalDate.of(2026, 7, 2)
            assertEquals(listOf(earlyId, lateId), repo.observeEntriesBetween(day, day).first().map { it.id })
        }

    @Test
    fun updateKeepsTimeAndOldValences() =
        runBlocking {
            val stressed = idOf("stressed")
            val tired = idOf("tired")
            val happy = idOf("happy")
            val id = repo.addEntry(1, listOf(stressed, tired))
            val before = repo.getEntry(id)!!
            db.openHelper.writableDatabase.execSQL("UPDATE feelings SET valence = 1 WHERE id = $tired")
            val happyValence =
                db
                    .feelingDao()
                    .getByIds(listOf(happy))
                    .single()
                    .valence
            val tiredOld = before.valences[1]

            repo.updateEntry(id, 4, listOf(tired, happy))

            val after = repo.getEntry(id)!!
            assertEquals(before.time, after.time)
            assertEquals(4, after.people)
            assertEquals(listOf(tired, happy), after.feelingIds)
            assertEquals(listOf(tiredOld, happyValence), after.valences)
        }

    @Test
    fun updateRejectsUnknownEntryAndInvalidInput() =
        runBlocking {
            val happy = idOf("happy")
            val id = repo.addEntry(1, listOf(happy))
            assertThrows(IllegalArgumentException::class.java) { runBlocking { repo.updateEntry(id + 99, 1, listOf(happy)) } }
            assertThrows(IllegalArgumentException::class.java) { runBlocking { repo.updateEntry(id, 1, emptyList()) } }
            assertThrows(IllegalArgumentException::class.java) { runBlocking { repo.updateEntry(id, 1000, listOf(happy)) } }
            assertEquals(1, repo.getEntry(id)!!.people)
        }

    @Test
    fun deleteEntryRemovesIt() =
        runBlocking {
            val id = repo.addEntry(0, listOf(idOf("happy")))
            repo.deleteEntry(id)
            assertNull(repo.getEntry(id))
        }

    @Test
    fun updateFeelingKeepsStoredEntryValences() =
        runBlocking {
            val stressed = idOf("stressed")
            val id = repo.addEntry(2, listOf(stressed))
            val stored = repo.getEntry(id)!!.valences.single()

            repo.updateFeeling(stressed, null, 0xFF123456.toInt(), 2)

            assertEquals(listOf(stored), repo.getEntry(id)!!.valences)
            val feeling = repo.observeFeelings().first().first { it.id == stressed }
            assertEquals(0xFF123456.toInt(), feeling.colorArgb)
            assertEquals(2, feeling.valence)
            assertEquals("stressed", feeling.baseKey)
            // lastUsedAt from the entry is not clobbered by the edit.
            assertEquals(clock.millis(), feeling.lastUsedAt)
        }

    @Test
    fun baseFeelingNameCannotChange() =
        runBlocking {
            val happy = idOf("happy")
            assertThrows(IllegalArgumentException::class.java) { runBlocking { repo.updateFeeling(happy, "Joy", 0, 1) } }
            val feeling = repo.observeFeelings().first().first { it.id == happy }
            assertEquals("happy", feeling.baseKey)
            assertNull(feeling.customName)
            assertEquals(2, feeling.valence)
        }

    @Test
    fun customFeelingCanBeRenamedAndValenceIsBounded() =
        runBlocking {
            val id = repo.addCustomFeeling("Wehmut", 1, -1)
            repo.updateFeeling(id, "Sehnsucht", 2, 1)
            val feeling = repo.observeFeelings().first().first { it.id == id }
            assertEquals("Sehnsucht", feeling.customName)
            assertEquals(2, feeling.colorArgb)
            assertEquals(1, feeling.valence)
            assertThrows(IllegalArgumentException::class.java) { runBlocking { repo.updateFeeling(id, null, 2, 3) } }
            assertThrows(IllegalArgumentException::class.java) { runBlocking { repo.updateFeeling(id, " ", 2, 0) } }
            assertThrows(IllegalArgumentException::class.java) { runBlocking { repo.updateFeeling(id + 99, null, 2, 0) } }
            assertEquals(
                "Sehnsucht",
                repo
                    .observeFeelings()
                    .first()
                    .first { it.id == id }
                    .customName,
            )
        }

    @Test
    fun setHiddenTogglesFlag() =
        runBlocking {
            val sad = idOf("sad")
            repo.setHidden(sad, true)
            assertEquals(
                true,
                repo
                    .observeFeelings()
                    .first()
                    .first { it.id == sad }
                    .hidden,
            )
            repo.setHidden(sad, false)
            assertEquals(
                false,
                repo
                    .observeFeelings()
                    .first()
                    .first { it.id == sad }
                    .hidden,
            )
        }
}
