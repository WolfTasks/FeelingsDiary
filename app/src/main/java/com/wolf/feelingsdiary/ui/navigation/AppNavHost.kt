package com.wolf.feelingsdiary.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.wolf.feelingsdiary.ui.entry.EntryRoute
import com.wolf.feelingsdiary.ui.home.HomeRoute

internal const val ROUTE_HOME = "home"
internal const val ROUTE_ENTRY = "entry"
private const val ARG_ENTRY_ID = "entryId"
private const val NO_ENTRY = -1L

/** Route for the entry screen: a new entry, or edit mode when [id] is given. */
internal fun entryRoute(id: Long? = null): String = if (id == null) ROUTE_ENTRY else "$ROUTE_ENTRY?$ARG_ENTRY_ID=$id"

@Composable
fun AppNavHost() {
    AppNavGraph(
        nav = rememberNavController(),
        home = { onAdd, onEdit -> HomeRoute(onAdd = onAdd, onEdit = onEdit) },
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
    home: @Composable (onAdd: () -> Unit, onEdit: (Long) -> Unit) -> Unit,
    entry: @Composable (onDone: () -> Unit) -> Unit,
) {
    NavHost(navController = nav, startDestination = ROUTE_HOME) {
        composable(ROUTE_HOME) {
            val owner = LocalLifecycleOwner.current
            home(
                dropUnlessResumed { nav.navigate(entryRoute()) },
                { id -> if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) nav.navigate(entryRoute(id)) },
            )
        }
        composable(
            "$ROUTE_ENTRY?$ARG_ENTRY_ID={$ARG_ENTRY_ID}",
            arguments =
                listOf(
                    navArgument(ARG_ENTRY_ID) {
                        type = NavType.LongType
                        defaultValue = NO_ENTRY
                    },
                ),
        ) { entry(dropUnlessResumed { nav.popBackStack() }) }
    }
}
