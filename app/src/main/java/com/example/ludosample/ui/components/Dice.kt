package com.example.ludosample.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.random.Random
import kotlinx.coroutines.launch

@Composable
fun Dice(
    value: Int?,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    playerColor: Color = Color(0xFF1A1A1A),
    rollId: Int = 0
) {
    var isRolling by remember { mutableStateOf(false) }
    var displayValue by remember { mutableIntStateOf(value ?: 0) }
    val animRotZ = remember { Animatable(0f) }
    val animRotX = remember { Animatable(0f) }
    val animScale = remember { Animatable(1f) }
    val animShakeX = remember { Animatable(0f) }
    val animShakeY = remember { Animatable(0f) }

    LaunchedEffect(rollId) {
        if (value != null && value in 1..6) {
            isRolling = true
            launch { animScale.animateTo(1.18f, tween(100)) }

            repeat(8) { i ->
                displayValue = (1..6).random()
                val speed = 30 + i * 8
                launch {
                    animRotX.animateTo(
                        animRotX.value + 180f,
                        tween(speed, easing = LinearEasing)
                    )
                }
                launch {
                    animShakeX.animateTo(
                        (Random.nextFloat() - 0.5f) * 6f,
                        tween(speed / 2)
                    )
                }
                launch {
                    animShakeY.animateTo(
                        (Random.nextFloat() - 0.5f) * 6f,
                        tween(speed / 2)
                    )
                }
                animRotZ.animateTo(
                    animRotZ.value + 45f,
                    tween(speed, easing = LinearEasing)
                )
            }

            displayValue = value

            launch { animShakeX.animateTo(0f, tween(80)) }
            launch { animShakeY.animateTo(0f, tween(80)) }
            launch { animRotX.animateTo(0f, tween(150)) }
            launch {
                animRotZ.animateTo(
                    0f,
                    spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                )
            }
            animScale.animateTo(
                1f,
                spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = 250f
                )
            )
            isRolling = false
        }
    }

    val dotColor = ensureContrast(playerColor)

    Canvas(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                rotationZ = animRotZ.value
                rotationX = animRotX.value
                scaleX = animScale.value
                scaleY = animScale.value
                translationX = animShakeX.value
                translationY = animShakeY.value
                cameraDistance = 12f * density
                alpha = if (enabled || isRolling) 1f else 0.5f
            }
            .then(if (enabled && !isRolling) Modifier.clickable { onClick() } else Modifier)
    ) {
        val s = this.size.width
        val cornerR = s * 0.15f
        val dotRadius = s * 0.07f

        // Face gradient (consistent ivory)
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFFF5F5F0), Color(0xFFD8D4C8)),
                start = Offset(0f, 0f),
                end = Offset(s, s)
            ),
            cornerRadius = CornerRadius(cornerR),
            size = this.size
        )
        // Inner shadow overlay
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(Color.Transparent, Color(0x22000000)),
                start = Offset(0f, 0f),
                end = Offset(s, s)
            ),
            cornerRadius = CornerRadius(cornerR),
            size = this.size
        )
        // Top-left highlight edge
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(Color.White.copy(alpha = 0.6f), Color.Transparent),
                start = Offset(0f, 0f),
                end = Offset(s * 0.5f, s * 0.5f)
            ),
            cornerRadius = CornerRadius(cornerR),
            size = this.size,
            style = Stroke(width = s * 0.03f)
        )
        // Beveled stroke
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF606060), Color(0xFF303030)),
                start = Offset(0f, 0f),
                end = Offset(s, s)
            ),
            cornerRadius = CornerRadius(cornerR),
            size = this.size,
            style = Stroke(width = s * 0.025f)
        )

        val showValue = if (isRolling) displayValue else (value ?: 0)
        if (showValue in 1..6) {
            drawDiceDots(showValue, dotColor, dotRadius, s)
        } else {
            drawCircle(
                color = dotColor.copy(alpha = 0.3f),
                radius = s * 0.15f,
                center = Offset(s / 2, s / 2)
            )
        }
    }
}

private fun DrawScope.drawDiceDots(value: Int, color: Color, radius: Float, s: Float) {
    val cx = s / 2
    val q1 = s * 0.28f
    val q3 = s * 0.72f

    val positions: List<Offset> = when (value) {
        1 -> listOf(Offset(cx, cx))
        2 -> listOf(Offset(q1, q3), Offset(q3, q1))
        3 -> listOf(Offset(q1, q3), Offset(cx, cx), Offset(q3, q1))
        4 -> listOf(Offset(q1, q1), Offset(q3, q1), Offset(q1, q3), Offset(q3, q3))
        5 -> listOf(Offset(q1, q1), Offset(q3, q1), Offset(cx, cx), Offset(q1, q3), Offset(q3, q3))
        6 -> listOf(
            Offset(q1, q1), Offset(q3, q1),
            Offset(q1, cx), Offset(q3, cx),
            Offset(q1, q3), Offset(q3, q3)
        )
        else -> emptyList()
    }

    for (pos in positions) {
        drawCircle(color = Color(0x33000000), radius = radius, center = pos + Offset(1f, 1.5f))
        drawCircle(color = color, radius = radius, center = pos)
    }
}

private fun ensureContrast(color: Color): Color {
    val luminance = 0.299f * color.red + 0.587f * color.green + 0.114f * color.blue
    if (luminance > 0.6f) {
        return Color(
            red = (color.red * 0.55f).coerceIn(0f, 1f),
            green = (color.green * 0.55f).coerceIn(0f, 1f),
            blue = (color.blue * 0.55f).coerceIn(0f, 1f),
            alpha = color.alpha
        )
    }
    return color
}
