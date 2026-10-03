package com.zotmobile.core.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Secure secret storage. Secrets (AI API keys, GitHub token, git identity) are
 * AES-GCM encrypted with a key held in the Android Keystore (hardware-backed
 * where available) and persisted in app-private preferences. They never appear
 * in logs, repositories, or exports.
 */
class SecretStore(private val context: Context) {

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("zot_secrets", Context.MODE_PRIVATE)
    }

    companion object {
        private const val KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "zot_master_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORM = "AES/GCM/NoPadding"
        const val SECRET_AI_KEY = "ai.apiKey."
        const val SECRET_GITHUB_TOKEN = "github.token"
        const val SECRET_GIT_NAME = "git.name"
        const val SECRET_GIT_EMAIL = "git.email"
    }

    fun put(key: String, value: String) {
        if (value.isBlank()) { prefs.edit().remove(key).apply(); return }
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, masterKey())
        val ct = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val iv = cipher.iv
        prefs.edit()
            .putString(key + ".ct", Base64.getEncoder().encodeToString(ct))
            .putString(key + ".iv", Base64.getEncoder().encodeToString(iv))
            .apply()
    }

    fun get(key: String): String? {
        val ct = prefs.getString(key + ".ct", null) ?: return null
        val iv = prefs.getString(key + ".iv", null) ?: return null
        return try {
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(Cipher.DECRYPT_MODE, masterKey(), GCMParameterSpec(128, Base64.getDecoder().decode(iv)))
            String(cipher.doFinal(Base64.getDecoder().decode(ct)), Charsets.UTF_8)
        } catch (_: Exception) { null }
    }

    fun remove(key: String) { prefs.edit().remove(key + ".ct").remove(key + ".iv").apply() }

    fun clearAll() { prefs.edit().clear().apply() }

    private fun masterKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        gen.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }
}
