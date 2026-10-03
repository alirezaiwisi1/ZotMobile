package com.zotmobile.core.model

/** A command execution event streamed to the UI/agent layer. */
data class CommandEvent(
    val sessionId: String,
    val command: String,
    val state: State,
    val exitCode: Int? = null,
    val stdout: String = "",
    val stderr: String = "",
    val durationMs: Long? = null,
    val partial: Boolean = false,
) {
    enum class State { RUNNING, COMPLETED, FAILED, STOPPED, NEEDS_CONFIRMATION }
}
