package com.zotmobile.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zotmobile.app.ZotApp
import com.zotmobile.core.model.CommandEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RunEntry(
    val command: String,
    val state: CommandEvent.State,
    var output: String = "",
    val exitCode: Int? = null,
    val durationMs: Long? = null,
)

class TerminalViewModel(private val app: ZotApp) : ViewModel() {

    private val _entries = MutableStateFlow<List<RunEntry>>(emptyList())
    val entries: StateFlow<List<RunEntry>> = _entries.asStateFlow()

    private val _needConfirm = MutableStateFlow<String?>(null)
    val needConfirm: StateFlow<String?> = _needConfirm.asStateFlow()

    private var currentSession: String? = null

    init {
        app.runner.addListener { e -> onEvent(e) }
    }

    private fun onEvent(e: CommandEvent) {
        viewModelScope.launch {
            val list = _entries.value.toMutableList()
            val existing = list.lastOrNull { it.command == e.command && it.state == CommandEvent.State.RUNNING }
            when {
                e.state == CommandEvent.State.NEEDS_CONFIRMATION -> {
                    _needConfirm.value = e.command
                    if (existing == null && list.none { it.command == e.command }) {
                        list.add(RunEntry(e.command, CommandEvent.State.RUNNING))
                    }
                }
                existing != null -> {
                    if (e.partial) existing.output += e.stdout
                    else {
                        existing.output += (e.stdout + e.stderr)
                        list[list.indexOf(existing)] = existing.copy(
                            state = e.state,
                            exitCode = e.exitCode,
                            durationMs = e.durationMs,
                        )
                        currentSession = null
                    }
                }
                e.state == CommandEvent.State.RUNNING && e.partial -> {
                    // late partial for an entry already terminal — ignore
                }
                e.state == CommandEvent.State.FAILED -> list.add(
                    RunEntry(e.command, e.state, e.stderr + e.stdout, e.exitCode, e.durationMs),
                )
                else -> list.add(
                    RunEntry(e.command, e.state, e.stdout + e.stderr, e.exitCode, e.durationMs),
                )
            }
            _entries.value = list
        }
    }

    fun run(command: String) {
        if (command.isBlank()) return
        currentSession = app.runner.run(command, workDir = app.currentWorkDir)
        _entries.value += RunEntry(command, CommandEvent.State.RUNNING)
    }

    fun confirmRun() {
        val cmd = _needConfirm.value ?: return
        _needConfirm.value = null
        currentSession = app.runner.runConfirmed(cmd, workDir = app.currentWorkDir)
    }

    fun cancelConfirm() { _needConfirm.value = null }

    fun stop() {
        currentSession?.let { app.runner.stop(it) }
    }

    fun clear() { _entries.value = emptyList() }
}
