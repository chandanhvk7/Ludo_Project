package com.example.ludosample.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ludosample.engine.BoardType
import com.example.ludosample.engine.GamePhase
import com.example.ludosample.engine.PlayerColor
import com.example.ludosample.ui.components.Dice
import com.example.ludosample.ui.components.LudoBoard
import com.example.ludosample.ui.theme.Accent
import com.example.ludosample.ui.theme.BoardBackdrop
import com.example.ludosample.ui.theme.SurfaceVariant
import com.example.ludosample.ui.theme.TextPrimary
import com.example.ludosample.ui.theme.TextSecondary
import com.example.ludosample.ui.viewmodel.GameViewModel
import kotlin.random.Random

private val colorMap = mapOf(
    PlayerColor.RED to Color(0xFFE53935),
    PlayerColor.GREEN to Color(0xFF43A047),
    PlayerColor.YELLOW to Color(0xFFFDD835),
    PlayerColor.BLUE to Color(0xFF1E88E5),
    PlayerColor.ORANGE to Color(0xFFEF6C00),
    PlayerColor.PURPLE to Color(0xFF6A1B9A)
)

@Composable
fun GameScreen(
    viewModel: GameViewModel,
    currentPlayerId: String,
    modifier: Modifier = Modifier
) {
    val gameState by viewModel.gameState.collectAsState()
    val validMoves by viewModel.validMoves.collectAsState()
    val remainingSeconds by viewModel.remainingSeconds.collectAsState()
    val showingNoMoves by viewModel.showingNoMoves.collectAsState()

    var diceTextVisible by remember { mutableStateOf(false) }
    LaunchedEffect(gameState.diceValue) {
        if (gameState.diceValue != null) {
            diceTextVisible = false
            delay(800)
            diceTextVisible = true
        } else {
            diceTextVisible = false
        }
    }

    val myPlayer = gameState.players[currentPlayerId]
    val isSpectating = myPlayer?.isFinished == true || myPlayer?.isEliminated == true
    val isMyTurn = !isSpectating && gameState.currentTurnPlayerId == currentPlayerId
    val canRoll = isMyTurn && gameState.phase == GamePhase.ROLLING && !showingNoMoves
    val currentPlayer = gameState.players[gameState.currentTurnPlayerId]

    var showConfetti by remember { mutableStateOf(false) }
    LaunchedEffect(gameState.phase) {
        if (gameState.phase == GamePhase.FINISHED) {
            showConfetti = true
            delay(6000)
            showConfetti = false
        }
    }

    val statusText = when {
        showingNoMoves && diceTextVisible -> "Rolled ${gameState.diceValue} — No moves!"
        gameState.phase == GamePhase.FINISHED -> "Game Over!"
        diceTextVisible && gameState.diceValue != null && isMyTurn &&
                gameState.phase == GamePhase.MOVING -> "Rolled ${gameState.diceValue} — Pick a token"
        diceTextVisible && gameState.diceValue != null -> "Rolled: ${gameState.diceValue}"
        gameState.phase == GamePhase.ROLLING && isMyTurn -> "Tap dice to roll"
        gameState.phase == GamePhase.MOVING && isMyTurn -> "Pick a token"
        gameState.phase == GamePhase.WAITING_FOR_PLAYERS -> "Waiting for players"
        else -> "Waiting..."
    }

    Box(modifier = modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BoardBackdrop),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Turn indicator
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val turnColor = currentPlayer?.color?.let { colorMap[it] } ?: Color.Gray
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(turnColor)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "${currentPlayer?.name ?: "?"}'s turn",
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            CircularTimerIndicator(remainingSeconds)
        }

        if (isSpectating) {
            val rank = gameState.finishOrder.indexOf(currentPlayerId) + 1
            val label = if (myPlayer?.isFinished == true) "You finished ${ordinal(rank)}!" else "You were eliminated"
            Text(
                text = label,
                color = Accent,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .background(SurfaceVariant, RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 2.dp)
            )
        }

        // Board with dice overlay and player corner badges
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            LudoBoard(
                boardType = gameState.boardType,
                gameState = gameState,
                validMoves = if (isMyTurn) validMoves else emptyList(),
                currentPlayerId = currentPlayerId,
                onTokenTapped = { tokenIndex -> viewModel.onTokenSelected(tokenIndex) },
                modifier = Modifier.fillMaxWidth()
            )
            Dice(
                value = gameState.diceValue,
                enabled = canRoll,
                onClick = { viewModel.onDiceRoll() },
                size = 48.dp
            )

            if (gameState.boardType == BoardType.CLASSIC) {
                val mySlot = gameState.players[currentPlayerId]?.slotIndex ?: 3
                val boardRotation = (3 - mySlot + 4) % 4
                val cornerAlignments = listOf(
                    0 to Alignment.TopStart,
                    1 to Alignment.TopEnd,
                    2 to Alignment.BottomEnd,
                    3 to Alignment.BottomStart
                )
                for ((physPos, alignment) in cornerAlignments) {
                    val slot = (physPos - boardRotation + 4) % 4
                    val player = gameState.players.values.find { it.slotIndex == slot }
                    if (player != null) {
                        val pColor = colorMap[player.color] ?: Color.Gray
                        val isActive = player.id == gameState.currentTurnPlayerId
                        val homeCount = player.tokens.count { it.isHome }
                        PlayerCornerBadge(
                            name = player.name,
                            color = pColor,
                            homeCount = homeCount,
                            isActive = isActive,
                            modifier = Modifier.align(alignment).padding(4.dp)
                        )
                    }
                }
            }
        }

        // Status text
        Text(
            text = statusText,
            color = if (showingNoMoves) Color(0xFFEF5350) else TextSecondary,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(vertical = 4.dp)
        )

        // Game over
        if (gameState.phase == GamePhase.FINISHED) {
            val winner = gameState.players[gameState.winner]
            Text(
                text = "${winner?.name ?: "Unknown"} wins!",
                color = Accent,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }

    if (showConfetti) {
        ConfettiOverlay()
    }
    } // outer Box
}

