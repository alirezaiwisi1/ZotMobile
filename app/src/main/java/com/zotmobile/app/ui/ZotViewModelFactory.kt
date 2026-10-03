package com.zotmobile.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.zotmobile.app.ZotApp

class ZotViewModelFactory(private val app: ZotApp) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when (modelClass) {
        ChatViewModel::class.java -> ChatViewModel(app) as T
        ProjectsViewModel::class.java -> ProjectsViewModel(app) as T
        FilesViewModel::class.java -> FilesViewModel(app) as T
        TerminalViewModel::class.java -> TerminalViewModel(app) as T
        SettingsViewModel::class.java -> SettingsViewModel(app) as T
        else -> throw IllegalArgumentException("Unknown VM " + modelClass)
    }
}
