package com.vurka.emogy.ui.more

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vurka.emogy.R
import com.vurka.emogy.ui.components.AppTopBar
import com.vurka.emogy.ui.components.HowToPlayBottomSheet
import com.vurka.emogy.ui.theme.BrightCyan
import com.vurka.emogy.ui.theme.EmeraldGreen
import com.vurka.emogy.ui.theme.GoldStar
import com.vurka.emogy.ui.theme.RoyalBlue
import com.vurka.emogy.util.SoundManager
import com.vurka.emogy.viewmodel.LevelSelectionViewModel

/**
 * The fifth top-level tab. Daily and Album have their own tabs; everything else lives here so the
 * Home screen can stay focused on starting a run.
 */
@Composable
fun MoreScreen(
    viewModel: LevelSelectionViewModel,
    onRewards: () -> Unit,
    onStats: () -> Unit,
    onThemes: () -> Unit,
    onStore: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit = {},
) {
    val coins by viewModel.coins.collectAsState()
    val totalStars by viewModel.totalStars.collectAsState()
    val levelsCompleted by viewModel.totalLevelsCompleted.collectAsState()
    val soundEnabled by viewModel.soundEnabled.collectAsState()
    val isDark = MaterialTheme.colorScheme.background.red < 0.5f
    var showHowToPlay by remember { mutableStateOf(false) }

    if (showHowToPlay) HowToPlayBottomSheet(onDismiss = { showHowToPlay = false })

    Scaffold(
        topBar = {
            AppTopBar(title = stringResource(R.string.more_title), onBack = null) {
                val pillBg = if (isDark) Color.White.copy(alpha = 0.12f) else Color(0xFFE2E8F0)
                Row(
                    modifier = Modifier
                        .padding(end = 16.dp)
                        .background(pillBg, RoundedCornerShape(50))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("🪙", fontSize = 16.sp)
                    Text("$coins", color = GoldStar, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        // The app-level scaffold already reserves room for the bottom navigation bar.
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .testTag("more_screen"),
            contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                OverviewCard(
                    levelsCompleted = levelsCompleted,
                    totalStars = totalStars,
                    coins = coins,
                    isDark = isDark
                )
            }

            item { SectionLabel(stringResource(R.string.more_progress), isDark) }
            item {
                MoreRow(
                    icon = Icons.Default.EmojiEvents,
                    title = stringResource(R.string.more_rewards),
                    subtitle = stringResource(R.string.more_rewards_sub),
                    accent = GoldStar,
                    testTag = "more_rewards",
                    isDark = isDark,
                    onClick = {
                        SoundManager.playTap(soundEnabled)
                        onRewards()
                    }
                )
            }
            item {
                MoreRow(
                    icon = Icons.AutoMirrored.Filled.ShowChart,
                    title = stringResource(R.string.more_stats),
                    subtitle = stringResource(R.string.more_stats_sub),
                    accent = RoyalBlue,
                    testTag = "more_stats",
                    isDark = isDark,
                    onClick = {
                        SoundManager.playTap(soundEnabled)
                        onStats()
                    }
                )
            }

            item { SectionLabel(stringResource(R.string.more_customise), isDark) }
            item {
                MoreRow(
                    icon = Icons.Default.Palette,
                    title = stringResource(R.string.more_themes),
                    subtitle = stringResource(R.string.more_themes_sub),
                    accent = Color(0xFFEC4899),
                    testTag = "more_themes",
                    isDark = isDark,
                    onClick = {
                        SoundManager.playTap(soundEnabled)
                        onThemes()
                    }
                )
            }
            item {
                MoreRow(
                    icon = Icons.Default.ShoppingCart,
                    title = stringResource(R.string.more_store),
                    subtitle = stringResource(R.string.more_store_sub),
                    accent = EmeraldGreen,
                    testTag = "more_store",
                    isDark = isDark,
                    onClick = {
                        SoundManager.playTap(soundEnabled)
                        onStore()
                    }
                )
            }

            item { SectionLabel(stringResource(R.string.more_app), isDark) }
            item {
                MoreRow(
                    icon = Icons.Default.Settings,
                    title = stringResource(R.string.more_settings),
                    subtitle = stringResource(R.string.more_settings_sub),
                    accent = Color(0xFF94A3B8),
                    testTag = "more_settings",
                    isDark = isDark,
                    onClick = {
                        SoundManager.playTap(soundEnabled)
                        onSettings()
                    }
                )
            }
            item {
                MoreRow(
                    icon = Icons.AutoMirrored.Filled.HelpOutline,
                    title = stringResource(R.string.more_how_to_play),
                    subtitle = stringResource(R.string.more_how_to_play_sub),
                    accent = BrightCyan,
                    testTag = "more_how_to_play",
                    isDark = isDark,
                    onClick = {
                        SoundManager.playTap(soundEnabled)
                        showHowToPlay = true
                    }
                )
            }
            item {
                MoreRow(
                    icon = Icons.Default.Info,
                    title = stringResource(R.string.more_about),
                    subtitle = stringResource(R.string.more_about_sub),
                    accent = Color(0xFF14B8A6),
                    testTag = "more_about",
                    isDark = isDark,
                    onClick = {
                        SoundManager.playTap(soundEnabled)
                        onAbout()
                    }
                )
            }
        }
    }
}

@Composable
private fun OverviewCard(
    levelsCompleted: Int,
    totalStars: Int,
    coins: Int,
    isDark: Boolean,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDark) Color(0xFF1C2541) else Color.White
        ),
        border = BorderStroke(1.dp, if (isDark) Color(0xFF2A3A6A) else Color(0xFFCBD5E1))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 18.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            OverviewStat("🗺️", "$levelsCompleted", stringResource(R.string.more_levels), isDark)
            OverviewStat("⭐", "$totalStars", stringResource(R.string.more_stars), isDark)
            OverviewStat("🪙", "$coins", stringResource(R.string.more_coins), isDark)
        }
    }
}

@Composable
private fun OverviewStat(icon: String, value: String, label: String, isDark: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(icon, fontSize = 22.sp)
        Text(
            value,
            fontSize = 18.sp,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            label,
            fontSize = 11.sp,
            color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
        )
    }
}

@Composable
private fun SectionLabel(text: String, isDark: Boolean) {
    Text(
        text = text,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
        fontSize = 11.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 2.sp,
        color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
    )
}

@Composable
private fun MoreRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accent: Color,
    testTag: String,
    isDark: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (isDark) Color(0xFF1E293B).copy(alpha = 0.85f) else Color.White)
            .clickable(onClick = onClick)
            .testTag(testTag)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
            )
        }
        Text("›", fontSize = 22.sp, color = if (isDark) Color(0xFF64748B) else Color(0xFF94A3B8))
    }
}
