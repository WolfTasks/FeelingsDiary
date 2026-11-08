package com.wolf.feelingsdiary.ui.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppNavGraphTest {
    @get:Rule
    val rule = createComposeRule()

    private lateinit var nav: NavHostController

    private val requests = Channel<Unit>(Channel.UNLIMITED)

    private fun setGraph() {
        rule.setContent {
            nav = rememberNavController()
            AppNavGraph(
                nav = nav,
                home = { onAdd, onEdit, onSettings ->
                    // Stacked texts would overlap and steal each other's taps.
                    Column {
                        Text(
                            "add",
                            Modifier.clickable {
                                onAdd()
                                onAdd()
                            },
                        )
                        Text("edit", Modifier.clickable { onEdit(5) })
                        Text(
                            "gear",
                            Modifier.clickable {
                                onSettings()
                                onSettings()
                            },
                        )
                    }
                },
                entry = { onDone ->
                    Text(
                        "close",
                        Modifier.clickable {
                            onDone()
                            onDone()
                        },
                    )
                },
                settings = { onBack ->
                    Text(
                        "back",
                        Modifier.clickable {
                            onBack()
                            onBack()
                        },
                    )
                },
                newEntryRequests = requests.receiveAsFlow(),
            )
        }
    }

    private val ENTRY_PATTERN = "entry?entryId={entryId}"

    private fun routes() = nav.currentBackStack.value.mapNotNull { it.destination.route }

    @Test
    fun doubleAddPushesEntryOnce() {
        setGraph()
        rule.onNodeWithText("add").performClick()
        rule.waitForIdle()
        assertEquals(listOf(ROUTE_HOME, ENTRY_PATTERN), routes())
    }

    @Test
    fun editNavigatesWithId() {
        setGraph()
        rule.onNodeWithText("edit").performClick()
        rule.waitForIdle()
        assertEquals(listOf(ROUTE_HOME, ENTRY_PATTERN), routes())
        assertEquals(5L, nav.currentBackStackEntry?.arguments?.getLong("entryId"))
    }

    @Test
    fun newEntryRequestOpensEntry() {
        setGraph()
        requests.trySend(Unit)
        rule.waitForIdle()
        assertEquals(listOf(ROUTE_HOME, ENTRY_PATTERN), routes())
        // Already on a new entry: a second request must not stack another one.
        requests.trySend(Unit)
        rule.waitForIdle()
        assertEquals(listOf(ROUTE_HOME, ENTRY_PATTERN), routes())
    }

    @Test
    fun newEntryRequestWhileEditingKeepsTheEdit() {
        setGraph()
        rule.onNodeWithText("edit").performClick()
        rule.waitForIdle()
        requests.trySend(Unit)
        rule.waitForIdle()
        assertEquals(listOf(ROUTE_HOME, ENTRY_PATTERN, ENTRY_PATTERN), routes())
        assertEquals(-1L, nav.currentBackStackEntry?.arguments?.getLong("entryId"))
    }

    @Test
    fun doubleCloseKeepsHome() {
        setGraph()
        rule.onNodeWithText("add").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("close").performClick()
        rule.waitForIdle()
        assertEquals(listOf(ROUTE_HOME), routes())
    }

    @Test
    fun gearOpensSettingsAndBackReturns() {
        setGraph()
        rule.onNodeWithText("gear").performClick()
        rule.waitForIdle()
        assertEquals(listOf(ROUTE_HOME, ROUTE_SETTINGS), routes())
        rule.onNodeWithText("back").performClick()
        rule.waitForIdle()
        assertEquals(listOf(ROUTE_HOME), routes())
    }
}
