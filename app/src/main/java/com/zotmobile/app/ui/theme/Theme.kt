package com.zotmobile.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Teal = Color(0xFF5EEAD4)
private val Ink = Color(0xFF10151C)
private val Paper = Color(0xFFFAFAF7)
private val SurfaceDark = Color(0xFF161B22)
private val SurfaceLight = Color(0xFFFFFFFF)
private val Accent = Color(0xFF7C9CF5)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF0F766E),
    secondary = Accent,
    background = Paper,
    surface = SurfaceLight,
    surfaceVariant = Color(0xFFEFF2F5),
    error = Color(0xFFB3261E),
)

private val DarkScheme = darkColorScheme(
    primary = Teal,
    secondary = Accent,
    background = Ink,
    surface = SurfaceDark,
    surfaceVariant = Color(0xFF1F2630),
    error = Color(0xFFF2B8B5),
)

@Composable
fun ZotTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        content = content,
    )
}
