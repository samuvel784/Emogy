package com.vurka.emogy.data.tasks

import kotlin.random.Random

/**
 * Central task configuration: every target, title and reward lives here, never in a screen.
 * Tuning the economy is a one-file change.
 *
 * Only rules the engine can measure from a real finished level are used. Unfair "complete a
 * specific world" style missions are expressed as "different worlds" instead, because every one
 * of the 1000 levels is always selectable.
 */
object TaskConfig {

    private fun daily(
        id: String,
        rule: TaskRule,
        difficulty: TaskDifficulty,
        target: Int,
        title: String,
        description: String,
        reward: TaskReward,
        group: String,
    ) = TaskDefinition(id, TaskPeriod.DAILY, rule, difficulty, title, description, target, reward, group)

    private fun weekly(
        id: String,
        rule: TaskRule,
        difficulty: TaskDifficulty,
        target: Int,
        title: String,
        description: String,
        reward: TaskReward,
        group: String,
    ) = TaskDefinition(id, TaskPeriod.WEEKLY, rule, difficulty, title, description, target, reward, group)

    private fun monthly(
        id: String,
        rule: TaskRule,
        target: Int,
        title: String,
        description: String,
        reward: TaskReward,
    ) = TaskDefinition(
        id, TaskPeriod.MONTHLY, rule, TaskDifficulty.HARD, title, description, target, reward, id,
    )

    // ---------------------------------------------------------------- daily (5 per day)

    private val dailyEasy = listOf(
        daily(
            "d_clear_2", TaskRule.COMPLETE_LEVELS, TaskDifficulty.EASY, 2,
            "Complete 2 levels", "Finish any 2 levels today.", TaskReward(coins = 50), "daily_levels",
        ),
        daily(
            "d_clear_3", TaskRule.COMPLETE_LEVELS, TaskDifficulty.EASY, 3,
            "Complete 3 levels", "Finish any 3 levels today.", TaskReward(coins = 60), "daily_levels",
        ),
        daily(
            "d_pairs_15", TaskRule.MATCH_PAIRS, TaskDifficulty.EASY, 15,
            "Match 15 pairs", "Flip matching emoji pairs in any levels.", TaskReward(coins = 45), "daily_pairs",
        ),
        daily(
            "d_stars_3", TaskRule.EARN_STARS, TaskDifficulty.EASY, 3,
            "Earn 3 stars", "Stars come from quick, mistake-free clears.", TaskReward(coins = 50), "daily_stars",
        ),
        daily(
            "d_distinct_2", TaskRule.COMPLETE_DISTINCT_LEVELS, TaskDifficulty.EASY, 2,
            "Play 2 different levels", "Clear two different levels today.", TaskReward(coins = 55), "daily_distinct",
        ),
    )

    private val dailyMedium = listOf(
        daily(
            "d_no_mistake_1", TaskRule.PERFECT_LEVELS, TaskDifficulty.MEDIUM, 1,
            "Flawless clear", "Complete a level without a single mismatch.", TaskReward(coins = 100), "daily_mistake",
        ),
        daily(
            "d_no_hint_1", TaskRule.NO_HINT_LEVELS, TaskDifficulty.MEDIUM, 1,
            "No hints needed", "Complete a level without using a hint.", TaskReward(coins = 90), "daily_hint",
        ),
        daily(
            "d_use_hint_1", TaskRule.USE_HINTS, TaskDifficulty.MEDIUM, 1,
            "Take a hint", "Use 1 hint in levels you complete.", TaskReward(coins = 80), "daily_hint",
        ),
        daily(
            "d_no_shuffle_1", TaskRule.NO_SHUFFLE_LEVELS, TaskDifficulty.MEDIUM, 1,
            "Steady board", "Complete a level without a mid-game shuffle.", TaskReward(coins = 90), "daily_shuffle",
        ),
        daily(
            "d_efficient_1", TaskRule.EFFICIENT_LEVELS, TaskDifficulty.MEDIUM, 1,
            "Sharp memory", "Clear a level using at most 2 moves per pair.", TaskReward(coins = 100), "daily_efficient",
        ),
        daily(
            "d_worlds_2", TaskRule.DISTINCT_WORLDS, TaskDifficulty.MEDIUM, 2,
            "World tour", "Play levels in 2 different worlds.", TaskReward(coins = 90), "daily_worlds",
        ),
        daily(
            "d_pairs_30", TaskRule.MATCH_PAIRS, TaskDifficulty.MEDIUM, 30,
            "Match 30 pairs", "Keep the matches coming across the day.", TaskReward(coins = 120), "daily_pairs",
        ),
    )

