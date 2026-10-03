package com.zotmobile.core.github

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Minimal GitHub REST client. Authenticates with a fine-grained personal
 * access token (repo scope for private repos, public-repo read for public).
 * The token is never logged and lives only in Android Keystore-backed storage
 * (see core-security). Clone URLs embed the token as a temporary credential
 * that is removed from the remote URL right after clone/fetch.
 */
class GitHubClient(
    private val tokenProvider: () -> String?,
) {
    private val json = Json { ignoreUnknownKeys = true }

    data class Repo(
        val fullName: String,
        val description: String,
        val isPrivate: Boolean,
        val defaultBranch: String,
        val cloneUrl: String,
        val updatedAt: Long,
    )

    sealed interface AuthCheck {
        data object Ok : AuthCheck
        data class Invalid(val reason: String) : AuthCheck
    }

    suspend fun validateToken(): AuthCheck = withContext(Dispatchers.IO) {
        val token = tokenProvider() ?: return@withContext AuthCheck.Invalid("No token configured yet.")
        val (code, body) = request("https://api.github.com/user", token)
        when (code) {
            200 -> AuthCheck.Ok
            401 -> AuthCheck.Invalid("The token was rejected by GitHub. Generate a new one and paste it in Settings.")
            else -> AuthCheck.Invalid("GitHub check failed (HTTP $code). Check your connection.")
        }
    }

    suspend fun listUserRepos(): Result<List<Repo>> = withContext(Dispatchers.IO) {
        val token = tokenProvider()
        val (code, body) = request("https://api.github.com/user/repos?per_page=100&sort=updated", token)
        when {
            code == 401 -> Result.failure(Exception(
                "GitHub rejected the token. Generate a new one at github.com/settings/tokens and paste it in Settings."))
            code != 200 -> Result.failure(Exception("Could not load repositories from GitHub (HTTP $code). Check your connection."))
            else -> Result.success(parseRepos(body))
        }
    }

    suspend fun searchRepos(query: String): Result<List<Repo>> = withContext(Dispatchers.IO) {
        val token = tokenProvider()
        val q = URLEncoder.encode(query, "UTF-8")
        val (code, body) = request("https://api.github.com/search/repositories?q=$q&per_page=20", token)
        when {
            code != 200 -> Result.failure(Exception("GitHub search failed (HTTP $code). Check your connection."))
            else -> Result.success(parseRepos(json.parseToJsonElement(body).jsonObject["items"]?.jsonArray?.toString() ?: "[]"))
        }
    }

    /** Authenticated HTTPS clone URL. Never persist this URL with the token embedded. */
    fun authenticatedCloneUrl(cloneUrl: String): String? {
        val token = tokenProvider() ?: return cloneUrl
        return cloneUrl.replaceFirst("https://", "https://x-access-token:" + token + "@")
    }

    /** Clone URL with the token stripped, safe to store/log. */
    fun sanitizedUrl(url: String): String =
        url.replace(Regex("https://x-access-token:[^@]+@"), "https://").replace(Regex("https://[^@:]+:[^@]+@"), "https://")

    private fun parseRepos(body: String): List<Repo> {
        val arr = json.parseToJsonElement(body).jsonArray
        return arr.map { el ->
            val o = el.jsonObject
            Repo(
                fullName = o["full_name"]?.jsonPrimitive?.content ?: "?",
                description = o["description"]?.jsonPrimitive?.content ?: "",
                isPrivate = o["private"]?.jsonPrimitive?.content == "true",
                defaultBranch = o["default_branch"]?.jsonPrimitive?.content ?: "main",
                cloneUrl = o["clone_url"]?.jsonPrimitive?.content ?: "",
                updatedAt = 0L,
            )
        }
    }

    private fun request(url: String, token: String?): Pair<Int, String> {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "ZotMobile")
            if (!token.isNullOrBlank()) conn.setRequestProperty("Authorization", "Bearer " + token)
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.readText().orEmpty()
            return code to body
        } finally {
            conn.disconnect()
        }
    }
}
