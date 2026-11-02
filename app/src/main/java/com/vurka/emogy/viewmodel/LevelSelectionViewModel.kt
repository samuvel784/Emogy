package com.vurka.emogy.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vurka.emogy.data.CoinTransaction
import com.vurka.emogy.data.LevelCatalog
import com.vurka.emogy.data.ProgressRepository
import com.vurka.emogy.data.AudioSettings
import com.vurka.emogy.data.DailyChallengeProgress
import com.vurka.emogy.data.ThemeRepository
import com.vurka.emogy.data.store.CosmeticKind
import com.vurka.emogy.data.store.PurchaseResult
import com.vurka.emogy.data.store.StoreRepository
import com.vurka.emogy.model.CardBackTheme
import com.vurka.emogy.model.LevelConfig
import com.vurka.emogy.util.AudioMix
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LevelSelectionViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ProgressRepository(application)
    private val themeRepository = ThemeRepository(application)
    private val storeRepository = StoreRepository(application)

    val lastPlayedLevel: StateFlow<Int> = repository.lastPlayedLevel.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        // 0 = "still loading", so the UI never mistakes a default for real progress.
        initialValue = 0
    )

    /** Levels the player has genuinely completed. Level availability never derives from this. */
    val completedLevels: StateFlow<Set<Int>> = repository.completedLevels.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptySet()
    )

    val totalLevelsCompleted: StateFlow<Int> = repository.totalLevelsCompleted.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    val highestCompletedLevel: StateFlow<Int> = repository.highestCompletedLevel.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    fun setLastPlayedLevel(level: Int) {
        viewModelScope.launch {
            repository.setLastPlayedLevel(level)
        }
    }

    val soundEnabled: StateFlow<Boolean> = repository.soundEnabled.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = true
    )

    val masterVolume: StateFlow<Float> = repository.masterVolume.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AudioMix.DEFAULT_MASTER
    )

    val musicVolume: StateFlow<Float> = repository.musicVolume.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AudioMix.DEFAULT_MUSIC
    )

    val effectsVolume: StateFlow<Float> = repository.effectsVolume.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AudioMix.DEFAULT_EFFECTS
    )

    /**
     * Null until DataStore has produced a real snapshot. The UI uses it to avoid starting music
     * with default volumes for a player who has muted the app.
     */
    val audioSettings: StateFlow<AudioSettings?> = repository.audioSettings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val hapticsEnabled: StateFlow<Boolean> = repository.hapticsEnabled.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = true
    )

    val themeMode: StateFlow<String> = repository.themeMode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = "Default"
    )

    val isAssistMode: StateFlow<Boolean> = repository.isAssistMode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    fun setIsAssistMode(enabled: Boolean) {
        viewModelScope.launch {
            repository.setIsAssistMode(enabled)
        }
    }

    /**
     * Null until DataStore answers, so a returning player never sees the first-run sheet flash
     * while the real value is loading.
     */
    val welcomeSeen: StateFlow<Boolean?> = repository.welcomeSeen.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    fun markWelcomeSeen() {
        viewModelScope.launch {
            repository.markWelcomeSeen()
        }
    }

    val allLevels: List<LevelConfig> = LevelCatalog.getAllLevels()

    fun setSoundEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setSoundEnabled(enabled)
        }
    }

    fun setMasterVolume(volume: Float) {
        viewModelScope.launch {
            repository.setMasterVolume(volume)
        }
    }

    fun setMusicVolume(volume: Float) {
        viewModelScope.launch {
            repository.setMusicVolume(volume)
        }
    }

    fun setEffectsVolume(volume: Float) {
        viewModelScope.launch {
            repository.setEffectsVolume(volume)
        }
    }

    fun setHapticsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setHapticsEnabled(enabled)
        }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch {
            repository.setThemeMode(mode)
        }
    }

    val starsMap: StateFlow<Map<Int, Int>> = repository.levelStars.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyMap()
    )

    val totalStars: StateFlow<Int> = repository.totalStarsEarned.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    fun getStars(level: Int): Flow<Int> = repository.getLevelStars(level)
    fun getBestMoves(level: Int): Flow<Int> = repository.getLevelBestMoves(level)
    fun getBestTime(level: Int): Flow<Int> = repository.getLevelBestTime(level)

    val themes = listOf(
        CardBackTheme("default", "Classic Blue", "The original design", 0, "🃏"),
        CardBackTheme("red_crimson", "Crimson Flame", "Complete 50 levels", 50, "🔥"),
        CardBackTheme("aurora_mint", "Aurora Mint", "Complete 100 levels", 100, "🍀"),
        CardBackTheme("royal_gold", "Royal Gold", "Complete 200 levels", 200, "👑"),
        CardBackTheme("neon_candy", "Neon Candy", "Complete 350 levels", 350, "🍭"),
        CardBackTheme("space_void", "Space Void", "Complete 500 levels", 500, "🌌"),
        CardBackTheme("dragon_fire", "Dragon Fire", "Complete 800 levels", 800, "🐉"),
        CardBackTheme("starlight_oath", "Starlight Oath", "30-day login streak reward", Int.MAX_VALUE, "🌠"),
        CardBackTheme("eclipse_sovereign", "Eclipse Sovereign", "Monthly milestone reward", Int.MAX_VALUE, "🌒")
    )

    val selectedCardThemeId: StateFlow<String> = themeRepository.selectedCardThemeId.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ThemeRepository.DEFAULT_CARD_THEME
    )

    val selectedBoardThemeId: StateFlow<String> = themeRepository.selectedBoardThemeId.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ThemeRepository.DEFAULT_BOARD
    )

    val selectedMatchEffectId: StateFlow<String> = themeRepository.selectedMatchEffectId.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ThemeRepository.DEFAULT_MATCH_EFFECT
    )

    val selectedVictoryEffectId: StateFlow<String> = themeRepository.selectedVictoryEffectId.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ThemeRepository.DEFAULT_VICTORY_EFFECT
    )

    /** Purchased hints, shared with the game HUD and the store. */
    val hintTokens: StateFlow<Int> = storeRepository.hintTokens.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    /** Every cosmetic the player owns, including legacy purchases and milestone rewards. */
    val ownedCosmetics: StateFlow<Set<String>> = repository.ownedCosmetics.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptySet()
    )

    fun equip(kind: CosmeticKind, id: String) {
        viewModelScope.launch {
            themeRepository.equip(kind, id)
        }
    }

    val selectedThemeId: StateFlow<String> = themeRepository.selectedThemeId.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = "default"
    )

    fun setSelectedThemeId(id: String) {
        viewModelScope.launch {
            themeRepository.setSelectedThemeId(id)
        }
    }

    val dailyStreak: StateFlow<Int> = repository.dailyStreak.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    val dailyChallenge: StateFlow<DailyChallengeProgress> = repository.dailyChallenge.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DailyChallengeProgress()
    )

    val totalGamesPlayed: StateFlow<Int> = repository.totalGamesPlayed.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    val perfectGames: StateFlow<Int> = repository.perfectGames.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    val coins: StateFlow<Int> = repository.coins.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    val totalCardFlips: StateFlow<Int> = repository.totalCardFlips.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    val animalCardFlips: StateFlow<Int> = repository.animalCardFlips.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    /** Emoji ids the player has matched, powering the offline album. */
    val collectedEmojis: StateFlow<Set<String>> = repository.collectedEmojis.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptySet()
    )

    /** Emoji ids the player has claimed in the offline album. */
    val claimedAlbumEmojis: StateFlow<Set<String>> = repository.claimedAlbumEmojis.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptySet()
    )

    fun claimAlbumEmoji(emojiId: String, onClaimed: (Int) -> Unit = {}) {
        viewModelScope.launch {
            if (repository.claimAlbumEmoji(emojiId)) {
                onClaimed(ProgressRepository.ALBUM_EMOJI_REWARD_COINS)
            }
        }
    }

    fun claimAllAlbumEmojis(emojiIds: Set<String>, onClaimed: (Int) -> Unit = {}) {
        viewModelScope.launch {
            val coinsEarned = repository.claimAllAlbumEmojis(emojiIds)
            if (coinsEarned > 0) {
                onClaimed(coinsEarned)
            }
        }
    }

    val coinTransactions: StateFlow<List<CoinTransaction>> = repository.coinTransactions.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun isCategoryRewardClaimed(categoryName: String): Flow<Boolean> =
        repository.isCategoryRewardClaimed(categoryName)

    fun claimCategoryReward(categoryName: String, coinAmount: Int = 100, onClaimed: (Int) -> Unit = {}) {
        viewModelScope.launch {
            if (repository.claimCategoryReward(categoryName, coinAmount)) {
                onClaimed(coinAmount)
            }
        }
    }

    fun claimReward(rewardId: String, coinAmount: Int) {
        viewModelScope.launch {
            repository.claimReward(rewardId, coinAmount)
        }
    }

    fun isRewardClaimed(rewardId: String): Flow<Boolean> = repository.isRewardClaimed(rewardId)

    val dailyClaimedCount: StateFlow<Int> = repository.dailyClaimedCount.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    val weeklyClaimedCount: StateFlow<Int> = repository.weeklyClaimedCount.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    fun isThemePurchased(themeId: String): Flow<Boolean> = combine(
        repository.isThemePurchased(themeId),
        repository.ownedCosmetics,
    ) { legacy, owned -> legacy || themeId in owned }

    /** One atomic transaction in [StoreRepository]; the UI only reacts to the result. */
    suspend fun purchase(itemId: String): PurchaseResult = storeRepository.purchase(itemId)

    fun updatePlayStats(isPerfect: Boolean) {
        viewModelScope.launch {
            repository.updatePlayStats(isPerfect)
        }
    }

    fun updateDailyStreak() {
        viewModelScope.launch {
            repository.updateDailyStreak()
        }
    }

    fun resetAllProgress() {
        viewModelScope.launch {
            repository.resetProgress()
            // Ownership is wiped with the save, so a paid card back must not stay equipped.
            themeRepository.resetSelections()
        }
    }
}
