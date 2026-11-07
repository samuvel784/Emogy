package com.vurka.emogy.ui.levels

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAbsoluteAlignment
import androidx.compose.ui.Modifier
import com.vurka.emogy.ui.theme.LocalReduceMotion
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vurka.emogy.R
import com.vurka.emogy.data.EmojiCatalog
import com.vurka.emogy.data.LevelCatalog
import com.vurka.emogy.data.WorldInfo
import com.vurka.emogy.model.LevelConfig
import com.vurka.emogy.ui.components.ConstantBackdrop
import com.vurka.emogy.ui.components.bob
import com.vurka.emogy.ui.components.pressBounce
import com.vurka.emogy.ui.components.pulse
import com.vurka.emogy.util.MusicManager
import com.vurka.emogy.util.SoundManager
import com.vurka.emogy.util.WorldMusic
import com.vurka.emogy.viewmodel.LevelSelectionViewModel
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sin

// ---------------------------------------------------------------------------------------------
// Map model: [banner(world 1), level 1..50, banner(world 2), level 51..100, ... , summit]
// The list is displayed with reverseLayout, so index 0 is at the bottom and the path climbs up.
// ---------------------------------------------------------------------------------------------

private sealed interface MapRow {
    val worldIndex: Int

    data class Banner(val world: WorldInfo) : MapRow {
        override val worldIndex: Int get() = world.index
    }

    data class Node(val config: LevelConfig, val world: WorldInfo) : MapRow {
        override val worldIndex: Int get() = world.index
    }

    data class Summit(override val worldIndex: Int) : MapRow
}

private val NodeRowHeight = 96.dp
private val BannerRowHeight = 150.dp
private val SummitRowHeight = 220.dp

private val mapRows: List<MapRow> by lazy {
    buildList {
        val levels = LevelCatalog.getAllLevels()
        LevelCatalog.getWorlds().forEach { w ->
            add(MapRow.Banner(w))
            val start = (w.index - 1) * LevelCatalog.LEVELS_PER_WORLD
            for (i in start until start + LevelCatalog.LEVELS_PER_WORLD) add(MapRow.Node(levels[i], w))
        }
        add(MapRow.Summit(LevelCatalog.WORLD_COUNT))
    }
}

/** Horizontal position (-1..1) of each row's path point. Banners and summit sit in the centre. */
private val rowBiases: FloatArray by lazy {
    FloatArray(mapRows.size) { i ->
        when (val row = mapRows[i]) {
            is MapRow.Node -> 0.66f * sin(row.config.levelNumber * 0.62f)
            else -> 0f
        }
    }
}

private val Gold = Color(0xFFFDE047)

