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
import androidx.compose.material3.CircularProgressIndicator
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
import com.example.ludosample.ui.theme.Accent
import com.example.ludosample.ui.theme.Background
import com.example.ludosample.ui.theme.ErrorRed
import com.example.ludosample.ui.theme.SurfaceVariant
import com.example.ludosample.ui.theme.TextMuted
import com.example.ludosample.ui.theme.TextPrimary
import com.example.ludosample.ui.theme.TextSecondary

private val lobbyColorMap = mapOf(
    PlayerColor.RED to Color(0xFFE57373),
    PlayerColor.GREEN to Color(0xFF81C784),
    PlayerColor.YELLOW to Color(0xFFFFD54F),
    PlayerColor.BLUE to Color(0xFF64B5F6),
    PlayerColor.ORANGE to Color(0xFFFFB74D),
    PlayerColor.PURPLE to Color(0xFFCE93D8)
)

@Composable
fun LobbyScreen(
    gameState: GameState,
    playerId: String,
    isCreator: Boolean,
    isLoading: Boolean,
    errorMessage: String?,
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
            .background(Background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Error banner
        if (errorMessage != null) {
            Text(
                text = errorMessage,
                color = ErrorRed,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ErrorRed.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                    .padding(12.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Loading state
        if (isLoading && gameState.roomCode.isBlank()) {
            Spacer(modifier = Modifier.weight(1f))
            CircularProgressIndicator(color = Accent)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Setting up room...",
                color = TextSecondary,
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.weight(1f))
            return
        }

        Text(
            text = "Room Code",
            color = TextSecondary,
            style = MaterialTheme.typography.titleSmall
        )

        Text(
            text = gameState.roomCode.ifBlank { "------" },
            color = Accent,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            letterSpacing = MaterialTheme.typography.displaySmall.letterSpacing * 2
        )

        Text(
            text = "Share this code with friends",
            color = TextMuted,
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
                color = TextPrimary,
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
                    thumbColor = Accent,
                    activeTrackColor = Accent.copy(alpha = 0.6f),
                    inactiveTrackColor = SurfaceVariant
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
                color = TextPrimary,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Player list header
        Text(
            text = "Players ($playerCount/${gameState.maxPlayers})",
            color = TextSecondary,
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
                    .padding(vertical = 4.dp)
                    .background(
                        if (isMe) SurfaceVariant else Color.Transparent,
                        RoundedCornerShape(10.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(pColor)
                        .then(
                            if (isMe) Modifier.border(2.dp, TextPrimary, CircleShape)
                            else Modifier
                        )
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = player.name,
                    color = TextPrimary,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isMe) FontWeight.Bold else FontWeight.Normal
                )
                if (isHost) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "HOST",
                        color = Accent,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (isMe) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "(you)",
                        color = TextMuted,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        if (canStart) {
            Button(
                onClick = onStartGame,
                colors = ButtonDefaults.buttonColors(containerColor = Accent),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(
                    text = "Start Game",
                    color = Background,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        } else if (!isCreator) {
            Text(
                text = "Waiting for host to start...",
                color = TextSecondary,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Text(
                text = "Need at least 2 players to start",
                color = TextMuted,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
