package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.audio.PirateSoundPlayer
import com.example.data.model.BoardTile
import com.example.data.model.DiceMode
import com.example.data.model.PlayerState
import com.example.data.model.StickerEmotion
import com.example.game.GameSnapshot
import com.example.game.PendingBoardAction
import com.example.ui.PirateViewModel
import com.example.ui.StickerPopup
import com.example.ui.components.BoardActionDialogs
import com.example.ui.components.DiceRollAnimState
import com.example.ui.components.PirateBoardView
import com.example.ui.components.StepMoveAnimState
import com.example.ui.theme.AbyssalNavy
import com.example.ui.theme.BioluminescentCyan
import com.example.ui.theme.BrassBorder
import com.example.ui.theme.CrimsonCorsair
import com.example.ui.theme.DeepOcean
import com.example.ui.theme.InkDark
import com.example.ui.theme.LagoonTeal
import com.example.ui.theme.MahoganyWood
import com.example.ui.theme.TreasureGold

@Composable
fun GameBoardScreen(
    viewModel: PirateViewModel,
    snapshot: GameSnapshot,
    selectedDiceMode: DiceMode,
    diceRollAnimState: DiceRollAnimState,
    stepMoveState: StepMoveAnimState?,
    isOnlineMatch: Boolean,
    activeRoomCode: String?,
    stickerPopups: Map<String, StickerPopup>,
    onExitToLobby: () -> Unit
) {
    var inspectedTile by remember { mutableStateOf<BoardTile?>(null) }
    var showLeaveConfirm by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }

    val soundPlayer = remember { PirateSoundPlayer() }
    DisposableEffect(soundPlayer) {
        onDispose { soundPlayer.release() }
    }
    LaunchedEffect(isMuted) {
        soundPlayer.isMuted = isMuted
    }
    LaunchedEffect(viewModel, soundPlayer) {
        viewModel.soundEvents.collect { effect ->
            soundPlayer.play(effect)
        }
    }

    BackHandler {
        showLeaveConfirm = true
    }

    if (showLeaveConfirm) {
        AlertDialog(
            onDismissRequest = { showLeaveConfirm = false },
            containerColor = MahoganyWood,
            title = { Text("ออกจากการเดินเรือ?", color = TreasureGold) },
            text = { Text("คุณต้องการกลับไปยังหน้าล็อบบี้หรือไม่?") },
            confirmButton = {
                Button(
                    onClick = {
                        showLeaveConfirm = false
                        onExitToLobby()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonCorsair, contentColor = Color.White)
                ) { Text("กลับล็อบบี้") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showLeaveConfirm = false }) { Text("เล่นต่อ") }
            }
        )
    }

    val isLocalTurn = viewModel.isLocalPlayersTurn()
    val activePlayer = snapshot.players.getOrNull(snapshot.currentTurnIndex) ?: snapshot.players.first()

    BoardActionDialogs(
        snapshot = snapshot,
        activePlayer = activePlayer,
        isLocalTurn = isLocalTurn,
        inspectedTile = inspectedTile,
        onDismissInspect = { inspectedTile = null },
        viewModel = viewModel,
        onExitToLobby = onExitToLobby
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(AbyssalNavy, DeepOcean)))
            .safeDrawingPadding()
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            val isWide = maxWidth >= 720.dp

            if (isWide) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(1.1f).fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        TopGameBar(
                            isOnlineMatch = isOnlineMatch,
                            roomCode = activeRoomCode,
                            isMuted = isMuted,
                            onToggleMute = { isMuted = !isMuted },
                            onBackClick = { showLeaveConfirm = true }
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        PirateBoardView(
                            snapshot = snapshot,
                            selectedDiceMode = selectedDiceMode,
                            isLocalTurn = isLocalTurn,
                            diceRollAnimState = diceRollAnimState,
                            stepMoveState = stepMoveState,
                            onDiceSpinTick = soundPlayer::playDiceRollTick,
                            onDiceSettle = soundPlayer::playDiceSettle,
                            onDiceRollComplete = viewModel::onDiceRollAnimationFinished,
                            onTileStep = soundPlayer::playTileStep,
                            onTileLanding = soundPlayer::playTileLanding,
                            onStepMovementComplete = viewModel::onStepMovementFinished,
                            onSelectDiceMode = viewModel::selectDiceMode,
                            onRollDice = viewModel::rollDiceForCurrentPlayer,
                            onTileClick = { tile ->
                                if (snapshot.pendingAction is PendingBoardAction.PickWhirlpoolTarget && isLocalTurn) {
                                    viewModel.pickWhirlpoolTarget(tile.index)
                                } else {
                                    inspectedTile = tile
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Column(
                        modifier = Modifier.weight(0.9f).fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PlayerStatusStrip(
                            players = snapshot.players,
                            activeIndex = snapshot.currentTurnIndex,
                            stickerPopups = stickerPopups
                        )
                        SailingLogPanel(
                            logs = snapshot.logs,
                            modifier = Modifier.weight(1f)
                        )
                        QuickChatStickerBar(
                            onSendSticker = { sticker -> viewModel.sendQuickChat(sticker.label, sticker) }
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TopGameBar(
                        isOnlineMatch = isOnlineMatch,
                        roomCode = activeRoomCode,
                        isMuted = isMuted,
                        onToggleMute = { isMuted = !isMuted },
                        onBackClick = { showLeaveConfirm = true }
                    )

                    PlayerStatusStrip(
                        players = snapshot.players,
                        activeIndex = snapshot.currentTurnIndex,
                        stickerPopups = stickerPopups
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        PirateBoardView(
                            snapshot = snapshot,
                            selectedDiceMode = selectedDiceMode,
                            isLocalTurn = isLocalTurn,
                            diceRollAnimState = diceRollAnimState,
                            stepMoveState = stepMoveState,
                            onDiceSpinTick = soundPlayer::playDiceRollTick,
                            onDiceSettle = soundPlayer::playDiceSettle,
                            onDiceRollComplete = viewModel::onDiceRollAnimationFinished,
                            onTileStep = soundPlayer::playTileStep,
                            onTileLanding = soundPlayer::playTileLanding,
                            onStepMovementComplete = viewModel::onStepMovementFinished,
                            onSelectDiceMode = viewModel::selectDiceMode,
                            onRollDice = viewModel::rollDiceForCurrentPlayer,
                            onTileClick = { tile ->
                                if (snapshot.pendingAction is PendingBoardAction.PickWhirlpoolTarget && isLocalTurn) {
                                    viewModel.pickWhirlpoolTarget(tile.index)
                                } else {
                                    inspectedTile = tile
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    SailingLogPanel(
                        logs = snapshot.logs,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(74.dp)
                    )

                    QuickChatStickerBar(
                        onSendSticker = { sticker -> viewModel.sendQuickChat(sticker.label, sticker) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TopGameBar(
    isOnlineMatch: Boolean,
    roomCode: String?,
    isMuted: Boolean,
    onToggleMute: () -> Unit,
    onBackClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier.minimumInteractiveComponentSize().testTag("back_to_lobby_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to lobby",
                    tint = TreasureGold
                )
            }
            Text(
                text = "PIRATE REALMS",
                style = MaterialTheme.typography.titleMedium,
                color = TreasureGold
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(
                onClick = onToggleMute,
                modifier = Modifier.minimumInteractiveComponentSize().testTag("sound_toggle_button")
            ) {
                Icon(
                    imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = if (isMuted) "Unmute sound effects" else "Mute sound effects",
                    tint = if (isMuted) BrassBorder else TreasureGold
                )
            }
            Text(
                text = if (isOnlineMatch && roomCode != null) "ONLINE #$roomCode" else "SOLO vs BOTS",
                style = MaterialTheme.typography.labelLarge,
                color = LagoonTeal,
                modifier = Modifier
                    .background(MahoganyWood, RoundedCornerShape(8.dp))
                    .border(1.dp, BrassBorder, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun PlayerStatusStrip(
    players: List<PlayerState>,
    activeIndex: Int,
    stickerPopups: Map<String, StickerPopup>
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        players.forEachIndexed { idx, player ->
            val isActive = idx == activeIndex && !player.bankrupt
            val accent = Color(player.captain.accentHex)
            val popup = stickerPopups[player.uid]

            Box(modifier = Modifier.weight(1f)) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = when {
                            player.bankrupt -> Color(0xFF1A1A1A)
                            isActive -> MahoganyWood
                            else -> DeepOcean.copy(alpha = 0.85f)
                        }
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = if (isActive) 2.dp else 1.dp,
                            color = if (isActive) TreasureGold else accent.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(12.dp)
                        )
                ) {
                    Column(
                        modifier = Modifier.padding(6.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Image(
                                painter = painterResource(id = player.captain.avatarRes),
                                contentDescription = player.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .border(1.5.dp, accent, CircleShape)
                            )
                            Text(
                                text = player.name,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (player.bankrupt) Color.Gray else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (player.bankrupt) "ล้มละลาย" else "${player.gold}G",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (player.bankrupt) CrimsonCorsair else TreasureGold
                            )
                            if (!player.bankrupt && player.angelCards > 0) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Shield,
                                        contentDescription = "Angel card",
                                        tint = BioluminescentCyan,
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Text(
                                        text = "${player.angelCards}",
                                        fontSize = 9.sp,
                                        color = BioluminescentCyan
                                    )
                                }
                            }
                        }
                    }
                }

                // Real-time floating Chibi Sticker reaction above profile card
                this@Row.AnimatedVisibility(
                    visible = popup != null,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = (-14).dp)
                        .zIndex(10f)
                ) {
                    if (popup != null) {
                        Surface(
                            color = TreasureGold,
                            shape = RoundedCornerShape(10.dp),
                            shadowElevation = 6.dp
                        ) {
                            Text(
                                text = "${popup.emoji} ${popup.text}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = InkDark,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SailingLogPanel(
    logs: List<String>,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MahoganyWood.copy(alpha = 0.9f)),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.border(1.dp, BrassBorder.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(logs) { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun QuickChatStickerBar(
    onSendSticker: (StickerEmotion) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StickerEmotion.entries.forEach { sticker ->
            Surface(
                color = MahoganyWood,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .border(1.dp, BrassBorder.copy(alpha = 0.7f), RoundedCornerShape(20.dp))
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { onSendSticker(sticker) }
                    .testTag("sticker_${sticker.id}")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(sticker.emoji, fontSize = 14.sp)
                    Text(
                        text = sticker.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = TreasureGold
                    )
                }
            }
        }
    }
}
