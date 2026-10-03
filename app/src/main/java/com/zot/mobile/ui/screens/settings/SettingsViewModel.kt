package com.zot.mobile.ui.screens.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zot.mobile.ZotApplication
import com.zot.mobile.core.ai.AiProviderCatalog
import com.zot.mobile.core.security.SecretStore
import com.zot.mobile.core.termux.Diagnostic
import com.zot.mobile.core.termux.TermuxClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val theme: String = "system",
    val aiProviderId: String = "openai",
    val aiModel: String = "",
    val hasAiKey: Boolean = false,
    val hasGithubToken: Boolean = false,
    val diagnostics: List<Diagnostic> = emptyList(),
    val diagnosing: Boolean = false,
    val message: String? = null,
)

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx = app as ZotApplication

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state

    val providers = AiProviderCatalog.all

    init {
        viewModelScope.launch {
            ctx.settingsRepository.themeState.collect { _state.value = _state.value.copy(theme = it) }
        }
        viewModelScope.launch {
            ctx.settingsRepository.aiProvider.collect { p ->
                _state.value = _state.value.copy(
                    aiProviderId = p,
                    hasAiKey = ctx.secretStore.get(SecretStore.aiKey(p)) != null,
                )
            }
        }
        viewModelScope.launch {
            ctx.settingsRepository.aiModel.collect { _state.value = _state.value.copy(aiModel = it) }
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(hasGithubToken = ctx.secretStore.get(SecretStore.GITHUB_TOKEN) != null)
        }
    }

    fun setTheme(t: String) = viewModelScope.launch { ctx.settingsRepository.setTheme(t) }

    fun setProvider(id: String) = viewModelScope.launch {
        ctx.settingsRepository.setAiProvider(id)
        _state.value = _state.value.copy(
            aiProviderId = id,
            hasAiKey = ctx.secretStore.get(SecretStore.aiKey(id)) != null,
            aiModel = AiProviderCatalog.byId(id).defaultModel,
        )
        ctx.settingsRepository.setAiModel(AiProviderCatalog.byId(id).defaultModel)
    }

    fun setModel(m: String) = viewModelScope.launch { ctx.settingsRepository.setAiModel(m) }

    /** Stores the key in EncryptedSharedPreferences only — never in plain text or sync'd prefs. */
    fun saveAiKey(key: String) = viewModelScope.launch {
        ctx.secretStore.save(SecretStore.aiKey(_state.value.aiProviderId), key.trim())
        _state.value = _state.value.copy(hasAiKey = true, message = "API key saved securely.")
    }

    fun saveGithubToken(token: String, username: String) = viewModelScope.launch {
        ctx.secretStore.save(SecretStore.GITHUB_TOKEN, token.trim())
        ctx.settingsRepository.setGithubUser(username.trim())
        _state.value = _state.value.copy(hasGithubToken = true, message = "GitHub token saved securely.")
        // configure git credential store inside Termux (token embedded per-clone remote instead)
    }

    fun deleteSecrets() = viewModelScope.launch {
        ctx.secretStore.delete(SecretStore.GITHUB_TOKEN)
        providers.forEach { ctx.secretStore.delete(SecretStore.aiKey(it.id)) }
        _state.value = _state.value.copy(hasAiKey = false, hasGithubToken = false, message = "All credentials erased.")
    }

    fun runDiagnostics() = viewModelScope.launch {
        _state.value = _state.value.copy(diagnosing = true, diagnostics = emptyList())
        val results = ctx.termuxClient.runDiagnostics()
        _state.value = _state.value.copy(diagnosing = false, diagnostics = results)
    }
}
