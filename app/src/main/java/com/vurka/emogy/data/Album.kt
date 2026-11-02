package com.vurka.emogy.data

import com.vurka.emogy.model.EmojiEntry

/** One album tile: the catalogue entry plus whether the player has matched & claimed it. */
data class AlbumEntryProgress(
    val entry: EmojiEntry,
    val isDiscovered: Boolean = false,
    val isClaimed: Boolean = false,
) {
    val isCollected: Boolean get() = isClaimed
    val isReadyToClaim: Boolean get() = isDiscovered && !isClaimed
}

/** The album section for one emoji category, in catalogue order. */
data class AlbumCategoryProgress(
    val name: String,
    val entries: List<AlbumEntryProgress>,
    val categoryIndex: Int = 0,
) {
    val collected: Int = entries.count { it.isCollected }
    val readyToClaimCount: Int = entries.count { it.isReadyToClaim }
    val total: Int = entries.size
    val fraction: Float = if (total == 0) 0f else collected.toFloat() / total
    val isComplete: Boolean = total > 0 && collected == total
    val rewardCoins: Int = 10 * (1 shl categoryIndex.coerceAtMost(20)) // 10, 20, 40, 80, 160, 320, 640...
}

/** Whole-album progress, already filtered to entries the device can draw. */
data class AlbumProgress(
    val categories: List<AlbumCategoryProgress>,
    val collected: Int,
    val readyToClaimCount: Int = 0,
    val total: Int,
) {
    val fraction: Float = if (total == 0) 0f else collected.toFloat() / total
    val isComplete: Boolean = total > 0 && collected == total
}

/**
 * Pure album maths, kept away from Compose and DataStore.
 *
 * Unknown ids in [collectedIds] are ignored, so a retired or unsupported emoji can never inflate
 * the counter. Category order follows the catalogue, which keeps the album stable between builds.
 */
object AlbumProgressCalculator {

    fun calculate(
        catalog: List<EmojiEntry>,
        collectedIds: Set<String>,
        claimedIds: Set<String> = collectedIds,
    ): AlbumProgress {
        val buckets = LinkedHashMap<String, MutableList<AlbumEntryProgress>>()
        catalog.distinctBy { it.id }.forEach { entry ->
            val discovered = entry.id in collectedIds
            val claimed = entry.id in claimedIds
            buckets.getOrPut(entry.category) { mutableListOf() } +=
                AlbumEntryProgress(
                    entry = entry,
                    isDiscovered = discovered,
                    isClaimed = discovered && claimed,
                )
        }

        var index = 0
        val categories = buckets.map { (name, entries) ->
            AlbumCategoryProgress(name = name, entries = entries, categoryIndex = index++)
        }

        return AlbumProgress(
            categories = categories,
            collected = categories.sumOf { it.collected },
            readyToClaimCount = categories.sumOf { it.readyToClaimCount },
            total = categories.sumOf { it.total },
        )
    }
}
