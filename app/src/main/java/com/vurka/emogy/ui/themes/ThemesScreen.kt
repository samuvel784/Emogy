package com.vurka.emogy.ui.themes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vurka.emogy.R
import com.vurka.emogy.data.store.Cosmetic
import com.vurka.emogy.data.store.CosmeticKind
import com.vurka.emogy.data.store.StoreCatalog
import com.vurka.emogy.ui.components.AppTopBar
import com.vurka.emogy.ui.theme.CosmeticSwatch
import com.vurka.emogy.ui.theme.EmeraldGreen
import com.vurka.emogy.ui.theme.RoyalBlue
import com.vurka.emogy.ui.theme.TextMuted
import com.vurka.emogy.ui.theme.VividRed
import com.vurka.emogy.util.SoundManager
import com.vurka.emogy.viewmodel.LevelSelectionViewModel

/**
 * Equip screen for every cosmetic kind. Ownership comes from the shared save (purchases, rewards and
 * real level milestones); equipping writes to the theme preferences.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemesScreen(
    viewModel: LevelSelectionViewModel,
    onBackPressed: () -> Unit,
) {
    val levelsCompleted by viewModel.totalLevelsCompleted.collectAsState()
    val owned by viewModel.ownedCosmetics.collectAsState()
    val soundEnabled by viewModel.soundEnabled.collectAsState()
    val selectedCardBack by viewModel.selectedThemeId.collectAsState()
    val selectedCardTheme by viewModel.selectedCardThemeId.collectAsState()
    val selectedBoard by viewModel.selectedBoardThemeId.collectAsState()
    val selectedMatch by viewModel.selectedMatchEffectId.collectAsState()
    val selectedVictory by viewModel.selectedVictoryEffectId.collectAsState()
    val isDark = MaterialTheme.colorScheme.background.red < 0.5f

    fun isOwned(cosmetic: Cosmetic): Boolean =
        cosmetic.default ||
            cosmetic.id in owned ||
            (cosmetic.milestoneLevel > 0 && levelsCompleted >= cosmetic.milestoneLevel)

    fun selectedIdFor(kind: CosmeticKind): String = when (kind) {
        CosmeticKind.CARD_BACK -> selectedCardBack
        CosmeticKind.CARD_THEME -> selectedCardTheme
        CosmeticKind.BOARD_THEME -> selectedBoard
        CosmeticKind.MATCH_EFFECT -> selectedMatch
        CosmeticKind.VICTORY_EFFECT -> selectedVictory
    }

    fun equip(cosmetic: Cosmetic) {
        if (!isOwned(cosmetic)) return
        SoundManager.playTap(soundEnabled)
        viewModel.equip(cosmetic.kind, cosmetic.id)
    }

    Scaffold(
        topBar = {
            AppTopBar(title = stringResource(R.string.themes_title), onBack = onBackPressed)
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .testTag("themes_screen"),
            contentPadding = PaddingValues(top = 14.dp, bottom = 26.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.themes_shop_hint),
                    fontSize = 12.sp,
                    color = TextMuted,
                )
            }

            section(
                titleRes = R.string.themes_section_backs,
                cosmetics = StoreCatalog.cardBacks,
                selectedId = selectedCardBack,
                levelsCompleted = levelsCompleted,
                isDark = isDark,
                isOwned = ::isOwned,
                onEquip = ::equip,
            )
            section(
                titleRes = R.string.themes_section_faces,
                cosmetics = StoreCatalog.cardThemes,
                selectedId = selectedCardTheme,
                levelsCompleted = levelsCompleted,
                isDark = isDark,
                isOwned = ::isOwned,
                onEquip = ::equip,
            )
            section(
                titleRes = R.string.themes_section_boards,
                cosmetics = StoreCatalog.boardThemes,
                selectedId = selectedBoard,
                levelsCompleted = levelsCompleted,
                isDark = isDark,
                isOwned = ::isOwned,
                onEquip = ::equip,
            )
            section(
                titleRes = R.string.themes_section_sparkle,
                cosmetics = StoreCatalog.matchEffects,
                selectedId = selectedMatch,
                levelsCompleted = levelsCompleted,
                isDark = isDark,
                isOwned = ::isOwned,
                onEquip = ::equip,
            )
            section(
                titleRes = R.string.themes_section_victory,
                cosmetics = StoreCatalog.victoryEffects,
                selectedId = selectedVictory,
                levelsCompleted = levelsCompleted,
                isDark = isDark,
                isOwned = ::isOwned,
                onEquip = ::equip,
            )
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(
    titleRes: Int,
    cosmetics: List<Cosmetic>,
    selectedId: String,
    levelsCompleted: Int,
    isDark: Boolean,
    isOwned: (Cosmetic) -> Boolean,
    onEquip: (Cosmetic) -> Unit,
) {
    item(key = "theme_header_$titleRes") {
        Text(
            text = stringResource(titleRes),
            modifier = Modifier.padding(top = 10.dp, start = 2.dp),
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            color = TextMuted,
        )
    }
    items(cosmetics.size, key = { cosmetics[it].id }) { index ->
        val cosmetic = cosmetics[index]
        val unlocked = isOwned(cosmetic)
        CosmeticRow(
            cosmetic = cosmetic,
            unlocked = unlocked,
            selected = selectedId == cosmetic.id,
            levelsCompleted = levelsCompleted,
            isDark = isDark,
            onClick = { onEquip(cosmetic) },
        )
    }
}

@Composable
private fun CosmeticRow(
    cosmetic: Cosmetic,
    unlocked: Boolean,
    selected: Boolean,
    levelsCompleted: Int,
    isDark: Boolean,
    onClick: () -> Unit,
) {
    val background = when {
        selected -> if (isDark) Color(0xFF2A2450) else Color(0xFFDBEAFE)
        unlocked -> MaterialTheme.colorScheme.surface
        else -> if (isDark) Color(0xFF131B30) else Color(0xFFF1F5F9)
    }
    val border = when {
        selected -> BorderStroke(2.dp, VividRed)
        unlocked -> BorderStroke(1.5.dp, RoyalBlue)
        else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = unlocked, onClick = onClick)
            .testTag("theme_${cosmetic.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = background),
        border = border,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CosmeticSwatch(cosmetic = cosmetic, isDark = isDark)
            Column(Modifier.weight(1f)) {
                Text(
                    cosmetic.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (unlocked) {
                        MaterialTheme.colorScheme.onBackground
                    } else {
                        TextMuted
                    },
                )
                Text(
                    cosmetic.description,
                    fontSize = 11.sp,
                    color = TextMuted,
                )
                if (!unlocked && cosmetic.milestoneLevel > 0) {
                    Text(
                        stringResource(
                            R.string.themes_locked_progress,
                            levelsCompleted.coerceAtMost(cosmetic.milestoneLevel),
                            cosmetic.milestoneLevel,
                        ),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextMuted,
                    )
                }
            }
            when {
                selected -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = stringResource(R.string.themes_equipped),
                        tint = EmeraldGreen,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        stringResource(R.string.themes_equipped),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = EmeraldGreen,
                    )
                }
                unlocked -> OutlinedButton(
                    onClick = onClick,
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                ) {
                    Text(
                        stringResource(R.string.store_equip),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                else -> Icon(
                    Icons.Default.Lock,
                    contentDescription = stringResource(R.string.cd_locked),
                    tint = TextMuted,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
