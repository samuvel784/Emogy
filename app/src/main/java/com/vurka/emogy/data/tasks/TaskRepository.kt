package com.vurka.emogy.data.tasks

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.vurka.emogy.data.GameKeys
import com.vurka.emogy.data.gameDataStore
import com.vurka.emogy.data.time.GameCalendar
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** What a single claim did. The UI maps this to an exact message, never a guess. */
sealed interface ClaimOutcome {
    data class Granted(val coins: Int, val hints: Int, val themeId: String?) : ClaimOutcome
    data object AlreadyClaimed : ClaimOutcome
    data object NotComplete : ClaimOutcome
    data object Expired : ClaimOutcome
    data object ClockAnomaly : ClaimOutcome
    data object Unknown : ClaimOutcome
}

data class ClaimBatch(
    val claimedCount: Int = 0,
    val coins: Int = 0,
    val hints: Int = 0,
    val themes: List<String> = emptyList(),
) {
    val isEmpty: Boolean get() = claimedCount == 0
}

/**
 * Durable task state.
 *
 * Every write is a single `DataStore.edit`, so a claim can never grant coins without also marking
 * the instance claimed (or vice versa). Double taps, recomposition, activity recreation and a
 * restart all observe the same persisted claim flag.
 */
class TaskRepository(
    private val dataStore: DataStore<Preferences>,
    private val calendar: GameCalendar,
) {
    constructor(context: Context) : this(context.gameDataStore, GameCalendar())

    private val engine = TaskEngine(calendar)

    val state: Flow<TaskState> = dataStore.data.map { decode(it) }

    val coins: Flow<Int> = dataStore.data.map { it[GameKeys.COINS] ?: 0 }

    val hintTokens: Flow<Int> = dataStore.data.map { (it[GameKeys.HINT_TOKENS] ?: 0).coerceAtLeast(0) }

    val ownedCosmetics: Flow<Set<String>> = dataStore.data.map {
        it[GameKeys.OWNED_COSMETICS] ?: emptySet()
    }

    val lastSeenDay: Flow<Long> = dataStore.data.map { it[GameKeys.LAST_SEEN_DAY] ?: 0L }

    /** Rolls the periods forward and persists the new definitions if anything changed. */
    suspend fun refresh() {
        dataStore.edit { prefs ->
            val today = calendar.today()
            val lastSeen = prefs[GameKeys.LAST_SEEN_DAY] ?: 0L
            if (today < lastSeen) return@edit
            val current = engine.ensureCurrent(decode(prefs), calendar.nowMillis())
            prefs[GameKeys.TASK_STATE] = TaskStateCodec.encode(current)
            if (today > lastSeen) prefs[GameKeys.LAST_SEEN_DAY] = today
        }
    }

    /**
     * Applies one finished level. Progress is written before the victory dialog's claim buttons can
     * be tapped, so a completed mission is immediately claimable.
     */
    suspend fun recordLevelCompleted(event: LevelCompletedEvent) {
        dataStore.edit { prefs ->
            val today = calendar.today()
            val eventDay = calendar.epochDayAt(event.atMillis)
            val lastSeen = prefs[GameKeys.LAST_SEEN_DAY] ?: 0L
            // A delayed event from an earlier local day must never write backwards into the period
            // that has already rolled over; a backwards clock must never grant progress either.
            if (today < lastSeen || eventDay != today) return@edit
            val current = engine.ensureCurrent(decode(prefs), event.atMillis)
            val next = engine.apply(current, event)
            prefs[GameKeys.TASK_STATE] = TaskStateCodec.encode(next)
            if (today > lastSeen) prefs[GameKeys.LAST_SEEN_DAY] = today
        }
    }

    suspend fun claim(period: TaskPeriod, definitionId: String): ClaimOutcome {
        var outcome: ClaimOutcome = ClaimOutcome.Unknown
        dataStore.edit { prefs ->
            val today = calendar.today()
            val now = calendar.nowMillis()
            val lastSeen = prefs[GameKeys.LAST_SEEN_DAY] ?: 0L
            if (today < lastSeen) {
                outcome = ClaimOutcome.ClockAnomaly
                return@edit
            }
            if (period == TaskPeriod.MONTHLY && !calendar.isMonthlyWindowOpen(today)) {
                outcome = ClaimOutcome.Expired
                return@edit
            }
            var current = engine.ensureCurrent(decode(prefs), now)
            val instance = current.period(period)?.instance(definitionId)
            outcome = when {
                instance == null -> ClaimOutcome.Expired
                instance.isClaimed -> ClaimOutcome.AlreadyClaimed
                !instance.isComplete -> ClaimOutcome.NotComplete
                else -> {
                    val reward = instance.definition.reward
                    grant(prefs, reward)
                    current = engine.markClaimed(current, period, definitionId, now)
                    current = engine.noteClaim(current, period, now)
                    prefs[GameKeys.TASK_STATE] = TaskStateCodec.encode(current)
                    prefs[GameKeys.LAST_SEEN_DAY] = maxOf(lastSeen, today)
                    ClaimOutcome.Granted(reward.coins, reward.hints, reward.themeId)
                }
            }
        }
        return outcome
    }

    /** Claims every completed, unclaimed mission in one cycle, atomically. */
    suspend fun claimAll(period: TaskPeriod): ClaimBatch {
        var batch = ClaimBatch()
        dataStore.edit { prefs ->
            val today = calendar.today()
            val now = calendar.nowMillis()
            val lastSeen = prefs[GameKeys.LAST_SEEN_DAY] ?: 0L
            if (today < lastSeen) return@edit
            if (period == TaskPeriod.MONTHLY && !calendar.isMonthlyWindowOpen(today)) return@edit

            var current = engine.ensureCurrent(decode(prefs), now)
            val ready = engine.readyToClaim(current, period)
            if (ready.isEmpty()) return@edit

            var coins = 0
            var hints = 0
            val themes = mutableListOf<String>()
            var count = 0
            for (instance in ready) {
                val reward = instance.definition.reward
                grant(prefs, reward)
                coins += reward.coins
                hints += reward.hints
                reward.themeId?.let { themes += it }
                count += 1
                current = engine.markClaimed(current, period, instance.definition.id, now)
                current = engine.noteClaim(current, period, now)
            }
            prefs[GameKeys.TASK_STATE] = TaskStateCodec.encode(current)
            prefs[GameKeys.LAST_SEEN_DAY] = maxOf(lastSeen, today)
            batch = ClaimBatch(count, coins, hints, themes)
        }
        return batch
    }

    /**
     * Monthly milestone: all five challenges claimed. The card back is permanent and granted only
     * once; the coins and hints are paid once per completed month.
     */
    suspend fun claimMonthlyMilestone(): ClaimOutcome {
        var outcome: ClaimOutcome = ClaimOutcome.Unknown
        dataStore.edit { prefs ->
            val today = calendar.today()
            val now = calendar.nowMillis()
            val lastSeen = prefs[GameKeys.LAST_SEEN_DAY] ?: 0L
            if (today < lastSeen) {
                outcome = ClaimOutcome.ClockAnomaly
                return@edit
            }
            if (!calendar.isMonthlyWindowOpen(today)) {
                outcome = ClaimOutcome.Expired
                return@edit
            }
            var current = engine.ensureCurrent(decode(prefs), now)
            val monthly = current.monthly
            outcome = when {
                monthly == null -> ClaimOutcome.Unknown
                monthly.milestoneClaimed -> ClaimOutcome.AlreadyClaimed
                !engine.monthlyMilestoneReady(current) -> ClaimOutcome.NotComplete
                else -> {
                    grant(prefs, MONTHLY_MILESTONE_REWARD)
                    current = engine.markMilestoneClaimed(current)
                    prefs[GameKeys.TASK_STATE] = TaskStateCodec.encode(current)
                    prefs[GameKeys.LAST_SEEN_DAY] = maxOf(lastSeen, today)
                    ClaimOutcome.Granted(
                        MONTHLY_MILESTONE_REWARD.coins,
                        MONTHLY_MILESTONE_REWARD.hints,
                        MONTHLY_MILESTONE_REWARD.themeId,
                    )
                }
            }
        }
        return outcome
    }

    private fun decode(prefs: Preferences): TaskState =
        TaskStateCodec.decode(prefs[GameKeys.TASK_STATE]) ?: TaskState()

    private fun grant(prefs: MutablePreferences, reward: TaskReward) {
        if (reward.coins > 0) {
            val next = ((prefs[GameKeys.COINS] ?: 0) + reward.coins)
                .coerceIn(0, GameKeys.MAX_COINS)
            prefs[GameKeys.COINS] = next
        }
        if (reward.hints > 0) {
            prefs[GameKeys.HINT_TOKENS] = (prefs[GameKeys.HINT_TOKENS] ?: 0) + reward.hints
        }
        val theme = reward.themeId
        if (!theme.isNullOrBlank()) {
            val owned = prefs[GameKeys.OWNED_COSMETICS] ?: emptySet()
            if (theme !in owned) prefs[GameKeys.OWNED_COSMETICS] = owned + theme
        }
    }

    companion object {
        val MONTHLY_MILESTONE_REWARD = TaskReward(
            coins = 3_000,
            hints = 5,
            themeId = "eclipse_sovereign",
        )
    }
}
