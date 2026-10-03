package com.zotmobile.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zotmobile.app.ZotApp
import com.zotmobile.core.agent.AgentOrchestrator
import com.zotmobile.core.agent.PendingChange
import com.zotmobile.core.ai.AiEvent
import com.zotmobile.core.ai.ProviderConfig
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** One row in the agent chat transcript. */
sealed interface ChatRow {
    data class User(val text: String) : ChatRow
    data class Assistant(val text: String) : ChatRow
    data class Status(val text: String) : ChatRow
    data class WriteConfirm(val change: PendingChange) : ChatRow
    data class CommandConfirm(val command: String) : ChatRow
    data class ErrorRow(val text: String) : ChatRow
}

class ChatViewModel(app: ZotApp) : ViewModel() {

    private val secretStore = app.secretStore
    private val projectManager = app.projectManager

    private val _rows = MutableStateFlow<List<ChatRow>>(emptyList())
    val rows: StateFlow<List<ChatRow>> = _rows.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _activeProject = MutableStateFlow<String?>(null)
    val activeProject: StateFlow<String?> = _activeProject.asStateFlow()

    private var orchestrator: AgentOrchestrator? = null

    fun openProject(name: String, app: ZotApp) {
        val p = projectManager.get(name) ?: return
        app.currentWorkDir = p.dir.absolutePath
        _activeProject.value = name
        orchestrator = null // rebuild lazily with new root
        _rows.value = listOf(ChatRow.Status("Opened project '" + name + "'. What should the agent do?"))
    }

    fun send(text: String, app: ZotApp) {
        if (text.isBlank() || _busy.value) return
        val projectName = _activeProject.value ?: run {
            _rows.value += ChatRow.ErrorRow("Open a project first (Projects tab).")
            return
        }
        val project = projectManager.get(projectName) ?: return
        val providerId = secretStore.get("ai.provider") ?: "openai"
        val info = app.providerRegistry.providers().firstOrNull { it.id == providerId }
            ?: app.providerRegistry.providers().first()
        val apiKey = secretStore.get(SecretStore.keyForProvider(providerId)) ?: ""
        if (info.needsApiKey && apiKey.isBlank()) {
            _rows.value += ChatRow.ErrorRow("No API key set for " + info.displayName + ". Add one in Settings. (File editing works offline; the agent needs internet.)")
            return
        }
        val config = ProviderConfig(
            providerId = info.id,
            apiKey = apiKey,
            model = secretStore.get("ai.model." + providerId)?.takeIf { it.isNotBlank() } ?: info.defaultModel,
            baseUrl = secretStore.get("ai.baseUrl." + providerId)?.takeIf { it.isNotBlank() } ?: info.defaultBaseUrl,
        )
        val orch = orchestrator ?: AgentOrchestrator(
            provider = app.providerRegistry.create(info.id),
            tools = app.agentToolsFor(project.dir),
            config = config,
        ).also { orchestrator = it }

        _rows.value += ChatRow.User(text)
        _busy.value = true
        viewModelScope.launch {
            orch.send(text).collect { ev ->
                when (ev) {
                    is AgentOrchestrator.UiEvent.WriteProposal -> _rows.value += ChatRow.WriteConfirm(ev.change)
                    is AgentOrchestrator.UiEvent.CommandProposal -> _rows.value += ChatRow.CommandConfirm(ev.command)
                    is AgentOrchestrator.UiEvent.CommandResult -> _rows.value += ChatRow.Status("$ " + ev.command + "\n" + ev.output.take(2000))
                    is AgentOrchestrator.UiEvent.Finished -> _rows.value += ChatRow.Assistant(ev.text)
                    is AgentOrchestrator.UiEvent.Error -> _rows.value += ChatRow.ErrorRow(ev.message)
                    else -> {}
                }
            }
            _busy.value = false
        }
    }

    fun confirm(app: ZotApp, accepted: Boolean) {
        val orch = orchestrator ?: return
        _busy.value = true
        viewModelScope.launch {
            orch.confirmProposal(accepted).collect { ev ->
                when (ev) {
                    is AgentOrchestrator.UiEvent.WriteProposal -> _rows.value += ChatRow.WriteConfirm(ev.change)
                    is AgentOrchestrator.UiEvent.CommandProposal -> _rows.value += ChatRow.CommandConfirm(ev.command)
                    is AgentOrchestrator.UiEvent.CommandResult -> _rows.value += ChatRow.Status("$ " + ev.command + "\n" + ev.output.take(2000))
                    is AgentOrchestrator.UiEvent.Finished -> _rows.value += ChatRow.Assistant(ev.text)
                    is AgentOrchestrator.UiEvent.Error -> _rows.value += ChatRow.ErrorRow(ev.message)
                    else -> {}
                }
            }
            _busy.value = false
        }
    }

    fun clear() { _rows.value = emptyList(); orchestrator?.resetConversation() }
}

object SecretStore {
    // Mirror of core-security keys, kept tiny for the UI layer.
    fun keyForProvider(id: String): String = "ai.apiKey." + id
    const val GITHUB = "github.token"
}
