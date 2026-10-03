package com.zot.mobile.core.termux

data class TermuxJob(
    val id: String,
    val command: String,
    val workdir: String? = null,
    val timeoutMs: Long = 120_000,
)

data class TermuxResult(
    val exitCode: Int,
    val stdout: String,
    val command: String,
    val durationMs: Long,
)

data class Diagnostic(val name: String, val ok: Boolean, val detail: String)

object TermuxPoller {
    suspend fun await(resultFile: java.io.File, timeoutMs: Long): TermuxResult =
        kotlinx.coroutines.withTimeout(timeoutMs) {
            val start = System.currentTimeMillis()
            while (!resultFile.exists()) kotlinx.coroutines.delay(200)
            var lastSize = -1L
            var stable = 0
            // Wait until output stops growing for ~400ms (job finished writing).
            while (stable < 2) {
                kotlinx.coroutines.delay(200)
                val size = resultFile.length()
                if (size == lastSize) stable++ else { stable = 0; lastSize = size }
            }
            val raw = resultFile.readText()
            val cmd = raw.substringAfter("===CMD===\n", "").substringBefore("===OUT===").trim()
            val stdout = raw.substringAfter("===OUT===\n", "").substringBefore("===EXIT===").trim()
            val exit = raw.substringAfter("===EXIT===", "").trim().toIntOrNull() ?: -1
            TermuxResult(exit, stdout, cmd, System.currentTimeMillis() - start)
        }
}
