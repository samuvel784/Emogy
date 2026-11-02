package com.vurka.emogy.ui.daily

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.vurka.emogy.R
import com.vurka.emogy.data.DailyChallenge
import com.vurka.emogy.ui.tasks.ClaimBurstData
import com.vurka.emogy.ui.tasks.ClaimBurstOverlay
import com.vurka.emogy.ui.tasks.ProgressionFeedback
import com.vurka.emogy.ui.tasks.TasksViewModel
import com.vurka.emogy.ui.tasks.progressionSections
import com.vurka.emogy.ui.theme.TextMuted
import com.vurka.emogy.util.SoundManager
import com.vurka.emogy.viewmodel.LevelSelectionViewModel

/**
 * The Daily tab is the progression hub: today's one-board challenge sits at the top, followed by
 * the weekly login, the login streak and the three mission cycles. The standalone Tasks screen
 * (More → Tasks & Rewards) renders the exact same missions through [progressionSections].
 */
@Composable
fun DailyScreen(
    viewModel: LevelSelectionViewModel,
    tasksViewModel: TasksViewModel,
    onPlay: (Long) -> Unit,
) {
    val progress by viewModel.dailyChallenge.collectAsState()
    val streak by viewModel.dailyStreak.collectAsState()
    val soundOn by viewModel.soundEnabled.collectAsState()
    val tasks by tasksViewModel.ui.collectAsState()
    val tasksSoundOn by tasksViewModel.soundEnabled.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var burst by remember { mutableStateOf<ClaimBurstData?>(null) }

    // Long-lived screen: refresh on resume and stop the midnight timer when hidden.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tasksViewModel.onResume() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { tasksViewModel.onHidden() }

    ProgressionFeedback(
        viewModel = tasksViewModel,
        soundEnabled = tasksSoundOn,
        snackbarHostState = snackbarHostState,
        onClaimed = { burst = it },
    )

    val today = DailyChallenge.epochDay(System.currentTimeMillis())
    val completedToday = progress.isCompleted(today)
    val isDark = MaterialTheme.colorScheme.background.red < 0.5f
    val background = if (isDark) {
        Brush.verticalGradient(
            listOf(Color(0xFF090D16), Color(0xFF16213E), Color(0xFF0F172A))
        )
    } else {
        Brush.verticalGradient(
            listOf(Color(0xFFFFF7ED), Color(0xFFEFF6FF), Color(0xFFF0FDFA))
        )
    }
    val surface = if (isDark) Color(0xFF1E293B).copy(alpha = 0.88f) else Color.White.copy(alpha = 0.94f)
    val onSurface = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    val muted = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
    val accent = Color(0xFF06B6D4)
    val dateLabel = DailyChallenge.dateLabel(today)

    val calendarDate = remember(today) {
        java.util.Calendar.getInstance().apply {
            timeInMillis = today * 86_400_000L
        }
    }
    val monthStr = remember(today) {
        java.text.SimpleDateFormat("MMM", java.util.Locale.getDefault())
            .format(calendarDate.time).uppercase()
    }
    val dayStr = remember(today) {
        java.text.SimpleDateFormat("d", java.util.Locale.getDefault())
            .format(calendarDate.time)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(background)
            .testTag("daily_screen")
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("daily_list"),
            contentPadding = PaddingValues(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                DailyHeader(streak = streak, onSurface = onSurface)
            }

            // Today's board, unchanged apart from living inside the shared scroll container.
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .size(width = 80.dp, height = 84.dp)
                            .shadow(8.dp, RoundedCornerShape(18.dp))
                            .clip(RoundedCornerShape(18.dp))
                            .background(surface)
                            .border(1.5.dp, accent, RoundedCornerShape(18.dp)),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(accent)
                                    .padding(vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = monthStr,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White,
                                    letterSpacing = 1.sp
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = dayStr,
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Black,
                                    color = onSurface
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        stringResource(R.string.daily_one_board),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 2.sp,
                        color = accent
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        dateLabel,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = muted,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(18.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(surface, RoundedCornerShape(22.dp))
                            .padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            if (completedToday) stringResource(R.string.daily_completed) else stringResource(R.string.daily_ready),
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Black,
                            color = if (completedToday) Color(0xFF10B981) else onSurface
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (completedToday) {
                                stringResource(R.string.daily_completed_body, DailyChallenge.REWARD_COINS)
                            } else {
                                stringResource(
                                    R.string.daily_ready_body,
                                    DailyChallenge.PAIR_COUNT,
                                    DailyChallenge.REWARD_COINS
                                )
                            },
                            fontSize = 13.sp,
                            color = muted,
                            textAlign = TextAlign.Center
                        )

                        Spacer(Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            DailyFact("🧩", stringResource(R.string.daily_pairs, DailyChallenge.PAIR_COUNT), onSurface)
                            DailyFact("⏱️", stringResource(R.string.daily_time, DailyChallenge.TIME_LIMIT_SEC), onSurface)
                            DailyFact("💡", stringResource(R.string.daily_hints, DailyChallenge.MAX_HINTS), onSurface)
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(surface, RoundedCornerShape(18.dp))
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(stringResource(R.string.daily_best_time), fontSize = 11.sp, color = muted, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (progress.bestTimeSec > 0) "${progress.bestTimeSec}s" else "—",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = onSurface
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.daily_best_moves), fontSize = 11.sp, color = muted, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (progress.bestMoves > 0) "${progress.bestMoves}" else "—",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = onSurface
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(stringResource(R.string.daily_completed_count), fontSize = 11.sp, color = muted, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${progress.completedCount}",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = onSurface
                            )
                        }
                    }

                    Spacer(Modifier.height(18.dp))

                    Button(
                        onClick = {
                            SoundManager.playTap(soundOn)
                            // Recompute in the click so a board opened just before midnight opens
                            // yesterday's board, not a stale one.
                            onPlay(DailyChallenge.epochDay(System.currentTimeMillis()))
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .testTag("daily_play_button"),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (completedToday) Color(0xFF10B981) else accent
                        )
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (completedToday) stringResource(R.string.daily_play_again) else stringResource(R.string.daily_play),
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 16.sp,
                            letterSpacing = 0.6.sp
                        )
                    }

                    Spacer(Modifier.height(14.dp))
                    Text(
                        stringResource(R.string.daily_body),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        color = muted
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                }
            }

            item {
                MissionsHeading()
            }

            progressionSections(
                state = tasks,
                onClaim = tasksViewModel::claim,
                onClaimAll = tasksViewModel::claimAll,
                onClaimMilestone = tasksViewModel::claimMilestone,
                onClaimLogin = tasksViewModel::claimWeeklyLogin,
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )

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

@Composable
private fun DailyHeader(streak: Int, onSurface: Color) {
    val streakLabel = pluralStringResource(R.plurals.daily_board_streak_days, streak, streak)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            stringResource(R.string.daily_title),
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            color = onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Row(
            modifier = Modifier
                .padding(start = 8.dp)
                .background(Color(0xFFFF4D6D).copy(alpha = 0.14f), RoundedCornerShape(50))
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                Icons.Default.LocalFireDepartment,
                contentDescription = null,
                tint = Color(0xFFFF4D6D),
                modifier = Modifier.size(16.dp)
            )
            Text(
                streakLabel,
                fontWeight = FontWeight.Black,
                fontSize = 11.sp,
                color = onSurface,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun MissionsHeading() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .testTag("daily_missions_header")
    ) {
        Text(
            stringResource(R.string.daily_missions_title),
            fontSize = 18.sp,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            stringResource(R.string.daily_missions_sub),
            fontSize = 12.sp,
            color = TextMuted
        )
    }
}

@Composable
private fun DailyFact(emoji: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(emoji, fontSize = 20.sp)
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color)
    }
}
