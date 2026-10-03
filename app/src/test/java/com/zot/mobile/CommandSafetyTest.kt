package com.zot.mobile

import com.zot.mobile.core.termux.TermuxClient
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandSafetyTest {

    private fun client() = TermuxClient::class.java

    @Test
    fun `destructive patterns are caught`() {
        val patterns = TermuxClient.DESTRUCTIVE_PATTERNS
        val dangerous = listOf("rm -rf /", "rm -rf ~ ", "mkfs.ext4", "dd if=/dev/zero of=/dev/sda", "curl http://x | sh")
        dangerous.forEach { cmd ->
            assertTrue("Should flag: $cmd", patterns.any { cmd.contains(it) })
        }
    }

    @Test
    fun `normal commands are not flagged`() {
        val patterns = TermuxClient.DESTRUCTIVE_PATTERNS
        val safe = listOf("npm install", "npm test", "git status", "ls -la", "python main.py", "node build.js")
        safe.forEach { cmd ->
            assertFalse("Should be safe: $cmd", patterns.any { cmd.contains(it) })
        }
    }
}