@Composable
fun LevelSelectScreen(
    viewModel: LevelSelectionViewModel,
    onLevelSelected: (Int) -> Unit
) {
    val lastPlayed by viewModel.lastPlayedLevel.collectAsStateWithLifecycle()
    val starsMap by viewModel.starsMap.collectAsStateWithLifecycle()
    val totalStars by viewModel.totalStars.collectAsStateWithLifecycle()
    val soundOn by viewModel.soundEnabled.collectAsStateWithLifecycle()
    val hintTokens by viewModel.hintTokens.collectAsStateWithLifecycle()

    val rows = mapRows
    val worlds = remember { LevelCatalog.getWorlds() }
    // 0 means "not loaded yet"; the map waits for a real value before positioning itself.
    val displayLevel = if (lastPlayed in 1..LevelCatalog.TOTAL_LEVELS) lastPlayed else 1
    val currentIndex = remember(displayLevel) {
        rows.indexOfFirst { it is MapRow.Node && it.config.levelNumber == displayLevel }.coerceAtLeast(0)
    }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var selectedLevel by rememberSaveable { mutableIntStateOf(0) }
    var showJumpDialog by rememberSaveable { mutableStateOf(false) }

    // Position the map on the last played level exactly once per visit. Returning from a game must
    // keep whatever position the player had scrolled to, not yank them back.
    var initialScrollDone by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(displayLevel) {
        if (!initialScrollDone && lastPlayed in 1..LevelCatalog.TOTAL_LEVELS) {
            initialScrollDone = true
            listState.scrollToItem((currentIndex - 3).coerceAtLeast(0))
        }
    }

    // The world that is roughly in the middle of the screen drives the background and the title chip.
    val visibleWorldIdx by remember {
        derivedStateOf { rows[(listState.firstVisibleItemIndex + 4).coerceIn(0, rows.lastIndex)].worldIndex }
    }
    val world = worlds[(visibleWorldIdx - 1).coerceIn(0, worlds.lastIndex)]

    // Scrolling into a new region changes the ambience.
    LaunchedEffect(world.index) {
        MusicManager.setGroup(WorldMusic.groupForWorld(world.index))
    }

    val showJump by remember(currentIndex) {
        derivedStateOf { abs(listState.firstVisibleItemIndex + 4 - currentIndex) > 7 }
    }

    fun scrollToLevel(level: Int) {
        val target = rows.indexOfFirst { it is MapRow.Node && it.config.levelNumber == level }
        if (target >= 0) scope.launch { listState.animateScrollToItem((target - 3).coerceAtLeast(0)) }
    }

    fun scrollToWorld(worldIndex: Int) {
        val target = rows.indexOfFirst { it is MapRow.Banner && it.world.index == worldIndex }
        if (target >= 0) scope.launch { listState.animateScrollToItem((target - 1).coerceAtLeast(0)) }
    }

    val deepNavy = Color(0xFF0B132B)
    val topColor by animateColorAsState(lerp(deepNavy, Color(world.colorB), 0.55f), tween(700), label = "mapTop")
    val bottomColor by animateColorAsState(lerp(deepNavy, Color(world.colorA), 0.30f), tween(700), label = "mapBottom")
    val backdropEmojis = remember(world.index) {
        world.categories.ifEmpty { EmojiCatalog.getAllCategories() }
            .flatMap { EmojiCatalog.getByCategory(it) }.take(10).map { it.emoji }
            .ifEmpty { listOf("⭐", "✨") }
    }

    val insets = WindowInsets.systemBars.asPaddingValues()

    Box(
        Modifier
            .fillMaxSize()
            .drawBehind { drawRect(Brush.verticalGradient(listOf(topColor, bottomColor))) }
    ) {
        ConstantBackdrop(brush = SolidColor(Color.Transparent), emojis = backdropEmojis, count = 10, alpha = 0.12f)

        LazyColumn(
            state = listState,
            reverseLayout = true,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = insets.calculateTopPadding() + 156.dp,
                bottom = 24.dp
            )
        ) {
            itemsIndexed(
                items = rows,
                key = { _, row ->
                    when (row) {
                        is MapRow.Banner -> "b${row.world.index}"
                        is MapRow.Node -> "n${row.config.levelNumber}"
                        is MapRow.Summit -> "summit"
                    }
                },
                contentType = { _, row ->
                    when (row) {
                        is MapRow.Banner -> 0
                        is MapRow.Node -> 1
                        is MapRow.Summit -> 2
                    }
                }
            ) { index, row ->
                when (row) {
                    is MapRow.Banner -> WorldBannerRow(
                        world = row.world,
                        isFirst = index == 0,
                        starsMap = starsMap
                    )

                    is MapRow.Node -> {
                        val level = row.config.levelNumber
                        val stars = starsMap[level] ?: 0
                        val isUnlocked = level == 1 || (starsMap[level - 1] ?: 0) > 0 || stars > 0
                        val context = LocalContext.current
                        MapNodeRow(
                            level = row.config,
                            world = row.world,
                            stars = stars,
                            isUnlocked = isUnlocked,
                            isCurrent = level == displayLevel,
                            bias = rowBiases[index],
                            biasPrev = rowBiases[(index - 1).coerceAtLeast(0)],
                            biasNext = rowBiases[(index + 1).coerceAtMost(rows.lastIndex)],
                            prevDone = (starsMap[level - 1] ?: 0) > 0 || stars > 0 || level == 1,
                            nextDone = (starsMap[level + 1] ?: 0) > 0,
                            onClick = {
                                SoundManager.playTap(soundOn)
                                if (isUnlocked) {
                                    selectedLevel = level
                                } else {
                                    Toast.makeText(context, "Complete previous level to unlock!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }

                    is MapRow.Summit -> SummitRow(reached = (starsMap[LevelCatalog.TOTAL_LEVELS] ?: 0) > 0)
                }
            }
        }

        // ---------- Top bar ----------
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(listOf(deepNavy.copy(alpha = 0.97f), deepNavy.copy(alpha = 0.8f), Color.Transparent))
                )
                .statusBarsPadding()
                .padding(bottom = 10.dp)
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.map_title), fontWeight = FontWeight.ExtraBold, color = Color.White, fontSize = 20.sp)
                    Text(
                        stringResource(R.string.map_subtitle, LevelCatalog.TOTAL_LEVELS, displayLevel),
                        fontSize = 12.sp,
                        color = Color(0xFFCBD5E1)
                    )
                }
                Text(
                    stringResource(R.string.map_stars, totalStars),
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier
                        .padding(end = 14.dp)
                        .background(Color.White.copy(alpha = 0.14f), RoundedCornerShape(50))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            // World selector: tap any world to fly straight to its banner.
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("world_selector"),
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item(key = "jump") {
                    WorldChip(
                        emoji = "🔎",
                        label = stringResource(R.string.map_jump),
                        selected = false,
                        accent = Color(0xFFE11D48)
                    ) { showJumpDialog = true }
                }
                itemsIndexed(worlds, key = { _, w -> "w${w.index}" }) { _, w ->
                    WorldChip(
                        emoji = w.emoji,
                        label = "${w.index}",
                        selected = w.index == world.index,
                        accent = Color(w.colorA)
                    ) { scrollToWorld(w.index) }
                }
            }
        }

        // ---------- "Jump to my level" ----------
        AnimatedVisibility(
            visible = showJump,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut()
        ) {
            Row(
                modifier = Modifier
                    .shadow(8.dp, RoundedCornerShape(50))
                    .clip(RoundedCornerShape(50))
                    .background(Brush.horizontalGradient(listOf(Color(0xFFE11D48), Color(0xFFFF7A45))))
                    .clickable {
                        scope.launch { listState.animateScrollToItem((currentIndex - 3).coerceAtLeast(0)) }
                    }
                    .testTag("jump_to_current")
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.map_current_level, displayLevel), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
            }
        }
    }

    if (selectedLevel > 0) {
        val config = LevelCatalog.getLevel(selectedLevel)
        val bestMoves by remember(selectedLevel) { viewModel.getBestMoves(selectedLevel) }
            .collectAsStateWithLifecycle(initialValue = 0)
        val bestTime by remember(selectedLevel) { viewModel.getBestTime(selectedLevel) }
            .collectAsStateWithLifecycle(initialValue = 0)
        LevelPreviewDialog(
            config = config,
            world = LevelCatalog.getWorldInfo(selectedLevel),
            stars = starsMap[selectedLevel] ?: 0,
            bestMoves = bestMoves,
            bestTime = bestTime,
            zen = false,
            hintTokens = hintTokens,
            onPlay = {
                SoundManager.playTap(soundOn)
                onLevelSelected(selectedLevel)
            },
            onDismiss = { selectedLevel = 0 }
        )
    }

    if (showJumpDialog) {
        JumpToLevelDialog(
            currentLevel = displayLevel,
            onGo = { level ->
                showJumpDialog = false
                scrollToLevel(level)
                selectedLevel = level
            },
            onDismiss = { showJumpDialog = false }
        )
    }
}

