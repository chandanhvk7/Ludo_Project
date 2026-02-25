package com.example.ludosample.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ludosample.engine.Player
import com.example.ludosample.engine.PlayerColor
import com.example.ludosample.ui.theme.SurfaceVariant
import com.example.ludosample.ui.theme.TextPrimary
import com.example.ludosample.ui.theme.TextSecondary

private val infoColorMap = mapOf(
    PlayerColor.RED to Color(0xFFE57373),
    PlayerColor.GREEN to Color(0xFF81C784),
    PlayerColor.YELLOW to Color(0xFFFFD54F),
    PlayerColor.BLUE to Color(0xFF64B5F6),
    PlayerColor.ORANGE to Color(0xFFFFB74D),
    PlayerColor.PURPLE to Color(0xFFCE93D8)
)

@Composable
fun PlayerInfoBar(
    players: List<Player>,
    currentTurnPlayerId: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        for (player in players.sortedBy { it.slotIndex }) {
            PlayerInfoChip(
                player = player,
                isActive = player.id == currentTurnPlayerId,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun PlayerInfoChip(
    player: Player,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    val pColor = infoColorMap[player.color] ?: Color.Gray
    val alpha = when {
        player.isEliminated -> 0.3f
        player.isFinished -> 0.6f
        else -> 1f
    }

    Column(
        modifier = modifier
            .padding(2.dp)
            .background(
                if (isActive) SurfaceVariant else Color.Transparent,
                RoundedCornerShape(8.dp)
            )
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(if (isActive) 24.dp else 18.dp)
                .clip(CircleShape)
                .background(pColor.copy(alpha = alpha))
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = player.name,
            color = TextPrimary.copy(alpha = alpha),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1
        )
        val tokensHome = player.tokens.count { it.isHome }
        if (tokensHome > 0 || player.isFinished) {
            Text(
                text = if (player.isFinished) "Done" else "$tokensHome/4",
                color = TextSecondary,
                style = MaterialTheme.typography.labelSmall
            )
        }
        if (player.isEliminated) {
            Text(
                text = "Out",
                color = Color(0xFFF85149).copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}
