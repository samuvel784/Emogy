package com.vurka.emogy.ui.store

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vurka.emogy.R
import com.vurka.emogy.data.store.Cosmetic
import com.vurka.emogy.data.store.CosmeticKind
import com.vurka.emogy.data.store.MysteryBoxData
import com.vurka.emogy.data.store.PurchaseResult
import com.vurka.emogy.data.store.StoreCategory
import com.vurka.emogy.data.store.StoreCatalog
import com.vurka.emogy.data.store.StoreItem
import com.vurka.emogy.ui.components.AppTopBar
import com.vurka.emogy.ui.theme.BrightCyan
import com.vurka.emogy.ui.theme.CosmeticSwatch
import com.vurka.emogy.ui.theme.EmeraldGreen
import com.vurka.emogy.ui.theme.GoldStar
import com.vurka.emogy.ui.theme.RoyalBlue
import com.vurka.emogy.ui.theme.TextMuted
import com.vurka.emogy.ui.theme.VividRed
import com.vurka.emogy.util.SoundManager
import com.vurka.emogy.viewmodel.LevelSelectionViewModel
import kotlinx.coroutines.launch

/**
 * The coin store. Every card is generated from [StoreCatalog]; prices, hint counts and ownership
 * are never hard-coded here. Purchases go through one atomic repository call.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreScreen(
    viewModel: LevelSelectionViewModel,
    onBackPressed: () -> Unit,
) {
    val coins by viewModel.coins.collectAsState()
    val hintTokens by viewModel.hintTokens.collectAsState()
    val soundEnabled by viewModel.soundEnabled.collectAsState()
    val owned by viewModel.ownedCosmetics.collectAsState()
    val levelsCompleted by viewModel.totalLevelsCompleted.collectAsState()
    val selectedCardBack by viewModel.selectedThemeId.collectAsState()
    val selectedCardTheme by viewModel.selectedCardThemeId.collectAsState()
    val selectedBoard by viewModel.selectedBoardThemeId.collectAsState()
    val selectedMatch by viewModel.selectedMatchEffectId.collectAsState()
    val selectedVictory by viewModel.selectedVictoryEffectId.collectAsState()

    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val isDark = MaterialTheme.colorScheme.background.red < 0.5f
    var category by rememberSaveable { mutableStateOf(StoreCategory.CARD_BACKS) }
    var showNotEnough by rememberSaveable { mutableStateOf(false) }
    var mysteryBoxDialogData by remember { mutableStateOf<MysteryBoxData?>(null) }

    if (showNotEnough) {
        AlertDialog(
            onDismissRequest = { showNotEnough = false },
            title = {
                Text(
                    stringResource(R.string.store_not_enough_title),
                    fontWeight = FontWeight.Bold,
                    color = VividRed,
                )
            },
            text = { Text(stringResource(R.string.store_not_enough_body)) },
            confirmButton = {
                Button(
                    onClick = { showNotEnough = false },
                    colors = ButtonDefaults.buttonColors(containerColor = VividRed),
                ) {
                    Text(
                        stringResource(R.string.action_got_it),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            textContentColor = MaterialTheme.colorScheme.onSurface,
        )
    }

    mysteryBoxDialogData?.let { box ->
        AlertDialog(
            onDismissRequest = { mysteryBoxDialogData = null },
            icon = { Text("📦", fontSize = 48.sp) },
            title = { Text("Mystery Box Unpacked!", fontWeight = FontWeight.Black) },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("You received:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("💡 +${box.hints} Hints", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = BrightCyan)
                    Text("🪙 +${box.bonusCoins} Bonus Coins", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = GoldStar)
                    box.unlockedEmoji?.let { emoji ->
                        Text("✨ Unlocked Emoji: $emoji", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = EmeraldGreen)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { mysteryBoxDialogData = null },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldStar)
                ) {
                    Text("Awesome!", color = Color(0xFF0F172A), fontWeight = FontWeight.Black)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            textContentColor = MaterialTheme.colorScheme.onSurface,
        )
    }

    fun selectedFor(kind: CosmeticKind): String = when (kind) {
        CosmeticKind.CARD_BACK -> selectedCardBack
        CosmeticKind.CARD_THEME -> selectedCardTheme
        CosmeticKind.BOARD_THEME -> selectedBoard
        CosmeticKind.MATCH_EFFECT -> selectedMatch
        CosmeticKind.VICTORY_EFFECT -> selectedVictory
    }

    fun owns(cosmetic: Cosmetic): Boolean =
        cosmetic.default ||
            cosmetic.id in owned ||
            (cosmetic.milestoneLevel > 0 && levelsCompleted >= cosmetic.milestoneLevel)

    fun buy(itemId: String) {
        scope.launch {
            when (val result = viewModel.purchase(itemId)) {
                is PurchaseResult.Success -> {
                    SoundManager.playCoin(soundEnabled)
                    if (result.mysteryBox != null) {
                        mysteryBoxDialogData = result.mysteryBox
                    } else {
                        result.item.cosmetic?.let { cosmetic ->
                            viewModel.equip(cosmetic.kind, cosmetic.id)
                        }
                        val message = if (result.item.hints > 0) {
                            context.getString(R.string.store_purchase_hints, result.item.hints)
                        } else {
                            context.getString(R.string.store_purchase_success, result.item.name)
                        }
                        snackbarHostState.showSnackbar(message)
                    }
                }
                PurchaseResult.AlreadyOwned -> {
                    SoundManager.playMismatch(soundEnabled)
                    snackbarHostState.showSnackbar(context.getString(R.string.store_purchase_owned))
                }
                PurchaseResult.InsufficientCoins -> {
                    SoundManager.playMismatch(soundEnabled)
                    showNotEnough = true
                }
                PurchaseResult.UnknownItem -> {
                    SoundManager.playMismatch(soundEnabled)
                    snackbarHostState.showSnackbar(context.getString(R.string.store_purchase_unknown))
                }
            }
        }
    }

    fun equip(cosmetic: Cosmetic) {
        SoundManager.playTap(soundEnabled)
        viewModel.equip(cosmetic.kind, cosmetic.id)
    }

    Scaffold(
        topBar = {
            AppTopBar(title = stringResource(R.string.store_title), onBack = onBackPressed) {
                InventoryPill(coins = coins, hints = hintTokens)
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("store_screen"),
        ) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(StoreCategory.values().toList()) { entry ->
                    FilterChip(
                        selected = category == entry,
                        onClick = {
                            SoundManager.playTap(soundEnabled)
                            category = entry
                        },
                        label = {
                            Text(
                                categoryLabel(entry),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        },
                        leadingIcon = if (category == entry) {
                            {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp),
                                )
                            }
                        } else {
                            null
                        },
                        modifier = Modifier.testTag("store_tab_${entry.name.lowercase()}"),
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(bottom = 26.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when (category) {
                    StoreCategory.HINTS -> {
                        item { HintBalanceCard(hints = hintTokens) }
                        item { CategoryIntro(R.string.store_hints_title, R.string.store_hints_body) }
                        items(StoreCatalog.hintPacks, key = { it.id }) { item ->
                            ConsumableCard(
                                item = item,
                                coins = coins,
                                onBuy = { buy(item.id) },
                            )
                        }
                    }

                    StoreCategory.BUNDLES -> {
                        item { CategoryIntro(R.string.store_bundles_title, R.string.store_bundles_body) }
                        items(StoreCatalog.bundles, key = { it.id }) { item ->
                            ConsumableCard(
                                item = item,
                                coins = coins,
                                onBuy = { buy(item.id) },
                            )
                        }
                    }

                    StoreCategory.CARD_BACKS -> {
                        item { CategoryIntro(R.string.store_backs_title, R.string.store_backs_body) }
                        items(StoreCatalog.cardBacks, key = { it.id }) { cosmetic ->
                            CosmeticCard(
                                cosmetic = cosmetic,
                                owned = owns(cosmetic),
                                selected = selectedFor(cosmetic.kind) == cosmetic.id,
                                coins = coins,
                                isDark = isDark,
                                onBuy = { buy(cosmetic.id) },
                                onEquip = { equip(cosmetic) },
                            )
                        }
                    }

                    StoreCategory.CARD_THEMES -> {
                        item { CategoryIntro(R.string.store_faces_title, R.string.store_faces_body) }
                        items(StoreCatalog.cardThemes, key = { it.id }) { cosmetic ->
                            CosmeticCard(
                                cosmetic = cosmetic,
                                owned = owns(cosmetic),
                                selected = selectedFor(cosmetic.kind) == cosmetic.id,
                                coins = coins,
                                isDark = isDark,
                                onBuy = { buy(cosmetic.id) },
                                onEquip = { equip(cosmetic) },
                            )
                        }
                    }

                    StoreCategory.EFFECTS -> {
                        item { CategoryIntro(R.string.store_effects_title, R.string.store_effects_body) }
                        EffectGroup(
                            titleRes = R.string.store_group_boards,
                            cosmetics = StoreCatalog.boardThemes,
                            selectedId = selectedBoard,
                            coins = coins,
                            owned = owned,
                            levelsCompleted = levelsCompleted,
                            isDark = isDark,
                            onBuy = ::buy,
                            onEquip = { equip(it) },
                        )
                        EffectGroup(
                            titleRes = R.string.store_group_sparkle,
                            cosmetics = StoreCatalog.matchEffects,
                            selectedId = selectedMatch,
                            coins = coins,
                            owned = owned,
                            levelsCompleted = levelsCompleted,
                            isDark = isDark,
                            onBuy = ::buy,
                            onEquip = { equip(it) },
                        )
                        EffectGroup(
                            titleRes = R.string.store_group_victory,
                            cosmetics = StoreCatalog.victoryEffects,
                            selectedId = selectedVictory,
                            coins = coins,
                            owned = owned,
                            levelsCompleted = levelsCompleted,
                            isDark = isDark,
                            onBuy = ::buy,
                            onEquip = { equip(it) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun categoryLabel(category: StoreCategory): String = when (category) {
    StoreCategory.HINTS -> stringResource(R.string.store_tab_hints)
    StoreCategory.CARD_THEMES -> stringResource(R.string.store_tab_card_themes)
    StoreCategory.CARD_BACKS -> stringResource(R.string.store_tab_card_backs)
    StoreCategory.EFFECTS -> stringResource(R.string.store_tab_effects)
    StoreCategory.BUNDLES -> stringResource(R.string.store_tab_bundles)
}

/** Adds one category group to a [androidx.compose.foundation.lazy.LazyListScope]. */
private fun androidx.compose.foundation.lazy.LazyListScope.EffectGroup(
    titleRes: Int,
    cosmetics: List<Cosmetic>,
    selectedId: String,
    coins: Int,
    owned: Set<String>,
    levelsCompleted: Int,
    isDark: Boolean,
    onBuy: (String) -> Unit,
    onEquip: (Cosmetic) -> Unit,
) {
    item(key = "effect_header_$titleRes") {
        Text(
            text = stringResource(titleRes),
            modifier = Modifier.padding(top = 8.dp, start = 2.dp),
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            color = TextMuted,
        )
    }
    items(cosmetics, key = { it.id }) { cosmetic ->
        CosmeticCard(
            cosmetic = cosmetic,
            owned = cosmetic.default ||
                cosmetic.id in owned ||
                (cosmetic.milestoneLevel > 0 && levelsCompleted >= cosmetic.milestoneLevel),
            selected = selectedId == cosmetic.id,
            coins = coins,
            isDark = isDark,
            onBuy = { onBuy(cosmetic.id) },
            onEquip = { onEquip(cosmetic) },
        )
    }
}

