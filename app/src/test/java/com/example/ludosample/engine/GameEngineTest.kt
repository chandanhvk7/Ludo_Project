package com.example.ludosample.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameEngineTest {

    private fun createTestGame(
        playerCount: Int = 2,
        boardType: BoardType = BoardType.CLASSIC
    ): GameState {
        val config = BoardConfig.forBoardType(boardType)
        val slots = config.assignSlots(playerCount)
        val players = (0 until playerCount).associate { i ->
            val slot = slots[i]
            "p$i" to Player(
                id = "p$i",
                name = "Player$i",
                color = config.colorForSlot(slot),
                slotIndex = slot
            )
        }
        return GameEngine.createInitialGameState(
            roomCode = "TEST",
            boardType = boardType,
            players = players,
            creatorPlayerId = "p0"
        )
    }

    // ── Dice ────────────────────────────────────────────────────────

    @Test
    fun `rollDice returns values 1 to 6`() {
        val results = (1..1000).map { GameEngine.rollDice() }.toSet()
        assertEquals(setOf(1, 2, 3, 4, 5, 6), results)
    }

    // ── Valid moves ─────────────────────────────────────────────────

    @Test
    fun `no valid moves when all tokens in yard and roll is not 6`() {
        val state = createTestGame()
        val config = BoardConfig.Classic
        val playerId = state.currentTurnPlayerId
        val moves = GameEngine.getValidMoves(config, state, playerId, 3)
        assertTrue(moves.isEmpty())
    }

    @Test
    fun `rolling 6 allows token to leave yard`() {
        val state = createTestGame()
        val config = BoardConfig.Classic
        val playerId = state.currentTurnPlayerId
        val moves = GameEngine.getValidMoves(config, state, playerId, 6)
        assertEquals(4, moves.size)
    }

    @Test
    fun `token on path can move by dice value`() {
        val state = createTestGame()
        val config = BoardConfig.Classic
        val playerId = state.currentTurnPlayerId
        val player = state.players[playerId]!!

        val movedTokens = player.tokens.toMutableList()
        movedTokens[0] = Token(position = 5)
        val updatedState = state.copy(
            players = state.players.toMutableMap().also {
                it[playerId] = player.copy(tokens = movedTokens)
            }
        )

        val moves = GameEngine.getValidMoves(config, updatedState, playerId, 3)
        assertTrue(0 in moves)
    }

    @Test
    fun `token cannot overshoot home`() {
        val config = BoardConfig.Classic
        val state = createTestGame()
        val playerId = state.currentTurnPlayerId
        val player = state.players[playerId]!!

        val movedTokens = player.tokens.toMutableList()
        movedTokens[0] = Token(position = config.pathLength + 4)
        val updatedState = state.copy(
            players = state.players.toMutableMap().also {
                it[playerId] = player.copy(tokens = movedTokens)
            }
        )

        val moves = GameEngine.getValidMoves(config, updatedState, playerId, 3)
        assertFalse(0 in moves)

        val exactMoves = GameEngine.getValidMoves(config, updatedState, playerId, 1)
        assertTrue(0 in exactMoves)
    }

    // ── Move token ──────────────────────────────────────────────────

    @Test
    fun `moving token from yard sets position to 0`() {
        val config = BoardConfig.Classic
        val state = createTestGame()
        val playerId = state.currentTurnPlayerId

        val afterRoll = GameEngine.applyDiceRoll(config, state, 6)
        assertEquals(GamePhase.MOVING, afterRoll.phase)

        val afterMove = GameEngine.applyTokenMove(config, afterRoll, playerId, 0)
        val movedToken = afterMove.players[playerId]!!.tokens[0]
        assertEquals(0, movedToken.position)
    }

    @Test
    fun `moving token advances by dice value`() {
        val config = BoardConfig.Classic
        val state = createTestGame()
        val playerId = state.currentTurnPlayerId
        val player = state.players[playerId]!!

        val movedTokens = player.tokens.toMutableList()
        movedTokens[0] = Token(position = 10)
        val stateWithToken = state.copy(
            players = state.players.toMutableMap().also {
                it[playerId] = player.copy(tokens = movedTokens)
            },
            phase = GamePhase.MOVING,
            diceValue = 4
        )

        val afterMove = GameEngine.applyTokenMove(config, stateWithToken, playerId, 0)
        val token = afterMove.players[playerId]!!.tokens[0]
        assertEquals(14, token.position)
    }

    // ── Capture ─────────────────────────────────────────────────────

    @Test
    fun `landing on opponent sends them to yard`() {
        val config = BoardConfig.Classic
        val state = createTestGame()
        val playerIds = state.players.keys.toList()
        val p0 = playerIds[0]
        val p1 = playerIds[1]
        val player0 = state.players[p0]!!
        val player1 = state.players[p1]!!

        val targetAbsolutePos = 5
        val p0Slot = player0.slotIndex
        val p1Slot = player1.slotIndex
        val p0RelPos = config.toRelativePosition(targetAbsolutePos, p0Slot)
        val p1RelPos = config.toRelativePosition(targetAbsolutePos, p1Slot)

        val p0Tokens = player0.tokens.toMutableList()
        p0Tokens[0] = Token(position = p0RelPos - 3)
        val p1Tokens = player1.tokens.toMutableList()
        p1Tokens[0] = Token(position = p1RelPos)

        val setupState = state.copy(
            currentTurnPlayerId = p0,
            phase = GamePhase.MOVING,
            diceValue = 3,
            players = mapOf(
                p0 to player0.copy(tokens = p0Tokens),
                p1 to player1.copy(tokens = p1Tokens)
            )
        )

        if (targetAbsolutePos in config.safeSpotIndices) return

        val afterMove = GameEngine.applyTokenMove(config, setupState, p0, 0)
        val opponentToken = afterMove.players[p1]!!.tokens[0]
        assertEquals(-1, opponentToken.position)
    }

    @Test
    fun `safe spot prevents capture`() {
        val config = BoardConfig.Classic
        val safeSpot = config.safeSpotIndices.first()
        val state = createTestGame()
        val playerIds = state.players.keys.toList()
        val p0 = playerIds[0]
        val p1 = playerIds[1]
        val player0 = state.players[p0]!!
        val player1 = state.players[p1]!!

        val p0RelPos = config.toRelativePosition(safeSpot, player0.slotIndex)
        val p1RelPos = config.toRelativePosition(safeSpot, player1.slotIndex)

        val p0Tokens = player0.tokens.toMutableList()
        p0Tokens[0] = Token(position = p0RelPos - 2)
        val p1Tokens = player1.tokens.toMutableList()
        p1Tokens[0] = Token(position = p1RelPos)

        val setupState = state.copy(
            currentTurnPlayerId = p0,
            phase = GamePhase.MOVING,
            diceValue = 2,
            players = mapOf(
                p0 to player0.copy(tokens = p0Tokens),
                p1 to player1.copy(tokens = p1Tokens)
            )
        )

        val afterMove = GameEngine.applyTokenMove(config, setupState, p0, 0)
        val opponentToken = afterMove.players[p1]!!.tokens[0]
        assertEquals(p1RelPos, opponentToken.position)
    }

    // ── Bonus turns ─────────────────────────────────────────────────

    @Test
    fun `rolling 6 gives bonus turn`() {
        val config = BoardConfig.Classic
        val state = createTestGame()
        val playerId = state.currentTurnPlayerId

        val afterRoll = GameEngine.applyDiceRoll(config, state, 6)
        val afterMove = GameEngine.applyTokenMove(config, afterRoll, playerId, 0)

        assertEquals(GamePhase.ROLLING, afterMove.phase)
        assertEquals(playerId, afterMove.currentTurnPlayerId)
    }

    @Test
    fun `non-6 roll passes to next player`() {
        val config = BoardConfig.Classic
        val state = createTestGame()
        val playerId = state.currentTurnPlayerId
        val player = state.players[playerId]!!

        val movedTokens = player.tokens.toMutableList()
        movedTokens[0] = Token(position = 5)
        val stateWithToken = state.copy(
            players = state.players.toMutableMap().also {
                it[playerId] = player.copy(tokens = movedTokens)
            }
        )

        val afterRoll = GameEngine.applyDiceRoll(config, stateWithToken, 3)
        val afterMove = GameEngine.applyTokenMove(config, afterRoll, playerId, 0)

        assertNotEquals(playerId, afterMove.currentTurnPlayerId)
    }

    // ── Three consecutive sixes ─────────────────────────────────────

    @Test
    fun `third consecutive six cancels roll and passes turn`() {
        val config = BoardConfig.Classic
        val state = createTestGame()
        val playerId = state.currentTurnPlayerId

        val stateWith2Sixes = state.copy(consecutiveSixes = 2)
        val afterThirdSix = GameEngine.applyDiceRoll(config, stateWith2Sixes, 6)

        assertNotEquals(playerId, afterThirdSix.currentTurnPlayerId)
        assertEquals(0, afterThirdSix.consecutiveSixes)
    }

    // ── Win condition ───────────────────────────────────────────────

    @Test
    fun `player finishes when all tokens reach home`() {
        val config = BoardConfig.Classic
        val state = createTestGame()
        val playerId = state.currentTurnPlayerId
        val player = state.players[playerId]!!

        val almostDone = player.tokens.toMutableList()
        almostDone[0] = Token(isHome = true)
        almostDone[1] = Token(isHome = true)
        almostDone[2] = Token(isHome = true)
        almostDone[3] = Token(position = config.homePosition - 1)

        val setupState = state.copy(
            currentTurnPlayerId = playerId,
            phase = GamePhase.MOVING,
            diceValue = 1,
            players = state.players.toMutableMap().also {
                it[playerId] = player.copy(tokens = almostDone)
            }
        )

        val afterMove = GameEngine.applyTokenMove(config, setupState, playerId, 3)
        val finishedPlayer = afterMove.players[playerId]!!
        assertTrue(finishedPlayer.isFinished)
        assertTrue(finishedPlayer.tokens[3].isHome)
        assertTrue(playerId in afterMove.finishOrder)
    }

    // ── Timeout / elimination ───────────────────────────────────────

    @Test
    fun `timeout increments counter`() {
        val config = BoardConfig.Classic
        val state = createTestGame()
        val playerId = state.currentTurnPlayerId

        val afterTimeout = GameEngine.handleTurnTimeout(config, state)
        val timedOutPlayer = afterTimeout.players[playerId]
        assertTrue((timedOutPlayer?.consecutiveTimeouts ?: 0) >= 1)
    }

    @Test
    fun `5 timeouts eliminates player`() {
        val config = BoardConfig.Classic
        val state = createTestGame()
        val playerId = state.currentTurnPlayerId
        val player = state.players[playerId]!!

        val stateNearElimination = state.copy(
            players = state.players.toMutableMap().also {
                it[playerId] = player.copy(consecutiveTimeouts = 4)
            }
        )

        val afterTimeout = GameEngine.handleTurnTimeout(config, stateNearElimination)
        val eliminated = afterTimeout.players[playerId]!!
        assertTrue(eliminated.isEliminated)
    }

    // ── Board configs ───────────────────────────────────────────────

    @Test
    fun `penta board has correct dimensions`() {
        val config = BoardConfig.Penta
        assertEquals(5, config.totalSlots)
        assertEquals(65, config.pathLength)
        assertEquals(13, config.cellsPerSegment)
        assertEquals(5, config.homeColumnLength)
    }

    @Test
    fun `hex board has correct dimensions`() {
        val config = BoardConfig.Hex
        assertEquals(6, config.totalSlots)
        assertEquals(78, config.pathLength)
        assertEquals(13, config.cellsPerSegment)
        assertEquals(5, config.homeColumnLength)
    }

    @Test
    fun `toAbsolutePosition wraps correctly`() {
        val config = BoardConfig.Classic
        assertEquals(0, config.toAbsolutePosition(0, 0))
        assertEquals(13, config.toAbsolutePosition(0, 1))
        assertEquals(14, config.toAbsolutePosition(1, 1))
        assertEquals(0, config.toAbsolutePosition(26, 2))
    }

    @Test
    fun `slot assignment spreads players evenly`() {
        val classic = BoardConfig.Classic
        assertEquals(listOf(0, 2), classic.assignSlots(2))
        assertEquals(listOf(0, 1, 3), classic.assignSlots(3))
        assertEquals(listOf(0, 1, 2, 3), classic.assignSlots(4))

        val hex = BoardConfig.Hex
        assertEquals(listOf(0, 3), hex.assignSlots(2))
        assertEquals(listOf(0, 2, 4), hex.assignSlots(3))
    }

    // ── Auto-pass on no valid moves ─────────────────────────────────

    @Test
    fun `auto-pass when no valid moves after roll`() {
        val config = BoardConfig.Classic
        val state = createTestGame()
        val playerId = state.currentTurnPlayerId

        val afterRoll = GameEngine.applyDiceRoll(config, state, 3)
        assertNotEquals(playerId, afterRoll.currentTurnPlayerId)
        assertEquals(GamePhase.ROLLING, afterRoll.phase)
    }
}
