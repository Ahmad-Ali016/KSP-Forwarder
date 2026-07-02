package com.kspay.forwarder.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.kspay.forwarder.ui.fareentry.FareEntryScreen
import com.kspay.forwarder.ui.history.HistoryScreen
import com.kspay.forwarder.ui.inprogress.InProgressScreen
import com.kspay.forwarder.ui.result.ResultScreen

@Composable
fun ForwarderNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = ForwarderDestination.FareEntry.route,
        modifier = modifier,
    ) {
        composable(ForwarderDestination.FareEntry.route) { FareEntryScreen() }
        composable(ForwarderDestination.InProgress.route) { InProgressScreen() }
        composable(ForwarderDestination.Result.route) { ResultScreen() }
        composable(ForwarderDestination.History.route) { HistoryScreen() }
    }
}
