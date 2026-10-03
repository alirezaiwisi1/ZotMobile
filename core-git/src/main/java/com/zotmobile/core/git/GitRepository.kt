package com.zotmobile.core.git

import com.zotmobile.core.model.CommandEvent
import com.zotmobile.core.termux.TermuxCommandRunner
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext

/**
 * Git operations for a single local repository. All heavy work shells out to
 * the `git` binary (Termux or local fallback) — no JGit, keeping the APK lean
 * and the behavior identical to the user's real git.
 */
class GitRepository(
    private val root: File,
    private val runner: TermuxCommandRunner,
) {
    suspend fun ensureInitialized(): Result<Unit> = withContext(Dispatchers.IO) {
        if (File(root, ".git").exists()) return@withContext Result.success(Unit)
        runCapture("git init").map { }
    }

    suspend fun status(): Result<GitStatus> = withContext(Dispatchers.IO) {
        val r = runCapture("git status --porcelain=v1 -b --untracked-files=all")
        r.map { parseStatus(it.stdout) }
    }

    suspend fun diff(path: String? = null): Result<GitDiff> = withContext(Dispatchers.IO) {
        val spec = if (path != null) " -- " + shellQuote(path) else ""
        val r = runCapture("git diff HEAD --no-color" + spec)
        r.map { parseDiff(it.stdout) }
    }

    suspend fun stage(path: String): Result<CommandEvent> = withContext(Dispatchers.IO) {
        runCapture("git add -- " + shellQuote(path))
    }

    suspend fun unstage(path: String): Result<CommandEvent> = withContext(Dispatchers.IO) {
        runCapture("git reset HEAD -- " + shellQuote(path))
    }

    suspend fun discard(path: String): Result<CommandEvent> = withContext(Dispatchers.IO) {
        runCapture("git checkout HEAD -- " + shellQuote(path))
    }

    suspend fun commit(message: String): Result<CommandEvent> = withContext(Dispatchers.IO) {
        if (message.isBlank()) return@withContext Result.failure(IllegalArgumentException("Commit message is empty"))
        runCapture("git commit -m " + shellQuote(message))
    }

    suspend fun pull(remote: String = "origin", branch: String? = null): Result<CommandEvent> =
        withContext(Dispatchers.IO) {
            runCapture("git pull " + remote + (branch?.let { " " + shellQuote(it) } ?: ""))
        }

    suspend fun push(remote: String = "origin", branch: String? = null): Result<CommandEvent> =
        withContext(Dispatchers.IO) {
            val b = branch ?: currentBranch().getOrDefault("main")
            runCapture("git push -u " + remote + " " + shellQuote(b))
        }

    suspend fun branches(): Result<List<String>> = withContext(Dispatchers.IO) {
        runCapture("git branch --list --all").map { out ->
            out.stdout.lines().filter { it.isNotBlank() }
                .map { it.trim().removePrefix("* ").removePrefix("remotes/origin/") }
                .distinct().sorted()
        }
    }

    suspend fun currentBranch(): Result<String> = withContext(Dispatchers.IO) {
        runCapture("git rev-parse --abbrev-ref HEAD").map { it.stdout.trim() }
    }

    suspend fun lastCommit(): Result<CommitInfo?> = withContext(Dispatchers.IO) {
        runCapture("git log -1 --pretty=format:%h%x1f%s%x1f%an%x1f%at").map { out ->
            if (out.stdout.isBlank()) null else out.stdout.split('\u001f').let { p ->
                CommitInfo(
                    hash = p.getOrNull(0) ?: "",
                    subject = p.getOrNull(1) ?: "",
                    author = p.getOrNull(2) ?: "",
                    timestamp = p.getOrNull(3)?.toLongOrNull()?.times(1000) ?: 0L,
                )
            }
        }
    }

    suspend fun checkoutBranch(branch: String): Result<CommandEvent> = withContext(Dispatchers.IO) {
        runCapture("git checkout " + shellQuote(branch))
    }

    suspend fun createBranch(branch: String): Result<CommandEvent> = withContext(Dispatchers.IO) {
        runCapture("git checkout -b " + shellQuote(branch))
    }

    suspend fun addRemote(name: String, url: String): Result<CommandEvent> = withContext(Dispatchers.IO) {
        runCapture("git remote add " + shellQuote(name) + " " + shellQuote(url))
    }

    /** Run an arbitrary git subcommand with output; used by agent tooling. */
    suspend fun git(args: String): Result<CommandEvent> = withContext(Dispatchers.IO) {
        runCapture("git " + args)
    }

    private suspend fun runCapture(cmd: String): Result<CommandEvent> {
        val final = runner.runCaptured(cmd, workDir = root.absolutePath)
        return when (final.state) {
            CommandEvent.State.COMPLETED -> if ((final.exitCode ?: -1) == 0) Result.success(final)
            else Result.failure(GitException(friendlyGitError(cmd, final)))
            else -> Result.failure(GitException(friendlyGitError(cmd, final)))
        }
    }

    private fun shellQuote(s: String): String = "'" + s.replace("'", "'\\''") + "'"

    private fun friendlyGitError(cmd: String, e: CommandEvent): String {
        val raw = (e.stderr + "\n" + e.stdout).trim()
        return when {
            raw.contains("git: not found") || raw.contains("command not found") ->
                "Git is not installed in the Termux environment.\nOpen Termux and run: pkg install git\nThen run the environment check again."
            raw.contains("Permission denied (publickey)") || raw.contains("Authentication failed") ->
                "GitHub rejected the credentials. Check the token in Settings → GitHub, or use a personal access token with repo scope."
            raw.contains("not a git repository") ->
                "This project is not a Git repository yet. Initialize it from the project menu."
            raw.contains("nothing to commit") ->
                "There are no changes to commit yet."
            raw.contains("No such file or directory") ->
                "A file or directory is missing on disk. Refresh the project and retry."
            raw.contains("Please tell me who you are") ->
                "Git needs your name and email to commit. Set them in Settings → Git identity."
            else -> raw.ifBlank { "Git command failed (${e.state})." }
        }
    }

    companion object {
        fun parseStatus(output: String): GitStatus {
            var branch = ""
            var ahead = 0
            var behind = 0
            val entries = mutableListOf<GitStatus.StatusEntry>()
            for (line in output.lines()) {
                when {
                    line.startsWith("## ") -> {
                        val body = line.removePrefix("## ")
                        val b = body.substringBefore("...")
                        val ab = Regex("ahead (\\d+)").find(body)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                        val be = Regex("behind (\\d+)").find(body)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                        branch = b; ahead = ab; behind = be
                    }
                    line.length >= 3 -> entries.add(GitStatus.StatusEntry(line[0], line[1], line.substring(3)))
                }
            }
            return GitStatus(branch, ahead, behind, entries)
        }

        fun parseDiff(output: String): GitDiff {
            val files = mutableListOf<GitDiff.FileDiff>()
            var cur: GitDiff.FileDiff? = null
            var hunkHeader = ""
            var hunkLines = mutableListOf<GitDiff.Line>()

            fun flushHunk() {
                if (hunkLines.isNotEmpty()) {
                    cur = cur?.copy(hunks = cur!!.hunks + GitDiff.Hunk(hunkHeader, hunkLines.toList()))
                    hunkLines = mutableListOf()
                }
            }

            for (line in output.lines()) {
                when {
                    line.startsWith("diff --git") -> {
                        flushHunk()
                        cur?.let { files.add(it) }
                        val paths = Regex("b/(.+)$").find(line)
                        cur = GitDiff.FileDiff(path = paths?.groupValues?.get(1) ?: "?")
                    }
                    line.startsWith("new file mode") -> cur = cur?.copy(isNew = true)
                    line.startsWith("deleted file mode") -> cur = cur?.copy(isDeleted = true)
                    line.startsWith("Binary files") -> cur = cur?.copy(isBinary = true)
                    line.startsWith("@@") -> {
                        flushHunk()
                        hunkHeader = line
                    }
                    line.startsWith("+++") || line.startsWith("---") -> {}
                    line.startsWith("+") -> hunkLines.add(GitDiff.Line(GitDiff.Line.Kind.ADD, line.drop(1)))
                    line.startsWith("-") -> hunkLines.add(GitDiff.Line(GitDiff.Line.Kind.REMOVE, line.drop(1)))
                    line.startsWith(" ") -> hunkLines.add(GitDiff.Line(GitDiff.Line.Kind.CONTEXT, line.drop(1)))
                    else -> {}
                }
            }
            flushHunk()
            cur?.let { files.add(it) }
            return GitDiff(files.filter { f -> f.hunks.isNotEmpty() || f.isNew || f.isDeleted || f.isBinary })
        }
    }
}

data class CommitInfo(val hash: String, val subject: String, val author: String, val timestamp: Long) {
    fun relativeTime(): String {
        val diff = System.currentTimeMillis() - timestamp
        val mins = diff / 60000
        return when {
            mins < 1 -> "just now"
            mins < 60 -> mins.toString() + " min ago"
            mins < 1440 -> (mins / 60).toString() + " h ago"
            mins < 43200 -> (mins / 1440).toString() + " d ago"
            else -> SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timestamp))
        }
    }
}

class GitException(message: String) : Exception(message)
