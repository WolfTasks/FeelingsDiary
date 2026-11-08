package com.wolf.feelingsdiary.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FeelingNamesTest {
    private val happy = Feeling(1, "happy", null, 0, 2, false, null)
    private val hiddenSad = Feeling(2, "sad", null, 0, -2, true, null)
    private val wehmut = Feeling(10, null, "Wehmut", 0, -1, false, null)
    private val feelings = listOf(happy, hiddenSad, wehmut)
    private val labels = mapOf("happy" to "fröhlich", "sad" to "traurig")

    private fun label(f: Feeling) = f.customName ?: labels.getValue(f.baseKey!!)

    @Test
    fun matchesIgnoringCaseAccentsAndSpaces() {
        assertEquals(happy, FeelingNames.findDuplicate("  FROHLICH ", feelings, ::label))
        assertEquals(wehmut, FeelingNames.findDuplicate("wehmut", feelings, ::label))
    }

    @Test
    fun includesHiddenFeelings() {
        assertEquals(hiddenSad, FeelingNames.findDuplicate("Traurig", feelings, ::label))
    }

    @Test
    fun exceptIdSkipsTheEditedFeeling() {
        assertNull(FeelingNames.findDuplicate("WEHMUT", feelings, ::label, exceptId = 10))
        assertNull(FeelingNames.findDuplicate("Sehnsucht", feelings, ::label))
    }
}
