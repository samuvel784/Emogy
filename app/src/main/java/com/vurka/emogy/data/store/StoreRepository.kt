package com.vurka.emogy.data.store

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.vurka.emogy.data.EmojiCatalog
import com.vurka.emogy.data.GameKeys
import com.vurka.emogy.data.gameDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class MysteryBoxData(
    val hints: Int = 15,
    val bonusCoins: Int = 500,
    val streakBonus: Int = 2,
    val unlockedEmoji: String? = null,
)

sealed interface PurchaseResult {
    data class Success(
        val item: StoreItem,
        val remainingCoins: Int,
        val hintTokens: Int,
        val mysteryBox: MysteryBoxData? = null,
    ) : PurchaseResult

    data object AlreadyOwned : PurchaseResult
    data object InsufficientCoins : PurchaseResult
    data object UnknownItem : PurchaseResult
}

/**
 * Atomic shop transactions.
 *
 * Hints and bundles are repeatable; a cosmetic is charged at most once and a milestone-unlocked
 * cosmetic is never charged at all. Coins can never go negative because the balance check and the
 * deduction happen inside the same edit.
 */
class StoreRepository(private val dataStore: DataStore<Preferences>) {
    constructor(context: Context) : this(context.gameDataStore)

    val coins: Flow<Int> = dataStore.data.map { it[GameKeys.COINS] ?: 0 }

    val hintTokens: Flow<Int> = dataStore.data.map { (it[GameKeys.HINT_TOKENS] ?: 0).coerceAtLeast(0) }

    val owned: Flow<Set<String>> = dataStore.data.map {
        val owned = it[GameKeys.OWNED_COSMETICS] ?: emptySet()
        val legacy = it.asMap().keys
            .filter { key -> key.name.startsWith("theme_purchased_") && it[key] == true }
            .map { key -> key.name.removePrefix("theme_purchased_") }
        (owned + legacy).toSet()
    }

    suspend fun purchase(itemId: String): PurchaseResult {
        var result: PurchaseResult = PurchaseResult.UnknownItem
        dataStore.edit { prefs ->
            val item = StoreCatalog.item(itemId)
            if (item == null) {
                result = PurchaseResult.UnknownItem
                return@edit
            }

            val cosmetic = item.cosmetic
            val ownedNow = prefs[GameKeys.OWNED_COSMETICS] ?: emptySet()
            val legacy = cosmetic != null && prefs[GameKeys.legacyThemePurchased(cosmetic.id)] == true
            val unlockedByMilestone = cosmetic != null &&
                cosmetic.milestoneLevel > 0 &&
                completedLevels(prefs) >= cosmetic.milestoneLevel

            if (cosmetic != null && (cosmetic.id in ownedNow || legacy || unlockedByMilestone)) {
                result = PurchaseResult.AlreadyOwned
                return@edit
            }

            val coins = prefs[GameKeys.COINS] ?: 0
            if (coins < item.price) {
                result = PurchaseResult.InsufficientCoins
                return@edit
            }

            prefs[GameKeys.COINS] = (coins - item.price).coerceAtLeast(0)
            recordTx(prefs, "${item.name} Purchased", -item.price)
            if (item.hints > 0) {
                prefs[GameKeys.HINT_TOKENS] = (prefs[GameKeys.HINT_TOKENS] ?: 0) + item.hints
            }
            if (cosmetic != null) {
                prefs[GameKeys.OWNED_COSMETICS] = ownedNow + cosmetic.id
            }

            var mysteryData: MysteryBoxData? = null
            if (itemId == "bundle_mystery_box") {
                val mysteryHints = 15
                val bonusCoins = 500
                val streakBonus = 2

                prefs[GameKeys.HINT_TOKENS] = (prefs[GameKeys.HINT_TOKENS] ?: 0) + mysteryHints
                prefs[GameKeys.COINS] = (prefs[GameKeys.COINS] ?: 0) + bonusCoins
                recordTx(prefs, "Mystery Box Bonus Coins", bonusCoins)
                val streakKey = intPreferencesKey("daily_streak")
                val currentStreak = prefs[streakKey] ?: 0
                prefs[streakKey] = currentStreak + streakBonus

                // Unlock 1 random uncollected emoji
                val catalog = EmojiCatalog.items.map { it.id }.toSet()
                val collectedKey = stringSetPreferencesKey("collected_emojis")
                val collected = prefs[collectedKey] ?: emptySet()
                val uncollected = catalog - collected
                val randomEmoji = uncollected.randomOrNull()
                if (randomEmoji != null) {
                    prefs[collectedKey] = collected + randomEmoji
                }

                mysteryData = MysteryBoxData(
                    hints = mysteryHints,
                    bonusCoins = bonusCoins,
                    streakBonus = streakBonus,
                    unlockedEmoji = randomEmoji,
                )
            }

            result = PurchaseResult.Success(
                item = item,
                remainingCoins = prefs[GameKeys.COINS] ?: 0,
                hintTokens = prefs[GameKeys.HINT_TOKENS] ?: 0,
                mysteryBox = mysteryData,
            )
        }
        return result
    }

    private fun recordTx(prefs: androidx.datastore.preferences.core.MutablePreferences, title: String, amount: Int) {
        if (amount == 0) return
        val txKey = androidx.datastore.preferences.core.stringPreferencesKey("coin_transactions")
        val raw = prefs[txKey]
        val current = com.vurka.emogy.data.CoinTransactionCodec.decode(raw)
        val newTx = com.vurka.emogy.data.CoinTransaction(title = title, amount = amount, timestampMillis = System.currentTimeMillis())
        prefs[txKey] = com.vurka.emogy.data.CoinTransactionCodec.encode(listOf(newTx) + current)
    }

    private fun completedLevels(prefs: Preferences): Int =
        prefs.asMap().count { (key, value) ->
            key.name.startsWith("stars_") && value is Int && value > 0
        }
}
