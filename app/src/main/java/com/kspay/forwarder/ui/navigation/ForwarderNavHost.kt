package com.kspay.forwarder.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kspay.forwarder.ForwarderApplication
import com.kspay.forwarder.ui.fareentry.FareEntryScreen
import com.kspay.forwarder.ui.history.HistoryScreen
import com.kspay.forwarder.ui.inprogress.InProgressScreen
import com.kspay.forwarder.ui.inprogress.InProgressViewModel
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
        composable(
            route = ForwarderDestination.InProgress.route,
            arguments = listOf(navArgument("outTradeNo") { type = NavType.StringType }),
        ) { backStackEntry ->
            val outTradeNo = backStackEntry.arguments?.getString("outTradeNo").orEmpty()
            val repository = (LocalContext.current.applicationContext as ForwarderApplication)
                .container.transactionRepository
            val viewModel: InProgressViewModel = viewModel(
                factory = viewModelFactory { initializer { InProgressViewModel(repository) } },
            )
            InProgressScreen(outTradeNo = outTradeNo, viewModel = viewModel)
        }
        composable(ForwarderDestination.Result.route) { ResultScreen() }
        composable(ForwarderDestination.History.route) { HistoryScreen() }
    }
}
