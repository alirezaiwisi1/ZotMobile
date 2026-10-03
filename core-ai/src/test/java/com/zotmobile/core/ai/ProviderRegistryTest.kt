package com.zotmobile.core.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderRegistryTest {

    @Test
    fun `registry lists multiple providers`() {
        val r = DefaultProviderRegistry()
        val ids = r.providers().map { it.id }
        assertTrue(ids.containsAll(listOf("openai", "anthropic", "gemini", "openrouter", "ollama")))
        assertTrue(ids.size >= 5)
    }

    @Test
    fun `create returns provider for every registered id`() {
        val r = DefaultProviderRegistry()
        r.providers().forEach { info ->
            val p = r.create(info.id)
            assertNotNull(p)
            assertEquals(info.id, p.info.id)
        }
    }

    @Test
    fun `anthropic provider type is dedicated`() {
        val p = DefaultProviderRegistry().create("anthropic")
        assertTrue(p is AnthropicProvider)
    }

    @Test
    fun `local ollama needs no api key`() {
        val info = DefaultProviderRegistry().providers().first { it.id == "ollama" }
        assertTrue(!info.needsApiKey)
    }
}
