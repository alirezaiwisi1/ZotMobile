package com.zotmobile.app.ui

import com.zotmobile.app.ZotApp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
fun ProjectsScreen(app: ZotApp) {
    val vm: ProjectsViewModel = viewModel(factory = ZotViewModelFactory(app))
    val projects by vm.projects.collectAsState()
    val loading by vm.loading.collectAsState()
    val message by vm.message.collectAsState()
    var showClone by remember { mutableStateOf(false) }
    var showCreate by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Projects", style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { showClone = true }) { Text("Clone from GitHub") }
            OutlinedButton(onClick = { showCreate = true }) { Text("New empty project") }
        }
        message?.let {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Text(it, Modifier.padding(10.dp), style = MaterialTheme.typography.bodySmall)
            }
        }
        if (loading) CircularProgressIndicator()
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(projects, key = { it.project.id }) { row ->
                ProjectCard(row,
                    onOpen = { vm.open(row.project) },
                    onPull = { vm.pull(row.project) },
                    onPush = { vm.push(row.project) },
                    onDelete = { vm.delete(row.project) },
                )
            }
        }
    }

    if (showClone) CloneDialog(
        onDismiss = { showClone = false },
        onClone = { full, url -> showClone = false; vm.cloneRepo(full, url) },
    )
    if (showCreate) TextDialog(
        title = "New project",
        placeholder = "my-project",
        onDismiss = { showCreate = false },
        onOk = { showCreate = false; vm.createLocal(it) },
    )
}

@Composable
private fun ProjectCard(
    row: ProjectRow,
    onOpen: () -> Unit,
    onPull: () -> Unit,
    onPush: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(row.project.name, style = MaterialTheme.typography.titleMedium)
            Text(
                buildString {
                    append(if (row.branch.isBlank()) "no branch yet" else row.branch)
                    if (row.ahead > 0) append("  ↑").append(row.ahead)
                    if (row.behind > 0) append("  ↓").append(row.behind)
                },
                style = MaterialTheme.typography.bodySmall,
            )
            if (row.modified > 0) Text(row.modified.toString() + " modified files", style = MaterialTheme.typography.bodySmall)
            row.lastCommit?.let {
                Text(it.hash + " " + it.subject + " (" + it.relativeTime() + ")", style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = onOpen) { Text("Open") }
                TextButton(onClick = onPull) { Text("Pull") }
                TextButton(onClick = onPush) { Text("Push") }
                TextButton(onClick = onDelete) { Text("Delete") }
            }
        }
    }
}

@Composable
private fun CloneDialog(onDismiss: () -> Unit, onClone: (String, String) -> Unit) {
    var repo by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Clone from GitHub") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = repo, onValueChange = { repo = it }, label = { Text("owner/repository") })
                OutlinedTextField(
                    value = url, onValueChange = { url = it },
                    label = { Text("Clone URL (https://…)") },
                    placeholder = { Text("https://github.com/owner/repo.git") },
                )
                Text(
                    "For private repos set a GitHub token in Settings first.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val u = url.ifBlank { "https://github.com/" + repo + ".git" }
                val name = repo.substringAfter('/').removeSuffix(".git")
                onClone(name, u)
            }) { Text("Clone") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun TextDialog(title: String, placeholder: String, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var value by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = value, onValueChange = { value = it }, placeholder = { Text(placeholder) }) },
        confirmButton = { Button(onClick = { if (value.isNotBlank()) onOk(value.trim()) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
