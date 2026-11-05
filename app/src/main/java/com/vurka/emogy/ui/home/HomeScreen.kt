package com.vurka.emogy.ui.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vurka.emogy.R
import com.vurka.emogy.data.CoinTransaction
import com.vurka.emogy.data.HomeProgressRules
import com.vurka.emogy.data.LevelCatalog
import com.vurka.emogy.ui.components.AnimatedBackdrop
import com.vurka.emogy.ui.components.HowToPlayBottomSheet
import com.vurka.emogy.ui.components.WelcomeSheet
import com.vurka.emogy.ui.components.popIn
import com.vurka.emogy.ui.components.pressBounce
import com.vurka.emogy.ui.components.pulse
import com.vurka.emogy.ui.theme.EmeraldGreen
import com.vurka.emogy.ui.theme.GoldStar
import com.vurka.emogy.ui.theme.VividRed
import com.vurka.emogy.util.SoundManager
import com.vurka.emogy.viewmodel.LevelSelectionViewModel

private val BackdropParticles = listOf("✦", "✧", "❖", "◈", "◆", "◇", "•", "·", "✦", "✧")

@Composable
fun HomeScreen(
    viewModel: LevelSelectionViewModel,
    onPlayClicked: (Int) -> Unit,
    onWorldMapClicked: () -> Unit,
    onStatsClicked: () -> Unit,
    onStoreClicked: () -> Unit,
    onSettingsClicked: () -> Unit,
    onDailyClicked: () -> Unit,
) {
    val streak by viewModel.dailyStreak.collectAsState()
    val totalStars by viewModel.totalStars.collectAsState()
    val coins by viewModel.coins.collectAsState()
    val transactions by viewModel.coinTransactions.collectAsState()
    val lastPlayed by viewModel.lastPlayedLevel.collectAsState()
    val starsMap by viewModel.starsMap.collectAsState()
    val totalFlips by viewModel.totalCardFlips.collectAsState()
    val soundOn by viewModel.soundEnabled.collectAsState()
    val welcomeSeen by viewModel.welcomeSeen.collectAsState()
    var showHowToPlay by remember { mutableStateOf(false) }
    var showWelcome by remember { mutableStateOf(false) }
    var showCoinHistoryDialog by remember { mutableStateOf(false) }

    if (showWelcome) {
        WelcomeSheet(
            onDismiss = {
                showWelcome = false
                viewModel.markWelcomeSeen()
            },
            onHowToPlay = {
                showWelcome = false
                viewModel.markWelcomeSeen()
                showHowToPlay = true
            }
        )
    }
    if (showHowToPlay) HowToPlayBottomSheet(onDismiss = { showHowToPlay = false })
    if (showCoinHistoryDialog) {
        CoinHistoryDialog(
            transactions = transactions,
            coins = coins,
            isDark = MaterialTheme.colorScheme.background.red < 0.5f,
            onDismiss = { showCoinHistoryDialog = false }
        )
    }

    val progressLoaded = lastPlayed in 1..LevelCatalog.TOTAL_LEVELS
    val hasPlayed = HomeProgressRules.hasPlayedBefore(lastPlayed, totalFlips)
    LaunchedEffect(progressLoaded, welcomeSeen, hasPlayed) {
        if (progressLoaded && welcomeSeen == false && !hasPlayed) {
            showWelcome = true
        }
    }

    val isDark = MaterialTheme.colorScheme.background.red < 0.5f
    val bg = remember(isDark) {
        if (isDark) {
            Brush.verticalGradient(
                listOf(
                    Color(0xFF090D16),
                    Color(0xFF131B2E),
                    Color(0xFF1E1B4B),
                    Color(0xFF0F172A)
                )
            )
        } else {
            Brush.verticalGradient(
                listOf(
                    Color(0xFFFFF1F2),
                    Color(0xFFEFF6FF),
                    Color(0xFFF3E8FF),
                    Color(0xFFF0FDFA)
                )
            )
        }
    }

    val continueLevel = if (lastPlayed in 1..LevelCatalog.TOTAL_LEVELS) lastPlayed else 1
    val world = remember(continueLevel) { LevelCatalog.getWorldInfo(continueLevel) }
    val worldStart = (world.index - 1) * LevelCatalog.LEVELS_PER_WORLD + 1
    val worldEnd = world.index * LevelCatalog.LEVELS_PER_WORLD
    val worldCompletedLevels = remember(world, starsMap) {
        (worldStart..worldEnd).count { (starsMap[it] ?: 0) > 0 }
    }
    val worldProgress = (worldCompletedLevels.toFloat() / LevelCatalog.LEVELS_PER_WORLD).coerceIn(0f, 1f)

    Box(Modifier.fillMaxSize()) {
        AnimatedBackdrop(brush = bg, emojis = BackdropParticles, count = 22, alpha = if (isDark) 0.15f else 0.35f)

        Column(Modifier.fillMaxSize()) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SmallOrb(Icons.AutoMirrored.Filled.ShowChart, stringResource(R.string.cd_statistics), Color(0xFF3B82F6), "stats_button", isDark) {
                    SoundManager.playTap(soundOn)
                    onStatsClicked()
                }
                Spacer(Modifier.width(10.dp))
                SmallOrb(Icons.Default.ShoppingCart, stringResource(R.string.cd_coin_store), Color(0xFF10B981), "store_button", isDark) {
                    SoundManager.playTap(soundOn)
                    onStoreClicked()
                }
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    GlassInfoChip(Icons.Default.LocalFireDepartment, "$streak", Color(0xFFFF4D6D), isDark) {
                        SoundManager.playTap(soundOn)
                        onDailyClicked()
                    }
                    GlassInfoChip(Icons.Default.Star, "$totalStars", Color(0xFFF59E0B), isDark)
                    GlassInfoChip("🪙", "$coins", isDark) {
                        SoundManager.playTap(soundOn)
                        showCoinHistoryDialog = true
                    }
                }
                Spacer(Modifier.weight(1f))
                SmallOrb(Icons.Default.Settings, stringResource(R.string.cd_settings), Color(0xFF64748B), "settings_button", isDark) {
                    SoundManager.playTap(soundOn)
                    onSettingsClicked()
                }
            }

            // Scrollable content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(24.dp))

                // Hero Logo
                Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
                    val glow by rememberInfiniteTransition(label = "glow").animateFloat(
                        0.35f, 0.8f,
                        infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                        label = "g"
                    )
                    Box(
                        Modifier
                            .size(200.dp)
                            .graphicsLayer { alpha = glow; scaleX = 0.9f + glow * 0.16f; scaleY = 0.9f + glow * 0.16f }
                            .background(
                                Brush.radialGradient(
                                    listOf(
                                        Color(0xFFFF4D6D).copy(alpha = 0.35f),
                                        Color(0xFF8B5CF6).copy(alpha = 0.2f),
                                        Color.Transparent
                                    )
                                ),
                                CircleShape
                            )
                    )
                    Box(
                        Modifier
                            .size(170.dp)
                            .popIn(100)
                            .shadow(20.dp, RoundedCornerShape(36.dp), spotColor = Color(0xFF8B5CF6))
                            .clip(RoundedCornerShape(36.dp))
                            .background(if (isDark) Color(0xFF1E293B).copy(alpha = 0.65f) else Color.White.copy(alpha = 0.9f))
                            .border(
                                BorderStroke(
                                    1.5.dp,
                                    Brush.linearGradient(
                                        listOf(
                                            Color(0xFFFF4D6D).copy(alpha = 0.8f),
                                            Color(0xFF8B5CF6).copy(alpha = 0.6f),
                                            Color(0xFF06B6D4).copy(alpha = 0.4f)
                                        )
                                    )
                                ),
                                RoundedCornerShape(36.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.emogy_logo),
                            contentDescription = stringResource(R.string.cd_logo),
                            modifier = Modifier
                                .size(135.dp)
                                .pulse(1f, 1.03f, 1800)
                                .clip(RoundedCornerShape(28.dp))
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.home_title),
                    style = MaterialTheme.typography.headlineLarge.copy(
                        brush = Brush.horizontalGradient(
                            listOf(Color(0xFFE11D48), Color(0xFF8B5CF6), Color(0xFF2563EB))
                        )
                    ),
                    fontWeight = FontWeight.Black,
                    fontSize = 38.sp,
                    letterSpacing = 4.sp,
                    modifier = Modifier.popIn(300)
                )
                Text(
                    stringResource(R.string.app_subtitle),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 3.sp,
                    color = if (isDark) Color(0xFFCBD5E1) else Color(0xFF475569)
                )

                Spacer(Modifier.height(44.dp))

                // Primary Play Button
                PrimaryPlayButton(
                    levelNumber = continueLevel,
                    label = stringResource(if (hasPlayed) R.string.home_continue else R.string.home_play),
                    onClick = {
                        SoundManager.playTap(soundOn)
                        onPlayClicked(continueLevel)
                    }
                )

                Spacer(Modifier.height(20.dp))

                // Progress Card
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(12.dp, RoundedCornerShape(24.dp))
                        .clip(RoundedCornerShape(24.dp))
                        .background(if (isDark) Color(0xFF1E293B).copy(alpha = 0.85f) else Color.White.copy(alpha = 0.95f))
                        .border(
                            BorderStroke(1.dp, if (isDark) Color.White.copy(alpha = 0.12f) else Color(0xFFCBD5E1)),
                            RoundedCornerShape(24.dp)
                        )
                        .clickable {
                            SoundManager.playTap(soundOn)
                            onWorldMapClicked()
                        }
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Explore, contentDescription = null, tint = Color(world.colorA), modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            stringResource(R.string.home_world_header, world.index, world.name),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            stringResource(R.string.home_level_range, worldStart, worldEnd),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
                        )
                    }
                    LinearProgressIndicator(
                        progress = { worldProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp)),
                        color = Color(world.colorA),
                        trackColor = if (isDark) Color(0xFF2A3A6A) else Color(0xFFE2E8F0)
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            "$worldCompletedLevels / ${LevelCatalog.LEVELS_PER_WORLD} levels",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isDark) Color(0xFFCBD5E1) else Color(0xFF475569)
                        )
                        Text(
                            stringResource(R.string.home_stars, totalStars),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) Color(0xFFFDE047) else Color(0xFFB45309)
                        )
                    }
                }

                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

