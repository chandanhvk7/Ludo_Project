package com.example.ludosample.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ludosample.engine.GameState
import com.example.ludosample.engine.PlayerColor

private val rankColorMap = mapOf(
    PlayerColor.RED to Color(0xFFC62828),
    PlayerColor.GREEN to Color(0xFF2E7D32),
    PlayerColor.YELLOW to Color(0xFFF9A825),
    PlayerColor.BLUE to Color(0xFF1565C0),
    PlayerColor.ORANGE to Color(0xFFEF6C00),
    PlayerColor.PURPLE to Color(0xFF6A1B9A)
)

private val rankLabels = listOf("1st", "2nd", "3rd", "4th", "5th", "6th")
private val rankColors = listOf(
    Color(0xFFFFD600),
    Color(0xFFC0C0C0),
    Color(0xFFCD7F32),
    Color.White.copy(alpha = 0.6f),
    Color.White.copy(alpha = 0.5f),
    Color.White.copy(alpha = 0.4f)
)

@Composable
fun GameOverScreen(
    gameState: GameState,
    onPlayAgain: () -> Unit,
    onGoHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF1B5E20))
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Game Over",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFFFD600),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        val winner = gameState.players[gameState.winner]
        if (winner != null) {
            Text(
                text = "${winner.name} wins!",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Rankings",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White.copy(alpha = 0.8f)
        )

        Spacer(modifier = Modifier.height(16.dp))

        gameState.finishOrder.forEachIndexed { index, playerId ->
            val player = gameState.players[playerId] ?: return@forEachIndexed
            val pColor = rankColorMap[player.color] ?: Color.Gray
            val isEliminated = player.isEliminated

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = rankLabels.getOrElse(index) { "${index + 1}th" },
                    color = rankColors.getOrElse(index) { Color.White.copy(alpha = 0.4f) },
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
                Text(
                    text = player.name,
                    color = Color.White.copy(alpha = if (isEliminated) 0.5f else 1f),
                    style = MaterialTheme.typography.bodyLarge
                )
                if (isEliminated) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "disconnected",
                        color = Color(0xFFF44336).copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onPlayAgain,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF43A047)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text("Play Again", style = MaterialTheme.typography.titleMedium)
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onGoHome,
            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.15f)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text("Back to Home", color = Color.White, style = MaterialTheme.typography.titleMedium)
        }
    }
}
