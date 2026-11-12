package com.wolf.feelingsdiary.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

enum class Aggregation { NONE, DAILY, WEEKLY, MONTHLY }

/** [x] is the horizontal position within the period (0..1); [entryId] is set only without aggregation. */
data class GraphPoint(
    val x: Float,
    val valence: Float,
    val mainFeelingId: Long,
    val otherFeelingIds: List<Long>,
    val people: Float,
    val entryId: Long?,
    val bucketStart: LocalDate,
)

data class GraphData(
    val points: List<GraphPoint>,
    val aggregation: Aggregation,
    val peopleScaleMax: Int,
    val feelingIds: Set<Long>,
)

object GraphBuilder {
    private val PEOPLE_SCALES = listOf(5, 10, 20, 50, 100, 200, 500, 1000)
    private const val MAX_OTHERS = 3
    private const val MINUTES_PER_DAY = 1440f
    private const val MAX_WEEKLY_DAYS = 2557 // seven years

    /**
     * Height of one entry: the main (first) feeling counts fully, each further feeling half as much
     * as the one before (weights 1, 1/2, 1/4, ...).
     */
    fun weightedValence(valences: List<Int>): Float {
        var weight = 1.0
        var sum = 0.0
        var weights = 0.0
        for (v in valences) {
            sum += v * weight
            weights += weight
            weight /= 2
        }
        return (sum / weights).toFloat()
    }

    fun build(
        entries: List<Entry>,
        period: GraphPeriod,
        firstDayOfWeek: DayOfWeek,
    ): GraphData {
        val inPeriod =
            entries
                .map { it to it.time.localDateTime() }
                .filter { (_, local) -> local.toLocalDate() in period.start..period.endInclusive }
                .sortedWith(compareBy({ it.first.time.epochMinute }, { it.first.id }))
        val aggregation = aggregationFor(period)
        val points =
            if (aggregation == Aggregation.NONE) {
                unaggregatedPoints(inPeriod, period)
            } else {
                inPeriod
                    .groupBy { (_, local) -> bucketStart(local.toLocalDate(), aggregation, firstDayOfWeek) }
                    .map { (bucket, members) ->
                        aggregate(bucket, members.map { it.first }, aggregation, period)
                    }.sortedBy { it.x }
            }
        val maxPeople = inPeriod.maxOfOrNull { it.first.people } ?: 0
        return GraphData(
            points = points,
            aggregation = aggregation,
            peopleScaleMax = PEOPLE_SCALES.firstOrNull { it >= maxPeople } ?: PEOPLE_SCALES.last(),
            feelingIds = inPeriod.flatMap { it.first.feelingIds }.toSet(),
        )
    }

    /**
     * Positions by real elapsed time (epoch minutes), so DST changes and zone changes never reorder entries.
     * [inPeriod] must be sorted by (epochMinute, id).
     */
    private fun unaggregatedPoints(
        inPeriod: List<Pair<Entry, LocalDateTime>>,
        period: GraphPeriod,
    ): List<GraphPoint> {
        if (inPeriod.isEmpty()) return emptyList()
        val periodMinutes = period.days * MINUTES_PER_DAY
        val startEpoch =
            midnightEpochMinute(
                period.start,
                inPeriod
                    .first()
                    .first.time.utcOffsetMinutes,
            )
        val endEpoch =
            midnightEpochMinute(
                period.endInclusive.plusDays(1),
                inPeriod
                    .last()
                    .first.time.utcOffsetMinutes,
            )
        val realSpan = endEpoch - startEpoch
        val useRealTime = realSpan >= periodMinutes / 2
        var previousX = 0f
        return inPeriod.map { (entry, local) ->
            val raw =
                if (useRealTime) {
                    (entry.time.epochMinute - startEpoch) / realSpan.toFloat()
                } else {
                    ChronoUnit.MINUTES.between(period.start.atStartOfDay(), local) / periodMinutes
                }
            // Clamp to the period and keep the sequence non-decreasing in (epochMinute, id) order.
            val x = maxOf(raw.coerceIn(0f, 1f), previousX)
            previousX = x
            GraphPoint(
                x = x,
                valence = weightedValence(entry.valences),
                mainFeelingId = entry.feelingIds.first(),
                otherFeelingIds = entry.feelingIds.drop(1),
                people = entry.people.toFloat(),
                entryId = entry.id,
                bucketStart = local.toLocalDate(),
            )
        }
    }

    private fun midnightEpochMinute(
        date: LocalDate,
        offsetMinutes: Int,
    ): Long = date.atStartOfDay().toEpochSecond(ZoneOffset.UTC) / 60 - offsetMinutes

    private fun aggregationFor(period: GraphPeriod): Aggregation =
        when (period.kind) {
            PeriodKind.DAY, PeriodKind.WEEK, PeriodKind.MONTH -> {
                Aggregation.NONE
            }

            PeriodKind.YEAR -> {
                Aggregation.DAILY
            }

            PeriodKind.RANGE -> {
                when {
                    period.days <= 31 -> Aggregation.NONE
                    period.days <= 366 -> Aggregation.DAILY
                    period.days <= MAX_WEEKLY_DAYS -> Aggregation.WEEKLY
                    else -> Aggregation.MONTHLY
                }
            }
        }

    private fun bucketStart(
        date: LocalDate,
        aggregation: Aggregation,
        firstDayOfWeek: DayOfWeek,
    ): LocalDate =
        when (aggregation) {
            Aggregation.WEEKLY -> date.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
            Aggregation.MONTHLY -> date.withDayOfMonth(1)
            else -> date
        }

    private fun aggregate(
        bucket: LocalDate,
        members: List<Entry>,
        aggregation: Aggregation,
        period: GraphPeriod,
    ): GraphPoint {
        // members are in chronological order, so lastIndexOf-style ties favour the later entry.
        val mainCounts = members.groupingBy { it.feelingIds.first() }.eachCount()
        val top = mainCounts.values.max()
        val main = members.last { mainCounts.getValue(it.feelingIds.first()) == top }.feelingIds.first()
        val others =
            members
                .flatMap { it.feelingIds }
                .filter { it != main }
                .groupingBy { it }
                .eachCount()
                .entries
                .sortedByDescending { it.value }
                .take(MAX_OTHERS)
                .map { it.key }
        // Weekly and monthly buckets may stick out of the period; centre them on the visible part.
        val bucketEnd =
            when (aggregation) {
                Aggregation.WEEKLY -> bucket.plusDays(6)
                Aggregation.MONTHLY -> bucket.with(TemporalAdjusters.lastDayOfMonth())
                else -> bucket
            }
        val from = maxOf(bucket, period.start)
        val to = minOf(bucketEnd, period.endInclusive)
        val startMinute = ChronoUnit.DAYS.between(period.start, from) * MINUTES_PER_DAY
        val endMinute = (ChronoUnit.DAYS.between(period.start, to) + 1) * MINUTES_PER_DAY
        return GraphPoint(
            x = ((startMinute + endMinute) / 2f) / (period.days * MINUTES_PER_DAY),
            valence = members.map { weightedValence(it.valences) }.average().toFloat(),
            mainFeelingId = main,
            otherFeelingIds = others,
            people = members.map { it.people }.average().toFloat(),
            entryId = null,
            bucketStart = bucket,
        )
    }
}
