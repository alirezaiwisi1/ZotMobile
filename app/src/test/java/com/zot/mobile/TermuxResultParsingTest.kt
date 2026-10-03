package com.zot.mobile

import com.zot.mobile.core.termux.TermuxPoller
import com.zot.mobile.core.termux.TermuxResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TermuxResultParsingTest {

    @Test
    fun `parses result file format`() {
        val dir = File.createTempFile("zot", "dir").apply { delete(); mkdirs() }
        val f = File(dir, "job.out")
        f.writeText(
            """
            ===CMD===
            npm test
            ===OUT===
            all tests passed
            second line
            ===EXIT===0
            """.trimIndent(),
        )
        val raw = f.readText()
        val cmd = raw.substringAfter("===CMD===\n", "").substringBefore("===OUT===").trim()
        val stdout = raw.substringAfter("===OUT===\n", "").substringBefore("===EXIT===").trim()
        val exit = raw.substringAfter("===EXIT===", "").trim().toIntOrNull() ?: -1
        assertEquals("npm test", cmd)
        assertEquals("all tests passed\nsecond line", stdout)
        assertEquals(0, exit)
    }

    @Test
    fun `nonzero exit preserved`() {
        val raw = "===CMD===\nx\n===OUT===\nerr\n===EXIT===1"
        assertEquals(1, raw.substringAfter("===EXIT===", "").trim().toIntOrNull())
    }

    @Test
    fun `termux result data class holds duration`() {
        val r = TermuxResult(0, "ok", "ls", 1234)
        assertEquals(1234, r.durationMs)
        assertEquals(0, r.exitCode)
    }
}
