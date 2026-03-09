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
    val isEliminated: Boolean = false,
    val kills: Int = 0,
    val deaths: Int = 0,
    val disconnectedAt: Long = 0
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
    val creatorPlayerId: String = "",
    val nextRoomCode: String = ""
)

data class MoveResult(
    val newState: GameState,
    val captured: Boolean = false,
    val enteredHome: Boolean = false
)

data class ChatMessage(
    val id: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val senderColor: String = "",
    val type: String = "text",
    val content: String = "",
    val timestamp: Long = 0
)

object ChatStickers {
    val all: LinkedHashMap<String, String> = linkedMapOf(
        "laughing" to "\uD83D\uDE02",
        "crying" to "\uD83D\uDE2D",
        "angry" to "\uD83D\uDE21",
        "thinking" to "\uD83E\uDD14",
        "fire" to "\uD83D\uDD25",
        "skull" to "\uD83D\uDC80",
        "crown" to "\uD83D\uDC51",
        "clown" to "\uD83E\uDD21",
        "heart" to "\u2764\uFE0F",
        "thumbs_up" to "\uD83D\uDC4D",
        "thumbs_down" to "\uD83D\uDC4E",
        "eyes" to "\uD83D\uDC40",
        "gg" to "GG",
        "oops" to "Oops!",
        "nice" to "Nice!",
        "hurry" to "Hurry up!"
    )

    fun display(key: String): String = all[key] ?: key
}