/**
 * Numeric jump dialog. Accepts 1..1000 only; invalid input shows an inline error instead of
 * loading an unrelated level.
 */
@Composable
private fun JumpToLevelDialog(
    currentLevel: Int,
    onGo: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    var errorKind by rememberSaveable { mutableStateOf<String?>(null) }

    fun submit(raw: String? = null) {
        val value = (raw ?: text).trim().toIntOrNull()
        if (value == null) {
            errorKind = "number"
            return
        }
        if (value !in 1..LevelCatalog.TOTAL_LEVELS) {
            errorKind = "range"
            return
        }
        errorKind = null
        onGo(value)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringResource(R.string.map_jump_title), fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.map_every_open, LevelCatalog.TOTAL_LEVELS), fontSize = 13.sp)
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it.filter { ch -> ch.isDigit() }.take(4)
                        errorKind = null
                    },
                    label = { Text(stringResource(R.string.map_level_number)) },
                    singleLine = true,
                    isError = errorKind != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("jump_level_field")
                )
                if (errorKind != null) {
                    Text(
                        if (errorKind == "number") {
                            stringResource(R.string.map_error_number)
                        } else {
                            stringResource(R.string.map_error_range, LevelCatalog.TOTAL_LEVELS)
                        },
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp
                    )
                }
                Text(stringResource(R.string.map_quick), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(1, 250, 500, 750, LevelCatalog.TOTAL_LEVELS).forEach { level ->
                        AssistChip(
                            onClick = { onGo(level) },
                            label = { Text("$level", fontSize = 12.sp) },
                            modifier = Modifier.testTag("jump_quick_$level")
                        )
                    }
                    AssistChip(
                        onClick = { submit(currentLevel.toString()) },
                        label = { Text(stringResource(R.string.map_last_played, currentLevel), fontSize = 12.sp) }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { submit() },
                modifier = Modifier.testTag("jump_go_button"),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE11D48))
            ) {
                Text(stringResource(R.string.action_go), color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@Composable
private fun WorldChip(
    emoji: String,
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    val bg = if (selected) accent else Color.White.copy(alpha = 0.10f)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .border(
                BorderStroke(1.dp, if (selected) Color.White else Color.White.copy(alpha = 0.25f)),
                RoundedCornerShape(50)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(emoji, fontSize = 13.sp)
        Spacer(Modifier.width(4.dp))
        Text(
            label,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold
        )
    }
}

private fun firstLevelOf(world: WorldInfo): Int = (world.index - 1) * LevelCatalog.LEVELS_PER_WORLD + 1

// ---------------------------------------------------------------------------------------------
// Path drawing
// ---------------------------------------------------------------------------------------------

private fun DrawScope.pathX(bias: Float, nodePx: Float): Float =
    (size.width - nodePx) / 2f * (1f + bias) + nodePx / 2f

/** S-curve from the row's path point to the middle of the row's top or bottom edge. */
private fun DrawScope.trailPath(fromX: Float, fromY: Float, toX: Float, toY: Float): Path {
    val length = toY - fromY
    return Path().apply {
        moveTo(fromX, fromY)
        cubicTo(fromX, fromY + length * 0.45f, toX, toY - length * 0.45f, toX, toY)
    }
}

private fun DrawScope.drawTrail(path: Path, done: Boolean, world: WorldInfo) {
    val track = if (done) Color(world.colorA).copy(alpha = 0.55f) else Color.Black.copy(alpha = 0.22f)
    drawPath(path, track, style = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round))
    if (done) {
        drawPath(path, Gold, style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round))
    } else {
        drawPath(
            path,
            Color.White.copy(alpha = 0.45f),
            style = Stroke(
                width = 3.dp.toPx(),
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 18f))
            )
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Rows
// ---------------------------------------------------------------------------------------------

@Composable
private fun MapNodeRow(
    level: LevelConfig,
    world: WorldInfo,
    stars: Int,
    isUnlocked: Boolean,
    isCurrent: Boolean,
    bias: Float,
    biasPrev: Float,
    biasNext: Float,
    prevDone: Boolean,
    nextDone: Boolean,
    onClick: () -> Unit
) {
    val boss = level.isBoss
    val nodeSize: Dp = if (boss) 76.dp else 62.dp
    val shape: Shape = if (boss) RoundedCornerShape(26.dp) else CircleShape
    val colorA = Color(world.colorA)
    val colorB = Color(world.colorB)
    val number = level.levelNumber

    val decorations = remember(world.index) {
        world.categories.ifEmpty { EmojiCatalog.getAllCategories() }
            .flatMap { EmojiCatalog.getByCategory(it) }.map { it.emoji }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .height(NodeRowHeight)
    ) {
        Canvas(Modifier.matchParentSize()) {
            val nodePx = nodeSize.toPx()
            val cx = pathX(bias, nodePx)
            val cy = size.height / 2f
            val xmPrev = (cx + pathX(biasPrev, nodePx)) / 2f
            drawTrail(trailPath(cx, cy, xmPrev, size.height), prevDone, world)
            val xmNext = (cx + pathX(biasNext, nodePx)) / 2f
            drawTrail(trailPath(cx, cy, xmNext, 0f), nextDone, world)
        }

        // Scenery on the opposite side of the path.
        if (number % 3 != 0 && decorations.isNotEmpty()) {
            Text(
                text = decorations[(number * 7) % decorations.size],
                fontSize = (20 + (number * 5) % 14).sp,
                modifier = Modifier
                    .align(BiasAbsoluteAlignment(if (bias >= 0f) -0.82f else 0.82f, 0f))
                    .graphicsLayer { alpha = 0.55f }
            )
        }

        // The node itself
        Box(
            Modifier
                .align(BiasAbsoluteAlignment(bias, 0f))
                .size(nodeSize)
        ) {
            if (isCurrent && isUnlocked) PulseRing(shape = shape, color = Gold)

            val fill = when {
                !isUnlocked -> Brush.linearGradient(listOf(Color(0xFF334155).copy(alpha = 0.6f), Color(0xFF1E293B).copy(alpha = 0.6f)))
                stars > 0 -> Brush.linearGradient(listOf(colorA, colorB))
                else -> Brush.linearGradient(listOf(colorA.copy(alpha = 0.75f), colorB.copy(alpha = 0.75f)))
            }
            val borderColor = when {
                !isUnlocked -> Color.Gray.copy(alpha = 0.4f)
                isCurrent -> Color.White
                boss -> Gold
                else -> Color.White.copy(alpha = 0.7f)
            }
            val description = when {
                !isUnlocked -> stringResource(R.string.cd_locked)
                stars > 0 -> stringResource(R.string.map_cd_level_stars, number, world.name, stars)
                boss -> stringResource(R.string.map_cd_level_boss, number, world.name)
                else -> stringResource(R.string.map_cd_level, number, world.name)
            }

            Box(
                Modifier
                    .fillMaxSize()
                    .then(if (isCurrent && isUnlocked) Modifier.pulse(1f, 1.08f, 800) else Modifier)
                    .pressBounce()
                    .shadow(8.dp, shape)
                    .clip(shape)
                    .background(fill)
                    .border(BorderStroke(if (isCurrent && isUnlocked) 3.dp else 2.dp, borderColor), shape)
                    .clickable(onClick = onClick)
                    .semantics { contentDescription = description }
                    .testTag("level_item_$number"),
                contentAlignment = Alignment.Center
            ) {
                if (!isUnlocked) {
                    Text("🔒", fontSize = 20.sp)
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$number", fontSize = if (boss) 20.sp else 17.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                        if (stars > 0) {
                            Text(
                                "★".repeat(stars) + "☆".repeat((3 - stars).coerceAtLeast(0)),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Gold
                            )
                        } else if (boss) {
                            Text(stringResource(R.string.map_boss), fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = Gold)
                        }
                    }
                }
            }

            if (boss && isUnlocked) {
                Text("👑", fontSize = 22.sp, modifier = Modifier.align(Alignment.TopCenter).offset(y = (-16).dp))
            }
            if (isCurrent && isUnlocked) {
                // Player marker hopping above the current level.
                Text(
                    "🧙",
                    fontSize = 28.sp,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = if (boss) (-42).dp else (-30).dp)
                        .bob(amplitudePx = 10f, periodMs = 1200)
                )
            }
        }
    }
}

