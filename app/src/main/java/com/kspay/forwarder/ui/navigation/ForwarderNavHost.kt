package com.kspay.forwarder.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
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
import com.kspay.forwarder.crypto.Money
import com.kspay.forwarder.data.TransactionRepository
import com.kspay.forwarder.di.AppContainer
import com.kspay.forwarder.ui.admin.TidSettingsScreen
import com.kspay.forwarder.ui.admin.TidSettingsViewModel
import com.kspay.forwarder.ui.fareentry.FareEntryScreen
import com.kspay.forwarder.ui.history.HistoryScreen
import com.kspay.forwarder.ui.history.HistoryViewModel
import com.kspay.forwarder.ui.inprogress.InProgressScreen
import com.kspay.forwarder.ui.inprogress.InProgressViewModel
import com.kspay.forwarder.ui.result.ResultScreen
import com.kspay.forwarder.ui.result.ResultViewModel
import kotlinx.coroutines.launch

@Composable
private fun rememberTransactionRepository(): TransactionRepository =
    (LocalContext.current.applicationContext as ForwarderApplication).container.transactionRepository

@Composable
private fun rememberAppContainer(): AppContainer =
    (LocalContext.current.applicationContext as ForwarderApplication).container

private const val OUT_TRADE_NO_ARG = "outTradeNo"

@Composable
fun ForwarderNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = ForwarderDestination.FareEntry.route,
        modifier = modifier,
    ) {
        composable(ForwarderDestination.FareEntry.route) {
            // Captures the container, not saleController itself — saleController is only
            // resolved (and its AndroidKeyStore-backed WorkingKeyStore built) once Charge is
            // actually tapped, not merely on composing this screen.
            val container = rememberAppContainer()
            val scope = rememberCoroutineScope()
            FareEntryScreen(
                onCharge = { amount ->
                    scope.launch {
                        val outTradeNo = container.saleController.charge(Money.toKpayCents(amount))
                        navController.navigate(ForwarderDestination.InProgress.routeFor(outTradeNo))
                    }
                },
                onSimulate = { amount ->
                    scope.launch {
                        val outTradeNo = container.saleController.simulateSuccess(Money.toKpayCents(amount))
                        navController.navigate(ForwarderDestination.Result.routeFor(outTradeNo))
                    }
                },
                onViewHistory = { navController.navigate(ForwarderDestination.History.route) },
                onTidSettings = { navController.navigate(ForwarderDestination.TidSettings.route) },
            )
        }
        composable(
            route = ForwarderDestination.InProgress.route,
            arguments = listOf(navArgument(OUT_TRADE_NO_ARG) { type = NavType.StringType }),
        ) { backStackEntry ->
            val outTradeNo = backStackEntry.arguments?.getString(OUT_TRADE_NO_ARG).orEmpty()
            val repository = rememberTransactionRepository()
            val container = rememberAppContainer()
            val scope = rememberCoroutineScope()
            val viewModel: InProgressViewModel = viewModel(
                factory = viewModelFactory { initializer { InProgressViewModel(repository) } },
            )
            InProgressScreen(
                outTradeNo = outTradeNo,
                viewModel = viewModel,
                onFinished = { transaction ->
                    navController.navigate(ForwarderDestination.Result.routeFor(transaction.outTradeNo)) {
                        popUpTo(ForwarderDestination.InProgress.route) { inclusive = true }
                    }
                },
                onAbort = { scope.launch { container.saleController.abort(outTradeNo) } },
            )
        }
        composable(
            route = ForwarderDestination.Result.route,
            arguments = listOf(navArgument(OUT_TRADE_NO_ARG) { type = NavType.StringType }),
        ) { backStackEntry ->
            val outTradeNo = backStackEntry.arguments?.getString(OUT_TRADE_NO_ARG).orEmpty()
            val repository = rememberTransactionRepository()
            val container = rememberAppContainer()
            val viewModel: ResultViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { ResultViewModel(repository, onPrintReceipt = container.saleController::printReceipt) }
                },
            )
            ResultScreen(
                outTradeNo = outTradeNo,
                viewModel = viewModel,
                onDone = {
                    navController.navigate(ForwarderDestination.FareEntry.route) {
                        popUpTo(ForwarderDestination.FareEntry.route) { inclusive = true }
                    }
                },
            )
        }
        composable(ForwarderDestination.History.route) {
            val repository = rememberTransactionRepository()
            val container = rememberAppContainer()
            val viewModel: HistoryViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { HistoryViewModel(repository, onPrintReceipt = container.saleController::printReceipt) }
                },
            )
            HistoryScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
        composable(ForwarderDestination.TidSettings.route) {
            val container = rememberAppContainer()
            val viewModel: TidSettingsViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { TidSettingsViewModel(container.adminPasswordStore, container.terminalInfoStore) }
                },
            )
            TidSettingsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
    }
}
