package com.wolf.feelingsdiary.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import com.wolf.feelingsdiary.ui.onboarding.OnboardingRoute
import com.wolf.feelingsdiary.ui.settings.SettingsRoute
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.receiveAsFlow

internal const val ROUTE_HOME = "home"
internal const val ROUTE_ENTRY = "entry"
internal const val ROUTE_SETTINGS = "settings"
internal const val ROUTE_FEELINGS = "feelings"
internal const val ROUTE_ONBOARDING = "onboarding"
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
    onboardingDone: suspend () -> Boolean,
) {
    AppNavGraph(
        nav = rememberNavController(),
        home = { onAdd, onEdit, onSettings, sheetRequests ->
            HomeRoute(onAdd = onAdd, onEdit = onEdit, onSettings = onSettings, sheetRequests = sheetRequests)
        },
        entry = { onDone -> EntryRoute(onDone = onDone) },
        settings = { onBack, onManageFeelings -> SettingsRoute(onBack = onBack, onManageFeelings = onManageFeelings) },
        feelings = { onBack -> FeelingsRoute(onBack = onBack) },
        onboarding = { onDone -> OnboardingRoute(onDone = onDone) },
        onboardingDone = onboardingDone,
        newEntryRequests = newEntryRequests,
        inputMode = inputMode,
    )
}

/**
 * The start destination is decided once, from [onboardingDone]: until it is known only the background
 * shows, so Home never flashes up before the onboarding. New-entry requests wait for that decision and
 * are dropped while the onboarding is shown.
 *
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
    onboarding: @Composable (onDone: () -> Unit) -> Unit,
    onboardingDone: suspend () -> Boolean,
    newEntryRequests: Flow<Unit> = emptyFlow(),
    inputMode: suspend () -> InputMode = { InputMode.SCREEN },
) {
    // Buffered until Home collects; the quick sheet lives on Home.
    val sheetRequests = remember { Channel<Unit>(Channel.CONFLATED) }
    val sheetRequestFlow = remember(sheetRequests) { sheetRequests.receiveAsFlow() }
    // Saved, so a recreated activity restores its back stack without waiting for the settings again.
    var start by rememberSaveable { mutableStateOf<String?>(null) }
    val currentOnboardingDone by rememberUpdatedState(onboardingDone)
    LaunchedEffect(Unit) {
        if (start == null) start = if (currentOnboardingDone()) ROUTE_HOME else ROUTE_ONBOARDING
    }
    val startDestination = start
    if (startDestination == null) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {}
        return
    }
    NavHost(navController = nav, startDestination = startDestination) {
        composable(ROUTE_ONBOARDING) {
            onboarding {
                // Guarded instead of dropUnlessResumed: the call follows a settings write and must not be
                // lost if the app is paused meanwhile; a second call finds Home on top and does nothing.
                if (nav.currentDestination?.route == ROUTE_ONBOARDING) {
                    nav.navigate(ROUTE_HOME) { popUpTo(ROUTE_ONBOARDING) { inclusive = true } }
                }
            }
        }
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
            // The app starts after the onboarding; a notification tap meanwhile is dropped, not kept for later.
            if (top?.destination?.route == ROUTE_ONBOARDING) return@collect
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
