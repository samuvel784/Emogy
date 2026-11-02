package com.vurka.emogy.game

import com.vurka.emogy.data.EmojiCatalog
import com.vurka.emogy.model.LevelConfig
import com.vurka.emogy.model.MemoryCard
import kotlin.random.Random

data class GameState(
    val cards: List<MemoryCard> = emptyList(),
    val firstSelectedCardId: Int? = null,
    val secondSelectedCardId: Int? = null,
    val moves: Int = 0,
    val mistakes: Int = 0,
    val matchedPairsCount: Int = 0,
    val totalPairs: Int = 0,
    val isPreviewing: Boolean = false,
    val isGameOver: Boolean = false,
    val isWon: Boolean = false,
    val isPaused: Boolean = false,
    val timeElapsedSec: Int = 0,
    val timeRemainingSec: Int = 0,
    val stars: Int = 0,
    val isHintOnCooldown: Boolean = false,
    val hintCooldownRemainingSec: Int = 0,
    /** Hints still available (-1 = unlimited). Purchased tokens are tracked separately. */
    val hintsLeft: Int = -1,
    /** Hints actually used this run, from the free allowance or from purchased tokens. */
    val hintsUsed: Int = 0,
    /** Consecutive successful matches. */
    val combo: Int = 0,
    val maxCombo: Int = 0,
    /** Seconds until the next reshuffle (0 when the level has no shuffle). */
    val shuffleCountdownSec: Int = 0,
    /** Incremented every time the unmatched cards are reshuffled (used to trigger UI effects). */
    val shuffleCount: Int = 0,
    /** Why the run ended in a loss: "time", "moves" or "". */
    val lossReason: String = "",
    /** Coins paid for this victory (0 on a replay of an already-cleared level). */
    val coinsAwarded: Int = 0,
    /** True when this victory cleared the level for the first time. */
    val isFirstClear: Boolean = false,
    /** True when this run set a new best time for the level. */
    val isPersonalBest: Boolean = false,
    /** True when this run was rebuilt from a saved snapshot. */
    val wasRestored: Boolean = false,
    /** The snapshot was saved while paused, so the run should come back paused. */
    val restoredPaused: Boolean = false,
    /** Cards currently revealed by Assist. Visual only and never persisted. */
    val assistCardIds: Set<Int> = emptySet(),
    /** Seconds until Assist can be used again (0 = ready). */
    val assistCooldownRemainingSec: Int = 0,
)

class MemoryGameEngine(private val levelConfig: LevelConfig, private val random: Random = Random.Default) {

    fun generateDeck(): List<MemoryCard> {
        val pool = EmojiCatalog.getPoolForCategories(
            categories = levelConfig.categories,
            requiredCount = levelConfig.pairCount,
            random = random
        )
        val selectedEmojis = pool.take(levelConfig.pairCount)

        val deck = mutableListOf<MemoryCard>()
        var idCounter = 0

        for (emojiEntry in selectedEmojis) {
            deck.add(MemoryCard(id = idCounter++, emojiId = emojiEntry.id, emoji = emojiEntry.emoji))
            deck.add(MemoryCard(id = idCounter++, emojiId = emojiEntry.id, emoji = emojiEntry.emoji))
        }

        return deck.shuffled(random)
    }

    /**
     * Swaps the board positions of every card that is not matched yet. Matched cards keep their
     * slots, so the board never "jumps" under the player's finger for pairs already solved.
     * Face-down state is forced so a reshuffle can never leak information.
     */
    fun shuffleUnmatched(cards: List<MemoryCard>): List<MemoryCard> {
        val slots = cards.indices.filter { !cards[it].isMatched }
        if (slots.size < 3) return cards
        val movable = slots.map { cards[it].copy(isFlipped = false) }.shuffled(random)
        val result = cards.toMutableList()
        slots.forEachIndexed { n, slot -> result[slot] = movable[n] }
        return result
    }

    /**
     * Calculates earned stars (1-3) based on completion time and number of mistakes.
     */
    fun calculateStars(totalPairs: Int, timeElapsedSec: Int, mistakes: Int, timeLimitSec: Int = 0): Int {
        val targetTimeSec = (totalPairs * 4).coerceAtLeast(10)
        val maxThreeStarMistakes = (totalPairs / 2).coerceAtLeast(1)
        val maxTwoStarMistakes = totalPairs

        val isFast = if (timeLimitSec > 0) {
            timeElapsedSec <= (timeLimitSec * 0.65).toInt()
        } else {
            timeElapsedSec <= targetTimeSec
        }

        val isModerateTime = if (timeLimitSec > 0) {
            timeElapsedSec <= (timeLimitSec * 0.85).toInt()
        } else {
            timeElapsedSec <= targetTimeSec * 2
        }

        return when {
            mistakes <= maxThreeStarMistakes && isFast -> 3
            mistakes <= maxTwoStarMistakes && isModerateTime -> 2
            else -> 1
        }
    }
}
