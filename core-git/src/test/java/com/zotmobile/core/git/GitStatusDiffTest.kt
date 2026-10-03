package com.zotmobile.core.git

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GitStatusDiffTest {

    @Test
    fun parseStatusBranchAndEntries() {
        val out = """
            ## main...origin/main [ahead 2, behind 1]
            M  src/App.tsx
             M src/main.ts
            ?? notes.md
            D  old/thing.txt
        """.trimIndent()
        val s = GitRepository.parseStatus(out)
        assertEquals("main", s.branch)
        assertEquals(2, s.ahead)
        assertEquals(1, s.behind)
        assertEquals(4, s.entries.size)
        assertTrue(s.entries[0].staged)
        assertTrue(s.entries[1].unstaged)
        assertTrue(s.entries[2].untracked)
        assertTrue(s.entries[3].deleted)
        assertTrue(s.hasChanges)
        assertEquals(4, s.modifiedCount)
    }

    @Test
    fun parseDiffCountsAddRemove() {
        val out = """
            diff --git a/src/App.tsx b/src/App.tsx
            --- a/src/App.tsx
            +++ b/src/App.tsx
            @@ -1,3 +1,4 @@
             line1
            -removed
            +added1
            +added2
             line4
        """.trimIndent()
        val d = GitRepository.parseDiff(out)
        assertEquals(1, d.files.size)
        assertEquals("src/App.tsx", d.files[0].path)
        assertEquals(2, d.files[0].addedLines)
        assertEquals(1, d.files[0].removedLines)
        assertEquals(2, d.totalAdded)
        assertEquals(1, d.totalRemoved)
    }

    @Test
    fun parseDiffNewFile() {
        val out = """
            diff --git a/new.txt b/new.txt
            new file mode 100644
            --- /dev/null
            +++ b/new.txt
            @@ -0,0 +1,2 @@
            +hello
            +world
        """.trimIndent()
        val d = GitRepository.parseDiff(out)
        assertTrue(d.files[0].isNew)
        assertEquals(2, d.files[0].addedLines)
    }

    @Test
    fun emptyStatusIsClean() {
        val s = GitRepository.parseStatus("## main\n")
        assertEquals("main", s.branch)
        assertTrue(!s.hasChanges)
    }
}
