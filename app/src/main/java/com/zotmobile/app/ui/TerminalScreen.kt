package com.zotmobile.app.ui

import com.zotmobile.app.ZotApp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zotmobile.core.model.CommandEvent

@Composable
fun TerminalScreen(app: ZotApp) {
    val vm: TerminalViewModel = viewModel(factory = ZotViewModelFactory(app))
    val entries by vm.entries.collectAsState()
    val needConfirm by vm.needConfirm.collectAsState()
    var input by remember { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("Run", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = { vm.stop() }) { Icon(Icons.Filled.Close, "Stop") }
            IconButton(onClick = { vm.clear() }) { Icon(Icons.Filled.Delete, "Clear") }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(entries) { e ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text(
                                "$ " + e.command,
                                style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { clipboard.setText(AnnotatedString(e.output)) }) {
                                Icon(Icons.Filled.ContentCopy, "Copy output", Modifier.padding(0.dp))
                            }
                        }
                        if (e.output.isNotBlank()) {
                            Text(
                                e.output.take(8000),
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            )
                        }
                        Text(
                            when (e.state) {
                                CommandEvent.State.RUNNING -> "… running"
                                CommandEvent.State.COMPLETED -> "✓ done  exit " + (e.exitCode ?: "?") + (e.durationMs?.let { "  " + (it / 1000) + "s" } ?: "")
                                CommandEvent.State.FAILED -> "✗ failed"
                                CommandEvent.State.STOPPED -> "■ stopped"
                                CommandEvent.State.NEEDS_CONFIRMATION -> "? needs approval"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = when (e.state) {
                                CommandEvent.State.COMPLETED -> MaterialTheme.colorScheme.primary
                                CommandEvent.State.FAILED -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = androidx.compose.ui.Alignment.Bottom) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("npm test, git status, …") },
                maxLines = 2,
                shape = RoundedCornerShape(12.dp),
            )
            IconButton(
                onClick = { vm.run(input.trim()); input = "" },
                enabled = input.isNotBlank(),
            ) { Icon(Icons.AutoMirrored.Filled.Send, "Run") }
        }
    }

    needConfirm?.let { cmd ->
        AlertDialog(
            onDismissRequest = { vm.cancelConfirm() },
            title = { Text("Confirm command") },
            text = { Text("This command can change or delete files:\n\n$ " + cmd) },
            confirmButton = { Button(onClick = { vm.confirmRun() }) { Text("Run it") } },
            dismissButton = { TextButton(onClick = { vm.cancelConfirm() }) { Text("Cancel") } },
        )
    }
}
