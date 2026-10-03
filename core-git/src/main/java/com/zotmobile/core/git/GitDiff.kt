package com.zotmobile.core.git

/** Parsed unified diff for the diff viewer. */
data class GitDiff(
    val files: List<FileDiff>,
) {
    data class FileDiff(
        val path: String,
        val oldPath: String? = null,
        val isNew: Boolean = false,
        val isDeleted: Boolean = false,
        val isBinary: Boolean = false,
        val hunks: List<Hunk> = emptyList(),
    ) {
        val addedLines: Int get() = hunks.sumOf { h -> h.lines.count { it.kind == Line.Kind.ADD } }
        val removedLines: Int get() = hunks.sumOf { h -> h.lines.count { it.kind == Line.Kind.REMOVE } }
    }

    data class Hunk(val header: String, val lines: List<Line>)

    data class Line(val kind: Kind, val text: String) {
        enum class Kind { CONTEXT, ADD, REMOVE }
    }

    val totalAdded: Int get() = files.sumOf { it.addedLines }
    val totalRemoved: Int get() = files.sumOf { it.removedLines }
}
