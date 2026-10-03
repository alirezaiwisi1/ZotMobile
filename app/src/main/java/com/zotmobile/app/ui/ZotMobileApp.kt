package com.zotmobile.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.ForkRight
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.zotmobile.app.ZotApp

data class Dest(val route: String, val label: String, val icon: ImageVector)

private val destinations = listOf(
    Dest("chat", "Agent", Icons.Filled.Chat),
    Dest("projects", "Projects", Icons.Filled.Folder),
    Dest("files", "Files", Icons.Filled.ForkRight),
    Dest("terminal", "Run", Icons.Filled.Terminal),
    Dest("settings", "Settings", Icons.Filled.Settings),
)

@Composable
fun ZotMobileApp() {
    val app = LocalContext.current.applicationContext as ZotApp
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    val showBar = destinations.any { it.route == current }

    Scaffold(
        bottomBar = {
            if (showBar) {
                NavigationBar {
                    destinations.forEach { d ->
                        NavigationBarItem(
                            selected = current == d.route,
                            onClick = {
                                nav.navigate(d.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(d.icon, contentDescription = d.label) },
                            label = { Text(d.label) },
                        )
                    }
                }
            }
        },
    ) { pad ->
        NavHost(navController = nav, startDestination = "chat", modifier = Modifier.padding(pad)) {
            composable("chat") { ChatScreen(app) }
            composable("projects") { ProjectsScreen(app) }
            composable("files") { FilesScreen(app) }
            composable("terminal") { TerminalScreen(app) }
            composable("settings") { SettingsScreen(app) }
        }
    }
}
