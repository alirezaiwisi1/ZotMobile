package com.zot.mobile.core.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking


private val Context.dataStore by preferencesDataStore(name = "zot_settings")

class SettingsRepository(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    object Keys {
        val theme = stringPreferencesKey("theme") // system | light | dark
        val aiProvider = stringPreferencesKey("ai_provider")
        val aiModel = stringPreferencesKey("ai_model")
        val setupDone = stringPreferencesKey("setup_done")
        val githubUser = stringPreferencesKey("github_user")
    }

    val themeState = context.dataStore.data.map { it[Keys.theme] ?: "system" }
    val aiProvider = context.dataStore.data.map { it[Keys.aiProvider] ?: "openai" }
    val aiModel = context.dataStore.data.map { it[Keys.aiModel] ?: "" }
    val setupDone = context.dataStore.data.map { it[Keys.setupDone] == "1" }
    val githubUser = context.dataStore.data.map { it[Keys.githubUser] ?: "" }

    fun themeFlow() = themeState.map { it }

    suspend fun providerBlocking(): String = runBlocking { context.dataStore.data.map { it[Keys.aiProvider] ?: "openai" }.first() }
    suspend fun modelBlocking(): String = runBlocking { context.dataStore.data.map { it[Keys.aiModel] ?: "" }.first() }

    suspend fun setTheme(v: String) = context.dataStore.edit { it[Keys.theme] = v }
    suspend fun setAiProvider(v: String) = context.dataStore.edit { it[Keys.aiProvider] = v }
    suspend fun setAiModel(v: String) = context.dataStore.edit { it[Keys.aiModel] = v }
    suspend fun setSetupDone() = context.dataStore.edit { it[Keys.setupDone] = "1" }
    suspend fun setGithubUser(v: String) = context.dataStore.edit { it[Keys.githubUser] = v }
}
