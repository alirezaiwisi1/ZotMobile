package com.zot.mobile.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.zot.mobile.ui.screens.chat.ChatScreen
import com.zot.mobile.ui.screens.files.FilesScreen
import com.zot.mobile.ui.screens.settings.SettingsScreen
import com.zot.mobile.ui.screens.setup.SetupScreen
import com.zot.mobile.ui.screens.terminal.TerminalScreen

sealed class Tab(val route: String, val label: String, val icon: @Composable () -> Unit) {
    data object Chat : Tab("chat", "Agent", { Icon(Icons.Default.Chat, null) })
    data object Files : Tab("files", "Files", { Icon(Icons.Default.Folder, null) })
    data object Terminal : Tab("terminal", "Run", { Icon(Icons.Default.Terminal, null) })
    data object Settings : Tab("settings", "Settings", { Icon(Icons.Default.Settings, null) })
}

@Composable
fun ZotApp(startOnSetup: Boolean = false) {
    val navController = rememberNavController()
    val tabs = listOf(Tab.Chat, Tab.Files, Tab.Terminal, Tab.Settings)
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    Scaffold(
        bottomBar = {
            if (currentRoute != "setup") {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = tab.icon,
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = if (startOnSetup) "setup" else Tab.Chat.route,
            modifier = Modifier.padding(padding),
        ) {
            composable("setup") { SetupScreen(onDone = { navController.navigate(Tab.Chat.route) { popUpTo("setup") { inclusive = true } } }) }
            composable(Tab.Chat.route) { ChatScreen() }
            composable(Tab.Files.route) { FilesScreen() }
            composable(Tab.Terminal.route) { TerminalScreen() }
            composable(Tab.Settings.route) { SettingsScreen(onRunSetup = { navController.navigate("setup") }) }
        }
    }
}
