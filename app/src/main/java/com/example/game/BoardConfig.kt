package com.example.game

import com.example.data.model.BoardTile
import com.example.data.model.BuildingTier
import com.example.data.model.CaptainId
import com.example.data.model.ColorGroup
import com.example.data.model.PlayerState
import com.example.data.model.TileType
import kotlin.math.roundToInt

object BoardConfig {
    const val BOARD_SIZE = 28
    const val BASE_SALARY = 400
    const val JAIL_TILE_INDEX = 7
    const val JAIL_BAIL_COST = 200

    /**
     * 8x8 perimeter loop (28 tiles clockwise starting from bottom-right Start Harbor at (7,7)).
     */
    val tiles: List<BoardTile> = listOf(
        // Bottom edge (right to left: (7,7) -> (7,0))
        BoardTile(0, 7, 7, "ท่าเรือหลวง", "รับเงินเดือน +400G", TileType.START),
        BoardTile(1, 7, 6, "เกาะปะการัง", "Corals", TileType.ISLAND, ColorGroup.CORAL, basePrice = 160, baseToll = 30),
        BoardTile(2, 7, 5, "อ่าวเปลือกหอย", "Shell Bay", TileType.ISLAND, ColorGroup.CORAL, basePrice = 180, baseToll = 35),
        BoardTile(3, 7, 4, "สันดอนไข่มุก", "Pearl Reef", TileType.ISLAND, ColorGroup.CORAL, basePrice = 200, baseToll = 40),
        BoardTile(4, 7, 3, "หีบสมบัติ", "การ์ดดวง", TileType.FORTUNE),
        BoardTile(5, 7, 2, "อ่าวอำพัน", "Amber Cove", TileType.ISLAND, ColorGroup.AMBER, basePrice = 220, baseToll = 45),
        BoardTile(6, 7, 1, "หาดทรายทอง", "Golden Sand", TileType.ISLAND, ColorGroup.AMBER, basePrice = 240, baseToll = 50),
        BoardTile(7, 7, 0, "เกาะกะโหลก", "ติดเกาะร้าง", TileType.JAIL),

        // Left edge (bottom to top: (6,0) -> (1,0))
        BoardTile(8, 6, 0, "ท่าเรือเครื่องเทศ", "Spice Port", TileType.ISLAND, ColorGroup.AMBER, basePrice = 260, baseToll = 55),
        BoardTile(9, 5, 0, "ลากูนนางเงือก", "Siren Lagoon", TileType.ISLAND, ColorGroup.EMERALD, basePrice = 280, baseToll = 60),
        BoardTile(10, 4, 0, "น้ำตกมรกต", "Emerald Falls", TileType.ISLAND, ColorGroup.EMERALD, basePrice = 300, baseToll = 65),
        BoardTile(11, 3, 0, "ภาษีคราเคน", "จ่ายอสูรทะเล", TileType.KRAKEN_TAX),
        BoardTile(12, 2, 0, "ถ้ำคริสตัล", "Crystal Cave", TileType.ISLAND, ColorGroup.EMERALD, basePrice = 320, baseToll = 70),
        BoardTile(13, 1, 0, "ประภาคารไพลิน", "Sapphire Point", TileType.ISLAND, ColorGroup.SAPPHIRE, basePrice = 340, baseToll = 75),

        // Top edge (left to right: (0,0) -> (0,7))
        BoardTile(14, 0, 0, "โรงเตี๊ยมวัดใจ", "ทอยเหรียญ x8", TileType.TAVERN),
        BoardTile(15, 0, 1, "เรือผีสิง", "Ghost Galleon", TileType.ISLAND, ColorGroup.SAPPHIRE, basePrice = 360, baseToll = 80),
        BoardTile(16, 0, 2, "รังคราเคน", "Kraken Lair", TileType.ISLAND, ColorGroup.SAPPHIRE, basePrice = 380, baseToll = 85),
        BoardTile(17, 0, 3, "หุบเหวหมอก", "Mist Abyss", TileType.ISLAND, ColorGroup.AMETHYST, basePrice = 400, baseToll = 90),
        BoardTile(18, 0, 4, "หีบสมบัติ", "การ์ดดวง", TileType.FORTUNE),
        BoardTile(19, 0, 5, "นครลอยฟ้า", "Sky Citadel", TileType.ISLAND, ColorGroup.AMETHYST, basePrice = 420, baseToll = 95),
        BoardTile(20, 0, 6, "เกาะดารา", "Starfall Isle", TileType.ISLAND, ColorGroup.AMETHYST, basePrice = 440, baseToll = 100),
        BoardTile(21, 0, 7, "เทศกาลโจรสลัด", "คูณค่าผ่านทาง", TileType.FESTIVAL),

        // Right edge (top to bottom: (1,7) -> (6,7))
        BoardTile(22, 1, 7, "เกาะมังกรทะเล", "Leviathan Key", TileType.ISLAND, ColorGroup.GOLD, basePrice = 460, baseToll = 110),
        BoardTile(23, 2, 7, "วิหารโบราณ", "Ancient Temple", TileType.ISLAND, ColorGroup.GOLD, basePrice = 480, baseToll = 120),
        BoardTile(24, 3, 7, "คลังราชาโจรสลัด", "King's Vault", TileType.ISLAND, ColorGroup.GOLD, basePrice = 500, baseToll = 130),
        BoardTile(25, 4, 7, "น้ำวนมนตรา", "วาร์ปทุกช่อง", TileType.WHIRLPOOL),
        BoardTile(26, 5, 7, "แหลมดาบทอง", "Cutlass Cape", TileType.ISLAND, ColorGroup.GOLD, basePrice = 520, baseToll = 140),
        BoardTile(27, 6, 7, "อ่าวราชันย์", "Crown Harbor", TileType.ISLAND, ColorGroup.CORAL, basePrice = 560, baseToll = 150)
    )

