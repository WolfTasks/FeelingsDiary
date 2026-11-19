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
}
