package com.wolf.feelingsdiary.ui.home

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GraphGeometryTest {
    private val rect = Rect(0f, 0f, 100f, 200f)

    @Test
    fun valenceMapsTopMiddleBottom() {
        assertEquals(0f, valenceToY(2f, rect), 0.001f)
        assertEquals(100f, valenceToY(0f, rect), 0.001f)
        assertEquals(200f, valenceToY(-2f, rect), 0.001f)
    }

    @Test
    fun peopleMapsProportionally() {
        assertEquals(100f, peopleToY(5f, 10, rect), 0.001f)
    }

    @Test
    fun plotRectAppliesMargins() {
        val r = plotRect(width = 480f, height = 400f, density = 2f)
        assertEquals(96f, r.left, 0.001f)
        assertEquals(480f - 56f, r.right, 0.001f)
        assertEquals(16f, r.top, 0.001f)
        assertEquals(400f - 40f, r.bottom, 0.001f)
    }

    @Test
    fun hitTestPicksNearestWithinRadius() {
        val offsets = listOf(Offset(10f, 10f), Offset(40f, 10f))
        assertEquals(1, hitTest(offsets, Offset(35f, 10f), 24f))
        assertEquals(0, hitTest(offsets, Offset(0f, 10f), 24f))
    }

    @Test
    fun hitTestReturnsNullOutsideRadius() {
        assertNull(hitTest(listOf(Offset(0f, 0f)), Offset(30f, 0f), 24f))
    }

    @Test
    fun hitTestOnEmptyListReturnsNull() {
        assertNull(hitTest(emptyList(), Offset(0f, 0f), 24f))
    }

    @Test
    fun visibleLabelsSkipOverlappingOnes() {
        // label 1 starts 5 px after label 0 ends (< 8 px gap) and is skipped; label 2 is far enough from 0.
        assertEquals(listOf(0, 2), visibleLabelIndices(listOf(0f, 25f, 60f), listOf(20f, 20f, 20f), 8f))
    }

    @Test
    fun visibleLabelsKeepAllWhenSpaced() {
        assertEquals(listOf(0, 1, 2), visibleLabelIndices(listOf(0f, 30f, 60f), listOf(20f, 20f, 20f), 8f))
    }

    @Test
    fun visibleLabelsEmptyAndFirstAlwaysKept() {
        assertEquals(emptyList<Int>(), visibleLabelIndices(emptyList(), emptyList(), 8f))
        assertEquals(listOf(0), visibleLabelIndices(listOf(0f, 1f), listOf(50f, 50f), 8f))
    }

    @Test
    fun plotRectHonoursCustomHorizontalMargins() {
        val r = plotRect(width = 480f, height = 400f, density = 2f, leftPx = 150f, rightPx = 90f)
        assertEquals(150f, r.left, 0.001f)
        assertEquals(390f, r.right, 0.001f)
        assertEquals(16f, r.top, 0.001f)
    }

    @Test
    fun marginsWidenForWideLabelsAndKeepMinimums() {
        val wide = graphMargins(listOf(60, 140, 90), listOf(10, 70), gapPx = 8f, density = 2f)
        assertEquals(148f, wide.leftPx, 0.001f)
        assertEquals(78f, wide.rightPx, 0.001f)
        val narrow = graphMargins(listOf(20), listOf(5), gapPx = 8f, density = 2f)
        assertEquals(96f, narrow.leftPx, 0.001f)
        assertEquals(56f, narrow.rightPx, 0.001f)
    }
}
