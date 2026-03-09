package com.example.ludosample.data

import com.example.ludosample.engine.BoardType
import com.example.ludosample.engine.GamePhase
import com.example.ludosample.engine.GameState
import com.example.ludosample.engine.Player
import com.example.ludosample.engine.PlayerColor
import com.example.ludosample.engine.Token
import com.example.ludosample.engine.ChatMessage
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

class GameRepository {

    companion object {
        private const val STALE_ROOM_MS = 2 * 60 * 60 * 1000L // 2 hours
        private const val FINISHED_ROOM_MS = 5 * 60 * 1000L // 5 minutes after creation
    }

    private val db = FirebaseDatabase.getInstance("https://ludo-sample-f1ce3-default-rtdb.asia-southeast1.firebasedatabase.app")
    private val gamesRef = db.getReference("games")

    /**
     * Returns the server time offset to correct for device clock skew.
     */
    val serverTimeOffset: Flow<Long> = callbackFlow {
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

    fun observeConnected(): Flow<Boolean> = callbackFlow {
        val ref = db.getReference(".info/connected")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(snapshot.getValue(Boolean::class.java) ?: false)
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    suspend fun createRoom(playerId: String, playerName: String, maxPlayers: Int): String {
        val roomCode = generateRoomCode()
        val boardType = BoardType.forPlayerCount(maxPlayers)

        cleanupStaleRooms()

        val initialData = mapOf(
            "roomCode" to roomCode,
            "boardType" to boardType.name,
            "maxPlayers" to maxPlayers,
            "phase" to GamePhase.WAITING_FOR_PLAYERS.name,
            "creatorPlayerId" to playerId,
            "consecutiveSixes" to 0,
            "createdAt" to ServerValue.TIMESTAMP,
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
                    "kills" to 0,
                    "deaths" to 0,
                    "disconnectedAt" to 0,
                    "tokens" to List(4) { mapOf("position" to -1, "isHome" to false) }
                )
            )
        )

        try {
            withTimeout(10_000L) {
                gamesRef.child(roomCode).setValue(initialData).await()
            }
        } catch (e: TimeoutCancellationException) {
            throw Exception("Firebase write timed out. Ensure Realtime Database is created in Firebase Console and security rules allow writes.")
        }
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
            "kills" to 0,
            "deaths" to 0,
            "disconnectedAt" to 0,
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

    fun setupOnDisconnect(roomCode: String, playerId: String) {
        val playerRef = gamesRef.child(roomCode).child("players").child(playerId)
        playerRef.child("disconnectedAt").onDisconnect().setValue(ServerValue.TIMESTAMP)
    }

    fun cancelOnDisconnect(roomCode: String, playerId: String) {
        val playerRef = gamesRef.child(roomCode).child("players").child(playerId)
        playerRef.child("disconnectedAt").onDisconnect().cancel()
    }

    suspend fun clearDisconnected(roomCode: String, playerId: String) {
        try {
            gamesRef.child(roomCode).child("players").child(playerId)
                .child("disconnectedAt").setValue(0).await()
        } catch (_: Exception) { }
    }

    suspend fun eliminatePlayer(roomCode: String, playerId: String) {
        try {
            val playerRef = gamesRef.child(roomCode).child("players").child(playerId)
            playerRef.child("isEliminated").setValue(true).await()
            playerRef.child("disconnectedAt").setValue(0).await()
            playerRef.child("tokens").setValue(
                List(4) { mapOf("position" to -1, "isHome" to false) }
            ).await()
        } catch (_: Exception) { }
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
            creatorPlayerId = snapshot.child("creatorPlayerId").getValue(String::class.java) ?: "",
            nextRoomCode = snapshot.child("nextRoomCode").getValue(String::class.java) ?: ""
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
            isEliminated = snapshot.child("isEliminated").getValue(Boolean::class.java) ?: false,
            kills = snapshot.child("kills").getValue(Int::class.java) ?: 0,
            deaths = snapshot.child("deaths").getValue(Int::class.java) ?: 0,
            disconnectedAt = snapshot.child("disconnectedAt").getValue(Long::class.java) ?: 0
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
                "isEliminated" to player.isEliminated,
                "kills" to player.kills,
                "deaths" to player.deaths,
                "disconnectedAt" to player.disconnectedAt
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
            "creatorPlayerId" to state.creatorPlayerId,
            "nextRoomCode" to state.nextRoomCode
        )
    }

