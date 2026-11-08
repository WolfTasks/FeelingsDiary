package com.wolf.feelingsdiary.ui.navigation

import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
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

    private fun setGraph() {
        rule.setContent {
            nav = rememberNavController()
            AppNavGraph(
                nav = nav,
                home = { onAdd, onEdit ->
                    Text(
                        "add",
                        Modifier.clickable {
                            onAdd()
                            onAdd()
                        },
                    )
                    Text("edit", Modifier.clickable { onEdit(5) })
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
    fun doubleCloseKeepsHome() {
        setGraph()
        rule.onNodeWithText("add").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("close").performClick()
        rule.waitForIdle()
        assertEquals(listOf(ROUTE_HOME), routes())
    }
}
