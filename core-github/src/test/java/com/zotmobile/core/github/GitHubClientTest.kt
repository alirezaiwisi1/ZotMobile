package com.zotmobile.core.github

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubClientTest {

    @Test
    fun `clone url embeds token`() {
        val c = GitHubClient { "tok123" }
        val u = c.authenticatedCloneUrl("https://github.com/owner/repo.git")
        assertEquals("https://x-access-token:tok123@github.com/owner/repo.git", u)
    }

    @Test
    fun `sanitized url removes token`() {
        val c = GitHubClient { "tok123" }
        val s = c.sanitizedUrl("https://x-access-token:tok123@github.com/owner/repo.git")
        assertEquals("https://github.com/owner/repo.git", s)
    }

    @Test
    fun `no token returns plain url`() {
        val c = GitHubClient { null }
        assertEquals("https://github.com/owner/repo.git", c.authenticatedCloneUrl("https://github.com/owner/repo.git"))
    }
}
