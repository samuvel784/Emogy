package com.vurka.emogy.data.tasks

import com.vurka.emogy.data.time.GameCalendar

/**
 * Pure task state machine: generation, rollover, progress and claim bookkeeping.
 *
 * It never touches storage, the clock or the UI, so every boundary (midnight, Sunday, day 28,
 * leap year) is unit-testable with an injected [GameCalendar].
 */
class TaskEngine(private val calendar: GameCalendar) {

    fun dailyKey(epochDay: Long): String = "D$epochDay"

    fun weeklyKey(epochDay: Long): String = "W${calendar.weekKey(epochDay)}"

    fun monthlyKey(epochDay: Long): String = "M${calendar.monthKey(epochDay)}"

    /** Rolls every cycle forward to the one that contains [nowMillis]. */
    fun ensureCurrent(state: TaskState, nowMillis: Long): TaskState {
        val today = calendar.epochDayAt(nowMillis)
        val daily = state.daily?.takeIf { it.key == dailyKey(today) }
            ?: TaskConfig.generate(TaskPeriod.DAILY, dailyKey(today), today)
        val weekly = state.weekly?.takeIf { it.key == weeklyKey(today) }
            ?: TaskConfig.generate(TaskPeriod.WEEKLY, weeklyKey(today), calendar.weekStart(today))
        val monthly = state.monthly?.takeIf { it.key == monthlyKey(today) }
            ?: TaskConfig.generate(TaskPeriod.MONTHLY, monthlyKey(today), calendar.monthStart(today))
        return TaskState(daily = daily, weekly = weekly, monthly = monthly)
    }

    /** Applies one real finished level. Monthly progress stops at day 28 00:00. */
    fun apply(state: TaskState, event: LevelCompletedEvent): TaskState {
        val current = ensureCurrent(state, event.atMillis)
        val eventDay = calendar.epochDayAt(event.atMillis)
        val monthlyOpen = calendar.isMonthlyWindowOpen(eventDay)

        val daily = current.daily?.let { period ->
            period.copy(instances = period.instances.map { applyToInstance(it, event) })
        }
        val weekly = current.weekly?.let { period ->
            period.copy(instances = period.instances.map { applyToInstance(it, event) })
        }
        val monthly = if (monthlyOpen) {
            current.monthly?.let { period ->
                period.copy(instances = period.instances.map { applyToInstance(it, event) })
            }
        } else {
            current.monthly
        }
        return TaskState(daily = daily, weekly = weekly, monthly = monthly)
    }

    /**
     * A claim feeds the next cycle's meta missions ("claim 8 daily tasks"). It never feeds its own
     * cycle, so no loop is possible.
     */
    fun noteClaim(state: TaskState, claimedPeriod: TaskPeriod, atMillis: Long): TaskState {
        val current = ensureCurrent(state, atMillis)
        val eventDay = calendar.epochDayAt(atMillis)
        return when (claimedPeriod) {
            TaskPeriod.DAILY -> current.copy(
                weekly = current.weekly?.let { bumpMeta(it, TaskRule.CLAIM_DAILY_TASKS, atMillis) },
            )
            TaskPeriod.WEEKLY -> current.copy(
                monthly = if (calendar.isMonthlyWindowOpen(eventDay)) {
                    current.monthly?.let { bumpMeta(it, TaskRule.CLAIM_WEEKLY_TASKS, atMillis) }
                } else {
                    current.monthly
                },
            )
            TaskPeriod.MONTHLY -> current
        }
    }

    fun markClaimed(state: TaskState, period: TaskPeriod, instanceId: String, atMillis: Long): TaskState {
        val target = state.period(period) ?: return state
        val updated = target.copy(
            instances = target.instances.map { instance ->
                if (instance.definition.id == instanceId && instance.claimedAtMillis == 0L) {
                    instance.copy(claimedAtMillis = atMillis)
                } else {
                    instance
                }
            },
        )
        return state.with(updated)
    }

