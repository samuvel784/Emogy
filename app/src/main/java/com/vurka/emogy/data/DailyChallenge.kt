package com.vurka.emogy.data

import com.vurka.emogy.model.LevelConfig
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * One deterministic board per local calendar day.
 *
 * The board is derived entirely from the day number, so two players on the same date get the same
 * cards without a server, an account or a network call. "Day" is the local calendar day, which
 * matches the streak maths and makes the reset happen at local midnight rather than at a UTC
 * boundary.
 */
object DailyChallenge {
    /** A sentinel used by the UI; daily runs are never recorded as a numbered level. */
    const val DAILY_LEVEL_ID = 0

    const val PAIR_COUNT = 8
    const val TIME_LIMIT_SEC = 120
    const val MAX_HINTS = 3
    const val PREVIEW_SEC = 3
    const val REWARD_COINS = 25

    private const val MILLIS_PER_DAY = 86_400_000L

    val categories = listOf("Animals", "Food", "Nature", "Faces", "Travel", "Fantasy")

    /** Shared by every daily run; only the seed changes. */
    val config = LevelConfig(
        levelNumber = DAILY_LEVEL_ID,
        pairCount = PAIR_COUNT,
        categories = categories,
        previewTimeSec = PREVIEW_SEC,
        timeLimitSec = TIME_LIMIT_SEC,
        moveLimit = 0,
        difficultyLabel = "Daily",
        shuffleIntervalSec = 0,
        maxHints = MAX_HINTS,
        mismatchDelayMs = 1_000,
        autoHideMs = 0,
        isBoss = false,
    )

    fun epochDay(nowMillis: Long, timeZone: TimeZone = TimeZone.getDefault()): Long =
        StreakCalculator.localEpochDay(nowMillis, timeZone)

    /**
     * Human-readable date for a stored daily epoch day.
     *
     * This deliberately does not use "now": if the app stays open across midnight, a run opened for
     * yesterday's board must keep yesterday's label. The local wall-clock millis are formatted in
     * UTC so the date cannot shift again when the formatter applies a zone offset.
     */
    fun dateLabel(
        epochDay: Long,
        timeZone: TimeZone = TimeZone.getDefault(),
        locale: Locale = Locale.getDefault(),
    ): String {
        val dayStart = epochDay * MILLIS_PER_DAY
        val wallMillis = dayStart - timeZone.getOffset(dayStart)
        return DateFormat.getDateInstance(DateFormat.FULL, locale).apply {
            this.timeZone = timeZone
        }.format(Date(wallMillis))
    }

    /**
     * Stable, well-spread seed. A different day always produces a different shuffle, but the same
     * day and the same deck generation always produce the same board.
     */
    fun seedFor(epochDay: Long): Long = epochDay * 1_000_003L + 0x4D454D4F4A49L
}

/** Locally stored daily-challenge record. */
data class DailyChallengeProgress(
    /** Last day (local epoch day) that was completed. 0 means never. */
    val lastCompletedDay: Long = 0L,
    val bestTimeSec: Int = 0,
    val bestMoves: Int = 0,
    val completedCount: Int = 0,
) {
    /**
     * `>=` rather than `==` so moving the device clock backwards cannot pay the same day twice.
     */
    fun isCompleted(day: Long): Boolean = day > 0L && lastCompletedDay >= day
}

data class DailyOutcome(
    val isFirstCompletion: Boolean,
    val coinsAwarded: Int,
    val bestTimeSec: Int,
    val bestMoves: Int,
    val isPersonalBestTime: Boolean,
)

/** Pure daily reward/best-score rules, kept out of DataStore so they can be unit-tested. */
object DailyChallengeRules {
    fun outcome(
        previous: DailyChallengeProgress,
        day: Long,
        moves: Int,
        timeSec: Int,
    ): DailyOutcome {
        val first = !previous.isCompleted(day)
        val safeMoves = moves.coerceAtLeast(1)
        val safeTime = timeSec.coerceAtLeast(1)

        val betterTime = previous.bestTimeSec <= 0 || safeTime < previous.bestTimeSec
        val betterMoves = previous.bestMoves <= 0 || safeMoves < previous.bestMoves

        return DailyOutcome(
            isFirstCompletion = first,
            coinsAwarded = if (first) DailyChallenge.REWARD_COINS else 0,
            bestTimeSec = if (betterTime) safeTime else previous.bestTimeSec,
            bestMoves = if (betterMoves) safeMoves else previous.bestMoves,
            isPersonalBestTime = betterTime,
        )
    }
}
