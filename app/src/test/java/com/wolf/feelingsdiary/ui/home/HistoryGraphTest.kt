package com.wolf.feelingsdiary.ui.home

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.wolf.feelingsdiary.domain.Aggregation
import com.wolf.feelingsdiary.domain.GraphData
import com.wolf.feelingsdiary.domain.GraphPoint
import com.wolf.feelingsdiary.ui.theme.FeelingsDiaryTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class HistoryGraphTest {
    @get:Rule
    val rule = createComposeRule()

    private fun point(
        x: Float,
        id: Long,
    ) = GraphPoint(x, 1f, 1L, listOf(2L, 99L), 3f, id, LocalDate.of(2026, 1, 1))

    private fun data(vararg points: GraphPoint) = GraphData(points.toList(), Aggregation.NONE, 5, setOf(1L, 2L))

    private val width = 400.dp
    private val height = 200.dp
    private var tapped: GraphPoint? = null
    private var density = 1f

    private fun show(data: GraphData) =
        rule.setContent {
            density = LocalDensity.current.density
            FeelingsDiaryTheme {
                HistoryGraph(
                    data = data,
                    colorOf = { if (it == 1L) Color.Red else Color.Blue },
                    xLabels = listOf(AxisLabel(0f, "Mon"), AxisLabel(1f, "Sun")),
                    onPointTap = { tapped = it },
                    modifier = Modifier.size(width, height),
                )
            }
        }

    @Test
    fun tapOnPointReportsIt() {
        val points = listOf(point(0.25f, 1), point(0.75f, 2))
        show(data(*points.toTypedArray()))
        val w = width.value * density
        val h = height.value * density
        val target = pointOffsets(points, plotRect(w, h, density))[0]
        rule.onNodeWithContentDescription("Graph, 2 entries").performTouchInput { click(Offset(target.x, target.y)) }
        assertEquals(points[0], tapped)
    }

    @Test
    fun emptyDataDrawsWithoutCrash() {
        show(data())
        rule.waitForIdle()
    }

    @Test
    fun singlePointDrawsWithoutCrash() {
        show(data(point(0.5f, 1)))
        rule.waitForIdle()
    }

    @Test
    fun hasGraphDescription() {
        show(data(point(0.25f, 1), point(0.75f, 2)))
        rule.onNodeWithContentDescription("Graph, 2 entries").assertExists()
    }

    @Test
    fun tapOnPointStillWorksWithLargeFontAndWidenedMargins() {
        val points = listOf(point(0.25f, 1), point(0.75f, 2))
        var margins: GraphMargins? = null
        rule.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale = 2f)) {
                FeelingsDiaryTheme {
                    margins = rememberGraphMargins(data(*points.toTypedArray()).peopleScaleMax)
                    HistoryGraph(
                        data = data(*points.toTypedArray()),
                        colorOf = { Color.Red },
                        xLabels = emptyList(),
                        onPointTap = { tapped = it },
                        modifier = Modifier.size(width, height),
                    )
                }
            }
        }
        val m = checkNotNull(margins)
        val d = rule.density.density
        assertTrue(m.leftPx >= 48f * d)
        val plot = plotRect(width.value * d, height.value * d, d, m.leftPx, m.rightPx)
        val target = pointOffsets(points, plot)[0]
        rule.onNodeWithContentDescription("Graph, 2 entries").performTouchInput { click(Offset(target.x, target.y)) }
        assertEquals(points[0], tapped)
    }
}
