package com.zotmobile.core.agent

import com.zotmobile.core.ai.AiEvent
import com.zotmobile.core.ai.AiMessage
import com.zotmobile.core.ai.AiProvider
import com.zotmobile.core.ai.AiTool
import com.zotmobile.core.ai.ProviderConfig
import com.zotmobile.core.ai.Role
import com.zotmobile.core.ai.ToolCall
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/**
 * Drives the AI agent loop (zot workflow):
 * user prompt -> model -> tool call -> (confirmation for writes) -> tool result
 * -> model -> ... -> final answer.
 *
 * Destructive operations (file writes, deletes, dangerous commands) never run
 * automatically: the orchestrator emits them as proposals and waits for the UI
 * to confirm or reject before applying and continuing the loop.
 */
class AgentOrchestrator(
    private val provider: AiProvider,
    private val tools: AgentTools,
    private val config: ProviderConfig,
) {
    sealed interface UiEvent {
        data class Chunk(val text: String) : UiEvent
        data class Thinking(val text: String) : UiEvent
        data class WriteProposal(val change: PendingChange) : UiEvent
        data class CommandProposal(val command: String) : UiEvent
        data class CommandResult(val command: String, val output: String) : UiEvent
        data class Finished(val text: String) : UiEvent
        data class Error(val message: String) : UiEvent
    }

    /** Conversations persist per session so follow-ups keep context. */
    private val history = mutableListOf<AiMessage>()
    private var lastProposal: PendingChange? = null
    private var lastProposalCommand: String? = null

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    fun resetConversation() { history.clear() }

    fun toolSchemas(): List<AiTool> = listOf(
        AiTool(
            name = "list_files",
            description = "List files and directories of the project or a subfolder.",
            parametersJsonSchema = """{"type":"object","properties":{"path":{"type":"string","description":"relative path, default project root"}},"required":[]}""",
        ),
        AiTool(
            name = "read_file",
            description = "Read a text file from the project.",
            parametersJsonSchema = """{"type":"object","properties":{"path":{"type":"string"}},"required":["path"]}""",
        ),
        AiTool(
            name = "search_project",
            description = "Search the project with a regular expression. Returns path:line: match.",
            parametersJsonSchema = """{"type":"object","properties":{"pattern":{"type":"string"}},"required":["pattern"]}""",
        ),
        AiTool(
            name = "write_file",
            description = "Propose new full content for a file. The user must approve before it is applied.",
            parametersJsonSchema = """{"type":"object","properties":{"path":{"type":"string"},"content":{"type":"string"}},"required":["path","content"]}""",
        ),
        AiTool(
            name = "delete_file",
            description = "Propose deleting a file. The user must approve before it is applied.",
            parametersJsonSchema = """{"type":"object","properties":{"path":{"type":"string"}},"required":["path"]}""",
        ),
        AiTool(
            name = "run_command",
            description = "Run a shell command in the project directory (e.g. npm test, ./gradlew build). Destructive commands need user approval.",
            parametersJsonSchema = """{"type":"object","properties":{"command":{"type":"string"}},"required":["command"]}""",
        ),
        AiTool(
            name = "git_status",
            description = "Show current branch and changed files.",
            parametersJsonSchema = """{"type":"object","properties":{},"required":[]}""",
        ),
        AiTool(
            name = "git_diff",
            description = "Show the current uncommitted diff.",
            parametersJsonSchema = """{"type":"object","properties":{},"required":[]}""",
        ),
    )

    fun send(userMessage: String): Flow<UiEvent> = flow {
        if (_busy.value) { emit(UiEvent.Error("The agent is already working. Wait for it to finish.")); return@flow }
        _busy.value = true
        try {
            if (history.isEmpty()) history.add(AiMessage(Role.SYSTEM, AgentTools.systemPrompt))
            history.add(AiMessage(Role.USER, userMessage))

            var rounds = 0
            while (rounds < MAX_ROUNDS) {
                rounds++
                var turn: com.zotmobile.core.ai.AiTurn? = null
                provider.complete(history, toolSchemas(), config).collect { ev: com.zotmobile.core.ai.AiEvent ->
                    when (ev) {
                        is AiEvent.Done -> { turn = com.zotmobile.core.ai.AiTurn.Answer(ev.answer) }
                        is AiEvent.ToolRequested -> { turn = com.zotmobile.core.ai.AiTurn.ToolUse(listOf(ev.call), "") }
                        is AiEvent.Failed -> { emit(UiEvent.Error(ev.error)); return@collect }
                        else -> {}
                    }
                }
                when (val t = turn) {
                    is com.zotmobile.core.ai.AiTurn.Answer -> {
                        history.add(AiMessage(Role.ASSISTANT, t.text))
                        emit(UiEvent.Finished(t.text))
                        return@flow
                    }
                    is com.zotmobile.core.ai.AiTurn.ToolUse -> {
                        val call = t.calls.firstOrNull() ?: continue
                        val result = execute(call)
                        // For proposals, result is pending until confirmed by UI.
                        if (result == PROPOSAL_PENDING) {
                            history.add(AiMessage(Role.ASSISTANT, "PROPOSED " + call.name + ": " + call.argumentsJson))
                            return@flow  // wait for confirmProposal()/rejectProposal()
                        }
                        history.add(AiMessage(Role.TOOL, result, toolName = call.name))
                    }
                    null -> { emit(UiEvent.Error("The AI returned an empty response. Try again.")); return@flow }
                }
            }
            emit(UiEvent.Error("The agent reached its step limit without finishing. Ask a more focused question."))
        } finally {
            _busy.value = false
        }
    }

    /** Apply or reject a pending proposal, then continue the agent loop. */
    fun confirmProposal(accepted: Boolean): Flow<UiEvent> = flow {
        val change = lastProposal
        val cmd = lastProposalCommand
        lastProposal = null
        lastProposalCommand = null
        when {
            change != null -> {
                if (accepted) {
                    val ok = tools.apply(change)
                    val msg = if (ok) "APPLIED " + change.path else "FAILED to apply " + change.path
                    history.add(AiMessage(Role.TOOL, msg, toolName = "write_file"))
                    emitAll(continueLoop())
                } else {
                    history.add(AiMessage(Role.TOOL, "USER REJECTED the change to " + change.path + ". Adjust your plan.", toolName = "write_file"))
                    emitAll(continueLoop())
                }
            }
            cmd != null -> {
                if (accepted) {
                    val out = tools.runCommand(cmd)
                    history.add(AiMessage(Role.TOOL, out.take(20_000), toolName = "run_command"))
                    emit(UiEvent.CommandResult(cmd, out))
                    emitAll(continueLoop())
                } else {
                    history.add(AiMessage(Role.TOOL, "USER REJECTED running this command. Propose another way.", toolName = "run_command"))
                    emitAll(continueLoop())
                }
            }
            else -> emit(UiEvent.Error("Nothing to confirm."))
        }
    }

    private fun continueLoop() = flow {
        // Continue: re-query the model with the tool result already in history
        var rounds = 0
        while (rounds < MAX_ROUNDS) {
            rounds++
            var turn: com.zotmobile.core.ai.AiTurn? = null
            provider.complete(history, toolSchemas(), config).collect { ev: com.zotmobile.core.ai.AiEvent ->
                when (ev) {
                    is AiEvent.Done -> { turn = com.zotmobile.core.ai.AiTurn.Answer(ev.answer) }
                    is AiEvent.ToolRequested -> { turn = com.zotmobile.core.ai.AiTurn.ToolUse(listOf(ev.call), "") }
                    is AiEvent.Failed -> { emit(UiEvent.Error(ev.error)); return@collect }
                    else -> {}
                }
            }
            when (val t = turn) {
                is com.zotmobile.core.ai.AiTurn.Answer -> {
                    history.add(AiMessage(Role.ASSISTANT, t.text))
                    emit(UiEvent.Finished(t.text))
                    return@flow
                }
                is com.zotmobile.core.ai.AiTurn.ToolUse -> {
                    val call = t.calls.firstOrNull() ?: continue
                    val result = execute(call)
                    if (result == PROPOSAL_PENDING) {
                        history.add(AiMessage(Role.ASSISTANT, "PROPOSED " + call.name + ": " + call.argumentsJson))
                        return@flow
                    }
                    history.add(AiMessage(Role.TOOL, result, toolName = call.name))
                }
                null -> { emit(UiEvent.Error("The AI returned an empty response. Try again.")); return@flow }
            }
        }
    }

    /** Execute a tool call; returns result text or PROPOSAL_PENDING if user confirmation is required. */
    private suspend fun execute(call: ToolCall): String {
        val args = parseArgs(call.argumentsJson)
        return when (call.name) {
            "list_files" -> tools.listFiles(args["path"] ?: ".")
            "read_file" -> tools.readFile(args["path"] ?: "")
            "search_project" -> tools.searchProject(args["pattern"] ?: "")
            "write_file" -> {
                val change = tools.writeFile(args["path"] ?: "", args["content"] ?: "")
                lastProposal = change
                PROPOSAL_PENDING
            }
            "delete_file" -> {
                val change = tools.deleteFile(args["path"] ?: "")
                lastProposal = change
                PROPOSAL_PENDING
            }
            "run_command" -> {
                val cmd = args["command"] ?: ""
                when (val policy = com.zotmobile.core.termux.SafetyPolicy.evaluate(cmd)) {
                    is com.zotmobile.core.termux.SafetyPolicy.Denied -> "error: " + policy.reason
                    is com.zotmobile.core.termux.SafetyPolicy.NeedsConfirmation -> {
                        lastProposalCommand = cmd
                        PROPOSAL_PENDING
                    }
                    com.zotmobile.core.termux.SafetyPolicy.Allowed -> {
                        val out = tools.runCommand(cmd)
                        out
                    }
                }
            }
            "git_status" -> tools.gitStatus()
            "git_diff" -> tools.gitDiff()
            else -> "error: unknown tool " + call.name
        }
    }

    private fun parseArgs(json: String): Map<String, String> {
        return try {
            val el = kotlinx.serialization.json.Json.parseToJsonElement(json.ifBlank { "{}" })
            (el as? kotlinx.serialization.json.JsonObject)?.mapValues { it.value.toString().trim('"') } ?: emptyMap()
        } catch (_: Exception) { emptyMap() }
    }

    companion object {
        const val PROPOSAL_PENDING = "__ZOT_PROPOSAL_PENDING__"
        const val MAX_ROUNDS = 12
    }
}
