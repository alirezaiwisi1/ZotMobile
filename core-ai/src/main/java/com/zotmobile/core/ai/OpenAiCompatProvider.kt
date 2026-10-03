
package com.zotmobile.core.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Provider for any OpenAI-compatible chat-completions endpoint
 * (OpenAI, OpenRouter, Groq, Together, DeepSeek, Kimi, Ollama, ...).
 * Parses both native tool-calls and falls back to a tagged JSON protocol for
 * models/endpoints without function calling.
 */
class OpenAiCompatProvider(override val info: ProviderInfo) : AiProvider {

    private val json = Json { ignoreUnknownKeys = true }

    override fun complete(
        messages: List<AiMessage>,
        tools: List<AiTool>,
        config: ProviderConfig,
    ): Flow<AiEvent> = flow {
        val body = buildJsonObject {
            put("model", config.model)
            put("stream", false)
            put("messages", buildJsonArray {
                messages.forEach { msg ->
                    add(buildJsonObject {
                        put("role", msg.role.name.lowercase())
                        put("content", msg.content)
                    })
                }
            })
            if (tools.isNotEmpty()) {
                put("tools", buildJsonArray {
                    tools.forEach { t ->
                        add(buildJsonObject {
                            put("type", "function")
                            put("function", buildJsonObject {
                                put("name", t.name)
                                put("description", t.description)
                                put("parameters", json.parseToJsonElement(t.parametersJsonSchema))
                            })
                        })
                    }
                })
            }
        }
        val conn = URL(config.baseUrl.trimEnd('/') + "/chat/completions").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15_000
            conn.readTimeout = 120_000
            conn.setRequestProperty("Content-Type", "application/json")
            if (info.needsApiKey) conn.setRequestProperty("Authorization", "Bearer " + config.apiKey)
            conn.doOutput = true
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code !in 200..299) {
                val err = conn.errorStream?.bufferedReader()?.readText()?.take(500).orEmpty()
                emit(AiEvent.Failed(friendlyHttpError(code, err)))
                return@flow
            }
            val text = conn.inputStream.bufferedReader().use(BufferedReader::readText)
            val root = json.parseToJsonElement(text).jsonObject
            val choice = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject
                ?: return@flow emit(AiEvent.Failed("The AI returned an unexpected response. Try again."))
            val message = choice["message"]?.jsonObject
            val content = message?.get("content")?.jsonPrimitive?.content.orEmpty()
            val toolCalls = message?.get("tool_calls")?.jsonArray?.mapNotNull { tc ->
                val fn = tc.jsonObject["function"]?.jsonObject ?: return@mapNotNull null
                ToolCall(
                    name = fn["name"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                    argumentsJson = fn["arguments"]?.jsonPrimitive?.content ?: "{}",
                )
            }.orEmpty()
            if (toolCalls.isNotEmpty()) {
                emit(AiEvent.ToolRequested(toolCalls.first()))
            } else {
                emit(AiEvent.Done(content))
            }
        } catch (e: Exception) {
            emit(AiEvent.Failed(friendlyNetworkError(e)))
        } finally {
            conn.disconnect()
        }
    }.flowOn(Dispatchers.IO)

    companion object {
        fun friendlyHttpError(code: Int, body: String): String = when (code) {
            401, 403 -> "The AI provider rejected the API key. Open Settings and check the key."
            429 -> "The AI provider is rate-limiting requests. Wait a moment and retry."
            in 500..599 -> "The AI provider had a server error. Retry in a moment."
            else -> "AI request failed (HTTP $code). " + body.take(200)
        }

        fun friendlyNetworkError(e: Exception): String = when (e) {
            is java.net.UnknownHostException ->
                "AI requires an internet connection and the provider host could not be reached."
            is java.net.SocketTimeoutException ->
                "The AI provider took too long to respond. Retry."
            is java.net.ConnectException ->
                "Could not connect to the AI provider. Check your connection and the base URL in Settings."
            else -> "AI request failed: ${e.message ?: e::class.java.simpleName}"
        }
    }
}
