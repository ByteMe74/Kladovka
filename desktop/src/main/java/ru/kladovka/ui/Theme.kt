package ru.kladovka.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun KladovkaTheme(
    darkTheme: Boolean? = null,
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    
    MaterialTheme(
        colorScheme = if (isDark) darkColorScheme() else lightColorScheme(),
        content = content
    )
}

private val lightColorScheme = lightColorScheme(
    primary = Color(0xFF2196F3),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBE2FF),
    onPrimaryContainer = Color(0xFF001D36),
    secondary = Color(0xFF7B1FA2),
    onSecondary = Color.White,
    background = Color(0xFFFFFBFE),
    onBackground = Color(0xFF000000)
)

private val darkColorScheme = darkColorScheme(
    primary = Color(0xFF90CAF9),
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF00204D),
    onPrimaryContainer = Color(0xFFDBE2FF),
    secondary = Color(0xFFE1BEE7),
    onSecondary = Color(0xFF000000),
    background = Color(0xFF121212),
    onBackground = Color(0xFFFFFFFF)
)

