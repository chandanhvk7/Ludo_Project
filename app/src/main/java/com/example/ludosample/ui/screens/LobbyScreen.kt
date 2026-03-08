package com.example.ludosample.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ludosample.engine.BoardType
import com.example.ludosample.engine.GameState
import com.example.ludosample.engine.PlayerColor
import com.example.ludosample.ui.theme.Accent
import com.example.ludosample.ui.theme.Background
import com.example.ludosample.ui.theme.ErrorRed
import com.example.ludosample.ui.theme.GlassBorder
import com.example.ludosample.ui.theme.GlassHighlight
import com.example.ludosample.ui.theme.GlassWhite
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

        val clipboardManager = LocalClipboardManager.current
        val context = LocalContext.current
        var showCopied by remember { mutableStateOf(false) }
        val roomCode = gameState.roomCode

        LaunchedEffect(showCopied) {
            if (showCopied) {
                delay(2000)
                showCopied = false
            }
        }

        Text(
            text = "Room Code",
            color = TextSecondary,
            style = MaterialTheme.typography.titleSmall
        )

        Text(
            text = roomCode.ifBlank { "------" },
            color = Accent,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            letterSpacing = MaterialTheme.typography.displaySmall.letterSpacing * 2
        )

        if (showCopied) {
            Text(
                text = "Copied!",
                color = Accent.copy(alpha = 0.8f),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        } else {
            Text(
                text = "Tap below to copy or share",
                color = TextMuted,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (roomCode.isNotBlank()) {
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {

                Box(
                    modifier = Modifier
                        .border(1.dp, GlassBorder, RoundedCornerShape(10.dp))
                        .background(
                            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                listOf(GlassHighlight, GlassWhite)
                            ),
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clickable {
                            clipboardManager.setText(AnnotatedString(roomCode))
                            showCopied = true
                        }
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = "Copy",
                            tint = TextPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Copy",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Box(
                    modifier = Modifier
                        .border(1.dp, Accent.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                        .background(
                            Accent.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clickable {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    "Join my Ludo game!\n\nhttps://chandanhvk7.github.io/Ludo_Project/join?code=$roomCode\n\nRoom Code: $roomCode"
                                )
                            }
                            context.startActivity(
                                Intent.createChooser(shareIntent, "Share room code")
                            )
                        }
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = "Share",
                            tint = Accent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Share",
                            color = Accent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

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
