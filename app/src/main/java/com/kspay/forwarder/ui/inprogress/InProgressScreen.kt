package com.kspay.forwarder.ui.inprogress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import com.kspay.forwarder.data.LocalTransaction

@Composable
fun InProgressScreen(
    outTradeNo: String,
    viewModel: InProgressViewModel,
    onFinished: (LocalTransaction) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(outTradeNo) { viewModel.observe(outTradeNo) }
    LaunchedEffect(uiState) {
        (uiState as? InProgressUiState.Finished)?.let { onFinished(it.transaction) }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Text(
            text = when (uiState) {
                InProgressUiState.Loading -> "Starting..."
                InProgressUiState.Sending -> "Sending sale..."
                InProgressUiState.Polling -> "Processing payment..."
                is InProgressUiState.Finished -> "Finishing up..."
            },
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 24.dp),
        )
    }
}
