package com.vurka.emogy.ui.tasks

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vurka.emogy.R
import com.vurka.emogy.data.login.LoginSlotState
import com.vurka.emogy.data.tasks.TaskDifficulty
import com.vurka.emogy.data.tasks.TaskInstance
import com.vurka.emogy.data.tasks.TaskPeriod
import com.vurka.emogy.ui.components.AppTopBar
import com.vurka.emogy.ui.theme.BrightCyan
import com.vurka.emogy.ui.theme.EmeraldGreen
import com.vurka.emogy.ui.theme.GoldStar
import com.vurka.emogy.ui.theme.RoyalBlue
import com.vurka.emogy.ui.theme.TextMuted
import com.vurka.emogy.ui.theme.VividRed
import com.vurka.emogy.util.SoundManager
import kotlinx.coroutines.delay

/** The Tasks tab: weekly login, 30-day streak, then the three independent mission cycles. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    onBackPressed: () -> Unit,
    viewModel: TasksViewModel = viewModel(),
) {
    val state by viewModel.ui.collectAsState()
    val soundEnabled by viewModel.soundEnabled.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var burst by remember { mutableStateOf<ClaimBurstData?>(null) }

    // Long-lived screen: refresh on resume and stop the midnight timer when hidden.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onHidden() }

    ProgressionFeedback(
        viewModel = viewModel,
        soundEnabled = soundEnabled,
        snackbarHostState = snackbarHostState,
        onClaimed = { burst = it },
    )

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                AppTopBar(title = stringResource(R.string.tasks_title), onBack = onBackPressed) {
                    CurrencyPill(coins = state.coins, hints = state.hintTokens)
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = MaterialTheme.colorScheme.background,
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
                    .testTag("tasks_screen"),
                contentPadding = PaddingValues(top = 14.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                progressionSections(
                    state = state,
                    onClaim = viewModel::claim,
                    onClaimAll = viewModel::claimAll,
                    onClaimMilestone = viewModel::claimMilestone,
                    onClaimLogin = viewModel::claimWeeklyLogin,
                )
            }
        }

        // Reward burst, drawn above the list but below the system snackbar.
        ClaimBurstOverlay(
            burst = burst,
            onDismiss = { burst = null },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 64.dp),
        )
    }
}

/**
 * The shared mission content, used by both the standalone Tasks screen and the Daily hub.
 * Keeping this in one place means the two entry points can never drift apart.
 */
internal fun LazyListScope.progressionSections(
    state: TasksUiState,
    onClaim: (TaskPeriod, String) -> Unit,
    onClaimAll: (TaskPeriod) -> Unit,
    onClaimMilestone: () -> Unit,
    onClaimLogin: () -> Unit,
) {
    item {
        WeeklyLoginCard(
            state = state,
            onClaim = onClaimLogin,
        )
    }
    item {
        StreakCard(state = state)
    }
    if (state.clockWarning) {
        item { ClockWarningCard() }
    }

    item {
        SectionHeader(
            title = stringResource(R.string.tasks_daily_title),
            subtitle = state.daily?.subtitle.orEmpty(),
            accent = RoyalBlue,
            tag = "daily",
            claimableCount = state.daily?.claimableCount ?: 0,
            onClaimAll = { onClaimAll(TaskPeriod.DAILY) },
        )
    }
    val daily = state.daily
    if (daily == null) {
        item { EmptySection() }
    } else {
        items(daily.instances.size) { index ->
            val instance = daily.instances[index]
            TaskCard(
                instance = instance,
                accent = RoyalBlue,
                onClaim = { onClaim(TaskPeriod.DAILY, instance.definition.id) },
            )
        }
        if (daily.allClaimed) {
            item { AllDoneRow() }
        }
    }

    item {
        SectionHeader(
            title = stringResource(R.string.tasks_weekly_title),
            subtitle = state.weekly?.subtitle.orEmpty(),
            accent = BrightCyan,
            tag = "weekly",
            claimableCount = state.weekly?.claimableCount ?: 0,
            onClaimAll = { onClaimAll(TaskPeriod.WEEKLY) },
        )
    }
    val weekly = state.weekly
    if (weekly == null) {
        item { EmptySection() }
    } else {
        items(weekly.instances.size) { index ->
            val instance = weekly.instances[index]
            TaskCard(
                instance = instance,
                accent = BrightCyan,
                onClaim = { onClaim(TaskPeriod.WEEKLY, instance.definition.id) },
            )
        }
        if (weekly.allClaimed) {
            item { AllDoneRow() }
        }
    }

    item {
        SectionHeader(
            title = stringResource(R.string.tasks_monthly_title),
            subtitle = state.monthly?.subtitle.orEmpty(),
            accent = GoldStar,
            tag = "monthly",
            claimableCount = state.monthly?.claimableCount ?: 0,
            onClaimAll = { onClaimAll(TaskPeriod.MONTHLY) },
        )
    }
    if (state.monthly?.windowOpen == false) {
        item {
            ClosedWindowCard(deadlineLabel = state.monthlyDeadlineLabel)
        }
    }
    val monthly = state.monthly
    if (monthly == null) {
        item { EmptySection() }
    } else {
        items(monthly.instances.size) { index ->
            val instance = monthly.instances[index]
            TaskCard(
                instance = instance,
                accent = GoldStar,
                onClaim = { onClaim(TaskPeriod.MONTHLY, instance.definition.id) },
            )
        }
        item {
            MilestoneCard(
                milestone = state.milestone,
                onClaim = onClaimMilestone,
            )
        }
    }
}

