package com.wolf.feelingsdiary.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.wolf.feelingsdiary.ui.entry.EntryRoute
import com.wolf.feelingsdiary.ui.home.HomeRoute

private const val ROUTE_HOME = "home"
private const val ROUTE_ENTRY = "entry"

@Composable
fun AppNavHost() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = ROUTE_HOME) {
        composable(ROUTE_HOME) { HomeRoute(onAdd = { nav.navigate(ROUTE_ENTRY) }) }
        composable(ROUTE_ENTRY) { EntryRoute(onDone = { nav.popBackStack() }) }
    }
}