    fun markMilestoneClaimed(state: TaskState, atMillis: Long): TaskState {
        val monthly = state.monthly ?: return state
        return state.with(monthly.copy(milestoneClaimed = true))
    }

    fun readyToClaim(state: TaskState, period: TaskPeriod): List<TaskInstance> =
        state.period(period)?.instances?.filter { it.isComplete && !it.isClaimed }.orEmpty()

    fun allClaimed(state: TaskState, period: TaskPeriod): Boolean {
        val instances = state.period(period)?.instances.orEmpty()
        return instances.isNotEmpty() && instances.all { it.isClaimed }
    }

    fun monthlyMilestoneReady(state: TaskState): Boolean =
        allClaimed(state, TaskPeriod.MONTHLY) && state.monthly?.milestoneClaimed == false

    private fun applyToInstance(instance: TaskInstance, event: LevelCompletedEvent): TaskInstance {
        if (instance.isComplete) return instance
        val definition = instance.definition
        var progress = instance.progress
        var distinct = instance.distinct
        when (definition.rule) {
            TaskRule.COMPLETE_LEVELS -> progress += 1
            TaskRule.COMPLETE_DISTINCT_LEVELS -> {
                distinct = distinct + event.levelKey
                progress = distinct.size
            }
            TaskRule.MATCH_PAIRS -> progress += event.pairs
            TaskRule.MATCH_DISTINCT_PAIRS -> {
                distinct = (distinct + event.distinctEmojiIds).take(MAX_DISTINCT_IDS).toSet()
                progress = distinct.size
            }
            TaskRule.EARN_STARS -> progress += event.stars
            TaskRule.PERFECT_LEVELS -> if (event.perfect) progress += 1
            TaskRule.LOW_MISTAKE_LEVELS -> if (event.lowMistake) progress += 1
            TaskRule.EFFICIENT_LEVELS -> if (event.efficient) progress += 1
            TaskRule.NO_HINT_LEVELS -> if (event.hintsUsed == 0) progress += 1
            TaskRule.USE_HINTS -> progress += event.hintsUsed
            TaskRule.NO_SHUFFLE_LEVELS -> if (event.shuffles == 0) progress += 1
            TaskRule.BOSS_LEVELS -> if (event.isBoss) progress += 1
            TaskRule.TIMED_LEVELS -> if (event.isTimed) progress += 1
            TaskRule.HIGH_DIFFICULTY_LEVELS -> if (event.isHighDifficulty) progress += 1
            TaskRule.DISTINCT_WORLDS -> if (event.world > 0) {
                distinct = distinct + event.world.toString()
                progress = distinct.size
            }
            TaskRule.EARN_GAMEPLAY_COINS -> progress += event.coins
            TaskRule.CLAIM_DAILY_TASKS, TaskRule.CLAIM_WEEKLY_TASKS -> Unit
        }
        val capped = progress.coerceIn(0, definition.target)
        val completedAt = if (capped >= definition.target && instance.completedAtMillis == 0L) {
            event.atMillis
        } else {
            instance.completedAtMillis
        }
        return instance.copy(
            progress = capped,
            distinct = distinct,
            completedAtMillis = completedAt,
        )
    }

    private fun bumpMeta(period: TaskPeriodState, rule: TaskRule, atMillis: Long): TaskPeriodState =
        period.copy(
            instances = period.instances.map { instance ->
                if (instance.definition.rule != rule || instance.isComplete) {
                    instance
                } else {
                    val next = (instance.progress + 1).coerceAtMost(instance.definition.target)
                    instance.copy(
                        progress = next,
                        completedAtMillis = if (next >= instance.definition.target && instance.completedAtMillis == 0L) {
                            atMillis
                        } else {
                            instance.completedAtMillis
                        },
                    )
                }
            },
        )

    private companion object {
        /** Distinct sets are persisted; keep the blob small even after thousands of matches. */
        const val MAX_DISTINCT_IDS = 2048
    }
}
