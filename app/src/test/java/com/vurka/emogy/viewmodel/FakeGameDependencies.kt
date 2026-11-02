package com.vurka.emogy.viewmodel

import com.vurka.emogy.data.DailyChallengeProgress
import com.vurka.emogy.data.DailyChallengeRules
import com.vurka.emogy.data.DailyOutcome
import com.vurka.emogy.data.GameSnapshot
import com.vurka.emogy.data.LevelRecordResult
import com.vurka.emogy.data.tasks.LevelCompletedEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeClock(var now: Long = 1_700_000_000_000L) : GameClock {
    override fun nowMillis(): Long = now
}

data class FeedbackCall(
    val cue: GameCue,
    val combo: Int,
    val soundEnabled: Boolean,
    val hapticsEnabled: Boolean,
)

class FakeFeedback : GameFeedback {
    val calls = mutableListOf<FeedbackCall>()

    override fun play(
        cue: GameCue,
        combo: Int,
        soundEnabled: Boolean,
        hapticsEnabled: Boolean,
    ) {
        calls += FeedbackCall(cue, combo, soundEnabled, hapticsEnabled)
    }
}

/**
 * [initialAssist] mirrors the persisted Assist-mode toggle.
 */
class FakeSettings(
    initialAssist: Boolean = false,
) : GameSettings {
    override val soundEnabled = MutableStateFlow(true)
    override val hapticsEnabled = MutableStateFlow(true)
    override val assistMode = MutableStateFlow(initialAssist)
}

data class RecordedLevel(
    val level: Int,
    val stars: Int,
    val moves: Int,
    val timeSec: Int,
    val result: LevelRecordResult,
)

data class RecordedDaily(
    val day: Long,
    val moves: Int,
    val timeSec: Int,
    val outcome: DailyOutcome,
)

class FakeProgress : GameProgress {
    val records = mutableListOf<RecordedLevel>()
    val dailyRecords = mutableListOf<RecordedDaily>()
    val playStats = mutableListOf<Boolean>()
    var losses = 0
    var streakUpdates = 0
    var flips = 0
    var lastPlayedLevel = 0
    val collectedEmojis = mutableSetOf<String>()
    private val starsByLevel = mutableMapOf<Int, Int>()
    private var daily = DailyChallengeProgress()

    override suspend fun setLastPlayedLevel(level: Int) {
        lastPlayedLevel = level
    }

    override suspend fun recordLevelResult(
        level: Int,
        stars: Int,
        moves: Int,
        timeSec: Int,
    ): LevelRecordResult {
        val safeStars = stars.coerceIn(0, 3)
        val existing = starsByLevel[level] ?: 0
        val firstClear = existing <= 0
        val coins = if (firstClear) 15 + 5 * safeStars else 0
        val result = LevelRecordResult(
            stars = maxOf(existing, safeStars),
            isFirstClear = firstClear,
            coinsAwarded = coins,
            isPersonalBest = true,
        )
        starsByLevel[level] = maxOf(existing, safeStars)
        records += RecordedLevel(level, safeStars, moves, timeSec, result)
        return result
    }

    override suspend fun recordDailyChallenge(
        day: Long,
        moves: Int,
        timeSec: Int,
    ): DailyOutcome {
        val outcome = DailyChallengeRules.outcome(daily, day, moves, timeSec)
        daily = DailyChallengeProgress(
            lastCompletedDay = maxOf(daily.lastCompletedDay, day),
            bestTimeSec = outcome.bestTimeSec,
            bestMoves = outcome.bestMoves,
            completedCount = daily.completedCount + if (outcome.isFirstCompletion) 1 else 0,
        )
        dailyRecords += RecordedDaily(day, moves, timeSec, outcome)
        return outcome
    }

    override suspend fun updateDailyStreak() {
        streakUpdates++
    }
    override suspend fun updatePlayStats(isPerfect: Boolean) {
        if (!isPerfect) losses++
        playStats += isPerfect
    }

    override suspend fun incrementCardFlips(count: Int, isAnimal: Boolean) {
        flips += count
    }

    override suspend fun collectEmoji(emojiId: String) {
        collectedEmojis += emojiId
    }
}

class FakeSnapshotStore(var saved: GameSnapshot? = null) : GameSnapshotStore {
    override suspend fun load(): GameSnapshot? = saved
    override suspend fun save(snapshot: GameSnapshot) {
        saved = snapshot
    }
    override suspend fun clear() {
        saved = null
    }
}

/** Atomic fake wallet: a consume either spends one token or fails, never both. */
class FakeHintWallet(initialTokens: Int = 0) : HintWallet {
    private val state = MutableStateFlow(initialTokens.coerceAtLeast(0))
    override val tokens: Flow<Int> = state
    val current: Int get() = state.value
    var consumeAttempts = 0
        private set

    override suspend fun tryConsume(): Boolean {
        consumeAttempts++
        val current = state.value
        if (current <= 0) return false
        state.value = current - 1
        return true
    }
}

class FakeGameplayTracker : GameplayTracker {
    val events = mutableListOf<LevelCompletedEvent>()
    var calls = 0
        private set

    override suspend fun record(event: LevelCompletedEvent) {
        calls++
        events += event
    }
}
