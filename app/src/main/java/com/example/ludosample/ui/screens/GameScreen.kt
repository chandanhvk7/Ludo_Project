package com.example.ludosample.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ludosample.engine.ChatMessage
import com.example.ludosample.engine.ChatStickers
import com.example.ludosample.engine.GamePhase
import com.example.ludosample.engine.GameState
import com.example.ludosample.engine.Player
import com.example.ludosample.engine.PlayerColor
import com.example.ludosample.ui.components.Dice
import com.example.ludosample.ui.components.LudoBoard
import com.example.ludosample.ui.theme.Accent
import com.example.ludosample.ui.theme.BoardBackdrop
import com.example.ludosample.ui.theme.GlassBorder
import com.example.ludosample.ui.theme.GlassHighlight
import com.example.ludosample.ui.theme.GlassWhite
import com.example.ludosample.ui.theme.ErrorRed
import com.example.ludosample.ui.theme.GreenButton
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(
    viewModel: GameViewModel,
    currentPlayerId: String,
    onQuit: () -> Unit = {},
    onPlayAgainNavigate: (String) -> Unit = {},
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
    var showGameOverSheet by remember { mutableStateOf(false) }
    LaunchedEffect(gameState.phase) {
        if (gameState.phase == GamePhase.FINISHED) {
            showConfetti = true
            delay(3000)
            showGameOverSheet = true
            delay(3000)
            showConfetti = false
        }
    }

    val playAgainRoom by viewModel.playAgainRoomCode.collectAsState()
    val chatMessages by viewModel.chatMessages.collectAsState()
    val unreadCount by viewModel.unreadCount.collectAsState()
    var showChatSheet by remember { mutableStateOf(false) }

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
                    remainingSeconds = remainingSeconds,
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
                    remainingSeconds = remainingSeconds,
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
                validMoves = validMoves,
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
                    remainingSeconds = remainingSeconds,
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
                    remainingSeconds = remainingSeconds,
                    modifier = Modifier.weight(1f)
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
        }

        // Centered dice + chat icon
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
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
                Spacer(modifier = Modifier.width(12.dp))
                ChatIconButton(
                    unreadCount = unreadCount,
                    onClick = {
                        showChatSheet = true
                        viewModel.isChatOpen = true
                        viewModel.clearUnread()
                    }
                )
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

    }

    if (showConfetti) {
        ConfettiOverlay()
    }

    // Chat bottom sheet
    if (showChatSheet) {
        val chatSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        DisposableEffect(Unit) {
            onDispose {
                viewModel.isChatOpen = false
            }
        }
        ModalBottomSheet(
            onDismissRequest = {
                showChatSheet = false
                viewModel.isChatOpen = false
            },
            sheetState = chatSheetState,
            containerColor = Color(0xF0131A2A),
            contentColor = TextPrimary
        ) {
            ChatSheetContent(
                messages = chatMessages,
                currentPlayerId = currentPlayerId,
                onSendText = { text ->
                    viewModel.sendChatMessage("text", text)
                },
                onSendSticker = { key ->
                    viewModel.sendChatMessage("sticker", key)
                    showChatSheet = false
                    viewModel.isChatOpen = false
                }
            )
        }
    }

    if (showGameOverSheet) {
        val sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            confirmValueChange = { false }
        )
        ModalBottomSheet(
            onDismissRequest = { },
            sheetState = sheetState,
            containerColor = Color(0xF0131A2A),
            contentColor = TextPrimary,
            dragHandle = null
        ) {
            GameOverSheetContent(
                gameState = gameState,
                onPlayAgain = { viewModel.playAgain() },
                onGoHome = onQuit
            )
        }
    }

    LaunchedEffect(playAgainRoom) {
        val room = playAgainRoom
        if (!room.isNullOrBlank()) {
            onPlayAgainNavigate(room)
        }
    }

    } // outer Box
}

