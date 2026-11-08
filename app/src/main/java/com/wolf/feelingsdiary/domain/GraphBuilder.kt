package com.wolf.feelingsdiary.domain

import java.time.DayOfWeek
import java.time.LocalDate
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
        val periodMinutes = period.days * MINUTES_PER_DAY
        val points =
            if (aggregation == Aggregation.NONE) {
                inPeriod
                    .map { (entry, local) ->
                        val minutes = ChronoUnit.MINUTES.between(period.start.atStartOfDay(), local)
                        GraphPoint(
                            x = minutes / periodMinutes,
                            valence = entry.valences.average().toFloat(),
                            mainFeelingId = entry.feelingIds.first(),
                            otherFeelingIds = entry.feelingIds.drop(1),
                            people = entry.people.toFloat(),
                            entryId = entry.id,
                            bucketStart = local.toLocalDate(),
                        )
                        // inPeriod is ordered by (epochMinute, id) and sortedBy is stable: ties on x keep that order.
                    }.sortedBy { it.x }
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
            valence = members.map { it.valences.average() }.average().toFloat(),
            mainFeelingId = main,
            otherFeelingIds = others,
            people = members.map { it.people }.average().toFloat(),
            entryId = null,
            bucketStart = bucket,
        )
    }
}
