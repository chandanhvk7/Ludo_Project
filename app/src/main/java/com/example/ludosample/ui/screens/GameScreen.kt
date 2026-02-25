package com.example.ludosample.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ludosample.engine.GamePhase
import com.example.ludosample.engine.PlayerColor
import com.example.ludosample.ui.components.Dice
import com.example.ludosample.ui.components.LudoBoard
import com.example.ludosample.ui.viewmodel.GameViewModel

private val colorMap = mapOf(
    PlayerColor.RED to Color(0xFFC62828),
    PlayerColor.GREEN to Color(0xFF2E7D32),
    PlayerColor.YELLOW to Color(0xFFF9A825),
    PlayerColor.BLUE to Color(0xFF1565C0),
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

    val myPlayer = gameState.players[currentPlayerId]
    val isSpectating = myPlayer?.isFinished == true || myPlayer?.isEliminated == true
    val isMyTurn = !isSpectating && gameState.currentTurnPlayerId == currentPlayerId
    val canRoll = isMyTurn && gameState.phase == GamePhase.ROLLING
    val currentPlayer = gameState.players[gameState.currentTurnPlayerId]

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF1B5E20))
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Turn indicator
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val turnColor = currentPlayer?.color?.let { colorMap[it] } ?: Color.Gray
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(turnColor)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${currentPlayer?.name ?: "?"}'s turn",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            TimerIndicator(remainingSeconds)
        }

        if (isSpectating) {
            val rank = gameState.finishOrder.indexOf(currentPlayerId) + 1
            val label = if (myPlayer?.isFinished == true) "You finished ${ordinal(rank)}!" else "You were eliminated"
            Text(
                text = label,
                color = Color(0xFFFFD600),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Board
        LudoBoard(
            boardType = gameState.boardType,
            gameState = gameState,
            validMoves = if (isMyTurn) validMoves else emptyList(),
            currentPlayerId = currentPlayerId,
            onTokenTapped = { tokenIndex -> viewModel.onTokenSelected(tokenIndex) },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Dice + status
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Dice(
                value = gameState.diceValue,
                enabled = canRoll,
                onClick = { viewModel.onDiceRoll() },
                size = 64.dp
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                val phaseText = when (gameState.phase) {
                    GamePhase.ROLLING -> if (isMyTurn) "Tap dice to roll" else "Waiting..."
                    GamePhase.MOVING -> if (isMyTurn) "Pick a token" else "Waiting..."
                    GamePhase.FINISHED -> "Game Over!"
                    GamePhase.WAITING_FOR_PLAYERS -> "Waiting for players"
                }
                Text(
                    text = phaseText,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge
                )
                if (gameState.diceValue != null && gameState.phase != GamePhase.ROLLING) {
                    Text(
                        text = "Rolled: ${gameState.diceValue}",
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Player list
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            for ((_, player) in gameState.players.entries.sortedBy { it.value.slotIndex }) {
                val pColor = colorMap[player.color] ?: Color.Gray
                val isActive = player.id == gameState.currentTurnPlayerId
                val alpha = when {
                    player.isEliminated -> 0.3f
                    player.isFinished -> 0.6f
                    else -> 1f
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(if (isActive) 28.dp else 20.dp)
                            .clip(CircleShape)
                            .background(pColor.copy(alpha = alpha))
                    )
                    Text(
                        text = player.name,
                        color = Color.White.copy(alpha = alpha),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                    )
                    val tokensHome = player.tokens.count { it.isHome }
                    if (tokensHome > 0) {
                        Text(
                            text = "$tokensHome/4",
                            color = Color.White.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }

        // Game over overlay
        if (gameState.phase == GamePhase.FINISHED) {
            Spacer(modifier = Modifier.height(8.dp))
            val winner = gameState.players[gameState.winner]
            Text(
                text = "${winner?.name ?: "Unknown"} wins!",
                color = Color(0xFFFFD600),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun TimerIndicator(seconds: Int) {
    val timerColor = when {
        seconds > 10 -> Color(0xFF4CAF50)
        seconds > 5 -> Color(0xFFFFC107)
        else -> Color(0xFFF44336)
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(timerColor)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "${seconds}s",
            color = timerColor,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun ordinal(n: Int): String = when {
    n % 100 in 11..13 -> "${n}th"
    n % 10 == 1 -> "${n}st"
    n % 10 == 2 -> "${n}nd"
    n % 10 == 3 -> "${n}rd"
    else -> "${n}th"
}
