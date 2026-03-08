package com.example.ludosample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.example.ludosample.data.PlayerPreferences
import com.example.ludosample.navigation.LudoNavGraph
import com.example.ludosample.ui.theme.LudoSampleTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = PlayerPreferences(applicationContext)

        enableEdgeToEdge()
        setContent {
            LudoSampleTheme {
                val navController = rememberNavController()
                var playerId by remember { mutableStateOf("") }
                var playerName by remember { mutableStateOf("") }

                var activeRoom by remember { mutableStateOf<String?>(null) }
                var prefsLoaded by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    playerId = prefs.getPlayerId()
                    playerName = prefs.getPlayerName()
                    activeRoom = prefs.getActiveRoom()
                    prefsLoaded = true
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    if (playerId.isNotBlank() && prefsLoaded) {
                        LudoNavGraph(
                            navController = navController,
                            playerId = playerId,
                            playerName = playerName,
                            prefs = prefs,
                            activeRoom = activeRoom,
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }
        }
    }
}
