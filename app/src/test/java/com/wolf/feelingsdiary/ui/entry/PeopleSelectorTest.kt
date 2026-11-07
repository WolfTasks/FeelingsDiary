package com.wolf.feelingsdiary.ui.entry

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.wolf.feelingsdiary.ui.theme.FeelingsDiaryTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The quick buttons must show their labels on one line, even on a narrow phone with large font. */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
class PeopleSelectorTest {
    @get:Rule
    val rule = createComposeRule()

    private fun show() =
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, LARGE_FONT_SCALE)) {
                FeelingsDiaryTheme {
                    // Content width of a 320dp phone with the screen's 16dp side padding.
                    Box(Modifier.width(288.dp)) { PeopleSelector(people = null, onPeople = {}) }
                }
            }
        }

    private fun assertFitsOnOneLine(text: String) {
        val node = rule.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode()
        val results = mutableListOf<TextLayoutResult>()
        node.config
            .getOrNull(SemanticsActions.GetTextLayoutResult)
            ?.action
            ?.invoke(results)
        val layout = results.single()
        assertEquals("'$text' wraps", 1, layout.lineCount)
        val needed = layout.multiParagraph.intrinsics.maxIntrinsicWidth
        assertTrue("'$text' is cut: needs $needed px, got ${layout.size.width} px", needed <= layout.size.width)
    }

    @Test
    fun aloneAndNumbersFitWithLargeFont() {
        show()
        assertFitsOnOneLine("alone")
        assertFitsOnOneLine("3")
        assertFitsOnOneLine("6+")
    }

    @Test
    @Config(qualifiers = "de")
    fun germanAloneFitsWithLargeFont() {
        show()
        assertFitsOnOneLine("allein")
        assertFitsOnOneLine("6+")
    }

    private companion object {
        const val LARGE_FONT_SCALE = 1.3f
    }
}
