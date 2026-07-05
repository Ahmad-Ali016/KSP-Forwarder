package com.kspay.forwarder.ui.result

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ResultScreen(
    outTradeNo: String,
    viewModel: ResultViewModel,
    onDone: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(outTradeNo) { viewModel.observe(outTradeNo) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when (val state = uiState) {
            ResultUiState.Loading -> CircularProgressIndicator()
            is ResultUiState.Success -> {
                Text("Payment Successful", style = MaterialTheme.typography.headlineSmall)
                Text(
                    state.amountDisplay,
                    style = MaterialTheme.typography.displayMedium,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
                state.refNo?.let { Text("Ref: $it") }
            }
            is ResultUiState.NonSuccess -> {
                Text("Payment Not Successful", style = MaterialTheme.typography.headlineSmall)
                Text(state.message, modifier = Modifier.padding(vertical = 16.dp))
            }
            is ResultUiState.Anomaly -> {
                Text("Payment Captured — Needs Review", style = MaterialTheme.typography.headlineSmall)
                Text(
                    state.amountDisplay,
                    style = MaterialTheme.typography.displayMedium,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
                Text("Do not charge again. A supervisor needs to reconcile this payment manually.")
            }
        }

        Button(onClick = onDone, modifier = Modifier.fillMaxWidth().padding(top = 32.dp)) {
            Text("Done")
        }
    }
}
