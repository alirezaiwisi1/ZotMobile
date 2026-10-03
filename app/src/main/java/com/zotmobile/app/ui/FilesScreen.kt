package com.zotmobile.app.ui

import com.zotmobile.app.ZotApp

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun FilesScreen(app: ZotApp) {
    val vm: FilesViewModel = viewModel(factory = ZotViewModelFactory(app))
    val entries by vm.entries.collectAsState()
    val path by vm.path.collectAsState()
    val openFile by vm.openFile.collectAsState()
    val error by vm.error.collectAsState()
    val dirty by vm.dirty.collectAsState()
    val saved by vm.saved.collectAsState()

    LaunchedEffect(Unit) { vm.openProjectRoot() }

    val open = openFile
    if (open != null) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.closeFile() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                Text(open.first, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                if (dirty) {
                    IconButton(onClick = { vm.saveOpenFile() }) { Icon(Icons.Filled.Save, "Save") }
                }
            }
            if (saved) Text("Saved.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 12.dp))
            OutlinedTextField(
                value = open.second,
                onValueChange = { vm.editContent(it) },
                modifier = Modifier.fillMaxSize().padding(8.dp),
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            )
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.up() }, enabled = path.isNotBlank()) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Up") }
            Text(
                "/" + path,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            )
        }
        error?.let {
            Card(Modifier.padding(8.dp)) { Text(it, Modifier.padding(8.dp), color = MaterialTheme.colorScheme.error) }
        }
        LazyColumn(Modifier.weight(1f)) {
            items(entries, key = { it.path }) { e ->
                Row(
                    Modifier.fillMaxWidth().clickable {
                        if (e.isDir) vm.navigate(e.path) else vm.openFile(e.path)
                    }.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (e.isDir) Icons.Filled.Folder else Icons.Filled.Description,
                        contentDescription = null,
                        tint = if (e.isDir) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(e.name, Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
