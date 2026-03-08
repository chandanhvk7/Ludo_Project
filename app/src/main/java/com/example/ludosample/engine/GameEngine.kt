package com.example.ludosample.engine

import kotlin.random.Random

object GameEngine {

    private const val TOKENS_PER_PLAYER = 4
    private const val MAX_CONSECUTIVE_SIXES = 3
    private const val ELIMINATION_TIMEOUT_COUNT = 5

    fun rollDice(): Int = Random.nextInt(1, 7)

    /**
     * Apply a dice roll to the current game state.
     *
     * Handles 3rd-consecutive-six cancellation and auto-pass when no valid moves exist.
     * Returns state in MOVING phase if the player has choices, or advances to the
     * next player/finishes the game otherwise.
     */
    fun applyDiceRoll(config: BoardConfig, state: GameState, diceValue: Int): GameState {
        val playerId = state.currentTurnPlayerId
        val player = state.players[playerId] ?: return state

        val updatedPlayer = player.copy(consecutiveTimeouts = 0)
        var newState = state.copy(
            players = state.players.toMutableMap().also { it[playerId] = updatedPlayer }
        )

        val newConsecutiveSixes = if (diceValue == 6) newState.consecutiveSixes + 1 else 0
        newState = newState.copy(
            diceValue = diceValue,
            consecutiveSixes = newConsecutiveSixes
        )

        if (newConsecutiveSixes >= MAX_CONSECUTIVE_SIXES) {
            return advanceToNextPlayer(newState.copy(consecutiveSixes = 0), playerId)
        }

        val validMoves = getValidMoves(config, newState, playerId, diceValue)
        if (validMoves.isEmpty()) {
            return advanceToNextPlayer(newState, playerId)
        }

        return newState.copy(phase = GamePhase.MOVING)
    }

    /**
     * Returns indices of tokens that can legally move with the given dice value.
     */
    fun getValidMoves(
        config: BoardConfig,
        state: GameState,
        playerId: String,
        diceValue: Int
    ): List<Int> {
        val player = state.players[playerId] ?: return emptyList()
        return player.tokens.indices.filter { index ->
            canMoveToken(config, player.tokens[index], diceValue)
        }
    }

    private fun canMoveToken(config: BoardConfig, token: Token, diceValue: Int): Boolean {
        if (token.isHome) return false

        if (token.position == -1) {
            return diceValue == 6
        }

        val newPos = token.position + diceValue
        return newPos <= config.homePosition
    }

    /**
     * Move the selected token and resolve the outcome (capture, home entry, bonus turn).
     *
     * Resets the player's consecutive timeout counter (manual action).
     */
    fun applyTokenMove(
        config: BoardConfig,
        state: GameState,
        playerId: String,
        tokenIndex: Int
    ): GameState {
        val diceValue = state.diceValue ?: return state
        val player = state.players[playerId] ?: return state
        if (tokenIndex !in player.tokens.indices) return state

        val validMoves = getValidMoves(config, state, playerId, diceValue)
        if (tokenIndex !in validMoves) return state

        val updatedPlayer = player.copy(consecutiveTimeouts = 0)
        val stateWithReset = state.copy(
            players = state.players.toMutableMap().also { it[playerId] = updatedPlayer }
        )

        val moveResult = performMove(config, stateWithReset, playerId, tokenIndex, diceValue)
        return resolveAfterMove(moveResult, playerId, diceValue)
    }

    /**
     * Handle a turn timeout: increment timeout counter, possibly eliminate the player,
     * then auto-roll and auto-play.
     */
    fun handleTurnTimeout(config: BoardConfig, state: GameState): GameState {
        val playerId = state.currentTurnPlayerId
        val player = state.players[playerId] ?: return state

        val newTimeoutCount = player.consecutiveTimeouts + 1
        val updatedPlayer = player.copy(consecutiveTimeouts = newTimeoutCount)
        var newState = state.copy(
            players = state.players.toMutableMap().also { it[playerId] = updatedPlayer }
        )

        if (newTimeoutCount >= ELIMINATION_TIMEOUT_COUNT) {
            return eliminatePlayer(newState, playerId)
        }

        return autoPlayTurn(config, newState)
    }

