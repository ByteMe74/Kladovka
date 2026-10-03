package ru.kladovka.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Режим темы: следовать системе или принудительно светлая/тёмная. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Фирменная тема «Кладовка»: глубокий бирюзовый + тёплый янтарь,
 * скруглённые формы и плотная типографика. Единый вид на любых устройствах.
 */

private val LightColors = lightColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF00696B),
    onPrimary = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    primaryContainer = androidx.compose.ui.graphics.Color(0xFF9CF1F2),
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFF002020),
    secondary = androidx.compose.ui.graphics.Color(0xFF4A6363),
    onSecondary = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFFCCE8E8),
    onSecondaryContainer = androidx.compose.ui.graphics.Color(0xFF051F1F),
    tertiary = androidx.compose.ui.graphics.Color(0xFF8B5000),
    onTertiary = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    tertiaryContainer = androidx.compose.ui.graphics.Color(0xFFFFDCC1),
    onTertiaryContainer = androidx.compose.ui.graphics.Color(0xFF2E1600),
    error = androidx.compose.ui.graphics.Color(0xFFBA1A1A),
    onError = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    errorContainer = androidx.compose.ui.graphics.Color(0xFFFFDAD6),
    onErrorContainer = androidx.compose.ui.graphics.Color(0xFF410002),
    background = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    onBackground = androidx.compose.ui.graphics.Color(0xFF101414),
    surface = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    onSurface = androidx.compose.ui.graphics.Color(0xFF101414),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFFD7E2E1),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF333D3C),
    outline = androidx.compose.ui.graphics.Color(0xFF6B7473),
    outlineVariant = androidx.compose.ui.graphics.Color(0xFFBEC9C8),
    surfaceTint = androidx.compose.ui.graphics.Color(0xFF00696B),
    inverseSurface = androidx.compose.ui.graphics.Color(0xFF2D3131),
    inverseOnSurface = androidx.compose.ui.graphics.Color(0xFFEFF1F0),
    inversePrimary = androidx.compose.ui.graphics.Color(0xFF4DD4D6),
    scrim = androidx.compose.ui.graphics.Color(0xFF000000),
    surfaceContainerLowest = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    surfaceContainerLow = androidx.compose.ui.graphics.Color(0xFFF4F6F5),
    surfaceContainer = androidx.compose.ui.graphics.Color(0xFFEEF1F0),
    surfaceContainerHigh = androidx.compose.ui.graphics.Color(0xFFE8EBEA),
    surfaceContainerHighest = androidx.compose.ui.graphics.Color(0xFFE2E5E4)
)

private val DarkColors = darkColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF5DD5D7),
    onPrimary = androidx.compose.ui.graphics.Color(0xFF003738),
    primaryContainer = androidx.compose.ui.graphics.Color(0xFF004F50),
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFF9CF1F2),
    secondary = androidx.compose.ui.graphics.Color(0xFFB1CCCB),
    onSecondary = androidx.compose.ui.graphics.Color(0xFF1C3534),
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFF334B4B),
    onSecondaryContainer = androidx.compose.ui.graphics.Color(0xFFCCE8E8),
    tertiary = androidx.compose.ui.graphics.Color(0xFFFFB876),
    onTertiary = androidx.compose.ui.graphics.Color(0xFF4A2800),
    tertiaryContainer = androidx.compose.ui.graphics.Color(0xFF693C00),
    onTertiaryContainer = androidx.compose.ui.graphics.Color(0xFFFFDCC1),
    error = androidx.compose.ui.graphics.Color(0xFFFFB4AB),
    onError = androidx.compose.ui.graphics.Color(0xFF690005),
    errorContainer = androidx.compose.ui.graphics.Color(0xFF93000A),
    onErrorContainer = androidx.compose.ui.graphics.Color(0xFFFFDAD6),
    background = androidx.compose.ui.graphics.Color(0xFF090C0C),
    onBackground = androidx.compose.ui.graphics.Color(0xFFF0F3F2),
    surface = androidx.compose.ui.graphics.Color(0xFF090C0C),
    onSurface = androidx.compose.ui.graphics.Color(0xFFF0F3F2),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFF3B4544),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFFC4CFCD),
    outline = androidx.compose.ui.graphics.Color(0xFF8C9694),
    outlineVariant = androidx.compose.ui.graphics.Color(0xFF3F4948),
    surfaceTint = androidx.compose.ui.graphics.Color(0xFF5DD5D7),
    inverseSurface = androidx.compose.ui.graphics.Color(0xFFE0E3E2),
    inverseOnSurface = androidx.compose.ui.graphics.Color(0xFF2D3131),
    inversePrimary = androidx.compose.ui.graphics.Color(0xFF00696B),
    scrim = androidx.compose.ui.graphics.Color(0xFF000000),
    surfaceContainerLowest = androidx.compose.ui.graphics.Color(0xFF040707),
    surfaceContainerLow = androidx.compose.ui.graphics.Color(0xFF111515),
    surfaceContainer = androidx.compose.ui.graphics.Color(0xFF151A19),
    surfaceContainerHigh = androidx.compose.ui.graphics.Color(0xFF1A1F1E),
    surfaceContainerHighest = androidx.compose.ui.graphics.Color(0xFF1F2423)
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