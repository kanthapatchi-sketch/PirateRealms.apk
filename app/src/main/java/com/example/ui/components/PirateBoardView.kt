package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Anchor
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Cyclone
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalBar
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BoardTile
import com.example.data.model.DiceMode
import com.example.data.model.TileType
import com.example.game.BoardConfig
import com.example.game.GameSnapshot
import com.example.game.PendingBoardAction
import kotlinx.coroutines.delay
import com.example.ui.theme.AbyssalNavy
import com.example.ui.theme.AntiqueParchment
import com.example.ui.theme.BioluminescentCyan
import com.example.ui.theme.BrassBorder
import com.example.ui.theme.BurnishedGold
import com.example.ui.theme.CrimsonCorsair
import com.example.ui.theme.DeepOcean
import com.example.ui.theme.InkDark
import com.example.ui.theme.LagoonTeal
import com.example.ui.theme.MahoganyWood
import com.example.ui.theme.PhantomViolet
import com.example.ui.theme.TreasureGold

data class StepMoveAnimState(
    val moveId: Long,
    val playerUid: String,
    val startTile: Int,
    val totalSteps: Int
)

private const val STEP_TILE_DELAY_MS = 380L
private const val STEP_LAND_PAUSE_MS = 220L

@Composable
fun PirateBoardView(
    snapshot: GameSnapshot,
    selectedDiceMode: DiceMode,
    isLocalTurn: Boolean,
    diceRollAnimState: DiceRollAnimState,
    stepMoveState: StepMoveAnimState?,
    onDiceSpinTick: () -> Unit,
    onDiceSettle: () -> Unit,
    onDiceRollComplete: (Long) -> Unit,
    onTileStep: () -> Unit,
    onTileLanding: () -> Unit,
    onStepMovementComplete: (Long) -> Unit,
    onSelectDiceMode: (DiceMode) -> Unit,
    onRollDice: () -> Unit,
    onTileClick: (BoardTile) -> Unit,
    modifier: Modifier = Modifier
) {
    val activePlayer = snapshot.players.getOrNull(snapshot.currentTurnIndex)
    val isWhirlpoolMode = snapshot.pendingAction is PendingBoardAction.PickWhirlpoolTarget

    var animatedStepTileByUid by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var activeSteppingPlayerUid by remember { mutableStateOf<String?>(null) }

    // LaunchedEffect loop moving the player token one board tile at a time with a fixed delay
    LaunchedEffect(stepMoveState?.moveId) {
        val move = stepMoveState
        if (move == null || move.totalSteps <= 0) {
            animatedStepTileByUid = emptyMap()
            activeSteppingPlayerUid = null
            return@LaunchedEffect
        }
        activeSteppingPlayerUid = move.playerUid
        var currentTile = move.startTile
        animatedStepTileByUid = mapOf(move.playerUid to currentTile)

        repeat(move.totalSteps) {
            delay(STEP_TILE_DELAY_MS)
            currentTile = (currentTile + 1) % BoardConfig.BOARD_SIZE
            animatedStepTileByUid = mapOf(move.playerUid to currentTile)
            onTileStep()
        }

        delay(STEP_LAND_PAUSE_MS)
        onTileLanding()
        onStepMovementComplete(move.moveId)
        animatedStepTileByUid = emptyMap()
        activeSteppingPlayerUid = null
    }

    val isTurnAnimating = diceRollAnimState.isRolling || stepMoveState != null || activeSteppingPlayerUid != null
    val activeStepTileIndex = activeSteppingPlayerUid?.let { uid -> animatedStepTileByUid[uid] }
    val tileByCoord = BoardConfig.tiles.associateBy { it.row to it.col }

    BoxWithConstraints(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .background(MahoganyWood)
            .border(3.dp, BurnishedGold, RoundedCornerShape(16.dp))
            .padding(4.dp)
    ) {
        val gridUnit = maxWidth / 8f

        // Center 6x6 Mystical Ocean Map & Dice Arena
        Box(
            modifier = Modifier
                .padding(gridUnit)
                .fillMaxSize()
                .clip(RoundedCornerShape(10.dp))
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF143854), DeepOcean, AbyssalNavy)
                    )
                )
                .border(1.5.dp, BrassBorder, RoundedCornerShape(10.dp))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val c = Offset(size.width / 2f, size.height / 2f)
                drawCircle(
                    color = BurnishedGold.copy(alpha = 0.14f),
                    radius = size.minDimension * 0.40f,
                    center = c,
                    style = Stroke(width = 2.5f)
                )
                drawCircle(
                    color = LagoonTeal.copy(alpha = 0.16f),
                    radius = size.minDimension * 0.25f,
                    center = c,
                    style = Stroke(width = 2f)
                )
                drawLine(
                    color = BurnishedGold.copy(alpha = 0.10f),
                    start = Offset(0f, c.y),
                    end = Offset(size.width, c.y),
                    strokeWidth = 1.5f
                )
                drawLine(
                    color = BurnishedGold.copy(alpha = 0.10f),
                    start = Offset(c.x, 0f),
                    end = Offset(c.x, size.height),
                    strokeWidth = 1.5f
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (isWhirlpoolMode) "🌀 แตะช่องบนกระดานเพื่อวาร์ป!" else "รอบที่ ${snapshot.turnNumber}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isWhirlpoolMode) BioluminescentCyan else BrassBorder,
                        fontWeight = FontWeight.Bold
                    )
                    if (activePlayer != null) {
                        Text(
                            text = "ตาของ ${activePlayer.name}",
                            style = MaterialTheme.typography.titleMedium,
                            color = TreasureGold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (snapshot.lastFortuneBanner != null) {
                        Text(
                            text = snapshot.lastFortuneBanner,
                            style = MaterialTheme.typography.labelSmall,
                            color = BioluminescentCyan,
                            textAlign = TextAlign.Center,
                            maxLines = 2
                        )
                    }
                }

                // 4-Second Animatable DiceRollingComponent
                DiceRollingComponent(
                    animState = diceRollAnimState,
                    staticDie1 = snapshot.lastDice1,
                    staticDie2 = snapshot.lastDice2,
                    onDiceSpinTick = onDiceSpinTick,
                    onDiceSettle = onDiceSettle,
                    onRollComplete = onDiceRollComplete
                )

                // Special Dice Control (Normal / Odd / Even) + Roll Button
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (activePlayer != null && isLocalTurn && !snapshot.hasRolledThisTurn && !isTurnAnimating && snapshot.winnerUid.isBlank()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            DiceMode.entries.forEach { mode ->
                                val enabled = mode == DiceMode.NORMAL || activePlayer.specialDiceCharges > 0
                                val label = if (mode == DiceMode.NORMAL) {
                                    mode.thaiLabel
                                } else {
                                    "${mode.thaiLabel}(${activePlayer.specialDiceCharges})"
                                }
                                FilterChip(
                                    selected = selectedDiceMode == mode,
                                    onClick = { if (enabled) onSelectDiceMode(mode) },
                                    enabled = enabled,
                                    label = {
                                        Text(
                                            text = label,
                                            fontSize = 10.sp,
                                            maxLines = 1
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = TreasureGold,
                                        selectedLabelColor = InkDark
                                    )
                                )
                            }
                        }
                    }

                    val canRoll = isLocalTurn && !snapshot.hasRolledThisTurn && !isTurnAnimating && snapshot.pendingAction == null && snapshot.winnerUid.isBlank()
                    Button(
                        onClick = onRollDice,
                        enabled = canRoll,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TreasureGold,
                            contentColor = InkDark,
                            disabledContainerColor = MahoganyWood.copy(alpha = 0.75f),
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("roll_dice_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Casino,
                            contentDescription = "Roll dice",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = when {
                                snapshot.winnerUid.isNotBlank() -> "จบแมตช์แล้ว"
                                diceRollAnimState.isRolling -> "กำลังทอยเต๋า..."
                                activeSteppingPlayerUid != null || stepMoveState != null -> "กำลังเดินทีละช่อง..."
                                isLocalTurn && canRoll -> "ทอยลูกเต๋า!"
                                activePlayer?.isBot == true -> "บอทกำลังเดิน..."
                                else -> "รอกัปตันอื่น..."
                            },
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }

        // 8x8 Perimeter Tile Grid
        Column(modifier = Modifier.fillMaxSize()) {
            for (r in 0..7) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    if (r == 0 || r == 7) {
                        for (c in 0..7) {
                            val tile = tileByCoord[r to c]!!
                            BoardTileCell(
                                tile = tile,
                                snapshot = snapshot,
                                animatedStepTileByUid = animatedStepTileByUid,
                                isWhirlpoolTargetable = isWhirlpoolMode,
                                isStepHighlighted = tile.index == activeStepTileIndex,
                                steppingPlayerUid = activeSteppingPlayerUid,
                                onClick = { onTileClick(tile) },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )
                        }
                    } else {
                        val leftTile = tileByCoord[r to 0]!!
                        val rightTile = tileByCoord[r to 7]!!
                        BoardTileCell(
                            tile = leftTile,
                            snapshot = snapshot,
                            animatedStepTileByUid = animatedStepTileByUid,
                            isWhirlpoolTargetable = isWhirlpoolMode,
                            isStepHighlighted = leftTile.index == activeStepTileIndex,
                            steppingPlayerUid = activeSteppingPlayerUid,
                            onClick = { onTileClick(leftTile) },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                        Spacer(modifier = Modifier.weight(6f))
                        BoardTileCell(
                            tile = rightTile,
                            snapshot = snapshot,
                            animatedStepTileByUid = animatedStepTileByUid,
                            isWhirlpoolTargetable = isWhirlpoolMode,
                            isStepHighlighted = rightTile.index == activeStepTileIndex,
                            steppingPlayerUid = activeSteppingPlayerUid,
                            onClick = { onTileClick(rightTile) },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BoardTileCell(
    tile: BoardTile,
    snapshot: GameSnapshot,
    animatedStepTileByUid: Map<String, Int>,
    isWhirlpoolTargetable: Boolean,
    isStepHighlighted: Boolean,
    steppingPlayerUid: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ownerUid = snapshot.tileOwners.getOrElse(tile.index) { "" }
    val ownerPlayer = snapshot.players.firstOrNull { it.uid == ownerUid }
    val level = snapshot.tileLevels.getOrElse(tile.index) { 0 }
    val festivalMult = snapshot.tileFestivals.getOrElse(tile.index) { 1 }
    val playersHere = snapshot.players.filter {
        !it.bankrupt && (animatedStepTileByUid[it.uid] ?: it.position) == tile.index
    }

    val borderColor = when {
        isStepHighlighted -> TreasureGold
        isWhirlpoolTargetable -> BioluminescentCyan
        ownerPlayer != null -> Color(ownerPlayer.captain.accentHex)
        else -> BrassBorder.copy(alpha = 0.55f)
    }

    val tileBg = when (tile.type) {
        TileType.ISLAND -> AntiqueParchment.copy(alpha = 0.94f)
        TileType.START -> Color(0xFFFFE082)
        TileType.JAIL -> Color(0xFF4A3B3B)
        TileType.TAVERN -> Color(0xFFFFCC80)
        TileType.FESTIVAL -> Color(0xFFF48FB1)
        TileType.WHIRLPOOL -> Color(0xFF80DEEA)
        TileType.FORTUNE -> Color(0xFFB39DDB)
        TileType.KRAKEN_TAX -> Color(0xFFEF9A9A)
    }

    Box(
        modifier = modifier
            .padding(1.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(tileBg)
            .border(
                width = when {
                    isStepHighlighted -> 2.5.dp
                    ownerPlayer != null || isWhirlpoolTargetable -> 2.dp
                    else -> 0.8.dp
                },
                color = borderColor,
                shape = RoundedCornerShape(5.dp)
            )
            .clickable(onClick = onClick)
            .testTag("board_tile_${tile.index}")
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            if (tile.type == TileType.ISLAND && tile.colorGroup != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .background(Color(tile.colorGroup.colorHex))
                )
            } else {
                Spacer(modifier = Modifier.height(2.dp))
            }

            if (tile.type != TileType.ISLAND) {
                val icon = when (tile.type) {
                    TileType.START -> Icons.Default.Anchor
                    TileType.JAIL -> Icons.Default.Lock
                    TileType.TAVERN -> Icons.Default.LocalBar
                    TileType.FESTIVAL -> Icons.Default.Celebration
                    TileType.WHIRLPOOL -> Icons.Default.Cyclone
                    TileType.FORTUNE -> Icons.Default.Inventory2
                    TileType.KRAKEN_TAX -> Icons.Default.Warning
                    TileType.ISLAND -> Icons.Default.Anchor
                }
                Icon(
                    imageVector = icon,
                    contentDescription = tile.name,
                    tint = if (tile.type == TileType.JAIL) AntiqueParchment else InkDark,
                    modifier = Modifier.size(13.dp)
                )
            }

            Text(
                text = tile.name,
                fontSize = 7.sp,
                lineHeight = 8.sp,
                fontWeight = FontWeight.Bold,
                color = if (tile.type == TileType.JAIL) AntiqueParchment else InkDark,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 1.dp)
            )

            if (tile.type == TileType.ISLAND) {
                val bottomText = if (ownerPlayer != null) {
                    val toll = BoardConfig.calculateToll(tile, ownerUid, level, festivalMult, snapshot.tileOwners)
                    "${toll}G"
                } else {
                    "${tile.basePrice}G"
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(bottom = 1.dp)
                ) {
                    if (level == 4) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "Landmark",
                            tint = CrimsonCorsair,
                            modifier = Modifier.size(8.dp)
                        )
                    } else if (level > 0) {
                        Text(
                            text = "★".repeat(level),
                            fontSize = 6.sp,
                            color = Color(0xFFB8860B)
                        )
                    }
                    Text(
                        text = bottomText,
                        fontSize = 6.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (ownerPlayer != null) Color(ownerPlayer.captain.accentHex) else InkDark
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(2.dp))
            }
        }

        // Festival x2/x4 badge
        if (festivalMult > 1) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .background(CrimsonCorsair, RoundedCornerShape(bottomStart = 4.dp))
                    .padding(horizontal = 2.dp)
            ) {
                Text(
                    text = "x$festivalMult",
                    fontSize = 6.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        // Player Chibi Tokens on this tile
        if (playersHere.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy((-4).dp)
            ) {
                playersHere.forEach { p ->
                    val isStepping = p.uid == steppingPlayerUid
                    val tokenSize by animateDpAsState(
                        targetValue = if (isStepping) 21.dp else 16.dp,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "pawn_step_size"
                    )
                    Image(
                        painter = painterResource(id = p.captain.avatarRes),
                        contentDescription = p.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(tokenSize)
                            .clip(CircleShape)
                            .border(
                                width = if (isStepping) 2.dp else 1.5.dp,
                                color = if (isStepping) TreasureGold else Color(p.captain.accentHex),
                                shape = CircleShape
                            )
                    )
                }
            }
        }
    }
}
