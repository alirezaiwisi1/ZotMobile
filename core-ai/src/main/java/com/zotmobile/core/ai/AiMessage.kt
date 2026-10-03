
package com.zotmobile.core.ai

import kotlinx.serialization.Serializable

/** Role of a message in the agent conversation. */
enum class Role { SYSTEM, USER, ASSISTANT, TOOL }

/** A message in an AI conversation. [content] carries text; [toolName]/[toolResultJson]
 *  carry tool-call round-trips for providers that support function calling. */
@Serializable
data class AiMessage(
    val role: Role,
    val content: String,
    val toolName: String? = null,
    val toolResultJson: String? = null,
)

/** A tool the agent layer can expose to a provider. */
data class AiTool(val name: String, val description: String, val parametersJsonSchema: String)

/** One tool invocation requested by the model. */
@Serializable
data class ToolCall(val name: String, val argumentsJson: String)

/** What the model produced in one completion round. */
sealed interface AiTurn {
    /** Model wants to call a tool. */
    data class ToolUse(val calls: List<ToolCall>, val raw: String) : AiTurn
    /** Model produced a final text answer. */
    data class Answer(val text: String) : AiTurn
}

/** A streamed or completed turn, as delivered to the UI. */
sealed interface AiEvent {
    data class Delta(val text: String) : AiEvent
    data class ToolRequested(val call: ToolCall) : AiEvent
    data class ToolResult(val call: ToolCall, val resultJson: String) : AiEvent
    data class Done(val answer: String) : AiEvent
    data class Failed(val error: String) : AiEvent
}