@Composable
private fun InventoryPill(coins: Int, hints: Int) {
    val isDark = MaterialTheme.colorScheme.background.red < 0.5f
    val pillBg = if (isDark) Color.White.copy(alpha = 0.12f) else Color(0xFFE2E8F0)
    Row(
        modifier = Modifier
            .padding(end = 12.dp)
            .background(pillBg, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("🪙", fontSize = 15.sp)
        Text(
            "$coins",
            color = GoldStar,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 14.sp,
            modifier = Modifier.testTag("store_coins"),
        )
        Text("💡", fontSize = 15.sp)
        Text(
            "$hints",
            color = BrightCyan,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 14.sp,
            modifier = Modifier.testTag("store_hints"),
        )
    }
}

@Composable
private fun CategoryIntro(titleRes: Int, bodyRes: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        border = BorderStroke(1.5.dp, RoyalBlue.copy(alpha = 0.45f)),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(titleRes),
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                stringResource(bodyRes),
                fontSize = 12.sp,
                color = TextMuted,
            )
        }
    }
}

@Composable
private fun HintBalanceCard(hints: Int) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("hint_balance"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = BrightCyan.copy(alpha = 0.12f)),
        border = BorderStroke(1.5.dp, BrightCyan),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("💡", fontSize = 26.sp)
            Column {
                Text(
                    stringResource(R.string.store_hint_balance, hints),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    stringResource(R.string.store_hint_balance_sub),
                    fontSize = 12.sp,
                    color = TextMuted,
                )
            }
        }
    }
}

