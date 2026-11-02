package com.vurka.emogy.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

/**
 * The one DataStore file that holds coins, progress, tasks, login state and store inventory.
 *
 * Every repository must use this single delegate: two `preferencesDataStore` delegates pointing at
 * the same file crash at runtime with "multiple DataStores active for the same file". Cosmetics
 * selection lives in the separate `theme_prefs` file (see [ThemeRepository]) so existing saves keep
 * working unchanged.
 */
internal val Context.gameDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "memory_match_prefs",
)

/** Keys shared by more than one repository. Never rename one without a migration read. */
internal object GameKeys {
    val COINS = intPreferencesKey("user_coins")
    val HINT_TOKENS = intPreferencesKey("hint_tokens")
    val OWNED_COSMETICS = stringSetPreferencesKey("owned_cosmetics")
    val LAST_SEEN_DAY = longPreferencesKey("last_seen_day")
    val TASK_STATE = stringPreferencesKey("task_state_v1")

    /** Legacy per-theme purchase flags written by earlier builds. Read, never rewritten. */
    fun legacyThemePurchased(themeId: String) = booleanPreferencesKey("theme_purchased_$themeId")

    /** Coins are a virtual score, not currency. The cap only prevents integer overflow. */
    const val MAX_COINS = 999_999_999
}
