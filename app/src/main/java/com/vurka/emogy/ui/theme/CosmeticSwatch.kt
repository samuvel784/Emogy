package com.vurka.emogy.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vurka.emogy.data.store.Cosmetic
import com.vurka.emogy.data.store.CosmeticKind

/**
 * Live preview of any cosmetic. Shared by the store and the cosmetics screen, so a purchased look
 * is drawn with exactly the same code the game uses.
 */
@Composable
fun CosmeticSwatch(
    cosmetic: Cosmetic,
    isDark: Boolean,
    size: Dp = 48.dp,
) {
    val shape = RoundedCornerShape(size / 4)
    when (cosmetic.kind) {
        CosmeticKind.CARD_BACK -> {
            val visual = CardBackVisuals.forTheme(cosmetic.id, isDark)
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(shape)
                    .background(visual.gradient)
                    .border(BorderStroke(2.dp, visual.border), shape),
                contentAlignment = Alignment.Center,
            ) {
                Text(visual.symbol, fontSize = (size.value * 0.42f).sp)
            }
        }
        CosmeticKind.CARD_THEME -> {
            val visual = CardFaceVisuals.forTheme(cosmetic.id, isDark, matched = false)
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(shape)
                    .background(visual.gradient)
                    .border(BorderStroke(2.dp, visual.border), shape),
                contentAlignment = Alignment.Center,
            ) {
                Text("😀", fontSize = (size.value * 0.42f).sp)
            }
        }
        CosmeticKind.BOARD_THEME -> {
            val brush = BoardVisuals.forTheme(
                cosmetic.id,
                isDark,
                Brush.verticalGradient(listOf(Color(0xFF334155), Color(0xFF0F172A))),
            )
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(shape)
                    .background(brush)
                    .border(BorderStroke(2.dp, RoyalBlue), shape),
                contentAlignment = Alignment.Center,
            ) {
                Text("🖼️", fontSize = (size.value * 0.42f).sp)
            }
        }
        CosmeticKind.MATCH_EFFECT -> {
            val visual = MatchEffectVisuals.forId(cosmetic.id)
            ColorDots(colors = visual.colors.ifEmpty { listOf(Color(0xFF475569)) }, size = size)
        }
        CosmeticKind.VICTORY_EFFECT -> {
            ColorDots(colors = VictoryPalette.colorsFor(cosmetic.id).take(6), size = size)
        }
    }
}

@Composable
private fun ColorDots(colors: List<Color>, size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size / 4))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(size / 6),
        contentAlignment = Alignment.Center,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            colors.take(4).forEach { color ->
                Box(
                    Modifier
                        .size(size / 7)
                        .clip(CircleShape)
                        .background(color),
                )
            }
        }
    }
}
