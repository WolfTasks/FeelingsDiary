package com.wolf.feelingsdiary.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.wolf.feelingsdiary.ui.entry.EntryRoute
import com.wolf.feelingsdiary.ui.home.HomeRoute

internal const val ROUTE_HOME = "home"
internal const val ROUTE_ENTRY = "entry"

@Composable
fun AppNavHost() {
    AppNavGraph(
        nav = rememberNavController(),
        home = { onAdd -> HomeRoute(onAdd = onAdd, onEdit = {}) },
        entry = { onDone -> EntryRoute(onDone = onDone) },
    )
}

/**
 * Navigation callbacks are wrapped in [dropUnlessResumed] so a double tap or a Close racing a
 * save-completion cannot push a second Entry screen or pop Home off the back stack.
 */
@Composable
internal fun AppNavGraph(
    nav: NavHostController,
    home: @Composable (onAdd: () -> Unit) -> Unit,
    entry: @Composable (onDone: () -> Unit) -> Unit,
) {
    NavHost(navController = nav, startDestination = ROUTE_HOME) {
        composable(ROUTE_HOME) { home(dropUnlessResumed { nav.navigate(ROUTE_ENTRY) }) }
        composable(ROUTE_ENTRY) { entry(dropUnlessResumed { nav.popBackStack() }) }
    }
}
