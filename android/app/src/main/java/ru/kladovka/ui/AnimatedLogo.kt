package ru.kladovka.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.withTransform

/**
 * Анимированная эмблема «Кладовки» — та же коробка, что на иконке приложения:
 * слегка покачивается, вещи внутри подпрыгивают, янтарный ярлык пульсирует.
 * Цвета берутся из темы (primary — коробка, tertiary — янтарь), поэтому читается
 * и на светлом, и на тёмном фоне.
 */
@Composable
fun AnimatedLogo(modifier: Modifier = Modifier) {
    val boxColor = MaterialTheme.colorScheme.primary
    val amber = MaterialTheme.colorScheme.tertiary

    val transition = rememberInfiniteTransition(label = "logo")
    val bob by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bob"
    )
    val pop by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(850, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pop"
    )

    Canvas(modifier) {
        val w = size.width
        val h = size.height

        // Покачивание всей эмблемы
        val dy = (bob - 0.5f) * h * 0.08f
        // Вещи внутри: лёгкий подъём в такт + пульс яркости
        val lift = (pop - 0.5f) * h * 0.05f
        val alpha = 0.75f + 0.25f * pop

        val sw = w * 0.9f
        val left = w * 0.05f
        val right = left + sw
        val bodyTop = h * 0.62f
        val bodyBottom = h * 0.96f
        val lidTop = h * 0.44f
        val lidBottom = h * 0.60f
        val r = CornerRadius(sw * 0.08f, sw * 0.08f)

        withTransform({ translate(0f, dy) }) {
            // Ручка
            drawRoundRect(
                color = boxColor,
                topLeft = Offset(w * 0.42f, h * 0.30f),
                size = Size(w * 0.16f, h * 0.10f),
                cornerRadius = CornerRadius(w * 0.04f, w * 0.04f)
            )
            // Крышка
            drawRoundRect(
                color = boxColor,
                topLeft = Offset(left, lidTop),
                size = Size(sw, lidBottom - lidTop),
                cornerRadius = r
            )
            // Тело коробки
            drawRoundRect(
                color = boxColor,
                topLeft = Offset(left, bodyTop),
                size = Size(sw, bodyBottom - bodyTop),
                cornerRadius = r
            )
            // Вещи внутри (янтарные) — подпрыгивают
            val itemW = sw * 0.18f
            val itemH = h * 0.10f
            val itemY = bodyTop + h * 0.06f - lift
            val itemRadius = CornerRadius(itemW * 0.18f, itemW * 0.18f)
            drawRoundRect(amber.copy(alpha = alpha), Offset(left + sw * 0.10f, itemY), Size(itemW, itemH), cornerRadius = itemRadius)
            drawRoundRect(amber.copy(alpha = alpha), Offset(left + sw * 0.41f, itemY), Size(itemW, itemH), cornerRadius = itemRadius)
            drawRoundRect(amber.copy(alpha = alpha), Offset(left + sw * 0.72f, itemY), Size(itemW * 0.7f, itemH), cornerRadius = itemRadius)
            // Янтарный ярлык-замок на крышке
            drawCircle(
                color = amber.copy(alpha = alpha),
                radius = w * 0.055f,
                center = Offset(w * 0.5f, (lidTop + lidBottom) / 2f + h * 0.005f)
            )
        }
    }
}