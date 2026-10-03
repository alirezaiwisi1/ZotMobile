package com.zot.mobile.ui.screens.setup

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun SetupScreen(vm: SetupViewModel = viewModel(), onDone: () -> Unit) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Step ${state.step + 1} of 8", style = MaterialTheme.typography.labelMedium)
        LinearProgressIndicator(progress = { (state.step + 1) / 8f }, modifier = Modifier.fillMaxWidth())

        when (state.step) {
            0 -> {
                Text("Welcome to Zot Mobile", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                Text(
                    "Chat with an AI coding agent that works directly on your phone. " +
                    "It uses Termux as the local engine — no VPS, no cloud server. " +
                    "Clone repos, edit files, run tests, and push to GitHub, all from this app.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = vm::next) { Text("Get started") }
            }
            1, 2 -> {
                Text("Check environment", style = MaterialTheme.typography.headlineSmall)
                Text("This app runs commands inside Termux on your device.", style = MaterialTheme.typography.bodyMedium)
                Button(onClick = { vm.runChecks() }, enabled = !state.diagnosing) {
                    Text(if (state.diagnosing) "Checking…" else "Run environment check")
                }
                if (state.diagnosing) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.diagnostics.forEach { d ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(10.dp)) {
                            Text((if (d.ok) "✓ " else "✗ ") + d.name, color = if (d.ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                            Text(d.detail, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (state.diagnostics.isNotEmpty() && state.diagnostics.any { !it.ok && it.name == "Termux" }) {
                    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://f-droid.org/en/packages/com.termux/"))
                    Button(onClick = { context.startActivity(intent) }) { Text("Install Termux (F-Droid)") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = vm::back) { Text("Back") }
                    Button(onClick = vm::next) { Text("Next") }
                }
            }
            3 -> {
                Text("Local environment", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "On first run inside Termux, the app may ask you to install base tools. " +
                    "Open Termux once and run: pkg update && pkg install git nodejs — then come back.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = vm::back) { Text("Back") }
                    Button(onClick = vm::next) { Text("Next") }
                }
            }
            4 -> {
                Text("AI provider", style = MaterialTheme.typography.headlineSmall)
                vm.providers.forEach { p ->
                    FilterChip(
                        selected = state.aiProviderId == p.id,
                        onClick = { vm.setProvider(p.id) },
                        label = { Text(p.label) },
                    )
                }
                var key by remember { mutableStateOf("") }
                OutlinedTextField(
                    key, { key = it },
                    label = { Text("API key") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                )
                Text("Keys are stored encrypted on this device only.", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = vm::back) { Text("Back") }
                    Button(onClick = { if (key.isNotBlank()) vm.saveAiKey(key) else vm.next() }) { Text("Next") }
                }
            }
            5 -> {
                Text("GitHub (optional)", style = MaterialTheme.typography.headlineSmall)
                var token by remember { mutableStateOf("") }
                var user by remember { mutableStateOf("") }
                OutlinedTextField(user, { user = it }, label = { Text("Username") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(token, { token = it }, label = { Text("Access token (fine-grained, repo scope)") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = vm::back) { Text("Back") }
                    Button(onClick = { vm.saveGithub(token, user) }) { Text("Next") }
                }
            }
            6 -> {
                Text("Create or import a project", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Go to the Files tab after finishing to clone a GitHub repository or create an empty project. " +
                    "Then chat with the agent in the Agent tab.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = vm::back) { Text("Back") }
                    Button(onClick = vm::next) { Text("Next") }
                }
            }
            7 -> {
                Text("You're ready! 🎉", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "The agent tab is your home base. Describe what you want changed, review the diff, accept it, run tests, commit and push.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                Button(onClick = { vm.finish(); onDone() }) { Text("Start coding") }
            }
        }
    }
}
