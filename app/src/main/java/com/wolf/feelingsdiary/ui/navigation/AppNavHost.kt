package com.wolf.feelingsdiary.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.wolf.feelingsdiary.domain.InputMode
import com.wolf.feelingsdiary.ui.entry.EntryRoute
import com.wolf.feelingsdiary.ui.feelings.FeelingsRoute
import com.wolf.feelingsdiary.ui.home.HomeRoute
import com.wolf.feelingsdiary.ui.settings.SettingsRoute
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.receiveAsFlow

internal const val ROUTE_HOME = "home"
internal const val ROUTE_ENTRY = "entry"
internal const val ROUTE_SETTINGS = "settings"
internal const val ROUTE_FEELINGS = "feelings"
private const val ARG_ENTRY_ID = "entryId"
private const val NO_ENTRY = -1L

/** Route for the entry screen: a new entry, or edit mode when [id] is given. */
internal fun entryRoute(id: Long? = null): String = if (id == null) ROUTE_ENTRY else "$ROUTE_ENTRY?$ARG_ENTRY_ID=$id"

/**
 * [newEntryRequests]: each emission opens a new entry (e.g. a tap on the reminder notification),
 * in the [inputMode] read at that moment.
 */
@Composable
fun AppNavHost(
    newEntryRequests: Flow<Unit>,
    inputMode: suspend () -> InputMode,
) {
    AppNavGraph(
        nav = rememberNavController(),
        home = { onAdd, onEdit, onSettings, sheetRequests ->
            HomeRoute(onAdd = onAdd, onEdit = onEdit, onSettings = onSettings, sheetRequests = sheetRequests)
        },
        entry = { onDone -> EntryRoute(onDone = onDone) },
        settings = { onBack, onManageFeelings -> SettingsRoute(onBack = onBack, onManageFeelings = onManageFeelings) },
        feelings = { onBack -> FeelingsRoute(onBack = onBack) },
        newEntryRequests = newEntryRequests,
        inputMode = inputMode,
    )
}

/**
 * Navigation callbacks are wrapped in [dropUnlessResumed] so a double tap or a Close racing a
 * save-completion cannot push a second Entry/Settings/Feelings screen or pop Home off the back stack.
 */
@Composable
internal fun AppNavGraph(
    nav: NavHostController,
    home: @Composable (onAdd: () -> Unit, onEdit: (Long) -> Unit, onSettings: () -> Unit, sheetRequests: Flow<Unit>) -> Unit,
    entry: @Composable (onDone: () -> Unit) -> Unit,
    settings: @Composable (onBack: () -> Unit, onManageFeelings: () -> Unit) -> Unit,
    feelings: @Composable (onBack: () -> Unit) -> Unit,
    newEntryRequests: Flow<Unit> = emptyFlow(),
    inputMode: suspend () -> InputMode = { InputMode.SCREEN },
) {
    // Buffered until Home collects; the quick sheet lives on Home.
    val sheetRequests = remember { Channel<Unit>(Channel.CONFLATED) }
    val sheetRequestFlow = remember(sheetRequests) { sheetRequests.receiveAsFlow() }
    NavHost(navController = nav, startDestination = ROUTE_HOME) {
        composable(ROUTE_HOME) {
            val owner = LocalLifecycleOwner.current
            home(
                dropUnlessResumed { nav.navigate(entryRoute()) },
                { id -> if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) nav.navigate(entryRoute(id)) },
                dropUnlessResumed { nav.navigate(ROUTE_SETTINGS) },
                sheetRequestFlow,
            )
        }
        composable(ROUTE_SETTINGS) {
            settings(dropUnlessResumed { nav.popBackStack() }, dropUnlessResumed { nav.navigate(ROUTE_FEELINGS) })
        }
        composable(ROUTE_FEELINGS) { feelings(dropUnlessResumed { nav.popBackStack() }) }
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
    // After NavHost so the graph is set before the first navigate.
    val currentInputMode by rememberUpdatedState(inputMode)
    LaunchedEffect(nav, newEntryRequests) {
        newEntryRequests.collect {
            // Read per request, so a changed setting applies to the next notification tap.
            val mode = currentInputMode()
            val top = nav.currentBackStackEntry
            val onEntry = top?.destination?.route?.startsWith(ROUTE_ENTRY) == true
            val onNewEntry = onEntry && top.arguments?.getLong(ARG_ENTRY_ID, NO_ENTRY) == NO_ENTRY
            if (mode == InputMode.QUICK_SHEET && !onEntry) {
                // The sheet lives on Home. Settings and feelings store each change at once, so they can
                // be left; an entry screen (new or edit) is kept and handled like the screen mode.
                nav.popBackStack(ROUTE_HOME, inclusive = false)
                sheetRequests.trySend(Unit)
            } else if (!onNewEntry) {
                // A running edit stays below the new entry instead of being discarded.
                nav.navigate(entryRoute())
            }
        }
    }
}