/** Shows the outcome of every claim exactly once, with sound and a short reward burst. */
@Composable
internal fun ProgressionFeedback(
    viewModel: TasksViewModel,
    soundEnabled: Boolean,
    snackbarHostState: SnackbarHostState,
    onClaimed: (ClaimBurstData) -> Unit,
) {
    val context = LocalContext.current
    val currentSound by rememberUpdatedState(soundEnabled)
    val nothingMessage = stringResource(R.string.tasks_nothing_to_claim)
    val alreadyMessage = stringResource(R.string.tasks_already_claimed)
    val clockMessage = stringResource(R.string.tasks_clock_toast)
    val loginAlreadyMessage = stringResource(R.string.tasks_login_already)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is TaskUiEvent.Claimed -> {
                    SoundManager.playCoin(currentSound)
                    onClaimed(ClaimBurstData(event.coins, event.hints))
                    snackbarHostState.showSnackbar(
                        context.getString(
                            R.string.tasks_claimed_toast,
                            event.coins,
                            event.hints,
                        ),
                    )
                }
                TaskUiEvent.NothingToClaim -> {
                    SoundManager.playMismatch(currentSound)
                    snackbarHostState.showSnackbar(nothingMessage)
                }
                TaskUiEvent.AlreadyClaimed -> {
                    SoundManager.playMismatch(currentSound)
                    snackbarHostState.showSnackbar(alreadyMessage)
                }
                TaskUiEvent.ClockAnomaly -> {
                    SoundManager.playMismatch(currentSound)
                    snackbarHostState.showSnackbar(clockMessage)
                }
                TaskUiEvent.LoginAlreadyClaimed -> {
                    SoundManager.playMismatch(currentSound)
                    snackbarHostState.showSnackbar(loginAlreadyMessage)
                }
            }
        }
    }
}

