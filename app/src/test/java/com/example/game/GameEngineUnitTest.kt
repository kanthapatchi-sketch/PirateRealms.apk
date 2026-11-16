package com.example.game

import com.example.data.model.BotDifficulty
import com.example.data.model.BotSlotSelection
import com.example.data.model.BuildingTier
import com.example.data.model.CaptainId
import com.example.data.model.DiceMode
import com.example.data.model.PlayerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GameEngineUnitTest {

    @Test
    fun captainSkills_applyCorrectDiscountsAndBonuses() {
        val tile = BoardConfig.tiles[10] // basePrice = 300
        val normalBuild = BoardConfig.calculateBuildCost(tile, BuildingTier.OUTPOST, CaptainId.IGNIS)
        val barnabyBuild = BoardConfig.calculateBuildCost(tile, BuildingTier.OUTPOST, CaptainId.BARNABY)
        assertEquals((normalBuild * 0.80).toInt(), barnabyBuild)

        val normalTakeover = BoardConfig.calculateTakeoverCost(tile, 2, CaptainId.MARINA)
        val ignisTakeover = BoardConfig.calculateTakeoverCost(tile, 2, CaptainId.IGNIS)
        assertEquals((normalTakeover * 0.80).toInt(), ignisTakeover)

        val marinaPlayer = PlayerState(0, "u1", "Marina", CaptainId.MARINA, false)
        val ignisPlayer = PlayerState(1, "u2", "Ignis", CaptainId.IGNIS, false)
        assertEquals(500, BoardConfig.calculatePassStartSalary(marinaPlayer))
        assertEquals(400, BoardConfig.calculatePassStartSalary(ignisPlayer))
    }

    @Test
    fun shadowVane_has100PercentOddEvenAccuracy() {
        val vane = PlayerState(0, "u1", "Vane", CaptainId.VANE, false, specialDiceCharges = 5)
        val rng = Random(42)
        repeat(25) {
            val oddOutcome = GameEngine.rollDice(vane, DiceMode.ODD, rng)
            assertTrue(oddOutcome.modeSucceeded)
            assertEquals(1, oddOutcome.total % 2)

            val evenOutcome = GameEngine.rollDice(vane, DiceMode.EVEN, rng)
            assertTrue(evenOutcome.modeSucceeded)
            assertEquals(0, evenOutcome.total % 2)
        }
    }

    @Test
    fun landmarkTier4_cannotBeTakenOver_andLiquidationTriggersBankruptcy() {
        val p1 = PlayerState(0, "u1", "Alice", CaptainId.IGNIS, false, gold = 500)
        val p2 = PlayerState(1, "u2", "Bob", CaptainId.BARNABY, false, gold = 100)
        val owners = MutableList(BoardConfig.BOARD_SIZE) { "" }
        val levels = MutableList(BoardConfig.BOARD_SIZE) { 0 }
        owners[1] = "u1"
        levels[1] = 4 // Landmark!

        val snap = GameSnapshot(players = listOf(p1, p2), tileOwners = owners, tileLevels = levels)
        assertFalse(BotAiEngine.shouldTakeoverTile(p2, BoardConfig.tiles[1], 4, "u1", owners))

        // Force p2 to owe 500G with no islands -> bankruptcy & u1 wins!
        val afterDebt = GameEngine.applyGoldDebitWithLiquidation(snap, 1, 500, 0, "ค่าผ่านทาง")
        assertTrue(afterDebt.players[1].bankrupt)
        assertEquals("u1", afterDebt.winnerUid)
    }

    @Test
    fun botAutoRandom_matchesExpectedWeightDistribution() {
        val rng = Random(1234)
        val counts = mutableMapOf(
            BotDifficulty.EASY to 0,
            BotDifficulty.MEDIUM to 0,
            BotDifficulty.HARD to 0
        )
        repeat(1200) {
            val d = BotAiEngine.resolveDifficulty(BotSlotSelection.AUTO_RANDOM, rng)
            counts[d] = (counts[d] ?: 0) + 1
        }
        // Hard (~45.8%) > Medium (~33.3%) > Easy (~20.8%)
        assertTrue(counts[BotDifficulty.HARD]!! > counts[BotDifficulty.MEDIUM]!!)
        assertTrue(counts[BotDifficulty.MEDIUM]!! > counts[BotDifficulty.EASY]!!)
    }

    @Test
    fun stepByStepMovement_stepsOneTileAtATimeAndPaysSalaryOnWrap() {
        val startPos = BoardConfig.BOARD_SIZE - 2
        val p1 = PlayerState(0, "u1", "Ignis", CaptainId.IGNIS, false, position = startPos, gold = 1000)
        val p2 = PlayerState(1, "u2", "Marina", CaptainId.MARINA, true, position = 0, gold = 1000)
        val snap = GameSnapshot(players = listOf(p1, p2))

        val prepared = GameEngine.prepareDiceOutcome(snap, DiceMode.NORMAL, Random(7))
        assertFalse(prepared.stayedInJail)
        assertFalse(prepared.wentToJailOnTripleDoubles)
        assertEquals(prepared.outcome.total, prepared.stepsToWalk)

        var walking = prepared.snapshotBeforeSteps
        val visited = mutableListOf<Int>()
        repeat(prepared.stepsToWalk) {
            walking = GameEngine.stepPlayerOneTile(walking, 0)
            visited.add(walking.players[0].position)
        }

        // Verify exact sequential tile progression
        for (stepIdx in 1..prepared.stepsToWalk) {
            assertEquals((startPos + stepIdx) % BoardConfig.BOARD_SIZE, visited[stepIdx - 1])
        }

        // Passing Start Harbor (tile 0) during steps awards +400G salary
        if (startPos + prepared.stepsToWalk >= BoardConfig.BOARD_SIZE) {
            assertTrue(walking.players[0].gold >= 1400)
        }

        val resolved = GameEngine.finalizeStepMovementAndResolveLanding(
            snapshot = walking,
            playerIndex = 0,
            outcome = prepared.outcome,
            modeLabel = prepared.modeLabel,
            random = Random(7)
        )
        assertTrue(resolved.hasRolledThisTurn)
        assertEquals(prepared.outcome.die1, resolved.lastDice1)
        assertEquals(prepared.outcome.die2, resolved.lastDice2)
    }
}
