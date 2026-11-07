package com.wolf.feelingsdiary.data

import com.wolf.feelingsdiary.data.db.EntryDao
import com.wolf.feelingsdiary.data.db.EntryEntity
import com.wolf.feelingsdiary.data.db.EntryFeelingEntity
import com.wolf.feelingsdiary.data.db.EntryWithFeelings
import com.wolf.feelingsdiary.data.db.FeelingDao
import com.wolf.feelingsdiary.data.db.FeelingEntity
import com.wolf.feelingsdiary.domain.Entry
import com.wolf.feelingsdiary.domain.EntryTime
import com.wolf.feelingsdiary.domain.Feeling
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import javax.inject.Inject

class DiaryRepository
    @Inject
    constructor(
        private val feelingDao: FeelingDao,
        private val entryDao: EntryDao,
        private val clock: Clock,
    ) {
        fun observeFeelings(): Flow<List<Feeling>> = feelingDao.observeAll().map { list -> list.map { it.toDomain() } }

        fun observeRecentEntries(limit: Int = 50): Flow<List<Entry>> =
            entryDao.observeRecent(limit).map { list -> list.map { it.toDomain() } }

        /** Stores an entry; feeling order is preserved (first = main feeling). Returns the entry id. */
        suspend fun addEntry(
            people: Int,
            feelingIds: List<Long>,
        ): Long {
            require(people in 0..999) { "people must be in 0..999" }
            require(feelingIds.isNotEmpty()) { "at least one feeling required" }
            require(feelingIds.toSet().size == feelingIds.size) { "duplicate feelings" }
            val valenceById = feelingDao.getByIds(feelingIds).associate { it.id to it.valence }
            require(valenceById.keys.containsAll(feelingIds)) { "unknown feeling id" }

            val time = EntryTime.now(clock)
            val entryId =
                entryDao.insertWithFeelings(
                    EntryEntity(epochMinute = time.epochMinute, utcOffsetMinutes = time.utcOffsetMinutes, people = people),
                    feelingIds.mapIndexed { index, id -> EntryFeelingEntity(0, index, id, valenceById.getValue(id)) },
                )
            feelingDao.touch(feelingIds, clock.millis())
            return entryId
        }

        suspend fun addCustomFeeling(
            name: String,
            colorArgb: Int,
            valence: Int,
        ): Long =
            feelingDao.insert(
                FeelingEntity(baseKey = null, customName = name, colorArgb = colorArgb, valence = valence),
            )

        private fun FeelingEntity.toDomain() = Feeling(id, baseKey, customName, colorArgb, valence, hidden, lastUsedAt)

        private fun EntryWithFeelings.toDomain(): Entry {
            val sorted = feelings.sortedBy { it.position }
            return Entry(
                id = entry.id,
                time = EntryTime(entry.epochMinute, entry.utcOffsetMinutes),
                people = entry.people,
                feelingIds = sorted.map { it.feelingId },
                valences = sorted.map { it.valence },
            )
        }
    }
