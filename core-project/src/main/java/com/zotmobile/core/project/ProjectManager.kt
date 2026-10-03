package com.zotmobile.core.project

import android.content.Context
import com.zotmobile.core.git.GitRepository
import com.zotmobile.core.github.GitHubClient
import com.zotmobile.core.termux.TermuxCommandRunner
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A locally cloned project workspace. */
data class Project(
    val id: String,
    val name: String,
    val dir: File,
    val repoUrl: String? = null,
)

/**
 * Manages the user's project list. Projects live under the app-private
 * files/projects directory, which Termux can access via the documented
 * run-as / world-readable model the setup wizard configures.
 */
class ProjectManager(private val context: Context) {

    private val projectsDir: File
        get() = File(context.filesDir, "projects").apply { mkdirs() }

    private val indexFile: File
        get() = File(context.filesDir, "projects.json")

    fun listProjects(): List<Project> {
        if (!indexFile.exists()) return emptyList()
        return try {
            val arr = kotlinx.serialization.json.Json.parseToJsonElement(indexFile.readText()).let { it as kotlinx.serialization.json.JsonArray }
            arr.mapNotNull { el ->
                val o = el as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
                val name = o["name"]?.toString()?.trim('"') ?: return@mapNotNull null
                val url = o["repoUrl"]?.toString()?.trim('"')?.takeIf { it != "null" }
                val dir = File(projectsDir, name)
                if (dir.isDirectory) Project(id = name, name = name, dir = dir, repoUrl = url) else null
            }
        } catch (_: Exception) { emptyList() }
    }

    fun saveProjects(projects: List<Project>) {
        val json = "[" + projects.joinToString(",") { p ->
            """{"name":"${p.name}","repoUrl":${p.repoUrl?.let { "\"$it\"" } ?: "null"}}"""
        } + "]"
        indexFile.writeText(json)
    }

    fun get(name: String): Project? = listProjects().firstOrNull { it.name == name }

    suspend fun clone(repoFullName: String, cloneUrl: String, runner: TermuxCommandRunner, client: GitHubClient): Result<Project> =
        withContext(Dispatchers.IO) {
            val name = repoFullName.substringAfter('/')
            val target = File(projectsDir, name)
            if (target.exists()) return@withContext Result.failure(Exception("A project named '" + name + "' already exists. Rename it or delete the old copy."))
            val authUrl = client.authenticatedCloneUrl(cloneUrl)
            val e = runner.runCaptured(
                "git clone --depth 50 " + shellQuote(authUrl ?: cloneUrl) + " " + shellQuote(target.absolutePath),
                workDir = projectsDir.absolutePath,
                timeoutMs = 300_000,
            )
            when {
                e.state == com.zotmobile.core.model.CommandEvent.State.COMPLETED && (e.exitCode ?: -1) == 0 -> {
                    val p = Project(id = name, name = name, dir = target, repoUrl = client.sanitizedUrl(cloneUrl))
                    saveProjects(listProjects() + p)
                    Result.success(p)
                }
                else -> Result.failure(Exception(friendlyCloneError(e)))
            }
        }

    suspend fun createLocal(name: String, runner: TermuxCommandRunner): Result<Project> =
        withContext(Dispatchers.IO) {
            if (!Regex("[A-Za-z0-9._-]+").matches(name)) return@withContext Result.failure(Exception("Use letters, numbers, dots, dashes only for the project name."))
            val target = File(projectsDir, name)
            if (target.exists()) return@withContext Result.failure(Exception("A project named '" + name + "' already exists."))
            target.mkdirs()
            val git = GitRepository(target, runner)
            git.ensureInitialized()
            val p = Project(id = name, name = name, dir = target)
            saveProjects(listProjects() + p)
            Result.success(p)
        }

    suspend fun importFolder(source: File, runner: TermuxCommandRunner): Result<Project> =
        withContext(Dispatchers.IO) {
            val name = source.name
            val target = File(projectsDir, name)
            if (target.exists()) return@withContext Result.failure(Exception("A project named '" + name + "' already exists."))
            source.copyRecursively(target)
            val p = Project(id = name, name = name, dir = target)
            saveProjects(listProjects() + p)
            Result.success(p)
        }

    suspend fun delete(project: Project): Result<Unit> = withContext(Dispatchers.IO) {
        if (!project.dir.deleteRecursively()) return@withContext Result.failure(Exception("Could not delete the project folder. Try again."))
        saveProjects(listProjects().filter { it.name != project.name })
        Result.success(Unit)
    }

    suspend fun rename(project: Project, newName: String): Result<Project> = withContext(Dispatchers.IO) {
        if (!Regex("[A-Za-z0-9._-]+").matches(newName)) return@withContext Result.failure(Exception("Use letters, numbers, dots, dashes only for the project name."))
        val target = File(projectsDir, newName)
        if (target.exists()) return@withContext Result.failure(Exception("A project named '" + newName + "' already exists."))
        if (!project.dir.renameTo(target)) return@withContext Result.failure(Exception("Could not rename the folder."))
        val updated = project.copy(id = newName, name = newName, dir = target)
        saveProjects(listProjects().filter { it.name != project.name } + updated)
        Result.success(updated)
    }

    private fun shellQuote(s: String): String = "'" + s.replace("'", "'\\''") + "'"

    private fun friendlyCloneError(e: com.zotmobile.core.model.CommandEvent): String {
        val raw = (e.stderr + e.stdout).trim()
        return when {
            raw.contains("git: not found") || raw.contains("command not found") ->
                "Git is not installed in the Termux environment.\nOpen Termux and run: pkg install git"
            raw.contains("Repository not found") ->
                "GitHub could not find that repository. Check the URL, or add a token with access to private repos in Settings."
            raw.contains("Permission denied (publickey)") || raw.contains("Authentication failed") ->
                "GitHub rejected the credentials. Set a personal access token in Settings → GitHub."
            raw.contains("Unable to access") || raw.contains("Could not resolve host") ->
                "No internet connection. Cloning needs the network — file editing works offline."
            else -> raw.ifBlank { "The clone failed. Check the URL and your connection." }
        }
    }
}
