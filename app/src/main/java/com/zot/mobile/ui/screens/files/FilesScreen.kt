package com.zot.mobile.ui.screens.files

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun FilesScreen(vm: FilesViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val projects by vm.projectList.collectAsState()
    var showCloneDialog by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showCommitDialog by remember { mutableStateOf(false) }

    when {
        state.openFile != null -> FileEditorView(
            file = state.openFile!!,
            onSave = vm::saveFile,
            onClose = vm::closeFile,
            onDelete = { vm.deleteFile(state.openFile!!.path) },
        )
        state.projectId == null -> ProjectListPane(
            projects = projects.map { it.name },
            onSelect = vm::selectProject,
            onClone = { showCloneDialog = true },
            onCreate = { showCreateDialog = true },
            loading = state.loading,
            error = state.error,
            onDismissError = vm::dismissError,
        )
        else -> ProjectBrowserPane(
            state = state,
            onOpen = vm::open,
            onUp = vm::navigateUp,
            onRefresh = vm::refresh,
            onPull = vm::pull,
            onPush = vm::push,
            onDiff = vm::showDiff,
            onHideDiff = vm::hideDiff,
            onCommit = { showCommitDialog = true },
            onDismissError = vm::dismissError,
        )
    }

    if (showCloneDialog) {
        CloneDialog(
            onConfirm = { url, name -> vm.cloneRepo(url, name); showCloneDialog = false },
            onDismiss = { showCloneDialog = false },
        )
    }
    if (showCreateDialog) {
        NameDialog(
            title = "New project",
            label = "Project name",
            onConfirm = { vm.createProject(it); showCreateDialog = false },
            onDismiss = { showCreateDialog = false },
        )
    }
    if (showCommitDialog) {
        NameDialog(
            title = "Commit changes",
            label = "Commit message",
            onConfirm = { vm.commit(it); showCommitDialog = false },
            onDismiss = { showCommitDialog = false },
        )
    }
}

@Composable
private fun ProjectListPane(
    projects: List<String>,
    onSelect: (String) -> Unit,
    onClone: () -> Unit,
    onCreate: () -> Unit,
    loading: Boolean,
    error: String?,
    onDismissError: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Projects", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = onClone) { Icon(Icons.Default.Add, null, Modifier.size(16.dp)); Text(" Clone repo") }
            OutlinedButton(onClick = onCreate) { Text("New project") }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let {
            ErrorCard(it, onDismissError)
        }
        if (projects.isEmpty()) Text("No projects yet. Clone a GitHub repository or create an empty one.", style = MaterialTheme.typography.bodySmall)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(projects) { p ->
                Card(Modifier.fillMaxWidth().clickable { onSelect(p) }) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Folder, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(p, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectBrowserPane(
    state: FilesUiState,
    onOpen: (com.zot.mobile.data.project.FileEntry) -> Unit,
    onUp: () -> Unit,
    onRefresh: () -> Unit,
    onPull: () -> Unit,
    onPush: () -> Unit,
    onDiff: () -> Unit,
    onHideDiff: () -> Unit,
    onCommit: () -> Unit,
    onDismissError: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        // Git summary bar
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AssistChip(onClick = onUp, label = { Text("← /${state.path}") })
            state.gitStatus?.let { gs ->
                Text(
                    "⎇ ${gs.branch} · ${gs.changes.size} changed",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilledTonalIconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, "Refresh") }
            FilledTonalIconButton(onClick = onPull) { Text("⬇", style = MaterialTheme.typography.labelLarge) }
            FilledTonalIconButton(onClick = onPush) { Text("⬆", style = MaterialTheme.typography.labelLarge) }
            FilledTonalIconButton(onClick = onDiff) { Text("±", style = MaterialTheme.typography.labelLarge) }
            Button(onClick = onCommit, enabled = (state.gitStatus?.changes?.size ?: 0) > 0) { Text("Commit") }
        }

        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 6.dp))
        state.error?.let { ErrorCard(it, onDismissError, Modifier.padding(12.dp)) }

        if (state.showDiff) {
            with(this@Column) { DiffViewer(state.diff, onHideDiff) }
        } else {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(12.dp)) {
                if (state.path.isNotBlank()) {
                    item {
                        ListItem(
                            headlineContent = { Text("..") },
                            leadingContent = { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) },
                            modifier = Modifier.clickable { onUp() },
                        )
                    }
                }
                items(state.entries) { e ->
                    ListItem(
                        headlineContent = { Text(e.name, style = MaterialTheme.typography.bodyMedium) },
                        leadingContent = {
                            Icon(
                                if (e.isDirectory) Icons.Default.Folder else Icons.Default.Description,
                                null, Modifier.size(18.dp),
                            )
                        },
                        modifier = Modifier.clickable { onOpen(e) },
                    )
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.DiffViewer(diff: String, onClose: () -> Unit) {
    Column(Modifier.weight(1f).fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Changes", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close") }
        }
        SelectionContainer {
            Text(
                diff.take(20_000),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            )
        }
    }
}

@Composable
private fun FileEditorView(
    file: OpenFile,
    onSave: (String) -> Unit,
    onClose: () -> Unit,
    onDelete: () -> Unit,
) {
    var text by rememberSaveable(file.path) { mutableStateOf(file.content) }
    var dirty by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close") }
            Text(file.path, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f), maxLines = 1)
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Delete") }
            FilledTonalIconButton(onClick = { onSave(text); dirty = false }, enabled = dirty) {
                Icon(Icons.Default.Save, "Save")
            }
        }
        HorizontalDivider()
        OutlinedTextField(
            value = text,
            onValueChange = { text = it; dirty = true },
            modifier = Modifier.fillMaxSize().padding(8.dp),
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        )
    }
}

@Composable
private fun CloneDialog(onConfirm: (String, String) -> Unit, onDismiss: () -> Unit) {
    var url by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Clone from GitHub") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(url, { url = it }, label = { Text("Repository URL") }, singleLine = true)
                OutlinedTextField(name, { name = it }, label = { Text("Project name (optional)") }, singleLine = true)
            }
        },
        confirmButton = { Button(onClick = { onConfirm(url, name) }, enabled = url.isNotBlank()) { Text("Clone") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun NameDialog(title: String, label: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var value by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value, { value = it }, label = { Text(label) }, singleLine = true) },
        confirmButton = { Button(onClick = { onConfirm(value) }, enabled = value.isNotBlank()) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ErrorCard(message: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.small, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.Top) {
            Text(message, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            IconButton(onClick = onDismiss, Modifier.size(24.dp)) { Icon(Icons.Default.Close, null, Modifier.size(14.dp)) }
        }
    }
}
