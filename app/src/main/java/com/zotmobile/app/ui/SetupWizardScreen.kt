package com.zotmobile.app.ui

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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.zotmobile.app.ZotApp
import com.zotmobile.core.termux.TermuxCommandRunner

private val STEPS = listOf(
    "Welcome",
    "Check Termux",
    "AI provider",
    "GitHub (optional)",
    "Ready",
)

@Composable
fun SetupWizardScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as ZotApp
    var step by remember { mutableIntStateOf(0) }
    var termuxOk by remember { mutableStateOf(false) }
    var apiOk by remember { mutableStateOf(false) }
    var providerChosen by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        LinearProgressIndicator(
            progress = { (step + 1f) / STEPS.size },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(STEPS[step], style = MaterialTheme.typography.headlineSmall)

        when (step) {
            0 -> {
                Text("ZotMobile puts an AI coding agent on your phone. " +
                    "It uses Termux to run commands, Git and GitHub for version control, " +
                    "and your choice of AI provider. No VPS or cloud server needed.")
                Text("Next, we check that Termux is installed.", style = MaterialTheme.typography.bodySmall)
            }
            1 -> {
                termuxOk = TermuxCommandRunner.isTermuxInstalled(context)
                apiOk = TermuxCommandRunner.isTermuxApiInstalled(context)
                Text(if (termuxOk) "✓ Termux is installed." else "✗ Termux is not installed.")
                Text(if (apiOk) "✓ Termux:API is installed." else "✗ Termux:API is not installed.")
                if (!termuxOk) {
                    Text("Termux runs the commands for your projects. Install it from F-Droid:")
                    Button(onClick = { context.startActivity(TermuxCommandRunner.termuxInstallIntent(context)) }) {
                        Text("Open F-Droid — Termux")
                    }
                }
                if (!apiOk) {
                    Text("Termux:API lets ZotMobile run commands inside Termux:")
                    Button(onClick = { context.startActivity(TermuxCommandRunner.termuxApiInstallIntent(context)) }) {
                        Text("Open F-Droid — Termux:API")
                    }
                }
                if (termuxOk && apiOk) {
                    Text("After first install, open Termux once and run:\n  pkg install git\nso Git is available.",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            2 -> {
                Text("Choose an AI provider. You need an API key from that provider — it stays on your device.")
                ProviderPickerInline(app = app, onChosen = { providerChosen = true })
            }
            3 -> {
                Text("Optional: connect GitHub to clone and push repositories.")
                GithubInline(app = app)
            }
            4 -> {
                Text("You're set. Open or clone a project from the Projects tab, then chat with the agent.")
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            if (step > 0) OutlinedButton(onClick = { step-- }) { Text("Back") }
            val canNext = when (step) {
                1 -> true // allow continue with fix hints
                2 -> providerChosen
                else -> true
            }
            Button(onClick = { if (step < STEPS.size - 1) step++ else onDone() }, enabled = canNext) {
                Text(if (step == STEPS.size - 1) "Start" else "Next")
            }
            if (step == 0) {
                TextButton(onClick = onDone) { Text("Skip setup") }
            }
        }
    }
}

@Composable
private fun ProviderPickerInline(app: ZotApp, onChosen: () -> Unit) {
    val providers = app.providerRegistry.providers()
    var selected by remember { mutableStateOf(app.secretStore.get("ai.provider") ?: "openai") }
    var key by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        providers.forEach { p ->
            TextButton(onClick = { selected = p.id }) {
                Text((if (selected == p.id) "● " else "○ ") + p.displayName)
            }
        }
        OutlinedTextField(
            value = key, onValueChange = { key = it },
            label = { Text("API key") },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
        )
        Button(onClick = {
            app.secretStore.put("ai.provider", selected)
            app.secretStore.put("ai.apiKey." + selected, key)
            onChosen()
        }) { Text("Save provider") }
    }
}

@Composable
private fun GithubInline(app: ZotApp) {
    var token by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value = token, onValueChange = { token = it },
            label = { Text("Personal access token (optional)") },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
        )
        Button(onClick = { app.secretStore.put("github.token", token) }) { Text("Save token") }
        TextButton(onClick = { }) { Text("Skip for now") }
    }
}