@Composable
private fun SmallOrb(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    testTag: String,
    isDark: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .pressBounce()
            .clip(CircleShape)
            .background(if (isDark) Color(0xFF1E293B).copy(alpha = 0.8f) else Color.White.copy(alpha = 0.9f))
            .border(
                BorderStroke(1.dp, if (isDark) Color.White.copy(alpha = 0.12f) else Color(0xFFCBD5E1)),
                CircleShape
            )
            .clickable(onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun GlassInfoChip(
    icon: ImageVector,
    text: String,
    tint: Color,
    isDark: Boolean,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (isDark) Color(0xFF1E293B).copy(alpha = 0.8f) else Color.White.copy(alpha = 0.9f))
            .border(
                BorderStroke(1.dp, if (isDark) Color.White.copy(alpha = 0.12f) else Color(0xFFCBD5E1)),
                RoundedCornerShape(50)
            )
            .then(if (onClick != null) Modifier.pressBounce().clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Text(
            text = text,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = if (isDark) Color.White else Color(0xFF1E293B)
        )
    }
}

@Composable
private fun GlassInfoChip(
    emoji: String,
    text: String,
    isDark: Boolean,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (isDark) Color(0xFF1E293B).copy(alpha = 0.8f) else Color.White.copy(alpha = 0.9f))
            .border(
                BorderStroke(1.dp, if (isDark) Color.White.copy(alpha = 0.12f) else Color(0xFFCBD5E1)),
                RoundedCornerShape(50)
            )
            .then(if (onClick != null) Modifier.pressBounce().clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(emoji, fontSize = 13.sp)
        Text(
            text = text,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = if (isDark) Color.White else Color(0xFF1E293B)
        )
    }
}

