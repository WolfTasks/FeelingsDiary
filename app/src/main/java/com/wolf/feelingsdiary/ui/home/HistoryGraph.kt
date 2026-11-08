package com.wolf.feelingsdiary.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wolf.feelingsdiary.R
import com.wolf.feelingsdiary.domain.GraphData
import com.wolf.feelingsdiary.domain.GraphPoint

data class AxisLabel(
    val x: Float,
    val text: String,
)

private val GRAPH_HEIGHT = 200.dp
private val LINE_WIDTH = 2.5.dp
private val MAIN_RADIUS = 4.dp
private val OTHER_RADIUS = 2.dp
private val OTHER_SPACING = 6.dp
private const val MAX_DRAWN_OTHERS = 3
private val TAP_RADIUS = 24.dp
private val LABEL_GAP = 4.dp
private const val AREA_ALPHA = 0.16f
private const val DASH_ON_DP = 3f
private const val DASH_OFF_DP = 2f

@Composable
fun HistoryGraph(
    data: GraphData,
    colorOf: (Long) -> Color,
    xLabels: List<AxisLabel>,
    onPointTap: (GraphPoint) -> Unit,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val primary = MaterialTheme.colorScheme.primary
    val labelStyle = graphLabelStyle()
    val valenceTexts = valenceTexts()
    val margins = rememberGraphMargins(data.peopleScaleMax)
    val description = pluralStringResource(R.plurals.history_graph_cd, data.points.size, data.points.size)
    val currentData by rememberUpdatedState(data)
    val currentTap by rememberUpdatedState(onPointTap)
    val currentMargins by rememberUpdatedState(margins)

    Canvas(
        modifier =
            modifier
                .fillMaxWidth()
                .height(GRAPH_HEIGHT)
                .semantics { contentDescription = description }
                .pointerInput(Unit) {
                    detectTapGestures { tap ->
                        val plot =
                            plotRect(size.width.toFloat(), size.height.toFloat(), density, currentMargins.leftPx, currentMargins.rightPx)
                        val points = currentData.points
                        hitTest(pointOffsets(points, plot), tap, TAP_RADIUS.toPx())?.let { currentTap(points[it]) }
                    }
                },
    ) {
        val plot = plotRect(size.width, size.height, density, margins.leftPx, margins.rightPx)
        val offsets = pointOffsets(data.points, plot)

        // Unknown feelings arrive as Color.Unspecified and fall back to onSurfaceVariant.
        fun resolve(id: Long) = colorOf(id).let { if (it == Color.Unspecified) onSurfaceVariant else it }
        val mainColors = data.points.map { resolve(it.mainFeelingId) }

        drawNeutralLine(plot, onSurfaceVariant)
        drawPeopleArea(data, plot, primary)
        drawLineSegments(offsets, mainColors)
        offsets.forEachIndexed { i, o ->
            drawCircle(mainColors[i], MAIN_RADIUS.toPx(), o)
            data.points[i].otherFeelingIds.take(MAX_DRAWN_OTHERS).forEachIndexed { j, id ->
                drawCircle(resolve(id), OTHER_RADIUS.toPx(), Offset(o.x, o.y + OTHER_SPACING.toPx() * (j + 1)))
            }
        }
        drawAxisLabels(measurer, labelStyle, plot, valenceTexts, data.peopleScaleMax, xLabels)
    }
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

    listOf(2f, 0f, -2f).zip(valenceTexts).forEach { (v, s) ->
        val t = measurer.measure(s, style, maxLines = 1)
        drawText(t, topLeft = Offset((plot.left - gap - t.size.width).coerceAtLeast(0f), valenceToY(v, plot) - t.size.height / 2f))
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

@Composable
private fun valenceTexts() =
    listOf(
        stringResource(R.string.valence_good),
        stringResource(R.string.valence_neutral),
        stringResource(R.string.valence_bad),
    )

@Composable
private fun graphLabelStyle(): TextStyle =
    MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)

@Composable
internal fun rememberGraphMargins(peopleScaleMax: Int): GraphMargins {
    val measurer = rememberTextMeasurer()
    val style = graphLabelStyle()
    val texts = valenceTexts()
    val density = LocalDensity.current
    return remember(texts, style, peopleScaleMax, density) {
        val gap = LABEL_GAP.value * density.density

        fun width(s: String) = measurer.measure(s, style, maxLines = 1).size.width
        graphMargins(texts.map(::width), listOf(width("0"), width(peopleScaleMax.toString())), gap, density.density)
    }
}
