package com.example.ludosample.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ludosample.data.GameRepository
import com.example.ludosample.engine.BoardConfig
import com.example.ludosample.engine.BoardType
import com.example.ludosample.engine.ChatMessage
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val NO_MOVES_DISPLAY_MS = 1500L
private const val AUTOPLAY_DELAY_MS = 600L

private const val TURN_TIMEOUT_MS = 30_000L
private const val TIMER_TICK_MS = 1_000L
private const val DISCONNECT_GRACE_MS = 60_000L
private const val DISCONNECT_CHECK_INTERVAL_MS = 5_000L

class GameViewModel : ViewModel() {

    private val repository = GameRepository()

    private val _gameState = MutableStateFlow(GameState())
    val gameState: StateFlow<GameState> = _gameState.asStateFlow()

    private val _validMoves = MutableStateFlow<List<Int>>(emptyList())
    val validMoves: StateFlow<List<Int>> = _validMoves.asStateFlow()

    private val _remainingSeconds = MutableStateFlow(30)
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
    private var disconnectMonitorJob: Job? = null
    private var connectionJob: Job? = null
    private var config: BoardConfig = BoardConfig.Classic
    private var isOnline = false
    private var onlinePlayerId = ""
    private var onlineRoomCode = ""
    private var serverOffset = 0L
    private var hasGameFinished = false

    private val _playAgainRoomCode = MutableStateFlow<String?>(null)
    val playAgainRoomCode: StateFlow<String?> = _playAgainRoomCode.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _latestBubble = MutableStateFlow<ChatMessage?>(null)
    val latestBubble: StateFlow<ChatMessage?> = _latestBubble.asStateFlow()

    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    var isChatOpen = false
    private var chatJob: Job? = null
    private var initialChatLoaded = false

    // ── Online mode ─────────────────────────────────────────────────

    fun connectToOnlineGame(roomCode: String, playerId: String) {
        if (listenerJob != null) return
        isOnline = true
        onlinePlayerId = playerId
        onlineRoomCode = roomCode

        startConnectionObserver(roomCode, playerId)
        startDisconnectMonitor()

        startChatObserver(roomCode)

        viewModelScope.launch {
            try {
                serverOffset = repository.serverTimeOffset.first()
            } catch (_: Exception) { }
        }

        listenerJob = viewModelScope.launch {
            repository.listenToGame(roomCode).collect { state ->
                if (hasGameFinished) return@collect

                config = BoardConfig.forBoardType(state.boardType)
                val prevDice = _gameState.value.diceValue
                if (state.diceValue != null && prevDice == null) {
                    _lastDiceValue.value = state.diceValue
                    _rollCount.value++
                }
                _gameState.value = state

                if (state.phase == GamePhase.MOVING) {
                    val turnPlayer = state.currentTurnPlayerId
                    val moves = GameEngine.getValidMoves(
                        config, state, turnPlayer, state.diceValue ?: 0
                    )
                    _validMoves.value = moves
                    if (turnPlayer == playerId) {
                        scheduleAutoPlayIfSingleMove(moves)
                    } else {
                        autoPlayJob?.cancel()
                    }
                } else {
                    _validMoves.value = emptyList()
                    autoPlayJob?.cancel()
                }

                if (state.phase == GamePhase.ROLLING || state.phase == GamePhase.MOVING) {
                    startTurnTimerFromServerTime(state.turnStartedAt)
                } else {
                    timerJob?.cancel()
                }

                if (state.phase == GamePhase.FINISHED) {
                    hasGameFinished = true
                    disconnectMonitorJob?.cancel()
                    connectionJob?.cancel()
                    autoPlayJob?.cancel()
                    if (state.creatorPlayerId == playerId) {
                        cleanupFinishedRoom(roomCode)
                    }
                }
            }
        }
    }

    private fun startConnectionObserver(roomCode: String, playerId: String) {
        connectionJob?.cancel()
        connectionJob = viewModelScope.launch {
            repository.observeConnected().collect { connected ->
                if (connected) {
                    repository.clearDisconnected(roomCode, playerId)
                    repository.setupOnDisconnect(roomCode, playerId)
                }
            }
        }
    }

    private fun startDisconnectMonitor() {
        disconnectMonitorJob?.cancel()
        disconnectMonitorJob = viewModelScope.launch {
            while (true) {
                delay(DISCONNECT_CHECK_INTERVAL_MS)
                val state = _gameState.value
                if (state.phase == GamePhase.FINISHED || state.phase == GamePhase.WAITING_FOR_PLAYERS) continue
                if (state.creatorPlayerId != onlinePlayerId) continue

                val now = System.currentTimeMillis()
                for ((pid, player) in state.players) {
                    if (player.isEliminated || player.isFinished) continue
                    if (player.disconnectedAt <= 0) continue
                    if (now - player.disconnectedAt >= DISCONNECT_GRACE_MS) {
                        repository.eliminatePlayer(onlineRoomCode, pid)
                        val updated = GameEngine.eliminatePlayer(_gameState.value, pid)
                        repository.updateGameState(onlineRoomCode, updated)
                    }
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
                val serverNow = System.currentTimeMillis() + serverOffset
                val elapsed = serverNow - turnStartedAt
                val remaining = ((TURN_TIMEOUT_MS - elapsed) / 1000).toInt().coerceIn(0, 30)
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
        _remainingSeconds.value = 30

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

    fun playAgain() {
        if (!isOnline || !hasGameFinished) return
        val state = _gameState.value

        viewModelScope.launch {
            var existing: String? = null
            for (attempt in 1..3) {
                existing = repository.getNextRoomCode(onlineRoomCode)
                if (existing != null) break
                delay(1000L)
            }

            if (existing != null) {
                _playAgainRoomCode.value = existing
            } else {
                val newRoom = repository.createRoom(
                    playerId = onlinePlayerId,
                    playerName = state.players[onlinePlayerId]?.name ?: "Player",
                    maxPlayers = state.maxPlayers
                )
                repository.setNextRoomCode(onlineRoomCode, newRoom)
                _playAgainRoomCode.value = newRoom
            }
        }
    }

    // ── Chat ─────────────────────────────────────────────────────────

    private fun startChatObserver(roomCode: String) {
        chatJob?.cancel()
        chatJob = viewModelScope.launch {
            repository.observeMessages(roomCode).collect { msg ->
                _chatMessages.value = _chatMessages.value + msg
                if (initialChatLoaded) {
                    _latestBubble.value = msg
                    if (!isChatOpen) {
                        _unreadCount.value++
                    }
                    launch {
                        delay(3500)
                        if (_latestBubble.value?.id == msg.id) {
                            _latestBubble.value = null
                        }
                    }
                }
            }
        }
        viewModelScope.launch {
            delay(1500)
            initialChatLoaded = true
        }
    }

    fun sendChatMessage(type: String, content: String) {
        if (!isOnline) return
        val player = _gameState.value.players[onlinePlayerId] ?: return
        val msg = ChatMessage(
            senderId = onlinePlayerId,
            senderName = player.name,
            senderColor = player.color.name,
            type = type,
            content = content
        )
        viewModelScope.launch {
            repository.sendMessage(onlineRoomCode, msg)
        }
    }

    fun clearUnread() {
        _unreadCount.value = 0
    }

    private fun cleanupFinishedRoom(roomCode: String) {
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
        disconnectMonitorJob?.cancel()
        connectionJob?.cancel()
        chatJob?.cancel()
    }
}
