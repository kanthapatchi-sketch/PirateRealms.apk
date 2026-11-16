package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Sailing
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.data.model.BotSlotSelection
import com.example.data.model.CaptainId
import com.example.data.model.PlayerState
import com.example.data.model.RoomDoc
import com.example.data.model.UserProfileDoc
import com.example.ui.LobbyUiState
import com.example.ui.PirateViewModel
import com.example.ui.theme.AbyssalNavy
import com.example.ui.theme.BrassBorder
import com.example.ui.theme.DeepOcean
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.InkDark
import com.example.ui.theme.LagoonTeal
import com.example.ui.theme.MahoganyWood
import com.example.ui.theme.TreasureGold
import com.example.ui.theme.WarmTeak

@Composable
fun LobbyScreen(
    viewModel: PirateViewModel,
    lobbyState: LobbyUiState,
    profile: UserProfileDoc?,
    waitingRoom: RoomDoc?,
    currentUserId: String,
    fallbackName: String
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    var selectedTab by remember { mutableIntStateOf(if (waitingRoom != null) 1 else 0) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(AbyssalNavy, DeepOcean, Color(0xFF07111A))
                )
            )
            .safeDrawingPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top Captain Header
            Card(
                colors = CardDefaults.cardColors(containerColor = MahoganyWood),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.5.dp, BrassBorder, RoundedCornerShape(18.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Image(
                        painter = painterResource(id = lobbyState.selectedCaptain.avatarRes),
                        contentDescription = lobbyState.selectedCaptain.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .border(2.dp, TreasureGold, CircleShape)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = profile?.displayName?.ifBlank { fallbackName } ?: fallbackName,
                            style = MaterialTheme.typography.titleLarge,
                            color = TreasureGold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.EmojiEvents,
                                    contentDescription = "Wins",
                                    tint = TreasureGold,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "ชนะ ${profile?.wins ?: 0}/${profile?.gamesPlayed ?: 0}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = "ทองสูงสุด ${profile?.highestGold ?: 2000}G",
                                style = MaterialTheme.typography.bodyMedium,
                                color = LagoonTeal
                            )
                        }
                    }
                    IconButton(
                        onClick = { signOutUser(credentialManager, scope) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("sign_out_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = "Sign out",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Title Banner
            Column {
                Text(
                    text = "เลือกกัปตันเรือโจรสลัด (4 Captains)",
                    style = MaterialTheme.typography.titleMedium,
                    color = TreasureGold
                )
                Text(
                    text = "สกิลเฉพาะตัวของกัปตันส่งผลตลอดทั้งเกม",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 4 Playable Chibi Captains Selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CaptainId.entries.forEach { captain ->
                    val selected = lobbyState.selectedCaptain == captain
                    val accent = Color(captain.accentHex)
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (selected) WarmTeak else MahoganyWood.copy(alpha = 0.8f)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .width(164.dp)
                            .border(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) TreasureGold else BrassBorder.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable { viewModel.selectCaptain(captain) }
                            .testTag("captain_card_${captain.id}")
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Image(
                                painter = painterResource(id = captain.avatarRes),
                                contentDescription = captain.displayName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .border(2.5.dp, accent, CircleShape)
                            )
                            Text(
                                text = captain.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                color = if (selected) TreasureGold else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                            Text(
                                text = captain.thaiTitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = accent
                            )
                            Text(
                                text = captain.skillName,
                                style = MaterialTheme.typography.labelLarge,
                                color = LagoonTeal,
                                maxLines = 1
                            )
                            Text(
                                text = captain.skillDescription,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                minLines = 3,
                                maxLines = 3
                            )
                        }
                    }
                }
            }

            // Game Mode Selector Tabs
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MahoganyWood,
                contentColor = TreasureGold,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, BrassBorder.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("เล่นคนเดียว vs บอท") },
                    icon = { Icon(Icons.Default.SmartToy, contentDescription = null) },
                    modifier = Modifier.testTag("tab_solo")
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("ออนไลน์กับเพื่อน") },
                    icon = { Icon(Icons.Default.Public, contentDescription = null) },
                    modifier = Modifier.testTag("tab_online")
                )
            }

            if (lobbyState.errorMessage != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.16f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = lobbyState.errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            if (selectedTab == 0) {
                SoloModePanel(
                    lobbyState = lobbyState,
                    onSelectSlot = viewModel::updateBotSlot,
                    onStartSolo = viewModel::startSoloGame
                )
            } else {
                OnlineModePanel(
                    lobbyState = lobbyState,
                    waitingRoom = waitingRoom,
                    currentUserId = currentUserId,
                    onCodeChange = viewModel::updateRoomCodeInput,
                    onCreatePrivate = viewModel::createPrivateRoom,
                    onAutoMatch = viewModel::startAutoMatchmaking,
                    onJoinByCode = viewModel::joinRoomByInput,
                    onCopyCode = { code -> clipboard.setText(AnnotatedString(code)) },
                    onShareInvite = { code ->
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "มาเดินเรือชิงเกาะใน Pirate Realms: Fortune Seas กัน! รหัสห้อง: $code (piraterealms://join/$code)"
                            )
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "เชิญเพื่อนเข้าห้อง"))
                    },
                    onStartMatch = { fillBots -> viewModel.startOnlineRoomMatch(fillBots) },
                    onLeaveRoom = viewModel::leaveWaitingRoom
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SoloModePanel(
    lobbyState: LobbyUiState,
    onSelectSlot: (Int, BotSlotSelection) -> Unit,
    onStartSolo: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MahoganyWood),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.5.dp, BrassBorder, RoundedCornerShape(18.dp))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "ตั้งค่าระดับ AI Bot (สูงสุด 4 ผู้เล่น)",
                style = MaterialTheme.typography.titleMedium,
                color = TreasureGold
            )
            Text(
                text = "อัตราสุ่มออโต้: ง่าย 25% (~20.8%) • ปานกลาง 40% (~33.3%) • ยาก 55% (~45.8%)",
                style = MaterialTheme.typography.labelSmall,
                color = LagoonTeal
            )

            BotSlotRow(
                slotLabel = "ช่องที่ 2 (คู่แข่งหลัก)",
                selected = lobbyState.botSlot2,
                allowOff = false,
                onSelect = { onSelectSlot(2, it) }
            )
            BotSlotRow(
                slotLabel = "ช่องที่ 3",
                selected = lobbyState.botSlot3,
                allowOff = true,
                onSelect = { onSelectSlot(3, it) }
            )
            BotSlotRow(
                slotLabel = "ช่องที่ 4",
                selected = lobbyState.botSlot4,
                allowOff = true,
                onSelect = { onSelectSlot(4, it) }
            )

            Button(
                onClick = onStartSolo,
                colors = ButtonDefaults.buttonColors(containerColor = TreasureGold, contentColor = InkDark),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .minimumInteractiveComponentSize()
                    .testTag("start_solo_button")
            ) {
                Icon(Icons.Default.Sailing, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text(
                    text = "ออกเรือเล่นคนเดียว (Start Solo)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun BotSlotRow(
    slotLabel: String,
    selected: BotSlotSelection,
    allowOff: Boolean,
    onSelect: (BotSlotSelection) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = slotLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            BotSlotSelection.entries
                .filter { allowOff || it != BotSlotSelection.OFF }
                .forEach { option ->
                    FilterChip(
                        selected = selected == option,
                        onClick = { onSelect(option) },
                        label = { Text(option.label, style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = TreasureGold,
                            selectedLabelColor = InkDark
                        )
                    )
                }
        }
    }
}

@Composable
private fun OnlineModePanel(
    lobbyState: LobbyUiState,
    waitingRoom: RoomDoc?,
    currentUserId: String,
    onCodeChange: (String) -> Unit,
    onCreatePrivate: () -> Unit,
    onAutoMatch: () -> Unit,
    onJoinByCode: () -> Unit,
    onCopyCode: (String) -> Unit,
    onShareInvite: (String) -> Unit,
    onStartMatch: (Boolean) -> Unit,
    onLeaveRoom: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MahoganyWood),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.5.dp, BrassBorder, RoundedCornerShape(18.dp))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (waitingRoom == null) {
                Text(
                    text = "ห้องเดินเรือออนไลน์ (2-4 กัปตัน)",
                    style = MaterialTheme.typography.titleMedium,
                    color = TreasureGold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onAutoMatch,
                        enabled = !lobbyState.isBusy,
                        colors = ButtonDefaults.buttonColors(containerColor = LagoonTeal, contentColor = AbyssalNavy),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("auto_match_button")
                    ) {
                        if (lobbyState.isBusy) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = AbyssalNavy, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Public, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("สุ่มจับคู่", fontWeight = FontWeight.Bold)
                        }
                    }

                    Button(
                        onClick = onCreatePrivate,
                        enabled = !lobbyState.isBusy,
                        colors = ButtonDefaults.buttonColors(containerColor = TreasureGold, contentColor = InkDark),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("create_private_room_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("สร้างห้องส่วนตัว", fontWeight = FontWeight.Bold)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = lobbyState.roomCodeInput,
                        onValueChange = onCodeChange,
                        label = { Text("รหัสห้อง 6 หลัก (เช่น AR7K2P)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TreasureGold,
                            unfocusedBorderColor = BrassBorder,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("room_code_input")
                    )
                    Button(
                        onClick = onJoinByCode,
                        enabled = !lobbyState.isBusy,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = WarmTeak, contentColor = TreasureGold),
                        modifier = Modifier
                            .height(54.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("join_room_button")
                    ) {
                        Text("เข้าร่วม", fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                // Active Waiting Room View
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = if (waitingRoom.isPublic) "ห้องจับคู่สาธารณะ" else "ห้องส่วนตัว (Private Room)",
                            style = MaterialTheme.typography.labelLarge,
                            color = LagoonTeal
                        )
                        Text(
                            text = "รหัสห้อง: ${waitingRoom.roomCode}",
                            style = MaterialTheme.typography.headlineMedium,
                            color = TreasureGold
                        )
                    }
                    Row {
                        IconButton(
                            onClick = { onCopyCode(waitingRoom.roomCode) },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy room code", tint = TreasureGold)
                        }
                        IconButton(
                            onClick = { onShareInvite(waitingRoom.roomCode) },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Share invite link", tint = LagoonTeal)
                        }
                    }
                }

                val players = waitingRoom.players.mapIndexed { idx, map ->
                    PlayerState.fromFirestoreMap(map, idx)
                }
                Text(
                    text = "กัปตันในห้อง (${players.size}/${waitingRoom.maxPlayers})",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                players.forEach { p ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DeepOcean.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Image(
                            painter = painterResource(id = p.captain.avatarRes),
                            contentDescription = p.captain.displayName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(p.name, style = MaterialTheme.typography.titleMedium, color = TreasureGold)
                            Text(p.captain.displayName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (p.uid == waitingRoom.hostId) {
                            Text("HOST", style = MaterialTheme.typography.labelSmall, color = EmeraldSuccess, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                val isHost = waitingRoom.hostId == currentUserId
                if (isHost) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (players.size >= 2) {
                            Button(
                                onClick = { onStartMatch(false) },
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess, contentColor = InkDark),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .minimumInteractiveComponentSize()
                            ) {
                                Text("เริ่มเกม (${players.size} คน)", fontWeight = FontWeight.Bold)
                            }
                        }
                        Button(
                            onClick = { onStartMatch(true) },
                            colors = ButtonDefaults.buttonColors(containerColor = TreasureGold, contentColor = InkDark),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .minimumInteractiveComponentSize()
                                .testTag("start_online_with_bots_button")
                        ) {
                            Icon(Icons.Default.Group, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("เติมบอท & เริ่มเกม", fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    Text(
                        text = "รอหัวหน้าห้องกดเริ่มการเดินเรือ...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = LagoonTeal
                    )
                }

                OutlinedButton(
                    onClick = onLeaveRoom,
                    modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize()
                ) {
                    Text("ออกจากห้อง", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