@Composable
private fun PlayerCornerBadge(
    name: String,
    color: Color,
    homeCount: Int,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    val bgColor = Color.Black.copy(alpha = 0.55f)
    val borderMod = if (isActive) Modifier.border(1.5.dp, color, RoundedCornerShape(8.dp)) else Modifier
    Row(
        modifier = modifier
            .then(borderMod)
            .background(bgColor, RoundedCornerShape(8.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = name,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
        )
        if (homeCount > 0) {
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = "\u2605$homeCount",
                color = color,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun CircularTimerIndicator(seconds: Int, maxSeconds: Int = 20) {
    val progress = seconds.toFloat() / maxSeconds
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(900, easing = LinearEasing),
        label = "timer"
    )
    val timerColor = when {
        seconds > 10 -> Color(0xFF66BB6A)
        seconds > 5 -> Color(0xFFFFCA28)
        else -> Color(0xFFEF5350)
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(42.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 3.dp.toPx()
            drawArc(
                color = timerColor.copy(alpha = 0.15f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            drawArc(
                color = timerColor,
                startAngle = -90f,
                sweepAngle = 360f * animatedProgress,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
        Text(
            text = "${seconds}s",
            color = timerColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

private class ConfettiParticle(
    var x: Float, var y: Float,
    var vx: Float, var vy: Float,
    var rot: Float, var rotSpeed: Float,
    val color: Color, val w: Float, val h: Float
)

@Composable
private fun ConfettiOverlay(modifier: Modifier = Modifier) {
    val confettiColors = listOf(
        Color(0xFFE53935), Color(0xFF43A047), Color(0xFFFDD835),
        Color(0xFF1E88E5), Color(0xFFEF6C00), Color(0xFFAB47BC)
    )

    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    val particles = remember(canvasSize) {
        if (canvasSize.width == 0) emptyList()
        else List(100) {
            ConfettiParticle(
                x = Random.nextFloat() * canvasSize.width,
                y = -Random.nextFloat() * canvasSize.height * 0.5f,
                vx = (Random.nextFloat() - 0.5f) * 5f,
                vy = Random.nextFloat() * 4f + 2f,
                rot = Random.nextFloat() * 360f,
                rotSpeed = (Random.nextFloat() - 0.5f) * 12f,
                color = confettiColors[Random.nextInt(confettiColors.size)],
                w = 6f + Random.nextFloat() * 8f,
                h = 3f + Random.nextFloat() * 5f
            )
        }
    }

    var frame by remember { mutableIntStateOf(0) }

    LaunchedEffect(canvasSize) {
        if (canvasSize.width == 0) return@LaunchedEffect
        while (true) {
            delay(16)
            frame++
            for (p in particles) {
                p.x += p.vx
                p.y += p.vy
                p.vy += 0.08f
                p.vx *= 0.99f
                p.rot += p.rotSpeed
                if (p.y > canvasSize.height + 30) {
                    p.y = -Random.nextFloat() * 40f
                    p.x = Random.nextFloat() * canvasSize.width
                    p.vy = Random.nextFloat() * 4f + 2f
                }
            }
        }
    }

    @Suppress("UNUSED_VARIABLE")
    val currentFrame = frame

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { canvasSize = it }
    ) {
        for (p in particles) {
            rotate(p.rot, pivot = Offset(p.x, p.y)) {
                drawRoundRect(
                    color = p.color,
                    topLeft = Offset(p.x - p.w / 2, p.y - p.h / 2),
                    size = Size(p.w, p.h),
                    cornerRadius = CornerRadius(1.5f)
                )
            }
        }
    }
}

private fun ordinal(n: Int): String = when {
    n % 100 in 11..13 -> "${n}th"
    n % 10 == 1 -> "${n}st"
    n % 10 == 2 -> "${n}nd"
    n % 10 == 3 -> "${n}rd"
    else -> "${n}th"
}
