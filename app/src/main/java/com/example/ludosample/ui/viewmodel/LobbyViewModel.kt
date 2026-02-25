package com.example.ludosample.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ludosample.data.GameRepository
import com.example.ludosample.engine.BoardType
import com.example.ludosample.engine.GameEngine
import com.example.ludosample.engine.GamePhase
import com.example.ludosample.engine.GameState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LobbyViewModel : ViewModel() {

    private val repository = GameRepository()

    private val _gameState = MutableStateFlow(GameState())
    val gameState: StateFlow<GameState> = _gameState.asStateFlow()

    private var listenerJob: Job? = null
    private var roomCode: String = ""

    fun createRoom(playerId: String, playerName: String) {
        viewModelScope.launch {
            roomCode = repository.createRoom(playerId, playerName, 4)
            listenToRoom(roomCode)
        }
    }

    fun joinRoom(roomCode: String, playerId: String, playerName: String) {
        viewModelScope.launch {
            val success = repository.joinRoom(roomCode, playerId, playerName)
            if (success) {
                this@LobbyViewModel.roomCode = roomCode
                listenToRoom(roomCode)
            }
        }
    }

    fun updateMaxPlayers(count: Int) {
        if (roomCode.isBlank()) return
        val currentState = _gameState.value
        if (currentState.phase != GamePhase.WAITING_FOR_PLAYERS) return

        viewModelScope.launch {
            val boardType = BoardType.forPlayerCount(count)
            val updated = currentState.copy(maxPlayers = count, boardType = boardType)
            repository.updateGameState(roomCode, updated)
        }
    }

    fun startGame() {
        if (roomCode.isBlank()) return
        val currentState = _gameState.value
        if (currentState.players.size < 2) return

        viewModelScope.launch {
            val initialState = GameEngine.createInitialGameState(
                roomCode = roomCode,
                boardType = currentState.boardType,
                players = currentState.players,
                creatorPlayerId = currentState.creatorPlayerId
            )
            repository.startGame(roomCode, initialState)
        }
    }

    private fun listenToRoom(code: String) {
        listenerJob?.cancel()
        listenerJob = viewModelScope.launch {
            repository.listenToGame(code).collect { state ->
                _gameState.value = state
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        listenerJob?.cancel()
    }
}
