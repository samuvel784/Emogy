package com.vurka.emogy.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.vurka.emogy.ui.theme.LocalReduceMotion
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Full-screen gradient with constant (static) emojis in the background (no movement).
 */
@Composable
fun ConstantBackdrop(
    brush: Brush,
    emojis: List<String>,
    modifier: Modifier = Modifier,
    count: Int = 10,
    alpha: Float = 0.15f
) {
    val measurer = rememberTextMeasurer()
    val glyphs = remember(emojis) {
        emojis.map { measurer.measure(it, TextStyle(fontSize = 34.sp)) }
    }
    val positions = remember(count, emojis) {
        val r = Random(42)
        List(count) {
            Triple(
                r.nextFloat(), // x (0..1)
                r.nextFloat(), // y (0..1)
                (r.nextFloat() - 0.5f) * 40f // rotation
            )
        }
    }
    Canvas(modifier.fillMaxSize()) {
        drawRect(brush)
        if (glyphs.isEmpty()) return@Canvas
        positions.forEachIndexed { i, (x, y, rot) ->
            val g = glyphs[i % glyphs.size]
            val cx = x * size.width
            val cy = y * size.height
            rotate(rot, Offset(cx, cy)) {
                drawText(
                    g,
                    topLeft = Offset(cx - g.size.width / 2f, cy - g.size.height / 2f),
                    alpha = alpha
                )
            }
        }
    }
}

/**
 * Full-screen gradient with softly drifting emoji. All per-frame work happens in the draw phase
 * (the animated value is only read inside Canvas), so it never triggers recomposition.
 */
@Composable
fun AnimatedBackdrop(
    brush: Brush,
    emojis: List<String>,
    modifier: Modifier = Modifier,
    count: Int = 14,
    alpha: Float = 0.16f
) {
    val reduceMotion = LocalReduceMotion.current
    if (reduceMotion) {
        // Static gradient only: nothing moves when the system asks for reduced motion.
        Canvas(modifier.fillMaxSize()) { drawRect(brush) }
        return
    }
    val measurer = rememberTextMeasurer()
    val glyphs = remember(emojis) {
        emojis.map { measurer.measure(it, TextStyle(fontSize = 34.sp)) }
    }
    // x, y (0..1), speed, phase, size factor, rotation speed
    val seeds = remember(count) {
        val r = Random(7)
        List(count) { floatArrayOf(r.nextFloat(), r.nextFloat(), 0.4f + r.nextFloat(), r.nextFloat(), 0.7f + r.nextFloat() * 1.1f, (r.nextFloat() - 0.5f) * 40f) }
    }
    val t = rememberInfiniteTransition(label = "backdrop").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(28000, easing = LinearEasing)),
        label = "backdropT"
    )
    Canvas(modifier.fillMaxSize()) {
        drawRect(brush)
        if (glyphs.isEmpty()) return@Canvas
        val time = t.value
        seeds.forEachIndexed { i, s ->
            val g = glyphs[i % glyphs.size]
            val y = ((s[1] - time * s[2]) % 1f + 1f) % 1f           // drift upward & wrap
            val x = s[0] + 0.04f * sin((time * 2 * PI + s[3] * 6.28f).toFloat())
            val cx = x * size.width
            val cy = y * (size.height + 120f) - 60f
            rotate(s[5] * sin((time * 2 * PI + s[3] * 6.28f).toFloat()), Offset(cx, cy)) {
                drawText(g, topLeft = Offset(cx - g.size.width / 2f, cy - g.size.height / 2f), alpha = alpha * (0.6f + 0.4f * s[4].coerceAtMost(1f)))
            }
        }
    }
}

/** Springs the element in (scale + fade) after [delayMs]. */
fun Modifier.popIn(delayMs: Int = 0): Modifier = composed {
    if (LocalReduceMotion.current) return@composed this
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(delayMs.toLong())
        a.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 260f))
    }
    graphicsLayer {
        scaleX = a.value; scaleY = a.value
        alpha = a.value.coerceIn(0f, 1f)
    }
}

/** Gentle floating up/down loop. */
fun Modifier.bob(amplitudePx: Float = 10f, periodMs: Int = 2400, phase: Float = 0f): Modifier = composed {
    if (LocalReduceMotion.current) return@composed this
    val v = rememberInfiniteTransition(label = "bob").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(periodMs, easing = LinearEasing)),
        label = "bobV"
    )
    graphicsLayer { translationY = amplitudePx * sin(((v.value + phase) * 2 * PI).toFloat()) }
}

/** Soft heartbeat pulse for primary actions. */
fun Modifier.pulse(from: Float = 1f, to: Float = 1.05f, periodMs: Int = 1100): Modifier = composed {
    if (LocalReduceMotion.current) return@composed this
    val v = rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = from, targetValue = to,
        animationSpec = infiniteRepeatable(tween(periodMs, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulseV"
    )
    graphicsLayer { scaleX = v.value; scaleY = v.value }
}

/** Squishes while pressed, springs back on release. Doesn't consume the gesture. */
fun Modifier.pressBounce(): Modifier = composed {
    if (LocalReduceMotion.current) return@composed this
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 500f),
        label = "press"
    )
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                pressed = true
                waitForUpOrCancellation(PointerEventPass.Initial)
                pressed = false
            }
        }
}
