package com.example.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.BoardTile
import com.example.data.model.BuildingTier
import com.example.data.model.CaptainId
import com.example.data.model.PlayerState
import com.example.data.model.TileType
import com.example.game.BoardConfig
import com.example.game.GameSnapshot
import com.example.game.PendingBoardAction
import com.example.ui.PirateViewModel
import com.example.ui.theme.BioluminescentCyan
import com.example.ui.theme.BrassBorder
import com.example.ui.theme.CrimsonCorsair
import com.example.ui.theme.DeepOcean
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.InkDark
import com.example.ui.theme.LagoonTeal
import com.example.ui.theme.MahoganyWood
import com.example.ui.theme.TreasureGold

@Composable
fun BoardActionDialogs(
    snapshot: GameSnapshot,
    activePlayer: PlayerState,
    isLocalTurn: Boolean,
    inspectedTile: BoardTile?,
    onDismissInspect: () -> Unit,
    viewModel: PirateViewModel,
    onExitToLobby: () -> Unit
) {
    // 1. Victory Dialog
    if (snapshot.winnerUid.isNotBlank()) {
        val winner = snapshot.players.firstOrNull { it.uid == snapshot.winnerUid }
        AlertDialog(
            onDismissRequest = {},
            containerColor = MahoganyWood,
            title = {
                Text(
                    text = "👑 เจ้าแห่งน่านน้ำคนสุดท้าย!",
                    color = TreasureGold,
                    style = MaterialTheme.typography.headlineMedium
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "${winner?.name ?: "Captain"} (${winner?.captain?.displayName ?: ""}) คว้าชัยชนะ!",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "ทรัพย์สินคงเหลือ: ${winner?.gold ?: 0}G",
                        style = MaterialTheme.typography.bodyLarge,
                        color = LagoonTeal
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = onExitToLobby,
                    colors = ButtonDefaults.buttonColors(containerColor = TreasureGold, contentColor = InkDark),
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("victory_lobby_button")
                ) {
                    Text("กลับสู่ท่าเรือ (Lobby)", fontWeight = FontWeight.Bold)
                }
            }
        )
        return
    }

    // 2. Tile Inspector (when user taps any tile outside Whirlpool)
    if (inspectedTile != null && snapshot.pendingAction !is PendingBoardAction.PickWhirlpoolTarget) {
        val ownerUid = snapshot.tileOwners.getOrElse(inspectedTile.index) { "" }
        val owner = snapshot.players.firstOrNull { it.uid == ownerUid }
        val level = snapshot.tileLevels.getOrElse(inspectedTile.index) { 0 }
        val fest = snapshot.tileFestivals.getOrElse(inspectedTile.index) { 1 }
        AlertDialog(
            onDismissRequest = onDismissInspect,
            containerColor = MahoganyWood,
            title = { Text(inspectedTile.name, color = TreasureGold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(inspectedTile.subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (inspectedTile.type == TileType.ISLAND) {
                        Text("ราคาที่ดิน: ${inspectedTile.basePrice}G")
                        Text("เจ้าของ: ${owner?.name ?: "ยังไม่มีเจ้าของ"}")
                        Text("สิ่งปลูกสร้าง: ${BuildingTier.fromLevel(level).thaiName} (Lv.$level)")
                        if (level == 4) {
                            Text("🛡️ แลนด์มาร์กป้องกันเทคโอเวอร์ 100%", color = EmeraldSuccess)
                        }
                        if (fest > 1) {
                            Text("🎉 งานเทศกาลคูณค่าผ่านทาง x$fest", color = BioluminescentCyan)
                        }
                        if (owner != null) {
                            val toll = BoardConfig.calculateToll(inspectedTile, ownerUid, level, fest, snapshot.tileOwners)
                            Text("ค่าผ่านทางปัจจุบัน: ${toll}G", color = TreasureGold, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = onDismissInspect) { Text("ปิด") }
            }
        )
    }

    if (!isLocalTurn) return

    // 3. Pending Turn Action Dialogs
    when (val action = snapshot.pendingAction) {
        is PendingBoardAction.BuyIsland -> {
            val tile = action.tile
            AlertDialog(
                onDismissRequest = {},
                containerColor = MahoganyWood,
                title = { Text("🏝️ ซื้อเกาะ ${tile.name}", color = TreasureGold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "ทองของคุณ: ${activePlayer.gold}G (ราคาที่ดิน ${tile.basePrice}G)" +
                                if (activePlayer.captain == CaptainId.BARNABY) "\n🔧 สกิล Barnaby: ลดค่าก่อสร้าง -20%" else "",
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        listOf(BuildingTier.NONE, BuildingTier.CAMP, BuildingTier.OUTPOST, BuildingTier.CASTLE).forEach { tier ->
                            val buildCost = if (tier == BuildingTier.NONE) 0 else BoardConfig.calculateBuildCost(tile, tier, activePlayer.captain)
                            val total = tile.basePrice + buildCost
                            val affordable = activePlayer.gold >= total
                            Button(
                                onClick = { viewModel.buyIsland(tier) },
                                enabled = affordable,
                                colors = ButtonDefaults.buttonColors(containerColor = DeepOcean, contentColor = TreasureGold),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .minimumInteractiveComponentSize()
                                    .testTag("buy_tier_${tier.level}")
                            ) {
                                Text(
                                    text = if (tier == BuildingTier.NONE) {
                                        "ซื้อเฉพาะที่ดิน (${total}G)"
                                    } else {
                                        "ซื้อ + ${tier.thaiName} (${total}G)"
                                    }
                                )
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    OutlinedButton(
                        onClick = { viewModel.skipBuyOrUpgrade() },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) { Text("ไม่ซื้อ") }
                }
            )
        }

        is PendingBoardAction.UpgradeIsland -> {
            val tile = action.tile
            val nextLevel = (action.currentLevel + 1).coerceAtMost(4)
            val nextTier = BuildingTier.fromLevel(nextLevel)
            val cost = BoardConfig.calculateBuildCost(tile, nextTier, activePlayer.captain)
            AlertDialog(
                onDismissRequest = {},
                containerColor = MahoganyWood,
                title = { Text("🔨 อัปเกรด ${tile.name}", color = TreasureGold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("ระดับปัจจุบัน: ${BuildingTier.fromLevel(action.currentLevel).thaiName}")
                        Text("อัปเกรดเป็น: ${nextTier.thaiName} (Lv.$nextLevel) ราคา ${cost}G")
                        if (nextTier == BuildingTier.LANDMARK) {
                            Text("👑 ระดับ 4 แลนด์มาร์ก: ป้องกันการถูกเทคโอเวอร์ 100%!", color = EmeraldSuccess)
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.upgradeIsland(nextTier) },
                        enabled = activePlayer.gold >= cost,
                        colors = ButtonDefaults.buttonColors(containerColor = TreasureGold, contentColor = InkDark),
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("confirm_upgrade_button")
                    ) {
                        Text("อัปเกรด (${cost}G)", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { viewModel.skipBuyOrUpgrade() }) { Text("ข้าม") }
                }
            )
        }

        is PendingBoardAction.PayTollOrDefend -> {
            AlertDialog(
                onDismissRequest = {},
                containerColor = MahoganyWood,
                title = { Text("⚠️ ตกเกาะศัตรู: ${action.tile.name}", color = CrimsonCorsair) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("เจ้าของ: ${action.owner.name}")
                        Text(
                            text = "ค่าผ่านทาง: ${action.tollAmount}G",
                            style = MaterialTheme.typography.titleLarge,
                            color = TreasureGold
                        )
                        if (action.canUseAngelCard) {
                            Text("👼 คุณมีการ์ดนางฟ้าพิทักษ์ ${activePlayer.angelCards} ใบ (ยกเว้นค่าผ่านทางเป็น 0G)", color = BioluminescentCyan)
                        }
                    }
                },
                confirmButton = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (action.canUseAngelCard) {
                            Button(
                                onClick = { viewModel.useAngelCard() },
                                colors = ButtonDefaults.buttonColors(containerColor = BioluminescentCyan, contentColor = InkDark),
                                modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("use_angel_card_button")
                            ) {
                                Text("🛡️ ใช้การ์ดนางฟ้า (จ่าย 0G)", fontWeight = FontWeight.Bold)
                            }
                        }
                        Button(
                            onClick = { viewModel.payToll(wantTakeoverPrompt = action.canTakeoverAfter) },
                            colors = ButtonDefaults.buttonColors(containerColor = TreasureGold, contentColor = InkDark),
                            modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("pay_toll_button")
                        ) {
                            Text(
                                text = if (action.canTakeoverAfter) "จ่ายค่าผ่านทาง & ดูราคาซื้อต่อ" else "จ่ายค่าผ่านทาง ${action.tollAmount}G",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            )
        }

        is PendingBoardAction.TakeoverOffer -> {
            AlertDialog(
                onDismissRequest = {},
                containerColor = MahoganyWood,
                title = { Text("⚔️ เทคโอเวอร์เกาะ ${action.tile.name}?", color = TreasureGold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("เจ้าของเดิม: ${action.currentOwner.name}")
                        Text("ราคาซื้อต่อ: ${action.takeoverCost}G (ทองของคุณ ${activePlayer.gold}G)")
                        if (activePlayer.captain == CaptainId.IGNIS) {
                            Text("🔥 สกิล Captain Ignis: ลดค่าเทคโอเวอร์แล้ว 20%!", color = CrimsonCorsair)
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.confirmTakeover() },
                        enabled = activePlayer.gold >= action.takeoverCost,
                        colors = ButtonDefaults.buttonColors(containerColor = CrimsonCorsair, contentColor = Color.White),
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("confirm_takeover_button")
                    ) {
                        Text("ยึดเกาะ (${action.takeoverCost}G)")
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { viewModel.skipBuyOrUpgrade() }) { Text("ไม่ซื้อต่อ") }
                }
            )
        }

        is PendingBoardAction.TavernMiniGame -> {
            AlertDialog(
                onDismissRequest = {},
                containerColor = MahoganyWood,
                title = { Text("🍺 โรงเตี๊ยมวัดใจ (รอบ ${action.round}/3)", color = TreasureGold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("ทายเหรียญหัว-ก้อย เพื่อทวีคูณรางวัล (สูงสุด x8)")
                        Text(
                            text = "รางวัลปัจจุบัน: ${action.currentPrize}G",
                            style = MaterialTheme.typography.titleLarge,
                            color = EmeraldSuccess
                        )
                        if (action.lastFlipResult != null) {
                            Text(action.lastFlipResult, color = BioluminescentCyan)
                        }
                    }
                },
                confirmButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.flipTavernCoin(true) },
                            colors = ButtonDefaults.buttonColors(containerColor = TreasureGold, contentColor = InkDark),
                            modifier = Modifier.minimumInteractiveComponentSize().testTag("tavern_heads_button")
                        ) { Text("ทายหัว") }
                        Button(
                            onClick = { viewModel.flipTavernCoin(false) },
                            colors = ButtonDefaults.buttonColors(containerColor = LagoonTeal, contentColor = InkDark),
                            modifier = Modifier.minimumInteractiveComponentSize().testTag("tavern_tails_button")
                        ) { Text("ทายก้อย") }
                    }
                },
                dismissButton = {
                    if (action.round > 1) {
                        OutlinedButton(onClick = { viewModel.collectTavern() }) {
                            Text("รับ ${action.currentPrize}G เลย")
                        }
                    }
                }
            )
        }

        is PendingBoardAction.PickFestivalIsland -> {
            AlertDialog(
                onDismissRequest = {},
                containerColor = MahoganyWood,
                title = { Text("🎉 เลือกเกาะจัดเทศกาล (x2)", color = TreasureGold) },
                text = {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(action.ownedTileIndices) { tileIdx ->
                            val tile = BoardConfig.tiles[tileIdx]
                            val mult = snapshot.tileFestivals[tileIdx]
                            Card(
                                colors = CardDefaults.cardColors(containerColor = DeepOcean),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, BrassBorder, RoundedCornerShape(10.dp))
                                    .clickable { viewModel.pickFestivalIsland(tileIdx) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(tile.name, color = TreasureGold, fontWeight = FontWeight.Bold)
                                    Text("x$mult → x${(mult * 2).coerceAtMost(4)}", color = BioluminescentCyan)
                                }
                            }
                        }
                    }
                },
                confirmButton = {}
            )
        }

        is PendingBoardAction.JailDecision -> {
            AlertDialog(
                onDismissRequest = {},
                containerColor = MahoganyWood,
                title = { Text("🏝️ ติดเกาะกะโหลก", color = CrimsonCorsair) },
                text = {
                    Text("คุณติดเกาะกะโหลกอยู่ (รอบ ${activePlayer.jailTurns + 1}/3) ต้องการจ่ายค่าไถ่เรือ ${action.bailCost}G เพื่อออกทันที หรือเสี่ยงทอยเต๋าเบิ้ล?")
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.payJailBail() },
                        colors = ButtonDefaults.buttonColors(containerColor = TreasureGold, contentColor = InkDark),
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) { Text("จ่ายค่าไถ่ (${action.bailCost}G)") }
                },
                dismissButton = {
                    OutlinedButton(onClick = { viewModel.dismissJailBailPrompt() }) {
                        Text("ทอยเต๋าหาเบิ้ล")
                    }
                }
            )
        }

        PendingBoardAction.PickWhirlpoolTarget, null -> Unit
    }
}
