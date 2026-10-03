package com.zotmobile.core.agent

import com.zotmobile.core.git.GitRepository
import com.zotmobile.core.termux.TermuxCommandRunner
import java.io.File

/**
 * Sandboxed project file tools exposed to the AI agent.
 * All paths are resolved inside the project root; traversal outside the root
 * is rejected. Every tool returns a plain-text result for the model.
 */
class AgentTools(
    val projectRoot: File,
    private val runner: TermuxCommandRunner?,
) {
    private val maxReadChars = 60_000

    fun listFiles(relPath: String = "."): String {
        val dir = resolve(relPath) ?: return err("Path outside the project: " + relPath)
        if (!dir.isDirectory) return err("Not a directory: " + relPath)
        val entries = dir.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })).orEmpty()
        return entries.take(400).joinToString("\n") { e ->
            (if (e.isDirectory) "dir  " else "file ") + e.name
        }
    }

    fun readFile(relPath: String): String {
        val f = resolve(relPath) ?: return err("Path outside the project: " + relPath)
        if (!f.isFile) return err("Not a file: " + relPath)
        val text = f.readText(Charsets.UTF_8)
        return if (text.length > maxReadChars) text.take(maxReadChars) + "\n... [truncated]"
        else text
    }

    fun searchProject(pattern: String): String {
        val regex = try { Regex(pattern) } catch (e: Exception) {
            return err("Invalid regular expression: " + e.message)
        }
        val out = StringBuilder()
        projectRoot.walkTopDown()
            .filter { it.isFile && !it.path.contains("/.git/") }
            .take(4000)
            .forEach { f ->
                if (f.length() > 2_000_000) return@forEach
                try {
                    f.useLines { lines ->
                        lines.forEachIndexed { i, line ->
                            if (regex.containsMatchIn(line) && out.length < 40_000) {
                                out.append(rel(f)).append(':').append(i + 1).append(": ").append(line.trim().take(300)).append('\n')
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        return out.toString().ifBlank { "No matches for: " + pattern }
    }

    /** Propose a file write. Not applied until user approves the [PendingChange]. */
    fun writeFile(relPath: String, newContent: String): PendingChange {
        val f = resolve(relPath) ?: return PendingChange.failed(relPath, "Path outside the project: " + relPath)
        val existed = f.isFile
        val oldContent = if (existed) f.readText(Charsets.UTF_8) else ""
        return PendingChange(
            path = relPath,
            isDelete = false,
            isCreate = !existed,
            oldContent = oldContent,
            newContent = newContent,
        )
    }

    fun deleteFile(relPath: String): PendingChange {
        val f = resolve(relPath) ?: return PendingChange.failed(relPath, "Path outside the project: " + relPath)
        if (!f.isFile) return PendingChange.failed(relPath, "Not a file: " + relPath)
        return PendingChange(
            path = relPath,
            isDelete = true,
            isCreate = false,
            oldContent = f.readText(Charsets.UTF_8),
            newContent = "",
        )
    }

    /** Applies an approved change to disk. */
    fun apply(change: PendingChange): Boolean {
        val f = resolve(change.path) ?: return false
        return try {
            when {
                change.isDelete -> f.delete()
                else -> {
                    f.parentFile?.mkdirs()
                    f.writeText(change.newContent, Charsets.UTF_8)
                    true
                }
            }
        } catch (_: Exception) { false }
    }

    fun revert(change: PendingChange): Boolean {
        val f = resolve(change.path) ?: return false
        return try {
            when {
                change.isDelete -> { f.writeText(change.oldContent, Charsets.UTF_8); true }
                change.isCreate -> f.delete()
                else -> { f.writeText(change.oldContent, Charsets.UTF_8); true }
            }
        } catch (_: Exception) { false }
    }

    suspend fun runCommand(cmd: String, timeoutMs: Long = 180_000): String {
        val r = runner ?: return "error: no execution environment (Termux) available"
        val e = r.runCaptured(cmd, workDir = projectRoot.absolutePath, timeoutMs = timeoutMs)
        return when (e.state) {
            com.zotmobile.core.model.CommandEvent.State.COMPLETED ->
                "exit=" + (e.exitCode ?: -1) + "\n" + (e.stdout + (if (e.stderr.isNotBlank()) "\n" + e.stderr else "")).take(30_000)
            else -> "error: " + (e.stderr.ifBlank { "command did not complete" })
        }
    }

    suspend fun gitStatus(): String {
        val git = GitRepository(projectRoot, runner ?: return "error: no execution environment available")
        return git.status().fold(
            onSuccess = { s ->
                "branch=" + s.branch + " ahead=" + s.ahead + " behind=" + s.behind + "\n" +
                s.entries.joinToString("\n") { it.x + "" + it.y + " " + it.path }.ifBlank { "(clean)" }
            },
            onFailure = { "error: " + (it.message ?: "unknown") },
        )
    }

    suspend fun gitDiff(): String {
        val git = GitRepository(projectRoot, runner ?: return "error: no execution environment available")
        return git.git("diff HEAD --no-color").fold(
            onSuccess = { it.stdout.take(60_000).ifBlank { "(no changes)" } },
            onFailure = { "error: " + (it.message ?: "unknown") },
        )
    }

    private fun resolve(relPath: String): File? {
        val cleaned = relPath.trim().removePrefix("./").removePrefix("/")
        if (cleaned.contains("..")) return null
        val f = File(projectRoot, cleaned)
        val canonical = try { f.canonicalFile } catch (_: Exception) { return null }
        val rootCanon = try { projectRoot.canonicalFile } catch (_: Exception) { return null }
        return if (canonical.path == rootCanon.path || canonical.path.startsWith(rootCanon.path + File.separator)) canonical else null
    }

    private fun rel(f: File): String =
        f.relativeToOrNull(projectRoot)?.path ?: f.name

    companion object {
        fun err(msg: String): String = "error: " + msg

        val systemPrompt: String = """
            You are ZotMobile, an AI coding agent running inside an Android app on the user's device.
            You work on the user's local project through tools.
            Rules:
            - Before editing, read the relevant files and state exactly which files you will change and why.
            - Make focused, minimal edits; never rewrite whole files unless asked.
            - After edits, offer to run the project's tests or build to verify.
            - Never print or request API keys, tokens, or passwords.
            - Explain errors in plain language with a suggested fix.
            - When done, summarize what changed as a short list of files.
        """.trimIndent()
    }
}

/** A proposed file change awaiting user accept/reject/revert. */
data class PendingChange(
    val path: String,
    val isDelete: Boolean,
    val isCreate: Boolean,
    val oldContent: String,
    val newContent: String,
    val failed: Boolean = false,
    val failureReason: String = "",
) {
    companion object {
        fun failed(path: String, reason: String) = PendingChange(path, false, false, "", "", failed = true, failureReason = reason)
    }
}
