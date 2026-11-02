package com.vurka.emogy.ui.album

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vurka.emogy.R
import com.vurka.emogy.data.AlbumCategoryProgress
import com.vurka.emogy.data.AlbumEntryProgress
import com.vurka.emogy.data.AlbumProgress
import com.vurka.emogy.data.AlbumProgressCalculator
import com.vurka.emogy.data.EmojiCatalog
import com.vurka.emogy.ui.components.AppTopBar
import com.vurka.emogy.ui.theme.EmeraldGreen
import com.vurka.emogy.ui.theme.GoldStar
import com.vurka.emogy.util.SoundManager
import com.vurka.emogy.viewmodel.LevelSelectionViewModel

/**
 * The offline sticker book. Matching a pair in any level or the Daily Challenge discovers that
 * emoji; claiming it awards coins and unlocks it completely in your album.
 */
@Composable
fun AlbumScreen(
    viewModel: LevelSelectionViewModel,
) {
    val collected by viewModel.collectedEmojis.collectAsState()
    val claimed by viewModel.claimedAlbumEmojis.collectAsState()
    val soundEnabled by viewModel.soundEnabled.collectAsState()
    val isDark = MaterialTheme.colorScheme.background.red < 0.5f

    val album = remember(collected, claimed) {
        AlbumProgressCalculator.calculate(
            catalog = EmojiCatalog.items,
            collectedIds = collected,
            claimedIds = claimed,
        )
    }
    var selected by remember { mutableStateOf<AlbumEntryProgress?>(null) }

    selected?.let { entry ->
        AlbumEntryDialog(
            entry = entry,
            isDark = isDark,
            onClaim = {
                viewModel.claimAlbumEmoji(entry.entry.id)
                selected = null
            },
            onDismiss = { selected = null },
        )
    }

    Scaffold(
        topBar = {
            AppTopBar(title = stringResource(R.string.album_title), onBack = null) {
                val pillBg = if (isDark) Color.White.copy(alpha = 0.12f) else Color(0xFFE2E8F0)
                Row(
                    modifier = Modifier
                        .padding(end = 16.dp)
                        .background(pillBg, RoundedCornerShape(50))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("📖", fontSize = 16.sp)
                    Text(
                        "${album.collected}/${album.total}",
                        color = GoldStar,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 64.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("album_screen"),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "album_summary") {
                AlbumSummary(
                    album = album,
                    isDark = isDark,
                    onClaimAll = {
                        SoundManager.playTap(soundEnabled)
                        val claimableIds = album.categories
                            .flatMap { it.entries }
                            .filter { it.isReadyToClaim }
                            .mapTo(HashSet()) { it.entry.id }
                        viewModel.claimAllAlbumEmojis(claimableIds)
                    }
                )
            }

            album.categories.forEach { category ->
                item(span = { GridItemSpan(maxLineSpan) }, key = "album_header_${category.name}") {
                    val isCategoryClaimed by viewModel.isCategoryRewardClaimed(category.name).collectAsState(initial = false)
                    CategoryHeader(
                        category = category,
                        isDark = isDark,
                        isCategoryClaimed = isCategoryClaimed,
                        onClaimCategory = {
                            SoundManager.playTap(soundEnabled)
                            viewModel.claimCategoryReward(category.name, category.rewardCoins)
                        }
                    )
                }
                items(
                    items = category.entries,
                    key = { "album_entry_${it.entry.id}" },
                ) { entry ->
                    AlbumTile(
                        entry = entry,
                        isDark = isDark,
                        onClick = {
                            SoundManager.playTap(soundEnabled)
                            if (entry.isReadyToClaim) {
                                viewModel.claimAlbumEmoji(entry.entry.id)
                            } else {
                                selected = entry
                            }
                        }
                    )
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }, key = "album_footer") {
                Text(
                    text = stringResource(R.string.album_footer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 8.dp),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
                )
            }
        }
    }
}

@Composable
private fun AlbumSummary(
    album: AlbumProgress,
    isDark: Boolean,
    onClaimAll: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDark) Color(0xFF1C2541) else Color.White
        ),
        border = BorderStroke(1.5.dp, GoldStar.copy(alpha = 0.7f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("📖", fontSize = 30.sp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (album.isComplete) {
                            stringResource(R.string.album_complete)
                        } else {
                            stringResource(R.string.album_collection)
                        },
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = stringResource(R.string.album_collected, album.collected, album.total),
                        fontSize = 13.sp,
                        color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
                    )
                }
                Text(
                    text = stringResource(R.string.album_percent, (album.fraction * 100).toInt()),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    color = GoldStar
                )
            }
            LinearProgressIndicator(
                progress = { album.fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp)),
                color = GoldStar,
                trackColor = if (isDark) Color(0xFF2A3A6A) else Color(0xFFE2E8F0)
            )
            Text(
                text = if (album.isComplete) {
                    stringResource(R.string.album_complete_body)
                } else {
                    stringResource(R.string.album_progress_body)
                },
                fontSize = 12.sp,
                color = if (isDark) Color(0xFFCBD5E1) else Color(0xFF475569)
            )

            AnimatedVisibility(visible = album.readyToClaimCount > 0) {
                Button(
                    onClick = onClaimAll,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GoldStar)
                ) {
                    Text(
                        text = stringResource(R.string.album_claim_all, album.readyToClaimCount * 10),
                        color = Color(0xFF0F172A),
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryHeader(
    category: AlbumCategoryProgress,
    isDark: Boolean,
    isCategoryClaimed: Boolean,
    onClaimCategory: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = category.name,
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        if (category.isComplete) {
            Text("✓", fontSize = 15.sp, fontWeight = FontWeight.Black, color = EmeraldGreen)
        }
        Spacer(Modifier.weight(1f))

        if (category.isComplete && !isCategoryClaimed) {
            Button(
                onClick = onClaimCategory,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = GoldStar),
                modifier = Modifier.height(28.dp)
            ) {
                Text("CLAIM +${category.rewardCoins} 🪙", color = Color(0xFF0F172A), fontWeight = FontWeight.Black, fontSize = 11.sp)
            }
        } else {
            Text(
                text = "${category.collected}/${category.total}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = when {
                    category.isComplete -> EmeraldGreen
                    isDark -> Color(0xFF94A3B8)
                    else -> Color(0xFF64748B)
                }
            )
        }
    }
}

