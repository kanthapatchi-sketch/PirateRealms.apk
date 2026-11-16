package com.example.game

import com.example.data.model.BoardTile
import com.example.data.model.BuildingTier
import com.example.data.model.CaptainId
import com.example.data.model.DiceMode
import com.example.data.model.PlayerState
import com.example.data.model.TileType
import kotlin.math.roundToInt
import kotlin.random.Random

data class DiceRollOutcome(
    val die1: Int,
    val die2: Int,
    val total: Int,
    val isDoubles: Boolean,
    val usedMode: DiceMode,
    val modeSucceeded: Boolean
)

sealed interface PendingBoardAction {
    data class BuyIsland(val tile: BoardTile) : PendingBoardAction
    data class UpgradeIsland(val tile: BoardTile, val currentLevel: Int) : PendingBoardAction
    data class PayTollOrDefend(
        val tile: BoardTile,
        val owner: PlayerState,
        val currentLevel: Int,
        val tollAmount: Int,
        val canUseAngelCard: Boolean,
        val canTakeoverAfter: Boolean,
        val takeoverCost: Int
    ) : PendingBoardAction
    data class TakeoverOffer(
        val tile: BoardTile,
        val currentOwner: PlayerState,
        val currentLevel: Int,
        val takeoverCost: Int
    ) : PendingBoardAction
    data class TavernMiniGame(
        val round: Int = 1,
        val currentPrize: Int = 120,
        val lastFlipResult: String? = null
    ) : PendingBoardAction
    data class PickFestivalIsland(val ownedTileIndices: List<Int>) : PendingBoardAction
    data object PickWhirlpoolTarget : PendingBoardAction
    data class JailDecision(val bailCost: Int) : PendingBoardAction
}

data class GameSnapshot(
    val players: List<PlayerState>,
    val tileOwners: List<String> = List(BoardConfig.BOARD_SIZE) { "" },
    val tileLevels: List<Int> = List(BoardConfig.BOARD_SIZE) { 0 },
    val tileFestivals: List<Int> = List(BoardConfig.BOARD_SIZE) { 1 },
    val currentTurnIndex: Int = 0,
    val turnNumber: Int = 1,
    val lastDice1: Int = 1,
    val lastDice2: Int = 1,
    val hasRolledThisTurn: Boolean = false,
    val grantExtraRollFromDoubles: Boolean = false,
    val pendingAction: PendingBoardAction? = null,
    val logs: List<String> = listOf("เรือโจรสลัดออกทะเล! กัปตันคนแรกเตรียมทอยลูกเต๋า"),
    val winnerUid: String = "",
    val lastFortuneBanner: String? = null
)

object GameEngine {

    fun rollDice(
        player: PlayerState,
        requestedMode: DiceMode,
        random: Random = Random
    ): DiceRollOutcome {
        val actualMode = if (requestedMode != DiceMode.NORMAL && player.specialDiceCharges > 0) {
            requestedMode
        } else {
            DiceMode.NORMAL
        }

        if (actualMode == DiceMode.NORMAL) {
            val d1 = random.nextInt(1, 7)
            val d2 = random.nextInt(1, 7)
            return DiceRollOutcome(d1, d2, d1 + d2, d1 == d2, DiceMode.NORMAL, true)
        }

        // Shadow Vane has 100% accuracy; others have 75% accuracy
        val accuracy = if (player.captain == CaptainId.VANE) 1.0 else 0.75
        val hitTargetParity = random.nextDouble() < accuracy
        val wantOdd = (actualMode == DiceMode.ODD)
        val targetOdd = if (hitTargetParity) wantOdd else !wantOdd

        val candidates = mutableListOf<Pair<Int, Int>>()
        for (a in 1..6) {
            for (b in 1..6) {
                if (((a + b) % 2 == 1) == targetOdd) {
                    candidates.add(a to b)
                }
            }
        }
        val chosen = candidates[random.nextInt(candidates.size)]
        return DiceRollOutcome(
            die1 = chosen.first,
            die2 = chosen.second,
            total = chosen.first + chosen.second,
            isDoubles = chosen.first == chosen.second,
            usedMode = actualMode,
            modeSucceeded = hitTargetParity
        )
    }

