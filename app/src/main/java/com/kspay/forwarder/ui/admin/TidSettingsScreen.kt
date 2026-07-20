package com.kspay.forwarder.ui.admin

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun TidSettingsScreen(viewModel: TidSettingsViewModel, onBack: () -> Unit = {}) {
    val uiState by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Back")
        }

        when (val state = uiState) {
            is TidSettingsUiState.Locked -> LockedContent(state, onUnlock = viewModel::unlock)
            is TidSettingsUiState.Unlocked -> UnlockedContent(
                state = state,
                onSaveTid = viewModel::saveTid,
                onChangePassword = viewModel::changePassword,
            )
        }
    }
}

@Composable
private fun LockedContent(state: TidSettingsUiState.Locked, onUnlock: (String) -> Unit) {
    var password by remember { mutableStateOf("") }

    Text("Admin Access", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 24.dp))
    OutlinedTextField(
        value = password,
        onValueChange = { password = it },
        label = { Text("Admin password") },
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
    )
    if (state.error != null) {
        Text(state.error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
    }
    Button(onClick = { onUnlock(password) }, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Text("Unlock")
    }
}

@Composable
private fun UnlockedContent(
    state: TidSettingsUiState.Unlocked,
    onSaveTid: (String) -> Unit,
    onChangePassword: (String, String) -> Unit,
) {
    var tid by remember { mutableStateOf(state.currentTid.orEmpty()) }
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }

    Text("Terminal ID", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 24.dp))
    Text("Current: " + (state.currentTid ?: "Not set"), style = MaterialTheme.typography.bodyMedium)
    OutlinedTextField(
        value = tid,
        onValueChange = { tid = it },
        label = { Text("8-digit TID") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
    Button(onClick = { onSaveTid(tid) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text("Save TID")
    }

    HorizontalDivider(modifier = Modifier.padding(vertical = 24.dp))

    Text("Change Admin Password", style = MaterialTheme.typography.titleLarge)
    OutlinedTextField(
        value = currentPassword,
        onValueChange = { currentPassword = it },
        label = { Text("Current password") },
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
    OutlinedTextField(
        value = newPassword,
        onValueChange = { newPassword = it },
        label = { Text("New password") },
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
    Button(
        onClick = { onChangePassword(currentPassword, newPassword) },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Text("Change Password")
    }

    if (state.message != null) {
        Text(state.message, modifier = Modifier.padding(top = 16.dp))
    }
}
