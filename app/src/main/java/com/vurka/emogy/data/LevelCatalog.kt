package com.vurka.emogy.data

import com.vurka.emogy.model.LevelConfig

/** Visual + naming info for one themed world (50 levels each). */
data class WorldInfo(
    val index: Int,
    val name: String,
    val emoji: String,
    val colorA: Long,
    val colorB: Long,
    val categories: List<String>
)

object LevelCatalog {
    const val LEVELS_PER_WORLD = 50
    const val WORLD_COUNT = 20
    const val TOTAL_LEVELS = LEVELS_PER_WORLD * WORLD_COUNT // 1000

    private val worlds: List<WorldInfo> = listOf(
        WorldInfo(1, "Animal Kingdom", "🦁", 0xFFF59E0B, 0xFFEF4444, listOf("Animals")),
        WorldInfo(2, "Feast World", "🍕", 0xFFFB7185, 0xFFF97316, listOf("Food")),
        WorldInfo(3, "Nature Realm", "🌸", 0xFF34D399, 0xFF0EA5E9, listOf("Nature")),
        WorldInfo(4, "Emotion Isles", "😎", 0xFFFACC15, 0xFFEC4899, listOf("Faces")),
        WorldInfo(5, "Sports Arena", "⚽", 0xFF22C55E, 0xFF15803D, listOf("Sports")),
        WorldInfo(6, "Treasure Vault", "💎", 0xFF38BDF8, 0xFF6366F1, listOf("Symbols")),
        WorldInfo(7, "Wild Frontier", "🐺", 0xFF84CC16, 0xFF166534, listOf("Wild")),
        WorldInfo(8, "Sky Voyage", "✈️", 0xFF60A5FA, 0xFF2563EB, listOf("Travel")),
        WorldInfo(9, "Cosmic Harbor", "🌌", 0xFF8B5CF6, 0xFF1E1B4B, listOf("Space")),
        WorldInfo(10, "Dragon Realm", "🐉", 0xFFEF4444, 0xFF7C2D12, listOf("Fantasy")),
        WorldInfo(11, "Party Town", "🎉", 0xFFF472B6, 0xFFA855F7, listOf("Party")),
        WorldInfo(12, "Safari Run", "🦒", 0xFFFBBF24, 0xFF16A34A, listOf("Animals", "Wild")),
        WorldInfo(13, "Bistro Bazaar", "🍰", 0xFFFDA4AF, 0xFFE11D48, listOf("Food", "Party")),
        WorldInfo(14, "Enchanted Grove", "🧚", 0xFF2DD4BF, 0xFF7C3AED, listOf("Nature", "Fantasy")),
        WorldInfo(15, "Champions' Peak", "🏆", 0xFFFCD34D, 0xFFB45309, listOf("Faces", "Sports")),
        WorldInfo(16, "Galaxy Express", "🚀", 0xFF818CF8, 0xFF0F172A, listOf("Space", "Travel")),
        WorldInfo(17, "Mystic Vault", "🔮", 0xFFC084FC, 0xFF4C1D95, listOf("Symbols", "Fantasy")),
        WorldInfo(18, "Wonder Carnival", "🎡", 0xFFFB923C, 0xFFDB2777, listOf("Party", "Faces")),
        WorldInfo(19, "Grand Expedition", "🧭", 0xFF14B8A6, 0xFF1D4ED8, listOf("Wild", "Nature", "Travel")),
        WorldInfo(20, "Cosmic Finale", "👑", 0xFFFBBF24, 0xFFE11D48, emptyList()) // empty = every category
    )

    private val cachedLevels: List<LevelConfig> by lazy { generateLevels(TOTAL_LEVELS) }

    fun getLevel(levelNumber: Int): LevelConfig =
        cachedLevels[(levelNumber - 1).coerceIn(0, cachedLevels.size - 1)]

    fun getAllLevels(): List<LevelConfig> = cachedLevels

    fun getWorlds(): List<WorldInfo> = worlds

    fun getWorldInfo(levelNumber: Int): WorldInfo {
        val idx = ((levelNumber - 1).coerceAtLeast(0) / LEVELS_PER_WORLD).coerceAtMost(WORLD_COUNT - 1)
        return worlds[idx]
    }

    /** Themed emoji categories for the world containing [levelNumber]. */
    fun getWorldCategories(levelNumber: Int): List<String> {
        val cats = getWorldInfo(levelNumber).categories
        return cats.ifEmpty { EmojiCatalog.getAllCategories() }
    }

    fun getWorldName(levelNumber: Int): String = getWorldInfo(levelNumber).name

    /** Linear interpolation helper used by the difficulty curve. */
    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t.coerceIn(0f, 1f)

