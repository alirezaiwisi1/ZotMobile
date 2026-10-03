package com.zot.mobile.ui.screens.files

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zot.mobile.ZotApplication
import com.zot.mobile.core.git.GitLayer
import com.zot.mobile.data.project.FileEntry
import com.zot.mobile.data.project.ProjectManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class FilesUiState(
    val projectId: String? = null,
    val path: String = "",
    val entries: List<FileEntry> = emptyList(),
    val loading: Boolean = false,
    val openFile: OpenFile? = null,
    val gitStatus: com.zot.mobile.core.git.GitStatus? = null,
    val gitLog: String = "",
    val diff: String = "",
    val error: String? = null,
    val showDiff: Boolean = false,
)

data class OpenFile(val path: String, val content: String, val editable: Boolean = true)

class FilesViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx = app as ZotApplication
    private val projects = ProjectManager(ctx.termuxClient, GitLayer(ctx.termuxClient, com.zot.mobile.core.termux.TermuxClient.PROJECTS_DIR), ctx.database.projectDao())
    private val git = GitLayer(ctx.termuxClient, com.zot.mobile.core.termux.TermuxClient.PROJECTS_DIR)

    private val _state = MutableStateFlow(FilesUiState())
    val state: StateFlow<FilesUiState> = _state

    val projectList = ctx.database.projectDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectProject(id: String) {
        _state.value = _state.value.copy(projectId = id, path = "", entries = emptyList(), openFile = null)
        viewModelScope.launch {
            projects.touch(id)
            val gs = git.status(id).getOrNull()
            _state.value = _state.value.copy(gitStatus = gs, gitLog = git.log(id).getOrNull() ?: "")
            browse(id, "")
        }
    }

    fun open(entry: FileEntry) {
        val pid = _state.value.projectId ?: return
        if (entry.isDirectory) browse(pid, entry.path) else loadFile(pid, entry.path)
    }

    fun navigateUp() {
        val s = _state.value
        val pid = s.projectId ?: return
        if (s.path.isBlank()) return
        browse(pid, s.path.substringBeforeLast('/', ""))
    }

    fun refresh() {
        val pid = _state.value.projectId ?: return
        viewModelScope.launch {
            browse(pid, _state.value.path)
            refreshGit(pid)
        }
    }

    fun closeFile() { _state.value = _state.value.copy(openFile = null) }

    fun saveFile(newContent: String) {
        val s = _state.value
        val pid = s.projectId ?: return
        val path = s.openFile?.path ?: return
        viewModelScope.launch {
            projects.writeFile(pid, path, newContent)
                .onSuccess { _state.value = s.copy(openFile = s.openFile?.copy(content = newContent)) }
                .onFailure { _state.value = s.copy(error = "Could not save file: ${it.message}") }
        }
    }

    fun deleteFile(path: String) {
        val pid = _state.value.projectId ?: return
        viewModelScope.launch {
            projects.deleteFile(pid, path)
            _state.value = _state.value.copy(openFile = null)
            refresh()
        }
    }

    fun cloneRepo(url: String, name: String) = viewModelScope.launch {
        _state.value = _state.value.copy(loading = true, error = null)
        projects.cloneRepository(url, name).fold(
            onSuccess = { _state.value = _state.value.copy(loading = false); selectProject(it.id) },
            onFailure = { e -> _state.value = _state.value.copy(loading = false, error = humanize(e.message ?: "Clone failed")) },
        )
    }

    fun createProject(name: String) = viewModelScope.launch {
        projects.createLocal(name).fold(
            onSuccess = { selectProject(it.id) },
            onFailure = { _state.value = _state.value.copy(error = humanize(it.message ?: "Creation failed")) },
        )
    }

    fun deleteProject(id: String) = viewModelScope.launch {
        _state.value = _state.value.copy(projectId = if (_state.value.projectId == id) null else _state.value.projectId)
        projects.delete(id)
    }

    fun pull() = viewModelScope.launch { val pid = _state.value.projectId ?: return@launch; git.pull(pid).fold(onSuccess = { refreshGit(pid) }, onFailure = { _state.value = _state.value.copy(error = humanize(it.message ?: "Pull failed")) }) }
    fun push() = viewModelScope.launch { val pid = _state.value.projectId ?: return@launch; git.push(pid).fold(onSuccess = { refreshGit(pid) }, onFailure = { _state.value = _state.value.copy(error = humanize(it.message ?: "Push failed")) }) }
    fun commit(message: String) = viewModelScope.launch { val pid = _state.value.projectId ?: return@launch; git.addAndCommit(pid, message).fold(onSuccess = { refreshGit(pid) }, onFailure = { _state.value = _state.value.copy(error = humanize(it.message ?: "Commit failed")) }) }

    fun showDiff() {
        val pid = _state.value.projectId ?: return
        viewModelScope.launch {
            git.diff(pid).fold(
                onSuccess = { _state.value = _state.value.copy(diff = it, showDiff = true) },
                onFailure = { _state.value = _state.value.copy(error = humanize(it.message ?: "")) },
            )
        }
    }
    fun hideDiff() { _state.value = _state.value.copy(showDiff = false) }
    fun dismissError() { _state.value = _state.value.copy(error = null) }

    private suspend fun refreshGit(pid: String) {
        _state.value = _state.value.copy(
            gitStatus = git.status(pid).getOrNull(),
            gitLog = git.log(pid).getOrNull() ?: "",
        )
    }

    private fun browse(pid: String, path: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            projects.listDirectory(pid, path).fold(
                onSuccess = { _state.value = _state.value.copy(loading = false, entries = it, path = path) },
                onFailure = { _state.value = _state.value.copy(loading = false, error = humanize(it.message ?: "Could not open folder")) },
            )
        }
    }

    private fun loadFile(pid: String, path: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            projects.readFile(pid, path).fold(
                onSuccess = { _state.value = _state.value.copy(loading = false, openFile = OpenFile(path, it)) },
                onFailure = { _state.value = _state.value.copy(loading = false, error = humanize(it.message ?: "Could not open file")) },
            )
        }
    }

    private fun humanize(msg: String) = when {
        msg.contains("command not found") -> "Git was not found in the Termux environment. Install Git and run the environment check again."
        msg.contains("Authentication failed") -> "GitHub authentication failed. Add a personal access token in Settings → GitHub."
        msg.contains("Could not reach the Termux") -> "Could not reach Termux. Open Termux once, then try again."
        else -> msg
    }
}
