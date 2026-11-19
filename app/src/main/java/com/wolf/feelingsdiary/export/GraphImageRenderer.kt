package com.wolf.feelingsdiary.export

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wolf.feelingsdiary.domain.GraphData
import com.wolf.feelingsdiary.ui.home.AxisLabel
import com.wolf.feelingsdiary.ui.home.GRAPH_LABEL_FONT_SP
import com.wolf.feelingsdiary.ui.home.GraphColors
import com.wolf.feelingsdiary.ui.home.LABEL_GAP
import com.wolf.feelingsdiary.ui.home.drawHistoryGraph
import com.wolf.feelingsdiary.ui.home.graphMargins
import com.wolf.feelingsdiary.ui.home.plotRect
import com.wolf.feelingsdiary.ui.theme.LightColors

/** Everything the exported image shows; texts are already localized. */
data class GraphImage(
    val title: String,
    val data: GraphData,
    val colorOf: (Long) -> Color,
    val xLabels: List<AxisLabel>,
    val valenceTexts: List<String>,
    val legend: List<Pair<String, Color>>,
    val peopleLabel: String,
    /** Legend entry standing in for the feelings that no longer fit, e.g. "+3 more". */
    val moreLabel: (Int) -> String,
)

/** Renders the history graph with title and legend into a bitmap, always in the light theme (mail and print). */
object GraphImageRenderer {
    const val WIDTH = 1600
    const val HEIGHT = 900
    const val DENSITY = 2.5f

    private val PADDING = 16.dp
    private val SECTION_GAP = 12.dp
    private val DOT = 10.dp
    private val DOT_GAP = 6.dp
    private val ITEM_GAP = 12.dp
    private val ROW_GAP = 4.dp
    private const val TITLE_SP = 20f
    private const val LEGEND_SP = 12f
    private const val PEOPLE_ALPHA = 0.4f
    private const val MAX_LEGEND_ROWS = 3

