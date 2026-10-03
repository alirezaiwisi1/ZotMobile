
package com.zotmobile.core.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.add
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.HttpURLConnection
import java.net.URL

/**
 * Anthropic Messages API provider. System prompt is passed via the top-level
 * system field; tool use round-trips map to Anthropic's content blocks.
 */
class AnthropicProvider(override val info: ProviderInfo) : AiProvider {

    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    override fun complete(
        messages: List<AiMessage>,
        tools: List<AiTool>,
        config: ProviderConfig,
    ) = flow {
        val system = messages.firstOrNull { it.role == Role.SYSTEM }?.content
        val chat = messages.filter { it.role != Role.SYSTEM }
        val body = buildJsonObject {
            put("model", config.model)
            put("max_tokens", 8192)
            if (!system.isNullOrBlank()) put("system", system)
            put("messages", buildJsonArray {
                chat.forEach { m ->
                    add(buildJsonObject {
                        put("role", if (m.role == Role.ASSISTANT) "assistant" else "user")
                        put("content", m.content)
                    })
                }
            })
            if (tools.isNotEmpty()) {
                put("tools", buildJsonArray {
                    tools.forEach { t ->
                        add(buildJsonObject {
                            put("name", t.name)
                            put("description", t.description)
                            put("input_schema", json.parseToJsonElement(t.parametersJsonSchema))
                        })
                    }
                })
            }
        }
        val conn = URL(config.baseUrl.trimEnd('/') + "/messages").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15_000
            conn.readTimeout = 120_000
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("x-api-key", config.apiKey)
            conn.setRequestProperty("anthropic-version", "2023-06-01")
            conn.doOutput = true
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code !in 200..299) {
                val err = conn.errorStream?.bufferedReader()?.readText()?.take(500).orEmpty()
                emit(AiEvent.Failed(OpenAiCompatProvider.friendlyHttpError(code, err)))
                return@flow
            }
            val root = json.parseToJsonElement(conn.inputStream.bufferedReader().readText()).jsonObject
            val content = root["content"]?.jsonArray.orEmpty()
            val text = content.filter { it.jsonObject["type"]?.jsonPrimitive?.content == "text" }
                .joinToString("") { it.jsonObject["text"]?.jsonPrimitive?.content ?: "" }
            val toolUse = content.firstOrNull { it.jsonObject["type"]?.jsonPrimitive?.content == "tool_use" }
            if (toolUse != null) {
                val tu = toolUse.jsonObject
                emit(AiEvent.ToolRequested(ToolCall(
                    name = tu["name"]?.jsonPrimitive?.content ?: "",
                    argumentsJson = tu["input"]?.jsonObject?.toString() ?: "{}",
                )))
            } else {
                emit(AiEvent.Done(text))
            }
        } catch (e: Exception) {
            emit(AiEvent.Failed(OpenAiCompatProvider.friendlyNetworkError(e)))
        } finally {
            conn.disconnect()
        }
    }.flowOn(Dispatchers.IO)
}
