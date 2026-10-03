
package com.zotmobile.core.ai

/** Built-in provider catalog. All are OpenAI-compatible except Anthropic and Gemini. */
class DefaultProviderRegistry : AiProviderRegistry {

    private val infos = listOf(
        ProviderInfo(
            id = "openai", displayName = "OpenAI",
            defaultBaseUrl = "https://api.openai.com/v1",
            defaultModel = "gpt-4o-mini", needsApiKey = true,
            docsHint = "Create a key at platform.openai.com/api-keys",
        ),
        ProviderInfo(
            id = "anthropic", displayName = "Anthropic Claude",
            defaultBaseUrl = "https://api.anthropic.com/v1",
            defaultModel = "claude-sonnet-4-5", needsApiKey = true,
            docsHint = "Create a key at console.anthropic.com",
        ),
        ProviderInfo(
            id = "gemini", displayName = "Google Gemini",
            defaultBaseUrl = "https://generativelanguage.googleapis.com/v1beta/openai",
            defaultModel = "gemini-2.0-flash", needsApiKey = true,
            docsHint = "Create a key at aistudio.google.com/apikey",
        ),
        ProviderInfo(
            id = "openrouter", displayName = "OpenRouter (many models)",
            defaultBaseUrl = "https://openrouter.ai/api/v1",
            defaultModel = "anthropic/claude-3.5-sonnet", needsApiKey = true,
            docsHint = "Create a key at openrouter.ai/keys",
        ),
        ProviderInfo(
            id = "ollama", displayName = "Ollama (local model)",
            defaultBaseUrl = "http://127.0.0.1:11434/v1",
            defaultModel = "qwen2.5-coder:7b", needsApiKey = false,
            docsHint = "Run: ollama serve inside Termux, then pull a model",
        ),
        ProviderInfo(
            id = "custom", displayName = "Custom OpenAI-compatible endpoint",
            defaultBaseUrl = "", defaultModel = "", needsApiKey = true,
            docsHint = "Any server exposing /chat/completions",
        ),
    )

    override fun providers(): List<ProviderInfo> = infos

    override fun create(providerId: String): AiProvider = when (providerId) {
        "anthropic" -> AnthropicProvider(infos.first { it.id == providerId })
        // Gemini and others expose OpenAI-shaped endpoints for these defaults today;
        // dedicated classes can be added without touching callers.
        else -> OpenAiCompatProvider(infos.first { it.id == providerId })
    }
}
