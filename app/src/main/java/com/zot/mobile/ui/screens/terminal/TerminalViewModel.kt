package com.zot.mobile.ui.screens.terminal

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zot.mobile.ZotApplication
import com.zot.mobile.core.git.GitLayer
import com.zot.mobile.core.termux.TermuxClient
import com.zot.mobile.data.project.ProjectManager
import com.zot.mobile.data.db.CommandLogEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TerminalUiState(
    val projectId: String? = null,
    val command: String = "",
    val running: Boolean = false,
    val output: String = "",
    val lastExitCode: Int? = null,
    val lastDurationMs: Long = 0,
    val needsConfirmation: String? = null,
    val error: String? = null,
)

class TerminalViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx = app as ZotApplication
    private val projects = ProjectManager(ctx.termuxClient, GitLayer(ctx.termuxClient, TermuxClient.PROJECTS_DIR), ctx.database.projectDao())
    private val termux = ctx.termuxClient

    private val _state = MutableStateFlow(TerminalUiState())
    val state: StateFlow<TerminalUiState> = _state

    val projectList = ctx.database.projectDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val log = ctx.database.commandLogDao().observeRecent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectProject(id: String) { _state.value = _state.value.copy(projectId = id) }
    fun setCommand(v: String) { _state.value = _state.value.copy(command = v) }
    fun clear() { _state.value = _state.value.copy(output = "", lastExitCode = null, needsConfirmation = null) }
    fun dismissConfirmation() { _state.value = _state.value.copy(needsConfirmation = null) }
    fun dismissError() { _state.value = _state.value.copy(error = null) }

    fun run() {
        val s = _state.value
        val cmd = s.command.trim()
        if (cmd.isEmpty() || s.running) return
        val pid = s.projectId
        if (pid == null) { _state.value = s.copy(error = "Select a project first."); return }

        // Safety gate: destructive commands need explicit confirmation
        if (termux.isDestructive(cmd) && s.needsConfirmation == null) {
            _state.value = s.copy(needsConfirmation = cmd)
            return
        }

        viewModelScope.launch {
            _state.value = s.copy(running = true, needsConfirmation = null)
            val result = projects.runCommand(pid, cmd)
            val humanExit = if (result.exitCode != 0) humanizeFailure(cmd, result) else null
            _state.value = _state.value.copy(
                running = false,
                output = "\$ $cmd\n${result.stdout}\n✓ Completed · exit ${result.exitCode} · ${result.durationMs}ms",
                lastExitCode = result.exitCode,
                lastDurationMs = result.durationMs,
                error = humanExit,
            )
            ctx.database.commandLogDao().insert(
                CommandLogEntity(
                    projectId = pid, command = cmd, exitCode = result.exitCode,
                    output = result.stdout.take(10_000), durationMs = result.durationMs,
                    timestamp = System.currentTimeMillis(),
                ),
            )
        }
    }

    private fun humanizeFailure(cmd: String, r: com.zot.mobile.core.termux.TermuxResult): String? = when {
        r.stdout.contains("command not found") && cmd.startsWith("npm") ->
            "npm is not installed in Termux. Run: pkg install nodejs"
        r.stdout.contains("command not found") && cmd.startsWith("git") ->
            "Git was not found in the Termux environment. Install Git and run the environment check again."
        r.stdout.contains("command not found") && cmd.startsWith("python") ->
            "Python is not installed in Termux. Run: pkg install python"
        r.stdout.contains("EACCES") || r.stdout.contains("Permission denied") ->
            "Permission denied. Check that the file/folder belongs to the Termux environment."
        r.exitCode == 127 -> "Command not found in the Termux environment. Check the tool is installed (see Settings → Environment)."
        else -> null
    }
}
