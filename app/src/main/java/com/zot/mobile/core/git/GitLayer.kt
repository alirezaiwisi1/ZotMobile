package com.zot.mobile.core.git

import com.zot.mobile.core.termux.TermuxClient
import com.zot.mobile.core.termux.TermuxJob

/** Git operations executed inside Termux. All commands are read/checked against the safety layer. */
class GitLayer(private val termux: TermuxClient, private val projectsDir: String) {

    suspend fun status(project: String): Result<GitStatus> = runGit(project, "git status --porcelain -b") { out ->
        val lines = out.lines().filter { it.isNotBlank() }
        val branch = lines.firstOrNull()?.removePrefix("## ")?.substringBefore("...")?.substringBefore(" ") ?: "unknown"
        val changes = lines.drop(1).map { line ->
            FileChange(
                path = line.drop(3).trim(),
                changeType = when (line.take(2).trim().firstOrNull()) {
                    'M' -> "modified"
                    'A', '?' -> "added"
                    'D' -> "deleted"
                    'R' -> "renamed"
                    else -> "changed"
                },
                staged = line.firstOrNull()?.let { it != ' ' && it != '?' } ?: false,
            )
        }
        GitStatus(branch, changes)
    }

    suspend fun diff(project: String): Result<String> = runGit(project, "git diff HEAD --stat && echo ---SPLIT--- && git diff HEAD") { it }

    suspend fun log(project: String, count: Int = 10): Result<String> =
        runGit(project, "git log --oneline -n $count") { it }

    suspend fun clone(url: String, name: String): Result<String> =
        runGit(name, "git clone '$url' '$projectsDir/$name' 2>&1 && echo CLONE_OK") { it }

    suspend fun pull(project: String): Result<String> = runGit(project, "git pull --ff-only 2>&1") { it }

    suspend fun push(project: String, branch: String? = null): Result<String> =
        runGit(project, "git push ${branch?.let { "origin $it" } ?: ""} 2>&1") { it }

    suspend fun addAndCommit(project: String, message: String, paths: List<String> = listOf("-A")): Result<String> {
        val add = "git add ${paths.joinToString(" ") { "'$it'" }}"
        val escaped = message.replace("'", "'\\''")
        return runGit(project, "$add && git commit -m '$escaped' 2>&1") { it }
    }

    suspend fun checkout(project: String, branch: String): Result<String> =
        runGit(project, "git checkout '$branch' 2>&1") { it }

    suspend fun branches(project: String): Result<List<String>> =
        runGit(project, "git branch -a --format='%(refname:short)'") { out ->
            out.lines().map { it.trim() }.filter { it.isNotBlank() }
        }

    suspend fun revertFile(project: String, path: String): Result<String> =
        runGit(project, "git checkout HEAD -- '$path' 2>&1 && echo REVERTED") { it }

    private suspend fun <T> runGit(project: String, command: String, parse: (String) -> T): Result<T> {
        return termux.runJob(
            TermuxJob(
                id = "git-${System.nanoTime()}",
                command = command,
                workdir = "$projectsDir/$project",
                timeoutMs = 180_000,
            ),
        ).map { r ->
            if (r.exitCode != 0 && !r.stdout.contains("CLONE_OK") && !r.stdout.contains("REVERTED")) {
                throw GitException(mapGitError(r.stdout, r.exitCode))
            }
            parse(r.stdout)
        }
    }

    private fun mapGitError(stdout: String, exit: Int): String = when {
        stdout.contains("not a git repository") -> "This project is not a Git repository yet."
        stdout.contains("Authentication failed") || stdout.contains("403") ->
            "GitHub authentication failed. Update your token in Settings → GitHub."
        stdout.contains("non-fast-forward") -> "Remote has new commits. Pull first, then push."
        stdout.contains("Repository not found") -> "Repository not found. Check the URL and your access."
        stdout.contains("command not found") -> "Git was not found in the Termux environment. Install Git and run the environment check again."
        else -> "Git command failed (exit $exit):\n$stdout"
    }
}

class GitException(message: String) : Exception(message)

data class GitStatus(val branch: String, val changes: List<FileChange>)
data class FileChange(val path: String, val changeType: String, val staged: Boolean)
