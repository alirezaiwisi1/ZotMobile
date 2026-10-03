package com.zot.mobile

import com.zot.mobile.core.ai.AiProvider
import com.zot.mobile.core.ai.AiProviderCatalog
import com.zot.mobile.core.ai.AnthropicWire
import com.zot.mobile.core.ai.GoogleWire
import com.zot.mobile.core.ai.OpenAiWire
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiProviderTest {

    @Test
    fun `openai wire encodes system and messages`() {
        val body = OpenAiWire().encodeRequest(
            AiProviderCatalog.byId("openai"), "gpt-4o-mini",
            listOf(com.zot.mobile.core.ai.ChatMessage("user", "hello")), "You are helpful",
        )
        val json = Json.parseToJsonElement(body).jsonObject
        assertEquals("gpt-4o-mini", json["model"]!!.jsonPrimitive.content)
        val messages = json["messages"]!!.jsonArray
        assertEquals(2, messages.size)
        assertEquals("system", messages[0].jsonObject["role"]!!.jsonPrimitive.content)
    }

    @Test
    fun `anthropic wire maps roles and sets max tokens`() {
        val body = AnthropicWire().encodeRequest(
            AiProviderCatalog.byId("anthropic"), "claude-test",
            listOf(
                com.zot.mobile.core.ai.ChatMessage("user", "hi"),
                com.zot.mobile.core.ai.ChatMessage("assistant", "hello"),
            ), "sys",
        )
        val json = Json.parseToJsonElement(body).jsonObject
        assertEquals(8192, json["max_tokens"]!!.jsonPrimitive.content.toInt())
        assertEquals("sys", json["system"]!!.jsonPrimitive.content)
        assertEquals("assistant", json["messages"]!!.jsonArray[1].jsonObject["role"]!!.jsonPrimitive.content)
    }

    @Test
    fun `google wire decodes candidates response`() {
        val out = GoogleWire().decodeResponse(
            """{"candidates":[{"content":{"parts":[{"text":"answer"}]}}]}""",
        )
        assertEquals("answer", out)
    }

    @Test
    fun `openai wire decodes chat completions response`() {
        val out = OpenAiWire().decodeResponse(
            """{"choices":[{"message":{"content":"hi there"}}]}""",
        )
        assertEquals("hi there", out)
    }

    @Test
    fun `catalog contains multiple providers and custom endpoint`() {
        assertTrue(AiProviderCatalog.all.size >= 5)
        val custom = AiProviderCatalog.byId("custom")
        assertTrue(custom.baseUrl.startsWith("http"))
    }

    @Test
    fun `decode of malformed response is empty not crash`() {
        assertEquals("", OpenAiWire().decodeResponse("not json"))
        assertEquals("", AnthropicWire().decodeResponse("{}"))
    }
}
