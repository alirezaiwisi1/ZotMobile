package com.zot.mobile.ui.screens.terminal

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun TerminalScreen(vm: TerminalViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val projects by vm.projectList.collectAsState()
    val clipboard = LocalClipboardManager.current

    Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // project selector
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            projects.forEach { p ->
                FilterChip(
                    selected = state.projectId == p.id,
                    onClick = { vm.selectProject(p.id) },
                    label = { Text(p.name, style = MaterialTheme.typography.labelSmall) },
                )
            }
            if (projects.isEmpty()) Text("No projects yet.", style = MaterialTheme.typography.bodySmall)
        }

        // output console
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            SelectionContainer {
                Text(
                    state.output.ifBlank { "Output appears here.\nCommands run inside the Termux environment on this device." },
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.padding(10.dp),
                )
            }
        }

        state.lastExitCode?.let { code ->
            Text(
                if (code == 0) "✓ Completed · ${state.lastDurationMs}ms" else "✗ Exit code: $code · ${state.lastDurationMs}ms",
                style = MaterialTheme.typography.labelSmall,
                color = if (code == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        }

        state.error?.let { err ->
            Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
                Text(err, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(10.dp))
            }
        }

        state.needsConfirmation?.let { cmd ->
            AlertDialog(
                onDismissRequest = vm::dismissConfirmation,
                title = { Text("Dangerous command") },
                text = { Text("This command can delete or damage data:\n\n$cmd\n\nRun it anyway?") },
                confirmButton = { Button(onClick = vm::run) { Text("Run anyway") } },
                dismissButton = { TextButton(onClick = vm::dismissConfirmation) { Text("Cancel") } },
            )
        }

        // command input + controls
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedTextField(
                value = state.command,
                onValueChange = vm::setCommand,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Command, e.g. npm test") },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall,
            )
            IconButton(onClick = { clipboard.setText(AnnotatedString(state.output)) }) { Icon(Icons.Default.Clear, "Copy") }
            FilledIconButton(onClick = vm::run, enabled = !state.running) {
                if (state.running) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                else Icon(Icons.AutoMirrored.Filled.Send, "Run")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("npm install", "npm test", "npm run build", "./gradlew build").forEach { preset ->
                AssistChip(onClick = { vm.setCommand(preset) }, label = { Text(preset, style = MaterialTheme.typography.labelSmall) })
            }
        }
    }
}
