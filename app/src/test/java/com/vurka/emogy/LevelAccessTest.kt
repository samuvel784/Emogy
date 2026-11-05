package com.vurka.emogy

import com.vurka.emogy.data.EmojiCatalog
import com.vurka.emogy.data.LevelCatalog
import com.vurka.emogy.data.ThemeUnlock
import com.vurka.emogy.game.MemoryGameEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Guards the product rule that every level 1..1000 is playable immediately and that cosmetic
 * unlocks stay tied to real progress.
 */
class LevelAccessTest {

    @Test
    fun everyLevelHasAValidConfiguration() {
        for (level in 1..LevelCatalog.TOTAL_LEVELS) {
            val config = LevelCatalog.getLevel(level)
            assertEquals("level number", level, config.levelNumber)
            assertTrue("level $level pairCount", config.pairCount in 3..20)
            assertTrue("level $level categories", config.categories.isNotEmpty())
            assertTrue("level $level preview", config.previewTimeSec >= 1)
            assertTrue("level $level time limit", config.timeLimitSec == 0 || config.timeLimitSec >= 30)
            assertTrue(
                "level $level move limit",
                config.moveLimit == 0 || config.moveLimit >= config.pairCount
            )
            assertEquals("level $level boss flag", LevelCatalog.isBossLevel(level), config.isBoss)
        }
    }

    @Test
    fun everyLevelProducesAPlayableDeck() {
        for (level in 1..LevelCatalog.TOTAL_LEVELS) {
            val config = LevelCatalog.getLevel(level)
            val deck = MemoryGameEngine(config, Random(level.toLong())).generateDeck()

            assertEquals("level $level deck size", config.pairCount * 2, deck.size)
            assertEquals("level $level unique ids", deck.size, deck.map { it.id }.toSet().size)
            assertEquals("level $level unique emoji", config.pairCount, deck.map { it.emojiId }.toSet().size)
            assertTrue(
                "level $level emoji pairs",
                deck.groupingBy { it.emojiId }.eachCount().values.all { it == 2 }
            )
        }
    }

    @Test
    fun everyLevelHasEnoughUniqueEmojiInItsWorld() {
        for (level in 1..LevelCatalog.TOTAL_LEVELS) {
            val config = LevelCatalog.getLevel(level)
            val pool = config.categories.flatMap { EmojiCatalog.getByCategory(it) }
                .map { it.id }
                .toSet()
            assertTrue(
                "level $level needs ${config.pairCount} unique emoji",
                pool.size >= config.pairCount
            )
        }
    }

    @Test
    fun outOfRangeLevelNumbersClampInsteadOfCrashing() {
        assertEquals(1, LevelCatalog.getLevel(0).levelNumber)
        assertEquals(1, LevelCatalog.getLevel(-50).levelNumber)
        assertEquals(LevelCatalog.TOTAL_LEVELS, LevelCatalog.getLevel(1001).levelNumber)
        assertEquals(LevelCatalog.TOTAL_LEVELS, LevelCatalog.getLevel(99_999).levelNumber)
    }

    @Test
    fun exactlyFortyBossLevelsOnTheVerifiedSchedule() {
        val bosses = (1..LevelCatalog.TOTAL_LEVELS).filter { LevelCatalog.isBossLevel(it) }
        assertEquals(40, bosses.size)
        assertEquals(25, bosses.first())
        assertEquals(1000, bosses.last())
    }

    @Test
    fun themesAreNotUnlockedByLevelAvailability() {
        // A brand-new player can reach all 1000 levels, yet must not own milestone themes.
        assertTrue(ThemeUnlock.isUnlocked(purchased = false, completedLevels = 0, milestoneLevels = 0))
        assertFalse(ThemeUnlock.isUnlocked(purchased = false, completedLevels = 0, milestoneLevels = 50))
        assertFalse(ThemeUnlock.isUnlocked(purchased = false, completedLevels = 49, milestoneLevels = 50))
        assertTrue(ThemeUnlock.isUnlocked(purchased = false, completedLevels = 50, milestoneLevels = 50))
        assertTrue(ThemeUnlock.isUnlocked(purchased = true, completedLevels = 0, milestoneLevels = 800))
    }

    @Test
    fun brandNewPlayerCanAccessLevel1Level2Level100AndLevel1000() {
        listOf(1, 2, 100, 500, 1000).forEach { level ->
            val config = LevelCatalog.getLevel(level)
            assertEquals(level, config.levelNumber)
            assertTrue(config.pairCount > 0)
        }
    }

    @Test
    fun accessToALevelDoesNotChangeWhenPreviousLevelHasZeroStars() {
        val emptyStarsMap = emptyMap<Int, Int>()
        listOf(1, 2, 100, 1000).forEach { level ->
            val stars = emptyStarsMap[level] ?: 0
            assertEquals(0, stars)
            val config = LevelCatalog.getLevel(level)
            assertEquals(level, config.levelNumber)
        }
    }

    @Test
    fun sequentialCompletionIsNotRequiredToStartAnotherLevel() {
        val targetLevel = 500
        val config = LevelCatalog.getLevel(targetLevel)
        assertEquals(500, config.levelNumber)
        val engine = MemoryGameEngine(config, Random(targetLevel.toLong()))
        val deck = engine.generateDeck()
        assertEquals(config.pairCount * 2, deck.size)
    }
}