    fun createInitialGameState(
        roomCode: String,
        boardType: BoardType,
        players: Map<String, Player>,
        creatorPlayerId: String
    ): GameState {
        val firstPlayer = players.values.random()
        return GameState(
            roomCode = roomCode,
            boardType = boardType,
            players = players,
            currentTurnPlayerId = firstPlayer.id,
            phase = GamePhase.ROLLING,
            maxPlayers = players.size,
            creatorPlayerId = creatorPlayerId,
            turnStartedAt = System.currentTimeMillis()
        )
    }

    fun advanceToNextPlayer(state: GameState, currentPlayerId: String): GameState {
        val playerList = state.players.values.sortedBy { it.slotIndex }
        val currentIdx = playerList.indexOfFirst { it.id == currentPlayerId }
        if (currentIdx == -1) return state.copy(phase = GamePhase.FINISHED)

        for (offset in 1..playerList.size) {
            val candidate = playerList[(currentIdx + offset) % playerList.size]
            if (!candidate.isFinished && !candidate.isEliminated) {
                return state.copy(
                    currentTurnPlayerId = candidate.id,
                    phase = GamePhase.ROLLING,
                    diceValue = null,
                    consecutiveSixes = 0,
                    turnStartedAt = System.currentTimeMillis()
                )
            }
        }

        return finalizeGame(state)
    }

    // ── Internal helpers ────────────────────────────────────────────────

    private fun performMove(
        config: BoardConfig,
        state: GameState,
        playerId: String,
        tokenIndex: Int,
        diceValue: Int
    ): MoveResult {
        val player = state.players[playerId] ?: return MoveResult(state)
        val token = player.tokens[tokenIndex]

        val newPosition: Int
        val reachedHome: Boolean

        if (token.position == -1) {
            newPosition = 0
            reachedHome = false
        } else {
            val targetPos = token.position + diceValue
            reachedHome = targetPos == config.homePosition
            newPosition = targetPos
        }

        val newToken = Token(position = newPosition, isHome = reachedHome)
        val updatedTokens = player.tokens.toMutableList().also { it[tokenIndex] = newToken }
        val updatedPlayer = player.copy(tokens = updatedTokens)

        var newState = state.copy(
            players = state.players.toMutableMap().also { it[playerId] = updatedPlayer }
        )

        var captured = false
        if (!reachedHome && newPosition in 0 until config.pathLength - 1) {
            val absolutePos = config.toAbsolutePosition(newPosition, player.slotIndex)
            if (absolutePos !in config.safeSpotIndices) {
                val (stateAfterCapture, didCapture) = applyCapturesAt(
                    config, newState, playerId, absolutePos
                )
                newState = stateAfterCapture
                captured = didCapture
            }
        }

        if (reachedHome) {
            val currentPlayer = newState.players[playerId]!!
            val allHome = currentPlayer.tokens.all { it.isHome }
            if (allHome && !currentPlayer.isFinished) {
                val finishedPlayer = currentPlayer.copy(isFinished = true)
                newState = newState.copy(
                    players = newState.players.toMutableMap().also {
                        it[playerId] = finishedPlayer
                    },
                    finishOrder = newState.finishOrder + playerId
                )
            }
        }

        return MoveResult(newState = newState, captured = captured, enteredHome = reachedHome)
    }

