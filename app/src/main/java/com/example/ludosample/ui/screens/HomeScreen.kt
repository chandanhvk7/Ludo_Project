package com.example.ludosample.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ludosample.ui.theme.Accent
import com.example.ludosample.ui.theme.Background
import com.example.ludosample.ui.theme.BlueButton
import com.example.ludosample.ui.theme.GreenButton
import com.example.ludosample.ui.theme.TextMuted
import com.example.ludosample.ui.theme.TextPrimary
import com.example.ludosample.ui.theme.TextSecondary

@Composable
fun HomeScreen(
    initialName: String,
    initialRoomCode: String = "",
    onCreateRoom: (playerName: String) -> Unit,
    onJoinRoom: (playerName: String, roomCode: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var playerName by rememberSaveable { mutableStateOf(initialName) }
    var roomCode by rememberSaveable { mutableStateOf(initialRoomCode) }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = TextPrimary,
        unfocusedTextColor = TextPrimary,
        focusedBorderColor = Accent,
        unfocusedBorderColor = TextMuted,
        focusedLabelColor = Accent,
        unfocusedLabelColor = TextSecondary,
        cursorColor = Accent
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Background)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Ludo",
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Bold,
            color = Accent,
            textAlign = TextAlign.Center
        )

        Text(
            text = "Online Multiplayer",
            style = MaterialTheme.typography.titleMedium,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(48.dp))

        OutlinedTextField(
            value = playerName,
            onValueChange = { playerName = it },
            label = { Text("Your Name") },
            singleLine = true,
            colors = fieldColors,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = { if (playerName.isNotBlank()) onCreateRoom(playerName.trim()) },
            enabled = playerName.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = GreenButton),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text("Create Room", style = MaterialTheme.typography.titleMedium)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "— or join a friend's room —",
            color = TextMuted,
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = roomCode,
            onValueChange = { roomCode = it.uppercase().take(6) },
            label = { Text("Room Code") },
            singleLine = true,
            colors = fieldColors,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (playerName.isNotBlank() && roomCode.length == 6)
                    onJoinRoom(playerName.trim(), roomCode.trim())
            },
            enabled = playerName.isNotBlank() && roomCode.length == 6,
            colors = ButtonDefaults.buttonColors(containerColor = BlueButton),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text("Join Room", style = MaterialTheme.typography.titleMedium)
        }
    }
}
