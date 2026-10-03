package com.zot.mobile.ui.screens.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zot.mobile.ZotApplication
import com.zot.mobile.core.ai.AiClient
import com.zot.mobile.core.ai.AiProviderCatalog
import com.zot.mobile.core.ai.ChatMessage
import com.zot.mobile.core.git.GitLayer
import com.zot.mobile.data.db.ChatMessageEntity
import com.zot.mobile.data.project.ProjectManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PendingPatch(val path: String, val diff: String, val applied: Boolean = false, val rejected: Boolean = false)
data class AgentStep(val text: String, val isResult: Boolean = false)

data class ChatUiState(
    val projectId: String? = null,
    val input: String = "",
    val busy: Boolean = false,
    val steps: List<AgentStep> = emptyList(),
    val pendingPatches: List<PendingPatch> = emptyList(),
    val error: String? = null,
    val requiresNetwork: Boolean = false,
)

class ChatViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx = app as ZotApplication
    private val projectsRoot = com.zot.mobile.core.termux.TermuxClient.PROJECTS_DIR
    private val projectManager = ProjectManager(ctx.termuxClient, GitLayer(ctx.termuxClient, projectsRoot), ctx.database.projectDao())
    private val git = GitLayer(ctx.termuxClient, projectsRoot)

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state

    val messages = ctx.database.chatDao().observeForProject(_state.value.projectId ?: "")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeProject = ctx.database.projectDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectProject(id: String) {
        _state.value = _state.value.copy(projectId = id, steps = emptyList(), pendingPatches = emptyList())
        viewModelScope.launch { projectManager.touch(id) }
    }

    fun setInput(v: String) { _state.value = _state.value.copy(input = v) }

    fun send() {
        val s = _state.value
        val prompt = s.input.trim()
        val projectId = s.projectId ?: run {
            _state.value = s.copy(error = "Select a project first (Files tab → choose project).")
            return
        }
        if (prompt.isEmpty() || s.busy) return

        viewModelScope.launch {
            _state.value = s.copy(input = "", busy = true, error = null, steps = s.steps + AgentStep(prompt))
            try {
                // Gather lightweight project context for the model
                val context = buildContext(projectId)
                val history = chatHistory(projectId)
                val reply = ctx.aiClient.chat(
                    providerId = providerId(),
                    model = modelFor(),
                    messages = history + ChatMessage("user", "$context\n\n$prompt"),
                    projectRoot = projectManager.projectsDir,
                )
                reply.fold(
                    onSuccess = { text ->
                        ctx.database.chatDao().insert(ChatMessageEntity(projectId = projectId, role = "user", content = prompt, timestamp = System.currentTimeMillis()))
                        ctx.database.chatDao().insert(ChatMessageEntity(projectId = projectId, role = "assistant", content = text, timestamp = System.currentTimeMillis()))
                        val patches = parsePatches(text)
                        _state.value = _state.value.copy(
                            busy = false,
                            steps = _state.value.steps + AgentStep(text),
                            pendingPatches = _state.value.pendingPatches + patches,
                        )
                    },
                    onFailure = { e ->
                        _state.value = _state.value.copy(busy = false, error = humanizeAiError(e.message ?: "Unknown error"))
                    },
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(busy = false, error = humanizeAiError(e.message ?: e.toString()))
            }
        }
    }

    fun applyPatch(patch: PendingPatch) {
        val projectId = _state.value.projectId ?: return
        viewModelScope.launch {
            // Apply unified diff via `git apply` inside Termux — safest path, atomic, reviewable
            val b64 = android.util.Base64.encodeToString(patch.diff.toByteArray(), android.util.Base64.NO_WRAP)
            val result = projectManager.runCommand(
                projectId,
                "echo '$b64' | base64 -d | git apply --whitespace=nowarn - && echo PATCH_OK",
            )
            val ok = result.stdout.contains("PATCH_OK")
            _state.value = _state.value.copy(
                pendingPatches = _state.value.pendingPatches.map {
                    if (it.path == patch.path) it.copy(applied = ok, rejected = !ok) else it
                },
                error = if (!ok) "Could not apply patch to ${patch.path}:\n${result.stdout.take(400)}" else null,
            )
        }
    }

    fun rejectPatch(patch: PendingPatch) {
        _state.value = _state.value.copy(
            pendingPatches = _state.value.pendingPatches.map {
                if (it.path == patch.path) it.copy(rejected = true) else it
            },
        )
    }

    fun revertPatch(patch: PendingPatch) {
        val projectId = _state.value.projectId ?: return
        viewModelScope.launch {
            projectManager.runCommand(projectId, "git checkout HEAD -- '${patch.path}' && echo REVERTED")
            _state.value = _state.value.copy(
                pendingPatches = _state.value.pendingPatches.map {
                    if (it.path == patch.path) it.copy(applied = false, rejected = true) else it
                },
            )
        }
    }

    fun dismissError() { _state.value = _state.value.copy(error = null) }

    private suspend fun buildContext(projectId: String): String {
        val status = git.status(projectId).getOrNull()
        val tree = projectManager.listDirectory(projectId, "").getOrNull()
        return buildString {
            appendLine("[Working directory: ${projectManager.projectsDir}/$projectId]")
            status?.let { appendLine("[Git branch: ${it.branch}]") }
            tree?.let { entries ->
                appendLine("[Project root]: ${entries.joinToString(", ") { e -> if (e.isDirectory) e.name + "/" else e.name }}")
            }
        }
    }

    private suspend fun chatHistory(projectId: String): List<ChatMessage> {
        val recent = ctx.database.chatDao().recentForProject(projectId, 20).reversed()
        val out = ArrayList<ChatMessage>(recent.size)
        for (m in recent) out.add(ChatMessage(m.role, m.content))
        return out
    }

    private fun parsePatches(text: String): List<PendingPatch> {
        val patches = mutableListOf<PendingPatch>()
        val regex = Regex("(?s)PATCH\\s+([\\w./-]+)\\s*```[a-z]*\\n(.*?)```")
        regex.findAll(text).forEach { m ->
            patches += PendingPatch(path = m.groupValues[1], diff = m.groupValues[2])
        }
        return patches
    }

    private suspend fun providerId(): String = ctx.settingsRepository.providerBlocking()
    private suspend fun modelFor(): String = ctx.settingsRepository.modelBlocking()
    private fun humanizeAiError(msg: String): String = when {
        msg.contains("No API key") -> msg
        msg.contains("Network", true) || msg.contains("Unable to resolve", true) ->
            "AI requires an internet connection. Check your network and try again."
        msg.contains("401") -> "API key rejected by the provider. Check the key in Settings → AI Provider."
        msg.contains("429") -> "Rate limit reached at the AI provider. Wait a moment and retry."
        else -> "AI request failed: $msg"
    }
}
