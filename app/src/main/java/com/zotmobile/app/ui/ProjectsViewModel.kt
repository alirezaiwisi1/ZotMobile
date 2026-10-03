package com.zotmobile.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zotmobile.app.ZotApp
import com.zotmobile.core.git.CommitInfo
import com.zotmobile.core.git.GitStatus
import com.zotmobile.core.project.Project
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProjectRow(
    val project: Project,
    val branch: String = "",
    val modified: Int = 0,
    val lastCommit: CommitInfo? = null,
    val ahead: Int = 0,
    val behind: Int = 0,
)

class ProjectsViewModel(private val app: ZotApp) : ViewModel() {

    private val _projects = MutableStateFlow<List<ProjectRow>>(emptyList())
    val projects: StateFlow<List<ProjectRow>> = _projects.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            val rows = app.projectManager.listProjects().map { p ->
                val git = app.gitFor(p.dir)
                val st: GitStatus? = git.status().getOrNull()
                ProjectRow(
                    project = p,
                    branch = st?.branch ?: "",
                    modified = st?.modifiedCount ?: 0,
                    lastCommit = git.lastCommit().getOrNull(),
                    ahead = st?.ahead ?: 0,
                    behind = st?.behind ?: 0,
                )
            }
            _projects.value = rows
            _loading.value = false
        }
    }

    fun cloneRepo(fullName: String, cloneUrl: String) {
        viewModelScope.launch {
            _loading.value = true
            val r = app.projectManager.clone(fullName, cloneUrl, app.runner, app.gitHubClient)
            _message.value = r.fold(
                onSuccess = { "Cloned '" + it.name + "'." },
                onFailure = { it.message ?: "Clone failed." },
            )
            _loading.value = false
            refresh()
        }
    }

    fun createLocal(name: String) {
        viewModelScope.launch {
            val r = app.projectManager.createLocal(name, app.runner)
            _message.value = r.fold(
                onSuccess = { "Created '" + it.name + "'." },
                onFailure = { it.message ?: "Could not create the project." },
            )
            refresh()
        }
    }

    fun delete(p: Project) {
        viewModelScope.launch {
            val r = app.projectManager.delete(p)
            _message.value = r.fold(
                onSuccess = { "Deleted '" + p.name + "'." },
                onFailure = { it.message ?: "Could not delete the project." },
            )
            refresh()
        }
    }

    fun rename(p: Project, newName: String) {
        viewModelScope.launch {
            val r = app.projectManager.rename(p, newName)
            _message.value = r.fold(
                onSuccess = { "Renamed to '" + it.name + "'." },
                onFailure = { it.message ?: "Could not rename." },
            )
            refresh()
        }
    }

    fun open(p: Project) {
        app.currentWorkDir = p.dir.absolutePath
        _message.value = "Opened '" + p.name + "'. Go to the Agent tab."
    }

    fun pull(p: Project) {
        viewModelScope.launch {
            val r = app.gitFor(p.dir).pull()
            _message.value = r.fold(
                onSuccess = { "Pulled latest changes." },
                onFailure = { it.message ?: "Pull failed." },
            )
            refresh()
        }
    }

    fun push(p: Project) {
        viewModelScope.launch {
            val r = app.gitFor(p.dir).push()
            _message.value = r.fold(
                onSuccess = { "Pushed to GitHub." },
                onFailure = { it.message ?: "Push failed." },
            )
            refresh()
        }
    }

    fun clearMessage() { _message.value = null }
}
