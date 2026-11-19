package com.wolf.feelingsdiary.ui.home

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.wolf.feelingsdiary.export.ExportResult
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ShareExportsTest {
    @get:Rule
    val rule = createComposeRule()

    private class Owner : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
    }

    @Test
    fun eventWaitsUntilScreenIsStarted() {
        val owner = Owner().apply { registry.currentState = Lifecycle.State.CREATED }
        val events = Channel<ExportEvent>(Channel.BUFFERED)
        val handled = mutableListOf<ExportEvent>()
        rule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                ShareExports(events.receiveAsFlow()) { handled += it }
            }
        }
        val event = ExportEvent.Share(ExportResult(emptyList(), "s", "text/csv"))
        events.trySend(event)
        rule.waitForIdle()
        assertEquals(emptyList<ExportEvent>(), handled)

        rule.runOnIdle { owner.registry.currentState = Lifecycle.State.STARTED }
        rule.waitForIdle()
        assertEquals(listOf(event), handled)
    }
}