@Composable
private fun PulseRing(shape: Shape, color: Color) {
    if (LocalReduceMotion.current) return
    val v = rememberInfiniteTransition(label = "ring").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "ringV"
    )
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                val s = 1f + v.value * 0.55f
                scaleX = s
                scaleY = s
                alpha = (1f - v.value) * 0.8f
            }
            .border(3.dp, color, shape)
    )
}

@Composable
private fun WorldBannerRow(world: WorldInfo, isFirst: Boolean, starsMap: Map<Int, Int>) {
    val first = firstLevelOf(world)
    val last = first + LevelCatalog.LEVELS_PER_WORLD - 1
    val earned = remember(world.index, starsMap) { (first..last).sumOf { starsMap[it] ?: 0 } }
    val max = LevelCatalog.LEVELS_PER_WORLD * 3

    Box(
        Modifier
            .fillMaxWidth()
            .height(BannerRowHeight),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.matchParentSize()) {
            val cx = size.width / 2f
            val path = Path().apply {
                moveTo(cx, size.height / 2f)
                lineTo(cx, 0f)
            }
            drawTrail(path, true, world)
            if (!isFirst) {
                val down = Path().apply {
                    moveTo(cx, size.height / 2f)
                    lineTo(cx, size.height)
                }
                drawTrail(down, true, world)
            }
        }
        Row(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .shadow(10.dp, RoundedCornerShape(24.dp))
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.horizontalGradient(listOf(Color(world.colorA), Color(world.colorB))))
                .border(2.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(world.emoji, fontSize = 40.sp, modifier = Modifier.bob(amplitudePx = 6f, periodMs = 2200))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.map_world, world.index),
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(world.name, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
                Text(stringResource(R.string.map_level_range, first, last), color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { earned.toFloat() / max },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = Gold,
                    trackColor = Color.White.copy(alpha = 0.3f)
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.map_stars_earned, earned, max), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
    }
}

