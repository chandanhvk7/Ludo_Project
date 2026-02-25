package com.example.ludosample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ludosample.ui.screens.GameScreen
import com.example.ludosample.ui.theme.LudoSampleTheme
import com.example.ludosample.ui.viewmodel.GameViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LudoSampleTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val viewModel: GameViewModel = viewModel()

                    LaunchedEffect(Unit) {
                        viewModel.startLocalGame(
                            listOf("Alice", "Bob", "Carol", "Dave")
                        )
                    }

                    GameScreen(
                        viewModel = viewModel,
                        currentPlayerId = "player_0",
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}