private val rankColorMap = mapOf(
    PlayerColor.RED to Color(0xFFE57373),
    PlayerColor.GREEN to Color(0xFF81C784),
    PlayerColor.YELLOW to Color(0xFFFFD54F),
    PlayerColor.BLUE to Color(0xFF64B5F6),
    PlayerColor.ORANGE to Color(0xFFFFB74D),
    PlayerColor.PURPLE to Color(0xFFCE93D8)
)

private val rankLabels = listOf("1st", "2nd", "3rd", "4th", "5th", "6th")
private val rankColors = listOf(
    Color(0xFFE8B931), Color(0xFFC0C0C0), Color(0xFFCD7F32),
    Color(0xFF8B949E), Color(0xFF6E7681), Color(0xFF545D68)
)

@Composable
private fun GameOverSheetContent(
    gameState: GameState,
    onPlayAgain: () -> Unit,
    onGoHome: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Game Over",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Accent,
            textAlign = TextAlign.Center
        )

        val winner = gameState.players[gameState.winner]
        if (winner != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${winner.name} wins!",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "Rankings", style = MaterialTheme.typography.titleSmall, color = TextSecondary)

        Spacer(modifier = Modifier.height(8.dp))

        gameState.finishOrder.forEachIndexed { index, playerId ->
            val player = gameState.players[playerId] ?: return@forEachIndexed
            val pColor = rankColorMap[player.color] ?: Color.Gray
            val isEliminated = player.isEliminated

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .background(SurfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = rankLabels.getOrElse(index) { "${index + 1}th" },
                    color = rankColors.getOrElse(index) { TextSecondary },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(48.dp)
                )
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(pColor.copy(alpha = if (isEliminated) 0.4f else 1f))
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = player.name,
                        color = TextPrimary.copy(alpha = if (isEliminated) 0.5f else 1f),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    if (isEliminated) {
                        Text(
                            text = "disconnected",
                            color = ErrorRed.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "\u2694${player.kills}  \u2620${player.deaths}",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onPlayAgain,
            colors = ButtonDefaults.buttonColors(containerColor = GreenButton),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text("Play Again", style = MaterialTheme.typography.titleMedium)
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onGoHome,
            colors = ButtonDefaults.buttonColors(containerColor = SurfaceVariant),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text("Back to Home", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun PlayerProfileCard(
    player: Player,
    isActive: Boolean,
    remainingSeconds: Int = 30,
    maxSeconds: Int = 30,
    modifier: Modifier = Modifier
) {
    val pColor = colorMap[player.color] ?: Color.Gray
    val shape = RoundedCornerShape(12.dp)

    val timerProgress = if (isActive) remainingSeconds.toFloat() / maxSeconds else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = timerProgress,
        animationSpec = tween(900, easing = LinearEasing),
        label = "profileTimer"
    )

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, if (isActive) pColor.copy(alpha = 0.3f) else GlassBorder, shape)
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
            if (player.disconnectedAt > 0 && !player.isEliminated && !player.isFinished) {
                val infiniteTransition = rememberInfiniteTransition(label = "dcBlink")
                val alpha by infiniteTransition.animateFloat(
                    initialValue = 0.4f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(800, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "dcAlpha"
                )
                Text(
                    text = "Reconnecting\u2026",
                    color = Color(0xFFFF9800).copy(alpha = alpha),
                    fontSize = 9.sp,
                    lineHeight = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                val sorted = player.tokens.sortedWith(compareByDescending<com.example.ludosample.engine.Token> { it.isHome }
                    .thenByDescending { it.position >= 0 })
                sorted.forEach { token ->
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
    } // Row

    if (isActive) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val cornerPx = 12.dp.toPx()
            val strokeW = 3.dp.toPx()
            val w = size.width - strokeW
            val h = size.height - strokeW
            val totalPerimeter = 2 * (w + h - 4 * cornerPx) + 2 * Math.PI.toFloat() * cornerPx
            val drawLength = totalPerimeter * animatedProgress
            drawRoundRect(
                color = pColor,
                topLeft = Offset(strokeW / 2, strokeW / 2),
                size = Size(w, h),
                cornerRadius = CornerRadius(cornerPx),
                style = Stroke(
                    width = strokeW,
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(drawLength, totalPerimeter), 0f
                    )
                )
            )
        }
    }
    } // Box
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
    val infiniteTransition = rememberInfiniteTransition(label = "diceBlink")
    val blinkAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "diceTrayBlink"
    )
    val borderColor = if (canRoll) currentPlayerColor.copy(alpha = blinkAlpha) else GlassBorder
    Box(
        modifier = modifier
            .border(3.dp, borderColor, shape)
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
            CircularTimerIndicator(remainingSeconds, isMyTurn = isMyTurn)
        }
    }
}

