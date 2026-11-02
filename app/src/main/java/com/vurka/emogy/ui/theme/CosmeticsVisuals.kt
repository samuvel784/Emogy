package com.vurka.emogy.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.vurka.emogy.data.store.CosmeticKind
import com.vurka.emogy.data.store.StoreCatalog

/** How a purchased card-face theme paints the revealed side of a card. */
data class CardFaceVisual(
    val gradient: Brush,
    val border: Color,
)

object CardFaceVisuals {

    fun forTheme(themeId: String, isDark: Boolean, matched: Boolean): CardFaceVisual = when (themeId) {
        "frosted_glass" -> CardFaceVisual(
            gradient = Brush.linearGradient(
                if (matched) {
                    listOf(Color(0xFF0F766E), Color(0xFF5EEAD4))
                } else {
                    listOf(Color(0xFF0E7490), Color(0xFF67E8F9))
                }
            ),
            border = Color(0xFFA5F3FC),
        )
        "neon_edge" -> CardFaceVisual(
            gradient = Brush.linearGradient(
                if (matched) listOf(Color(0xFF4C1D95), Color(0xFF22D3EE)) else listOf(Color(0xFF312E81), Color(0xFF7C3AED))
            ),
            border = Color(0xFF22D3EE),
        )
        "gilded_frame" -> CardFaceVisual(
            gradient = Brush.linearGradient(
                if (matched) listOf(Color(0xFF92400E), Color(0xFFFBBF24)) else listOf(Color(0xFF78350F), Color(0xFFF59E0B))
            ),
            border = Color(0xFFFDE68A),
        )
        else -> CardFaceVisual(
            gradient = Brush.linearGradient(
                when {
                    matched -> listOf(Color(0xFF10B981), Color(0xFF34D399))
                    isDark -> listOf(Color(0xFF24325B), Color(0xFF24325B))
                    else -> listOf(Color.White, Color.White)
                }
            ),
            border = if (matched) Color(0xFFA7F3D0) else RoyalBlue,
        )
    }
}

/** Full-board backdrop. The world gradient is the default; themed boards replace it. */
object BoardVisuals {

    fun forTheme(themeId: String, isDark: Boolean, fallback: Brush): Brush = when (themeId) {
        "board_aurora" -> Brush.verticalGradient(
            if (isDark) {
                listOf(Color(0xFF042F2E), Color(0xFF0F172A), Color(0xFF1E1B4B))
            } else {
                listOf(Color(0xFFCCFBF1), Color(0xFFE0F2FE), Color(0xFFEDE9FE))
            }
        )
        "board_ember" -> Brush.verticalGradient(
            if (isDark) {
                listOf(Color(0xFF1C1917), Color(0xFF431407), Color(0xFF0C0A09))
            } else {
                listOf(Color(0xFFFFF7ED), Color(0xFFFFEDD5), Color(0xFFFEF3C7))
            }
        )
        "board_abyss" -> Brush.verticalGradient(
            if (isDark) {
                listOf(Color(0xFF020617), Color(0xFF0C4A6E), Color(0xFF082F49))
            } else {
                listOf(Color(0xFFE0F2FE), Color(0xFFBAE6FD), Color(0xFFDDD6FE))
            }
        )
        else -> fallback
    }
}

/** Match sparkle colours. `sparkle_none` disables the overlay entirely. */
data class MatchSparkleVisual(
    val active: Boolean,
    val colors: List<Color>,
)

object MatchEffectVisuals {
    fun forId(id: String): MatchSparkleVisual = when (id) {
        "sparkle_gold" -> MatchSparkleVisual(
            active = true,
            colors = listOf(Color(0xFFFDE047), Color(0xFFFBBF24), Color(0xFFFFF7ED)),
        )
        "sparkle_cyan" -> MatchSparkleVisual(
            active = true,
            colors = listOf(Color(0xFF67E8F9), Color(0xFF22D3EE), Color(0xFFE0F2FE)),
        )
        "sparkle_prism" -> MatchSparkleVisual(
            active = true,
            colors = listOf(
                Color(0xFFF472B6), Color(0xFFFBBF24), Color(0xFF34D399),
                Color(0xFF60A5FA), Color(0xFFA78BFA),
            ),
        )
        else -> MatchSparkleVisual(active = false, colors = emptyList())
    }
}

/** Victory confetti palettes. */
object VictoryPalette {
    private val classic = listOf(
        Color(0xFFF59E0B), Color(0xFF8B5CF6), Color(0xFF06B6D4), Color(0xFFEF4444),
        Color(0xFF10B981), Color(0xFFFFEB3B), Color(0xFFE91E63), Color(0xFF3B82F6),
    )

    fun colorsFor(id: String): List<Color> = when (id) {
        "confetti_royal" -> listOf(
            Color(0xFFFBBF24), Color(0xFFFDE68A), Color(0xFF8B5CF6),
            Color(0xFFA78BFA), Color(0xFFFFF7ED), Color(0xFF6D28D9),
        )
        "confetti_aurora" -> listOf(
            Color(0xFF5EEAD4), Color(0xFF22D3EE), Color(0xFF3B82F6),
            Color(0xFFA78BFA), Color(0xFFECFEFF), Color(0xFF0EA5E9),
        )
        else -> classic
    }
}

/** Equipped cosmetic ids, resolved to the defaults when a saved id no longer exists. */
object EquippedCosmetics {

    fun effectiveCardBack(id: String): String = resolve(id, CosmeticKind.CARD_BACK, ThemeDefaults.cardBack)
    fun effectiveCardTheme(id: String): String = resolve(id, CosmeticKind.CARD_THEME, ThemeDefaults.cardTheme)
    fun effectiveBoard(id: String): String = resolve(id, CosmeticKind.BOARD_THEME, ThemeDefaults.board)
    fun effectiveMatch(id: String): String = resolve(id, CosmeticKind.MATCH_EFFECT, ThemeDefaults.match)
    fun effectiveVictory(id: String): String = resolve(id, CosmeticKind.VICTORY_EFFECT, ThemeDefaults.victory)

    private fun resolve(id: String, kind: CosmeticKind, fallback: String): String {
        val cosmetic = StoreCatalog.cosmetic(id)
        return if (cosmetic != null && cosmetic.kind == kind) id else fallback
    }

    private object ThemeDefaults {
        const val cardBack = "default"
        const val cardTheme = "classic"
        const val board = "board_default"
        const val match = "sparkle_none"
        const val victory = "confetti_classic"
    }
}
