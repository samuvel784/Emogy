package com.vurka.emogy.ui.game

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VisibilityOff
import com.vurka.emogy.ui.theme.VividRed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vurka.emogy.R
import com.vurka.emogy.data.EmojiCatalog
import com.vurka.emogy.data.DailyChallenge
import com.vurka.emogy.data.LevelCatalog
import com.vurka.emogy.model.LevelConfig
import com.vurka.emogy.model.MemoryCard
import com.vurka.emogy.ui.components.AnimatedBackdrop
import com.vurka.emogy.ui.components.EmogyPopupDialog
import com.vurka.emogy.ui.theme.BoardVisuals
import com.vurka.emogy.ui.theme.CardBackVisual
import com.vurka.emogy.ui.theme.CardBackVisuals
import com.vurka.emogy.ui.theme.CardFaceVisuals
import com.vurka.emogy.ui.theme.Chrome
import com.vurka.emogy.ui.theme.EmeraldGreen
import com.vurka.emogy.ui.theme.EquippedCosmetics
import com.vurka.emogy.ui.theme.GoldStar
import com.vurka.emogy.ui.theme.LocalReduceMotion
import com.vurka.emogy.ui.theme.MatchEffectVisuals
import com.vurka.emogy.ui.theme.MatchSparkleVisual
import com.vurka.emogy.ui.theme.RoyalBlue
import com.vurka.emogy.ui.theme.VictoryPalette
import com.vurka.emogy.util.MusicManager
import com.vurka.emogy.util.SoundManager
import com.vurka.emogy.util.WorldMusic
import com.vurka.emogy.viewmodel.GameViewModel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.time.Duration.Companion.milliseconds

private val HudSurface = Chrome.TopBar

