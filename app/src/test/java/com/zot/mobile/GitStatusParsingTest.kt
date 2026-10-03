package com.zot.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercises the git porcelain-status parsing logic (mirrored from GitLayer.parse)
 * as pure string logic so it runs on the JVM without Android or Termux.
 */
class GitStatusParsingTest {

    private fun parseStatus(out: String): Pair<String, List<Triple<String, String, Boolean>>> {
        val lines = out.lines().filter { it.isNotBlank() }
        val branch = lines.firstOrNull()?.removePrefix("## ")?.substringBefore("...")?.substringBefore(" ") ?: "unknown"
        val changes = lines.drop(1).map { line ->
            Triple(
                line.drop(3).trim(),
                when (line.take(2).trim().firstOrNull()) {
                    'M' -> "modified"
                    'A', '?' -> "added"
                    'D' -> "deleted"
                    else -> "changed"
                },
                line.firstOrNull()?.let { it != ' ' && it != '?' } ?: false,
            )
        }
        return branch to changes
    }

    @Test
    fun `parses branch and changes`() {
        val out = """
            ## main...origin/main
             M src/App.tsx
            ?? new-file.txt
            D  old.txt
        """.trimIndent()
        val (branch, changes) = parseStatus(out)
        assertEquals("main", branch)
        assertEquals(3, changes.size)
        assertEquals("src/App.tsx", changes[0].first)
        assertEquals("modified", changes[0].second)
        assertEquals("added", changes[1].second)
        assertEquals("deleted", changes[2].second)
        assertTrue(changes[2].third) // staged deletion
    }

    @Test
    fun `empty status yields clean tree`() {
        val (branch, changes) = parseStatus("## main\n")
        assertEquals("main", branch)
        assertTrue(changes.isEmpty())
    }

    @Test
    fun `git error mapping produces human messages`() {
        fun map(stdout: String) = when {
            stdout.contains("not a git repository") -> "This project is not a Git repository yet."
            stdout.contains("Authentication failed") -> "GitHub authentication failed."
            stdout.contains("command not found") -> "Git was not found in the Termux environment. Install Git and run the environment check again."
            else -> "Git command failed."
        }
        assertEquals("Git was not found in the Termux environment. Install Git and run the environment check again.", map("git: command not found"))
        assertEquals("This project is not a Git repository yet.", map("fatal: not a git repository"))
        assertEquals("GitHub authentication failed.", map("Authentication failed for https://github.com/"))
    }
}
