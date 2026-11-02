package com.vurka.emogy.data.tasks

/** The three independent task cycles. They never share a reset counter. */
enum class TaskPeriod { DAILY, WEEKLY, MONTHLY }

/** Difficulty only drives generation quotas and card colour; it is not persisted. */
enum class TaskDifficulty { EASY, MEDIUM, HARD }

/** Every rule here is measured from an authoritative gameplay event. */
enum class TaskRule {
    COMPLETE_LEVELS,
    COMPLETE_DISTINCT_LEVELS,
    MATCH_PAIRS,
    MATCH_DISTINCT_PAIRS,
    EARN_STARS,
    PERFECT_LEVELS,
    LOW_MISTAKE_LEVELS,
    EFFICIENT_LEVELS,
    NO_HINT_LEVELS,
    USE_HINTS,
    NO_SHUFFLE_LEVELS,
    BOSS_LEVELS,
    TIMED_LEVELS,
    HIGH_DIFFICULTY_LEVELS,
    DISTINCT_WORLDS,
    CLAIM_DAILY_TASKS,
    CLAIM_WEEKLY_TASKS,
    EARN_GAMEPLAY_COINS,
}

/** A reward is always granted in the same transaction as the claim that earned it. */
data class TaskReward(
    val coins: Int = 0,
    val hints: Int = 0,
    val themeId: String? = null,
) {
    val isEmpty: Boolean get() = coins == 0 && hints == 0 && themeId == null
}

/**
 * One curated mission. [id] is permanent: progress, claims and statistics all key off it, never
 * off the visible title (two cycles may show the same title with different targets).
 */
data class TaskDefinition(
    val id: String,
    val period: TaskPeriod,
    val rule: TaskRule,
    val difficulty: TaskDifficulty,
    val title: String,
    val description: String,
    val target: Int,
    val reward: TaskReward,
    /** Only one mission per group can appear in a generated set. */
    val exclusiveGroup: String = id,
)

/** One generated task for one concrete period, with its own progress and claim state. */
data class TaskInstance(
    val definition: TaskDefinition,
    val progress: Int = 0,
    val distinct: Set<String> = emptySet(),
    val completedAtMillis: Long = 0L,
    val claimedAtMillis: Long = 0L,
) {
    val isComplete: Boolean get() = progress >= definition.target
    val isClaimed: Boolean get() = claimedAtMillis > 0L
}

/** Everything generated and claimed inside one period. */
data class TaskPeriodState(
    val period: TaskPeriod,
    val key: String,
    val startEpochDay: Long,
    val instances: List<TaskInstance> = emptyList(),
    val milestoneClaimed: Boolean = false,
) {
    fun instance(id: String): TaskInstance? = instances.firstOrNull { it.definition.id == id }
}

/**
 * One finished level, reported by the game engine after the reward write succeeded.
 *
 * This is the only source of task progress. Visiting a screen, tapping a button or opening the
 * Tasks page never advances a mission.
 */
data class LevelCompletedEvent(
    val atMillis: Long,
    val levelId: Int,
    val world: Int,
    val stars: Int,
    val pairs: Int,
    val distinctEmojiIds: Set<String>,
    val moves: Int,
    val mistakes: Int,
    val hintsUsed: Int,
    val shuffles: Int,
    val seconds: Int,
    val coins: Int,
    val isBoss: Boolean,
    val isDaily: Boolean,
    val isTimed: Boolean,
    val isHighDifficulty: Boolean,
) {
    val perfect: Boolean get() = mistakes == 0
    val lowMistake: Boolean get() = mistakes <= 1
    /** At most two attempts per pair: always defined, unlike a per-level move limit of 0. */
    val efficient: Boolean get() = pairs > 0 && moves <= pairs * 2
    val levelKey: String get() = if (isDaily) "daily" else levelId.toString()
}

/** The whole task state, one slot per cycle. */
data class TaskState(
    val daily: TaskPeriodState? = null,
    val weekly: TaskPeriodState? = null,
    val monthly: TaskPeriodState? = null,
) {
    fun period(period: TaskPeriod): TaskPeriodState? = when (period) {
        TaskPeriod.DAILY -> daily
        TaskPeriod.WEEKLY -> weekly
        TaskPeriod.MONTHLY -> monthly
    }

    fun with(period: TaskPeriodState): TaskState = when (period.period) {
        TaskPeriod.DAILY -> copy(daily = period)
        TaskPeriod.WEEKLY -> copy(weekly = period)
        TaskPeriod.MONTHLY -> copy(monthly = period)
    }
}
