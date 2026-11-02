package com.vurka.emogy.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.runtime.withFrameNanos
import com.vurka.emogy.ui.theme.LocalReduceMotion
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

private class ConfettiPiece(
    val fromLeft: Boolean,
    val angle: Float,       // launch angle in radians, measured from straight up
    val speed: Float,       // in screen-heights per second
    val delaySec: Float,    // staggered launch (two cannon volleys)
    val lifeSec: Float,
    val size: Float,        // in dp-ish px multiplier
    val color: Color,
    val spin: Float,        // radians per second
    val phase: Float,
    val ribbon: Boolean
)

private val ConfettiColors = listOf(
    Color(0xFFF59E0B), Color(0xFF8B5CF6), Color(0xFF06B6D4), Color(0xFFEF4444),
    Color(0xFF10B981), Color(0xFFFFEB3B), Color(0xFFE91E63), Color(0xFF3B82F6)
)

/**
 * Physics based confetti: two cannons at the bottom corners fire two volleys, pieces fly up,
 * slow down with air drag, then flutter down under gravity while flipping in 3D and fading out.
 * The animation clock is only read inside the Canvas draw lambda, so nothing recomposes per frame.
 *
 * [colors] is the equipped victory palette; the default keeps the classic celebration.
 */
@Composable
fun ConfettiCelebration(
    modifier: Modifier = Modifier,
    colors: List<Color> = ConfettiColors,
) {
    // Confetti is pure decoration; skip it entirely under reduced motion.
    if (LocalReduceMotion.current) return
    val palette = colors.ifEmpty { ConfettiColors }
    val pieces = remember(palette) {
        val r = Random(System.nanoTime())
        List(150) { i ->
            val fromLeft = i % 2 == 0
            ConfettiPiece(
                fromLeft = fromLeft,
                angle = (0.12f + r.nextFloat() * 0.75f) * (if (fromLeft) 1f else -1f),
                speed = 0.75f + r.nextFloat() * 0.75f,
                delaySec = if (i < 90) r.nextFloat() * 0.15f else 0.7f + r.nextFloat() * 0.25f,
                lifeSec = 3.2f + r.nextFloat() * 1.3f,
                size = 0.7f + r.nextFloat() * 0.9f,
                color = palette[r.nextInt(palette.size)],
                spin = (r.nextFloat() - 0.5f) * 14f,
                phase = r.nextFloat() * 6.28f,
                ribbon = r.nextBoolean()
            )
        }
    }

    var clockMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            clockMs = (now - start) / 1_000_000L
            if (clockMs > 6000L) break
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val t = clockMs / 1000f
        val h = size.height
        val w = size.width
        val drag = 1.5f
        val gravity = 1.05f * h
        val unit = size.minDimension / 60f

        pieces.forEach { p ->
            val age = t - p.delaySec
            if (age <= 0f || age >= p.lifeSec) return@forEach

            val v = p.speed * h * 1.25f
            val vx = sin(p.angle) * v
            val vy = -cos(p.angle) * v
            val k = (1f - exp(-drag * age)) / drag      // integrated drag
            val x0 = if (p.fromLeft) -0.02f * w else 1.02f * w
            val y0 = h * 1.02f
            val sway = sin(age * 3f + p.phase) * unit * 2.2f * age.coerceAtMost(2f)
            val px = x0 + vx * k + sway
            val py = y0 + vy * k + 0.5f * gravity * age * age * 0.55f

            val fade = if (age > p.lifeSec - 0.8f) ((p.lifeSec - age) / 0.8f).coerceIn(0f, 1f) else 1f
            val flip = cos(age * 7f + p.phase)           // fake 3D flip by squashing the width
            val pw = p.size * unit * (if (p.ribbon) 1.1f else 2.2f)
            val ph = p.size * unit * (if (p.ribbon) 3.4f else 2.2f)

            rotate(degrees = (p.spin * age) * (180f / PI.toFloat()), pivot = Offset(px, py)) {
                drawRoundRect(
                    color = p.color.copy(alpha = 0.95f * fade),
                    topLeft = Offset(px - pw * kotlin.math.abs(flip) / 2f, py - ph / 2f),
                    size = Size(pw * kotlin.math.abs(flip).coerceAtLeast(0.15f), ph),
                    cornerRadius = CornerRadius(pw * 0.25f)
                )
            }
        }
    }
}
