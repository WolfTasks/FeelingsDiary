package com.wolf.feelingsdiary.export

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.test.core.app.ApplicationProvider
import com.wolf.feelingsdiary.domain.Aggregation
import com.wolf.feelingsdiary.domain.GraphData
import com.wolf.feelingsdiary.domain.GraphPoint
import com.wolf.feelingsdiary.ui.home.AxisLabel
import com.wolf.feelingsdiary.ui.theme.LightColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GraphImageRendererTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()

    private fun point(x: Float) = GraphPoint(x, 2f, 1L, emptyList(), 2f, 1L, LocalDate.of(2026, 11, 1))

    @Test
    fun rendersLightImageWithLine() {
        val image =
            GraphImage(
                title = "Feelings diary – November 2026",
                data = GraphData(listOf(point(0.2f), point(0.8f)), Aggregation.NONE, 5, setOf(1L)),
                colorOf = { Color.Red },
                xLabels = listOf(AxisLabel(0f, "1"), AxisLabel(1f, "30")),
                valenceTexts = listOf("good", "neutral", "bad"),
                legend = listOf("happy" to Color.Red),
                peopleLabel = "👥 People",
                moreLabel = { n -> "+$n more" },
            )
        val bitmap = GraphImageRenderer.render(ctx, image)
        assertEquals(GraphImageRenderer.WIDTH, bitmap.width)
        assertEquals(GraphImageRenderer.HEIGHT, bitmap.height)
        assertEquals(LightColors.surface.toArgb(), bitmap.getPixel(5, 5))
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val red =
            pixels.count {
                android.graphics.Color.red(it) > 200 && android.graphics.Color.green(it) < 80 && android.graphics.Color.blue(it) < 80
            }
        assertTrue("red pixels: $red", red > 100)
    }

    private fun image(
        title: String = "Title",
        legend: List<Pair<String, Color>> = listOf("happy" to Color.Red),
    ) = GraphImage(
        title = title,
        data = GraphData(listOf(point(0.2f), point(0.8f)), Aggregation.NONE, 5, setOf(1L)),
        colorOf = { Color.Red },
        xLabels = listOf(AxisLabel(0f, "1"), AxisLabel(1f, "30")),
        valenceTexts = listOf("good", "neutral", "bad"),
        legend = legend,
        peopleLabel = "People",
        moreLabel = { n -> "+$n more" },
    )

    private fun redPixels(bitmap: android.graphics.Bitmap): Int {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels.count {
            android.graphics.Color.red(it) > 200 && android.graphics.Color.green(it) < 80 && android.graphics.Color.blue(it) < 80
        }
    }

    @Test
    fun longTitleStaysInsideImage() {
        val bitmap = GraphImageRenderer.render(ctx, image(title = "W".repeat(400)))
        val background = LightColors.surface.toArgb()
        // Right padding (16 dp at density 2.5 = 40 px) of the title band stays empty.
        val edge =
            (GraphImageRenderer.WIDTH - 30 until GraphImageRenderer.WIDTH).flatMap { x ->
                (40 until 100).map { y -> bitmap.getPixel(x, y) }
            }
        assertTrue(edge.all { it == background })
    }

    @Test
    fun manyFeelingsKeepGraphVisible() {
        val legend = (1..80).map { "feeling number $it" to Color.Blue }
        val spread = GraphData(listOf(point(0.2f), point(0.8f).copy(valence = -2f)), Aggregation.NONE, 5, setOf(1L))
        val bitmap = GraphImageRenderer.render(ctx, image(legend = legend).copy(data = spread))
        val redRows =
            (0 until bitmap.height).filter { y ->
                (0 until bitmap.width).any { x ->
                    val c = bitmap.getPixel(x, y)
                    android.graphics.Color.red(c) > 200 && android.graphics.Color.green(c) < 80 && android.graphics.Color.blue(c) < 80
                }
            }
        // Valence +2 to -2 spans half the plot; the plot keeps at least half the image height.
        assertTrue(
            "red rows ${redRows.firstOrNull()}..${redRows.lastOrNull()}",
            redRows.last() - redRows.first() > GraphImageRenderer.HEIGHT / 4,
        )
    }
}
