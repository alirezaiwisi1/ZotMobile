package com.zotmobile.app.ui

import com.zotmobile.app.ZotApp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun SettingsScreen(app: ZotApp) {
    val vm: SettingsViewModel = viewModel(factory = ZotViewModelFactory(app))
    val providers by vm.providers.collectAsState()
    val selected by vm.selectedProvider.collectAsState()
    val diagnostics by vm.diagnostics.collectAsState()
    val githubStatus by vm.githubStatus.collectAsState()
    val savedFlash by vm.savedFlash.collectAsState()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.titleLarge)
        if (savedFlash) Text("Saved.", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)

        // --- Environment diagnostics ---
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Environment", style = MaterialTheme.typography.titleMedium)
                diagnostics.forEach { d ->
                    Text(
                        (if (d.ok) "✓ " else "✗ ") + d.label + " — " + d.detail,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (!d.ok && d.fixHint.isNotBlank()) {
                        Text("   " + d.fixHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = { vm.runDiagnostics() }) { Text("Re-run checks") }
            }
        }

        // --- AI provider ---
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("AI provider", style = MaterialTheme.typography.titleMedium)
                providers.forEach { p ->
                    val isSel = p.id == selected
                    Row(Modifier.fillMaxWidth()) {
                        TextButton(onClick = { vm.selectProvider(p.id) }) {
                            Text((if (isSel) "● " else "○ ") + p.displayName)
                        }
                    }
                    if (isSel) {
                        var key by remember { mutableStateOf("") }
                        var model by remember { mutableStateOf("") }
                        var baseUrl by remember { mutableStateOf("") }
                        Text(p.docsHint, style = MaterialTheme.typography.bodySmall)
                        OutlinedTextField(
                            value = key, onValueChange = { key = it },
                            label = { Text(if (p.needsApiKey) "API key" else "API key (optional)") },
                            modifier = Modifier.fillMaxWidth(),
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        )
                        OutlinedTextField(
                            value = model, onValueChange = { model = it },
                            label = { Text("Model (default: " + p.defaultModel + ")") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = baseUrl, onValueChange = { baseUrl = it },
                            label = { Text("Base URL (default: " + p.defaultBaseUrl + ")") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { vm.saveAiKey(key) }) { Text("Save key") }
                            OutlinedButton(onClick = { vm.saveModel(model) }) { Text("Save model") }
                            OutlinedButton(onClick = { vm.saveBaseUrl(baseUrl) }) { Text("Save URL") }
                        }
                    }
                }
                Text(
                    "Keys are encrypted with the Android Keystore and never leave the device except to call your provider.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // --- GitHub ---
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("GitHub", style = MaterialTheme.typography.titleMedium)
                Text(githubStatus, style = MaterialTheme.typography.bodySmall)
                var token by remember { mutableStateOf("") }
                OutlinedTextField(
                    value = token, onValueChange = { token = it },
                    label = { Text("Personal access token") },
                    placeholder = { Text("github.com/settings/tokens — scope: repo") },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.saveGithubToken(token) }) { Text("Save token") }
                    OutlinedButton(onClick = { vm.checkGithub() }) { Text("Test") }
                }
            }
        }

        // --- Git identity ---
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Git identity", style = MaterialTheme.typography.titleMedium)
                var name by remember { mutableStateOf("") }
                var email by remember { mutableStateOf("") }
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
                Button(onClick = { vm.saveGitIdentity(name, email) }) { Text("Save") }
            }
        }
    }
}
