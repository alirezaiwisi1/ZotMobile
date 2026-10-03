package com.zot.mobile.ui.screens.setup

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zot.mobile.ZotApplication
import com.zot.mobile.core.ai.AiProviderCatalog
import com.zot.mobile.core.security.SecretStore
import com.zot.mobile.core.termux.Diagnostic
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class SetupUiState(
    val step: Int = 0, // 0..7
    val diagnostics: List<Diagnostic> = emptyList(),
    val diagnosing: Boolean = false,
    val aiProviderId: String = "openai",
    val githubUser: String = "",
)

class SetupViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx = app as ZotApplication

    private val _state = MutableStateFlow(SetupUiState())
    val state: StateFlow<SetupUiState> = _state

    val providers = AiProviderCatalog.all

    fun next() { _state.value = _state.value.copy(step = (_state.value.step + 1).coerceAtMost(7)) }
    fun back() { _state.value = _state.value.copy(step = (_state.value.step - 1).coerceAtLeast(0)) }

    fun runChecks() = viewModelScope.launch {
        _state.value = _state.value.copy(diagnosing = true, diagnostics = emptyList())
        _state.value = _state.value.copy(diagnosing = false, diagnostics = ctx.termuxClient.runDiagnostics())
    }

    fun setProvider(id: String) = viewModelScope.launch {
        _state.value = _state.value.copy(aiProviderId = id)
        ctx.settingsRepository.setAiProvider(id)
    }

    fun saveAiKey(key: String) = viewModelScope.launch {
        ctx.secretStore.save(SecretStore.aiKey(_state.value.aiProviderId), key.trim())
        next()
    }

    fun saveGithub(token: String, user: String) = viewModelScope.launch {
        if (token.isNotBlank()) ctx.secretStore.save(SecretStore.GITHUB_TOKEN, token.trim())
        ctx.settingsRepository.setGithubUser(user.trim())
        next()
    }

    fun finish() = viewModelScope.launch {
        ctx.settingsRepository.setSetupDone()
    }
}
