package com.example.game

import com.example.data.model.BoardTile
import com.example.data.model.BotDifficulty
import com.example.data.model.BotSlotSelection
import com.example.data.model.BuildingTier
import com.example.data.model.DiceMode
import com.example.data.model.PlayerState
import com.example.data.model.TileType
import kotlin.random.Random

object BotAiEngine {

    /**
     * Weighted random bot spawn per GDD weights:
     * Easy = 0.25 (~20.8%), Medium = 0.40 (~33.3%), Hard = 0.55 (~45.8%), total = 1.20.
     */
    fun resolveDifficulty(selection: BotSlotSelection, random: Random = Random): BotDifficulty {
        return when (selection) {
            BotSlotSelection.EASY -> BotDifficulty.EASY
            BotSlotSelection.MEDIUM -> BotDifficulty.MEDIUM
            BotSlotSelection.HARD -> BotDifficulty.HARD
            BotSlotSelection.AUTO_RANDOM -> {
                val roll = random.nextDouble() * 1.20
                when {
                    roll < 0.25 -> BotDifficulty.EASY
                    roll < 0.65 -> BotDifficulty.MEDIUM
                    else -> BotDifficulty.HARD
                }
            }
            BotSlotSelection.OFF -> BotDifficulty.MEDIUM
        }
    }

    fun chooseDiceMode(
        bot: PlayerState,
        players: List<PlayerState>,
        tileOwners: List<String>,
        tileLevels: List<Int>,
        tileFestivals: List<Int>
    ): DiceMode {
        if (bot.specialDiceCharges <= 0) return DiceMode.NORMAL
        return when (bot.botDifficulty) {
            BotDifficulty.EASY -> DiceMode.NORMAL
            BotDifficulty.MEDIUM -> {
                // Occasionally use odd/even to hit unowned islands
                if (Random.nextFloat() < 0.25f) {
                    if (Random.nextBoolean()) DiceMode.ODD else DiceMode.EVEN
                } else DiceMode.NORMAL
            }
            BotDifficulty.HARD -> {
                // Evaluate expected value of odd vs even sums (2..12)
                val oddScore = evaluateParityScore(bot, true, tileOwners, tileLevels, tileFestivals)
                val evenScore = evaluateParityScore(bot, false, tileOwners, tileLevels, tileFestivals)
                val best = maxOf(oddScore, evenScore)
                if (best > 40) {
                    if (oddScore >= evenScore) DiceMode.ODD else DiceMode.EVEN
                } else {
                    DiceMode.NORMAL
                }
            }
        }
    }

    private fun evaluateParityScore(
        bot: PlayerState,
        isOdd: Boolean,
        tileOwners: List<String>,
        tileLevels: List<Int>,
        tileFestivals: List<Int>
    ): Int {
        var score = 0
        for (sum in 2..12) {
            if ((sum % 2 == 1) != isOdd) continue
            val targetIdx = (bot.position + sum) % BoardConfig.BOARD_SIZE
            val tile = BoardConfig.tiles[targetIdx]
            val owner = tileOwners[targetIdx]
            when {
                tile.type == TileType.ISLAND && owner.isBlank() && bot.gold >= tile.basePrice -> score += 35
                tile.type == TileType.ISLAND && owner == bot.uid && tileLevels[targetIdx] < 4 -> score += 25
                tile.type == TileType.ISLAND && owner.isNotBlank() && owner != bot.uid -> {
                    val toll = BoardConfig.calculateToll(tile, owner, tileLevels[targetIdx], tileFestivals[targetIdx], tileOwners)
                    score -= toll / 10
                }
                tile.type == TileType.FESTIVAL || tile.type == TileType.WHIRLPOOL -> score += 20
                tile.type == TileType.JAIL || tile.type == TileType.KRAKEN_TAX -> score -= 30
            }
        }
        return score
    }

    fun shouldUseAngelCard(
        bot: PlayerState,
        tollDue: Int
    ): Boolean {
        if (bot.angelCards <= 0) return false
        return when (bot.botDifficulty) {
            BotDifficulty.EASY -> tollDue >= bot.gold // Only when desperate
            BotDifficulty.MEDIUM -> tollDue >= 180 || tollDue >= bot.gold / 2
            BotDifficulty.HARD -> tollDue >= 250 || tollDue >= (bot.gold * 0.40)
        }
    }

