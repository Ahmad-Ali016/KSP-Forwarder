package com.kspay.forwarder.ui.history

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kspay.forwarder.crypto.Money
import com.kspay.forwarder.data.TransactionState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val TIMESTAMP_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

private val PRINTABLE_STATES =
    setOf(TransactionState.SUCCEEDED, TransactionState.FORWARDING, TransactionState.FORWARDED)

@Composable
fun HistoryScreen(viewModel: HistoryViewModel, onBack: () -> Unit = {}) {
    val transactions by viewModel.transactions.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Back")
        }

        if (transactions.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No transactions yet")
            }
            return@Column
        }

        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            items(transactions, key = { it.outTradeNo }) { transaction ->
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text(
                        "$" + Money.fromKpayCents(transaction.payAmountCents).setScale(2).toPlainString() +
                            "  ·  " + transaction.state,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(transaction.outTradeNo, style = MaterialTheme.typography.bodySmall)
                    Text(TIMESTAMP_FORMAT.format(Date(transaction.updatedAt)), style = MaterialTheme.typography.bodySmall)
                    if (transaction.lastError != null) {
                        Text("Error: " + transaction.lastError, style = MaterialTheme.typography.bodySmall)
                    }
                    if (transaction.state in PRINTABLE_STATES) {
                        OutlinedButton(onClick = { viewModel.printReceipt(transaction.outTradeNo) }) {
                            Text("Print")
                        }
                    }
                }
                HorizontalDivider()
            }
        }
    }
}
