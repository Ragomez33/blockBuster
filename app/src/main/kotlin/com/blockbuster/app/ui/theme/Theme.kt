package com.blockbuster.app.ui.theme

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

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF7EE2B8),
    onPrimary = Color(0xFF00382A),
    primaryContainer = Color(0xFF0E4D3B),
    onPrimaryContainer = Color(0xFFB6F2D8),
    secondary = Color(0xFFB8C7FF),
    onSecondary = Color(0xFF102448),
    secondaryContainer = Color(0xFF263A60),
    onSecondaryContainer = Color(0xFFD9E2FF),
    tertiary = Color(0xFFF3B7C8),
    onTertiary = Color(0xFF4A2430),
    background = Color(0xFF0A0E13),
    onBackground = Color(0xFFE6EDF5),
    surface = Color(0xFF111820),
    onSurface = Color(0xFFE6EDF5),
    surfaceVariant = Color(0xFF1A2430),
    onSurfaceVariant = Color(0xFFA8B5C4),
    outline = Color(0xFF455465),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF006B52),
    onPrimary = Color.White,
    secondary = Color(0xFF4A5E8C),
    tertiary = Color(0xFF7A4A57),
)

@Composable
fun BlockBusterTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme || isSystemInDarkTheme()) {
                dynamicDarkColorScheme(context)
            } else {
                dynamicLightColorScheme(context)
            }
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