    fun applyGoldDebitWithLiquidation(
        snapshot: GameSnapshot,
        debtorIndex: Int,
        amountDue: Int,
        creditorIndex: Int?,
        reason: String
    ): GameSnapshot {
        val players = snapshot.players.toMutableList()
        val owners = snapshot.tileOwners.toMutableList()
        val levels = snapshot.tileLevels.toMutableList()
        val festivals = snapshot.tileFestivals.toMutableList()
        val logs = snapshot.logs.toMutableList()

        var debtor = players[debtorIndex]
        if (debtor.gold >= amountDue) {
            debtor = debtor.copy(gold = debtor.gold - amountDue)
            players[debtorIndex] = debtor
            if (creditorIndex != null && !players[creditorIndex].bankrupt) {
                val creditor = players[creditorIndex]
                players[creditorIndex] = creditor.copy(gold = creditor.gold + amountDue)
            }
            return snapshot.copy(players = players)
        }

        // Forced liquidation of cheapest islands first
        val ownedIndices = owners.indices
            .filter { owners[it] == debtor.uid }
            .sortedBy { BoardConfig.calculateLiquidationValue(BoardConfig.tiles[it], levels[it]) }

        var availableGold = debtor.gold
        for (tileIdx in ownedIndices) {
            if (availableGold >= amountDue) break
            val tile = BoardConfig.tiles[tileIdx]
            val saleValue = BoardConfig.calculateLiquidationValue(tile, levels[tileIdx])
            availableGold += saleValue
            owners[tileIdx] = ""
            levels[tileIdx] = 0
            festivals[tileIdx] = 1
            logs.add(0, "⚖️ ${debtor.name} ขายทอดตลาด ${tile.name} ได้ +${saleValue}G เพื่อจ่าย$reason")
        }

        if (availableGold >= amountDue) {
            val remainingGold = availableGold - amountDue
            players[debtorIndex] = debtor.copy(gold = remainingGold)
            if (creditorIndex != null && !players[creditorIndex].bankrupt) {
                val creditor = players[creditorIndex]
                players[creditorIndex] = creditor.copy(gold = creditor.gold + amountDue)
            }
            return snapshot.copy(
                players = players,
                tileOwners = owners,
                tileLevels = levels,
                tileFestivals = festivals,
                logs = logs.take(40)
            )
        } else {
            // Bankruptcy! Transfer whatever cash remains to creditor
            if (creditorIndex != null && !players[creditorIndex].bankrupt) {
                val creditor = players[creditorIndex]
                players[creditorIndex] = creditor.copy(gold = creditor.gold + availableGold)
            }
            players[debtorIndex] = debtor.copy(gold = 0, bankrupt = true)
            logs.add(0, "💀 ${debtor.name} ล้มละลายออกจากน่านน้ำ!")

            val alive = players.filter { !it.bankrupt }
            val winnerUid = if (alive.size == 1) alive.first().uid else ""
            if (winnerUid.isNotBlank()) {
                logs.add(0, "👑 ${alive.first().name} เป็นเจ้าแห่งน่านน้ำคนสุดท้าย (Last Pirate Standing)!")
            }

            return snapshot.copy(
                players = players,
                tileOwners = owners,
                tileLevels = levels,
                tileFestivals = festivals,
                logs = logs.take(40),
                winnerUid = winnerUid
            )
        }
    }

    fun resolveJailBail(snapshot: GameSnapshot): GameSnapshot {
        val idx = snapshot.currentTurnIndex
        val player = snapshot.players[idx]
        if (!player.inJail || player.gold < BoardConfig.JAIL_BAIL_COST) return snapshot
        val updatedPlayers = snapshot.players.toMutableList()
        updatedPlayers[idx] = player.copy(
            gold = player.gold - BoardConfig.JAIL_BAIL_COST,
            inJail = false,
            jailTurns = 0
        )
        val logs = (listOf("🔓 ${player.name} จ่ายค่าไถ่เรือ ${BoardConfig.JAIL_BAIL_COST}G ออกจากเกาะกะโหลก") + snapshot.logs).take(40)
        return snapshot.copy(
            players = updatedPlayers,
            pendingAction = null,
            logs = logs
        )
    }

    fun executeRollAndMove(
        snapshot: GameSnapshot,
        requestedMode: DiceMode,
        random: Random = Random
    ): GameSnapshot {
        if (snapshot.winnerUid.isNotBlank()) return snapshot
        val idx = snapshot.currentTurnIndex
        var player = snapshot.players[idx]
        if (player.bankrupt) return advanceToNextTurn(snapshot)

        val outcome = rollDice(player, requestedMode, random)
        val players = snapshot.players.toMutableList()
        val logs = snapshot.logs.toMutableList()

        val chargeUsed = if (outcome.usedMode != DiceMode.NORMAL && player.specialDiceCharges > 0) 1 else 0
        player = player.copy(specialDiceCharges = (player.specialDiceCharges - chargeUsed).coerceAtLeast(0))

        val modeLabel = if (outcome.usedMode != DiceMode.NORMAL) {
            " [${outcome.usedMode.thaiLabel}${if (outcome.modeSucceeded) " ✓" else " พลาด!"}]"
        } else ""

        // Handle Deserted Island (Jail)
        if (player.inJail) {
            if (outcome.isDoubles) {
                player = player.copy(inJail = false, jailTurns = 0, doublesStreak = 0)
                players[idx] = player
                logs.add(0, "🎲 ${player.name} ทอยได้เบิ้ล ${outcome.die1}-${outcome.die2}! หลุดพ้นจากเกาะกะโหลก")
            } else if (player.jailTurns >= 2) {
                player = player.copy(inJail = false, jailTurns = 0, doublesStreak = 0)
                players[idx] = player
                logs.add(0, "⛵ ${player.name} ครบกำหนดติดเกาะกะโหลกแล้ว ออกเรือต่อด้วยแต้ม ${outcome.total}")
            } else {
                players[idx] = player.copy(jailTurns = player.jailTurns + 1)
                logs.add(0, "🏝️ ${player.name} ทอยได้ ${outcome.die1}+${outcome.die2} (ไม่เบิ้ล) ยังติดเกาะกะโหลก (${player.jailTurns + 1}/3)")
                return snapshot.copy(
                    players = players,
                    lastDice1 = outcome.die1,
                    lastDice2 = outcome.die2,
                    hasRolledThisTurn = true,
                    grantExtraRollFromDoubles = false,
                    pendingAction = null,
                    logs = logs.take(40)
                )
            }
        }

        val newDoublesStreak = if (outcome.isDoubles) player.doublesStreak + 1 else 0
        if (newDoublesStreak >= 3) {
            players[idx] = player.copy(
                position = BoardConfig.JAIL_TILE_INDEX,
                inJail = true,
                jailTurns = 0,
                doublesStreak = 0
            )
            logs.add(0, "⚡ ${player.name} ทอยเบิ้ล 3 ครั้งติด! ถูกพายุซัดไปเกาะกะโหลก")
            return snapshot.copy(
                players = players,
                lastDice1 = outcome.die1,
                lastDice2 = outcome.die2,
                hasRolledThisTurn = true,
                grantExtraRollFromDoubles = false,
                pendingAction = null,
                logs = logs.take(40)
            )
        }

        val oldPos = player.position
        val rawNewPos = oldPos + outcome.total
        val passedStart = rawNewPos >= BoardConfig.BOARD_SIZE
        val newPos = rawNewPos % BoardConfig.BOARD_SIZE
        var salaryGain = 0
        if (passedStart) {
            salaryGain = BoardConfig.calculatePassStartSalary(player)
            logs.add(0, "⚓ ${player.name} ผ่านท่าเรือหลวง รับเงินเดือน +${salaryGain}G")
        }

        player = player.copy(
            position = newPos,
            gold = player.gold + salaryGain,
            doublesStreak = newDoublesStreak
        )
        players[idx] = player

        val landedTile = BoardConfig.tiles[newPos]
        logs.add(
            0,
            "🎲 ${player.name} ทอยได้ ${outcome.die1}+${outcome.die2}=${outcome.total}$modeLabel เดินสู่ ${landedTile.name}"
        )

        val movedSnapshot = snapshot.copy(
            players = players,
            lastDice1 = outcome.die1,
            lastDice2 = outcome.die2,
            hasRolledThisTurn = true,
            grantExtraRollFromDoubles = outcome.isDoubles,
            logs = logs.take(40),
            lastFortuneBanner = null
        )

        return resolveLanding(movedSnapshot, idx, newPos, random)
    }

