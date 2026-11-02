package com.vurka.emogy.viewmodel

import android.content.Context
import com.vurka.emogy.data.DailyOutcome
import com.vurka.emogy.data.GameSnapshot
import com.vurka.emogy.data.LevelRecordResult
import com.vurka.emogy.data.ProgressRepository
import com.vurka.emogy.data.tasks.LevelCompletedEvent
import com.vurka.emogy.data.tasks.TaskRepository
import com.vurka.emogy.util.HapticManager
import com.vurka.emogy.util.SoundManager
import kotlinx.coroutines.flow.Flow

/** Bridges [GameFeedback] to the real SoundPool/Haptic engines. */
class AndroidGameFeedback(context: Context) : GameFeedback {
    private val appContext = context.applicationContext

    override fun play(
        cue: GameCue,
        combo: Int,
        soundEnabled: Boolean,
        hapticsEnabled: Boolean,
    ) {
        when (cue) {
            GameCue.FLIP -> {
                SoundManager.playFlip(soundEnabled)
                HapticManager.triggerFlip(appContext, hapticsEnabled)
            }
            GameCue.MATCH -> {
                SoundManager.playMatch(combo, soundEnabled)
                HapticManager.triggerMatch(appContext, hapticsEnabled)
            }
            GameCue.MISMATCH -> {
                SoundManager.playMismatch(soundEnabled)
                HapticManager.triggerMismatch(appContext, hapticsEnabled)
            }
            GameCue.HINT -> {
                SoundManager.playHint(soundEnabled)
                HapticManager.triggerHint(appContext, hapticsEnabled)
            }
            GameCue.SHUFFLE -> {
                SoundManager.playShuffle(soundEnabled)
                HapticManager.triggerHint(appContext, hapticsEnabled)
            }
            GameCue.VICTORY -> {
                SoundManager.playVictory(soundEnabled)
                HapticManager.triggerVictory(appContext, hapticsEnabled)
            }
            GameCue.DEFEAT -> {
                SoundManager.playDefeat(soundEnabled)
                HapticManager.triggerMismatch(appContext, hapticsEnabled)
            }
            GameCue.PAUSE -> SoundManager.playPause(soundEnabled)
            GameCue.TICK -> SoundManager.playTick(urgent = false, enabled = soundEnabled)
            GameCue.TICK_URGENT -> SoundManager.playTick(urgent = true, enabled = soundEnabled)
        }
    }
}

class RepositoryGameProgress(private val repository: ProgressRepository) : GameProgress {
    override suspend fun setLastPlayedLevel(level: Int) = repository.setLastPlayedLevel(level)

    override suspend fun recordLevelResult(
        level: Int,
        stars: Int,
        moves: Int,
        timeSec: Int,
    ): LevelRecordResult = repository.recordLevelResult(level, stars, moves, timeSec)

    override suspend fun recordDailyChallenge(
        day: Long,
        moves: Int,
        timeSec: Int,
    ): DailyOutcome = repository.recordDailyChallenge(day, moves, timeSec)

    override suspend fun updateDailyStreak() = repository.updateDailyStreak()

    override suspend fun updatePlayStats(isPerfect: Boolean) = repository.updatePlayStats(isPerfect)

    override suspend fun incrementCardFlips(count: Int, isAnimal: Boolean) =
        repository.incrementCardFlips(count, isAnimal)

    override suspend fun collectEmoji(emojiId: String) = repository.collectEmoji(emojiId)
}

class RepositoryGameSettings(private val repository: ProgressRepository) : GameSettings {
    override val soundEnabled: Flow<Boolean> = repository.soundEnabled
    override val hapticsEnabled: Flow<Boolean> = repository.hapticsEnabled
    override val assistMode: Flow<Boolean> = repository.isAssistMode
}

class RepositoryGameSnapshotStore(private val repository: ProgressRepository) : GameSnapshotStore {
    override suspend fun load(): GameSnapshot? = repository.loadGameSnapshot()
    override suspend fun save(snapshot: GameSnapshot) = repository.saveGameSnapshot(snapshot)
    override suspend fun clear() = repository.clearGameSnapshot()
}

class RepositoryHintWallet(private val repository: ProgressRepository) : HintWallet {
    override val tokens: Flow<Int> = repository.hintTokens
    override suspend fun tryConsume(): Boolean = repository.tryConsumeHintToken()
}

class RepositoryGameplayTracker(private val repository: TaskRepository) : GameplayTracker {
    override suspend fun record(event: LevelCompletedEvent) = repository.recordLevelCompleted(event)
}
