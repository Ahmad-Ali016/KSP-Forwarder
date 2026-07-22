package com.kspay.forwarder.ui.fareentry

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.math.BigDecimal

private val KEYPAD_KEYS = listOf('1', '2', '3', '4', '5', '6', '7', '8', '9', 'C', '0', '⌫')

@Composable
fun FareEntryScreen(
    onCharge: (BigDecimal) -> Unit = {},
    onSimulate: (BigDecimal) -> Unit = {},
    onViewHistory: () -> Unit = {},
    onTidSettings: () -> Unit = {},
    viewModel: FareEntryViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(
            text = uiState.displayText,
            style = MaterialTheme.typography.displayMedium,
            modifier = Modifier.padding(vertical = 32.dp),
        )

        LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.height(280.dp)) {
            items(KEYPAD_KEYS) { key ->
                OutlinedButton(
                    onClick = {
                        when (key) {
                            'C' -> viewModel.onClear()
                            '⌫' -> viewModel.onBackspace()
                            else -> viewModel.onDigit(key)
                        }
                    },
                    modifier = Modifier.padding(4.dp).fillMaxWidth(),
                ) {
                    Text(key.toString(), style = MaterialTheme.typography.headlineSmall)
                }
            }
        }

        Button(
            onClick = { onCharge(uiState.amount) },
            enabled = uiState.isValid,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        ) {
            Text("Charge")
        }

        OutlinedButton(
            onClick = onViewHistory,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            Text("History")
        }

        // Always visible, not debug-gated -- this must work on real production terminals
        // running the release build, since an admin sets the terminal's TID once after
        // installing the app (see TidSettingsScreen).
        OutlinedButton(
            onClick = onTidSettings,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            Text("TID")
        }
    }
}
