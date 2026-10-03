package com.zot.mobile.core.ai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.contentOrNull
import java.io.File

// ─── Provider abstraction ────────────────────────────────────────────────

/** A single AI provider definition. Nothing about a specific vendor is hard-coded in app logic. */
data class AiProvider(
    val id: String,
    val label: String,
    val baseUrl: String,
    val wire: Wire, // which request shape to use
    val defaultModel: String,
) {
    enum class Wire { OPENAI_CHAT, ANTHROPIC_MESSAGES, GOOGLE_GENERATE }
}

/** Built-in catalog. Users can add OpenAI-compatible custom endpoints too. */
object AiProviderCatalog {
    val all = listOf(
        AiProvider("openai", "OpenAI", "https://api.openai.com/v1", AiProvider.Wire.OPENAI_CHAT, "gpt-4o-mini"),
        AiProvider("anthropic", "Anthropic", "https://api.anthropic.com/v1", AiProvider.Wire.ANTHROPIC_MESSAGES, "claude-sonnet-4-20250514"),
        AiProvider("google", "Google AI", "https://generativelanguage.googleapis.com/v1beta", AiProvider.Wire.GOOGLE_GENERATE, "gemini-1.5-flash"),
        AiProvider("openrouter", "OpenRouter", "https://openrouter.ai/api/v1", AiProvider.Wire.OPENAI_CHAT, "openai/gpt-4o-mini"),
        AiProvider("groq", "Groq", "https://api.groq.com/openai/v1", AiProvider.Wire.OPENAI_CHAT, "llama-3.1-8b-instant"),
        AiProvider("custom", "Custom OpenAI-compatible", "http://localhost:8080/v1", AiProvider.Wire.OPENAI_CHAT, ""),
    )
    fun byId(id: String) = all.firstOrNull { it.id == id } ?: all.first()
}

// ─── Wire protocol translation ───────────────────────────────────────────

@Serializable
data class ChatMessage(val role: String, val content: String)

interface WireEncoder {
    fun encodeRequest(provider: AiProvider, model: String, messages: List<ChatMessage>, system: String): String
    fun decodeResponse(body: String): String
}

class OpenAiWire : WireEncoder {
    override fun encodeRequest(provider: AiProvider, model: String, messages: List<ChatMessage>, system: String): String =
        Json.encodeToString(
            JsonObject.serializer(),
            buildJsonObject {
                put("model", model)
                put("messages", buildJsonArray {
                    if (system.isNotBlank()) add(buildJsonObject { put("role", "system"); put("content", system) })
                    messages.forEach {
                        add(buildJsonObject { put("role", it.role); put("content", it.content) })
                    }
                })
            },
        )

    override fun decodeResponse(body: String): String = runCatching {
        Json.parseToJsonElement(body).jsonObject["choices"]!!
        .jsonArray.first().jsonObject["message"]!!.jsonObject["content"]!!.jsonPrimitive.content
    }.getOrDefault("")
}

class AnthropicWire : WireEncoder {
    override fun encodeRequest(provider: AiProvider, model: String, messages: List<ChatMessage>, system: String): String =
        Json.encodeToString(
            JsonObject.serializer(),
            buildJsonObject {
                put("model", model)
                if (system.isNotBlank()) put("system", system)
                put("max_tokens", 8192)
                put("messages", buildJsonArray {
                    messages.forEach {
                        add(buildJsonObject {
                            put("role", if (it.role == "assistant") "assistant" else "user")
                            put("content", it.content)
                        })
                    }
                })
            },
        )

    override fun decodeResponse(body: String): String = runCatching {
        Json.parseToJsonElement(body).jsonObject["content"]!!
        .jsonArray.first().jsonObject["text"]!!.jsonPrimitive.content
    }.getOrDefault("")
}

class GoogleWire : WireEncoder {
    override fun encodeRequest(provider: AiProvider, model: String, messages: List<ChatMessage>, system: String): String =
        Json.encodeToString(
            JsonObject.serializer(),
            buildJsonObject {
                if (system.isNotBlank()) {
                    put("systemInstruction", buildJsonObject {
                        put("parts", buildJsonArray { add(buildJsonObject { put("text", system) }) })
                    })
                }
                put("contents", buildJsonArray {
                    messages.forEach {
                        add(buildJsonObject {
                            put("role", if (it.role == "assistant") "model" else "user")
                            put("parts", buildJsonArray { add(buildJsonObject { put("text", it.content) }) })
                        })
                    }
                })
            },
        )

