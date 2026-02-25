package com.example.ludosample.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ludosample.engine.BoardConfig
import com.example.ludosample.engine.BoardType
import com.example.ludosample.engine.GameEngine
import com.example.ludosample.engine.GamePhase
import com.example.ludosample.engine.GameState
import com.example.ludosample.engine.Player
import com.example.ludosample.engine.PlayerColor
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TURN_TIMEOUT_MS = 20_000L
private const val TIMER_TICK_MS = 1_000L

class GameViewModel : ViewModel() {

    private val _gameState = MutableStateFlow(GameState())
    val gameState: StateFlow<GameState> = _gameState.asStateFlow()

    private val _validMoves = MutableStateFlow<List<Int>>(emptyList())
    val validMoves: StateFlow<List<Int>> = _validMoves.asStateFlow()

    private val _remainingSeconds = MutableStateFlow(20)
    val remainingSeconds: StateFlow<Int> = _remainingSeconds.asStateFlow()

    private var timerJob: Job? = null
    private var config: BoardConfig = BoardConfig.Classic

    /**
     * Start a local pass-and-play game with the given player names.
     */
    fun startLocalGame(playerNames: List<String>) {
        val playerCount = playerNames.size
        val boardType = BoardType.forPlayerCount(playerCount)
        config = BoardConfig.forBoardType(boardType)
        val slots = config.assignSlots(playerCount)

        val players = playerNames.mapIndexed { i, name ->
            val slotIndex = slots[i]
            val id = "player_$i"
            id to Player(
                id = id,
                name = name,
                color = config.colorForSlot(slotIndex),
                slotIndex = slotIndex
            )
        }.toMap()

        val state = GameEngine.createInitialGameState(
            roomCode = "LOCAL",
            boardType = boardType,
            players = players,
            creatorPlayerId = players.keys.first()
        )
        _gameState.value = state
        _validMoves.value = emptyList()
        startTurnTimer()
    }

    fun onDiceRoll() {
        val state = _gameState.value
        if (state.phase != GamePhase.ROLLING) return

        val diceValue = GameEngine.rollDice()
        val newState = GameEngine.applyDiceRoll(config, state, diceValue)
        _gameState.value = newState

        if (newState.phase == GamePhase.MOVING) {
            _validMoves.value = GameEngine.getValidMoves(
                config, newState, newState.currentTurnPlayerId, newState.diceValue!!
            )
        } else {
            _validMoves.value = emptyList()
            startTurnTimer()
        }
    }

    fun onTokenSelected(tokenIndex: Int) {
        val state = _gameState.value
        if (state.phase != GamePhase.MOVING) return

        val newState = GameEngine.applyTokenMove(config, state, state.currentTurnPlayerId, tokenIndex)
        _gameState.value = newState
        _validMoves.value = emptyList()

        if (newState.phase != GamePhase.FINISHED) {
            startTurnTimer()
        } else {
            timerJob?.cancel()
        }
    }

    private fun startTurnTimer() {
        timerJob?.cancel()
        _remainingSeconds.value = 20

        timerJob = viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            while (true) {
                val elapsed = System.currentTimeMillis() - startTime
                val remaining = ((TURN_TIMEOUT_MS - elapsed) / 1000).toInt().coerceAtLeast(0)
                _remainingSeconds.value = remaining

                if (remaining <= 0) {
                    handleTimeout()
                    break
                }
                delay(TIMER_TICK_MS)
            }
        }
    }

    private fun handleTimeout() {
        val state = _gameState.value
        if (state.phase == GamePhase.FINISHED) return

        val newState = GameEngine.handleTurnTimeout(config, state)
        _gameState.value = newState
        _validMoves.value = emptyList()

        if (newState.phase != GamePhase.FINISHED) {
            startTurnTimer()
        }
    }

    fun currentPlayerName(): String {
        val state = _gameState.value
        return state.players[state.currentTurnPlayerId]?.name ?: ""
    }

    fun currentPlayerColor(): PlayerColor {
        val state = _gameState.value
        return state.players[state.currentTurnPlayerId]?.color ?: PlayerColor.RED
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }
}