@Composable
private fun ConsumableCard(item: StoreItem, coins: Int, onBuy: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("store_item_${item.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(item.symbol, fontSize = 28.sp)
            Column(Modifier.weight(1f)) {
                Text(
                    item.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    item.description,
                    fontSize = 12.sp,
                    color = TextMuted,
                )
            }
            Button(
                onClick = onBuy,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (coins >= item.price) VividRed else MaterialTheme.colorScheme.surfaceVariant,
                ),
                modifier = Modifier.testTag("store_buy_${item.id}"),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Text(
                    stringResource(R.string.store_price, item.price),
                    color = if (coins >= item.price) Color.White else TextMuted,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun CosmeticCard(
    cosmetic: Cosmetic,
    owned: Boolean,
    selected: Boolean,
    coins: Int,
    isDark: Boolean,
    onBuy: () -> Unit,
    onEquip: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("cosmetic_${cosmetic.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                RoyalBlue.copy(alpha = 0.12f)
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        border = when {
            selected -> BorderStroke(2.dp, EmeraldGreen)
            owned -> BorderStroke(1.dp, EmeraldGreen.copy(alpha = 0.45f))
            else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        },
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
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    cosmetic.description,
                    fontSize = 11.sp,
                    color = TextMuted,
                )
                when {
                    cosmetic.rewardOnly && !owned -> Text(
                        stringResource(R.string.store_reward_only_hint),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = GoldStar,
                    )
                    !owned && cosmetic.milestoneLevel > 0 -> Text(
                        stringResource(
                            R.string.store_milestone_note,
                            cosmetic.milestoneLevel,
                        ),
                        fontSize = 11.sp,
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
                        contentDescription = stringResource(R.string.cd_selected),
                        tint = EmeraldGreen,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        stringResource(R.string.store_equipped),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = EmeraldGreen,
                    )
                }
                owned -> OutlinedButton(
                    onClick = onEquip,
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                    modifier = Modifier.testTag("store_equip_${cosmetic.id}"),
                ) {
                    Text(
                        stringResource(R.string.store_equip),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                cosmetic.rewardOnly -> Icon(
                    Icons.Default.Lock,
                    contentDescription = stringResource(R.string.cd_locked),
                    tint = TextMuted,
                    modifier = Modifier.size(20.dp),
                )
                else -> Button(
                    onClick = onBuy,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (coins >= cosmetic.price) {
                            VividRed
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.testTag("store_buy_${cosmetic.id}"),
                ) {
                    Text(
                        stringResource(R.string.store_price, cosmetic.price),
                        color = if (coins >= cosmetic.price) Color.White else TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

