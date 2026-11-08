package com.wolf.feelingsdiary.ui.home

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.wolf.feelingsdiary.domain.GraphPoint

const val MARGIN_LEFT_DP = 48f
const val MARGIN_RIGHT_DP = 28f
private const val MARGIN_TOP_DP = 8f
private const val MARGIN_BOTTOM_DP = 20f
private const val MAX_VALENCE = 2f

fun plotRect(
    width: Float,
    height: Float,
    density: Float,
    leftPx: Float = MARGIN_LEFT_DP * density,
    rightPx: Float = MARGIN_RIGHT_DP * density,
): Rect =
    Rect(
        left = leftPx,
        top = MARGIN_TOP_DP * density,
        right = width - rightPx,
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

/** Indices of labels to draw: the first, then each label starting at least [minGapPx] after the last drawn one ends. */
fun visibleLabelIndices(
    lefts: List<Float>,
    widths: List<Float>,
    minGapPx: Float,
): List<Int> {
    val result = mutableListOf<Int>()
    var lastRight = Float.NEGATIVE_INFINITY
    lefts.indices.forEach { i ->
        if (result.isEmpty() || lefts[i] - lastRight >= minGapPx) {
            result += i
            lastRight = lefts[i] + widths[i]
        }
    }
    return result
}

/** Horizontal plot margins in px, widened so the axis labels fit at any font scale. */
data class GraphMargins(
    val leftPx: Float,
    val rightPx: Float,
)

fun graphMargins(
    leftLabelWidths: List<Int>,
    rightLabelWidths: List<Int>,
    gapPx: Float,
    density: Float,
): GraphMargins =
    GraphMargins(
        leftPx = maxOf(MARGIN_LEFT_DP * density, (leftLabelWidths.maxOrNull() ?: 0) + gapPx),
        rightPx = maxOf(MARGIN_RIGHT_DP * density, (rightLabelWidths.maxOrNull() ?: 0) + gapPx),
    )
