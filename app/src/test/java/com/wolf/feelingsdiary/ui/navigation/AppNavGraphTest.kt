package com.wolf.feelingsdiary.ui.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.wolf.feelingsdiary.domain.InputMode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    private var mode = InputMode.SCREEN
    private val sheetOpenings = mutableListOf<Unit>()

    private fun setGraph(onboardingDone: suspend () -> Boolean = { true }) {
        rule.setContent {
            nav = rememberNavController()
            AppNavGraph(
                nav = nav,
                home = { onAdd, onEdit, onSettings, sheetRequests ->
                    LaunchedEffect(sheetRequests) { sheetRequests.collect { sheetOpenings += Unit } }
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
                settings = { onBack, onManageFeelings ->
                    Column {
                        Text(
                            "back",
                            Modifier.clickable {
                                onBack()
                                onBack()
                            },
                        )
                        Text(
                            "feelings",
                            Modifier.clickable {
                                onManageFeelings()
                                onManageFeelings()
                            },
                        )
                    }
                },
                feelings = { onBack ->
                    Text(
                        "feelingsBack",
                        Modifier.clickable {
                            onBack()
                            onBack()
                        },
                    )
                },
                onboarding = { onDone ->
                    Text(
                        "start",
                        Modifier.clickable {
                            onDone()
                            onDone()
                        },
                    )
                },
                onboardingDone = onboardingDone,
                newEntryRequests = requests.receiveAsFlow(),
                inputMode = { mode },
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
    fun notificationOpensSheetInQuickMode() {
        setGraph()
        rule.onNodeWithText("gear").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("feelings").performClick()
        rule.waitForIdle()
        mode = InputMode.QUICK_SHEET
        requests.trySend(Unit)
        rule.waitForIdle()
        assertEquals(listOf(ROUTE_HOME), routes())
        assertEquals(1, sheetOpenings.size)
    }

    @Test
    fun notificationOnHomeInQuickModeOpensSheet() {
        setGraph()
        mode = InputMode.QUICK_SHEET
        requests.trySend(Unit)
        rule.waitForIdle()
        assertEquals(listOf(ROUTE_HOME), routes())
        assertEquals(1, sheetOpenings.size)
    }

    @Test
    fun notificationInQuickModeWhileEditingKeepsTheEdit() {
        setGraph()
        rule.onNodeWithText("edit").performClick()
        rule.waitForIdle()
        mode = InputMode.QUICK_SHEET
        requests.trySend(Unit)
        rule.waitForIdle()
        // The sheet lives on Home; leaving the edit would discard it, so the entry screen opens above.
        assertEquals(listOf(ROUTE_HOME, ENTRY_PATTERN, ENTRY_PATTERN), routes())
        assertEquals(0, sheetOpenings.size)
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

    @Test
    fun manageFeelingsOpensOnceAndBackReturnsToSettings() {
        setGraph()
        rule.onNodeWithText("gear").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("feelings").performClick()
        rule.waitForIdle()
        assertEquals(listOf(ROUTE_HOME, ROUTE_SETTINGS, ROUTE_FEELINGS), routes())
        rule.onNodeWithText("feelingsBack").performClick()
        rule.waitForIdle()
        assertEquals(listOf(ROUTE_HOME, ROUTE_SETTINGS), routes())
    }

    @Test
    fun firstStartShowsOnboarding() {
        val loaded = CompletableDeferred<Boolean>()
        setGraph { loaded.await() }
        // Until the settings are loaded nothing is shown: no Home flashing up before the onboarding.
        rule.waitForIdle()
        rule.onNodeWithText("add").assertDoesNotExist()
        rule.onNodeWithText("start").assertDoesNotExist()
        assertEquals(emptyList<String>(), routes())
        loaded.complete(false)
        rule.waitForIdle()
        assertEquals(listOf(ROUTE_ONBOARDING), routes())
        rule.onNodeWithText("start").assertExists()
        rule.onNodeWithText("add").assertDoesNotExist()
    }

    @Test
    fun doneStartsAtHome() {
        setGraph { true }
        rule.waitForIdle()
        assertEquals(listOf(ROUTE_HOME), routes())
        rule.onNodeWithText("start").assertDoesNotExist()
    }

    @Test
    fun finishStoresChoicesAndNavigatesHome() {
        setGraph { false }
        rule.onNodeWithText("start").performClick()
        rule.waitForIdle()
        // Double finish pushes Home once; the onboarding is gone, so Back leaves the app.
        assertEquals(listOf(ROUTE_HOME), routes())
        assertNull(nav.previousBackStackEntry)
    }

    @Test
    fun newEntryRequestDuringOnboardingIsIgnored() {
        setGraph { false }
        requests.trySend(Unit)
        rule.waitForIdle()
        assertEquals(listOf(ROUTE_ONBOARDING), routes())
        rule.onNodeWithText("start").performClick()
        rule.waitForIdle()
        // The ignored request is not replayed after the onboarding.
        assertEquals(listOf(ROUTE_HOME), routes())
        requests.trySend(Unit)
        rule.waitForIdle()
        assertEquals(listOf(ROUTE_HOME, ENTRY_PATTERN), routes())
    }
}
