package com.example.ludosample.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ludosample.data.GameRepository
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

    private val repository = GameRepository()

    private val _gameState = MutableStateFlow(GameState())
    val gameState: StateFlow<GameState> = _gameState.asStateFlow()

    private val _validMoves = MutableStateFlow<List<Int>>(emptyList())
    val validMoves: StateFlow<List<Int>> = _validMoves.asStateFlow()

    private val _remainingSeconds = MutableStateFlow(20)
    val remainingSeconds: StateFlow<Int> = _remainingSeconds.asStateFlow()

    private var timerJob: Job? = null
    private var listenerJob: Job? = null
    private var config: BoardConfig = BoardConfig.Classic
    private var isOnline = false
    private var onlinePlayerId = ""
    private var onlineRoomCode = ""

    // ── Online mode ─────────────────────────────────────────────────

    fun connectToOnlineGame(roomCode: String, playerId: String) {
        if (listenerJob != null) return
        isOnline = true
        onlinePlayerId = playerId
        onlineRoomCode = roomCode

        listenerJob = viewModelScope.launch {
            repository.listenToGame(roomCode).collect { state ->
                config = BoardConfig.forBoardType(state.boardType)
                _gameState.value = state

                if (state.phase == GamePhase.MOVING && state.currentTurnPlayerId == playerId) {
                    _validMoves.value = GameEngine.getValidMoves(
                        config, state, playerId, state.diceValue ?: 0
                    )
                } else {
                    _validMoves.value = emptyList()
                }

                if (state.phase == GamePhase.ROLLING || state.phase == GamePhase.MOVING) {
                    startTurnTimerFromServerTime(state.turnStartedAt)
                } else {
                    timerJob?.cancel()
                }
            }
        }
    }

    fun onDiceRollOnline() {
        val state = _gameState.value
        if (state.phase != GamePhase.ROLLING) return
        if (state.currentTurnPlayerId != onlinePlayerId) return

        val diceValue = GameEngine.rollDice()
        val newState = GameEngine.applyDiceRoll(config, state, diceValue)

        viewModelScope.launch {
            repository.updateGameState(onlineRoomCode, newState)
        }
    }

    fun onTokenSelectedOnline(tokenIndex: Int) {
        val state = _gameState.value
        if (state.phase != GamePhase.MOVING) return
        if (state.currentTurnPlayerId != onlinePlayerId) return

        val newState = GameEngine.applyTokenMove(config, state, onlinePlayerId, tokenIndex)

        viewModelScope.launch {
            repository.updateGameState(onlineRoomCode, newState)
        }
    }

    private fun startTurnTimerFromServerTime(turnStartedAt: Long) {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                val elapsed = System.currentTimeMillis() - turnStartedAt
                val remaining = ((TURN_TIMEOUT_MS - elapsed) / 1000).toInt().coerceAtLeast(0)
                _remainingSeconds.value = remaining

                if (remaining <= 0) {
                    handleOnlineTimeout()
                    break
                }
                delay(TIMER_TICK_MS)
            }
        }
    }

    private fun handleOnlineTimeout() {
        val state = _gameState.value
        if (state.phase == GamePhase.FINISHED) return

        val newState = GameEngine.handleTurnTimeout(config, state)
        viewModelScope.launch {
            repository.updateGameState(onlineRoomCode, newState)
        }
    }

    // ── Local mode ──────────────────────────────────────────────────

    fun startLocalGame(playerNames: List<String>) {
        isOnline = false
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
        if (isOnline) {
            onDiceRollOnline()
            return
        }

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
        if (isOnline) {
            onTokenSelectedOnline(tokenIndex)
            return
        }

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

    @Suppress("unused")
    fun currentPlayerColor(): PlayerColor {
        val state = _gameState.value
        return state.players[state.currentTurnPlayerId]?.color ?: PlayerColor.RED
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        listenerJob?.cancel()
    }
}
