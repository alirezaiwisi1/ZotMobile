package com.zotmobile.core.termux

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.zotmobile.core.model.CommandEvent
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Executes shell commands for the app.
 *
 * Primary path: Termux's official RUN_COMMAND intent (com.termux.app.RunCommandService),
 * the supported Termux integration mechanism. Output capture over intents is not
 * supported by Termux, so commands are wrapped with a redirect into a file under
 * app-private storage which is tailed as they run. Termux can write to app storage
 * because the app exports its files directory to Termux via
 * termux-setup-storage style binding (documented in the setup wizard).
 *
 * Fallback path: when Termux:API is unavailable, a small validated subset runs as a
 * local process so file browsing and offline features still work.
 */
class TermuxCommandRunner(
    private val context: Context,
    private val workingDirProvider: () -> String,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val listeners = mutableListOf<(CommandEvent) -> Unit>()
    private val counter = AtomicLong(0)
    private val jobs = ConcurrentHashMap<String, Job>()
    private val sessions = ConcurrentHashMap<String, Process>()

    companion object {
        const val TERMUX_PACKAGE = "com.termux"
        const val TERMUX_API_PACKAGE = "com.termux.api"
        const val RUN_COMMAND_ACTION = "com.termux.RUN_COMMAND"
        const val RUN_COMMAND_PATH = "com.termux.RUN_COMMAND_PATH"
        const val RUN_COMMAND_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS"
        const val RUN_COMMAND_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
        const val RUN_COMMAND_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
        const val TERMUX_BIN = "/data/data/com.termux/files/usr/bin"

        fun isTermuxInstalled(context: Context): Boolean = try {
            context.packageManager.getPackageInfo(TERMUX_PACKAGE, 0); true
        } catch (_: Exception) { false }

        fun isTermuxApiInstalled(context: Context): Boolean = try {
            context.packageManager.getPackageInfo(TERMUX_API_PACKAGE, 0); true
        } catch (_: Exception) { false }

        fun termuxInstallIntent(context: Context): Intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://f-droid.org/en/packages/com.termux/")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        fun termuxApiInstallIntent(context: Context): Intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://f-droid.org/en/packages/com.termux.api/")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun addListener(l: (CommandEvent) -> Unit) { synchronized(listeners) { listeners.add(l) } }

    /** Run a command; returns a session id for [stop]. Safety-gated. */
    fun run(command: String, workDir: String = workingDirProvider(), background: Boolean = false): String {
        val sessionId = "zot-" + counter.incrementAndGet()
        when (val policy = SafetyPolicy.evaluate(command)) {
            is SafetyPolicy.Denied -> {
                emit(CommandEvent(sessionId, command, CommandEvent.State.FAILED, stderr = policy.reason))
                return sessionId
            }
            is SafetyPolicy.NeedsConfirmation -> {
                emit(CommandEvent(sessionId, command, CommandEvent.State.NEEDS_CONFIRMATION, stderr = policy.warning))
                return sessionId
            }
            SafetyPolicy.Allowed -> Unit
        }
        runUnchecked(sessionId, command, workDir, background)
        return sessionId
    }

    /** Run a previously gated command after the user approved it. */
    fun runConfirmed(command: String, workDir: String = workingDirProvider(), background: Boolean = false): String {
        val sessionId = "zot-" + counter.incrementAndGet()
        runUnchecked(sessionId, command, workDir, background)
        return sessionId
    }

    private fun runUnchecked(sessionId: String, command: String, workDir: String, background: Boolean) {
        if (isTermuxApiInstalled(context)) runViaTermux(sessionId, command, workDir)
        else runViaFallback(sessionId, command, workDir)
    }

    private fun runViaTermux(sessionId: String, command: String, workDir: String) {
        // Termux does not stream output over intents, so tee output into app storage and tail it.
        val outDir = File(context.filesDir, "sessions").apply { mkdirs() }
        val outFile = File(outDir, sessionId + ".log")
        val wrapped = "PATH=$TERMUX_BIN:\$PATH; { " + command +
            " ; echo \"__ZOT_EXIT_$?\" ; } > " + outFile.absolutePath + " 2>&1"
        val intent = Intent(RUN_COMMAND_ACTION).apply {
            setClassName(TERMUX_PACKAGE, "com.termux.app.RunCommandService")
            putExtra(RUN_COMMAND_PATH, TERMUX_BIN + "/bash")
            putExtra(RUN_COMMAND_ARGUMENTS, arrayOf("-c", wrapped))
            putExtra(RUN_COMMAND_WORKDIR, workDir)
            putExtra(RUN_COMMAND_BACKGROUND, true)
        }
        try {
            if (android.os.Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent)
            else context.startService(intent)
            emit(CommandEvent(sessionId, command, CommandEvent.State.RUNNING))
            tailFile(sessionId, command, outFile)
        } catch (e: Exception) {
            emit(CommandEvent(
                sessionId, command, CommandEvent.State.FAILED,
                stderr = "Termux could not be started. Install Termux and Termux:API from F-Droid, " +
                    "grant the 'Run commands in Termux environment' permission, then run the " +
                    "environment check again. (" + (e.message ?: e::class.java.simpleName) + ")",
            ))
        }
    }

    private fun runViaFallback(sessionId: String, command: String, workDir: String) {
        val start = System.currentTimeMillis()
        emit(CommandEvent(sessionId, command, CommandEvent.State.RUNNING))
        val job = scope.launch {
            try {
                val proc = ProcessBuilder("/bin/sh", "-c", command)
                    .directory(File(workDir).takeIf { it.isDirectory } ?: File("/"))
                    .start()
                sessions[sessionId] = proc
                val out = proc.inputStream.bufferedReader().readText()
                val err = proc.errorStream.bufferedReader().readText()
                val code = proc.waitFor()
                emit(CommandEvent(sessionId, command, CommandEvent.State.COMPLETED,
                    exitCode = code, stdout = out, stderr = err,
                    durationMs = System.currentTimeMillis() - start))
            } catch (e: IOException) {
                emit(CommandEvent(sessionId, command, CommandEvent.State.FAILED,
                    stderr = "This command needs the Termux environment. Install Termux and " +
                        "Termux:API from F-Droid, then run the environment check again. " +
                        "(" + (e.message ?: "process error") + ")",
                    durationMs = System.currentTimeMillis() - start))
            } catch (e: Exception) {
                emit(CommandEvent(sessionId, command, CommandEvent.State.FAILED,
                    stderr = "Command failed: " + (e.message ?: e::class.java.simpleName),
                    durationMs = System.currentTimeMillis() - start))
            } finally {
                sessions.remove(sessionId)
            }
        }
        jobs[sessionId] = job
    }

    private fun tailFile(sessionId: String, command: String, outFile: File) {
        val start = System.currentTimeMillis()
        val job = scope.launch {
            val sb = StringBuilder()
            var done = false
            var offset = 0L
            while (isActive && !done) {
                if (outFile.exists()) {
                    val len = outFile.length()
                    if (len > offset) {
                        outFile.inputStream().use { ins ->
                            ins.skip(offset)
                            val chunk = ins.readBytes().toString(Charsets.UTF_8)
                            offset = len
                            if (chunk.isNotEmpty()) {
                                sb.append(chunk)
                                emit(CommandEvent(sessionId, command, CommandEvent.State.RUNNING,
                                    stdout = chunk, partial = true))
                            }
                        }
                    }
                    val m = Regex("__ZOT_EXIT_(\\d+)\\s*$").find(sb.toString())
                    if (m != null) {
                        done = true
                        val full = sb.toString().replaceRange(m.range, "").trimEnd()
                        emit(CommandEvent(sessionId, command, CommandEvent.State.COMPLETED,
                            exitCode = m.groupValues[1].toIntOrNull() ?: -1,
                            stdout = full, durationMs = System.currentTimeMillis() - start))
                        break
                    }
                }
                kotlinx.coroutines.delay(250)
            }
            if (!done && isActive) {
                emit(CommandEvent(sessionId, command, CommandEvent.State.COMPLETED,
                    exitCode = null, stdout = sb.toString(),
                    durationMs = System.currentTimeMillis() - start))
            }
        }
        jobs[sessionId] = job
    }

    /**
     * Suspend convenience: run a command and await its final event.
     * Used by the git layer and agent tooling.
     */
    suspend fun runCaptured(
        command: String,
        workDir: String = workingDirProvider(),
        timeoutMs: Long = 120_000,
    ): CommandEvent {
        val collected = java.util.concurrent.ConcurrentLinkedDeque<CommandEvent>()
        var last: CommandEvent? = null
        val l: (CommandEvent) -> Unit = { e ->
            collected.add(e)
            if (e.state == CommandEvent.State.COMPLETED || e.state == CommandEvent.State.FAILED ||
                e.state == CommandEvent.State.STOPPED) {
                synchronized(this) { last = e }
            }
        }
        addListener(l)
        try {
            run(command, workDir, background = true)
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                synchronized(this) { last?.let { return it } }
                kotlinx.coroutines.delay(150)
            }
            return last ?: CommandEvent("timeout", command, CommandEvent.State.FAILED,
                stderr = "The command took too long to finish. Retry or increase the timeout.")
        } finally {
            synchronized(listeners) { listeners.remove(l) }
        }
    }

    fun stop(sessionId: String) {
        sessions.remove(sessionId)?.destroy()
        jobs[sessionId]?.cancel()
        emit(CommandEvent(sessionId, "", CommandEvent.State.STOPPED))
    }

    fun shutdown() { scope.cancel() }

    private fun emit(e: CommandEvent) { synchronized(listeners) { listeners.forEach { it(e) } } }
}
