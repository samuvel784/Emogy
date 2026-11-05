package com.vurka.emogy.data.store

import com.vurka.emogy.data.login.LoginRules

enum class StoreCategory { HINTS, CARD_THEMES, CARD_BACKS, EFFECTS, BUNDLES }

enum class CosmeticKind { CARD_BACK, CARD_THEME, BOARD_THEME, MATCH_EFFECT, VICTORY_EFFECT }

/**
 * One collectible appearance. [price] is what the store charges; [milestoneLevel] additionally
 * unlocks it for free once that many levels have genuinely been completed.
 */
data class Cosmetic(
    val id: String,
    val kind: CosmeticKind,
    val name: String,
    val description: String,
    val price: Int = 0,
    val purchasable: Boolean = price > 0,
    val default: Boolean = false,
    val milestoneLevel: Int = 0,
    val rewardOnly: Boolean = false,
)

/** A purchasable entry. Ids are stable and never reused, so ownership survives renames. */
data class StoreItem(
    val id: String,
    val category: StoreCategory,
    val name: String,
    val description: String,
    val price: Int,
    val hints: Int = 0,
    val cosmetic: Cosmetic? = null,
    val effectGroup: String? = null,
    val symbol: String = "🪙",
)

/**
 * The whole shop, in one place. Every displayed price and quantity comes from here; the UI never
 * invents a number.
 */
object StoreCatalog {

    // ---------------------------------------------------------------- cosmetics

    val cardBacks: List<Cosmetic> = listOf(
        Cosmetic("default", CosmeticKind.CARD_BACK, "Classic Blue", "The original card back.", default = true),
        Cosmetic(
            "red_crimson", CosmeticKind.CARD_BACK, "Crimson Flame",
            "A fiery gradient with a warm ember border.", price = 1_500, milestoneLevel = 50,
        ),
        Cosmetic(
            "aurora_mint", CosmeticKind.CARD_BACK, "Aurora Mint",
            "Fresh mint and teal, like a polar sky.", price = 2_500, milestoneLevel = 100,
        ),
        Cosmetic(
            "royal_gold", CosmeticKind.CARD_BACK, "Royal Gold",
            "Deep gold with a crown accent.", price = 4_000, milestoneLevel = 200,
        ),
        Cosmetic(
            "neon_candy", CosmeticKind.CARD_BACK, "Neon Candy",
            "Bright pink and violet neon.", price = 5_500, milestoneLevel = 350,
        ),
        Cosmetic(
            "space_void", CosmeticKind.CARD_BACK, "Space Void",
            "A deep cosmic gradient from the outer rim.", price = 7_000, milestoneLevel = 500,
        ),
        Cosmetic(
            "dragon_fire", CosmeticKind.CARD_BACK, "Dragon Fire",
            "The legendary ember-and-flame back.", price = 10_000, milestoneLevel = 800,
        ),
        Cosmetic(
            LoginRules.STREAK_THEME_ID, CosmeticKind.CARD_BACK, "Starlight Oath",
            "Earned by checking in 30 days in a row.", rewardOnly = true,
        ),
        Cosmetic(
            "eclipse_sovereign", CosmeticKind.CARD_BACK, "Eclipse Sovereign",
            "Earned by claiming every monthly challenge in one cycle.", rewardOnly = true,
        ),
    )

    val cardThemes: List<Cosmetic> = listOf(
        Cosmetic("classic", CosmeticKind.CARD_THEME, "Classic Face", "The original card face.", default = true),
        Cosmetic(
            "frosted_glass", CosmeticKind.CARD_THEME, "Frosted Glass",
            "Cool translucent faces for revealed cards.", price = 2_500,
        ),
        Cosmetic(
            "neon_edge", CosmeticKind.CARD_THEME, "Neon Edge",
            "Violet faces with a bright cyan rim.", price = 3_500,
        ),
        Cosmetic(
            "gilded_frame", CosmeticKind.CARD_THEME, "Gilded Frame",
            "Warm gold faces with a hand-drawn frame.", price = 4_500,
        ),
    )

    val boardThemes: List<Cosmetic> = listOf(
        Cosmetic("board_default", CosmeticKind.BOARD_THEME, "World Backdrop", "The world's own colours.", default = true),
        Cosmetic(
            "board_aurora", CosmeticKind.BOARD_THEME, "Aurora Board",
            "Teal and indigo ribbons behind the deck.", price = 2_000,
        ),
        Cosmetic(
            "board_ember", CosmeticKind.BOARD_THEME, "Ember Board",
            "A slow amber glow for evening runs.", price = 3_500,
        ),
        Cosmetic(
            "board_abyss", CosmeticKind.BOARD_THEME, "Abyss Board",
            "Deep-sea blues with a cyan horizon.", price = 5_000,
        ),
    )