/** Floating "+coins / +hints" confirmation shared by the Tasks screen and the Daily hub. */
@Composable
internal fun ClaimBurstOverlay(
    burst: ClaimBurstData?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(burst) {
        if (burst != null) {
            delay(1_800)
            onDismiss()
        }
    }
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visible = burst != null,
            enter = scaleIn(spring(dampingRatio = 0.5f, stiffness = 300f)) + fadeIn(),
            exit = scaleOut() + fadeOut(),
        ) {
            burst?.let { data ->
                Surface(
                    shape = RoundedCornerShape(50),
                    color = EmeraldGreen,
                    modifier = Modifier.testTag("claim_burst"),
                ) {
                    Text(
                        text = "+🪙 ${data.coins}  +💡 ${data.hints}",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 17.sp,
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

internal data class ClaimBurstData(val coins: Int, val hints: Int)

@Composable
private fun CurrencyPill(coins: Int, hints: Int) {
    val isDark = MaterialTheme.colorScheme.background.red < 0.5f
    val pillBg = if (isDark) Color.White.copy(alpha = 0.12f) else Color(0xFFE2E8F0)
    Row(
        modifier = Modifier
            .padding(end = 12.dp)
            .background(pillBg, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("🪙", fontSize = 15.sp)
        Text(
            "$coins",
            color = GoldStar,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 14.sp,
            modifier = Modifier.testTag("tasks_coins"),
        )
        Text("💡", fontSize = 15.sp)
        Text(
            "$hints",
            color = BrightCyan,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 14.sp,
            modifier = Modifier.testTag("tasks_hints"),
        )
    }
}

@Composable
private fun WeeklyLoginCard(state: TasksUiState, onClaim: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("weekly_login_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.5.dp, RoyalBlue.copy(alpha = 0.5f)),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.tasks_login_title),
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                stringResource(R.string.tasks_login_sub, state.weekLabel),
                fontSize = 12.sp,
                color = TextMuted,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                state.loginSlots.forEach { slot ->
                    LoginSlot(slot = slot, modifier = Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(12.dp))
            val today = state.loginSlots.firstOrNull { it.isToday }
            val todayCoins = today?.rewardCoins ?: 0
            val todayHints = today?.rewardHints ?: 0
            val label = when {
                today == null -> stringResource(R.string.tasks_login_no_slot)
                today.state == LoginSlotState.CLAIMED -> stringResource(R.string.tasks_login_claimed_today)
                state.canClaimToday && todayHints > 0 ->
                    stringResource(R.string.tasks_login_claim_hints, todayCoins, todayHints)
                state.canClaimToday -> stringResource(R.string.tasks_login_claim, todayCoins)
                else -> stringResource(R.string.tasks_login_unavailable)
            }
            Button(
                onClick = onClaim,
                enabled = state.canClaimToday,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag("login_claim_today"),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmeraldGreen,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Text(
                    label,
                    fontWeight = FontWeight.Bold,
                    color = if (state.canClaimToday) Color.White else TextMuted,
                )
            }
        }
    }
}

@Composable
private fun LoginSlot(slot: LoginSlotUi, modifier: Modifier = Modifier) {
    val claimed = slot.state == LoginSlotState.CLAIMED
    val missed = slot.state == LoginSlotState.MISSED
    val available = slot.state == LoginSlotState.AVAILABLE
    val background = when {
        claimed -> EmeraldGreen.copy(alpha = 0.18f)
        available -> GoldStar.copy(alpha = 0.24f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    }
    val border = when {
        claimed -> BorderStroke(1.5.dp, EmeraldGreen)
        available -> BorderStroke(2.dp, GoldStar)
        else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    }
    val description = stringResource(
        R.string.cd_login_slot,
        slot.dayLabel,
        slot.rewardCoins,
        slot.rewardHints,
    )
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(background, RoundedCornerShape(10.dp))
            .border(border, RoundedCornerShape(10.dp))
            .semantics { contentDescription = description }
            .testTag("login_slot_${slot.index}")
            .padding(vertical = 6.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            slot.dayLabel,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = if (available) GoldStar else TextMuted,
            maxLines = 1,
        )
        Spacer(Modifier.height(3.dp))
        when {
            claimed -> Text("✓", fontSize = 14.sp, color = EmeraldGreen, fontWeight = FontWeight.Black)
            missed -> Text("·", fontSize = 14.sp, color = TextMuted)
            else -> {
                Text("🪙", fontSize = 12.sp)
                Text(
                    "${slot.rewardCoins}",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (slot.rewardHints > 0) {
                    Text("💡${slot.rewardHints}", fontSize = 8.sp, color = BrightCyan)
                }
            }
        }
    }
}

@Composable
private fun StreakCard(state: TasksUiState) {
    val target = state.streakTarget.coerceAtLeast(1)
    val progress by animateFloatAsState(
        targetValue = (state.streak.toFloat() / target.toFloat()).coerceIn(0f, 1f),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "streakProgress",
    )
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("streak_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.5.dp, EmeraldGreen.copy(alpha = 0.5f)),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.tasks_streak_title),
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    stringResource(R.string.tasks_streak_value, state.streak, target),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                    color = EmeraldGreen,
                    modifier = Modifier.testTag("streak_value"),
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.tasks_streak_body),
                fontSize = 12.sp,
                color = TextMuted,
            )
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp)),
                color = EmeraldGreen,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("🌠", fontSize = 16.sp)
                Text(
                    if (state.streakThemeGranted) {
                        stringResource(R.string.tasks_streak_granted)
                    } else {
                        stringResource(R.string.tasks_streak_reward)
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (state.streakThemeGranted) EmeraldGreen else GoldStar,
                )
            }
        }
    }
}

@Composable
private fun ClockWarningCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = VividRed.copy(alpha = 0.12f)),
        border = BorderStroke(1.5.dp, VividRed),
    ) {
        Text(
            stringResource(R.string.tasks_clock_warning),
            modifier = Modifier.padding(14.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = VividRed,
        )
    }
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String,
    accent: Color,
    tag: String,
    claimableCount: Int,
    onClaimAll: () -> Unit,
) {
    Column(
        Modifier
            .padding(top = 8.dp)
            .testTag("section_$tag"),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(accent),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        title,
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
                Text(
                    subtitle,
                    fontSize = 12.sp,
                    color = TextMuted,
                    modifier = Modifier.padding(start = 16.dp),
                )
            }
            if (claimableCount > 0) {
                TextButton(
                    onClick = onClaimAll,
                    modifier = Modifier.testTag("claim_all_$tag"),
                ) {
                    Text(
                        stringResource(R.string.tasks_claim_all, claimableCount),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentTextColor(accent),
                    )
                }
            }
        }
    }
}

/** Keeps accent text readable on both themes without inventing a new palette. */
private fun accentTextColor(accent: Color): Color =
    if (accent == GoldStar) Color(0xFFB45309) else accent

