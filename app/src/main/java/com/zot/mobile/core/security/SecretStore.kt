package com.zot.mobile.core.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Secure storage for API keys and GitHub tokens using Android Keystore-backed
 * EncryptedSharedPreferences. Nothing is ever stored in plain text or committed.
 */
class SecretStore(context: Context) {

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "zot_secrets",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun save(key: String, value: String) = prefs.edit().putString(key, value).apply()

    fun get(key: String): String? = prefs.getString(key, null)

    fun delete(key: String) = prefs.edit().remove(key).apply()

    companion object {
        const val GITHUB_TOKEN = "github_pat"
        const val AI_KEY_PREFIX = "ai_key_"
        fun aiKey(providerId: String) = "$AI_KEY_PREFIX$providerId"
    }
}
