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
import java.time.LocalDate
import java.time.ZoneOffset
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

        /** Entries whose stored local date lies in [from, toInclusive], ascending by time. */
        fun observeEntriesBetween(
            from: LocalDate,
            toInclusive: LocalDate,
        ): Flow<List<Entry>> {
            // Offsets range from -12 h to +14 h, so widen the UTC window and filter by stored local date.
            val fromMinute = (from.atStartOfDay(ZoneOffset.UTC).toEpochSecond() / 60 - 14 * 60).toInt()
            val toMinute = (toInclusive.plusDays(1).atStartOfDay(ZoneOffset.UTC).toEpochSecond() / 60 + 12 * 60).toInt()
            return entryDao.observeBetween(fromMinute, toMinute).map { list ->
                list.map { it.toDomain() }.filter { it.time.localDateTime().toLocalDate() in from..toInclusive }
            }
        }

        suspend fun getEntry(id: Long): Entry? = entryDao.getById(id)?.toDomain()

        /** Replaces people and feelings; time and stored valences of already-present feelings are kept. */
        suspend fun updateEntry(
            id: Long,
            people: Int,
            feelingIds: List<Long>,
        ) {
            require(people in 0..999) { "people must be in 0..999" }
            require(feelingIds.isNotEmpty()) { "at least one feeling required" }
            require(feelingIds.toSet().size == feelingIds.size) { "duplicate feelings" }
            val existing = requireNotNull(entryDao.getById(id)) { "unknown entry id" }
            val valenceById = feelingDao.getByIds(feelingIds).associate { it.id to it.valence }
            require(valenceById.keys.containsAll(feelingIds)) { "unknown feeling id" }

            val oldValences = existing.feelings.associate { it.feelingId to it.valence }
            entryDao.replace(
                id,
                people,
                feelingIds.mapIndexed { index, fid ->
                    EntryFeelingEntity(id, index, fid, oldValences[fid] ?: valenceById.getValue(fid))
                },
            )
            val added = feelingIds.filter { it !in oldValences }
            if (added.isNotEmpty()) feelingDao.touch(added, clock.millis())
        }

        suspend fun deleteEntry(id: Long) = entryDao.delete(id)

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
