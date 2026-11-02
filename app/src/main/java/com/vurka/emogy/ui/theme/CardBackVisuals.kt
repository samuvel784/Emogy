package com.vurka.emogy.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** Visual description of a card back: a gradient fill, an accent border and a symbol. */
data class CardBackVisual(
    val gradient: Brush,
    val border: Color,
    val symbol: String
)

/**
 * Single source of truth for how each unlockable card-back theme looks.
 * Shared by the in-game cards and the Themes preview screen.
 */
object CardBackVisuals {

    fun forTheme(themeId: String, isDark: Boolean): CardBackVisual = when (themeId) {
        "red_crimson" -> CardBackVisual(
            gradient = Brush.linearGradient(
                colors = if (isDark) {
                    listOf(Color(0xFF7F1D1D), Color(0xFFB91C1C))
                } else {
                    listOf(Color(0xFFDC2626), Color(0xFFF87171))
                }
            ),
            border = Color(0xFFFCA5A5),
            symbol = "🔥"
        )
        "royal_gold" -> CardBackVisual(
            gradient = Brush.linearGradient(
                colors = if (isDark) {
                    listOf(Color(0xFF4A3A0B), Color(0xFFA16207))
                } else {
                    listOf(Color(0xFFCA8A04), Color(0xFFFDE047))
                }
            ),
            border = Color(0xFFFBBF24),
            symbol = "👑"
        )
        "space_void" -> CardBackVisual(
            gradient = Brush.linearGradient(
                colors = if (isDark) {
                    listOf(Color(0xFF1E1B4B), Color(0xFF4C1D95))
                } else {
                    listOf(Color(0xFF6D28D9), Color(0xFFA78BFA))
                }
            ),
            border = Color(0xFFA78BFA),
            symbol = "🌌"
        )
        "aurora_mint" -> CardBackVisual(
            gradient = Brush.linearGradient(
                colors = if (isDark) listOf(Color(0xFF064E3B), Color(0xFF0D9488)) else listOf(Color(0xFF10B981), Color(0xFF5EEAD4))
            ),
            border = Color(0xFF99F6E4),
            symbol = "🍀"
        )
        "neon_candy" -> CardBackVisual(
            gradient = Brush.linearGradient(
                colors = if (isDark) listOf(Color(0xFF831843), Color(0xFF7C3AED)) else listOf(Color(0xFFEC4899), Color(0xFFA78BFA))
            ),
            border = Color(0xFFF9A8D4),
            symbol = "🍭"
        )
        "dragon_fire" -> CardBackVisual(
            gradient = Brush.linearGradient(
                colors = if (isDark) listOf(Color(0xFF450A0A), Color(0xFFEA580C)) else listOf(Color(0xFFDC2626), Color(0xFFFB923C))
            ),
            border = Color(0xFFFDBA74),
            symbol = "🐉"
        )
        "starlight_oath" -> CardBackVisual(
            gradient = Brush.linearGradient(
                colors = if (isDark) listOf(Color(0xFF0F172A), Color(0xFF312E81), Color(0xFF0891B2)) else listOf(Color(0xFF1E3A8A), Color(0xFF7C3AED), Color(0xFF22D3EE))
            ),
            border = Color(0xFFA5F3FC),
            symbol = "🌠"
        )
        "eclipse_sovereign" -> CardBackVisual(
            gradient = Brush.linearGradient(
                colors = if (isDark) listOf(Color(0xFF111827), Color(0xFF4C1D95), Color(0xFFB45309)) else listOf(Color(0xFF312E81), Color(0xFF7C3AED), Color(0xFFF59E0B))
            ),
            border = Color(0xFFFCD34D),
            symbol = "🌒"
        )
        else -> CardBackVisual(
            gradient = Brush.linearGradient(
                colors = if (isDark) {
                    listOf(Color(0xFF1E3A8A), Color(0xFF2563EB))
                } else {
                    listOf(Color(0xFF2563EB), Color(0xFF60A5FA))
                }
            ),
            border = VividRed,
            symbol = "❓"
        )
    }
}
