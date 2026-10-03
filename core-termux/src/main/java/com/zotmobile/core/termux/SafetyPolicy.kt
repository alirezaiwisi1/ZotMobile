package com.zotmobile.core.termux

import kotlin.text.RegexOption.IGNORE_CASE

/**
 * Central command safety gate. Every command passes through [evaluate] before
 * execution. Destructive patterns require explicit user confirmation; a small
 * set of patterns is outright denied from the GUI.
 */
sealed interface SafetyPolicy {
    data object Allowed : SafetyPolicy
    data class NeedsConfirmation(val warning: String) : SafetyPolicy
    data class Denied(val reason: String) : SafetyPolicy

    companion object {
        private val DENIED = listOf(
            Regex("rm\\s+-rf\\s+/(?!home|data|storage|sdcard)", IGNORE_CASE),
            Regex("mkfs|dd\\s+if=|shred", IGNORE_CASE),
            Regex(":\\(\\)\\s*\\{", IGNORE_CASE),
            Regex("chmod\\s+-R\\s+777\\s+/", IGNORE_CASE),
            Regex("userdel|usermod|visudo", IGNORE_CASE),
            Regex("curl[^|]*\\|\\s*(ba)?sh", IGNORE_CASE),
            Regex("cat\\s+.*id_rsa", IGNORE_CASE),
        )

        private val DESTRUCTIVE = listOf(
            Regex("\\brm\\b", IGNORE_CASE),
            Regex("\\bgit\\s+reset\\s+--hard|git\\s+push\\s+.*--force|git\\s+clean\\b", IGNORE_CASE),
            Regex("\\bkill\\b|\\bpkill\\b", IGNORE_CASE),
            Regex("\\bgit\\s+checkout\\s+--\\s+\\.", IGNORE_CASE),
            Regex("\\bgit\\s+branch\\s+-D\\b", IGNORE_CASE),
            Regex("\\bnpm\\s+(uninstall|rm)\\b", IGNORE_CASE),
            Regex("\\bpip\\s+uninstall\\b", IGNORE_CASE),
        )

        fun evaluate(command: String): SafetyPolicy {
            DENIED.firstOrNull { it.containsMatchIn(command) }?.let {
                return Denied(
                    "This command was blocked for safety: it can damage your system or leak " +
                    "credentials. If you are sure you need it, run it manually inside Termux."
                )
            }
            DESTRUCTIVE.firstOrNull { it.containsMatchIn(command) }?.let {
                return NeedsConfirmation("This command can change or delete files. Confirm to run it.")
            }
            return Allowed
        }
    }
}