@Composable
private fun AlbumTile(
    entry: AlbumEntryProgress,
    isDark: Boolean,
    onClick: () -> Unit,
) {
    val readyToClaim = entry.isReadyToClaim
    val isClaimed = entry.isClaimed
    val shape = RoundedCornerShape(14.dp)

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(
                when {
                    readyToClaim && isDark -> Color(0xFF1E293B)
                    readyToClaim -> Color(0xFFFFFBEB)
                    isClaimed && isDark -> Color(0xFF24325B)
                    isClaimed -> Color.White
                    isDark -> Color(0xFF151D33)
                    else -> Color(0xFFE8EDF5)
                }
            )
            .then(
                if (readyToClaim) {
                    Modifier.border(2.dp, GoldStar, shape)
                } else Modifier
            )
            .clickable(onClick = onClick)
            .testTag("album_tile_${entry.entry.id}"),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = entry.entry.emoji,
            fontSize = 26.sp,
            modifier = Modifier.alpha(if (isClaimed || readyToClaim) 1f else 0.2f)
        )

        if (readyToClaim) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(GoldStar)
                    .padding(vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+10 🪙",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF0F172A)
                )
            }
        } else if (!isClaimed) {
            Text(
                text = "🔒",
                fontSize = 9.sp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 4.dp, bottom = 3.dp)
                    .alpha(0.75f)
            )
        }
    }
}

@Composable
private fun AlbumEntryDialog(
    entry: AlbumEntryProgress,
    isDark: Boolean,
    onClaim: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Text(entry.entry.emoji, fontSize = 46.sp) },
        title = {
            Text(
                text = if (entry.isClaimed) {
                    entry.entry.name
                } else if (entry.isReadyToClaim) {
                    stringResource(R.string.album_ready_to_claim)
                } else {
                    stringResource(R.string.album_locked)
                },
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (entry.isClaimed) {
                    Text(
                        text = entry.entry.category,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = GoldStar
                    )
                    Text(
                        text = stringResource(R.string.album_claimed),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        color = if (isDark) Color(0xFFCBD5E1) else Color(0xFF475569)
                    )
                } else if (entry.isReadyToClaim) {
                    Text(
                        text = "Claim +10 coins to unlock ${entry.entry.name} completely!",
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.SemiBold,
                        color = GoldStar
                    )
                } else {
                    Text(
                        text = stringResource(R.string.album_locked_body),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        color = if (isDark) Color(0xFFCBD5E1) else Color(0xFF475569)
                    )
                }
            }
        },
        confirmButton = {
            if (entry.isReadyToClaim) {
                Button(
                    onClick = onClaim,
                    colors = ButtonDefaults.buttonColors(containerColor = GoldStar)
                ) {
                    Text("CLAIM +10 🪙", color = Color(0xFF0F172A), fontWeight = FontWeight.Black)
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text(
                        if (entry.isClaimed) stringResource(R.string.action_nice) else stringResource(R.string.action_got_it),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    )
}
