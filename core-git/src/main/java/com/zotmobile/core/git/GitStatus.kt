package com.zotmobile.core.git

/** Parsed `git status --porcelain=v1 -b` output. */
data class GitStatus(
    val branch: String,
    val ahead: Int,
    val behind: Int,
    val entries: List<StatusEntry>,
) {
    data class StatusEntry(val x: Char, val y: Char, val path: String) {
        val staged: Boolean get() = x != ' '
        val unstaged: Boolean get() = y != ' '
        val untracked: Boolean get() = x == '?'
        val deleted: Boolean get() = y == 'D' || x == 'D'
        val added: Boolean get() = y == 'A' || x == 'A'
        val modified: Boolean get() = y == 'M' || x == 'M'
        val label: String get() = when {
            untracked -> "new"
            deleted -> "deleted"
            added -> "added"
            modified -> "modified"
            else -> "" + x + y
        }
    }

    val hasChanges: Boolean get() = entries.isNotEmpty()
    val modifiedCount: Int get() = entries.size
}
