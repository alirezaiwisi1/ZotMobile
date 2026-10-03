package com.zotmobile.core.termux

import android.content.Context

/**
 * Environment diagnostics for the setup wizard and the status screen.
 * Each check yields a human-readable status; the UI renders check/cross/fix.
 */
data class Diagnostic(
    val id: String,
    val label: String,
    val ok: Boolean,
    val detail: String,
    val fixHint: String,
)

class EnvironmentDiagnostics(private val context: Context) {

    /** Runs all checks synchronously (safe to call from IO dispatcher). */
    fun runAll(): List<Diagnostic> = buildList {
        val termux = TermuxCommandRunner.isTermuxInstalled(context)
        add(Diagnostic(
            "termux", "Termux", termux,
            if (termux) "Installed" else "Not installed",
            if (termux) "" else "Install Termux from F-Droid, then retry.",
        ))
        val api = TermuxCommandRunner.isTermuxApiInstalled(context)
        add(Diagnostic(
            "termux_api", "Termux:API", api,
            if (api) "Installed" else "Not installed",
            if (api) "" else "Install the Termux:API add-on app from F-Droid (needed to run commands).",
        ))
        if (termux) {
            for (tool in listOf("git" to "Git", "node" to "Node.js", "python" to "Python")) {
                val found = probeTermuxTool(tool.first)
                add(Diagnostic(
                    "tool_" + tool.first, tool.second, found,
                    if (found) "Available in Termux" else "Not found",
                    if (found) "" else "Open Termux and run: pkg install ${tool.first}",
                ))
            }
        }
        val dirs = context.filesDir
        add(Diagnostic(
            "workspace", "Project directory", dirs.isDirectory && dirs.canWrite(),
            "App workspace: " + dirs.absolutePath,
            if (dirs.canWrite()) "" else "Storage unavailable — free up space and retry.",
        ))
    }

    /** Termux tool presence probe. Reads Termux's installed-packages listing if reachable. */
    private fun probeTermuxTool(tool: String): Boolean {
        val pkgFile = java.io.File("/data/data/com.termux/files/usr/bin/" + tool)
        if (pkgFile.exists()) return true
        return false
    }
}
