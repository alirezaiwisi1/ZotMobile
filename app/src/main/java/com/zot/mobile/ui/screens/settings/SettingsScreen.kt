package com.zot.mobile.ui.screens.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun SettingsScreen(vm: SettingsViewModel = viewModel(), onRunSetup: () -> Unit) {
    val state by vm.state.collectAsState()

    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Settings", style = MaterialTheme.typography.titleLarge)
        }
        state.message?.let { msg ->
            item { AssistChip(onClick = {}, label = { Text(msg) }) }
        }

        // Theme
        item {
            Card { Column(Modifier.padding(12.dp)) {
                Text("Appearance", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                    listOf("system" to "System", "light" to "Light", "dark" to "Dark").forEach { (v, label) ->
                        FilterChip(selected = state.theme == v, onClick = { vm.setTheme(v) }, label = { Text(label) })
                    }
                }
            } }
        }

        // AI provider
        item {
            Card { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("AI Provider", style = MaterialTheme.typography.titleSmall)
                Text("API keys are stored in Android's encrypted storage and never leave the device except to call your chosen provider.", style = MaterialTheme.typography.bodySmall)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    vm.providers.forEach { p ->
                        FilterChip(selected = state.aiProviderId == p.id, onClick = { vm.setProvider(p.id) }, label = { Text(p.label, style = MaterialTheme.typography.labelSmall) })
                    }
                }
                OutlinedTextField(
                    value = state.aiModel,
                    onValueChange = vm::setModel,
                    label = { Text("Model") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                var key by remember { mutableStateOf("") }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = key, onValueChange = { key = it },
                        label = { Text(if (state.hasAiKey) "Replace API key" else "API key") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.weight(1f), singleLine = true,
                    )
                    Button(onClick = { vm.saveAiKey(key); key = "" }, enabled = key.isNotBlank()) { Text("Save") }
                }
                if (state.hasAiKey) Text("✓ Key stored securely", style = MaterialTheme.typography.labelSmall)
            } }
        }

        // GitHub
        item {
            Card { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("GitHub", style = MaterialTheme.typography.titleSmall)
                Text("Create a fine-grained personal access token at github.com/settings/tokens with repo access only. It is stored encrypted on this device.", style = MaterialTheme.typography.bodySmall)
                var token by remember { mutableStateOf("") }
                var user by remember { mutableStateOf("") }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(token, { token = it }, label = { Text("Access token") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.weight(1f), singleLine = true)
                }
                OutlinedTextField(user, { user = it }, label = { Text("GitHub username") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(onClick = { vm.saveGithubToken(token, user); token = "" }, enabled = token.isNotBlank() && user.isNotBlank()) {
                    Text(if (state.hasGithubToken) "Replace token" else "Save token")
                }
                if (state.hasGithubToken) Text("✓ Token stored securely", style = MaterialTheme.typography.labelSmall)
            } }
        }

        // Environment diagnostics
        item {
            Card { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Environment Status", style = MaterialTheme.typography.titleSmall)
                Button(onClick = vm::runDiagnostics, enabled = !state.diagnosing) {
                    Text(if (state.diagnosing) "Checking…" else "Run environment check")
                }
                if (state.diagnosing) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.diagnostics.forEach { d ->
                    Text(
                        (if (d.ok) "✓ " else "✗ ") + d.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (d.ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    )
                    if (!d.ok) Text("   ${d.detail}", style = MaterialTheme.typography.bodySmall)
                }
                Button(onClick = onRunSetup, enabled = !state.diagnosing) { Text("Re-run setup wizard") }
            } }
        }

        item {
            Card { Column(Modifier.padding(12.dp)) {
                Text("Security", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                OutlinedButton(onClick = vm::deleteSecrets) { Text("Erase all stored credentials") }
            } }
        }
    }
}
