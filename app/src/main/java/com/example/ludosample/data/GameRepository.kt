package com.example.ludosample.data

import com.example.ludosample.engine.BoardType
import com.example.ludosample.engine.GamePhase
import com.example.ludosample.engine.GameState
import com.example.ludosample.engine.Player
import com.example.ludosample.engine.PlayerColor
import com.example.ludosample.engine.Token
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class GameRepository {

    private val db = FirebaseDatabase.getInstance()
    private val gamesRef = db.getReference("games")

    /**
     * Returns the server time offset to correct for device clock skew.
     */
    private val serverTimeOffset: Flow<Long> = callbackFlow {
        val ref = db.getReference(".info/serverTimeOffset")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(snapshot.getValue(Long::class.java) ?: 0L)
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    suspend fun createRoom(playerId: String, playerName: String, maxPlayers: Int): String {
        val roomCode = generateRoomCode()
        val boardType = BoardType.forPlayerCount(maxPlayers)

        val initialData = mapOf(
            "roomCode" to roomCode,
            "boardType" to boardType.name,
            "maxPlayers" to maxPlayers,
            "phase" to GamePhase.WAITING_FOR_PLAYERS.name,
            "creatorPlayerId" to playerId,
            "consecutiveSixes" to 0,
            "turnStartedAt" to ServerValue.TIMESTAMP,
            "finishOrder" to emptyList<String>(),
            "players" to mapOf(
                playerId to mapOf(
                    "id" to playerId,
                    "name" to playerName,
                    "color" to PlayerColor.RED.name,
                    "slotIndex" to 0,
                    "isFinished" to false,
                    "consecutiveTimeouts" to 0,
                    "isEliminated" to false,
                    "tokens" to List(4) { mapOf("position" to -1, "isHome" to false) }
                )
            )
        )

        gamesRef.child(roomCode).setValue(initialData).await()
        return roomCode
    }

    suspend fun joinRoom(roomCode: String, playerId: String, playerName: String): Boolean {
        val snapshot = gamesRef.child(roomCode).get().await()
        if (!snapshot.exists()) return false

        val phase = snapshot.child("phase").getValue(String::class.java)
        if (phase != GamePhase.WAITING_FOR_PLAYERS.name) return false

        val maxPlayers = snapshot.child("maxPlayers").getValue(Int::class.java) ?: 4
        val playersSnap = snapshot.child("players")
        val currentCount = playersSnap.childrenCount.toInt()
        if (currentCount >= maxPlayers) return false

        val existingSlots = playersSnap.children.mapNotNull {
            it.child("slotIndex").getValue(Int::class.java)
        }.toSet()

        val boardType = BoardType.valueOf(
            snapshot.child("boardType").getValue(String::class.java) ?: "CLASSIC"
        )
        val config = com.example.ludosample.engine.BoardConfig.forBoardType(boardType)
        val allSlots = config.assignSlots(maxPlayers)
        val availableSlot = allSlots.first { it !in existingSlots }
        val color = config.colorForSlot(availableSlot)

        val playerData = mapOf(
            "id" to playerId,
            "name" to playerName,
            "color" to color.name,
            "slotIndex" to availableSlot,
            "isFinished" to false,
            "consecutiveTimeouts" to 0,
            "isEliminated" to false,
            "tokens" to List(4) { mapOf("position" to -1, "isHome" to false) }
        )

        gamesRef.child(roomCode).child("players").child(playerId)
            .setValue(playerData).await()
        return true
    }

    fun listenToGame(roomCode: String): Flow<GameState> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val state = snapshotToGameState(snapshot)
                if (state != null) trySend(state)
            }
            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        gamesRef.child(roomCode).addValueEventListener(listener)
        awaitClose { gamesRef.child(roomCode).removeEventListener(listener) }
    }

    suspend fun updateGameState(roomCode: String, state: GameState) {
        val data = gameStateToMap(state)
        gamesRef.child(roomCode).updateChildren(data).await()
    }

    suspend fun startGame(roomCode: String, state: GameState) {
        val data = gameStateToMap(state).toMutableMap()
        data["turnStartedAt"] = ServerValue.TIMESTAMP
        gamesRef.child(roomCode).updateChildren(data).await()
    }

    suspend fun deleteRoom(roomCode: String) {
        gamesRef.child(roomCode).removeValue().await()
    }

    // ── Serialization helpers ───────────────────────────────────────

    private fun snapshotToGameState(snapshot: DataSnapshot): GameState? {
        if (!snapshot.exists()) return null

        val players = mutableMapOf<String, Player>()
        for (child in snapshot.child("players").children) {
            val p = snapshotToPlayer(child) ?: continue
            players[p.id] = p
        }

        @Suppress("UNCHECKED_CAST")
        val finishOrder = (snapshot.child("finishOrder").value as? List<String>) ?: emptyList()

        return GameState(
            roomCode = snapshot.child("roomCode").getValue(String::class.java) ?: "",
            boardType = BoardType.valueOf(
                snapshot.child("boardType").getValue(String::class.java) ?: "CLASSIC"
            ),
            players = players,
            currentTurnPlayerId = snapshot.child("currentTurnPlayerId").getValue(String::class.java) ?: "",
            diceValue = snapshot.child("diceValue").getValue(Int::class.java),
            phase = GamePhase.valueOf(
                snapshot.child("phase").getValue(String::class.java) ?: "WAITING_FOR_PLAYERS"
            ),
            winner = snapshot.child("winner").getValue(String::class.java),
            finishOrder = finishOrder,
            maxPlayers = snapshot.child("maxPlayers").getValue(Int::class.java) ?: 4,
            turnStartedAt = snapshot.child("turnStartedAt").getValue(Long::class.java) ?: 0L,
            consecutiveSixes = snapshot.child("consecutiveSixes").getValue(Int::class.java) ?: 0,
            creatorPlayerId = snapshot.child("creatorPlayerId").getValue(String::class.java) ?: ""
        )
    }

    private fun snapshotToPlayer(snapshot: DataSnapshot): Player? {
        val id = snapshot.child("id").getValue(String::class.java) ?: return null
        val tokens = mutableListOf<Token>()
        for (t in snapshot.child("tokens").children) {
            tokens.add(
                Token(
                    position = t.child("position").getValue(Int::class.java) ?: -1,
                    isHome = t.child("isHome").getValue(Boolean::class.java) ?: false
                )
            )
        }
        while (tokens.size < 4) tokens.add(Token())

        return Player(
            id = id,
            name = snapshot.child("name").getValue(String::class.java) ?: "",
            color = PlayerColor.valueOf(
                snapshot.child("color").getValue(String::class.java) ?: "RED"
            ),
            slotIndex = snapshot.child("slotIndex").getValue(Int::class.java) ?: 0,
            tokens = tokens,
            isFinished = snapshot.child("isFinished").getValue(Boolean::class.java) ?: false,
            consecutiveTimeouts = snapshot.child("consecutiveTimeouts").getValue(Int::class.java) ?: 0,
            isEliminated = snapshot.child("isEliminated").getValue(Boolean::class.java) ?: false
        )
    }

    private fun gameStateToMap(state: GameState): Map<String, Any?> {
        val playersMap = state.players.mapValues { (_, player) ->
            mapOf(
                "id" to player.id,
                "name" to player.name,
                "color" to player.color.name,
                "slotIndex" to player.slotIndex,
                "tokens" to player.tokens.map { t ->
                    mapOf("position" to t.position, "isHome" to t.isHome)
                },
                "isFinished" to player.isFinished,
                "consecutiveTimeouts" to player.consecutiveTimeouts,
                "isEliminated" to player.isEliminated
            )
        }

        return mapOf(
            "roomCode" to state.roomCode,
            "boardType" to state.boardType.name,
            "players" to playersMap,
            "currentTurnPlayerId" to state.currentTurnPlayerId,
            "diceValue" to state.diceValue,
            "phase" to state.phase.name,
            "winner" to state.winner,
            "finishOrder" to state.finishOrder,
            "maxPlayers" to state.maxPlayers,
            "turnStartedAt" to ServerValue.TIMESTAMP,
            "consecutiveSixes" to state.consecutiveSixes,
            "creatorPlayerId" to state.creatorPlayerId
        )
    }

    private fun generateRoomCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { chars.random() }.joinToString("")
    }
}
