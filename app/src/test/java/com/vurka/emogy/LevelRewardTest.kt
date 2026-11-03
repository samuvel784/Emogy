package com.vurka.emogy

import com.vurka.emogy.data.LevelBest
import com.vurka.emogy.data.LevelRewardCalculator
import com.vurka.emogy.data.PurchaseCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LevelRewardTest {

    private val firstClear = LevelBest()

    @Test
    fun firstClearPaysTheFlatBonusPlusStars() {
        val result = LevelRewardCalculator.calculate(
            previous = firstClear,
            stars = 2,
            moves = 14,
            timeSec = 38,
        )

        assertTrue(result.isFirstClear)
        assertTrue(result.isPersonalBest)
        assertEquals(2, result.stars)
        assertEquals(14, result.bestMoves)
        assertEquals(38, result.bestTimeSec)
        assertEquals(15 + 5 * 2, result.coinsAwarded)
    }

    @Test
    fun replayPaysNothingAndKeepsBetterRecords() {
        val previous = LevelBest(stars = 3, moves = 10, timeSec = 40)

        val result = LevelRewardCalculator.calculate(
            previous = previous,
            stars = 1,
            moves = 20,
            timeSec = 60,
        )

        assertFalse(result.isFirstClear)
        assertFalse(result.isPersonalBest)
        assertEquals(0, result.coinsAwarded)
        assertEquals(3, result.stars)
        assertEquals(10, result.bestMoves)
        assertEquals(40, result.bestTimeSec)
    }

    @Test
    fun replayCanImproveOneRecordWithoutLosingAnother() {
        val previous = LevelBest(stars = 2, moves = 10, timeSec = 40)

        val result = LevelRewardCalculator.calculate(
            previous = previous,
            stars = 3,
            moves = 15,
            timeSec = 30,
        )

        assertTrue(result.isPersonalBest)
        assertEquals(3, result.stars)
        assertEquals(10, result.bestMoves)
        assertEquals(30, result.bestTimeSec)
        assertEquals(0, result.coinsAwarded)
    }

    @Test
    fun zeroStarsIsNotAClearAndPaysNothing() {
        val result = LevelRewardCalculator.calculate(
            previous = firstClear,
            stars = 0,
            moves = 5,
            timeSec = 9,
        )

        assertFalse(result.isFirstClear)
        assertEquals(0, result.coinsAwarded)
        assertEquals(0, result.stars)
    }

    @Test
    fun zeroStarRunCannotTurnALaterClearIntoAReplay() {
        LevelRewardCalculator.calculate(firstClear, stars = 0, moves = 5, timeSec = 9)
        val realClear = LevelRewardCalculator.calculate(
            previous = LevelBest(), // zero-star run did not persist a record
            stars = 1,
            moves = 8,
            timeSec = 20,
        )

        assertTrue(realClear.isFirstClear)
        assertEquals(20, realClear.coinsAwarded)
    }

    @Test
    fun starsAreClampedToThree() {
        val result = LevelRewardCalculator.calculate(
            previous = firstClear,
            stars = 9,
            moves = 4,
            timeSec = 12,
        )

        assertEquals(3, result.stars)
        assertEquals(15 + 5 * 3, result.coinsAwarded)
    }

    @Test
    fun negativeStarsAreClampedToZeroAndNeverReward() {
        val result = LevelRewardCalculator.calculate(
            previous = firstClear,
            stars = -5,
            moves = 4,
            timeSec = 12,
        )

        assertEquals(0, result.stars)
        assertEquals(0, result.coinsAwarded)
        assertFalse(result.isFirstClear)
    }
}

class PurchaseCalculatorTest {

    @Test
    fun insufficientCoinsDoesNotBuy() {
        val result = PurchaseCalculator.buy(coins = 40, cost = 120, alreadyOwned = false)

        assertFalse(result.success)
        assertEquals(40, result.remainingCoins)
    }

    @Test
    fun exactBalanceBuysAndLeavesZero() {
        val result = PurchaseCalculator.buy(coins = 120, cost = 120, alreadyOwned = false)

        assertTrue(result.success)
        assertEquals(0, result.remainingCoins)
    }

    @Test
    fun buyingAnOwnedItemSucceedsWithoutChargingAgain() {
        val result = PurchaseCalculator.buy(coins = 300, cost = 120, alreadyOwned = true)

        assertTrue(result.success)
        assertEquals(300, result.remainingCoins)
    }

    @Test
    fun negativeValuesCannotCreateCoins() {
        val result = PurchaseCalculator.buy(coins = -5, cost = -20, alreadyOwned = false)

        assertTrue(result.success)
        assertEquals(0, result.remainingCoins)
    }
}
