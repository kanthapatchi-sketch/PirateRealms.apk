package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.PirateSoundEffect
import com.example.data.firebase.PirateRepository
import com.example.data.model.BotDifficulty
import com.example.data.model.BotSlotSelection
import com.example.data.model.BuildingTier
import com.example.data.model.CaptainId
import com.example.data.model.DiceMode
import com.example.data.model.PlayerState
import com.example.data.model.RoomDoc
import com.example.data.model.RoomMessageDoc
import com.example.data.model.StickerEmotion
import com.example.data.model.UserProfileDoc
import com.example.game.BoardConfig
import com.example.game.BotAiEngine
import com.example.game.GameEngine
import com.example.game.GameSnapshot
import com.example.game.PendingBoardAction
import com.example.ui.components.DiceRollAnimState
import com.example.ui.components.StepMoveAnimState
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StickerPopup(
    val senderUid: String,
    val emoji: String,
    val text: String,
    val timestampMs: Long
)

data class LobbyUiState(
    val selectedCaptain: CaptainId = CaptainId.IGNIS,
    val botSlot2: BotSlotSelection = BotSlotSelection.AUTO_RANDOM,
    val botSlot3: BotSlotSelection = BotSlotSelection.AUTO_RANDOM,
    val botSlot4: BotSlotSelection = BotSlotSelection.AUTO_RANDOM,
    val roomCodeInput: String = "",
    val activeWaitingRoomCode: String? = null,
    val isBusy: Boolean = false,
    val errorMessage: String? = null
)