    private fun progress(level: Int, from: Int, to: Int): Float =
        (level - from).toFloat() / (to - from).toFloat()

    fun isBossLevel(levelNumber: Int): Boolean = levelNumber >= 25 && levelNumber % 25 == 0

    private fun generateLevels(totalLevels: Int): List<LevelConfig> {
        return (1..totalLevels).map { i ->
            val boss = isBossLevel(i)

            val basePairs = when {
                i <= 5 -> 8                               // 8 pairs = 16 cards (4x4 grid)
                i <= 15 -> 8 + ((i - 5) / 5)              // 8..10 pairs = 16..20 cards (4x4 to 4x5 grid)
                i <= 50 -> 10 + ((i - 15) / 18)           // 10..11 pairs = 20..22 cards (4x5 to 4x6 grid)
                i <= 150 -> 11 + ((i - 50) / 50)          // 11..13 pairs = 22..26 cards (5x5 to 5x6 grid)
                i <= 300 -> 13 + ((i - 150) / 75)         // 13..15 pairs = 26..30 cards (6x5 grid)
                i <= 600 -> 15 + ((i - 300) / 300)        // 15..16 pairs = 30..32 cards (6x6 grid)
                else -> 16 + ((i - 600) / 100)            // 16..20 pairs = 32..40 cards (6x6 grid)
            }
            val pairCount = (basePairs + if (boss) 2 else 0).coerceIn(3, 20)

            val difficulty = when {
                boss -> "Boss"
                i <= 20 -> "Easy"
                i <= 100 -> "Medium"
                i <= 300 -> "Hard"
                i <= 600 -> "Expert"
                else -> "Master"
            }

            // Shorter peek as levels climb (bigger decks get +1s so they stay fair).
            val previewBase = when {
                i <= 10 -> 5
                i <= 40 -> 4
                i <= 150 -> 3
                else -> 2
            }
            val bigDeckBonus = if (pairCount >= 14) 1 else 0
            val bossPenalty = if (boss) 1 else 0
            val previewTime = (previewBase + bigDeckBonus - bossPenalty).coerceAtLeast(2)

            // Seconds budget per pair shrinks smoothly: 11s -> 3.8s.
            val timeLimit = if (i <= 20) {
                0
            } else {
                val secPerPair = when {
                    i <= 100 -> lerp(11f, 8f, progress(i, 20, 100))
                    i <= 300 -> lerp(8f, 6f, progress(i, 100, 300))
                    i <= 600 -> lerp(6f, 4.6f, progress(i, 300, 600))
                    else -> lerp(4.6f, 3.8f, progress(i, 600, TOTAL_LEVELS))
                }
                val raw = pairCount * secPerPair + 10f
                (if (boss) raw * 0.9f else raw).toInt().coerceAtLeast(30)
            }

            // Move budget (a "move" is one pair of flips): 2.8x pairs -> 2.0x pairs.
            val moveLimit = if (i <= 40) {
                0
            } else {
                val factor = lerp(2.8f, 2.0f, progress(i, 40, TOTAL_LEVELS))
                val raw = kotlin.math.ceil(pairCount * factor).toInt()
                (if (boss) raw - 2 else raw).coerceAtLeast(pairCount + 5)
            }

            // Mid-game reshuffle of unmatched cards from level 100: every 45s -> every 18s.
            val shuffle = if (i < 100) {
                0
            } else {
                val base = lerp(45f, 18f, progress(i, 100, TOTAL_LEVELS)).toInt()
                if (boss) (base * 0.8f).toInt().coerceAtLeast(12) else base
            }

            // Hints become scarcer; bosses give none.
            val hints = when {
                boss -> 0
                i <= 20 -> -1
                i <= 100 -> 5
                i <= 300 -> 3
                i <= 600 -> 2
                else -> 1
            }

            // Wrong pairs flip back faster => less time to memorise them.
            val mismatchDelay = when {
                i <= 20 -> 1000
                i <= 100 -> 850
                i <= 300 -> 700
                i <= 600 -> 560
                else -> 460
            }

            // From level 200 a lone flipped card hides again: 4.5s -> 2.5s.
            val autoHide = if (i < 200) 0 else lerp(4500f, 2500f, progress(i, 200, TOTAL_LEVELS)).toInt()

            LevelConfig(
                levelNumber = i,
                pairCount = pairCount,
                categories = getWorldCategories(i),
                previewTimeSec = previewTime,
                timeLimitSec = timeLimit,
                moveLimit = moveLimit,
                difficultyLabel = difficulty,
                shuffleIntervalSec = shuffle,
                maxHints = hints,
                mismatchDelayMs = mismatchDelay,
                autoHideMs = autoHide,
                isBoss = boss
            )
        }
    }
}