@Composable
private fun CoinHistoryDialog(
    transactions: List<CoinTransaction>,
    coins: Int,
    isDark: Boolean,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("🪙 Coin History", fontWeight = FontWeight.Black, fontSize = 20.sp)
                Box(
                    modifier = Modifier
                        .background(GoldStar.copy(alpha = 0.2f), RoundedCornerShape(50))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("$coins 🪙", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = GoldStar)
                }
            }
        },
        text = {
            if (transactions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No coin transactions yet.\nPlay levels or complete missions to earn coins!",
                        textAlign = TextAlign.Center,
                        fontSize = 13.sp,
                        color = if (isDark) Color(0xFFCBD5E1) else Color(0xFF475569)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(transactions) { tx ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    tx.title,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                tx.formattedAmount,
                                fontWeight = FontWeight.Black,
                                fontSize = 14.sp,
                                color = if (tx.isCredit) EmeraldGreen else VividRed
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = VividRed)
            ) {
                Text("Got It", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun PrimaryPlayButton(
    levelNumber: Int,
    label: String,
    onClick: () -> Unit
) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "s"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .pressBounce()
            .shadow(16.dp, RoundedCornerShape(20.dp), spotColor = Color(0xFFE11D48))
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFFE11D48), Color(0xFFFF4D6D), Color(0xFF8B5CF6))
                )
            )
            .clickable(onClick = onClick)
            .testTag("play_button"),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "$label • Level $levelNumber",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            )
        }
    }
}
