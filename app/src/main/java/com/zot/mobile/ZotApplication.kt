package com.zot.mobile

import android.app.Application
import com.zot.mobile.core.ai.AiClient
import com.zot.mobile.core.ai.AiProviderRegistry
import com.zot.mobile.core.security.SecretStore
import com.zot.mobile.core.settings.SettingsRepository
import com.zot.mobile.core.termux.TermuxClient
import com.zot.mobile.data.db.ZotDatabase

class ZotApplication : Application() {
    lateinit var secretStore: SecretStore
        private set
    lateinit var settingsRepository: SettingsRepository
        private set
    lateinit var termuxClient: TermuxClient
        private set
    lateinit var aiRegistry: AiProviderRegistry
        private set
    lateinit var aiClient: AiClient
        private set
    lateinit var database: ZotDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        secretStore = SecretStore(this)
        settingsRepository = SettingsRepository(this)
        termuxClient = TermuxClient(this)
        aiRegistry = AiProviderRegistry()
        aiClient = AiClient(aiRegistry, secretStore)
        database = ZotDatabase.build(this)
    }
}
