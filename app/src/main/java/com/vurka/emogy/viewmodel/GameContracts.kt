package com.vurka.emogy.viewmodel

import com.vurka.emogy.data.DailyOutcome
import com.vurka.emogy.data.GameSnapshot
import com.vurka.emogy.data.LevelRecordResult
import com.vurka.emogy.data.tasks.LevelCompletedEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Wall clock used only for timestamps and snapshot expiry, so tests never depend on the device. */
fun interface GameClock {
    fun nowMillis(): Long
}

object SystemGameClock : GameClock {
    override fun nowMillis(): Long = System.currentTimeMillis()
}

/** Every audible/haptic moment the game can produce, independent of the Android engines. */
enum class GameCue {
    FLIP,
    MATCH,
    MISMATCH,
    HINT,
    SHUFFLE,
    VICTORY,
    DEFEAT,
    PAUSE,
    TICK,
    TICK_URGENT,
}

interface GameFeedback {
    fun play(
        cue: GameCue,
        combo: Int = 0,
        soundEnabled: Boolean,
        hapticsEnabled: Boolean,
    )
}

/**
 * Settings the game needs while a run is active.
 */
interface GameSettings {
    val soundEnabled: Flow<Boolean>
    val hapticsEnabled: Flow<Boolean>

    /** Opt-in Assist mode: a heart button reveals one matching pair on request. */
    val assistMode: Flow<Boolean>
}

/** Purchased hint inventory. A token is spent only by [tryConsume], which is atomic. */
interface HintWallet {
    val tokens: Flow<Int>
    suspend fun tryConsume(): Boolean
}

/** Used by unit tests and by any screen that runs without a wallet. */
object NoHintWallet : HintWallet {
    override val tokens: Flow<Int> = flowOf(0)
    override suspend fun tryConsume(): Boolean = false
}

/** The single authoritative sink for task progress. Never called from a composable. */
fun interface GameplayTracker {
    suspend fun record(event: LevelCompletedEvent)
}

object NoGameplayTracker : GameplayTracker {
    override suspend fun record(event: LevelCompletedEvent) = Unit
}

/** The progress writes a finished run needs. [ProgressRepository] is the production implementation. */
interface GameProgress {
    suspend fun setLastPlayedLevel(level: Int)

    suspend fun recordLevelResult(
        level: Int,
        stars: Int,
        moves: Int,
        timeSec: Int,
    ): LevelRecordResult

    suspend fun recordDailyChallenge(
        day: Long,
        moves: Int,
        timeSec: Int,
    ): DailyOutcome

    suspend fun updateDailyStreak()

    suspend fun updatePlayStats(isPerfect: Boolean)

    suspend fun incrementCardFlips(count: Int = 1, isAnimal: Boolean = false)

    /** Adds one matched emoji to the offline album. Idempotent. */
    suspend fun collectEmoji(emojiId: String)
}

interface GameSnapshotStore {
    suspend fun load(): GameSnapshot?
    suspend fun save(snapshot: GameSnapshot)
    suspend fun clear()
}
