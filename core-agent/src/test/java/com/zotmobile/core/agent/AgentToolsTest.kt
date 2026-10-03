package com.zotmobile.core.agent

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AgentToolsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun tools(): AgentTools {
        // The runner is only used by command/git tools which these tests don't call.
        return AgentTools(tmp.root, null)
    }

    @Test
    fun `list files shows entries`() {
        tmp.newFolder("src")
        tmp.newFile("src/Main.kt")
        tmp.newFile("README.md")
        val out = tools().listFiles(".")
        assertTrue(out.contains("dir  src"))
        assertTrue(out.contains("file README.md"))
    }

    @Test
    fun `read and write file round trip`() {
        tmp.newFile("a.txt").writeText("hello")
        assertEquals("hello", tools().readFile("a.txt"))
    }

    @Test
    fun `path traversal rejected`() {
        assertTrue(tools().readFile("../../etc/passwd").startsWith("error:"))
        assertTrue(tools().readFile("/etc/passwd").startsWith("error:"))
        assertTrue(tools().listFiles("..").startsWith("error:"))
    }

    @Test
    fun `search project finds matches with line numbers`() {
        val f = File(tmp.root, "code.kt"); f.writeText("fun main() {}\nval magic = 42\n")
        val out = tools().searchProject("magic")
        assertTrue(out.contains("code.kt:2:"))
    }

    @Test
    fun `write proposal is not applied until confirmed`() {
        val change = tools().writeFile("new.txt", "content")
        assertFalse(File(tmp.root, "new.txt").exists())
        assertTrue(tools().apply(change))
        assertEquals("content", File(tmp.root, "new.txt").readText())
    }

    @Test
    fun `reject keeps old content and revert restores`() {
        tmp.newFile("e.txt").writeText("old")
        val t = tools()
        val change = t.writeFile("e.txt", "new")
        assertTrue(t.apply(change))
        assertEquals("new", File(tmp.root, "e.txt").readText())
        assertTrue(t.revert(change))
        assertEquals("old", File(tmp.root, "e.txt").readText())
    }

    @Test
    fun `delete proposal requires approval`() {
        tmp.newFile("d.txt").writeText("x")
        val t = tools()
        val change = t.deleteFile("d.txt")
        assertTrue(File(tmp.root, "d.txt").exists())
        assertTrue(t.apply(change))
        assertFalse(File(tmp.root, "d.txt").exists())
        assertTrue(t.revert(change))
        assertEquals("x", File(tmp.root, "d.txt").readText())
    }
}

