package com.example.ludosample.engine

enum class PlayerColor {
    RED, GREEN, YELLOW, BLUE, ORANGE, PURPLE;

    companion object {
        fun forSlotCount(count: Int): List<PlayerColor> = entries.take(count)
    }
}

enum class BoardType {
    CLASSIC, PENTA, HEX;

    companion object {
        fun forPlayerCount(count: Int): BoardType = when (count) {
            in 2..4 -> CLASSIC
            5 -> PENTA
            6 -> HEX
            else -> throw IllegalArgumentException("Player count must be 2-6, got $count")
        }
    }
}

enum class GamePhase {
    WAITING_FOR_PLAYERS,
    ROLLING,
    MOVING,
    FINISHED
}

data class Token(
    val position: Int = -1,
    val isHome: Boolean = false
)

data class Player(
    val id: String = "",
    val name: String = "",
    val color: PlayerColor = PlayerColor.RED,
    val slotIndex: Int = 0,
    val tokens: List<Token> = List(4) { Token() },
    val isFinished: Boolean = false,
    val consecutiveTimeouts: Int = 0,
    val isEliminated: Boolean = false
)

data class GameState(
    val roomCode: String = "",
    val boardType: BoardType = BoardType.CLASSIC,
    val players: Map<String, Player> = emptyMap(),
    val currentTurnPlayerId: String = "",
    val diceValue: Int? = null,
    val phase: GamePhase = GamePhase.WAITING_FOR_PLAYERS,
    val winner: String? = null,
    val finishOrder: List<String> = emptyList(),
    val maxPlayers: Int = 4,
    val turnStartedAt: Long = 0L,
    val consecutiveSixes: Int = 0,
    val creatorPlayerId: String = ""
)

data class MoveResult(
    val newState: GameState,
    val captured: Boolean = false,
    val enteredHome: Boolean = false
)