    fun render(
        context: Context,
        image: GraphImage,
    ): Bitmap {
        val density = Density(DENSITY)
        val measurer = TextMeasurer(createFontFamilyResolver(context), density, LayoutDirection.Ltr)
        val bitmap = ImageBitmap(WIDTH, HEIGHT)
        CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(bitmap), Size(WIDTH.toFloat(), HEIGHT.toFloat())) {
            drawRect(LightColors.surface)
            drawContent(image, measurer)
        }
        return bitmap.asAndroidBitmap()
    }

    private fun DrawScope.drawContent(
        image: GraphImage,
        measurer: TextMeasurer,
    ) {
        val pad = PADDING.toPx()
        val gap = SECTION_GAP.toPx()
        val contentWidth = size.width - 2 * pad

        val textWidth = Constraints(maxWidth = contentWidth.toInt())

        fun line(
            text: String,
            style: TextStyle,
            maxWidth: Constraints = textWidth,
        ) = measurer.measure(text, style, TextOverflow.Ellipsis, softWrap = false, maxLines = 1, constraints = maxWidth)

        val title = line(image.title, TextStyle(color = LightColors.onSurface, fontSize = TITLE_SP.sp))
        drawText(title, topLeft = Offset(pad, pad))

        val legendStyle = TextStyle(color = LightColors.onSurface, fontSize = LEGEND_SP.sp)
        val itemWidth = Constraints(maxWidth = (contentWidth - DOT.toPx() - DOT_GAP.toPx()).toInt())
        val feelingTexts = image.legend.map { (label, _) -> line(label, legendStyle, itemWidth) }
        val peopleItem = line(image.peopleLabel, legendStyle, itemWidth) to LightColors.primary.copy(alpha = PEOPLE_ALPHA)

        // Keep the graph readable: at most MAX_LEGEND_ROWS rows, the rest folds into "+N more".
        fun items(shown: Int): List<Pair<TextLayoutResult, Color>> {
            val more = image.legend.size - shown
            val moreItem =
                if (more >
                    0
                ) {
                    listOf(line(image.moreLabel(more), legendStyle, itemWidth) to LightColors.onSurfaceVariant)
                } else {
                    emptyList()
                }
            return feelingTexts.take(shown).zip(image.legend.take(shown).map { it.second }) + moreItem + peopleItem
        }
        var legendItems = items(image.legend.size)
        var shown = image.legend.size
        while (shown > 0 && legendRows(legendItems.map { it.first }, contentWidth).size > MAX_LEGEND_ROWS) {
            shown--
            legendItems = items(shown)
        }
        val legendTexts = legendItems.map { it.first }
        val legendRows = legendRows(legendTexts, contentWidth)
        val rowHeight = maxOf(legendTexts.maxOf { it.size.height }.toFloat(), DOT.toPx())
        val legendTop = size.height - pad - legendRows.size * rowHeight - (legendRows.size - 1) * ROW_GAP.toPx()
        drawLegend(legendRows, legendItems.map { it.second }, legendTexts, Offset(pad, legendTop), rowHeight)

        val labelStyle = TextStyle(color = LightColors.onSurfaceVariant, fontSize = GRAPH_LABEL_FONT_SP.sp)
        val people = line(image.peopleLabel, labelStyle)
        val areaTop = pad + title.size.height + gap
        drawText(people, topLeft = Offset(size.width - pad - people.size.width, areaTop))
        val graphTop = areaTop + people.size.height + LABEL_GAP.toPx()
        val graphHeight = legendTop - gap - graphTop

        fun width(s: String) = measurer.measure(s, labelStyle, maxLines = 1).size.width
        val margins =
            graphMargins(
                image.valenceTexts.map(::width),
                listOf(width("0"), width(image.data.peopleScaleMax.toString())),
                LABEL_GAP.toPx(),
                density,
            )
        val plot = plotRect(contentWidth, graphHeight, density, margins.leftPx, margins.rightPx)
        val colors = GraphColors(LightColors.onSurfaceVariant, LightColors.primary, LightColors.onSurfaceVariant)
        translate(pad, graphTop) {
            // The axis labels clamp to the draw size, so give them the graph area rather than the whole image.
            drawGraphArea(image, plot, colors, measurer, labelStyle, Size(contentWidth, graphHeight))
        }
    }

    private fun DrawScope.drawGraphArea(
        image: GraphImage,
        plot: Rect,
        colors: GraphColors,
        measurer: TextMeasurer,
        labelStyle: TextStyle,
        area: Size,
    ) {
        CanvasDrawScope().draw(this, layoutDirection, drawContext.canvas, area) {
            drawHistoryGraph(image.data, plot, image.colorOf, colors, measurer, labelStyle, image.valenceTexts, image.xLabels)
        }
    }

    /** Indices per legend row, wrapping when the next item would exceed [maxWidth]. */
    private fun DrawScope.legendRows(
        texts: List<TextLayoutResult>,
        maxWidth: Float,
    ): List<List<Int>> {
        val rows = mutableListOf(mutableListOf<Int>())
        var x = 0f
        texts.forEachIndexed { i, t ->
            val w = DOT.toPx() + DOT_GAP.toPx() + t.size.width
            if (rows.last().isNotEmpty() && x + w > maxWidth) {
                rows += mutableListOf<Int>()
                x = 0f
            }
            rows.last() += i
            x += w + ITEM_GAP.toPx()
        }
        return rows
    }

    private fun DrawScope.drawLegend(
        rows: List<List<Int>>,
        colors: List<Color>,
        texts: List<TextLayoutResult>,
        origin: Offset,
        rowHeight: Float,
    ) {
        val radius = DOT.toPx() / 2
        rows.forEachIndexed { r, row ->
            val y = origin.y + r * (rowHeight + ROW_GAP.toPx())
            var x = origin.x
            row.forEach { i ->
                drawCircle(colors[i], radius, Offset(x + radius, y + rowHeight / 2))
                x += DOT.toPx() + DOT_GAP.toPx()
                drawText(texts[i], topLeft = Offset(x, y + (rowHeight - texts[i].size.height) / 2))
                x += texts[i].size.width + ITEM_GAP.toPx()
            }
        }
    }
}
