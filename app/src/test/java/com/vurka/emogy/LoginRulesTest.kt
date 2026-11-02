package com.vurka.emogy

import com.vurka.emogy.data.login.LoginRules
import com.vurka.emogy.data.login.LoginSlotState
import com.vurka.emogy.data.login.LoginState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginRulesTest {

    private val sunday = 100L

    @Test
    fun `first check in starts a one day streak`() {
        val result = LoginRules.checkIn(LoginState(), 100L)

        assertTrue(result.advanced)
        assertFalse(result.alreadyCheckedIn)
        assertEquals(1, result.state.streak)
        assertEquals(100L, result.state.lastCheckInDay)
        assertEquals(100L, result.state.lastSeenDay)
        assertEquals(100L, result.state.streakStartDay)
    }

    @Test
    fun `checking in twice on the same local day changes nothing`() {
        val first = LoginRules.checkIn(LoginState(), 100L)
        val again = LoginRules.checkIn(first.state, 100L)

        assertTrue(again.alreadyCheckedIn)
        assertFalse(again.advanced)
        assertEquals(first.state, again.state)
    }

    @Test
    fun `consecutive days advance the streak and skipped days reset it`() {
        val day1 = LoginRules.checkIn(LoginState(), 100L)
        val day2 = LoginRules.checkIn(day1.state, 101L)
        val afterGap = LoginRules.checkIn(day2.state, 103L)

        assertEquals(2, day2.state.streak)
        assertEquals(1, afterGap.state.streak)
        assertTrue(afterGap.brokeStreak)
        assertEquals(103L, afterGap.state.streakStartDay)
    }

    @Test
    fun `streak caps at thirty days and the theme is granted only once`() {
        var state = LoginState()
        var grantedOnDay = -1L
        for (day in 100L until 160L) {
            val result = LoginRules.checkIn(state, day)
            state = result.state
            if (result.reachedTarget) grantedOnDay = day
        }

        assertEquals(30, state.streak)
        assertEquals(129L, grantedOnDay)
        assertTrue(state.themeGranted)
    }

    @Test
    fun `a backwards clock never advances or grants anything`() {
        val state = LoginState(lastCheckInDay = 110L, streak = 5, lastSeenDay = 110L)
        val result = LoginRules.checkIn(state, 109L)

        assertTrue(result.clockAnomaly)
        assertFalse(result.advanced)
        assertEquals(state, result.state)
    }

    @Test
    fun `weekly slots map Sunday to Saturday with the right states`() {
        val state = LoginState(claimedDays = setOf(sunday))
        val slots = LoginRules.weekSlots(state, weekStart = sunday, today = sunday + 2)

        assertEquals(7, slots.size)
        assertEquals(LoginSlotState.CLAIMED, slots[0].state)
        assertEquals(LoginSlotState.MISSED, slots[1].state)
        assertEquals(LoginSlotState.AVAILABLE, slots[2].state)
        assertEquals(LoginSlotState.FUTURE, slots[3].state)
        assertEquals(LoginSlotState.FUTURE, slots[6].state)
        assertEquals(sunday + 6, slots[6].epochDay)
        assertEquals(LoginRules.weeklyRewards[6], slots[6].reward)
        assertEquals(1, slots[6].reward.hints)
    }

    @Test
    fun `claiming today grants the slot reward exactly once`() {
        val state = LoginState(lastSeenDay = sunday + 2)
        val (next, reward) = LoginRules.claimToday(state, today = sunday + 2, weekStart = sunday)

        assertNotNull(reward)
        assertEquals(LoginRules.weeklyRewards[2], reward)
        assertTrue(sunday + 2 in next.claimedDays)

        val (again, secondReward) = LoginRules.claimToday(next, today = sunday + 2, weekStart = sunday)
        assertNull(secondReward)
        assertEquals(next, again)
    }

    @Test
    fun `a claimed slot is never claimable again`() {
        val state = LoginState(claimedDays = setOf(sunday + 2), lastSeenDay = sunday + 2)

        assertFalse(LoginRules.canClaimToday(state, today = sunday + 2, weekStart = sunday))
    }

    @Test
    fun `a day outside the week is not claimable`() {
        assertFalse(LoginRules.canClaimToday(LoginState(), today = sunday + 7, weekStart = sunday))
        assertFalse(LoginRules.canClaimToday(LoginState(), today = sunday - 1, weekStart = sunday))
    }

    @Test
    fun `claiming prunes day keys from older weeks`() {
        val state = LoginState(
            lastSeenDay = sunday + 2,
            claimedDays = setOf(sunday - 30, sunday - 8, sunday + 1),
        )

        val (next, reward) = LoginRules.claimToday(state, today = sunday + 2, weekStart = sunday)

        assertNotNull(reward)
        assertFalse(sunday - 30 in next.claimedDays)
        assertFalse(sunday - 8 in next.claimedDays)
        assertTrue(sunday + 1 in next.claimedDays)
        assertTrue(sunday + 2 in next.claimedDays)
    }

    @Test
    fun `weekly reward table pays more towards the weekend`() {
        assertEquals(7, LoginRules.weeklyRewards.size)
        assertTrue(LoginRules.weeklyRewards[6].coins > LoginRules.weeklyRewards[0].coins)
        assertTrue(LoginRules.weeklyRewards.all { it.coins > 0 })
    }
}