    val matchEffects: List<Cosmetic> = listOf(
        Cosmetic("sparkle_none", CosmeticKind.MATCH_EFFECT, "No Sparkle", "Clean, instant matches.", default = true),
        Cosmetic(
            "sparkle_gold", CosmeticKind.MATCH_EFFECT, "Golden Sparkle",
            "Warm sparks when a pair locks in.", price = 2_000,
        ),
        Cosmetic(
            "sparkle_cyan", CosmeticKind.MATCH_EFFECT, "Cyan Sparkle",
            "Cool cyan sparks on every match.", price = 2_500,
        ),
        Cosmetic(
            "sparkle_prism", CosmeticKind.MATCH_EFFECT, "Prism Sparkle",
            "A shifting rainbow burst.", price = 3_500,
        ),
    )

    val victoryEffects: List<Cosmetic> = listOf(
        Cosmetic("confetti_classic", CosmeticKind.VICTORY_EFFECT, "Classic Confetti", "The original victory shower.", default = true),
        Cosmetic(
            "confetti_royal", CosmeticKind.VICTORY_EFFECT, "Royal Confetti",
            "Gold and violet for a regal finish.", price = 2_500,
        ),
        Cosmetic(
            "confetti_aurora", CosmeticKind.VICTORY_EFFECT, "Aurora Confetti",
            "Mint, cyan and deep blue.", price = 3_500,
        ),
    )

    val cosmetics: List<Cosmetic> =
        cardBacks + cardThemes + boardThemes + matchEffects + victoryEffects

    private val cosmeticById: Map<String, Cosmetic> = cosmetics.associateBy { it.id }

    fun cosmetic(id: String): Cosmetic? = cosmeticById[id]

    // ---------------------------------------------------------------- consumables

    val hintPacks: List<StoreItem> = listOf(
        StoreItem(
            "hints_1", StoreCategory.HINTS, "1 Hint", "One hint for any level.", 50,
            hints = 1, symbol = "💡",
        ),
        StoreItem(
            "hints_5", StoreCategory.HINTS, "5 Hints", "Five hints (50 coins each).", 250,
            hints = 5, symbol = "💡",
        ),
        StoreItem(
            "hints_10", StoreCategory.HINTS, "10 Hints", "Ten extra hints.", 500,
            hints = 10, symbol = "💡",
        ),
        StoreItem(
            "hints_20", StoreCategory.HINTS, "20 Hints", "Twenty extra hints.", 1_000,
            hints = 20, symbol = "💡",
        ),
        StoreItem(
            "hints_40", StoreCategory.HINTS, "40 Hints", "Forty extra hints.", 2_000,
            hints = 40, symbol = "💡",
        ),
        StoreItem(
            "hints_50", StoreCategory.HINTS, "50 Hints", "Fifty extra hints.", 2_500,
            hints = 50, symbol = "💡",
        ),
        StoreItem(
            "hints_100", StoreCategory.HINTS, "100 Hints", "One hundred hints.", 5_000,
            hints = 100, symbol = "💡",
        ),
    )

    val bundles: List<StoreItem> = listOf(
        StoreItem(
            "bundle_40_hints", StoreCategory.BUNDLES, "40 Hints Bundle",
            "40 hints for 1,500 coins — save 500 coins!", 1_500,
            hints = 40, symbol = "🎁",
        ),
        StoreItem(
            "bundle_mystery_box", StoreCategory.BUNDLES, "Mystery Box",
            "Unpack hints, bonus coins & a random emoji!", 2_000,
            symbol = "📦",
        ),
    )

    /** Every purchasable store entry, including one entry per cosmetic. */
    val items: List<StoreItem> = hintPacks + bundles + cosmetics.filter { it.purchasable }.map { cosmetic ->
        StoreItem(
            id = cosmetic.id,
            category = when (cosmetic.kind) {
                CosmeticKind.CARD_BACK -> StoreCategory.CARD_BACKS
                CosmeticKind.CARD_THEME -> StoreCategory.CARD_THEMES
                CosmeticKind.BOARD_THEME,
                CosmeticKind.MATCH_EFFECT,
                CosmeticKind.VICTORY_EFFECT -> StoreCategory.EFFECTS
            },
            name = cosmetic.name,
            description = cosmetic.description,
            price = cosmetic.price,
            cosmetic = cosmetic,
            effectGroup = when (cosmetic.kind) {
                CosmeticKind.BOARD_THEME -> "Board backgrounds"
                CosmeticKind.MATCH_EFFECT -> "Match sparkle"
                CosmeticKind.VICTORY_EFFECT -> "Victory celebration"
                else -> null
            },
            symbol = when (cosmetic.kind) {
                CosmeticKind.CARD_BACK -> "🃏"
                CosmeticKind.CARD_THEME -> "🎴"
                CosmeticKind.BOARD_THEME -> "🖼️"
                CosmeticKind.MATCH_EFFECT -> "✨"
                CosmeticKind.VICTORY_EFFECT -> "🎉"
            },
        )
    }

    private val itemById: Map<String, StoreItem> = items.associateBy { it.id }

    fun item(id: String): StoreItem? = itemById[id]
}
