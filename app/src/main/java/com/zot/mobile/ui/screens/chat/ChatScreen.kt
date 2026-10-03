package com.zot.mobile.ui.screens.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun ChatScreen(vm: ChatViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val projects by vm.activeProject.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(state.steps.size) {
        if (state.steps.isNotEmpty()) listState.animateScrollToItem(state.steps.size - 1)
    }

    Column(Modifier.fillMaxSize()) {
        // Project selector row
        if (projects.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Project:", style = MaterialTheme.typography.labelMedium)
                projects.forEach { p ->
                    FilterChip(
                        selected = state.projectId == p.id,
                        onClick = { vm.selectProject(p.id) },
                        label = { Text(p.name, style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }
        } else {
            Text(
                "No projects yet — open the Files tab to clone a repository or create one.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(12.dp),
            )
        }

        HorizontalDivider()

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.steps) { step ->
                AgentBubble(step)
            }
            items(state.pendingPatches.filter { !it.applied && !it.rejected }) { patch ->
                PatchCard(patch, onApply = { vm.applyPatch(patch) }, onReject = { vm.rejectPatch(patch) })
            }
            items(state.pendingPatches.filter { it.applied }) { patch ->
                AppliedPatchCard(patch, onRevert = { vm.revertPatch(patch) })
            }
        }

        state.error?.let { err ->
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                onClick = { vm.dismissError() },
            ) {
                Text(err, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(10.dp))
            }
        }

        // Input bar
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            OutlinedTextField(
                value = state.input,
                onValueChange = vm::setInput,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ask the agent to change your project…") },
                maxLines = 5,
                enabled = !state.busy,
            )
            FilledIconButton(onClick = vm::send, enabled = !state.busy && state.input.isNotBlank()) {
                if (state.busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Icon(Icons.AutoMirrored.Filled.Send, "Send")
            }
        }
    }
}

@Composable
private fun AgentBubble(step: AgentStep) {
    val isUser = !step.isResult && step.text.lines().firstOrNull()?.startsWith("[") != true &&
        step.text.length < 400 && !step.text.contains("```")
    Surface(
        color = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        SelectionContainer {
            Text(
                step.text,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(10.dp),
            )
        }
    }
}

@Composable
private fun PatchCard(patch: PendingPatch, onApply: () -> Unit, onReject: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp)) {
            Text("Proposed change: ${patch.path}", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                patch.diff.take(1500),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                maxLines = 10,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onApply) {
                    Icon(Icons.Default.Check, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Accept")
                }
                OutlinedButton(onClick = onReject) {
                    Icon(Icons.Default.Close, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Reject")
                }
            }
        }
    }
}

@Composable
private fun AppliedPatchCard(patch: PendingPatch, onRevert: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Check, null, Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Applied: ${patch.path}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onRevert) {
                Icon(Icons.Default.Undo, null, Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text("Revert")
            }
        }
    }
}
