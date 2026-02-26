package com.example.ludosample.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ludosample.data.GameRepository
import com.example.ludosample.engine.BoardConfig
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

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var listenerJob: Job? = null
    private var roomCode: String = ""

    fun createRoom(playerId: String, playerName: String) {
        if (_isLoading.value) return
        _isLoading.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            try {
                roomCode = repository.createRoom(playerId, playerName, 4)
                listenToRoom(roomCode)
            } catch (e: Exception) {
                _errorMessage.value = "Failed to create room: ${e.message ?: "Check your internet connection and Firebase setup"}"
                _isLoading.value = false
            }
        }
    }

    fun joinRoom(roomCode: String, playerId: String, playerName: String) {
        if (_isLoading.value) return
        _isLoading.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            try {
                val success = repository.joinRoom(roomCode, playerId, playerName)
                if (success) {
                    this@LobbyViewModel.roomCode = roomCode
                    listenToRoom(roomCode)
                } else {
                    _errorMessage.value = "Could not join room. It may be full or already started."
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to join room: ${e.message ?: "Check your connection"}"
                _isLoading.value = false
            }
        }
    }

    fun updateMaxPlayers(count: Int) {
        if (roomCode.isBlank()) return
        val currentState = _gameState.value
        if (currentState.phase != GamePhase.WAITING_FOR_PLAYERS) return

        viewModelScope.launch {
            try {
                val boardType = BoardType.forPlayerCount(count)
                val updated = currentState.copy(maxPlayers = count, boardType = boardType)
                repository.updateGameState(roomCode, updated)
            } catch (_: Exception) { }
        }
    }

    fun startGame() {
        if (roomCode.isBlank()) return
        val currentState = _gameState.value
        val actualCount = currentState.players.size
        if (actualCount < 2) return

        viewModelScope.launch {
            try {
                val actualBoardType = BoardType.forPlayerCount(actualCount)
                val actualConfig = BoardConfig.forBoardType(actualBoardType)
                val properSlots = actualConfig.assignSlots(actualCount)

                val sortedPlayers = currentState.players.values.sortedBy { it.slotIndex }
                val reassignedPlayers = sortedPlayers.mapIndexed { i, player ->
                    val newSlot = properSlots[i]
                    val newColor = actualConfig.colorForSlot(newSlot)
                    player.id to player.copy(slotIndex = newSlot, color = newColor)
                }.toMap()

                val initialState = GameEngine.createInitialGameState(
                    roomCode = roomCode,
                    boardType = actualBoardType,
                    players = reassignedPlayers,
                    creatorPlayerId = currentState.creatorPlayerId
                )
                repository.startGame(roomCode, initialState)
            } catch (e: Exception) {
                _errorMessage.value = "Failed to start game: ${e.message}"
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    private fun listenToRoom(code: String) {
        listenerJob?.cancel()
        listenerJob = viewModelScope.launch {
            try {
                repository.listenToGame(code).collect { state ->
                    _gameState.value = state
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                _errorMessage.value = "Lost connection to room: ${e.message}"
                _isLoading.value = false
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        listenerJob?.cancel()
    }
}
