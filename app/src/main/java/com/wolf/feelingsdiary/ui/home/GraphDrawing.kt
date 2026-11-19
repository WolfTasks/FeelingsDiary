package com.wolf.feelingsdiary.ui.home

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import com.wolf.feelingsdiary.domain.GraphBuilder.MAX_WEIGHTED_VALENCE
import com.wolf.feelingsdiary.domain.GraphData

const val GRAPH_LABEL_FONT_SP = 10f
internal val LABEL_GAP = 4.dp
private val LINE_WIDTH = 2.5.dp
private val MAIN_RADIUS = 4.dp
private val OTHER_RADIUS = 2.dp
private val OTHER_SPACING = 6.dp
private const val MAX_DRAWN_OTHERS = 3
private const val AREA_ALPHA = 0.16f
private const val DASH_ON_DP = 3f
private const val DASH_OFF_DP = 2f

/** Theme colours of the graph; [fallback] stands in for feelings without a known colour. */
data class GraphColors(
    val neutral: Color,
    val people: Color,
    val fallback: Color,
)

/** Draws the whole graph into [plot]; shared by the on-screen graph and the exported image. */
fun DrawScope.drawHistoryGraph(
    data: GraphData,
    plot: Rect,
    colorOf: (Long) -> Color,
    colors: GraphColors,
    measurer: TextMeasurer,
    labelStyle: TextStyle,
    valenceTexts: List<String>,
    xLabels: List<AxisLabel>,
) {
    val offsets = pointOffsets(data.points, plot)

    // Unknown feelings arrive as Color.Unspecified and fall back to colors.fallback.
    fun resolve(id: Long) = colorOf(id).let { if (it == Color.Unspecified) colors.fallback else it }
    val mainColors = data.points.map { resolve(it.mainFeelingId) }

    drawNeutralLine(plot, colors.neutral)
    drawPeopleArea(data, plot, colors.people)
    drawLineSegments(offsets, mainColors)
    offsets.forEachIndexed { i, o ->
        drawCircle(mainColors[i], MAIN_RADIUS.toPx(), o)
        data.points[i].otherFeelingIds.take(MAX_DRAWN_OTHERS).forEachIndexed { j, id ->
            drawCircle(resolve(id), OTHER_RADIUS.toPx(), Offset(o.x, o.y + OTHER_SPACING.toPx() * (j + 1)))
        }
    }
    drawAxisLabels(measurer, labelStyle, plot, valenceTexts, data.peopleScaleMax, xLabels)
}

private fun DrawScope.dashEffect() = PathEffect.dashPathEffect(floatArrayOf(DASH_ON_DP * density, DASH_OFF_DP * density))

private fun DrawScope.drawNeutralLine(
    plot: Rect,
    color: Color,
) {
    val y = valenceToY(0f, plot)
    drawLine(color, Offset(plot.left, y), Offset(plot.right, y), strokeWidth = 1.dp.toPx(), pathEffect = dashEffect())
}

private fun DrawScope.drawPeopleArea(
    data: GraphData,
    plot: Rect,
    color: Color,
) {
    if (data.points.isEmpty()) return
    val tops = data.points.map { Offset(plot.left + it.x * plot.width, peopleToY(it.people, data.peopleScaleMax, plot)) }
    val edge =
        Path().apply {
            moveTo(tops.first().x, tops.first().y)
            tops.drop(1).forEach { lineTo(it.x, it.y) }
        }
    val area =
        Path().apply {
            addPath(edge)
            lineTo(tops.last().x, plot.bottom)
            lineTo(tops.first().x, plot.bottom)
            close()
        }
    drawPath(area, color.copy(alpha = AREA_ALPHA))
    drawPath(edge, color, style = Stroke(1.dp.toPx(), pathEffect = dashEffect()))
}

private fun DrawScope.drawLineSegments(
    offsets: List<Offset>,
    mainColors: List<Color>,
) {
    for (i in 0 until offsets.size - 1) {
        val a = offsets[i]
        val b = offsets[i + 1]
        val brush = Brush.horizontalGradient(listOf(mainColors[i], mainColors[i + 1]), a.x, maxOf(b.x, a.x + 1f))
        drawLine(brush, a, b, strokeWidth = LINE_WIDTH.toPx())
    }
}

private fun DrawScope.drawAxisLabels(
    measurer: TextMeasurer,
    style: TextStyle,
    plot: Rect,
    valenceTexts: List<String>,
    peopleMax: Int,
    xLabels: List<AxisLabel>,
) {
    val gap = LABEL_GAP.toPx()

    listOf(MAX_WEIGHTED_VALENCE, 0f, -MAX_WEIGHTED_VALENCE).zip(valenceTexts).forEach { (valence, label) ->
        val t = measurer.measure(label, style, maxLines = 1)
        drawText(t, topLeft = Offset((plot.left - gap - t.size.width).coerceAtLeast(0f), valenceToY(valence, plot) - t.size.height / 2f))
    }
    listOf(0 to plot.bottom, peopleMax to plot.top).forEach { (n, y) ->
        val t = measurer.measure(n.toString(), style, maxLines = 1)
        drawText(t, topLeft = Offset(plot.right + gap, y - t.size.height / 2f))
    }
    val measured = xLabels.map { measurer.measure(it.text, style, maxLines = 1) }
    val lefts =
        xLabels.mapIndexed { i, l ->
            (plot.left + l.x * plot.width - measured[i].size.width / 2f).coerceIn(0f, size.width - measured[i].size.width)
        }
    visibleLabelIndices(lefts, measured.map { it.size.width.toFloat() }, gap).forEach { i ->
        drawText(measured[i], topLeft = Offset(lefts[i], plot.bottom + gap))
    }
}