@Composable
fun GameScreen(
    viewModel: GameViewModel,
    onBackPressed: () -> Unit,
    onNextLevel: (Int) -> Unit,
    onWorldMap: () -> Unit,
    onHome: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val themeId by viewModel.selectedThemeId.collectAsStateWithLifecycle()
    val cardThemeId by viewModel.selectedCardThemeId.collectAsStateWithLifecycle()
    val boardThemeId by viewModel.selectedBoardThemeId.collectAsStateWithLifecycle()
    val matchEffectId by viewModel.selectedMatchEffectId.collectAsStateWithLifecycle()
    val victoryEffectId by viewModel.selectedVictoryEffectId.collectAsStateWithLifecycle()
    val hintTokens by viewModel.hintTokens.collectAsStateWithLifecycle()
    val assistEnabled by viewModel.isAssistMode.collectAsStateWithLifecycle()
    val soundEnabled by viewModel.soundEnabled.collectAsStateWithLifecycle()
    val levelNumber = viewModel.levelNumber
    val config = viewModel.levelConfig
    val isDark = MaterialTheme.colorScheme.background.red < 0.5f
    val reduceMotion = LocalReduceMotion.current
    val world = remember(levelNumber) { LevelCatalog.getWorldInfo(levelNumber) }

    // Daily runs have no level number or world; they get the date of the board actually opened,
    // not the current date, so a run that crosses midnight keeps its label.
    val dailyDateLabel = remember(viewModel.dailyEpochDay) {
        DailyChallenge.dateLabel(viewModel.dailyEpochDay)
    }
    val topBarTitle = when {
        viewModel.isDaily -> stringResource(R.string.game_daily_title)
        config.isBoss -> stringResource(R.string.game_boss_title, levelNumber)
        else -> stringResource(R.string.game_level_title, levelNumber)
    }
    val topBarSubtitle = if (viewModel.isDaily) dailyDateLabel else "${world.emoji} ${viewModel.worldName}"

    // World ambience, plus a one-off transition cue when the level opens.
    LaunchedEffect(levelNumber) {
        MusicManager.setGroup(WorldMusic.groupForLevel(levelNumber))
        // Give the persisted volume a couple of frames to arrive before making any noise.
        delay(320.milliseconds)
        if (config.isBoss) SoundManager.playBoss(soundEnabled) else SoundManager.playWhoosh(soundEnabled)
    }

    // One rising bell per star, timed to follow the victory fanfare.
    LaunchedEffect(state.isWon, state.stars) {
        if (!state.isWon) return@LaunchedEffect
        delay(1250.milliseconds)
        repeat(state.stars.coerceIn(0, 3)) { index ->
            SoundManager.playStar(index, soundEnabled)
            delay(260.milliseconds)
        }
    }

    // The clock must never run while the app is in the background, and an interrupted run is
    // written to DataStore so process death cannot erase it.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        viewModel.pauseIfRunning()
        viewModel.saveSnapshotNow()
    }
    // Back opens the pause menu instead of silently losing the run. A second Back resumes, so the
    // dialog can never trap the player on a dead screen.
    BackHandler(enabled = !state.isPaused && !state.isGameOver) { viewModel.togglePause() }
    BackHandler(enabled = state.isPaused) { viewModel.togglePause() }

    val snackbarHostState = remember { SnackbarHostState() }
    val restoredMessage = stringResource(R.string.game_restored)
    val restartLabel = stringResource(R.string.action_restart)
    LaunchedEffect(state.wasRestored) {
        if (state.wasRestored && !state.restoredPaused) {
            val result = snackbarHostState.showSnackbar(
                message = restoredMessage,
                actionLabel = restartLabel,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.startNewGame()
        }
    }

    // Screen shake whenever a new mistake happens (applied in the draw phase only).
    val shake = remember { Animatable(0f) }
    var lastMistakes by remember { mutableIntStateOf(0) }
    LaunchedEffect(state.mistakes) {
        if ((state.mistakes > lastMistakes) && !reduceMotion) {
            val frames = listOf(16f, -16f, 11f, -11f, 6f, -6f, 0f)
            frames.forEach { target -> shake.animateTo(target, animationSpec = tween(durationMillis = 45)) }
        }
        if (reduceMotion) shake.snapTo(0f)
        lastMistakes = state.mistakes
    }

    val worldEmojis = remember(levelNumber) {
        EmojiCatalog.items
            .asSequence()
            .filter { it.category in world.categories.ifEmpty { EmojiCatalog.getAllCategories() } }
            .map { it.emoji }
            .toList()
            .shuffled(kotlin.random.Random(levelNumber))
            .take(8)
    }
    val baseBg = MaterialTheme.colorScheme.background
    val worldBackdrop = remember(world, baseBg) {
        Brush.verticalGradient(
            listOf(
                Color(world.colorB).copy(alpha = 0.55f).compositeOver(baseBg),
                Color(world.colorA).copy(alpha = 0.20f).compositeOver(baseBg),
                baseBg,
            )
        )
    }
    val equippedBoard = remember(boardThemeId) {
        EquippedCosmetics.effectiveBoard(boardThemeId)
    }
    val backdropBrush = remember(equippedBoard, isDark, worldBackdrop) {
        BoardVisuals.forTheme(equippedBoard, isDark, worldBackdrop)
    }
    val equippedCardBack = remember(themeId) { EquippedCosmetics.effectiveCardBack(themeId) }
    val equippedCardFace = remember(cardThemeId) { EquippedCosmetics.effectiveCardTheme(cardThemeId) }
    val matchSparkle = remember(matchEffectId) {
        MatchEffectVisuals.forId(EquippedCosmetics.effectiveMatch(matchEffectId))
    }
    val victoryColors = remember(victoryEffectId) {
        VictoryPalette.colorsFor(EquippedCosmetics.effectiveVictory(victoryEffectId))
    }
    val backStyle = remember(equippedCardBack, isDark) {
        CardBackVisuals.forTheme(equippedCardBack, isDark)
    }

    // The HUD shows every hint the player can really use: the free allowance plus bought tokens.
    val availableHints = (if (state.hintsLeft < 0) 0 else state.hintsLeft) + hintTokens
    val noHintsMsg = stringResource(R.string.game_no_hints_msg)
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedBackdrop(brush = backdropBrush, emojis = worldEmojis, count = 12, alpha = 0.14f)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            GameTopBar(
                title = topBarTitle,
                subtitle = topBarSubtitle,
                isBoss = config.isBoss,
                hintsLeft = availableHints,
                hintCooldown = state.hintCooldownRemainingSec,
                hintEnabled = (!state.isHintOnCooldown) && (!state.isPreviewing) && (!state.isGameOver),
                showAssist = assistEnabled,
                assistEnabled = assistEnabled &&
                    (!state.isPreviewing) &&
                    (!state.isGameOver) &&
                    (state.assistCooldownRemainingSec == 0),
                assistCooldown = state.assistCooldownRemainingSec,
                onBack = { viewModel.togglePause() },
                onHint = {
                    if (availableHints > 0) {
                        viewModel.useHint()
                    } else {
                        scope.launch {
                            snackbarHostState.currentSnackbarData?.dismiss()
                            snackbarHostState.showSnackbar(
                                message = noHintsMsg,
                                duration = SnackbarDuration.Short
                            )
                        }
                    }
                },
                onAssist = viewModel::useAssist,
                onPause = viewModel::togglePause,
                onRestart = viewModel::startNewGame
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                HudCard(
                    moves = state.moves,
                    moveLimit = config.moveLimit,
                    mistakes = state.mistakes,
                    matched = state.matchedPairsCount,
                    total = state.totalPairs,
                    timeElapsed = state.timeElapsedSec,
                    timeRemaining = state.timeRemainingSec,
                    timeLimit = config.timeLimitSec,
                    isDark = isDark
                )

                Spacer(Modifier.height(8.dp))
                ProgressAndTime(
                    matched = state.matchedPairsCount,
                    total = state.totalPairs,
                    timeRemaining = state.timeRemainingSec,
                    timeLimit = config.timeLimitSec,
                    isDark = isDark
                )

                Spacer(Modifier.height(8.dp))
                StatusRow(
                    config = config,
                    isPreviewing = state.isPreviewing,
                    combo = state.combo,
                    shuffleCountdown = state.shuffleCountdownSec
                )

                Spacer(Modifier.height(8.dp))
                Box(modifier = Modifier.weight(1f)) {
                    CardGrid(
                        cards = state.cards,
                        assistCardIds = state.assistCardIds,
                        backStyle = backStyle,
                        cardFaceThemeId = equippedCardFace,
                        isDark = isDark,
                        shakeProvider = { shake.value },
                        onCardClick = viewModel::onCardClicked,
                        modifier = Modifier.fillMaxSize()
                    )
                    MatchSparkle(
                        trigger = state.matchedPairsCount,
                        visual = matchSparkle,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        // Floating feedback
        Box(Modifier.fillMaxSize()) {
            ComboPopup(combo = state.combo)
            ShufflePopup(shuffleCount = state.shuffleCount)
        }

        if (state.isPreviewing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 32.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Button(
                    onClick = viewModel::manualFlipCards,
                    modifier = Modifier
                        .height(56.dp)
                        .padding(horizontal = 32.dp)
                        .testTag("flip_cards_button"),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = VividRed),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
                ) {
                    Icon(
                        Icons.Default.VisibilityOff,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Flip Cards to Start",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 16.sp
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        var showVictoryDialog by remember { mutableStateOf(value = false) }

        LaunchedEffect(state.isGameOver, state.isWon) {
            if (state.isGameOver && state.isWon) {
                showVictoryDialog = false
                delay(1400.milliseconds)
                showVictoryDialog = true
            } else {
                showVictoryDialog = false
            }
        }

        if (state.isGameOver && state.isWon) {
            ConfettiCelebration(
                modifier = Modifier.fillMaxSize(),
                colors = victoryColors,
            )

            if (!showVictoryDialog) {
                VictoryCelebrationBanner()
            } else {
                VictoryDialog(
                    levelNumber = levelNumber,
                    worldName = viewModel.worldName,
                    isBoss = config.isBoss,
                    isDaily = viewModel.isDaily,
                    dailyCompleted = state.isFirstClear,
                    dailyLabel = dailyDateLabel,
                    stars = state.stars,
                    moves = state.moves,
                    mistakes = state.mistakes,
                    timeElapsedSec = state.timeElapsedSec,
                    maxCombo = state.maxCombo,
                    coinsAwarded = state.coinsAwarded,
                    isPersonalBest = state.isPersonalBest,
                    soundEnabled = soundEnabled,
                    onNext = { onNextLevel((levelNumber + 1).coerceAtMost(LevelCatalog.TOTAL_LEVELS)) },
                    onReplay = viewModel::startNewGame,
                    onWorldMap = onWorldMap,
                    onHome = onHome,
                )
            }
        }

        if (state.isGameOver && !state.isWon) {
            EmogyPopupDialog(
                visible = true,
                onDismissRequest = {},
                borderBrush = Brush.linearGradient(listOf(VividRed, Color(0xFFDC2626)))
            ) {
                Text("💔", fontSize = 48.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    if (state.lossReason == "moves") {
                        stringResource(R.string.game_loss_moves_title)
                    } else {
                        stringResource(R.string.game_loss_time_title)
                    },
                    fontWeight = FontWeight.Black,
                    fontSize = 24.sp,
                    color = VividRed,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (state.lossReason == "moves") {
                        stringResource(
                            R.string.game_loss_moves_body,
                            config.moveLimit,
                            state.matchedPairsCount,
                            state.totalPairs
                        )
                    } else {
                        stringResource(
                            R.string.game_loss_time_body,
                            state.matchedPairsCount,
                            state.totalPairs
                        )
                    },
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    color = Color.White
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    when {
                        config.isBoss -> stringResource(R.string.game_loss_boss_tip)
                        viewModel.isDaily -> stringResource(R.string.game_loss_daily_tip)
                        else -> stringResource(R.string.game_loss_tip)
                    },
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    color = Color(0xFF94A3B8)
                )
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = {
                        SoundManager.playTap(soundEnabled)
                        viewModel.startNewGame()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("retry_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = VividRed),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(stringResource(R.string.action_retry), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    TextButton(
                        onClick = {
                            SoundManager.playTap(soundEnabled)
                            onWorldMap()
                        }
                    ) {
                        Text(stringResource(R.string.action_choose_level), color = Color(0xFF94A3B8), fontWeight = FontWeight.SemiBold)
                    }
                    TextButton(
                        onClick = {
                            SoundManager.playTap(soundEnabled)
                            onBackPressed()
                        }
                    ) {
                        Text(stringResource(R.string.action_exit), color = Color(0xFF94A3B8), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        if (state.isPaused) {
            EmogyPopupDialog(
                visible = true,
                onDismissRequest = { viewModel.togglePause() },
                borderBrush = Brush.linearGradient(listOf(RoyalBlue, Color(0xFF38BDF8)))
            ) {
                Text("⏸️", fontSize = 48.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    if (state.wasRestored) stringResource(R.string.game_restored) else stringResource(R.string.game_paused),
                    fontWeight = FontWeight.Black,
                    fontSize = 24.sp,
                    color = RoyalBlue
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (state.wasRestored) {
                        stringResource(R.string.game_restored_body)
                    } else {
                        stringResource(R.string.game_paused_body)
                    },
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    color = Color.White
                )
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = { viewModel.togglePause() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("resume_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(stringResource(R.string.action_resume), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = {
                        viewModel.abandonGame()
                        onBackPressed()
                    },
                    modifier = Modifier.testTag("quit_button")
                ) {
                    Text(stringResource(R.string.action_quit), color = Color(0xFF94A3B8), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun VictoryCelebrationBanner() {
    var scaleState by remember { mutableFloatStateOf(0.2f) }
    val animatedScale by animateFloatAsState(
        targetValue = scaleState,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "celebration_banner_scale"
    )

    LaunchedEffect(Unit) {
        scaleState = 1.0f
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .scale(animatedScale)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF1E293B), Color(0xFF0F172A))
                    ),
                    shape = RoundedCornerShape(24.dp)
                )
                .border(
                    width = 2.dp,
                    brush = Brush.linearGradient(listOf(GoldStar, Color(0xFFF59E0B))),
                    shape = RoundedCornerShape(24.dp)
                )
                .padding(horizontal = 32.dp, vertical = 24.dp)
        ) {
            Text("🎉", fontSize = 64.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                text = "VICTORY!",
                fontSize = 36.sp,
                fontWeight = FontWeight.Black,
                color = GoldStar
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Level Complete!",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Victory
// ---------------------------------------------------------------------------------------------

@Composable
private fun VictoryDialog(
    levelNumber: Int,
    worldName: String,
    isBoss: Boolean,
    isDaily: Boolean,
    dailyCompleted: Boolean,
    dailyLabel: String,
    stars: Int,
    moves: Int,
    mistakes: Int,
    timeElapsedSec: Int,
    maxCombo: Int,
    coinsAwarded: Int,
    isPersonalBest: Boolean,
    soundEnabled: Boolean,
    onNext: () -> Unit,
    onReplay: () -> Unit,
    onWorldMap: () -> Unit,
    onHome: () -> Unit,
) {
    val isFinalLevel = (!isDaily) && (levelNumber >= LevelCatalog.TOTAL_LEVELS)

    EmogyPopupDialog(
        visible = true,
        onDismissRequest = {},
        borderBrush = Brush.linearGradient(listOf(GoldStar, Color(0xFFF59E0B), EmeraldGreen))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(if (isDaily) "📅" else if (isFinalLevel) "🏔️" else "🎉", fontSize = 44.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                when {
                    isDaily -> stringResource(R.string.game_victory_daily)
                    isFinalLevel -> stringResource(R.string.game_victory_summit)
                    else -> stringResource(R.string.game_victory)
                },
                fontWeight = FontWeight.Black,
                fontSize = 26.sp,
                color = VividRed
            )
            Spacer(Modifier.height(4.dp))
            Text(
                when {
                    isDaily && dailyCompleted -> stringResource(
                        R.string.game_victory_daily_first,
                        DailyChallenge.REWARD_COINS
                    )
                    isDaily -> stringResource(R.string.game_victory_daily_done, dailyLabel)
                    isBoss -> stringResource(R.string.game_victory_boss, levelNumber)
                    else -> stringResource(R.string.game_victory_level, levelNumber, worldName)
                },
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                color = if (MaterialTheme.colorScheme.background.red < 0.5f) Color(0xFF94A3B8) else Color(0xFF64748B)
            )

            if (!isDaily) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (index in 1..3) {
                        VictoryStar(earned = index <= stars, delayMs = 250 + (index * 300))
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                VictoryStat("⏱️", "${timeElapsedSec}s", stringResource(R.string.game_stat_time))
                VictoryStat("👟", moves.toString(), stringResource(R.string.game_stat_moves))
                VictoryStat("❌", mistakes.toString(), stringResource(R.string.game_stat_mistakes))
                VictoryStat("🔥", "x$maxCombo", stringResource(R.string.game_stat_combo))
            }

            if (mistakes == 0) {
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.game_perfect),
                    fontWeight = FontWeight.Bold,
                    color = EmeraldGreen,
                    fontSize = 14.sp
                )
            }
            if (isPersonalBest) {
                Spacer(Modifier.height(6.dp))
                Text(
                    if (isDaily) stringResource(R.string.game_new_daily_best) else stringResource(R.string.game_new_best),
                    fontWeight = FontWeight.Bold,
                    color = RoyalBlue,
                    fontSize = 13.sp
                )
            }
            if (coinsAwarded > 0) {
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.game_coins, coinsAwarded), fontWeight = FontWeight.Bold, color = GoldStar, fontSize = 14.sp)
            }

            Spacer(Modifier.height(20.dp))
            if (isDaily) {
                Button(
                    onClick = {
                        SoundManager.playTap(soundEnabled)
                        onHome()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("daily_done_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(stringResource(R.string.action_done), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            } else if (isFinalLevel) {
                Text(
                    stringResource(R.string.game_finished_all, LevelCatalog.TOTAL_LEVELS),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    color = if (MaterialTheme.colorScheme.background.red < 0.5f) Color(0xFF94A3B8) else Color(0xFF64748B)
                )
            } else {
                Button(
                    onClick = {
                        SoundManager.playTap(soundEnabled)
                        onNext()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("next_level_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = VividRed),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        stringResource(R.string.game_next_level, levelNumber + 1),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TextButton(
                    onClick = {
                        SoundManager.playTap(soundEnabled)
                        onReplay()
                    },
                    modifier = Modifier.testTag("replay_button")
                ) {
                    Text(
                        if (isDaily) stringResource(R.string.game_play_again) else stringResource(R.string.game_replay),
                        color = RoyalBlue,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                TextButton(
                    onClick = {
                        SoundManager.playTap(soundEnabled)
                        onWorldMap()
                    },
                    modifier = Modifier.testTag("victory_map_button")
                ) {
                    Text(
                        if (isDaily) stringResource(R.string.action_choose_level) else stringResource(R.string.game_world_map),
                        color = RoyalBlue,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (!isDaily) {
                    TextButton(
                        onClick = {
                            SoundManager.playTap(soundEnabled)
                            onHome()
                        },
                        modifier = Modifier.testTag("victory_home_button")
                    ) {
                        Text(stringResource(R.string.game_home), color = RoyalBlue, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Top bar & HUD
// ---------------------------------------------------------------------------------------------

@Composable
private fun GameTopBar(
    title: String,
    subtitle: String,
    isBoss: Boolean,
    hintsLeft: Int,
    hintCooldown: Int,
    hintEnabled: Boolean,
    showAssist: Boolean,
    assistEnabled: Boolean,
    assistCooldown: Int,
    onBack: () -> Unit,
    onHint: () -> Unit,
    onAssist: () -> Unit,
    onPause: () -> Unit,
    onRestart: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(HudSurface.copy(alpha = 0.92f), RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp))
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack, modifier = Modifier.testTag("back_button")) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back), tint = Color.White)
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                color = if (isBoss) GoldStar else Color.White,
                fontSize = 18.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF94A3B8),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(onClick = onHint, enabled = hintEnabled, modifier = Modifier.testTag("hint_button")) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Lightbulb,
                    contentDescription = stringResource(R.string.cd_hint),
                    tint = if (hintEnabled) GoldStar else Color(0xFF64748B)
                )
                val badge = when {
                    hintCooldown > 0 -> hintCooldown.toString()
                    hintsLeft >= 0 -> hintsLeft.toString()
                    else -> null
                }
                if (badge != null) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 6.dp, y = (-6).dp)
                            .size(16.dp)
                            .background(if (hintCooldown > 0) Color(0xFF475569) else VividRed, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(badge, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
        if (showAssist) {
            IconButton(
                onClick = onAssist,
                enabled = assistEnabled,
                modifier = Modifier.testTag("assist_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Favorite,
                        contentDescription = stringResource(R.string.cd_assist),
                        tint = if (assistEnabled) Color(0xFFFB7185) else Color(0xFF64748B)
                    )
                    if (assistCooldown > 0) {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 6.dp, y = (-6).dp)
                                .size(16.dp)
                                .background(Color(0xFF475569), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                assistCooldown.toString(),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
        IconButton(onClick = onPause, modifier = Modifier.testTag("pause_button")) {
            Icon(Icons.Default.Pause, contentDescription = stringResource(R.string.cd_pause), tint = Color.White)
        }
        IconButton(onClick = onRestart, modifier = Modifier.testTag("restart_button")) {
            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.cd_restart), tint = Color.White)
        }
    }
}

@Composable
private fun HudCard(
    moves: Int,
    moveLimit: Int,
    mistakes: Int,
    matched: Int,
    total: Int,
    timeElapsed: Int,
    timeRemaining: Int,
    timeLimit: Int,
    isDark: Boolean
) {
    val textColor = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    val movesLeft = if (moveLimit > 0) moveLimit - moves else Int.MAX_VALUE
    val timeLow = (timeLimit > 0) && (timeRemaining <= 10)
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(18.dp))
            .background(if (isDark) HudSurface else Color.White, RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = if (moveLimit > 0) {
                stringResource(R.string.hud_moves, moves, moveLimit)
            } else {
                stringResource(R.string.hud_moves_zen, moves)
            },
            color = if (movesLeft <= 3) Color(0xFFF43F5E) else VividRed,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
        Text(stringResource(R.string.hud_mistakes, mistakes), color = Color(0xFFF43F5E), fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.hud_pairs, matched, total), color = RoyalBlue, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text(
            text = if (timeLimit > 0) {
                stringResource(R.string.hud_time_left, timeRemaining)
            } else {
                stringResource(R.string.hud_time_elapsed, timeElapsed)
            },
            color = if (timeLow) Color(0xFFF43F5E) else textColor,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ProgressAndTime(matched: Int, total: Int, timeRemaining: Int, timeLimit: Int, isDark: Boolean) {
    val progress by animateFloatAsState(
        targetValue = if (total > 0) matched.toFloat() / total.toFloat() else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "matchProgress"
    )
    val track = if (isDark) Color(0xFF2A3A6A) else Color(0xFFCBD5E1)
    LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp)),
        color = EmeraldGreen,
        trackColor = track
    )
    if (timeLimit > 0) {
        Spacer(Modifier.height(5.dp))
        val frac by animateFloatAsState(
            targetValue = (timeRemaining.toFloat() / timeLimit.toFloat()).coerceIn(0f, 1f),
            animationSpec = tween(durationMillis = 900),
            label = "timeBar"
        )
        LinearProgressIndicator(
            progress = { frac },
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = when {
                frac > 0.5f -> RoyalBlue
                frac > 0.2f -> GoldStar
                else -> VividRed
            },
            trackColor = track
        )
    }
}

/** One fixed-height row: "Memorize!" during the preview, otherwise level-rule chips. */
@Composable
private fun StatusRow(
    config: LevelConfig,
    isPreviewing: Boolean,
    combo: Int,
    shuffleCountdown: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isPreviewing) {
            RuleChip(stringResource(R.string.status_memorize), GoldStar)
        } else {
            if (combo >= 2) RuleChip(stringResource(R.string.status_combo, combo), Color(0xFFF97316))
            if (config.shuffleIntervalSec > 0) {
                RuleChip(stringResource(R.string.status_shuffle, shuffleCountdown), if (shuffleCountdown <= 5) VividRed else Color(0xFF8B5CF6))
            }
            if (config.autoHideMs > 0) {
                RuleChip(stringResource(R.string.status_hides, "%.1f".format(config.autoHideMs / 1000f)), Color(0xFF06B6D4))
            }
        }
    }
}

@Composable
private fun RuleChip(text: String, color: Color) {
    Text(
        text = text,
        color = Color.White,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .background(color.copy(alpha = 0.85f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

@Composable
private fun BoxScope.ComboPopup(combo: Int) {
    var visible by remember { mutableStateOf(value = false) }
    LaunchedEffect(combo) {
        if (combo >= 3) {
            visible = true
            delay(900.milliseconds)
        }
        visible = false
    }
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.align(Alignment.Center),
        enter = scaleIn(spring(dampingRatio = 0.45f, stiffness = 300f)) + fadeIn(),
        exit = fadeOut(tween(250)) + slideOutVertically { -it }
    ) {
        Text(
            text = stringResource(R.string.popup_combo, combo),
            fontSize = 32.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            modifier = Modifier
                .background(Color(0xFFF97316).copy(alpha = 0.92f), RoundedCornerShape(24.dp))
                .padding(horizontal = 22.dp, vertical = 10.dp)
        )
    }
}

@Composable
private fun BoxScope.ShufflePopup(shuffleCount: Int) {
    var visible by remember { mutableStateOf(value = false) }
    LaunchedEffect(shuffleCount) {
        if (shuffleCount > 0) {
            visible = true
            delay(1100.milliseconds)
        }
        visible = false
    }
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.align(Alignment.Center),
        enter = scaleIn(spring(dampingRatio = 0.5f, stiffness = 260f)) + fadeIn(),
        exit = fadeOut(tween(300))
    ) {
        Text(
            text = stringResource(R.string.popup_shuffled),
            fontSize = 30.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            modifier = Modifier
                .background(Color(0xFF7C3AED).copy(alpha = 0.92f), RoundedCornerShape(24.dp))
                .padding(horizontal = 22.dp, vertical = 10.dp)
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Card grid
// ---------------------------------------------------------------------------------------------

/**
 * Short radial burst played on every real match. The animation progress is only read inside the
 * draw lambda, so a sparkle never recomposes the board.
 */
@Composable
private fun MatchSparkle(
    trigger: Int,
    visual: MatchSparkleVisual,
    modifier: Modifier = Modifier,
) {
    if (!visual.active) return
    if (LocalReduceMotion.current) return
    val progress = remember { Animatable(0f) }
    var lastTrigger by remember { mutableIntStateOf(trigger) }
    LaunchedEffect(trigger) {
        if (trigger > lastTrigger) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(durationMillis = 650, easing = FastOutSlowInEasing))
        }
        lastTrigger = trigger
    }
    Canvas(modifier) {
        val p = progress.value
        if ((p <= 0f) || (p >= 1f)) return@Canvas
        val colors = visual.colors.ifEmpty { listOf(GoldStar) }
        val center = Offset(size.width / 2f, size.height / 2f)
        val alpha = (1f - p).coerceIn(0f, 1f)
        val count = 16
        repeat(count) { index ->
            val angle = index * (2.0 * PI / count) + p * 0.4
            val distance = size.minDimension * (0.06f + 0.40f * p)
            drawCircle(
                color = colors[index % colors.size].copy(alpha = alpha),
                radius = size.minDimension * 0.014f * (1f - p * 0.4f),
                center = Offset(
                    center.x + cos(angle).toFloat() * distance,
                    center.y + sin(angle).toFloat() * distance,
                ),
            )
        }
        drawCircle(
            color = colors.first().copy(alpha = alpha * 0.5f),
            radius = size.minDimension * 0.05f * (1f - p),
            center = center,
        )
    }
}

@Composable
private fun CardGrid(
    cards: List<MemoryCard>,
    assistCardIds: Set<Int>,
    backStyle: CardBackVisual,
    cardFaceThemeId: String,
    isDark: Boolean,
    shakeProvider: () -> Float,
    onCardClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val columns = CardMetrics.columnsFor(cards.size)
    val emojiSize = CardMetrics.emojiSizeSp(columns).sp
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { translationX = shakeProvider() },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(
            items = cards,
            key = { it.id }
        ) { card ->
            MemoryCardItem(
                card = card,
                backStyle = backStyle,
                cardFaceThemeId = cardFaceThemeId,
                isDark = isDark,
                emojiSize = emojiSize,
                isAssistRevealed = card.id in assistCardIds,
                entranceDelayMs = (card.id % 24) * 28,
                onClick = { onCardClick(card.id) },
                // Cards glide to their new slots when the board reshuffles.
                modifier = Modifier.animateItem()
            )
        }
    }
}

/**
 * One card. The flip angle is only read inside [graphicsLayer] lambdas, so the 3D flip runs in the
 * draw phase and never triggers recomposition while animating.
 */
@Composable
fun MemoryCardItem(
    card: MemoryCard,
    backStyle: CardBackVisual,
    cardFaceThemeId: String,
    isDark: Boolean,
    emojiSize: TextUnit,
    modifier: Modifier = Modifier,
    isAssistRevealed: Boolean = false,
    entranceDelayMs: Int = 0,
    onClick: () -> Unit
) {
    val faceUp = card.isFlipped || card.isMatched || isAssistRevealed
    val reduceMotion = LocalReduceMotion.current
    val rotation = animateFloatAsState(
        targetValue = if (faceUp) 180f else 0f,
        animationSpec = tween(durationMillis = if (reduceMotion) 0 else 300, easing = FastOutSlowInEasing),
        label = "cardFlip"
    )

    // Staggered entrance when the deck appears.
    val entrance = remember { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (reduceMotion) {
            entrance.snapTo(1f)
        } else {
            delay(entranceDelayMs.milliseconds)
            entrance.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 300f))
        }
    }

    // Bouncy "pop" the moment a pair is matched.
    val pop = remember { Animatable(1f) }
    LaunchedEffect(card.isMatched) {
        if (card.isMatched && !reduceMotion) {
            pop.animateTo(1.18f, tween(110))
            pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
        }
    }

    val shape = remember { RoundedCornerShape(18.dp) }
    val faceStyle = remember(cardFaceThemeId, isDark, card.isMatched) {
        CardFaceVisuals.forTheme(cardFaceThemeId, isDark, card.isMatched)
    }
    val frontBrush: Brush = faceStyle.gradient
    val frontBorder = when {
        card.isMatched -> BorderStroke(2.5.dp, faceStyle.border)
        isAssistRevealed -> BorderStroke(2.5.dp, Color(0xFFFBBF24))
        else -> BorderStroke(1.5.dp, faceStyle.border)
    }
    val description = when {
        card.isMatched -> stringResource(R.string.cd_card_matched, card.emoji)
        isAssistRevealed -> stringResource(R.string.cd_card_assist, card.emoji)
        faceUp -> stringResource(R.string.cd_card_face_up, card.emoji)
        else -> stringResource(R.string.cd_card_hidden)
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .graphicsLayer {
                rotationY = rotation.value
                cameraDistance = 14f * density
                val sc = entrance.value * pop.value * (if (card.isMatched) 0.97f else 1f)
                scaleX = sc
                scaleY = sc
                alpha = entrance.value.coerceIn(0f, 1f)
            }
            .shadow(4.dp, shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .semantics { contentDescription = description; role = Role.Button }
            .testTag("card_${card.id}")
    ) {
        // Back face (shown while the flip angle is below 90 degrees)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = if (rotation.value <= 90f) 1f else 0f }
                .clip(shape)
                .background(backStyle.gradient, shape)
                .drawBehind {
                    // Subtle diagonal weave so the card back has some texture.
                    val step = size.width / 4f
                    for (k in -4..8) {
                        drawLine(
                            color = Color.White.copy(alpha = 0.08f),
                            start = Offset(k * step, 0f),
                            end = Offset(k * step + size.height, size.height),
                            strokeWidth = 2f
                        )
                    }
                }
                .border(BorderStroke(2.dp, backStyle.border), shape)
                // The glyph is decorative; the card's own semantics describe it, so a face-down
                // card never reveals its emoji to TalkBack.
                .clearAndSetSemantics { },
            contentAlignment = Alignment.Center
        ) {
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                val density = LocalDensity.current
                val glyphSp = CardMetrics.glyphSizeSp(
                    preferredSp = emojiSize.value * 0.75f,
                    cardWidthDp = maxWidth.value,
                    fontScale = density.fontScale,
                )
                Text(text = backStyle.symbol, fontSize = glyphSp.sp)
            }
        }

        // Front face (mirrored back to normal because the container is rotated 180 degrees)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = if (rotation.value > 90f) 1f else 0f
                    rotationY = 180f
                }
                .clip(shape)
                .background(frontBrush, shape)
                .border(frontBorder, shape)
                .clearAndSetSemantics { },
            contentAlignment = Alignment.Center
        ) {
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                val density = LocalDensity.current
                val glyphSp = CardMetrics.glyphSizeSp(
                    preferredSp = emojiSize.value,
                    cardWidthDp = maxWidth.value,
                    fontScale = density.fontScale,
                )
                Text(text = card.emoji, fontSize = glyphSp.sp)
            }
        }
    }
}

@Composable
fun VictoryStat(icon: String, value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = icon, fontSize = 20.sp)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = value, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
        Text(
            text = label,
            fontSize = 11.sp,
            color = Color(0xFF94A3B8),
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun VictoryStar(earned: Boolean, delayMs: Int) {
    val scale = remember { Animatable(0f) }
    val spin = remember { Animatable(-90f) }
    LaunchedEffect(Unit) {
        delay(delayMs.milliseconds)
        coroutineScope {
            launch { scale.animateTo(if (earned) 1.15f else 0.8f, spring(dampingRatio = 0.4f, stiffness = 180f)) }
            launch { spin.animateTo(0f, tween(450)) }
        }
        scale.animateTo(if (earned) 1f else 0.8f, tween(150))
    }
    Text(
        text = if (earned) "⭐" else "☆",
        fontSize = 38.sp,
        modifier = Modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value; rotationZ = spin.value }
    )
}
