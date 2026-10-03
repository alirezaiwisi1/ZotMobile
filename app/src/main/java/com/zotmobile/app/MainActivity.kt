package com.zotmobile.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.zotmobile.app.ui.SetupWizardScreen
import com.zotmobile.app.ui.ZotMobileApp
import com.zotmobile.app.ui.theme.ZotTheme

class MainActivity : ComponentActivity() {

    private var setupDone by mutableStateOf<Boolean?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as ZotApp
        setupDone = app.secretStore.get("setup.done") == "1"
        setContent {
            ZotTheme {
                when (setupDone) {
                    null -> {}
                    true -> ZotMobileApp()
                    false -> SetupWizardScreen(onDone = {
                        app.secretStore.put("setup.done", "1")
                        setupDone = true
                    })
                }
            }
        }
    }
}