    override fun decodeResponse(body: String): String = runCatching {
        Json.parseToJsonElement(body).jsonObject["candidates"]!!
        .jsonArray.first().jsonObject["content"]!!.jsonObject["parts"]!!
        .jsonArray.first().jsonObject["text"]!!.jsonPrimitive.content
    }.getOrDefault("")
}

class AiProviderRegistry {
    fun encoderFor(provider: AiProvider): WireEncoder = when (provider.wire) {
        AiProvider.Wire.OPENAI_CHAT -> OpenAiWire()
        AiProvider.Wire.ANTHROPIC_MESSAGES -> AnthropicWire()
        AiProvider.Wire.GOOGLE_GENERATE -> GoogleWire()
    }

    fun endpoint(provider: AiProvider, model: String): String = when (provider.wire) {
        AiProvider.Wire.OPENAI_CHAT -> "${provider.baseUrl}/chat/completions"
        AiProvider.Wire.ANTHROPIC_MESSAGES -> "${provider.baseUrl}/messages"
        AiProvider.Wire.GOOGLE_GENERATE -> "${provider.baseUrl}/models/$model:generateContent"
    }
}

// ─── Agent loop ──────────────────────────────────────────────────────────

/**
 * The agent system prompt mirrors zot's harness philosophy: the model plans,
 * proposes exact file edits, and only then runs them — each edit is surfaced
 * to the user as a reviewable patch.
 */
object AgentPrompt {
    const val SYSTEM = """
You are Zot Mobile, a coding agent running on the user's Android device inside Termux.
You operate on the project at the given working directory.

Rules:
1. To read files, list directories, or search, request a tool call — do not guess file contents.
2. Before modifying files, briefly state which files you will change and why.
3. Emit file changes ONLY as fenced diff blocks (unified diff format) prefixed with a line 'PATCH <relative-path>'.
4. Emit shell commands ONLY as fenced blocks prefixed with 'RUN '.
5. Never run destructive commands (rm -rf, mkfs, dd) — refuse instead.
6. After changes, suggest running tests or build commands.
Keep answers concise for a mobile screen.
"""
}

/**
 * Tool execution loop: the model can request READ / LIST / GREP / PATCH / RUN actions;
 * we execute them locally (via Termux for RUN, direct FS for read/list/grep through the
 * project mirror) and feed results back until the model produces a final answer.
 */
class AiClient(
    private val registry: AiProviderRegistry,
    private val secrets: com.zot.mobile.core.security.SecretStore,
) {
    private val http = okhttp3.OkHttpClient.Builder()
        .connectTimeout(java.time.Duration.ofSeconds(20))
        .readTimeout(java.time.Duration.ofSeconds(180))
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun chat(
        providerId: String,
        model: String,
        messages: List<ChatMessage>,
        projectRoot: String?,
    ): Result<String> {
        val provider = AiProviderCatalog.byId(providerId)
        val key = secrets.get(com.zot.mobile.core.security.SecretStore.aiKey(providerId))
            ?: return Result.failure(AiException("No API key configured for ${provider.label}. Add one in Settings → AI Provider."))
        val encoder = registry.encoderFor(provider)
        val body = encoder.encodeRequest(provider, model.ifBlank { provider.defaultModel }, messages, AgentPrompt.SYSTEM)
        val url = registry.endpoint(provider, model.ifBlank { provider.defaultModel })

        val request = okhttp3.Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $key")
            .apply { if (provider.wire == AiProvider.Wire.ANTHROPIC_MESSAGES) header("x-api-key", key).header("anthropic-version", "2023-06-01") }
            .post(okhttp3.RequestBody.create(okhttp3.MediaType.Companion.run { "application/json".toMediaTypeOrNull() }, body))
            .build()

        return awaitCall(request)
    }

    private suspend fun awaitCall(request: okhttp3.Request): Result<String> =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                http.newCall(request).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) throw AiException("Provider error ${resp.code}: ${text.take(300)}")
                    text
                }
            }.recoverCatching { e ->
                if (e is AiException) throw e
                throw AiException("Network error: ${e.message}. AI features need an internet connection.")
            }
        }
}

class AiException(message: String) : Exception(message)