    data class PreparedRollResult(
        val outcome: DiceRollOutcome,
        val snapshotBeforeSteps: GameSnapshot,
        val stepsToWalk: Int,
        val wentToJailOnTripleDoubles: Boolean,
        val stayedInJail: Boolean,
        val modeLabel: String
    )

    fun prepareDiceOutcome(
        snapshot: GameSnapshot,
        requestedMode: DiceMode,
        random: Random = Random
    ): PreparedRollResult {
        val idx = snapshot.currentTurnIndex
        var player = snapshot.players[idx]
        val outcome = rollDice(player, requestedMode, random)
        val players = snapshot.players.toMutableList()
        val logs = snapshot.logs.toMutableList()

        val chargeUsed = if (outcome.usedMode != DiceMode.NORMAL && player.specialDiceCharges > 0) 1 else 0
        player = player.copy(specialDiceCharges = (player.specialDiceCharges - chargeUsed).coerceAtLeast(0))

        val modeLabel = if (outcome.usedMode != DiceMode.NORMAL) {
            " [${outcome.usedMode.thaiLabel}${if (outcome.modeSucceeded) " ✓" else " พลาด!"}]"
        } else ""

        if (player.inJail) {
            if (outcome.isDoubles) {
                player = player.copy(inJail = false, jailTurns = 0, doublesStreak = 0)
                players[idx] = player
                logs.add(0, "🎲 ${player.name} ทอยได้เบิ้ล ${outcome.die1}-${outcome.die2}! หลุดพ้นจากเกาะกะโหลก")
            } else if (player.jailTurns >= 2) {
                player = player.copy(inJail = false, jailTurns = 0, doublesStreak = 0)
                players[idx] = player
                logs.add(0, "⛵ ${player.name} ครบกำหนดติดเกาะกะโหลกแล้ว ออกเรือต่อด้วยแต้ม ${outcome.total}")
            } else {
                players[idx] = player.copy(jailTurns = player.jailTurns + 1)
                logs.add(0, "🏝️ ${player.name} ทอยได้ ${outcome.die1}+${outcome.die2} (ไม่เบิ้ล) ยังติดเกาะกะโหลก (${player.jailTurns + 1}/3)")
                return PreparedRollResult(
                    outcome = outcome,
                    snapshotBeforeSteps = snapshot.copy(
                        players = players,
                        lastDice1 = outcome.die1,
                        lastDice2 = outcome.die2,
                        hasRolledThisTurn = true,
                        grantExtraRollFromDoubles = false,
                        pendingAction = null,
                        logs = logs.take(40),
                        lastFortuneBanner = null
                    ),
                    stepsToWalk = 0,
                    wentToJailOnTripleDoubles = false,
                    stayedInJail = true,
                    modeLabel = modeLabel
                )
            }
        }

        val newDoublesStreak = if (outcome.isDoubles) player.doublesStreak + 1 else 0
        if (newDoublesStreak >= 3) {
            players[idx] = player.copy(
                position = BoardConfig.JAIL_TILE_INDEX,
                inJail = true,
                jailTurns = 0,
                doublesStreak = 0
            )
            logs.add(0, "⚡ ${player.name} ทอยเบิ้ล 3 ครั้งติด! ถูกพายุซัดไปเกาะกะโหลก")
            return PreparedRollResult(
                outcome = outcome,
                snapshotBeforeSteps = snapshot.copy(
                    players = players,
                    lastDice1 = outcome.die1,
                    lastDice2 = outcome.die2,
                    hasRolledThisTurn = true,
                    grantExtraRollFromDoubles = false,
                    pendingAction = null,
                    logs = logs.take(40),
                    lastFortuneBanner = null
                ),
                stepsToWalk = 0,
                wentToJailOnTripleDoubles = true,
                stayedInJail = false,
                modeLabel = modeLabel
            )
        }

        player = player.copy(doublesStreak = newDoublesStreak)
        players[idx] = player
        return PreparedRollResult(
            outcome = outcome,
            snapshotBeforeSteps = snapshot.copy(
                players = players,
                lastDice1 = outcome.die1,
                lastDice2 = outcome.die2,
                logs = logs.take(40),
                lastFortuneBanner = null
            ),
            stepsToWalk = outcome.total,
            wentToJailOnTripleDoubles = false,
            stayedInJail = false,
            modeLabel = modeLabel
        )
    }

