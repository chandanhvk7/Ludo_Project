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

private const val NO_MOVES_DISPLAY_MS = 1500L
private const val AUTOPLAY_DELAY_MS = 600L

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

    private val _showingNoMoves = MutableStateFlow(false)
    val showingNoMoves: StateFlow<Boolean> = _showingNoMoves.asStateFlow()

    private val _lastDiceValue = MutableStateFlow<Int?>(null)
    val lastDiceValue: StateFlow<Int?> = _lastDiceValue.asStateFlow()

    private val _rollCount = MutableStateFlow(0)
    val rollCount: StateFlow<Int> = _rollCount.asStateFlow()

    private var timerJob: Job? = null
    private var listenerJob: Job? = null
    private var cleanupJob: Job? = null
    private var autoPlayJob: Job? = null
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

        viewModelScope.launch {
            repository.setupOnDisconnect(roomCode, playerId)
        }

        listenerJob = viewModelScope.launch {
            repository.listenToGame(roomCode).collect { state ->
                config = BoardConfig.forBoardType(state.boardType)
                val prevDice = _gameState.value.diceValue
                if (state.diceValue != null && prevDice == null) {
                    _lastDiceValue.value = state.diceValue
                    _rollCount.value++
                }
                _gameState.value = state

                if (state.phase == GamePhase.MOVING && state.currentTurnPlayerId == playerId) {
                    val moves = GameEngine.getValidMoves(
                        config, state, playerId, state.diceValue ?: 0
                    )
                    _validMoves.value = moves
                    scheduleAutoPlayIfSingleMove(moves)
                } else {
                    _validMoves.value = emptyList()
                    autoPlayJob?.cancel()
                }

                if (state.phase == GamePhase.ROLLING || state.phase == GamePhase.MOVING) {
                    startTurnTimerFromServerTime(state.turnStartedAt)
                } else {
                    timerJob?.cancel()
                }

                if (state.phase == GamePhase.FINISHED && state.creatorPlayerId == playerId) {
                    scheduleRoomCleanup(roomCode)
                }
            }
        }
    }

    fun quitGame() {
        val state = _gameState.value
        val playerId = if (isOnline) onlinePlayerId else state.currentTurnPlayerId
        if (playerId.isEmpty()) return

        val player = state.players[playerId] ?: return
        if (player.isFinished || player.isEliminated) return

        val newState = GameEngine.eliminatePlayer(state, playerId)

        if (isOnline) {
            viewModelScope.launch {
                repository.cancelOnDisconnect(onlineRoomCode, onlinePlayerId)
                repository.updateGameState(onlineRoomCode, newState)
            }
        } else {
            _gameState.value = newState
        }

        timerJob?.cancel()
        autoPlayJob?.cancel()
    }

    private fun scheduleAutoPlayIfSingleMove(moves: List<Int>) {
        autoPlayJob?.cancel()
        if (moves.size == 1) {
            autoPlayJob = viewModelScope.launch {
                delay(AUTOPLAY_DELAY_MS)
                onTokenSelected(moves.first())
            }
        }
    }

    fun onDiceRollOnline() {
        val state = _gameState.value
        if (state.phase != GamePhase.ROLLING) return
        if (state.currentTurnPlayerId != onlinePlayerId) return

        val diceValue = GameEngine.rollDice()
        _lastDiceValue.value = diceValue
        _rollCount.value++
        val newState = GameEngine.applyDiceRoll(config, state, diceValue)

        val hasValidMoves = newState.phase == GamePhase.MOVING
        if (hasValidMoves) {
            viewModelScope.launch {
                repository.updateGameState(onlineRoomCode, newState)
            }
        } else {
            val intermediateState = state.copy(diceValue = diceValue)
            _gameState.value = intermediateState
            _showingNoMoves.value = true
            viewModelScope.launch {
                repository.updateGameState(onlineRoomCode, intermediateState)
                delay(NO_MOVES_DISPLAY_MS)
                _showingNoMoves.value = false
                repository.updateGameState(onlineRoomCode, newState)
            }
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
        if (state.phase != GamePhase.ROLLING || _showingNoMoves.value) return

        val diceValue = GameEngine.rollDice()
        _lastDiceValue.value = diceValue
        _rollCount.value++
        val newState = GameEngine.applyDiceRoll(config, state, diceValue)

        if (newState.phase == GamePhase.MOVING) {
            _gameState.value = newState
            val moves = GameEngine.getValidMoves(
                config, newState, newState.currentTurnPlayerId, newState.diceValue!!
            )
            _validMoves.value = moves
            scheduleAutoPlayIfSingleMove(moves)
        } else {
            _gameState.value = state.copy(diceValue = diceValue)
            _validMoves.value = emptyList()
            _showingNoMoves.value = true
            viewModelScope.launch {
                delay(NO_MOVES_DISPLAY_MS)
                _showingNoMoves.value = false
                _gameState.value = newState
                startTurnTimer()
            }
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

    private fun scheduleRoomCleanup(roomCode: String) {
        if (cleanupJob != null) return
        cleanupJob = viewModelScope.launch {
            delay(30_000L)
            repository.deleteFinishedRoom(roomCode)
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        listenerJob?.cancel()
        cleanupJob?.cancel()
        autoPlayJob?.cancel()
    }
}
