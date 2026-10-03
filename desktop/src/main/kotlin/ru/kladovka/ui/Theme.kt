package ru.kladovka.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Фирменная тема «Кладовка»: глубокий бирюзовый + тёплый янтарь.
 * Палитра перенесена из Android-приложения без изменений, чтобы приложения
 * на телефоне и компьютере выглядели одинаково.
 *
 * Сам переключатель тем живёт в слое данных (ru.kladovka.data.ThemeMode) —
 * так настройки не зависят от Compose.
 */

private val LightColors = lightColorScheme(
    primary = Color(0xFF00696B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF9CF1F2),
    onPrimaryContainer = Color(0xFF002020),
    secondary = Color(0xFF4A6363),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCCE8E8),
    onSecondaryContainer = Color(0xFF051F1F),
    tertiary = Color(0xFF8B5000),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDCC1),
    onTertiaryContainer = Color(0xFF2E1600),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF101414),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF101414),
    surfaceVariant = Color(0xFFD7E2E1),
    onSurfaceVariant = Color(0xFF333D3C),
    outline = Color(0xFF6B7473),
    outlineVariant = Color(0xFFBEC9C8),
    surfaceTint = Color(0xFF00696B),
    inverseSurface = Color(0xFF2D3131),
    inverseOnSurface = Color(0xFFEFF1F0),
    inversePrimary = Color(0xFF4DD4D6),
    scrim = Color(0xFF000000),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF4F6F5),
    surfaceContainer = Color(0xFFEEF1F0),
    surfaceContainerHigh = Color(0xFFE8EBEA),
    surfaceContainerHighest = Color(0xFFE2E5E4)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF5DD5D7),
    onPrimary = Color(0xFF003738),
    primaryContainer = Color(0xFF004F50),
    onPrimaryContainer = Color(0xFF9CF1F2),
    secondary = Color(0xFFB1CCCB),
    onSecondary = Color(0xFF1C3534),
    secondaryContainer = Color(0xFF334B4B),
    onSecondaryContainer = Color(0xFFCCE8E8),
    tertiary = Color(0xFFFFB876),
    onTertiary = Color(0xFF4A2800),
    tertiaryContainer = Color(0xFF693C00),
    onTertiaryContainer = Color(0xFFFFDCC1),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF090C0C),
    onBackground = Color(0xFFF0F3F2),
    surface = Color(0xFF090C0C),
    onSurface = Color(0xFFF0F3F2),
    surfaceVariant = Color(0xFF3B4544),
    onSurfaceVariant = Color(0xFFC4CFCD),
    outline = Color(0xFF8C9694),
    outlineVariant = Color(0xFF3F4948),
    surfaceTint = Color(0xFF5DD5D7),
    inverseSurface = Color(0xFFE0E3E2),
    inverseOnSurface = Color(0xFF2D3131),
    inversePrimary = Color(0xFF00696B),
    scrim = Color(0xFF000000),
    surfaceContainerLowest = Color(0xFF040707),
    surfaceContainerLow = Color(0xFF111515),
    surfaceContainer = Color(0xFF151A19),
    surfaceContainerHigh = Color(0xFF1A1F1E),
    surfaceContainerHighest = Color(0xFF1F2423)
)

/** Скругления: карточки 16dp, диалоги 28dp. */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/** Плотная типографика: заголовки заметнее, подписи чуть разреженнее. */
private val AppTypography = Typography().let { base ->
    base.copy(
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = base.labelLarge.copy(letterSpacing = 0.2.sp)
    )
}

@Composable
fun KladovkaTheme(
    darkTheme: Boolean? = null,
    content: @Composable () -> Unit
) {
    // null → следовать за системой; true/false → принудительно тёмная/светлая
    val useDark = darkTheme ?: isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (useDark) DarkColors else LightColors,
        shapes = AppShapes,
        typography = AppTypography,
        content = content
    )
}
