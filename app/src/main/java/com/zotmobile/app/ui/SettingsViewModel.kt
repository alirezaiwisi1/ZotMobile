package com.zotmobile.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zotmobile.app.ZotApp
import com.zotmobile.core.ai.ProviderInfo
import com.zotmobile.core.git.GitStatus
import com.zotmobile.core.github.GitHubClient
import com.zotmobile.core.termux.Diagnostic
import com.zotmobile.core.termux.EnvironmentDiagnostics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(private val app: ZotApp) : ViewModel() {

    private val _providers = MutableStateFlow<List<ProviderInfo>>(emptyList())
    val providers: StateFlow<List<ProviderInfo>> = _providers.asStateFlow()

    private val _selectedProvider = MutableStateFlow("")
    val selectedProvider: StateFlow<String> = _selectedProvider.asStateFlow()

    private val _diagnostics = MutableStateFlow<List<Diagnostic>>(emptyList())
    val diagnostics: StateFlow<List<Diagnostic>> = _diagnostics.asStateFlow()

    private val _githubStatus = MutableStateFlow("")
    val githubStatus: StateFlow<String> = _githubStatus.asStateFlow()

    private val _savedFlash = MutableStateFlow(false)
    val savedFlash: StateFlow<Boolean> = _savedFlash.asStateFlow()

    init {
        _providers.value = app.providerRegistry.providers()
        _selectedProvider.value = app.secretStore.get("ai.provider") ?: "openai"
        checkGithub()
        runDiagnostics()
    }

    fun selectProvider(id: String) {
        _selectedProvider.value = id
        app.secretStore.put("ai.provider", id)
    }

    fun saveAiKey(key: String) {
        app.secretStore.put(com.zotmobile.core.security.SecretStore.SECRET_AI_KEY + _selectedProvider.value, key)
        flashSaved()
    }

    fun saveModel(model: String) {
        app.secretStore.put("ai.model." + _selectedProvider.value, model)
        flashSaved()
    }

    fun saveBaseUrl(url: String) {
        app.secretStore.put("ai.baseUrl." + _selectedProvider.value, url)
        flashSaved()
    }

    fun saveGithubToken(token: String) {
        app.secretStore.put(com.zotmobile.core.security.SecretStore.SECRET_GITHUB_TOKEN, token)
        checkGithub()
        flashSaved()
    }

    fun saveGitIdentity(name: String, email: String) {
        app.secretStore.put(com.zotmobile.core.security.SecretStore.SECRET_GIT_NAME, name)
        app.secretStore.put(com.zotmobile.core.security.SecretStore.SECRET_GIT_EMAIL, email)
        flashSaved()
    }

    private fun flashSaved() {
        _savedFlash.value = true
        viewModelScope.launch {
            kotlinx.coroutines.delay(1500)
            _savedFlash.value = false
        }
    }

    fun checkGithub() {
        viewModelScope.launch {
            _githubStatus.value = "Checking…"
            val r = app.gitHubClient.validateToken()
            _githubStatus.value = when (r) {
                is GitHubClient.AuthCheck.Ok -> "✓ Connected to GitHub"
                is GitHubClient.AuthCheck.Invalid -> r.reason
            }
        }
    }

    fun runDiagnostics() {
        viewModelScope.launch {
            _diagnostics.value = withContext(Dispatchers.IO) {
                EnvironmentDiagnostics(app).runAll()
            }
        }
    }
}
