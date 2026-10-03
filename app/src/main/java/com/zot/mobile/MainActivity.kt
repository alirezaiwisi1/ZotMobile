package com.zot.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zot.mobile.ui.ZotApp
import com.zot.mobile.ui.theme.ZotTheme
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settingsRepo = (application as ZotApplication).settingsRepository
        val theme = runBlocking { settingsRepo.themeFlow() }
        setContent {
            val themeSetting by settingsRepo.themeState.collectAsStateWithLifecycle(initialValue = theme)
            val dark = when (themeSetting) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }
            ZotTheme(darkTheme = dark) {
                ZotApp()
            }
        }
    }
}