    fun stepPlayerOneTile(snapshot: GameSnapshot, playerIndex: Int): GameSnapshot {
        val players = snapshot.players.toMutableList()
        val player = players[playerIndex]
        val nextPos = (player.position + 1) % BoardConfig.BOARD_SIZE
        val logs = snapshot.logs.toMutableList()
        var salaryGain = 0
        if (nextPos == 0) {
            salaryGain = BoardConfig.calculatePassStartSalary(player)
            logs.add(0, "⚓ ${player.name} ผ่านท่าเรือหลวง รับเงินเดือน +${salaryGain}G")
        }
        players[playerIndex] = player.copy(
            position = nextPos,
            gold = player.gold + salaryGain
        )
        return snapshot.copy(
            players = players,
            logs = logs.take(40)
        )
    }

    fun finalizeStepMovementAndResolveLanding(
        snapshot: GameSnapshot,
        playerIndex: Int,
        outcome: DiceRollOutcome,
        modeLabel: String,
        random: Random = Random
    ): GameSnapshot {
        val player = snapshot.players[playerIndex]
        val landedTile = BoardConfig.tiles[player.position]
        val logs = (listOf(
            "🎲 ${player.name} ทอยได้ ${outcome.die1}+${outcome.die2}=${outcome.total}$modeLabel เดินสู่ ${landedTile.name}"
        ) + snapshot.logs).take(40)

        val movedSnapshot = snapshot.copy(
            lastDice1 = outcome.die1,
            lastDice2 = outcome.die2,
            hasRolledThisTurn = true,
            grantExtraRollFromDoubles = outcome.isDoubles,
            logs = logs,
            lastFortuneBanner = null
        )
        return resolveLanding(movedSnapshot, playerIndex, player.position, random)
    }