    private val dailyHard = listOf(
        daily(
            "d_boss_1", TaskRule.BOSS_LEVELS, TaskDifficulty.HARD, 1,
            "Boss breaker", "Complete a boss level (every 25th level).", TaskReward(coins = 150), "daily_boss",
        ),
        daily(
            "d_timed_1", TaskRule.TIMED_LEVELS, TaskDifficulty.HARD, 1,
            "Beat the clock", "Complete a level that has a time limit.", TaskReward(coins = 150), "daily_timed",
        ),
        daily(
            "d_stars_9", TaskRule.EARN_STARS, TaskDifficulty.HARD, 9,
            "Star collector", "Earn 9 stars today.", TaskReward(coins = 130), "daily_stars",
        ),
    )

    // --------------------------------------------------------------- weekly (6 per week)

    private val weeklyEasy = listOf(
        weekly(
            "w_clear_15", TaskRule.COMPLETE_LEVELS, TaskDifficulty.EASY, 15,
            "Complete 15 levels", "Finish 15 levels this week.", TaskReward(coins = 250), "weekly_levels",
        ),
        weekly(
            "w_pairs_100", TaskRule.MATCH_PAIRS, TaskDifficulty.EASY, 100,
            "Match 100 pairs", "Match 100 pairs this week.", TaskReward(coins = 250), "weekly_pairs",
        ),
        weekly(
            "w_stars_15", TaskRule.EARN_STARS, TaskDifficulty.EASY, 15,
            "Earn 15 stars", "Collect 15 stars this week.", TaskReward(coins = 250), "weekly_stars",
        ),
        weekly(
            "w_distinct_5", TaskRule.COMPLETE_DISTINCT_LEVELS, TaskDifficulty.EASY, 5,
            "Complete 5 different levels", "Clear five different levels this week.", TaskReward(coins = 300), "weekly_distinct",
        ),
    )

    private val weeklyMedium = listOf(
        weekly(
            "w_boss_3", TaskRule.BOSS_LEVELS, TaskDifficulty.MEDIUM, 3,
            "Boss hunter", "Complete 3 boss levels this week.", TaskReward(coins = 450), "weekly_boss",
        ),
        weekly(
            "w_no_hint_3", TaskRule.NO_HINT_LEVELS, TaskDifficulty.MEDIUM, 3,
            "Self-reliant", "Complete 3 levels without hints.", TaskReward(coins = 400), "weekly_hint",
        ),
        weekly(
            "w_no_shuffle_5", TaskRule.NO_SHUFFLE_LEVELS, TaskDifficulty.MEDIUM, 5,
            "Unshaken", "Complete 5 levels without a shuffle.", TaskReward(coins = 450), "weekly_shuffle",
        ),
        weekly(
            "w_worlds_3", TaskRule.DISTINCT_WORLDS, TaskDifficulty.MEDIUM, 3,
            "Three realms", "Play levels in 3 different worlds.", TaskReward(coins = 450), "weekly_worlds",
        ),
        weekly(
            "w_use_hint_5", TaskRule.USE_HINTS, TaskDifficulty.MEDIUM, 5,
            "Well assisted", "Use 5 hints in levels you complete.", TaskReward(coins = 450), "weekly_hint",
        ),
        weekly(
            "w_daily_claims_8", TaskRule.CLAIM_DAILY_TASKS, TaskDifficulty.MEDIUM, 8,
            "Daily dedication", "Claim 8 daily tasks this week.", TaskReward(coins = 400), "weekly_daily_claims",
        ),
    )

    private val weeklyHard = listOf(
        weekly(
            "w_mistake_5", TaskRule.LOW_MISTAKE_LEVELS, TaskDifficulty.HARD, 5,
            "Cool under pressure", "Complete 5 levels with at most 1 mismatch each.",
            TaskReward(coins = 700, hints = 1), "weekly_mistake",
        ),
        weekly(
            "w_efficient_3", TaskRule.EFFICIENT_LEVELS, TaskDifficulty.HARD, 3,
            "Perfect recall", "Clear 3 levels using at most 2 moves per pair.", TaskReward(coins = 700), "weekly_efficient",
        ),
        weekly(
            "w_timed_5", TaskRule.TIMED_LEVELS, TaskDifficulty.HARD, 5,
            "Against the clock", "Complete 5 timed levels.", TaskReward(coins = 650), "weekly_timed",
        ),
        weekly(
            "w_high_3", TaskRule.HIGH_DIFFICULTY_LEVELS, TaskDifficulty.HARD, 3,
            "Expert territory", "Complete 3 Expert or Master levels.", TaskReward(coins = 700), "weekly_high",
        ),
    )

    // ------------------------------------------------------------- monthly (5 per month)

