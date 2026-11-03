package com.vurka.emogy.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.vurka.emogy.data.store.CosmeticKind
import com.vurka.emogy.data.store.StoreCatalog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore(name = "theme_prefs")

/**
 * Equipped cosmetics. Kept in its own file (as before) so existing players keep their selected card
 * back, while ownership and coins stay in the main save.
 */
class ThemeRepository(private val context: Context) {
    private val dataStore = context.themeDataStore

    companion object {
        const val DEFAULT_CARD_BACK = "default"
        const val DEFAULT_CARD_THEME = "classic"
        const val DEFAULT_BOARD = "board_default"
        const val DEFAULT_MATCH_EFFECT = "sparkle_none"
        const val DEFAULT_VICTORY_EFFECT = "confetti_classic"

        private val SELECTED_THEME_ID_KEY = stringPreferencesKey("selected_theme_id")
        private val SELECTED_CARD_THEME_KEY = stringPreferencesKey("selected_card_theme")
        private val SELECTED_BOARD_KEY = stringPreferencesKey("selected_board_theme")
        private val SELECTED_MATCH_KEY = stringPreferencesKey("selected_match_effect")
        private val SELECTED_VICTORY_KEY = stringPreferencesKey("selected_victory_effect")
    }

    /** Selected card back. The name is kept for every existing call site. */
    val selectedThemeId: Flow<String> = dataStore.data.map { prefs ->
        prefs[SELECTED_THEME_ID_KEY] ?: DEFAULT_CARD_BACK
    }

    val selectedCardThemeId: Flow<String> = dataStore.data.map { prefs ->
        prefs[SELECTED_CARD_THEME_KEY] ?: DEFAULT_CARD_THEME
    }

    val selectedBoardThemeId: Flow<String> = dataStore.data.map { prefs ->
        prefs[SELECTED_BOARD_KEY] ?: DEFAULT_BOARD
    }

    val selectedMatchEffectId: Flow<String> = dataStore.data.map { prefs ->
        prefs[SELECTED_MATCH_KEY] ?: DEFAULT_MATCH_EFFECT
    }

    val selectedVictoryEffectId: Flow<String> = dataStore.data.map { prefs ->
        prefs[SELECTED_VICTORY_KEY] ?: DEFAULT_VICTORY_EFFECT
    }

    suspend fun setSelectedThemeId(id: String, isOwned: (String) -> Boolean = { true }) {
        val cosmetic = StoreCatalog.cosmetic(id)
        if (cosmetic != null && (cosmetic.default || isOwned(id))) {
            dataStore.edit { it[SELECTED_THEME_ID_KEY] = id }
        }
    }

    suspend fun equip(kind: CosmeticKind, id: String, isOwned: (String) -> Boolean = { true }) {
        val cosmetic = StoreCatalog.cosmetic(id)
        if (cosmetic != null && cosmetic.kind == kind && (cosmetic.default || isOwned(id))) {
            dataStore.edit { prefs ->
                when (kind) {
                    CosmeticKind.CARD_BACK -> prefs[SELECTED_THEME_ID_KEY] = id
                    CosmeticKind.CARD_THEME -> prefs[SELECTED_CARD_THEME_KEY] = id
                    CosmeticKind.BOARD_THEME -> prefs[SELECTED_BOARD_KEY] = id
                    CosmeticKind.MATCH_EFFECT -> prefs[SELECTED_MATCH_KEY] = id
                    CosmeticKind.VICTORY_EFFECT -> prefs[SELECTED_VICTORY_KEY] = id
                }
            }
        }
    }

    /** Called after a full progress reset so a paid theme is not left equipped but unowned. */
    suspend fun resetSelections() {
        dataStore.edit { prefs ->
            prefs[SELECTED_THEME_ID_KEY] = DEFAULT_CARD_BACK
            prefs[SELECTED_CARD_THEME_KEY] = DEFAULT_CARD_THEME
            prefs[SELECTED_BOARD_KEY] = DEFAULT_BOARD
            prefs[SELECTED_MATCH_KEY] = DEFAULT_MATCH_EFFECT
            prefs[SELECTED_VICTORY_KEY] = DEFAULT_VICTORY_EFFECT
        }
    }
}
