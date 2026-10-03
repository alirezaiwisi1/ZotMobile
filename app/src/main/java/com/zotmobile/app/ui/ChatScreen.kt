package com.zotmobile.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zotmobile.app.ZotApp
import com.zotmobile.core.agent.PendingChange

@Composable
fun ChatScreen(app: ZotApp) {
    val vm: ChatViewModel = viewModel(factory = ZotViewModelFactory(app))
    val rows by vm.rows.collectAsState()
    val busy by vm.busy.collectAsState()
    val active by vm.activeProject.collectAsState()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(rows.size) { if (rows.isNotEmpty()) listState.animateScrollToItem(rows.size - 1) }

    Column(Modifier.fillMaxSize().imePadding()) {
        Text(
            text = active ?: "No project open",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(rows) { row -> ChatRowView(row, onConfirm = { acc -> vm.confirm(app, acc) }) }
        }
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ask the agent to change your project…") },
                maxLines = 4,
                shape = RoundedCornerShape(14.dp),
            )
            IconButton(
                onClick = {
                    vm.send(input, app)
                    input = ""
                },
                enabled = !busy && input.isNotBlank(),
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
            }
        }
    }
}

@Composable
private fun ChatRowView(row: ChatRow, onConfirm: (Boolean) -> Unit) {
    when (row) {
        is ChatRow.User -> Bubble(text = row.text, alignEnd = true)
        is ChatRow.Assistant -> Bubble(text = row.text, alignEnd = false)
        is ChatRow.Status -> Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                row.text,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(10.dp),
            )
        }
        is ChatRow.ErrorRow -> Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(row.text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(10.dp))
        }
        is ChatRow.WriteConfirm -> ConfirmCard(
            title = (if (row.change.isDelete) "Delete file " else if (row.change.isCreate) "Create file " else "Modify file ") + row.change.path,
            body = DiffPreview.diffSummary(row.change),
            onConfirm = onConfirm,
        )
        is ChatRow.CommandConfirm -> ConfirmCard(
            title = "Run command",
            body = "$ " + row.command,
            onConfirm = onConfirm,
        )
    }
}

@Composable
private fun Bubble(text: String, alignEnd: Boolean) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start,
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (alignEnd) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant,
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(0.85f),
        ) {
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (alignEnd) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(12.dp),
            )
        }
    }
}

@Composable
private fun ConfirmCard(title: String, body: String, onConfirm: (Boolean) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall, maxLines = 14)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onConfirm(true) }) { Text("Approve") }
                OutlinedButton(onClick = { onConfirm(false) }) { Text("Cancel") }
            }
        }
    }
}

/** Local diff computation for pending changes (no git needed). */
object DiffPreview {
    fun diffSummary(change: PendingChange): String {
        if (change.failed) return change.failureReason
        val old = change.oldContent.lines().toSet()
        val new = change.newContent.lines().toSet()
        val removed = change.oldContent.lines().filter { it !in new }.take(8)
        val added = change.newContent.lines().filter { it !in old }.take(8)
        val sb = StringBuilder()
        if (change.isDelete) sb.append("(whole file deleted)\n")
        if (removed.isNotEmpty()) sb.append("- ").append(removed.joinToString("\n- ")).append('\n')
        if (added.isNotEmpty()) sb.append("+ ").append(added.joinToString("\n+ "))
        return sb.toString().ifBlank { "(no textual changes)" }
    }
}