    suspend fun setNextRoomCode(currentRoomCode: String, nextRoomCode: String) {
        try {
            gamesRef.child(currentRoomCode).child("nextRoomCode")
                .setValue(nextRoomCode).await()
        } catch (_: Exception) { }
    }

    suspend fun getNextRoomCode(roomCode: String): String? {
        return try {
            val snap = gamesRef.child(roomCode).child("nextRoomCode").get().await()
            snap.getValue(String::class.java)?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun isPlayerInActiveGame(roomCode: String, playerId: String): Boolean {
        return try {
            val snap = gamesRef.child(roomCode).get().await()
            if (!snap.exists()) return false
            val phase = snap.child("phase").getValue(String::class.java) ?: return false
            if (phase == GamePhase.FINISHED.name) return false
            val playerSnap = snap.child("players").child(playerId)
            if (!playerSnap.exists()) return false
            val isEliminated = playerSnap.child("isEliminated").getValue(Boolean::class.java) ?: false
            !isEliminated
        } catch (_: Exception) {
            false
        }
    }

    // ── Chat ─────────────────────────────────────────────────────────

    fun observeMessages(roomCode: String): Flow<ChatMessage> = callbackFlow {
        val query = gamesRef.child(roomCode).child("messages")
            .orderByChild("timestamp").limitToLast(50)
        val listener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val msg = snapshotToChatMessage(snapshot) ?: return
                trySend(msg)
            }
            override fun onChildChanged(s: DataSnapshot, p: String?) {}
            override fun onChildRemoved(s: DataSnapshot) {}
            override fun onChildMoved(s: DataSnapshot, p: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        }
        query.addChildEventListener(listener)
        awaitClose { query.removeEventListener(listener) }
    }

    suspend fun sendMessage(roomCode: String, message: ChatMessage) {
        try {
            val data = mapOf(
                "senderId" to message.senderId,
                "senderName" to message.senderName,
                "senderColor" to message.senderColor,
                "type" to message.type,
                "content" to message.content,
                "timestamp" to ServerValue.TIMESTAMP
            )
            gamesRef.child(roomCode).child("messages").push().setValue(data).await()
        } catch (_: Exception) { }
    }

    private fun snapshotToChatMessage(snapshot: DataSnapshot): ChatMessage? {
        val id = snapshot.key ?: return null
        return ChatMessage(
            id = id,
            senderId = snapshot.child("senderId").getValue(String::class.java) ?: "",
            senderName = snapshot.child("senderName").getValue(String::class.java) ?: "",
            senderColor = snapshot.child("senderColor").getValue(String::class.java) ?: "",
            type = snapshot.child("type").getValue(String::class.java) ?: "text",
            content = snapshot.child("content").getValue(String::class.java) ?: "",
            timestamp = snapshot.child("timestamp").getValue(Long::class.java) ?: 0
        )
    }

    suspend fun deleteFinishedRoom(roomCode: String) {
        try {
            gamesRef.child(roomCode).removeValue().await()
        } catch (_: Exception) { }
    }

    suspend fun cleanupStaleRooms() {
        try {
            val snapshot = gamesRef.get().await()
            val now = System.currentTimeMillis()
            val staleCutoff = now - STALE_ROOM_MS
            val finishedCutoff = now - FINISHED_ROOM_MS
            for (child in snapshot.children) {
                val createdAt = child.child("createdAt").getValue(Long::class.java) ?: 0L
                val phase = child.child("phase").getValue(String::class.java) ?: ""
                val isFinished = phase == GamePhase.FINISHED.name
                val isStale = createdAt < staleCutoff
                val isFinishedOld = isFinished && createdAt < finishedCutoff

                if (isStale || isFinishedOld) {
                    child.ref.removeValue()
                }
            }
        } catch (e: Exception) { }
    }

    private fun generateRoomCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { chars.random() }.joinToString("")
    }
}
