package com.zot.mobile.core.termux

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Integration with Termux via the officially supported RUN_COMMAND intent API
 * (com.termux.permission.RUN_COMMAND) and the Termux:API/FilesAccess mechanisms.
 *
 * We never shell out ourselves; all commands run inside the Termux app sandbox
 * and results are relayed through a result file that the runner script writes.
 */
class TermuxClient(private val context: Context) {

    companion object {
        const val TERMUX_PACKAGE = "com.termux"
        const val RUN_COMMAND_ACTION = "com.termux.RUN_COMMAND"
        const val RUN_COMMAND_PATH = "com.termux.RUN_COMMAND_PATH"
        const val RUN_COMMAND_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS"
        const val RUN_COMMAND_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
        const val RUN_COMMAND_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
        const val RUN_COMMAND_RESULT_DIRECTORY = "com.termux.RUN_COMMAND_RESULT_DIRECTORY"

        const val RUNNER_PATH = "bin/zot-runner"
        const val HOME = "/data/data/com.termux/files/home"
        const val PROJECTS_DIR = "$HOME/zot-projects"

        /** Commands considered destructive; require explicit user confirmation in UI. */
        val DESTRUCTIVE_PATTERNS = listOf(
            "rm -rf /", "rm -rf ~", "mkfs", "dd if=", "shutdown", "reboot",
            ":(){", "> /dev/sd", "chmod 777 /", "curl | sh", "wget | sh", "curl | bash", "| sh", "| bash",
        )
    }

    fun isTermuxInstalled(): Boolean = try {
        context.packageManager.getPackageInfo(TERMUX_PACKAGE, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    fun isRunCommandPermissionGranted(): Boolean =
        context.checkSelfPermission("com.termux.permission.RUN_COMMAND") ==
            PackageManager.PERMISSION_GRANTED

    fun termuxInstallIntent(): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse("https://f-droid.org/en/packages/com.termux/"))

    /**
     * Sends a job to Termux. The runner script (installed during setup) reads the
     * job file, executes it, and writes stdout/stderr/exit code to a result file
     * under app-external storage that we poll.
     */
    suspend fun runJob(job: TermuxJob): Result<TermuxResult> = withContext(Dispatchers.IO) {
        runCatching {
            if (!isTermuxInstalled()) error("Termux is not installed")
            if (!isRunCommandPermissionGranted()) error("RUN_COMMAND permission not granted")

            val resultDir = File(context.getExternalFilesDir(null), "results").apply { mkdirs() }
            val resultFile = File(resultDir, "${job.id}.out")
            resultFile.delete()

            val jobFile = File(resultDir, "${job.id}.job").apply {
                writeText(buildScript(job, resultFile.absolutePath))
            }

            // Result files must be readable by Termux: share via a world-readable location
            // is not allowed; instead we pass the job through RUN_COMMAND_ARGUMENTS directly.
            val intent = Intent(RUN_COMMAND_ACTION).apply {
                setClassName(TERMUX_PACKAGE, "com.termux.app.RunCommandService")
                putExtra(RUN_COMMAND_PATH, "/data/data/com.termux/files/usr/bin/bash")
                putExtra(
                    RUN_COMMAND_ARGUMENTS,
                    arrayOf(
                        "-c",
                        buildInlineCommand(job, resultFile.absolutePath),
                    ),
                )
                putExtra(RUN_COMMAND_WORKDIR, job.workdir ?: HOME)
                putExtra(RUN_COMMAND_BACKGROUND, true)
                putExtra(RUN_COMMAND_RESULT_DIRECTORY, resultDir.absolutePath)
            }
            context.startService(intent)

            TermuxPoller.await(resultFile, job.timeoutMs)
        }
    }

    private fun buildInlineCommand(job: TermuxJob, resultPath: String): String {
        val escapedCmd = job.command.replace("'", "'\\''")
        val escapedCwd = (job.workdir ?: HOME).replace("'", "'\\''")
        return """
        |{ echo "===CMD==="; echo '${escapedCmd.replace("\n", "\n")}'; } > '$resultPath' 2>&1
        |echo "===OUT===" >> '$resultPath'
        |cd '$escapedCwd' 2>>'$resultPath' && bash -c '${escapedCmd}' >> '$resultPath' 2>&1
        |ec=${'$'}?
        |echo "===EXIT===${'$'}ec" >> '$resultPath'
        """.trimMargin()
    }

    private fun buildScript(job: TermuxJob, resultPath: String): String =
        buildInlineCommand(job, resultPath)

    fun isDestructive(command: String): Boolean =
        DESTRUCTIVE_PATTERNS.any { command.trim().contains(it) }

    suspend fun runDiagnostics(): List<Diagnostic> = withContext(Dispatchers.IO) {
        if (!isTermuxInstalled()) {
            return@withContext listOf(Diagnostic("Termux", false, "Termux is not installed. Install Termux from F-Droid, then reopen this app."))
        }
        if (!isRunCommandPermissionGranted()) {
            return@withContext listOf(
                Diagnostic("Termux", true, "Termux installed"),
                Diagnostic("RUN_COMMAND permission", false, "Grant the Run command permission in Android Settings → Apps → Zot → Permissions."),
            )
        }
        val probe = runJob(
            TermuxJob(
                id = "diag-${System.currentTimeMillis()}",
                command = """
                for c in git node npm python go; do
                  if command -v ${'$'}c >/dev/null 2>&1; then echo "OK ${'$'}c"; else echo "MISSING ${'$'}c"; fi
                done
                echo "HOME_OK ${'$'}(test -d ${'$'}HOME && echo 1 || echo 0)"
                """.trimIndent(),
                workdir = HOME,
                timeoutMs = 30_000,
            ),
        )
        val out = probe.getOrNull()?.stdout ?: ""
        val lines = out.lines().filter { it.isNotBlank() }
        val diags = mutableListOf<Diagnostic>()
        diags += Diagnostic("Termux", true, "Termux installed and reachable")
        diags += Diagnostic("RUN_COMMAND permission", true, "Permission granted")
        if (out.isEmpty()) {
            diags += Diagnostic("Environment probe", false, "Could not run commands inside Termux. Open Termux once manually, then run the setup script from Settings.")
            return@withContext diags
        }
        for (tool in listOf("git", "node", "npm", "python", "go")) {
            val ok = lines.any { it.trim() == "OK $tool" }
            diags += Diagnostic(
                name = tool.replaceFirstChar { it.uppercase() },
                ok = ok,
                detail = if (ok) "$tool available" else "$tool not found — run: pkg install ${pkgFor(tool)}",
            )
        }
        diags
    }

    private fun pkgFor(tool: String) = when (tool) {
        "python" -> "python"
        else -> tool
    }
}