@Composable
private fun CircularTimerIndicator(
    seconds: Int,
    maxSeconds: Int = 30,
    isMyTurn: Boolean = false
) {
    val progress = seconds.toFloat() / maxSeconds
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(900, easing = LinearEasing),
        label = "timer"
    )
    val timerColor = if (isMyTurn) {
        when {
            seconds > 15 -> Color(0xFF66BB6A)
            seconds > 7 -> Color(0xFFFFCA28)
            else -> Color(0xFFEF5350)
        }
    } else {
        Color(0xFF6E7681)
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

// ── Chat UI Components ───────────────────────────────────────────

@Composable
private fun ChatIconButton(
    unreadCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick
        )
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(
                    brush = Brush.verticalGradient(listOf(GlassHighlight, GlassWhite)),
                    shape = CircleShape
                )
                .border(1.dp, GlassBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Chat,
                contentDescription = "Chat",
                tint = TextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
        if (unreadCount > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 2.dp, y = (-2).dp)
                    .size(18.dp)
                    .background(ErrorRed, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (unreadCount > 9) "9+" else "$unreadCount",
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ChatSheetContent(
    messages: List<ChatMessage>,
    currentPlayerId: String,
    onSendText: (String) -> Unit,
    onSendSticker: (String) -> Unit
) {
    val listState = rememberLazyListState()
    var inputText by remember { mutableStateOf("") }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .padding(horizontal = 12.dp)
    ) {
        // Sticker row
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            items(ChatStickers.all.entries.toList()) { (key, display) ->
                val isEmoji = display.length <= 2 || display.any { it.code > 0x2000 }
                Box(
                    modifier = Modifier
                        .background(SurfaceVariant, RoundedCornerShape(20.dp))
                        .clickable { onSendSticker(key) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = display,
                        fontSize = if (isEmoji) 22.sp else 13.sp,
                        fontWeight = if (!isEmoji) FontWeight.Bold else null,
                        color = if (!isEmoji) Accent else Color.Unspecified
                    )
                }
            }
        }

        // Message list
        LazyColumn(
            state = listState,
            modifier = Modifier
                .height(200.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                val isMe = msg.senderId == currentPlayerId
                val senderColor = try {
                    colorMap[PlayerColor.valueOf(msg.senderColor)] ?: Color.Gray
                } catch (_: Exception) { Color.Gray }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
                ) {
                    if (!isMe) {
                        Text(
                            text = msg.senderName,
                            color = senderColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 8.dp, bottom = 1.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .widthIn(max = 260.dp)
                            .background(
                                if (isMe) senderColor.copy(alpha = 0.2f)
                                else SurfaceVariant,
                                RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        if (msg.type == "sticker") {
                            val display = ChatStickers.display(msg.content)
                            Text(text = display, fontSize = 28.sp)
                        } else {
                            Text(
                                text = msg.content,
                                color = TextPrimary,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }

        // Input row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = { Text("Message...", color = TextSecondary, fontSize = 13.sp) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = Accent,
                    unfocusedBorderColor = GlassBorder,
                    cursorColor = Accent
                ),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = {
                    val text = inputText.trim()
                    if (text.isNotBlank()) {
                        onSendText(text)
                        inputText = ""
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Filled.Send,
                    contentDescription = "Send",
                    tint = Accent
                )
            }
        }

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