@Composable
private fun SummitRow(reached: Boolean) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(SummitRowHeight),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🏔️", fontSize = 64.sp, modifier = Modifier.bob(amplitudePx = 8f, periodMs = 2600))
            Text("🚩", fontSize = 30.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                if (reached) stringResource(R.string.map_summit_done) else stringResource(R.string.map_summit_waiting),
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp,
                textAlign = TextAlign.Center
            )
            Text(
                stringResource(R.string.map_summit_meta, LevelCatalog.TOTAL_LEVELS, LevelCatalog.WORLD_COUNT),
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 12.sp
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Level preview
// ---------------------------------------------------------------------------------------------

@Composable
private fun LevelPreviewDialog(
    config: LevelConfig,
    world: WorldInfo,
    stars: Int,
    bestMoves: Int,
    bestTime: Int,
    zen: Boolean,
    hintTokens: Int,
    onPlay: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Column {
                Text(
                    if (config.isBoss) {
                        stringResource(R.string.map_preview_boss, config.levelNumber)
                    } else {
                        stringResource(R.string.map_preview_level, config.levelNumber)
                    },
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp
                )
                Text(
                    stringResource(R.string.map_preview_world, world.emoji, world.name, config.difficultyLabel),
                    fontSize = 13.sp,
                    color = Color(world.colorA)
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    if (stars > 0) "★".repeat(stars) + "☆".repeat((3 - stars).coerceAtLeast(0)) else "☆☆☆",
                    color = Gold,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                if (stars > 0) {
                    val best = buildList {
                        if (bestTime > 0) add("⏱ ${formatTime(bestTime)}")
                        if (bestMoves > 0) add(stringResource(R.string.map_best_moves, bestMoves))
                    }
                    if (best.isNotEmpty()) {
                        InfoLine("🏅", stringResource(R.string.map_best, best.joinToString(" • ")))
                    }
                }
                InfoLine("🃏", stringResource(R.string.map_pairs, config.pairCount, config.pairCount * 2))
                if (zen) {
                    InfoLine("🧘", stringResource(R.string.map_zen_note))
                } else {
                    InfoLine(
                        "⏳",
                        if (config.timeLimitSec > 0) {
                            stringResource(R.string.map_time_limit, config.timeLimitSec)
                        } else {
                            stringResource(R.string.map_no_time)
                        }
                    )
                    InfoLine(
                        "👟",
                        if (config.moveLimit > 0) {
                            stringResource(R.string.map_moves_max, config.moveLimit)
                        } else {
                            stringResource(R.string.map_no_move)
                        }
                    )
                    if (config.shuffleIntervalSec > 0) {
                        InfoLine("🔀", stringResource(R.string.map_reshuffle, config.shuffleIntervalSec))
                    }
                    if (config.autoHideMs > 0) {
                        InfoLine("⚡", stringResource(R.string.map_hide, "%.1f".format(config.autoHideMs / 1000f)))
                    }
                }
                InfoLine(
                    "💡",
                    when {
                        zen || config.maxHints < 0 -> stringResource(R.string.map_hints_unlimited)
                        config.maxHints == 0 && hintTokens > 0 -> "💡 $hintTokens Hint tokens available"
                        config.maxHints == 0 -> stringResource(R.string.map_hints_none)
                        else -> pluralStringResource(R.plurals.map_hints, config.maxHints, config.maxHints)
                    }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onPlay,
                modifier = Modifier.testTag("play_level_button"),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE11D48))
            ) {
                Text(stringResource(R.string.action_play), color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

/** mm:ss for the personal-best line. */
private fun formatTime(totalSec: Int): String {
    val safe = totalSec.coerceAtLeast(0)
    return "%d:%02d".format(safe / 60, safe % 60)
}

@Composable
private fun InfoLine(emoji: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(emoji, fontSize = 16.sp)
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 14.sp)
    }
}
