package com.zotmobile.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zotmobile.app.ZotApp
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class FileEntry(val name: String, val path: String, val isDir: Boolean)

class FilesViewModel(private val app: ZotApp) : ViewModel() {

    private val _entries = MutableStateFlow<List<FileEntry>>(emptyList())
    val entries: StateFlow<List<FileEntry>> = _entries.asStateFlow()

    private val _path = MutableStateFlow("")
    val path: StateFlow<String> = _path.asStateFlow()

    private val _openFile = MutableStateFlow<Pair<String, String>?>(null) // rel path, content
    val openFile: StateFlow<Pair<String, String>?> = _openFile.asStateFlow()

    private val _dirty = MutableStateFlow(false)
    val dirty: StateFlow<Boolean> = _dirty.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    private var root: File? = null

    fun openProjectRoot() {
        val dir = File(app.currentWorkDir)
        if (!dir.isDirectory) { _error.value = "Open a project first (Projects tab)."; return }
        root = dir
        _path.value = ""
        listDir("")
        _error.value = null
    }

    fun navigate(rel: String) {
        if (root == null) { openProjectRoot(); if (root == null) return }
        _path.value = rel
        listDir(rel)
    }

    fun up() {
        val cur = _path.value
        if (cur.isBlank()) return
        val parent = cur.substringBeforeLast('/', "")
        navigate(parent)
    }

    private fun listDir(rel: String) {
        val r = root ?: return
        val dir = if (rel.isBlank()) r else File(r, rel)
        viewModelScope.launch(Dispatchers.IO) {
            val list = dir.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })).orEmpty()
                .filter { it.name != ".git" }
                .map { FileEntry(it.name, if (rel.isBlank()) it.name else rel + "/" + it.name, it.isDirectory) }
            withContext(Dispatchers.Main) { _entries.value = list }
        }
    }

    fun openFile(rel: String) {
        val r = root ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val f = File(r, rel)
            if (!f.isFile) return@launch
            if (f.length() > 400_000) { withContext(Dispatchers.Main) { _error.value = "File is too large to preview on a phone." } ; return@launch }
            val text = try { f.readText(Charsets.UTF_8) } catch (_: Exception) { null }
            withContext(Dispatchers.Main) {
                if (text == null) _error.value = "This file is binary and cannot be opened in the editor."
                else { _openFile.value = rel to text; _dirty.value = false; _saved.value = false }
            }
        }
    }

    fun editContent(text: String) {
        _openFile.value = _openFile.value?.copy(second = text)
        _dirty.value = true
        _saved.value = false
    }

    fun saveOpenFile() {
        val rel = _openFile.value?.first ?: return
        val content = _openFile.value?.second ?: return
        val r = root ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                File(r, rel).writeText(content, Charsets.UTF_8)
                withContext(Dispatchers.Main) { _saved.value = true; _dirty.value = false }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { _error.value = "Could not save: " + e.message }
            }
        }
    }

    fun closeFile() { _openFile.value = null; _dirty.value = false }

    fun clearError() { _error.value = null }
}