    private fun applyCapturesAt(
        config: BoardConfig,
        state: GameState,
        movingPlayerId: String,
        absolutePos: Int
    ): Pair<GameState, Boolean> {
        var newState = state
        var captured = false
        var totalKills = 0

        for ((pid, player) in state.players) {
            if (pid == movingPlayerId) continue
            if (player.isFinished || player.isEliminated) continue

            var capturedCount = 0
            val updatedTokens = player.tokens.map { token ->
                if (!token.isHome && token.position in 0 until config.pathLength - 1) {
                    val tokenAbsPos = config.toAbsolutePosition(token.position, player.slotIndex)
                    if (tokenAbsPos == absolutePos) {
                        captured = true
                        capturedCount++
                        Token()
                    } else {
                        token
                    }
                } else {
                    token
                }
            }

            if (capturedCount > 0) {
                totalKills += capturedCount
                newState = newState.copy(
                    players = newState.players.toMutableMap().also {
                        it[pid] = player.copy(
                            tokens = updatedTokens,
                            deaths = player.deaths + capturedCount
                        )
                    }
                )
            }
        }

        if (totalKills > 0) {
            val mover = newState.players[movingPlayerId]!!
            newState = newState.copy(
                players = newState.players.toMutableMap().also {
                    it[movingPlayerId] = mover.copy(kills = mover.kills + totalKills)
                }
            )
        }

        return Pair(newState, captured)
    }

    private fun resolveAfterMove(
        moveResult: MoveResult,
        playerId: String,
        diceValue: Int
    ): GameState {
        val state = moveResult.newState

        val activePlayers = state.players.values.count { !it.isFinished && !it.isEliminated }
        if (activePlayers <= 1) {
            return finalizeGame(state)
        }

        val player = state.players[playerId]!!
        if (player.isFinished) {
            return advanceToNextPlayer(state, playerId)
        }

        val isBonusTurn = diceValue == 6 || moveResult.captured || moveResult.enteredHome
        if (isBonusTurn) {
            return state.copy(
                phase = GamePhase.ROLLING,
                diceValue = null,
                turnStartedAt = System.currentTimeMillis()
            )
        }

        return advanceToNextPlayer(state, playerId)
    }

    private fun autoPlayTurn(config: BoardConfig, state: GameState): GameState {
        val playerId = state.currentTurnPlayerId
        val diceValue = rollDice()

        val newConsecutiveSixes = if (diceValue == 6) state.consecutiveSixes + 1 else 0
        var newState = state.copy(
            diceValue = diceValue,
            consecutiveSixes = newConsecutiveSixes
        )

        if (newConsecutiveSixes >= MAX_CONSECUTIVE_SIXES) {
            return advanceToNextPlayer(newState.copy(consecutiveSixes = 0), playerId)
        }

        val validMoves = getValidMoves(config, newState, playerId, diceValue)
        if (validMoves.isEmpty()) {
            return advanceToNextPlayer(newState, playerId)
        }

        val tokenIndex = validMoves.random()
        val moveResult = performMove(config, newState, playerId, tokenIndex, diceValue)
        return resolveAfterMove(moveResult, playerId, diceValue)
    }

    fun eliminatePlayer(state: GameState, playerId: String): GameState {
        val player = state.players[playerId] ?: return state
        val eliminatedPlayer = player.copy(
            isEliminated = true,
            tokens = List(TOKENS_PER_PLAYER) { Token() }
        )
        var newState = state.copy(
            players = state.players.toMutableMap().also { it[playerId] = eliminatedPlayer }
        )

        val activePlayers = newState.players.values.count { !it.isFinished && !it.isEliminated }
        if (activePlayers <= 1) {
            return finalizeGame(newState)
        }

        return advanceToNextPlayer(newState, playerId)
    }

    private fun finalizeGame(state: GameState): GameState {
        val remainingActive = state.players.values
            .filter { !it.isFinished && !it.isEliminated }

        var finishOrder = state.finishOrder
        for (p in remainingActive) {
            if (p.id !in finishOrder) {
                finishOrder = finishOrder + p.id
            }
        }

        val eliminated = state.players.values.filter { it.isEliminated }
        for (p in eliminated) {
            if (p.id !in finishOrder) {
                finishOrder = finishOrder + p.id
            }
        }

        return state.copy(
            phase = GamePhase.FINISHED,
            finishOrder = finishOrder,
            winner = finishOrder.firstOrNull()
        )
    }
}