    private val monthlyPool = listOf(
        monthly(
            "m_clear_100", TaskRule.COMPLETE_LEVELS, 100,
            "Century run", "Complete 100 levels during this monthly challenge.",
            TaskReward(coins = 2400),
        ),
        monthly(
            "m_pairs_1000", TaskRule.MATCH_PAIRS, 1000,
            "Thousand pairs", "Match 1000 pairs before the deadline.",
            TaskReward(coins = 1800),
        ),
        monthly(
            "m_stars_100", TaskRule.EARN_STARS, 100,
            "Star vault", "Earn 100 stars this month.",
            TaskReward(coins = 1800),
        ),
        monthly(
            "m_boss_10", TaskRule.BOSS_LEVELS, 10,
            "Boss gauntlet", "Complete 10 boss levels.",
            TaskReward(coins = 1500, hints = 2),
        ),
        monthly(
            "m_worlds_8", TaskRule.DISTINCT_WORLDS, 8,
            "Worlds wanderer", "Complete levels in 8 different worlds.",
            TaskReward(coins = 2400),
        ),
        monthly(
            "m_perfect_20", TaskRule.PERFECT_LEVELS, 20,
            "Flawless twenty", "Complete 20 levels with no mismatches.",
            TaskReward(coins = 2400),
        ),
        monthly(
            "m_no_hint_20", TaskRule.NO_HINT_LEVELS, 20,
            "Unaided", "Complete 20 levels without hints.",
            TaskReward(coins = 2000, hints = 2),
        ),
        monthly(
            "m_efficient_15", TaskRule.EFFICIENT_LEVELS, 15,
            "Photographic memory", "Clear 15 levels using at most 2 moves per pair.",
            TaskReward(coins = 2000),
        ),
        monthly(
            "m_high_20", TaskRule.HIGH_DIFFICULTY_LEVELS, 20,
            "Mastery", "Complete 20 Expert or Master levels.",
            TaskReward(coins = 2500),
        ),
        monthly(
            "m_weekly_claims_8", TaskRule.CLAIM_WEEKLY_TASKS, 8,
            "Weekly regular", "Claim 8 weekly missions this month.",
            TaskReward(coins = 2000),
        ),
    )

    val all: List<TaskDefinition> = dailyEasy + dailyMedium + dailyHard +
        weeklyEasy + weeklyMedium + weeklyHard + monthlyPool

    private val byId: Map<String, TaskDefinition> = all.associateBy { it.id }

    fun definition(id: String): TaskDefinition? = byId[id]

    /** A brand-new task set for one period. The seed is derived from the period key. */
    fun generate(period: TaskPeriod, key: String, startEpochDay: Long): TaskPeriodState {
        val seed = key.hashCode()
        val rng = Random(seed)
        val instances = when (period) {
            TaskPeriod.DAILY -> build(dailyEasy, 2, dailyMedium, 2, dailyHard, 1, rng)
            TaskPeriod.WEEKLY -> {
                val instances = build(weeklyEasy, 2, weeklyMedium, 3, weeklyHard, 1, rng).toMutableList()
                if (instances.none { it.definition.reward.hints > 0 }) {
                    val hintDef = weeklyHard.first { it.reward.hints > 0 }
                    if (!instances.any { it.definition.id == hintDef.id }) {
                        if (instances.isNotEmpty()) {
                            instances[instances.lastIndex] = TaskInstance(hintDef)
                        } else {
                            instances.add(TaskInstance(hintDef))
                        }
                    }
                }
                instances
            }
            TaskPeriod.MONTHLY -> build(monthlyPool, 5, emptyList(), 0, emptyList(), 0, rng)
        }
        return TaskPeriodState(
            period = period,
            key = key,
            startEpochDay = startEpochDay,
            instances = instances,
        )
    }

    private fun build(
        easy: List<TaskDefinition>,
        easyCount: Int,
        medium: List<TaskDefinition>,
        mediumCount: Int,
        hard: List<TaskDefinition>,
        hardCount: Int,
        rng: Random,
    ): List<TaskInstance> {
        val used = mutableSetOf<String>()
        val picked = mutableListOf<TaskDefinition>()
        pick(easy, easyCount, rng, used, picked)
        pick(medium, mediumCount, rng, used, picked)
        pick(hard, hardCount, rng, used, picked)
        return picked.map { TaskInstance(it) }
    }

    private fun pick(
        pool: List<TaskDefinition>,
        count: Int,
        rng: Random,
        used: MutableSet<String>,
        into: MutableList<TaskDefinition>,
    ) {
        if (count <= 0) return
        for (definition in pool.shuffled(rng)) {
            if (into.count { it.difficulty == definition.difficulty } >= count) break
            if (!used.add(definition.exclusiveGroup)) continue
            into += definition
        }
    }
}