class PirateViewModel(
    private val repository: PirateRepository,
    private val currentUserId: String,
    private val initialDisplayName: String
) : ViewModel() {

    private val _lobbyState = MutableStateFlow(LobbyUiState())
    val lobbyState: StateFlow<LobbyUiState> = _lobbyState.asStateFlow()

    private val _gameState = MutableStateFlow<GameSnapshot?>(null)
    val gameState: StateFlow<GameSnapshot?> = _gameState.asStateFlow()

    private val _isOnlineMatch = MutableStateFlow(false)
    val isOnlineMatch: StateFlow<Boolean> = _isOnlineMatch.asStateFlow()

    private val _activeRoomCode = MutableStateFlow<String?>(null)
    val activeRoomCode: StateFlow<String?> = _activeRoomCode.asStateFlow()

    private val _selectedDiceMode = MutableStateFlow(DiceMode.NORMAL)
    val selectedDiceMode: StateFlow<DiceMode> = _selectedDiceMode.asStateFlow()

    private val _stickerPopups = MutableStateFlow<Map<String, StickerPopup>>(emptyMap())
    val stickerPopups: StateFlow<Map<String, StickerPopup>> = _stickerPopups.asStateFlow()

    private val _roomMessages = MutableStateFlow<List<RoomMessageDoc>>(emptyList())
    val roomMessages: StateFlow<List<RoomMessageDoc>> = _roomMessages.asStateFlow()

    private val _waitingRoom = MutableStateFlow<RoomDoc?>(null)
    val waitingRoom: StateFlow<RoomDoc?> = _waitingRoom.asStateFlow()

    private val _diceRollAnimState = MutableStateFlow(DiceRollAnimState())
    val diceRollAnimState: StateFlow<DiceRollAnimState> = _diceRollAnimState.asStateFlow()

    private val _stepMoveState = MutableStateFlow<StepMoveAnimState?>(null)
    val stepMoveState: StateFlow<StepMoveAnimState?> = _stepMoveState.asStateFlow()

    private val _soundEvents = MutableSharedFlow<PirateSoundEffect>(extraBufferCapacity = 16)
    val soundEvents: SharedFlow<PirateSoundEffect> = _soundEvents.asSharedFlow()

    val userProfile: StateFlow<UserProfileDoc?> = repository.observeUserProfile(currentUserId)
        .catch { error ->
            val fsErr = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (fsErr == null && error is CancellationException) throw error
            Log.w(TAG, "Error observing user profile", fsErr ?: error)
            emit(null)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = null
        )

    private data class PendingTurnExecution(
        val rollId: Long,
        val playerIndex: Int,
        val prepared: GameEngine.PreparedRollResult,
        val isBot: Boolean
    )

    private var roomObserverJob: Job? = null
    private var messageObserverJob: Job? = null
    private var botScheduleJob: Job? = null
    private var humanAutoEndJob: Job? = null
    private var pendingTurnExecution: PendingTurnExecution? = null
    private var rollSequenceCounter = 0L
    private var recordedEndForMatch = false

    init {
        viewModelScope.launch {
            val res = repository.ensureUserProfile(initialDisplayName, CaptainId.IGNIS)
            res.getOrNull()?.let { profile ->
                _lobbyState.update { it.copy(selectedCaptain = CaptainId.fromId(profile.preferredCaptain)) }
            }
        }
    }

    fun selectCaptain(captain: CaptainId) {
        _lobbyState.update { it.copy(selectedCaptain = captain) }
        viewModelScope.launch {
            repository.updatePreferredCaptain(captain)
        }
    }

    fun updateBotSlot(slotNumber: Int, selection: BotSlotSelection) {
        _lobbyState.update { state ->
            when (slotNumber) {
                2 -> state.copy(botSlot2 = selection)
                3 -> state.copy(botSlot3 = selection)
                4 -> state.copy(botSlot4 = selection)
                else -> state
            }
        }
    }

    fun updateRoomCodeInput(code: String) {
        _lobbyState.update { it.copy(roomCodeInput = code.uppercase().take(6), errorMessage = null) }
    }

    fun clearError() {
        _lobbyState.update { it.copy(errorMessage = null) }
    }

    fun startSoloGame() {
        val lobby = _lobbyState.value
        val playerCaptain = lobby.selectedCaptain
        val playerName = userProfile.value?.displayName?.ifBlank { initialDisplayName } ?: initialDisplayName

        val availableCaptains = CaptainId.entries.filter { it != playerCaptain }.toMutableList()
        val players = mutableListOf(
            PlayerState(
                slotIndex = 0,
                uid = currentUserId,
                name = playerName,
                captain = playerCaptain,
                isBot = false,
                specialDiceCharges = if (playerCaptain == CaptainId.VANE) 5 else 3
            )
        )

        val slotConfigs = listOf(lobby.botSlot2, lobby.botSlot3, lobby.botSlot4)
        slotConfigs.forEachIndexed { idx, selection ->
            if (selection != BotSlotSelection.OFF || players.size < 2) {
                val effectiveSelection = if (selection == BotSlotSelection.OFF) BotSlotSelection.AUTO_RANDOM else selection
                val diff = BotAiEngine.resolveDifficulty(effectiveSelection)
                val botCap = availableCaptains.removeFirstOrNull() ?: CaptainId.entries[(idx + 1) % CaptainId.entries.size]
                players.add(
                    PlayerState(
                        slotIndex = players.size,
                        uid = "bot_${idx + 1}",
                        name = "${botCap.displayName.substringBefore(" ")} (${diff.name.first()})",
                        captain = botCap,
                        isBot = true,
                        botDifficulty = diff,
                        specialDiceCharges = if (botCap == CaptainId.VANE) 5 else 3
                    )
                )
            }
        }

        botScheduleJob?.cancel()
        humanAutoEndJob?.cancel()
        pendingTurnExecution = null
        _diceRollAnimState.value = DiceRollAnimState()
        _stepMoveState.value = null
        recordedEndForMatch = false
        _isOnlineMatch.value = false
        _activeRoomCode.value = null
        _roomMessages.value = emptyList()
        _selectedDiceMode.value = DiceMode.NORMAL
        _gameState.value = GameSnapshot(
            players = players,
            logs = listOf("🏴‍☠️ ออกเรือโหมดเล่นคนเดียว! ผู้เล่น ${players.size} ลำ พร้อมชิงน่านน้ำ")
        )
    }

    fun createPrivateRoom() {
        createOnlineRoom(isPublic = false)
    }

    fun startAutoMatchmaking() {
        val captain = _lobbyState.value.selectedCaptain
        val name = userProfile.value?.displayName?.ifBlank { initialDisplayName } ?: initialDisplayName
        _lobbyState.update { it.copy(isBusy = true, errorMessage = null) }
        viewModelScope.launch {
            val result = repository.findOrCreatePublicRoom(name, captain)
            _lobbyState.update { it.copy(isBusy = false) }
            result.onSuccess { code ->
                attachToRoom(code)
            }.onFailure { err ->
                _lobbyState.update { it.copy(errorMessage = err.localizedMessage ?: "จับคู่ออนไลน์ไม่สำเร็จ") }
            }
        }
    }

    private fun createOnlineRoom(isPublic: Boolean) {
        val captain = _lobbyState.value.selectedCaptain
        val name = userProfile.value?.displayName?.ifBlank { initialDisplayName } ?: initialDisplayName
        _lobbyState.update { it.copy(isBusy = true, errorMessage = null) }
        viewModelScope.launch {
            val result = repository.createRoom(name, captain, isPublic = isPublic, maxPlayers = 4)
            _lobbyState.update { it.copy(isBusy = false) }
            result.onSuccess { code ->
                attachToRoom(code)
            }.onFailure { err ->
                _lobbyState.update { it.copy(errorMessage = err.localizedMessage ?: "สร้างห้องไม่สำเร็จ") }
            }
        }
    }

    fun joinRoomByInput() {
        val code = _lobbyState.value.roomCodeInput.trim().uppercase()
        if (code.length != 6) {
            _lobbyState.update { it.copy(errorMessage = "กรุณากรอกรหัสห้อง 6 หลัก") }
            return
        }
        val captain = _lobbyState.value.selectedCaptain
        val name = userProfile.value?.displayName?.ifBlank { initialDisplayName } ?: initialDisplayName
        _lobbyState.update { it.copy(isBusy = true, errorMessage = null) }
        viewModelScope.launch {
            val result = repository.joinRoomByCode(code, name, captain)
            _lobbyState.update { it.copy(isBusy = false) }
            result.onSuccess { room ->
                attachToRoom(room.roomCode)
            }.onFailure { err ->
                _lobbyState.update { it.copy(errorMessage = err.localizedMessage ?: "เข้าห้องไม่สำเร็จ") }
            }
        }
    }

    private fun attachToRoom(roomCode: String) {
        _lobbyState.update { it.copy(activeWaitingRoomCode = roomCode, errorMessage = null) }
        _activeRoomCode.value = roomCode
        recordedEndForMatch = false

        roomObserverJob?.cancel()
        roomObserverJob = viewModelScope.launch {
            repository.observeRoom(roomCode)
                .catch { err ->
                    _lobbyState.update { it.copy(errorMessage = err.localizedMessage ?: "การเชื่อมต่อห้องขัดข้อง") }
                }
                .collect { room ->
                    _waitingRoom.value = room
                    if (room == null) {
                        _lobbyState.update { it.copy(activeWaitingRoomCode = null) }
                        if (_isOnlineMatch.value) {
                            _gameState.value = null
                            _isOnlineMatch.value = false
                        }
                        return@collect
                    }

                    if (room.status == "playing" || room.status == "finished") {
                        _isOnlineMatch.value = true
                        val incoming = roomToGameSnapshot(room, _gameState.value)
                        if (pendingTurnExecution == null) {
                            _gameState.value = incoming
                            if (room.status == "finished" && !recordedEndForMatch) {
                                recordedEndForMatch = true
                                val me = incoming.players.firstOrNull { it.uid == currentUserId }
                                repository.recordMatchOutcome(
                                    won = room.winnerUid == currentUserId,
                                    finalGold = me?.gold ?: 0
                                )
                            }
                            maybeTriggerOnlineHostBotTurn(room, incoming)
                        }
                    }
                }
        }

        messageObserverJob?.cancel()
        messageObserverJob = viewModelScope.launch {
            var lastSeenMsgId: String? = null
            repository.observeMessages(roomCode, currentUserId)
                .catch { /* logged by repository */ }
                .collect { messages ->
                    _roomMessages.value = messages
                    val newest = messages.lastOrNull()
                    if (newest != null && newest.id != lastSeenMsgId) {
                        lastSeenMsgId = newest.id
                        val sticker = StickerEmotion.entries.firstOrNull { it.id == newest.stickerId }
                        val emoji = sticker?.emoji ?: "💬"
                        showStickerBubble(newest.senderId, emoji, newest.text)
                    }
                }
        }
    }

    fun startOnlineRoomMatch(fillWithBots: Boolean) {
        val room = _waitingRoom.value ?: return
        if (room.hostId != currentUserId) return

        val humanPlayers = room.players.mapIndexed { idx, map ->
            PlayerState.fromFirestoreMap(map, idx)
        }.toMutableList()

        if (fillWithBots) {
            val usedCaps = humanPlayers.map { it.captain }.toMutableSet()
            while (humanPlayers.size < 4) {
                val botCap = CaptainId.entries.firstOrNull { it !in usedCaps } ?: CaptainId.entries[humanPlayers.size % 4]
                usedCaps.add(botCap)
                val diff = BotAiEngine.resolveDifficulty(BotSlotSelection.AUTO_RANDOM)
                humanPlayers.add(
                    PlayerState(
                        slotIndex = humanPlayers.size,
                        uid = "bot_${humanPlayers.size}",
                        name = "${botCap.displayName.substringBefore(" ")} (${diff.name.first()})",
                        captain = botCap,
                        isBot = true,
                        botDifficulty = diff,
                        specialDiceCharges = if (botCap == CaptainId.VANE) 5 else 3
                    )
                )
            }
        }

        if (humanPlayers.size < 2) {
            _lobbyState.update { it.copy(errorMessage = "ต้องมีผู้เล่นอย่างน้อย 2 คน (หรือกดเติมบอท)") }
            return
        }

        viewModelScope.launch {
            repository.startMatch(
                roomCode = room.roomCode,
                finalPlayers = humanPlayers,
                initialLog = "⚓ ห้อง #${room.roomCode} ออกเรือแล้ว! ขอให้โชคดีทุกกัปตัน"
            )
        }
    }

    fun leaveWaitingRoom() {
        val code = _lobbyState.value.activeWaitingRoomCode ?: return
        roomObserverJob?.cancel()
        messageObserverJob?.cancel()
        _lobbyState.update { it.copy(activeWaitingRoomCode = null) }
        _waitingRoom.value = null
        _activeRoomCode.value = null
        viewModelScope.launch {
            repository.leaveWaitingRoom(code)
        }
    }

    fun selectDiceMode(mode: DiceMode) {
        _selectedDiceMode.value = mode
    }

    fun isLocalPlayersTurn(): Boolean {
        val snap = _gameState.value ?: return false
        if (snap.winnerUid.isNotBlank()) return false
        val active = snap.players.getOrNull(snap.currentTurnIndex) ?: return false
        return if (_isOnlineMatch.value) {
            active.uid == currentUserId && !active.isBot
        } else {
            !active.isBot
        }
    }

    fun rollDiceForCurrentPlayer() {
        val snap = _gameState.value ?: return
        if (!isLocalPlayersTurn() || snap.hasRolledThisTurn || pendingTurnExecution != null) return
        val modeToUse = _selectedDiceMode.value
        _selectedDiceMode.value = DiceMode.NORMAL
        humanAutoEndJob?.cancel()
        startDiceRollTurn(snap, modeToUse, isBot = false)
    }

    fun onDiceRollAnimationFinished(rollId: Long) {
        val exec = pendingTurnExecution ?: return
        if (exec.rollId != rollId) return
        _diceRollAnimState.update { it.copy(isRolling = false) }

        val prepared = exec.prepared
        if (prepared.stayedInJail || prepared.wentToJailOnTripleDoubles || prepared.stepsToWalk <= 0) {
            pendingTurnExecution = null
            _soundEvents.tryEmit(PirateSoundEffect.TILE_LANDING)
            val snap = prepared.snapshotBeforeSteps
            if (exec.isBot) {
                _gameState.value = snap
                if (snap.winnerUid.isNotBlank()) {
                    commitSnapshotUpdate(snap)
                } else {
                    botScheduleJob?.cancel()
                    botScheduleJob = viewModelScope.launch {
                        delay(700L)
                        commitSnapshotUpdate(GameEngine.advanceToNextTurn(snap))
                    }
                }
            } else {
                commitSnapshotUpdate(snap)
            }
        } else {
            val activePlayer = prepared.snapshotBeforeSteps.players[exec.playerIndex]
            _stepMoveState.value = StepMoveAnimState(
                moveId = rollId,
                playerUid = activePlayer.uid,
                startTile = activePlayer.position,
                totalSteps = prepared.stepsToWalk
            )
        }
    }

    fun onStepMovementFinished(moveId: Long) {
        val exec = pendingTurnExecution ?: return
        if (exec.rollId != moveId) return
        pendingTurnExecution = null
        _stepMoveState.value = null

        var walkedSnap = exec.prepared.snapshotBeforeSteps
        repeat(exec.prepared.stepsToWalk) {
            walkedSnap = GameEngine.stepPlayerOneTile(walkedSnap, exec.playerIndex)
        }

        val ownersBefore = walkedSnap.tileOwners
        val levelsBefore = walkedSnap.tileLevels
        val resolvedSnap = GameEngine.finalizeStepMovementAndResolveLanding(
            snapshot = walkedSnap,
            playerIndex = exec.playerIndex,
            outcome = exec.prepared.outcome,
            modeLabel = exec.prepared.modeLabel
        )
        if (resolvedSnap.tileOwners != ownersBefore || resolvedSnap.tileLevels != levelsBefore) {
            _soundEvents.tryEmit(PirateSoundEffect.PURCHASE_ISLAND)
        }

        if (exec.isBot) {
            _gameState.value = resolvedSnap
            if (resolvedSnap.winnerUid.isNotBlank()) {
                commitSnapshotUpdate(resolvedSnap)
            } else {
                botScheduleJob?.cancel()
                botScheduleJob = viewModelScope.launch {
                    delay(700L)
                    commitSnapshotUpdate(GameEngine.advanceToNextTurn(resolvedSnap))
                }
            }
        } else {
            commitSnapshotUpdate(resolvedSnap)
        }
    }

    fun payJailBail() {
        val snap = _gameState.value ?: return
        if (!isLocalPlayersTurn()) return
        val updated = GameEngine.resolveJailBail(snap)
        commitSnapshotUpdate(updated)
    }

    fun dismissJailBailPrompt() {
        val snap = _gameState.value ?: return
        if (!isLocalPlayersTurn()) return
        commitSnapshotUpdate(snap.copy(pendingAction = null))
    }

    fun buyIsland(tier: BuildingTier) {
        val snap = _gameState.value ?: return
        val pending = snap.pendingAction as? PendingBoardAction.BuyIsland ?: return
        val updated = GameEngine.buyIsland(snap, pending.tile.index, tier)
        if (updated.tileOwners != snap.tileOwners) {
            _soundEvents.tryEmit(PirateSoundEffect.PURCHASE_ISLAND)
        }
        finishActionAndMaybeAdvance(updated)
    }

    fun skipBuyOrUpgrade() {
        val snap = _gameState.value ?: return
        finishActionAndMaybeAdvance(snap.copy(pendingAction = null))
    }

    fun upgradeIsland(targetTier: BuildingTier) {
        val snap = _gameState.value ?: return
        val pending = snap.pendingAction as? PendingBoardAction.UpgradeIsland ?: return
        val updated = GameEngine.upgradeIsland(snap, pending.tile.index, targetTier)
        if (updated.tileLevels != snap.tileLevels) {
            _soundEvents.tryEmit(PirateSoundEffect.PURCHASE_ISLAND)
        }
        finishActionAndMaybeAdvance(updated)
    }

    fun useAngelCard() {
        val snap = _gameState.value ?: return
        val pending = snap.pendingAction as? PendingBoardAction.PayTollOrDefend ?: return
        val updated = GameEngine.useAngelCardForToll(snap, snap.currentTurnIndex, pending.tile)
        finishActionAndMaybeAdvance(updated)
    }

    fun payToll(wantTakeoverPrompt: Boolean) {
        val snap = _gameState.value ?: return
        val pending = snap.pendingAction as? PendingBoardAction.PayTollOrDefend ?: return
        val updated = GameEngine.confirmTollPayment(snap, pending, wantTakeoverPrompt)
        if (updated.pendingAction == null) {
            finishActionAndMaybeAdvance(updated)
        } else {
            commitSnapshotUpdate(updated)
        }
    }

    fun confirmTakeover() {
        val snap = _gameState.value ?: return
        val pending = snap.pendingAction as? PendingBoardAction.TakeoverOffer ?: return
        val updated = GameEngine.takeoverIsland(snap, pending.tile.index)
        if (updated.tileOwners != snap.tileOwners) {
            _soundEvents.tryEmit(PirateSoundEffect.PURCHASE_ISLAND)
        }
        finishActionAndMaybeAdvance(updated)
    }

    fun flipTavernCoin(guessHeads: Boolean) {
        val snap = _gameState.value ?: return
        val pending = snap.pendingAction as? PendingBoardAction.TavernMiniGame ?: return
        val updated = GameEngine.playTavernFlip(snap, pending, guessHeads)
        if (updated.pendingAction == null) {
            finishActionAndMaybeAdvance(updated)
        } else {
            commitSnapshotUpdate(updated)
        }
    }

    fun collectTavern() {
        val snap = _gameState.value ?: return
        val pending = snap.pendingAction as? PendingBoardAction.TavernMiniGame ?: return
        val updated = GameEngine.collectTavernPrize(snap, pending.currentPrize)
        finishActionAndMaybeAdvance(updated)
    }

    fun pickFestivalIsland(tileIndex: Int) {
        val snap = _gameState.value ?: return
        val updated = GameEngine.applyFestivalToTile(snap, tileIndex)
        finishActionAndMaybeAdvance(updated)
    }

    fun pickWhirlpoolTarget(tileIndex: Int) {
        val snap = _gameState.value ?: return
        val updated = GameEngine.warpPlayerToTile(snap, snap.currentTurnIndex, tileIndex)
        _soundEvents.tryEmit(PirateSoundEffect.TILE_LANDING)
        if (updated.pendingAction == null) {
            finishActionAndMaybeAdvance(updated)
        } else {
            commitSnapshotUpdate(updated)
        }
    }

    fun endTurnIfReady() {
        val snap = _gameState.value ?: return
        if (!isLocalPlayersTurn() || !snap.hasRolledThisTurn || snap.pendingAction != null) return
        val next = GameEngine.advanceToNextTurn(snap)
        commitSnapshotUpdate(next)
    }

    fun sendQuickChat(text: String, sticker: StickerEmotion?) {
        val snap = _gameState.value ?: return
        val me = snap.players.firstOrNull { it.uid == currentUserId } ?: snap.players.first()
        val emoji = sticker?.emoji ?: "💬"
        showStickerBubble(me.uid, emoji, text)

        val code = _activeRoomCode.value
        val room = _waitingRoom.value
        if (_isOnlineMatch.value && code != null && room != null) {
            viewModelScope.launch {
                repository.sendMessage(
                    roomCode = code,
                    senderName = me.name,
                    captain = me.captain,
                    text = text,
                    stickerId = sticker?.id ?: "",
                    memberUids = room.memberUids
                )
            }
        }
    }

    fun exitMatchToLobby() {
        botScheduleJob?.cancel()
        humanAutoEndJob?.cancel()
        roomObserverJob?.cancel()
        messageObserverJob?.cancel()
        pendingTurnExecution = null
        _diceRollAnimState.value = DiceRollAnimState()
        _stepMoveState.value = null
        _gameState.value = null
        _isOnlineMatch.value = false
        _activeRoomCode.value = null
        _waitingRoom.value = null
        _lobbyState.update { it.copy(activeWaitingRoomCode = null) }
    }

    private fun finishActionAndMaybeAdvance(updated: GameSnapshot) {
        if (updated.winnerUid.isNotBlank()) {
            commitSnapshotUpdate(updated)
            return
        }
        // Automatically advance turn after resolving action unless doubles gives another roll
        val next = GameEngine.advanceToNextTurn(updated)
        commitSnapshotUpdate(next)
    }

    private fun commitSnapshotUpdate(updated: GameSnapshot) {
        _gameState.value = updated

        if (updated.winnerUid.isNotBlank() && !recordedEndForMatch && !_isOnlineMatch.value) {
            recordedEndForMatch = true
            val me = updated.players.firstOrNull { it.uid == currentUserId }
            viewModelScope.launch {
                repository.recordMatchOutcome(
                    won = updated.winnerUid == currentUserId,
                    finalGold = me?.gold ?: 0
                )
            }
        }

        val code = _activeRoomCode.value
        if (_isOnlineMatch.value && code != null) {
            viewModelScope.launch {
                repository.commitTurnState(
                    roomCode = code,
                    players = updated.players,
                    tileOwners = updated.tileOwners,
                    tileLevels = updated.tileLevels,
                    tileFestivals = updated.tileFestivals,
                    currentTurnIndex = updated.currentTurnIndex,
                    turnNumber = updated.turnNumber,
                    lastDice1 = updated.lastDice1,
                    lastDice2 = updated.lastDice2,
                    lastLog = updated.logs.firstOrNull() ?: "Voyage continues",
                    winnerUid = updated.winnerUid
                )
            }
        } else {
            // Check if next turn is a Solo Bot turn
            if (updated.winnerUid.isBlank() && updated.pendingAction == null) {
                val active = updated.players.getOrNull(updated.currentTurnIndex)
                if (active != null && active.isBot && !updated.hasRolledThisTurn) {
                    scheduleSoloBotTurn()
                } else if (updated.hasRolledThisTurn && active != null && !active.isBot) {
                    // Human finished roll without pending modal -> advance after brief pause or let user tap/auto
                    scheduleHumanAutoEndIfNoPending(updated)
                }
            }
        }
    }

    private fun scheduleHumanAutoEndIfNoPending(snapshot: GameSnapshot) {
        if (snapshot.pendingAction != null || snapshot.winnerUid.isNotBlank()) return
        humanAutoEndJob?.cancel()
        humanAutoEndJob = viewModelScope.launch {
            delay(650)
            val cur = _gameState.value ?: return@launch
            if (cur === snapshot && cur.pendingAction == null && cur.hasRolledThisTurn) {
                val next = GameEngine.advanceToNextTurn(cur)
                commitSnapshotUpdate(next)
            }
        }
    }

    private fun scheduleSoloBotTurn() {
        if (pendingTurnExecution != null || botScheduleJob?.isActive == true) return
        botScheduleJob = viewModelScope.launch {
            delay(650L)
            val snap = _gameState.value ?: return@launch
            if (snap.winnerUid.isNotBlank() || pendingTurnExecution != null) return@launch
            val bot = snap.players.getOrNull(snap.currentTurnIndex) ?: return@launch
            if (!bot.isBot) return@launch

            val mode = BotAiEngine.chooseDiceMode(
                bot,
                snap.players,
                snap.tileOwners,
                snap.tileLevels,
                snap.tileFestivals
            )
            startDiceRollTurn(snap, mode, isBot = true)
        }
    }

    private fun maybeTriggerOnlineHostBotTurn(room: RoomDoc, snap: GameSnapshot) {
        if (room.hostId != currentUserId || snap.winnerUid.isNotBlank()) return
        val active = snap.players.getOrNull(snap.currentTurnIndex) ?: return
        if (!active.isBot || snap.hasRolledThisTurn || pendingTurnExecution != null || botScheduleJob?.isActive == true) return

        botScheduleJob = viewModelScope.launch {
            delay(700L)
            val current = _gameState.value ?: return@launch
            val currentActive = current.players.getOrNull(current.currentTurnIndex) ?: return@launch
            if (!currentActive.isBot || current.winnerUid.isNotBlank() || pendingTurnExecution != null) return@launch
            val mode = BotAiEngine.chooseDiceMode(
                currentActive,
                current.players,
                current.tileOwners,
                current.tileLevels,
                current.tileFestivals
            )
            startDiceRollTurn(current, mode, isBot = true)
        }
    }

    private fun startDiceRollTurn(
        initialSnap: GameSnapshot,
        requestedMode: DiceMode,
        isBot: Boolean
    ) {
        if (initialSnap.winnerUid.isNotBlank() || pendingTurnExecution != null) return
        val active = initialSnap.players.getOrNull(initialSnap.currentTurnIndex) ?: return
        if (active.bankrupt) {
            commitSnapshotUpdate(GameEngine.advanceToNextTurn(initialSnap))
            return
        }

        val prepared = GameEngine.prepareDiceOutcome(initialSnap, requestedMode)
        val rollId = ++rollSequenceCounter
        pendingTurnExecution = PendingTurnExecution(
            rollId = rollId,
            playerIndex = initialSnap.currentTurnIndex,
            prepared = prepared,
            isBot = isBot
        )
        _gameState.value = prepared.snapshotBeforeSteps
        _diceRollAnimState.value = DiceRollAnimState(
            isRolling = true,
            targetDie1 = prepared.outcome.die1,
            targetDie2 = prepared.outcome.die2,
            diceMode = prepared.outcome.usedMode,
            isDoubles = prepared.outcome.isDoubles,
            rollerName = active.name,
            rollId = rollId
        )
    }

    private fun showStickerBubble(uid: String, emoji: String, text: String) {
        val now = System.currentTimeMillis()
        _stickerPopups.update { current ->
            current + (uid to StickerPopup(uid, emoji, text, now))
        }
        viewModelScope.launch {
            delay(3600)
            _stickerPopups.update { current ->
                val item = current[uid]
                if (item != null && item.timestampMs == now) current - uid else current
            }
        }
    }

    private fun roomToGameSnapshot(room: RoomDoc, previous: GameSnapshot?): GameSnapshot {
        val players = room.players.mapIndexed { idx, map ->
            PlayerState.fromFirestoreMap(map, idx)
        }
        val prevLogs = previous?.logs.orEmpty()
        val updatedLogs = if (room.lastLog.isNotBlank() && prevLogs.firstOrNull() != room.lastLog) {
            (listOf(room.lastLog) + prevLogs).take(40)
        } else {
            prevLogs.ifEmpty { listOf(room.lastLog.ifBlank { "เริ่มเกมออนไลน์ #${room.roomCode}" }) }
        }
        return GameSnapshot(
            players = players,
            tileOwners = room.tileOwners.ifEmpty { List(BoardConfig.BOARD_SIZE) { "" } },
            tileLevels = if (room.tileLevels.size == BoardConfig.BOARD_SIZE) room.tileLevels.map { it.toInt() } else List(BoardConfig.BOARD_SIZE) { 0 },
            tileFestivals = if (room.tileFestivals.size == BoardConfig.BOARD_SIZE) room.tileFestivals.map { it.toInt() } else List(BoardConfig.BOARD_SIZE) { 1 },
            currentTurnIndex = room.currentTurnIndex.toInt().coerceIn(0, (players.size - 1).coerceAtLeast(0)),
            turnNumber = room.turnNumber.toInt(),
            lastDice1 = room.lastDice1.toInt().coerceIn(1, 6),
            lastDice2 = room.lastDice2.toInt().coerceIn(1, 6),
            hasRolledThisTurn = false,
            pendingAction = null,
            logs = updatedLogs,
            winnerUid = room.winnerUid
        )
    }

    private companion object {
        const val TAG = "PirateVM"
    }
}
