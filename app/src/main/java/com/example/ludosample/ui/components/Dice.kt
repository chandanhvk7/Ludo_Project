package com.example.ludosample.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun Dice(
    value: Int?,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp
) {
    var isRolling by remember { mutableStateOf(false) }
    var displayValue by remember { mutableIntStateOf(value ?: 0) }
    val rotation = remember { Animatable(0f) }

    LaunchedEffect(value) {
        if (value != null && value in 1..6) {
            isRolling = true
            repeat(8) {
                displayValue = (1..6).random()
                rotation.animateTo(
                    rotation.value + 45f,
                    animationSpec = tween(50, easing = LinearEasing)
                )
                delay(30)
            }
            displayValue = value
            rotation.animateTo(0f, animationSpec = tween(100))
            isRolling = false
        }
    }

    val bgColor = when {
        isRolling -> Color(0xFFFFF9C4)
        enabled -> Color.White
        else -> Color(0xFFE0E0E0)
    }
    val dotColor = if (enabled || isRolling) Color(0xFF212121) else Color(0xFF9E9E9E)

    Canvas(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                rotationZ = rotation.value
                scaleX = if (isRolling) 1.1f else 1f
                scaleY = if (isRolling) 1.1f else 1f
            }
            .then(if (enabled && !isRolling) Modifier.clickable { onClick() } else Modifier)
    ) {
        val s = this.size.width
        val cornerR = s * 0.15f
        val dotRadius = s * 0.07f

        drawRoundRect(
            color = bgColor,
            cornerRadius = CornerRadius(cornerR, cornerR),
            size = this.size
        )
        drawRoundRect(
            color = Color(0xFF424242),
            cornerRadius = CornerRadius(cornerR, cornerR),
            size = this.size,
            style = Stroke(width = s * 0.04f)
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
        drawCircle(color = color, radius = radius, center = pos)
    }
}
