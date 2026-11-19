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
import com.wolf.feelingsdiary.domain.GraphBuilder.MAX_WEIGHTED_VALENCE
import com.wolf.feelingsdiary.domain.GraphData
import com.wolf.feelingsdiary.domain.GraphPoint

data class AxisLabel(
    val x: Float,
    val text: String,
)

private val GRAPH_HEIGHT = 200.dp
private val TAP_RADIUS = 24.dp

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
    val colors = GraphColors(neutral = onSurfaceVariant, people = MaterialTheme.colorScheme.primary, fallback = onSurfaceVariant)
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
        drawHistoryGraph(data, plot, colorOf, colors, measurer, labelStyle, valenceTexts, xLabels)
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
    MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = GRAPH_LABEL_FONT_SP.sp)

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