@Composable
private fun TaskCard(
    instance: TaskInstance,
    accent: Color,
    onClaim: () -> Unit,
) {
    val definition = instance.definition
    val complete = instance.isComplete
    val claimed = instance.isClaimed
    val fraction by animateFloatAsState(
        targetValue = (instance.progress.toFloat() / definition.target.coerceAtLeast(1).toFloat())
            .coerceIn(0f, 1f),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "taskProgress",
    )
    val difficultyLabel = when (definition.difficulty) {
        TaskDifficulty.EASY -> stringResource(R.string.tasks_difficulty_easy)
        TaskDifficulty.MEDIUM -> stringResource(R.string.tasks_difficulty_medium)
        TaskDifficulty.HARD -> stringResource(R.string.tasks_difficulty_hard)
    }
    val border = when {
        claimed -> BorderStroke(1.dp, EmeraldGreen.copy(alpha = 0.55f))
        complete -> BorderStroke(2.dp, GoldStar)
        else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    }
    val progressText = stringResource(R.string.tasks_progress, instance.progress, definition.target)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("task_${definition.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = border,
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        definition.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        definition.description,
                        fontSize = 12.sp,
                        color = TextMuted,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = accent.copy(alpha = 0.16f),
                ) {
                    Text(
                        difficultyLabel,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = accentTextColor(accent),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = if (complete) EmeraldGreen else accent,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    progressText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (complete) EmeraldGreen else TextMuted,
                    modifier = Modifier.testTag("task_progress_${definition.id}"),
                )
                RewardRow(instance)
            }

            Spacer(Modifier.height(10.dp))
            when {
                claimed -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = stringResource(R.string.cd_claimed),
                        tint = EmeraldGreen,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        stringResource(R.string.tasks_claimed),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = EmeraldGreen,
                    )
                }
                complete -> Button(
                    onClick = onClaim,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .testTag("task_claim_${definition.id}"),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                ) {
                    Text(
                        stringResource(R.string.tasks_claim),
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
                else -> Text(
                    stringResource(R.string.tasks_in_progress),
                    fontSize = 11.sp,
                    color = TextMuted,
                )
            }
        }
    }
}

@Composable
private fun RewardRow(instance: TaskInstance) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val reward = instance.definition.reward
        if (reward.coins > 0) {
            Text(
                stringResource(R.string.tasks_reward_coins, reward.coins),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = GoldStar,
            )
        }
        if (reward.hints > 0) {
            Text(
                stringResource(R.string.tasks_reward_hints, reward.hints),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = BrightCyan,
            )
        }
        if (reward.themeId != null) {
            Text("🎴", fontSize = 13.sp)
        }
    }
}

@Composable
private fun ClosedWindowCard(deadlineLabel: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("monthly_closed"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = GoldStar.copy(alpha = 0.12f)),
        border = BorderStroke(1.5.dp, GoldStar.copy(alpha = 0.7f)),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                stringResource(R.string.tasks_monthly_closed_title),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                stringResource(R.string.tasks_monthly_closed, deadlineLabel),
                fontSize = 12.sp,
                color = TextMuted,
            )
        }
    }
}

@Composable
private fun MilestoneCard(milestone: MonthlyMilestoneUi?, onClaim: () -> Unit) {
    if (milestone == null) return
    val locked = !milestone.available && !milestone.claimed
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("monthly_milestone"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (milestone.claimed) {
                EmeraldGreen.copy(alpha = 0.12f)
            } else {
                GoldStar.copy(alpha = 0.14f)
            },
        ),
        border = BorderStroke(2.dp, if (milestone.claimed) EmeraldGreen else GoldStar),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🌒", fontSize = 22.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.tasks_milestone_title),
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.tasks_milestone_body),
                fontSize = 12.sp,
                color = TextMuted,
            )
            Spacer(Modifier.height(10.dp))
            when {
                milestone.claimed -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = EmeraldGreen,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        stringResource(R.string.tasks_milestone_claimed),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = EmeraldGreen,
                    )
                }
                milestone.available -> Button(
                    onClick = onClaim,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .testTag("claim_milestone"),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = GoldStar),
                ) {
                    Text(
                        stringResource(
                            R.string.tasks_milestone_claim_value,
                            milestone.coins,
                            milestone.hints,
                        ),
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF3B2500),
                    )
                }
                else -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = stringResource(R.string.cd_locked),
                        tint = TextMuted,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        stringResource(R.string.tasks_milestone_locked),
                        fontSize = 12.sp,
                        color = TextMuted,
                    )
                }
            }
        }
    }
}

@Composable
private fun AllDoneRow() {
    Text(
        text = stringResource(R.string.tasks_all_done),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        textAlign = TextAlign.Center,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = EmeraldGreen,
    )
}

@Composable
private fun EmptySection() {
    Text(
        stringResource(R.string.tasks_empty),
        fontSize = 12.sp,
        color = TextMuted,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}
