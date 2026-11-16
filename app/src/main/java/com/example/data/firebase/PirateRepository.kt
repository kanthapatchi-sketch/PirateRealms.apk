package com.example.data.firebase

import android.content.Context
import com.example.R
import com.example.data.model.BotDifficulty
import com.example.data.model.CaptainId
import com.example.data.model.PlayerState
import com.example.data.model.RoomDoc
import com.example.data.model.RoomMessageDoc
import com.example.data.model.UserProfileDoc
import com.example.game.BoardConfig
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID
import kotlin.random.Random

class PirateRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth get() = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("ต้องเข้าสู่ระบบด้วย Google ก่อนใช้งานโหมดออนไลน์")
    }

    fun observeUserProfile(userId: String): Flow<UserProfileDoc?> {
        val path = "users/$userId"
        return db.collection("users").document(userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObject(UserProfileDoc::class.java, ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                val fsErr = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (fsErr == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == userId) {
                    handleFirestoreError(fsErr ?: error, OperationType.GET, path)
                }
                throw error
            }
    }

    suspend fun ensureUserProfile(
        displayName: String,
        preferredCaptain: CaptainId = CaptainId.IGNIS
    ): Result<UserProfileDoc> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        return try {
            val snap = docRef.get().await()
            val existing = snap.toObject(UserProfileDoc::class.java, ServerTimestampBehavior.ESTIMATE)
            if (existing != null) {
                Result.success(existing)
            } else {
                val safeName = displayName.trim().ifBlank { "Captain" }.take(40)
                val payload = mapOf(
                    "userId" to uid,
                    "displayName" to safeName,
                    "preferredCaptain" to preferredCaptain.id,
                    "wins" to 0L,
                    "gamesPlayed" to 0L,
                    "highestGold" to 2000L,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
                val created = docRef.get().await()
                    .toObject(UserProfileDoc::class.java, ServerTimestampBehavior.ESTIMATE)
                    ?: UserProfileDoc(
                        id = uid,
                        userId = uid,
                        displayName = safeName,
                        preferredCaptain = preferredCaptain.id
                    )
                Result.success(created)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updatePreferredCaptain(captain: CaptainId): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        return try {
            docRef.update(
                mapOf(
                    "preferredCaptain" to captain.id,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun recordMatchOutcome(won: Boolean, finalGold: Int): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        return try {
            val snap = docRef.get().await()
            val current = snap.toObject(UserProfileDoc::class.java, ServerTimestampBehavior.ESTIMATE)
            if (current != null) {
                val newHigh = maxOf(current.highestGold, finalGold.toLong().coerceAtLeast(0L))
                docRef.update(
                    mapOf(
                        "wins" to current.wins + if (won) 1L else 0L,
                        "gamesPlayed" to current.gamesPlayed + 1L,
                        "highestGold" to newHigh,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun createRoom(
        hostName: String,
        hostCaptain: CaptainId,
        isPublic: Boolean,
        maxPlayers: Int = 4
    ): Result<String> {
        val uid = requireUserId()
        val roomCode = generateRoomCode()
        val docRef = db.collection("rooms").document(roomCode)
        val safeName = hostName.trim().ifBlank { "Captain" }.take(40)
        val hostSlot = PlayerState(
            slotIndex = 0,
            uid = uid,
            name = safeName,
            captain = hostCaptain,
            isBot = false,
            specialDiceCharges = if (hostCaptain == CaptainId.VANE) 5 else 3
        )
        val payload = mapOf(
            "roomCode" to roomCode,
            "hostId" to uid,
            "status" to "waiting",
            "isPublic" to isPublic,
            "maxPlayers" to maxPlayers.coerceIn(2, 4).toLong(),
            "memberUids" to listOf(uid),
            "players" to listOf(hostSlot.toFirestoreMap()),
            "tileOwners" to List(BoardConfig.BOARD_SIZE) { "" },
            "tileLevels" to List(BoardConfig.BOARD_SIZE) { 0L },
            "tileFestivals" to List(BoardConfig.BOARD_SIZE) { 1L },
            "currentTurnIndex" to 0L,
            "turnNumber" to 0L,
            "lastDice1" to 0L,
            "lastDice2" to 0L,
            "lastLog" to "กัปตัน $safeName เปิดห้องเดินเรือ #$roomCode",
            "winnerUid" to "",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(roomCode)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun joinRoomByCode(
        rawRoomCode: String,
        playerName: String,
        playerCaptain: CaptainId
    ): Result<RoomDoc> {
        val uid = requireUserId()
        val roomCode = rawRoomCode.trim().uppercase()
        if (!roomCode.matches(Regex("^[A-Z0-9]{6}$"))) {
            return Result.failure(IllegalArgumentException("รหัสห้องต้องเป็นตัวอักษร/ตัวเลข 6 หลัก"))
        }
        val docRef = db.collection("rooms").document(roomCode)
        return try {
            val snap = docRef.get().await()
            val room = snap.toObject(RoomDoc::class.java, ServerTimestampBehavior.ESTIMATE)
                ?: return Result.failure(IllegalStateException("ไม่พบห้องรหัส $roomCode"))

            if (uid in room.memberUids) {
                return Result.success(room)
            }
            if (room.status != "waiting") {
                return Result.failure(IllegalStateException("ห้อง $roomCode เริ่มเกมไปแล้ว"))
            }
            if (room.memberUids.size >= room.maxPlayers.toInt()) {
                return Result.failure(IllegalStateException("ห้อง $roomCode เต็มแล้ว"))
            }

            val existingPlayers = room.players.mapIndexed { idx, map ->
                PlayerState.fromFirestoreMap(map, idx)
            }
            val safeName = playerName.trim().ifBlank { "Pirate" }.take(40)
            val newSlot = PlayerState(
                slotIndex = existingPlayers.size,
                uid = uid,
                name = safeName,
                captain = playerCaptain,
                isBot = false,
                specialDiceCharges = if (playerCaptain == CaptainId.VANE) 5 else 3
            )
            val updatedPlayers = (existingPlayers + newSlot).map { it.toFirestoreMap() }
            val updatedMembers = room.memberUids + uid

            docRef.update(
                mapOf(
                    "memberUids" to updatedMembers,
                    "players" to updatedPlayers,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            val updatedSnap = docRef.get().await()
            val updatedRoom = updatedSnap.toObject(RoomDoc::class.java, ServerTimestampBehavior.ESTIMATE)
                ?: room.copy(memberUids = updatedMembers, players = updatedPlayers)
            Result.success(updatedRoom)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun findOrCreatePublicRoom(
        playerName: String,
        playerCaptain: CaptainId
    ): Result<String> {
        val uid = requireUserId()
        val roomsCol = db.collection("rooms")
        return try {
            val querySnap = roomsCol
                .whereEqualTo("status", "waiting")
                .whereEqualTo("isPublic", true)
                .limit(10)
                .get()
                .await()

            val candidates = querySnap.toObjects(RoomDoc::class.java, ServerTimestampBehavior.ESTIMATE)
                .filter { it.memberUids.size < it.maxPlayers.toInt() }

            val myExisting = candidates.firstOrNull { uid in it.memberUids }
            if (myExisting != null) {
                return Result.success(myExisting.roomCode)
            }

            val openRoom = candidates.firstOrNull()
            if (openRoom != null) {
                val joined = joinRoomByCode(openRoom.roomCode, playerName, playerCaptain)
                if (joined.isSuccess) {
                    return Result.success(openRoom.roomCode)
                }
            }

            createRoom(
                hostName = playerName,
                hostCaptain = playerCaptain,
                isPublic = true,
                maxPlayers = 4
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, "rooms")
            Result.failure(e)
        }
    }

    fun observeRoom(roomCode: String): Flow<RoomDoc?> {
        val path = "rooms/$roomCode"
        return db.collection("rooms").document(roomCode)
            .snapshots()
            .map { snapshot ->
                snapshot.toObject(RoomDoc::class.java, ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                val fsErr = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (fsErr == null && error is CancellationException) throw error
                if (auth.currentUser != null) {
                    handleFirestoreError(fsErr ?: error, OperationType.GET, path)
                }
                throw error
            }
    }

    fun observeMyRooms(userId: String): Flow<List<RoomDoc>> {
        val path = "rooms"
        return db.collection("rooms")
            .whereArrayContains("memberUids", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(RoomDoc::class.java, ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                val fsErr = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (fsErr == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == userId) {
                    handleFirestoreError(fsErr ?: error, OperationType.LIST, path)
                }
                throw error
            }
    }

    suspend fun startMatch(
        roomCode: String,
        finalPlayers: List<PlayerState>,
        initialLog: String
    ): Result<Unit> {
        requireUserId()
        val docRef = db.collection("rooms").document(roomCode)
        return try {
            docRef.update(
                mapOf(
                    "status" to "playing",
                    "players" to finalPlayers.map { it.toFirestoreMap() },
                    "tileOwners" to List(BoardConfig.BOARD_SIZE) { "" },
                    "tileLevels" to List(BoardConfig.BOARD_SIZE) { 0L },
                    "tileFestivals" to List(BoardConfig.BOARD_SIZE) { 1L },
                    "currentTurnIndex" to 0L,
                    "turnNumber" to 1L,
                    "lastLog" to initialLog.take(240),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun commitTurnState(
        roomCode: String,
        players: List<PlayerState>,
        tileOwners: List<String>,
        tileLevels: List<Int>,
        tileFestivals: List<Int>,
        currentTurnIndex: Int,
        turnNumber: Int,
        lastDice1: Int,
        lastDice2: Int,
        lastLog: String,
        winnerUid: String = ""
    ): Result<Unit> {
        requireUserId()
        val docRef = db.collection("rooms").document(roomCode)
        val newStatus = if (winnerUid.isNotBlank()) "finished" else "playing"
        return try {
            docRef.update(
                mapOf(
                    "status" to newStatus,
                    "players" to players.map { it.toFirestoreMap() },
                    "tileOwners" to tileOwners,
                    "tileLevels" to tileLevels.map { it.toLong() },
                    "tileFestivals" to tileFestivals.map { it.toLong() },
                    "currentTurnIndex" to currentTurnIndex.toLong(),
                    "turnNumber" to turnNumber.toLong(),
                    "lastDice1" to lastDice1.toLong(),
                    "lastDice2" to lastDice2.toLong(),
                    "lastLog" to lastLog.take(240),
                    "winnerUid" to winnerUid,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun leaveWaitingRoom(roomCode: String): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("rooms").document(roomCode)
        return try {
            val snap = docRef.get().await()
            val room = snap.toObject(RoomDoc::class.java, ServerTimestampBehavior.ESTIMATE)
                ?: return Result.success(Unit)
            if (room.hostId == uid) {
                docRef.delete().await()
            } else if (uid in room.memberUids && room.status == "waiting") {
                val updatedMembers = room.memberUids.filterNot { it == uid }
                val updatedPlayers = room.players
                    .mapIndexed { idx, map -> PlayerState.fromFirestoreMap(map, idx) }
                    .filterNot { it.uid == uid }
                    .mapIndexed { idx, p -> p.copy(slotIndex = idx).toFirestoreMap() }
                docRef.update(
                    mapOf(
                        "memberUids" to updatedMembers,
                        "players" to updatedPlayers,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    fun observeMessages(roomCode: String, userId: String): Flow<List<RoomMessageDoc>> {
        val path = "rooms/$roomCode/messages"
        return db.collection("rooms").document(roomCode)
            .collection("messages")
            .whereArrayContains("memberUids", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(RoomMessageDoc::class.java, ServerTimestampBehavior.ESTIMATE)
                    .sortedBy { it.createdAt?.seconds ?: Long.MAX_VALUE }
            }
            .catch { error ->
                val fsErr = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (fsErr == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == userId) {
                    handleFirestoreError(fsErr ?: error, OperationType.LIST, path)
                }
                throw error
            }
    }

    suspend fun sendMessage(
        roomCode: String,
        senderName: String,
        captain: CaptainId,
        text: String,
        stickerId: String,
        memberUids: List<String>
    ): Result<Unit> {
        val uid = requireUserId()
        val msgId = UUID.randomUUID().toString().replace("-", "").take(20)
        val docRef = db.collection("rooms").document(roomCode)
            .collection("messages").document(msgId)
        val payload = RoomMessageDoc(
            roomId = roomCode,
            senderId = uid,
            senderName = senderName.trim().ifBlank { "Captain" }.take(40),
            captainId = captain.id,
            text = text.trim().ifBlank { "Ahoy!" }.take(120),
            stickerId = stickerId.take(32),
            memberUids = memberUids
        ).toCreateMap()
        return try {
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    private fun generateRoomCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { chars[Random.nextInt(chars.length)] }.joinToString("")
    }
}
