package com.example.ludosample.ui.screens

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ludosample.engine.BoardType
import com.example.ludosample.engine.GameState
import com.example.ludosample.engine.PlayerColor

private val lobbyColorMap = mapOf(
    PlayerColor.RED to Color(0xFFC62828),
    PlayerColor.GREEN to Color(0xFF2E7D32),
    PlayerColor.YELLOW to Color(0xFFF9A825),
    PlayerColor.BLUE to Color(0xFF1565C0),
    PlayerColor.ORANGE to Color(0xFFEF6C00),
    PlayerColor.PURPLE to Color(0xFF6A1B9A)
)

@Composable
fun LobbyScreen(
    gameState: GameState,
    playerId: String,
    isCreator: Boolean,
    onPlayerCountChanged: (Int) -> Unit,
    onStartGame: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedPlayerCount by rememberSaveable { mutableIntStateOf(gameState.maxPlayers) }
    val playerCount = gameState.players.size
    val canStart = isCreator && playerCount >= 2

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF1B5E20))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Room Code",
            color = Color.White.copy(alpha = 0.7f),
            style = MaterialTheme.typography.titleSmall
        )

        Text(
            text = gameState.roomCode,
            color = Color(0xFFFFD600),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            letterSpacing = MaterialTheme.typography.displaySmall.letterSpacing * 2
        )

        Text(
            text = "Share this code with friends",
            color = Color.White.copy(alpha = 0.5f),
            style = MaterialTheme.typography.bodySmall
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Player count selector (creator only)
        if (isCreator) {
            val boardLabel = when {
                selectedPlayerCount <= 4 -> "Classic Board"
                selectedPlayerCount == 5 -> "Pentagon Board"
                else -> "Hexagon Board"
            }

            Text(
                text = "Players: $selectedPlayerCount ($boardLabel)",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium
            )

            Slider(
                value = selectedPlayerCount.toFloat(),
                onValueChange = {
                    val newCount = it.toInt()
                    selectedPlayerCount = newCount
                    onPlayerCountChanged(newCount)
                },
                valueRange = 2f..6f,
                steps = 3,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFFFFD600),
                    activeTrackColor = Color(0xFF66BB6A)
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))
        } else {
            val boardLabel = when (gameState.boardType) {
                BoardType.CLASSIC -> "Classic Board"
                BoardType.PENTA -> "Pentagon Board"
                BoardType.HEX -> "Hexagon Board"
            }
            Text(
                text = "Max players: ${gameState.maxPlayers} ($boardLabel)",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Player list
        Text(
            text = "Players ($playerCount/${gameState.maxPlayers})",
            color = Color.White,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        for ((pid, player) in gameState.players.entries.sortedBy { it.value.slotIndex }) {
            val pColor = lobbyColorMap[player.color] ?: Color.Gray
            val isMe = pid == playerId
            val isHost = pid == gameState.creatorPlayerId

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .background(
                        if (isMe) Color.White.copy(alpha = 0.1f) else Color.Transparent,
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(pColor)
                        .then(
                            if (isMe) Modifier.border(2.dp, Color.White, CircleShape)
                            else Modifier
                        )
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = player.name,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isMe) FontWeight.Bold else FontWeight.Normal
                )
                if (isHost) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "HOST",
                        color = Color(0xFFFFD600),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (isMe) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "(you)",
                        color = Color.White.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Waiting / Start
        if (canStart) {
            Button(
                onClick = onStartGame,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD600)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(
                    text = "Start Game",
                    color = Color(0xFF1B5E20),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        } else if (!isCreator) {
            Text(
                text = "Waiting for host to start...",
                color = Color.White.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Text(
                text = "Need at least 2 players to start",
                color = Color.White.copy(alpha = 0.4f),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
