package com.zot.mobile.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Color(0xFF00639B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCCE5FF),
    secondary = Color(0xFF51606F),
    surface = Color(0xFFFCFCFF),
    surfaceVariant = Color(0xFFDFE2EB),
    background = Color(0xFFFCFCFF),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF97CBFF),
    onPrimary = Color(0xFF003353),
    primaryContainer = Color(0xFF004A76),
    secondary = Color(0xFFB8C8D9),
    surface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFF43474E),
    background = Color(0xFF1A1C1E),
)

// Slightly rounded shapes per design requirements
private val ZotShapes = androidx.compose.material3.Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
)

private val Int.dp get() = androidx.compose.ui.unit.Dp(this.toFloat())

@Composable
fun ZotTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        shapes = ZotShapes,
        content = content,
    )
}
