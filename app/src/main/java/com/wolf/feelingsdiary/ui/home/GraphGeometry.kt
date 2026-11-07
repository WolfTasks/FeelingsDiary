package com.wolf.feelingsdiary.ui.home

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.wolf.feelingsdiary.domain.GraphPoint

private const val MARGIN_LEFT_DP = 48f
private const val MARGIN_RIGHT_DP = 28f
private const val MARGIN_TOP_DP = 8f
private const val MARGIN_BOTTOM_DP = 20f
private const val MAX_VALENCE = 2f

fun plotRect(
    width: Float,
    height: Float,
    density: Float,
): Rect =
    Rect(
        left = MARGIN_LEFT_DP * density,
        top = MARGIN_TOP_DP * density,
        right = width - MARGIN_RIGHT_DP * density,
        bottom = height - MARGIN_BOTTOM_DP * density,
    )

/** +2 maps to the top of [plot], -2 to the bottom. */
fun valenceToY(
    valence: Float,
    plot: Rect,
): Float = plot.bottom - (valence + MAX_VALENCE) / (2 * MAX_VALENCE) * plot.height

/** 0 maps to the bottom of [plot], [max] to the top. */
fun peopleToY(
    people: Float,
    max: Int,
    plot: Rect,
): Float = plot.bottom - (people / max).coerceIn(0f, 1f) * plot.height

fun pointOffsets(
    points: List<GraphPoint>,
    plot: Rect,
): List<Offset> = points.map { Offset(plot.left + it.x * plot.width, valenceToY(it.valence, plot)) }

/** Index of the offset nearest to [tap] within [maxDistancePx], or null. */
fun hitTest(
    offsets: List<Offset>,
    tap: Offset,
    maxDistancePx: Float,
): Int? =
    offsets
        .withIndex()
        .map { (i, o) -> i to (o - tap).getDistance() }
        .filter { it.second <= maxDistancePx }
        .minByOrNull { it.second }
        ?.first