    fun calculateBuildCost(tile: BoardTile, targetTier: BuildingTier, captain: CaptainId): Int {
        if (tile.type != TileType.ISLAND || targetTier == BuildingTier.NONE) return 0
        val raw = tile.basePrice * targetTier.buildCostRatio
        val discounted = if (captain == CaptainId.BARNABY) raw * 0.80 else raw
        return discounted.roundToInt()
    }

    fun calculateInvestedValue(tile: BoardTile, currentLevel: Int): Int {
        var total = tile.basePrice
        for (lvl in 1..currentLevel) {
            val tier = BuildingTier.fromLevel(lvl)
            total += (tile.basePrice * tier.buildCostRatio).roundToInt()
        }
        return total
    }

    fun calculateTakeoverCost(tile: BoardTile, currentLevel: Int, buyerCaptain: CaptainId): Int {
        val invested = calculateInvestedValue(tile, currentLevel)
        val baseTakeover = invested * 1.5
        val discounted = if (buyerCaptain == CaptainId.IGNIS) baseTakeover * 0.80 else baseTakeover
        return discounted.roundToInt()
    }

    fun calculateLiquidationValue(tile: BoardTile, currentLevel: Int): Int {
        return (calculateInvestedValue(tile, currentLevel) * 0.65).roundToInt()
    }

    fun hasColorMonopoly(ownerUid: String, group: ColorGroup?, tileOwners: List<String>): Boolean {
        if (group == null || ownerUid.isBlank()) return false
        val groupTiles = tiles.filter { it.colorGroup == group }
        return groupTiles.isNotEmpty() && groupTiles.all { tileOwners.getOrNull(it.index) == ownerUid }
    }

    fun calculateToll(
        tile: BoardTile,
        ownerUid: String,
        level: Int,
        festivalMultiplier: Int,
        tileOwners: List<String>
    ): Int {
        if (tile.type != TileType.ISLAND || ownerUid.isBlank()) return 0
        val tier = BuildingTier.fromLevel(level)
        var toll = tile.baseToll * tier.tollMultiplier
        if (hasColorMonopoly(ownerUid, tile.colorGroup, tileOwners)) {
            toll *= 1.5 // +50% color set bonus
        }
        toll *= festivalMultiplier.coerceIn(1, 4)
        return toll.roundToInt()
    }

    fun calculatePassStartSalary(player: PlayerState): Int {
        return if (player.captain == CaptainId.MARINA) {
            (BASE_SALARY * 1.25).roundToInt() // +25% bonus = 500G
        } else {
            BASE_SALARY
        }
    }
}