    fun resolveLanding(
        snapshot: GameSnapshot,
        playerIndex: Int,
        tileIndex: Int,
        random: Random = Random
    ): GameSnapshot {
        val tile = BoardConfig.tiles[tileIndex]
        val player = snapshot.players[playerIndex]
        val logs = snapshot.logs.toMutableList()

        return when (tile.type) {
            TileType.START -> {
                // Already got salary if passed; if warped here, give small dock bonus
                val updated = snapshot.players.toMutableList()
                updated[playerIndex] = player.copy(gold = player.gold + 150)
                logs.add(0, "🏴‍☠️ ${player.name} เทียบท่าเรือหลวง รับโบนัสเทียบท่า +150G")
                snapshot.copy(players = updated, pendingAction = null, logs = logs.take(40))
            }

            TileType.JAIL -> {
                val updated = snapshot.players.toMutableList()
                updated[playerIndex] = player.copy(inJail = true, jailTurns = 0, doublesStreak = 0)
                logs.add(0, "💀 ${player.name} เกยตื้นที่เกาะกะโหลก!")
                snapshot.copy(
                    players = updated,
                    grantExtraRollFromDoubles = false,
                    pendingAction = null,
                    logs = logs.take(40)
                )
            }

            TileType.KRAKEN_TAX -> {
                val tax = 150 + (player.gold * 0.05).roundToInt()
                logs.add(0, "🐙 ภาษีคราเคน! ${player.name} ต้องจ่ายค่าคุ้มครอง ${tax}G")
                applyGoldDebitWithLiquidation(
                    snapshot.copy(logs = logs.take(40)),
                    debtorIndex = playerIndex,
                    amountDue = tax,
                    creditorIndex = null,
                    reason = "ภาษีคราเคน"
                ).copy(pendingAction = null)
            }

            TileType.TAVERN -> {
                if (player.isBot) {
                    val won = random.nextBoolean()
                    val delta = if (won) 240 else -80
                    val updated = snapshot.players.toMutableList()
                    updated[playerIndex] = player.copy(gold = (player.gold + delta).coerceAtLeast(0))
                    logs.add(0, "🍺 ${player.name} เล่นทายเหรียญที่โรงเตี๊ยม ${if (won) "ชนะ +240G!" else "แพ้ -80G"}")
                    snapshot.copy(players = updated, pendingAction = null, logs = logs.take(40))
                } else {
                    snapshot.copy(pendingAction = PendingBoardAction.TavernMiniGame())
                }
            }

            TileType.FESTIVAL -> {
                val ownedIndices = snapshot.tileOwners.indices.filter { snapshot.tileOwners[it] == player.uid }
                if (ownedIndices.isEmpty()) {
                    logs.add(0, "🎉 ${player.name} แวะเทศกาลโจรสลัด แต่ยังไม่มีเกาะให้จัดงาน")
                    snapshot.copy(pendingAction = null, logs = logs.take(40))
                } else if (player.isBot) {
                    val chosen = BotAiEngine.chooseFestivalTileIndex(player, snapshot.tileOwners, snapshot.tileFestivals)
                    if (chosen != null) applyFestivalToTile(snapshot, chosen) else snapshot
                } else {
                    snapshot.copy(pendingAction = PendingBoardAction.PickFestivalIsland(ownedIndices))
                }
            }

            TileType.WHIRLPOOL -> {
                if (player.isBot) {
                    val dest = BotAiEngine.chooseWhirlpoolDestination(player, snapshot.tileOwners, snapshot.tileLevels)
                    warpPlayerToTile(snapshot, playerIndex, dest, random)
                } else {
                    snapshot.copy(pendingAction = PendingBoardAction.PickWhirlpoolTarget)
                }
            }

            TileType.FORTUNE -> {
                resolveFortuneCard(snapshot, playerIndex, random)
            }

            TileType.ISLAND -> {
                val ownerUid = snapshot.tileOwners[tileIndex]
                val currentLevel = snapshot.tileLevels[tileIndex]
                when {
                    ownerUid.isBlank() -> {
                        if (player.isBot) {
                            val chosenTier = BotAiEngine.chooseBuyTier(player, tile, snapshot.tileOwners)
                            if (chosenTier != null) buyIsland(snapshot, tileIndex, chosenTier) else snapshot
                        } else if (player.gold >= tile.basePrice) {
                            snapshot.copy(pendingAction = PendingBoardAction.BuyIsland(tile))
                        } else {
                            logs.add(0, "💸 ${player.name} ทองไม่พอซื้อ ${tile.name} (${tile.basePrice}G)")
                            snapshot.copy(pendingAction = null, logs = logs.take(40))
                        }
                    }

                    ownerUid == player.uid -> {
                        if (currentLevel >= 4) {
                            logs.add(0, "🏰 ${player.name} แวะตรวจตราแลนด์มาร์ก ${tile.name}")
                            snapshot.copy(pendingAction = null, logs = logs.take(40))
                        } else if (player.isBot) {
                            val nextTier = BotAiEngine.shouldUpgradeOwnedTile(player, tile, currentLevel, snapshot.tileOwners)
                            if (nextTier != null) upgradeIsland(snapshot, tileIndex, nextTier) else snapshot
                        } else {
                            val nextCost = BoardConfig.calculateBuildCost(tile, BuildingTier.fromLevel(currentLevel + 1), player.captain)
                            if (player.gold >= nextCost) {
                                snapshot.copy(pendingAction = PendingBoardAction.UpgradeIsland(tile, currentLevel))
                            } else {
                                snapshot.copy(pendingAction = null)
                            }
                        }
                    }

                    else -> {
                        val ownerIndex = snapshot.players.indexOfFirst { it.uid == ownerUid }
                        val owner = snapshot.players.getOrNull(ownerIndex)
                        if (owner == null || owner.bankrupt) {
                            snapshot.copy(pendingAction = null)
                        } else {
                            val toll = BoardConfig.calculateToll(
                                tile = tile,
                                ownerUid = ownerUid,
                                level = currentLevel,
                                festivalMultiplier = snapshot.tileFestivals[tileIndex],
                                tileOwners = snapshot.tileOwners
                            )
                            val takeoverCost = BoardConfig.calculateTakeoverCost(tile, currentLevel, player.captain)
                            val canTakeover = currentLevel < 4 && (player.gold - toll) >= takeoverCost

                            if (player.isBot) {
                                val useAngel = BotAiEngine.shouldUseAngelCard(player, toll)
                                var afterToll = if (useAngel) {
                                    useAngelCardForToll(snapshot, playerIndex, tile)
                                } else {
                                    logs.add(0, "💰 ${player.name} จ่ายค่าผ่านทาง ${toll}G ให้ ${owner.name} ที่ ${tile.name}")
                                    applyGoldDebitWithLiquidation(
                                        snapshot.copy(logs = logs.take(40)),
                                        debtorIndex = playerIndex,
                                        amountDue = toll,
                                        creditorIndex = ownerIndex,
                                        reason = "ค่าผ่านทาง"
                                    )
                                }
                                if (!afterToll.players[playerIndex].bankrupt && afterToll.winnerUid.isBlank()) {
                                    val botNow = afterToll.players[playerIndex]
                                    if (BotAiEngine.shouldTakeoverTile(botNow, tile, currentLevel, ownerUid, afterToll.tileOwners)) {
                                        afterToll = takeoverIsland(afterToll, tileIndex)
                                    }
                                }
                                afterToll.copy(pendingAction = null)
                            } else {
                                // Interactive player prompt if they have an Angel Card or can Takeover, else pay toll directly
                                if (player.angelCards > 0 || canTakeover) {
                                    snapshot.copy(
                                        pendingAction = PendingBoardAction.PayTollOrDefend(
                                            tile = tile,
                                            owner = owner,
                                            currentLevel = currentLevel,
                                            tollAmount = toll,
                                            canUseAngelCard = player.angelCards > 0,
                                            canTakeoverAfter = canTakeover,
                                            takeoverCost = takeoverCost
                                        )
                                    )
                                } else {
                                    logs.add(0, "💰 ${player.name} จ่ายค่าผ่านทาง ${toll}G ให้ ${owner.name} ที่ ${tile.name}")
                                    applyGoldDebitWithLiquidation(
                                        snapshot.copy(logs = logs.take(40)),
                                        debtorIndex = playerIndex,
                                        amountDue = toll,
                                        creditorIndex = ownerIndex,
                                        reason = "ค่าผ่านทาง"
                                    ).copy(pendingAction = null)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    fun buyIsland(snapshot: GameSnapshot, tileIndex: Int, tier: BuildingTier): GameSnapshot {
        val idx = snapshot.currentTurnIndex
        val player = snapshot.players[idx]
        val tile = BoardConfig.tiles[tileIndex]
        val buildCost = if (tier == BuildingTier.NONE) 0 else BoardConfig.calculateBuildCost(tile, tier, player.captain)
        val totalCost = tile.basePrice + buildCost
        if (player.gold < totalCost) return snapshot.copy(pendingAction = null)

        val players = snapshot.players.toMutableList()
        players[idx] = player.copy(gold = player.gold - totalCost)
        val owners = snapshot.tileOwners.toMutableList()
        val levels = snapshot.tileLevels.toMutableList()
        owners[tileIndex] = player.uid
        levels[tileIndex] = tier.level

        val tierDesc = if (tier == BuildingTier.NONE) "" else " พร้อมสร้าง ${tier.thaiName}"
        val logs = (listOf("🏝️ ${player.name} ซื้อ ${tile.name}$tierDesc (-${totalCost}G)") + snapshot.logs).take(40)
        return snapshot.copy(
            players = players,
            tileOwners = owners,
            tileLevels = levels,
            pendingAction = null,
            logs = logs
        )
    }

    fun upgradeIsland(snapshot: GameSnapshot, tileIndex: Int, targetTier: BuildingTier): GameSnapshot {
        val idx = snapshot.currentTurnIndex
        val player = snapshot.players[idx]
        val tile = BoardConfig.tiles[tileIndex]
        val currentLevel = snapshot.tileLevels[tileIndex]
        if (targetTier.level <= currentLevel || targetTier.level > 4) return snapshot.copy(pendingAction = null)

        var totalCost = 0
        for (lvl in (currentLevel + 1)..targetTier.level) {
            totalCost += BoardConfig.calculateBuildCost(tile, BuildingTier.fromLevel(lvl), player.captain)
        }
        if (player.gold < totalCost) return snapshot.copy(pendingAction = null)

        val players = snapshot.players.toMutableList()
        players[idx] = player.copy(gold = player.gold - totalCost)
        val levels = snapshot.tileLevels.toMutableList()
        levels[tileIndex] = targetTier.level
        val badge = if (targetTier == BuildingTier.LANDMARK) "🏛️ แลนด์มาร์กป้องกันเทคโอเวอร์!" else targetTier.thaiName
        val logs = (listOf("🔨 ${player.name} อัปเกรด ${tile.name} เป็น $badge (-${totalCost}G)") + snapshot.logs).take(40)

        return snapshot.copy(
            players = players,
            tileLevels = levels,
            pendingAction = null,
            logs = logs
        )
    }

    fun useAngelCardForToll(snapshot: GameSnapshot, playerIndex: Int, tile: BoardTile): GameSnapshot {
        val players = snapshot.players.toMutableList()
        val player = players[playerIndex]
        if (player.angelCards <= 0) return snapshot
        players[playerIndex] = player.copy(angelCards = player.angelCards - 1)
        val logs = (listOf("🛡️ ${player.name} ใช้การ์ดนางฟ้าพิทักษ์! ยกเว้นค่าผ่านทาง ${tile.name} เป็น 0G") + snapshot.logs).take(40)
        return snapshot.copy(
            players = players,
            pendingAction = null,
            logs = logs,
            lastFortuneBanner = "🛡️ การ์ดนางฟ้าพิทักษ์ทำงาน! ค่าผ่านทาง = 0G"
        )
    }

    fun confirmTollPayment(
        snapshot: GameSnapshot,
        action: PendingBoardAction.PayTollOrDefend,
        offerTakeoverAfter: Boolean
    ): GameSnapshot {
        val idx = snapshot.currentTurnIndex
        val ownerIdx = snapshot.players.indexOfFirst { it.uid == action.owner.uid }
        val logs = (listOf("💰 ${snapshot.players[idx].name} จ่ายค่าผ่านทาง ${action.tollAmount}G ให้ ${action.owner.name}") + snapshot.logs).take(40)
        val debited = applyGoldDebitWithLiquidation(
            snapshot.copy(logs = logs),
            debtorIndex = idx,
            amountDue = action.tollAmount,
            creditorIndex = if (ownerIdx >= 0) ownerIdx else null,
            reason = "ค่าผ่านทาง"
        )
        val payer = debited.players[idx]
        if (offerTakeoverAfter && !payer.bankrupt && debited.winnerUid.isBlank() && action.currentLevel < 4 && payer.gold >= action.takeoverCost) {
            return debited.copy(
                pendingAction = PendingBoardAction.TakeoverOffer(
                    tile = action.tile,
                    currentOwner = action.owner,
                    currentLevel = action.currentLevel,
                    takeoverCost = action.takeoverCost
                )
            )
        }
        return debited.copy(pendingAction = null)
    }

    fun takeoverIsland(snapshot: GameSnapshot, tileIndex: Int): GameSnapshot {
        val idx = snapshot.currentTurnIndex
        val buyer = snapshot.players[idx]
        val tile = BoardConfig.tiles[tileIndex]
        val currentLevel = snapshot.tileLevels[tileIndex]
        if (currentLevel >= 4) return snapshot.copy(pendingAction = null)

        val ownerUid = snapshot.tileOwners[tileIndex]
        val ownerIdx = snapshot.players.indexOfFirst { it.uid == ownerUid }
        val cost = BoardConfig.calculateTakeoverCost(tile, currentLevel, buyer.captain)
        if (buyer.gold < cost) return snapshot.copy(pendingAction = null)

        val players = snapshot.players.toMutableList()
        players[idx] = buyer.copy(gold = buyer.gold - cost)
        if (ownerIdx >= 0 && !players[ownerIdx].bankrupt) {
            players[ownerIdx] = players[ownerIdx].copy(gold = players[ownerIdx].gold + cost)
        }
        val owners = snapshot.tileOwners.toMutableList()
        owners[tileIndex] = buyer.uid
        val logs = (listOf("⚔️ ${buyer.name} เทคโอเวอร์ ${tile.name} สำเร็จ! (-${cost}G)") + snapshot.logs).take(40)

        return snapshot.copy(
            players = players,
            tileOwners = owners,
            pendingAction = null,
            logs = logs
        )
    }

    fun applyFestivalToTile(snapshot: GameSnapshot, tileIndex: Int): GameSnapshot {
        val festivals = snapshot.tileFestivals.toMutableList()
        val currentMult = festivals[tileIndex]
        val nextMult = (currentMult * 2).coerceAtMost(4)
        festivals[tileIndex] = nextMult
        val tile = BoardConfig.tiles[tileIndex]
        val player = snapshot.players[snapshot.currentTurnIndex]
        val logs = (listOf("🎉 ${player.name} จัดเทศกาลโจรสลัดที่ ${tile.name}! ค่าผ่านทาง x$nextMult") + snapshot.logs).take(40)
        return snapshot.copy(
            tileFestivals = festivals,
            pendingAction = null,
            logs = logs
        )
    }

    fun warpPlayerToTile(
        snapshot: GameSnapshot,
        playerIndex: Int,
        targetTileIndex: Int,
        random: Random = Random
    ): GameSnapshot {
        val players = snapshot.players.toMutableList()
        val player = players[playerIndex]
        val tile = BoardConfig.tiles[targetTileIndex]
        players[playerIndex] = player.copy(position = targetTileIndex)
        val logs = (listOf("🌀 ${player.name} ดำดิ่งสู่น้ำวนมนตรา วาร์ปไปยัง ${tile.name}!") + snapshot.logs).take(40)
        val warped = snapshot.copy(
            players = players,
            pendingAction = null,
            logs = logs
        )
        return if (targetTileIndex == 25) warped else resolveLanding(warped, playerIndex, targetTileIndex, random)
    }

    fun resolveFortuneCard(
        snapshot: GameSnapshot,
        playerIndex: Int,
        random: Random = Random
    ): GameSnapshot {
        val players = snapshot.players.toMutableList()
        val player = players[playerIndex]
        val logs = snapshot.logs.toMutableList()

        return when (random.nextInt(6)) {
            0 -> {
                players[playerIndex] = player.copy(gold = player.gold + 350)
                val banner = "💎 ขุดพบขุมทรัพย์โบราณ! รับ +350G"
                logs.add(0, "✨ ${player.name}: $banner")
                snapshot.copy(players = players, pendingAction = null, logs = logs.take(40), lastFortuneBanner = banner)
            }
            1 -> {
                var plundered = 0
                for (i in players.indices) {
                    if (i != playerIndex && !players[i].bankrupt) {
                        val take = minOf(120, players[i].gold)
                        players[i] = players[i].copy(gold = players[i].gold - take)
                        plundered += take
                    }
                }
                players[playerIndex] = player.copy(gold = player.gold + plundered)
                val banner = "🏴‍☠️ ปล้นสะดมกลางทะเล! ยึดทองจากคู่แข่งรวม +${plundered}G"
                logs.add(0, "✨ ${player.name}: $banner")
                snapshot.copy(players = players, pendingAction = null, logs = logs.take(40), lastFortuneBanner = banner)
            }
            2 -> {
                players[playerIndex] = player.copy(angelCards = player.angelCards + 1)
                val banner = "👼 ได้รับการ์ดนางฟ้าพิทักษ์ +1 ใบ!"
                logs.add(0, "✨ ${player.name}: $banner")
                snapshot.copy(players = players, pendingAction = null, logs = logs.take(40), lastFortuneBanner = banner)
            }
            3 -> {
                val owned = snapshot.tileOwners.indices.filter { snapshot.tileOwners[it] == player.uid }
                if (owned.isNotEmpty()) {
                    val chosen = owned[random.nextInt(owned.size)]
                    val updated = applyFestivalToTile(snapshot, chosen)
                    val banner = "🎊 สุ่มจัดเทศกาล x2 บนเกาะ ${BoardConfig.tiles[chosen].name}!"
                    updated.copy(lastFortuneBanner = banner)
                } else {
                    players[playerIndex] = player.copy(gold = player.gold + 200)
                    val banner = "🪙 ลมทะเลพัดเหรียญทองขึ้นเรือ +200G"
                    logs.add(0, "✨ ${player.name}: $banner")
                    snapshot.copy(players = players, pendingAction = null, logs = logs.take(40), lastFortuneBanner = banner)
                }
            }
            4 -> {
                players[playerIndex] = player.copy(
                    position = BoardConfig.JAIL_TILE_INDEX,
                    inJail = true,
                    jailTurns = 0,
                    doublesStreak = 0
                )
                val banner = "🌪️ พายุคลั่งพัดติดเกาะกะโหลก!"
                logs.add(0, "✨ ${player.name}: $banner")
                snapshot.copy(
                    players = players,
                    grantExtraRollFromDoubles = false,
                    pendingAction = null,
                    logs = logs.take(40),
                    lastFortuneBanner = banner
                )
            }
            else -> {
                val salary = BoardConfig.calculatePassStartSalary(player)
                players[playerIndex] = player.copy(position = 0, gold = player.gold + salary)
                val banner = "💨 ลมหนุนพัดกลับท่าเรือหลวง! รับเงินเดือน +${salary}G"
                logs.add(0, "✨ ${player.name}: $banner")
                snapshot.copy(players = players, pendingAction = null, logs = logs.take(40), lastFortuneBanner = banner)
            }
        }
    }

    fun playTavernFlip(
        snapshot: GameSnapshot,
        current: PendingBoardAction.TavernMiniGame,
        guessHeads: Boolean,
        random: Random = Random
    ): GameSnapshot {
        val coinHeads = random.nextBoolean()
        val won = (coinHeads == guessHeads)
        val idx = snapshot.currentTurnIndex
        val player = snapshot.players[idx]
        val coinLabel = if (coinHeads) "หัว (Heads)" else "ก้อย (Tails)"

        return if (won) {
            if (current.round >= 3) {
                // Max x8 reached -> collect!
                val players = snapshot.players.toMutableList()
                players[idx] = player.copy(gold = player.gold + current.currentPrize)
                val logs = (listOf("🏆 ${player.name} ชนะโรงเตี๊ยม 3 รอบรวด! รับ x8 = +${current.currentPrize}G") + snapshot.logs).take(40)
                snapshot.copy(players = players, pendingAction = null, logs = logs)
            } else {
                snapshot.copy(
                    pendingAction = PendingBoardAction.TavernMiniGame(
                        round = current.round + 1,
                        currentPrize = current.currentPrize * 2,
                        lastFlipResult = "ออก $coinLabel! ชนะ! รางวัลทวีคูณเป็น ${current.currentPrize * 2}G"
                    )
                )
            }
        } else {
            val logs = (listOf("🍺 ${player.name} ทายเหรียญพลาด ($coinLabel) ที่โรงเตี๊ยม") + snapshot.logs).take(40)
            snapshot.copy(pendingAction = null, logs = logs)
        }
    }

    fun collectTavernPrize(snapshot: GameSnapshot, prize: Int): GameSnapshot {
        val idx = snapshot.currentTurnIndex
        val player = snapshot.players[idx]
        val players = snapshot.players.toMutableList()
        players[idx] = player.copy(gold = player.gold + prize)
        val logs = (listOf("🪙 ${player.name} เก็บเงินรางวัลโรงเตี๊ยม +${prize}G") + snapshot.logs).take(40)
        return snapshot.copy(players = players, pendingAction = null, logs = logs)
    }

    fun advanceToNextTurn(snapshot: GameSnapshot): GameSnapshot {
        if (snapshot.winnerUid.isNotBlank()) return snapshot
        val current = snapshot.players[snapshot.currentTurnIndex]
        if (snapshot.grantExtraRollFromDoubles && !current.inJail && !current.bankrupt) {
            return snapshot.copy(
                hasRolledThisTurn = false,
                grantExtraRollFromDoubles = false,
                pendingAction = null,
                logs = (listOf("🎲 ${current.name} ได้สิทธิ์ทอยเบิ้ลซ้ำอีกครั้ง!") + snapshot.logs).take(40)
            )
        }

        val size = snapshot.players.size
        var nextIdx = (snapshot.currentTurnIndex + 1) % size
        var guard = 0
        while (snapshot.players[nextIdx].bankrupt && guard < size) {
            nextIdx = (nextIdx + 1) % size
            guard++
        }

        val nextPlayer = snapshot.players[nextIdx]
        val nextPending = if (nextPlayer.inJail && !nextPlayer.isBot && nextPlayer.gold >= BoardConfig.JAIL_BAIL_COST) {
            PendingBoardAction.JailDecision(BoardConfig.JAIL_BAIL_COST)
        } else {
            null
        }

        return snapshot.copy(
            currentTurnIndex = nextIdx,
            turnNumber = snapshot.turnNumber + 1,
            hasRolledThisTurn = false,
            grantExtraRollFromDoubles = false,
            pendingAction = nextPending,
            lastFortuneBanner = null
        )
    }
}
