package com.example.ludosample.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.ludosample.engine.GamePhase
import com.example.ludosample.ui.screens.GameOverScreen
import com.example.ludosample.ui.screens.GameScreen
import com.example.ludosample.ui.screens.HomeScreen
import com.example.ludosample.ui.screens.LobbyScreen
import com.example.ludosample.ui.viewmodel.GameViewModel
import com.example.ludosample.ui.viewmodel.LobbyViewModel

object Routes {
    const val HOME = "home"
    const val LOBBY = "lobby/{roomCode}"
    const val GAME = "game/{roomCode}"
    const val GAME_OVER = "gameOver/{roomCode}"

    fun lobby(roomCode: String) = "lobby/$roomCode"
    fun game(roomCode: String) = "game/$roomCode"
    fun gameOver(roomCode: String) = "gameOver/$roomCode"
}

@Composable
fun LudoNavGraph(
    navController: NavHostController,
    playerId: String,
    playerName: String,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = modifier
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                initialName = playerName,
                onCreateRoom = { name ->
                    navController.navigate(Routes.lobby("CREATE:$name"))
                },
                onJoinRoom = { name, code ->
                    navController.navigate(Routes.lobby("JOIN:$code:$name"))
                }
            )
        }

        composable(Routes.LOBBY) { backStackEntry ->
            val rawArg = backStackEntry.arguments?.getString("roomCode") ?: return@composable
            val lobbyViewModel: LobbyViewModel = viewModel()

            LaunchedEffect(rawArg) {
                when {
                    rawArg.startsWith("CREATE:") -> {
                        val name = rawArg.removePrefix("CREATE:")
                        lobbyViewModel.createRoom(playerId, name)
                    }
                    rawArg.startsWith("JOIN:") -> {
                        val parts = rawArg.removePrefix("JOIN:").split(":", limit = 2)
                        if (parts.size == 2) {
                            lobbyViewModel.joinRoom(parts[0], playerId, parts[1])
                        }
                    }
                }
            }

            val gameState by lobbyViewModel.gameState.collectAsState()
            val isCreator = gameState.creatorPlayerId == playerId

            if (gameState.phase == GamePhase.ROLLING || gameState.phase == GamePhase.MOVING) {
                LaunchedEffect(gameState.roomCode) {
                    navController.navigate(Routes.game(gameState.roomCode)) {
                        popUpTo(Routes.HOME)
                    }
                }
            } else {
                LobbyScreen(
                    gameState = gameState,
                    playerId = playerId,
                    isCreator = isCreator,
                    onPlayerCountChanged = { count ->
                        lobbyViewModel.updateMaxPlayers(count)
                    },
                    onStartGame = {
                        lobbyViewModel.startGame()
                    }
                )
            }
        }

        composable(Routes.GAME) { backStackEntry ->
            val roomCode = backStackEntry.arguments?.getString("roomCode") ?: return@composable
            val gameViewModel: GameViewModel = viewModel()

            LaunchedEffect(roomCode) {
                gameViewModel.connectToOnlineGame(roomCode, playerId)
            }

            val gameState by gameViewModel.gameState.collectAsState()

            if (gameState.phase == GamePhase.FINISHED) {
                LaunchedEffect(Unit) {
                    navController.navigate(Routes.gameOver(roomCode)) {
                        popUpTo(Routes.HOME)
                    }
                }
            } else {
                GameScreen(
                    viewModel = gameViewModel,
                    currentPlayerId = playerId
                )
            }
        }

        composable(Routes.GAME_OVER) { backStackEntry ->
            val roomCode = backStackEntry.arguments?.getString("roomCode") ?: return@composable
            val gameViewModel: GameViewModel = viewModel()
            val gameState by gameViewModel.gameState.collectAsState()

            LaunchedEffect(roomCode) {
                gameViewModel.connectToOnlineGame(roomCode, playerId)
            }

            GameOverScreen(
                gameState = gameState,
                onPlayAgain = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                },
                onGoHome = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                }
            )
        }
    }
}
