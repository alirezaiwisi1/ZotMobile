
package com.zotmobile.core.ai

import kotlinx.coroutines.flow.Flow

/** Configuration for a provider instance, entered by the user in Settings. */
data class ProviderConfig(
    val providerId: String,
    val apiKey: String,
    val model: String,
    val baseUrl: String,
)

/** Static metadata about one supported provider kind. */
data class ProviderInfo(
    val id: String,
    val displayName: String,
    val defaultBaseUrl: String,
    val defaultModel: String,
    val needsApiKey: Boolean,
    val docsHint: String,
)

/**
 * Abstraction over AI backends. Implementations translate [AiMessage]/[AiTool] into
 * provider-specific HTTP calls. No provider is hard-coded into the app UI: the user
 * picks one in Settings and supplies their own key, stored securely.
 */
interface AiProvider {
    val info: ProviderInfo

    /** Send the conversation and return a stream of events (deltas, tool requests, final answer). */
    fun complete(
        messages: List<AiMessage>,
        tools: List<AiTool>,
        config: ProviderConfig,
    ): Flow<AiEvent>
}

/** Registry of available provider kinds; used by Settings to build the picker. */
interface AiProviderRegistry {
    fun providers(): List<ProviderInfo>
    fun create(providerId: String): AiProvider
}
