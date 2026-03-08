package com.example.ludosample.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ludosample.engine.GamePhase
import com.example.ludosample.engine.Player
import com.example.ludosample.engine.PlayerColor
import com.example.ludosample.ui.components.Dice
import com.example.ludosample.ui.components.LudoBoard
import com.example.ludosample.ui.theme.Accent
import com.example.ludosample.ui.theme.BoardBackdrop
import com.example.ludosample.ui.theme.GlassBorder
import com.example.ludosample.ui.theme.GlassHighlight
import com.example.ludosample.ui.theme.GlassWhite
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
    onQuit: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val gameState by viewModel.gameState.collectAsState()
    val validMoves by viewModel.validMoves.collectAsState()
    val remainingSeconds by viewModel.remainingSeconds.collectAsState()
    val showingNoMoves by viewModel.showingNoMoves.collectAsState()
    val lastDiceValue by viewModel.lastDiceValue.collectAsState()
    val rollId by viewModel.rollCount.collectAsState()

    var showQuitDialog by remember { mutableStateOf(false) }
    val myPlayer = gameState.players[currentPlayerId]
    val isSpectating = myPlayer?.isFinished == true || myPlayer?.isEliminated == true

    BackHandler {
        if (isSpectating || gameState.phase == GamePhase.FINISHED) {
            onQuit()
        } else {
            showQuitDialog = true
        }
    }

    if (showQuitDialog) {
        AlertDialog(
            onDismissRequest = { showQuitDialog = false },
            title = { Text("Leave Game?", color = TextPrimary) },
            text = {
                Text(
                    "Your tokens will be removed and you will be eliminated from this game.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showQuitDialog = false
                    viewModel.quitGame()
                    onQuit()
                }) {
                    Text("Quit", color = Color(0xFFEF5350))
                }
            },
            dismissButton = {
                TextButton(onClick = { showQuitDialog = false }) {
                    Text("Stay", color = Accent)
                }
            },
            containerColor = SurfaceVariant,
            shape = RoundedCornerShape(16.dp)
        )
    }

    var diceTextVisible by remember { mutableStateOf(false) }
    LaunchedEffect(rollId) {
        if (rollId > 0) {
            diceTextVisible = false
            delay(800)
            diceTextVisible = true
        }
    }
    LaunchedEffect(gameState.diceValue) {
        if (gameState.diceValue == null) {
            diceTextVisible = false
        }
    }

    val isMyTurn = !isSpectating && gameState.currentTurnPlayerId == currentPlayerId
    val canRoll = isMyTurn && gameState.phase == GamePhase.ROLLING && !showingNoMoves

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

    // Determine player placement by physical board position (after rotation)
    val allPlayers = gameState.players.values.toList()
    val me = allPlayers.find { it.id == currentPlayerId }
    val mySlot = me?.slotIndex ?: 3
    val boardRotation = (3 - mySlot + 4) % 4

    // Map each player to their physical position on the rotated board
    // Physical positions: 0=top-left, 1=top-right, 2=bottom-right, 3=bottom-left
    val physicalPositions = allPlayers.associateWith { player ->
        (player.slotIndex + boardRotation) % 4
    }
    val topLeft = allPlayers.find { physicalPositions[it] == 0 }
    val topRight = allPlayers.find { physicalPositions[it] == 1 }
    val bottomRight = allPlayers.find { physicalPositions[it] == 2 }
    val bottomLeft = allPlayers.find { physicalPositions[it] == 3 }

    val turnPlayer = gameState.players[gameState.currentTurnPlayerId]
    val turnColor = turnPlayer?.let { colorMap[it.color] } ?: Color.Gray
    val displayDice = gameState.diceValue ?: lastDiceValue

    Box(modifier = modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BoardBackdrop),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Top row: top-left and top-right players
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (topLeft != null) {
                PlayerProfileCard(
                    player = topLeft,
                    isActive = topLeft.id == gameState.currentTurnPlayerId,
                    modifier = Modifier.weight(1f)
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.width(8.dp))
            if (topRight != null) {
                PlayerProfileCard(
                    player = topRight,
                    isActive = topRight.id == gameState.currentTurnPlayerId,
                    modifier = Modifier.weight(1f)
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
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

        Spacer(modifier = Modifier.height(4.dp))

        // Board
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
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Bottom row: bottom-left and bottom-right players
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (bottomLeft != null) {
                PlayerProfileCard(
                    player = bottomLeft,
                    isActive = bottomLeft.id == gameState.currentTurnPlayerId,
                    modifier = Modifier.weight(1f)
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.width(8.dp))
            if (bottomRight != null) {
                PlayerProfileCard(
                    player = bottomRight,
                    isActive = bottomRight.id == gameState.currentTurnPlayerId,
                    modifier = Modifier.weight(1f)
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
        }

        // Centered dice
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            GlassDiceContainer(
                diceValue = displayDice,
                rollId = rollId,
                canRoll = canRoll,
                isMyTurn = isMyTurn,
                onRoll = { viewModel.onDiceRoll() },
                remainingSeconds = remainingSeconds,
                currentPlayerColor = turnColor
            )
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
private fun PlayerProfileCard(
    player: Player,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    val pColor = colorMap[player.color] ?: Color.Gray
    val shape = RoundedCornerShape(12.dp)
    val borderMod = if (isActive) Modifier.border(2.dp, pColor, shape) else Modifier.border(1.dp, GlassBorder, shape)

    Row(
        modifier = modifier
            .then(borderMod)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(GlassHighlight, GlassWhite)
                ),
                shape = shape
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Faux-3D avatar circle
        val initial = player.name.firstOrNull()?.uppercase() ?: "?"
        Box(
            modifier = Modifier.size(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val r = size.width / 2
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(pColor.copy(alpha = 0.9f), pColor.copy(alpha = 0.5f)),
                        center = Offset(r * 0.7f, r * 0.6f),
                        radius = r * 1.2f
                    ),
                    radius = r,
                    center = Offset(r, r)
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.35f),
                    radius = r * 0.22f,
                    center = Offset(r * 0.65f, r * 0.55f)
                )
            }
            Text(
                text = initial,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column {
            Text(
                text = player.name,
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                player.tokens.forEach { token ->
                    val indicatorSize = 10.dp
                    Box(modifier = Modifier.size(indicatorSize)) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val cr = size.width / 2
                            when {
                                token.isHome -> {
                                    drawCircle(color = pColor, radius = cr)
                                    drawCircle(color = Color.White, radius = cr * 0.4f)
                                }
                                token.position >= 0 -> {
                                    drawCircle(color = pColor, radius = cr)
                                }
                                else -> {
                                    drawCircle(
                                        color = pColor.copy(alpha = 0.3f),
                                        radius = cr,
                                        style = Stroke(width = 2f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 2.dp)
            ) {
                Text(
                    text = "\u2694 ${player.kills}",
                    color = pColor.copy(alpha = 0.85f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "\u2620 ${player.deaths}",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun GlassDiceContainer(
    diceValue: Int?,
    rollId: Int,
    canRoll: Boolean,
    isMyTurn: Boolean,
    onRoll: () -> Unit,
    remainingSeconds: Int,
    currentPlayerColor: Color = Color.Gray,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(16.dp)
    val borderColor = if (canRoll) currentPlayerColor else GlassBorder
    Box(
        modifier = modifier
            .border(2.dp, borderColor, shape)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(GlassHighlight, GlassWhite)
                ),
                shape = shape
            )
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Dice(
                value = diceValue,
                rollId = rollId,
                enabled = isMyTurn,
                onClick = { if (canRoll) onRoll() },
                size = 48.dp,
                playerColor = currentPlayerColor
            )
            CircularTimerIndicator(remainingSeconds)
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
