package com.vurka.emogy.data.login

import com.vurka.emogy.data.tasks.TaskReward

/**
 * Persistent login state.
 *
 * The seven-day calendar and the 30-day streak are deliberately separate fields:
 *  - [claimedDays] belongs to the weekly Sunday–Saturday calendar and is reset naturally by the
 *    week key (old days simply stop matching the current week);
 *  - [streak] only counts consecutive *local dates*, independent of any task or weekly reset.
 */
data class LoginState(
    val lastCheckInDay: Long = 0L,
    val streak: Int = 0,
    val streakStartDay: Long = 0L,
    val lastSeenDay: Long = 0L,
    val themeGranted: Boolean = false,
    val claimedDays: Set<Long> = emptySet(),
)

enum class LoginSlotState { CLAIMED, AVAILABLE, MISSED, FUTURE }

data class WeeklyLoginSlot(
    val index: Int,
    val epochDay: Long,
    val reward: TaskReward,
    val state: LoginSlotState,
)

enum class LoginClaimResult { GRANTED, ALREADY_CLAIMED, CLOCK_ANOMALY }

data class CheckInResult(
    val state: LoginState,
    val advanced: Boolean = false,
    val brokeStreak: Boolean = false,
    val reachedTarget: Boolean = false,
    val alreadyCheckedIn: Boolean = false,
    val clockAnomaly: Boolean = false,
)

/**
 * Pure rules for both login systems.
 *
 * Clock safeguards are honest about being local-only: a backwards clock never grants progress and
 * never claims a slot; a forward jump breaks the streak because the days in between were missed.
 */
object LoginRules {

    const val STREAK_TARGET = 30
    const val STREAK_THEME_ID = "starlight_oath"

    /** Index 0 = Sunday. Tuned against the task economy (1,075 coins + 1 hint per perfect week). */
    val weeklyRewards: List<TaskReward> = listOf(
        TaskReward(coins = 75),
        TaskReward(coins = 100),
        TaskReward(coins = 125),
        TaskReward(coins = 150),
        TaskReward(coins = 175),
        TaskReward(coins = 200),
        TaskReward(coins = 250, hints = 1),
    )

    fun checkIn(state: LoginState, today: Long): CheckInResult {
        if (today < state.lastSeenDay) {
            return CheckInResult(state = state, clockAnomaly = true)
        }
        val seen = state.copy(lastSeenDay = maxOf(state.lastSeenDay, today))

        if (state.lastCheckInDay == today) {
            return CheckInResult(state = seen, alreadyCheckedIn = true)
        }

        val consecutive = state.lastCheckInDay > 0L && today == state.lastCheckInDay + 1L
        val nextStreak = when {
            consecutive -> (state.streak + 1).coerceAtMost(STREAK_TARGET)
            else -> 1
        }
        val reached = nextStreak >= STREAK_TARGET && !state.themeGranted
        val next = seen.copy(
            lastCheckInDay = today,
            streak = nextStreak,
            streakStartDay = if (consecutive) state.streakStartDay else today,
            themeGranted = state.themeGranted || reached,
        )
        return CheckInResult(
            state = next,
            advanced = true,
            brokeStreak = state.lastCheckInDay > 0L && !consecutive,
            reachedTarget = reached,
        )
    }

    /** Seven slots for the week starting at [weekStart]. */
    fun weekSlots(state: LoginState, weekStart: Long, today: Long): List<WeeklyLoginSlot> =
        weeklyRewards.mapIndexed { index, reward ->
            val day = weekStart + index
            WeeklyLoginSlot(
                index = index,
                epochDay = day,
                reward = reward,
                state = slotState(state, day, today),
            )
        }

    fun slotState(state: LoginState, slotDay: Long, today: Long): LoginSlotState = when {
        slotDay in state.claimedDays -> LoginSlotState.CLAIMED
        slotDay == today -> LoginSlotState.AVAILABLE
        slotDay < today -> LoginSlotState.MISSED
        else -> LoginSlotState.FUTURE
    }

    fun canClaimToday(state: LoginState, today: Long, weekStart: Long): Boolean {
        if (today < state.lastSeenDay) return false
        if (today !in weekStart until weekStart + 7) return false
        return today !in state.claimedDays
    }

    /** Adds today to the claimed set and prunes older weeks so the blob never grows forever. */
    fun claimToday(state: LoginState, today: Long, weekStart: Long): Pair<LoginState, TaskReward?> {
        if (!canClaimToday(state, today, weekStart)) return state to null
        val reward = weeklyRewards[(today - weekStart).toInt().coerceIn(0, 6)]
        val pruned = state.claimedDays.filter { it >= weekStart }.toSet()
        return state.copy(
            claimedDays = pruned + today,
            lastSeenDay = maxOf(state.lastSeenDay, today),
        ) to reward
    }
}