    fun chooseBuyTier(
        bot: PlayerState,
        tile: BoardTile,
        tileOwners: List<String>
    ): BuildingTier? {
        if (bot.gold < tile.basePrice) return null
        val remainingAfterLand = bot.gold - tile.basePrice

        return when (bot.botDifficulty) {
            BotDifficulty.EASY -> {
                // Random buy, cheap tier
                if (Random.nextFloat() < 0.70f) {
                    val campCost = BoardConfig.calculateBuildCost(tile, BuildingTier.CAMP, bot.captain)
                    if (remainingAfterLand >= campCost && Random.nextBoolean()) BuildingTier.CAMP else BuildingTier.NONE
                } else null
            }
            BotDifficulty.MEDIUM -> {
                // Prioritizes completing color set & building outpost
                val ownsSameColor = BoardConfig.tiles
                    .filter { it.colorGroup == tile.colorGroup && it.index != tile.index }
                    .any { tileOwners[it.index] == bot.uid }
                val outpostCost = BoardConfig.calculateBuildCost(tile, BuildingTier.OUTPOST, bot.captain)
                val campCost = BoardConfig.calculateBuildCost(tile, BuildingTier.CAMP, bot.captain)
                when {
                    ownsSameColor && remainingAfterLand >= outpostCost + 150 -> BuildingTier.OUTPOST
                    remainingAfterLand >= campCost + 100 -> BuildingTier.CAMP
                    else -> BuildingTier.NONE
                }
            }
            BotDifficulty.HARD -> {
                // ROI & monopoly blocking
                val castleCost = BoardConfig.calculateBuildCost(tile, BuildingTier.CASTLE, bot.captain)
                val outpostCost = BoardConfig.calculateBuildCost(tile, BuildingTier.OUTPOST, bot.captain)
                val campCost = BoardConfig.calculateBuildCost(tile, BuildingTier.CAMP, bot.captain)
                when {
                    remainingAfterLand >= castleCost + 250 -> BuildingTier.CASTLE
                    remainingAfterLand >= outpostCost + 200 -> BuildingTier.OUTPOST
                    remainingAfterLand >= campCost + 120 -> BuildingTier.CAMP
                    else -> BuildingTier.NONE
                }
            }
        }
    }

    fun shouldUpgradeOwnedTile(
        bot: PlayerState,
        tile: BoardTile,
        currentLevel: Int,
        tileOwners: List<String>
    ): BuildingTier? {
        if (currentLevel >= 4) return null
        val nextTier = BuildingTier.fromLevel(currentLevel + 1)
        val cost = BoardConfig.calculateBuildCost(tile, nextTier, bot.captain)
        if (bot.gold < cost) return null

        return when (bot.botDifficulty) {
            BotDifficulty.EASY -> if (bot.gold - cost >= 350 && Random.nextFloat() < 0.45f) nextTier else null
            BotDifficulty.MEDIUM -> if (bot.gold - cost >= 180) nextTier else null
            BotDifficulty.HARD -> {
                val hasSet = BoardConfig.hasColorMonopoly(bot.uid, tile.colorGroup, tileOwners)
                val buffer = if (nextTier == BuildingTier.LANDMARK || hasSet) 80 else 150
                if (bot.gold - cost >= buffer) nextTier else null
            }
        }
    }

    fun shouldTakeoverTile(
        bot: PlayerState,
        tile: BoardTile,
        currentLevel: Int,
        ownerUid: String,
        tileOwners: List<String>
    ): Boolean {
        if (currentLevel >= 4) return false // Landmark cannot be taken over
        val takeoverCost = BoardConfig.calculateTakeoverCost(tile, currentLevel, bot.captain)
        if (bot.gold < takeoverCost) return false

        return when (bot.botDifficulty) {
            BotDifficulty.EASY -> false
            BotDifficulty.MEDIUM -> {
                val ownsSameColor = BoardConfig.tiles
                    .filter { it.colorGroup == tile.colorGroup }
                    .any { tileOwners[it.index] == bot.uid }
                ownsSameColor && (bot.gold - takeoverCost >= 250)
            }
            BotDifficulty.HARD -> {
                val enemyMonopolyThreat = BoardConfig.tiles
                    .count { it.colorGroup == tile.colorGroup && tileOwners[it.index] == ownerUid } >= 2
                val reserve = if (enemyMonopolyThreat) 100 else 280
                (bot.gold - takeoverCost) >= reserve
            }
        }
    }

    fun chooseFestivalTileIndex(bot: PlayerState, tileOwners: List<String>, tileLevels: List<Int>): Int? {
        val owned = tileOwners.indices.filter { tileOwners[it] == bot.uid }
        if (owned.isEmpty()) return null
        return owned.maxByOrNull { idx ->
            BoardConfig.tiles[idx].baseToll * (tileLevels[idx] + 1)
        }
    }

    fun chooseWhirlpoolDestination(
        bot: PlayerState,
        tileOwners: List<String>,
        tileLevels: List<Int>
    ): Int {
        // Prefer highest value own island to upgrade to landmark, or best unowned island
        val upgradeable = tileOwners.indices.firstOrNull {
            tileOwners[it] == bot.uid && tileLevels[it] < 4
        }
        if (upgradeable != null) return upgradeable
        val unowned = BoardConfig.tiles.filter {
            it.type == TileType.ISLAND && tileOwners[it.index].isBlank() && bot.gold >= it.basePrice
        }
        return unowned.maxByOrNull { it.basePrice }?.index ?: 0
    }
}
