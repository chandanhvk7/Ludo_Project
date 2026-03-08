package com.example.ludosample.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.ludosample.data.GameRepository
import com.example.ludosample.data.PlayerPreferences
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
    prefs: PlayerPreferences,
    activeRoom: String? = null,
    deepLinkRoom: String? = null,
    modifier: Modifier = Modifier
) {
    var rejoinHandled by remember { mutableStateOf(false) }
    var pendingDeepLinkRoom by remember { mutableStateOf(deepLinkRoom) }

    LaunchedEffect(activeRoom, deepLinkRoom) {
        if (rejoinHandled) return@LaunchedEffect
        rejoinHandled = true

        if (!activeRoom.isNullOrBlank()) {
            val repo = GameRepository()
            if (repo.isPlayerInActiveGame(activeRoom, playerId)) {
                navController.navigate(Routes.game(activeRoom)) {
                    popUpTo(Routes.HOME)
                }
                return@LaunchedEffect
            } else {
                prefs.setActiveRoom(null)
            }
        }

        if (!deepLinkRoom.isNullOrBlank() && playerName.isNotBlank()) {
            navController.navigate(Routes.lobby("JOIN:$deepLinkRoom:$playerName")) {
                popUpTo(Routes.HOME)
            }
            pendingDeepLinkRoom = null
        }
    }

    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = modifier
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                initialName = playerName,
                initialRoomCode = pendingDeepLinkRoom ?: "",
                onCreateRoom = { name ->
                    navController.navigate(Routes.lobby("CREATE:$name"))
                },
                onJoinRoom = { name, code ->
                    pendingDeepLinkRoom = null
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
            val isLoading by lobbyViewModel.isLoading.collectAsState()
            val errorMessage by lobbyViewModel.errorMessage.collectAsState()
            val isCreator = gameState.creatorPlayerId == playerId

            if (gameState.phase == GamePhase.ROLLING || gameState.phase == GamePhase.MOVING) {
                LaunchedEffect(gameState.roomCode) {
                    prefs.setActiveRoom(gameState.roomCode)
                    navController.navigate(Routes.game(gameState.roomCode)) {
                        popUpTo(Routes.HOME)
                    }
                }
            } else {
                LobbyScreen(
                    gameState = gameState,
                    playerId = playerId,
                    isCreator = isCreator,
                    isLoading = isLoading,
                    errorMessage = errorMessage,
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
            val scope = rememberCoroutineScope()

            LaunchedEffect(roomCode) {
                prefs.setActiveRoom(roomCode)
                gameViewModel.connectToOnlineGame(roomCode, playerId)
            }

            val gamePhase = gameViewModel.gameState.collectAsState().value.phase
            LaunchedEffect(gamePhase) {
                if (gamePhase == GamePhase.FINISHED) {
                    prefs.setActiveRoom(null)
                }
            }

            GameScreen(
                viewModel = gameViewModel,
                currentPlayerId = playerId,
                onQuit = {
                    scope.launch { prefs.setActiveRoom(null) }
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                },
                onPlayAgainNavigate = { newRoomCode ->
                    scope.launch { prefs.setActiveRoom(null) }
                    navController.navigate(Routes.lobby("JOIN:$newRoomCode:$playerName")) {
                        popUpTo(Routes.HOME)
                    }
                }
            )
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
