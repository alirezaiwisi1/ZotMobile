package com.zot.mobile.data.project

import com.zot.mobile.core.git.GitLayer
import com.zot.mobile.core.termux.TermuxClient
import com.zot.mobile.core.termux.TermuxJob
import com.zot.mobile.data.db.ProjectDao
import com.zot.mobile.data.db.ProjectEntity
import java.io.File

/**
 * Project file operations run against the Termux home over direct filesystem
 * access is NOT possible across app sandboxes on modern Android — so all file
 * mutations go through Termux jobs too, and cached listings mirror state.
 */
class ProjectManager(
    private val termux: TermuxClient,
    private val git: GitLayer,
    private val projectDao: ProjectDao,
    val projectsDir: String = TermuxClient.PROJECTS_DIR,
) {

    suspend fun observeProjects() = projectDao.observeAll()

    suspend fun cloneRepository(url: String, name: String): Result<ProjectEntity> {
        val clean = name.trim().ifBlank { url.substringAfterLast('/').removeSuffix(".git") }
        return git.clone(url, clean).map {
            val entity = ProjectEntity(
                id = clean, name = clean, remoteUrl = url,
                createdAt = System.currentTimeMillis(), lastOpenedAt = System.currentTimeMillis(),
            )
            projectDao.upsert(entity)
            entity
        }
    }

    suspend fun createLocal(name: String): Result<ProjectEntity> = runCatching {
        termux.runJob(
            TermuxJob("mkdir-${System.nanoTime()}", "mkdir -p '$projectsDir/$name' && cd '$projectsDir/$name' && git init -q && echo OK", timeoutMs = 30_000),
        ).getOrThrow()
        val entity = ProjectEntity(
            id = name, name = name, remoteUrl = null,
            createdAt = System.currentTimeMillis(), lastOpenedAt = System.currentTimeMillis(),
        )
        projectDao.upsert(entity)
        entity
    }

    suspend fun touch(id: String) {
        projectDao.get(id)?.let { projectDao.upsert(it.copy(lastOpenedAt = System.currentTimeMillis())) }
    }

    suspend fun delete(id: String): Result<Unit> = runCatching {
        termux.runJob(
            TermuxJob("rm-${System.nanoTime()}", "rm -rf '${escape(projectsDir)}/${escape(id)}'", timeoutMs = 60_000),
        ).getOrThrow()
        projectDao.get(id)?.let { projectDao.delete(it) }
    }

    suspend fun rename(id: String, newName: String): Result<Unit> = runCatching {
        termux.runJob(
            TermuxJob("mv-${System.nanoTime()}", "mv '${escape(projectsDir)}/${escape(id)}' '${escape(projectsDir)}/${escape(newName)}'", timeoutMs = 60_000),
        ).getOrThrow()
        projectDao.get(id)?.let { projectDao.delete(it) }
        projectDao.upsert(ProjectEntity(newName, newName, null, System.currentTimeMillis(), System.currentTimeMillis()))
    }

    /** Lists a directory tree shallowly (depth 2 for mobile browsing). */
    suspend fun listDirectory(project: String, relativePath: String): Result<List<FileEntry>> =
        termux.runJob(
            TermuxJob(
                id = "ls-${System.nanoTime()}",
                command = "ls -F --color=never '${escape(projectsDir)}/${escape(project)}/${escape(relativePath.trim('/'))}'",
                timeoutMs = 30_000,
            ),
        ).map { r ->
            if (r.exitCode != 0) throw Exception(mapLsError(r.stdout))
            r.stdout.lines().filter { it.isNotBlank() }.map { name ->
                val isDir = name.endsWith("/")
                FileEntry(
                    name = name.trimEnd('/'),
                    path = if (relativePath.isBlank()) name.trimEnd('/') else "${relativePath.trimEnd('/')}/${name.trimEnd('/')}",
                    isDirectory = isDir,
                )
            }.sortedWith(compareByDescending<FileEntry> { it.isDirectory }.thenBy { it.name.lowercase() })
        }

    private fun mapLsError(out: String) = when {
        out.contains("No such file") -> "Folder not found. Refresh the project."
        else -> "Could not read folder:\n${out.take(200)}"
    }

    /** Reads a file (max ~200KB to protect mobile memory). */
    suspend fun readFile(project: String, path: String): Result<String> =
        termux.runJob(
            TermuxJob("cat-${System.nanoTime()}", "head -c 204800 '${escape(projectsDir)}/${escape(project)}/${escape(path)}'", timeoutMs = 30_000),
        ).map { it.stdout }

    suspend fun writeFile(project: String, path: String, content: String): Result<Unit> {
        val dir = path.substringBeforeLast('/', "")
        val mkdir = if (dir.isNotBlank()) "mkdir -p '${escape(projectsDir)}/${escape(project)}/${escape(dir)}' && " else ""
        // content delivered via base64 to survive quoting/newlines
        val b64 = android.util.Base64.encodeToString(content.toByteArray(), android.util.Base64.NO_WRAP)
        return termux.runJob(
            TermuxJob(
                "write-${System.nanoTime()}",
                "$mkdir echo '$b64' | base64 -d > '${escape(projectsDir)}/${escape(project)}/${escape(path)}' && echo WRITE_OK",
                timeoutMs = 30_000,
            ),
        ).map { r -> if (!r.stdout.contains("WRITE_OK")) throw Exception("Write failed: ${r.stdout.take(200)}") }
    }

    suspend fun deleteFile(project: String, path: String): Result<Unit> = runCatching {
        termux.runJob(
            TermuxJob("delfile-${System.nanoTime()}", "rm -f '${escape(projectsDir)}/${escape(project)}/${escape(path)}' && echo DEL_OK", timeoutMs = 30_000),
        ).getOrThrow().let { r -> if (!r.stdout.contains("DEL_OK")) throw Exception("Delete failed") }
    }

    suspend fun runCommand(project: String, command: String): com.zot.mobile.core.termux.TermuxResult =
        termux.runJob(
            TermuxJob("cmd-${System.nanoTime()}", command, workdir = "$projectsDir/$project", timeoutMs = 300_000),
        ).getOrElse {
            com.zot.mobile.core.termux.TermuxResult(-1, "Could not reach the Termux environment: ${it.message}", command, 0)
        }

    private fun escape(s: String) = s.replace("'", "'\\''")
}

data class FileEntry(val name: String, val path: String, val isDirectory: Boolean)
