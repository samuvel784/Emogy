package com.vurka.emogy

import com.vurka.emogy.data.tasks.LevelCompletedEvent
import com.vurka.emogy.data.tasks.TaskConfig
import com.vurka.emogy.data.tasks.TaskDefinition
import com.vurka.emogy.data.tasks.TaskEngine
import com.vurka.emogy.data.tasks.TaskInstance
import com.vurka.emogy.data.tasks.TaskPeriod
import com.vurka.emogy.data.tasks.TaskPeriodState
import com.vurka.emogy.data.tasks.TaskState
import com.vurka.emogy.data.time.GameCalendar
import com.vurka.emogy.data.time.TaskClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class TaskEngineTest {

    private val utc = TimeZone.getTimeZone("UTC")
    private val now = millis(2024, 6, 5, 12)
    private val calendar = GameCalendar(TaskClock { now }, utc)
    private val engine = TaskEngine(calendar)
    private val today = calendar.today()

    private fun millis(year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0): Long =
        Calendar.getInstance(utc).apply {
            clear()
            set(year, month - 1, day, hour, minute, 0)
        }.timeInMillis

    private fun definition(id: String): TaskDefinition =
        requireNotNull(TaskConfig.definition(id)) { "missing task $id" }

    private fun periodState(
        period: TaskPeriod,
        definitions: List<TaskDefinition>,
        progress: Int = 0,
    ): TaskPeriodState = TaskPeriodState(
        period = period,
        key = when (period) {
            TaskPeriod.DAILY -> engine.dailyKey(today)
            TaskPeriod.WEEKLY -> engine.weeklyKey(today)
            TaskPeriod.MONTHLY -> engine.monthlyKey(today)
        },
        startEpochDay = when (period) {
            TaskPeriod.DAILY -> today
            TaskPeriod.WEEKLY -> calendar.weekStart(today)
            TaskPeriod.MONTHLY -> calendar.monthStart(today)
        },
        instances = definitions.map { TaskInstance(it, progress = progress) },
    )

    private fun event(
        atMillis: Long = now,
        levelId: Int = 1,
        world: Int = 1,
        stars: Int = 3,
        pairs: Int = 8,
        moves: Int = 10,
        mistakes: Int = 0,
        hintsUsed: Int = 0,
        shuffles: Int = 0,
        coins: Int = 20,
        isBoss: Boolean = false,
        isDaily: Boolean = false,
        isTimed: Boolean = false,
        isHighDifficulty: Boolean = false,
    ) = LevelCompletedEvent(
        atMillis = atMillis,
        levelId = levelId,
        world = world,
        stars = stars,
        pairs = pairs,
        distinctEmojiIds = setOf("a$levelId", "b$levelId"),
        moves = moves,
        mistakes = mistakes,
        hintsUsed = hintsUsed,
        shuffles = shuffles,
        seconds = 60,
        coins = coins,
        isBoss = isBoss,
        isDaily = isDaily,
        isTimed = isTimed,
        isHighDifficulty = isHighDifficulty,
    )

    private fun dailyProgress(state: TaskState, id: String): Int =
        state.daily?.instance(id)?.progress ?: -1

    private fun weeklyProgress(state: TaskState, id: String): Int =
        state.weekly?.instance(id)?.progress ?: -1

    private fun monthlyProgress(state: TaskState, id: String): Int =
        state.monthly?.instance(id)?.progress ?: -1

    @Test
    fun `generated sets have the configured sizes and only their own period`() {
        val state = engine.ensureCurrent(TaskState(), now)

        assertEquals(5, state.daily?.instances?.size)
        assertEquals(6, state.weekly?.instances?.size)
        assertEquals(5, state.monthly?.instances?.size)
        state.daily?.instances?.forEach { assertEquals(TaskPeriod.DAILY, it.definition.period) }
        state.weekly?.instances?.forEach { assertEquals(TaskPeriod.WEEKLY, it.definition.period) }
        state.monthly?.instances?.forEach { assertEquals(TaskPeriod.MONTHLY, it.definition.period) }
    }

    @Test
    fun `generation is deterministic for the same period key and exclusive groups do not repeat`() {
        val first = engine.ensureCurrent(TaskState(), now)
        val second = engine.ensureCurrent(TaskState(), now)

        assertEquals(
            first.daily?.instances?.map { it.definition.id },
            second.daily?.instances?.map { it.definition.id },
        )
        val groups = first.daily?.instances?.map { it.definition.exclusiveGroup }.orEmpty()
        assertEquals(groups.size, groups.toSet().size)
    }

    @Test
    fun `daily rollover resets progress while weekly and monthly survive`() {
        val state = TaskState(
            daily = periodState(TaskPeriod.DAILY, listOf(definition("d_clear_2")), progress = 1),
            weekly = periodState(TaskPeriod.WEEKLY, listOf(definition("w_clear_15")), progress = 4),
            monthly = periodState(TaskPeriod.MONTHLY, listOf(definition("m_clear_100")), progress = 30),
        )
        val tomorrowNoon = millis(2024, 6, 6, 12)
        val rolled = engine.ensureCurrent(state, tomorrowNoon)

        assertNotEquals(state.daily?.key, rolled.daily?.key)
        assertTrue(rolled.daily?.instances?.all { it.progress == 0 } == true)
        assertEquals(state.weekly?.key, rolled.weekly?.key)
        assertEquals(4, weeklyProgress(rolled, "w_clear_15"))
        assertEquals(30, monthlyProgress(rolled, "m_clear_100"))
    }

    @Test
    fun `weekly rollover happens on Sunday and keeps the monthly progress`() {
        val saturday = millis(2024, 6, 8, 23)
        val saturdayCalendar = GameCalendar(TaskClock { saturday }, utc)
        val saturdayEngine = TaskEngine(saturdayCalendar)
        val state = saturdayEngine.ensureCurrent(TaskState(), saturday)
        val sunday = millis(2024, 6, 9, 0)
        val rolled = saturdayEngine.ensureCurrent(state, sunday)

        assertNotEquals(state.weekly?.key, rolled.weekly?.key)
        assertEquals(state.monthly?.key, rolled.monthly?.key)
        assertEquals(0, rolled.weekly?.instances?.sumOf { it.progress } ?: -1)
    }

    @Test
    fun `counting rule caps at the target and stops growing`() {
        val state = TaskState(daily = periodState(TaskPeriod.DAILY, listOf(definition("d_clear_2"))))
        val afterOne = engine.apply(state, event(levelId = 1))
        val afterTwo = engine.apply(afterOne, event(levelId = 2))
        val afterThree = engine.apply(afterTwo, event(levelId = 3))

        assertEquals(1, dailyProgress(afterOne, "d_clear_2"))
        assertEquals(2, dailyProgress(afterTwo, "d_clear_2"))
        assertEquals(2, dailyProgress(afterThree, "d_clear_2"))
        assertTrue(afterTwo.daily?.instance("d_clear_2")?.isComplete == true)
        assertEquals(now, afterTwo.daily?.instance("d_clear_2")?.completedAtMillis)
    }

    @Test
    fun `distinct level rule counts a repeated level only once`() {
        val state = TaskState(
            daily = periodState(TaskPeriod.DAILY, listOf(definition("d_distinct_2"))),
        )
        val first = engine.apply(state, event(levelId = 7))
        val repeat = engine.apply(first, event(levelId = 7))
        val different = engine.apply(repeat, event(levelId = 8))

        assertEquals(1, dailyProgress(first, "d_distinct_2"))
        assertEquals(1, dailyProgress(repeat, "d_distinct_2"))
        assertEquals(2, dailyProgress(different, "d_distinct_2"))
    }

    @Test
    fun `efficient rule requires at most two moves per pair`() {
        val state = TaskState(
            daily = periodState(TaskPeriod.DAILY, listOf(definition("d_efficient_1"))),
        )
        val cheap = engine.apply(state, event(pairs = 8, moves = 16))
        val expensive = engine.apply(state, event(pairs = 8, moves = 17))

        assertEquals(1, dailyProgress(cheap, "d_efficient_1"))
        assertEquals(0, dailyProgress(expensive, "d_efficient_1"))
    }

    @Test
    fun `perfect and hint rules use the reported payload`() {
        val perfectState = TaskState(
            daily = periodState(TaskPeriod.DAILY, listOf(definition("d_no_mistake_1"))),
        )
        val noHintState = TaskState(
            daily = periodState(TaskPeriod.DAILY, listOf(definition("d_no_hint_1"))),
        )
        val useHintState = TaskState(
            daily = periodState(TaskPeriod.DAILY, listOf(definition("d_use_hint_1"))),
        )

        assertEquals(1, dailyProgress(engine.apply(perfectState, event(mistakes = 0)), "d_no_mistake_1"))
        assertEquals(0, dailyProgress(engine.apply(perfectState, event(mistakes = 2)), "d_no_mistake_1"))
        assertEquals(1, dailyProgress(engine.apply(noHintState, event(hintsUsed = 0)), "d_no_hint_1"))
        assertEquals(0, dailyProgress(engine.apply(noHintState, event(hintsUsed = 1)), "d_no_hint_1"))
        assertEquals(1, dailyProgress(engine.apply(useHintState, event(hintsUsed = 2)), "d_use_hint_1"))
    }

    @Test
    fun `boss timed and world rules read the right flags`() {
        val bossState = TaskState(daily = periodState(TaskPeriod.DAILY, listOf(definition("d_boss_1"))))
        val timedState = TaskState(daily = periodState(TaskPeriod.DAILY, listOf(definition("d_timed_1"))))
        val worldsState = TaskState(daily = periodState(TaskPeriod.DAILY, listOf(definition("d_worlds_2"))))

        assertEquals(1, dailyProgress(engine.apply(bossState, event(isBoss = true)), "d_boss_1"))
        assertEquals(0, dailyProgress(engine.apply(bossState, event(isBoss = false)), "d_boss_1"))
        assertEquals(1, dailyProgress(engine.apply(timedState, event(isTimed = true)), "d_timed_1"))

        val oneWorld = engine.apply(worldsState, event(world = 2, levelId = 10))
        val twoWorlds = engine.apply(oneWorld, event(world = 2, levelId = 11))
        val otherWorld = engine.apply(twoWorlds, event(world = 5, levelId = 12))
        assertEquals(1, dailyProgress(oneWorld, "d_worlds_2"))
        assertEquals(1, dailyProgress(twoWorlds, "d_worlds_2"))
        assertEquals(2, dailyProgress(otherWorld, "d_worlds_2"))
    }

    @Test
    fun `daily runs contribute one shared level key`() {
        val state = TaskState(
            daily = periodState(TaskPeriod.DAILY, listOf(definition("d_distinct_2"))),
        )
        val firstDaily = engine.apply(state, event(levelId = 0, isDaily = true))
        val secondDaily = engine.apply(firstDaily, event(levelId = 0, isDaily = true))

        assertEquals(1, dailyProgress(secondDaily, "d_distinct_2"))
    }

    @Test
    fun `monthly progress stops at day 28 midnight`() {
        val monthlyState = TaskState(
            monthly = periodState(TaskPeriod.MONTHLY, listOf(definition("m_clear_100"))),
        )
        val lastOpenMoment = millis(2024, 6, 27, 23, 59)
        val closedMoment = millis(2024, 6, 28, 0, 0)

        val open = engine.apply(monthlyState, event(atMillis = lastOpenMoment))
        val closed = engine.apply(open, event(atMillis = closedMoment))

        assertEquals(1, monthlyProgress(open, "m_clear_100"))
        assertEquals(1, monthlyProgress(closed, "m_clear_100"))
    }

    @Test
    fun `weekly progress keeps counting after the monthly window closes`() {
        val lastOpenMoment = millis(2024, 6, 27, 23, 59)
        val closedMoment = millis(2024, 6, 28, 0, 0)
        val eventDay = calendar.epochDayAt(lastOpenMoment)
        val weeklyState = TaskState(
            weekly = TaskPeriodState(
                period = TaskPeriod.WEEKLY,
                key = engine.weeklyKey(eventDay),
                startEpochDay = calendar.weekStart(eventDay),
                instances = listOf(TaskInstance(definition("w_clear_15"))),
            ),
        )

        val first = engine.apply(weeklyState, event(atMillis = lastOpenMoment))
        val second = engine.apply(first, event(atMillis = closedMoment))

        assertEquals(1, weeklyProgress(first, "w_clear_15"))
        assertEquals(2, weeklyProgress(second, "w_clear_15"))
    }

    @Test
    fun `claiming a daily task feeds the weekly meta mission and never its own cycle`() {
        val state = TaskState(
            daily = periodState(TaskPeriod.DAILY, listOf(definition("d_clear_2"))),
            weekly = periodState(TaskPeriod.WEEKLY, listOf(definition("w_daily_claims_8"))),
        )
        val first = engine.noteClaim(state, TaskPeriod.DAILY, now)
        val second = engine.noteClaim(first, TaskPeriod.DAILY, now)

        assertEquals(0, dailyProgress(second, "d_clear_2"))
        assertEquals(1, weeklyProgress(first, "w_daily_claims_8"))
        assertEquals(2, weeklyProgress(second, "w_daily_claims_8"))
    }

    @Test
    fun `claiming a weekly task feeds the monthly meta mission`() {
        val state = TaskState(
            weekly = periodState(TaskPeriod.WEEKLY, listOf(definition("w_clear_15"))),
            monthly = periodState(TaskPeriod.MONTHLY, listOf(definition("m_weekly_claims_8"))),
        )

        val claimed = engine.noteClaim(state, TaskPeriod.WEEKLY, now)

        assertEquals(1, monthlyProgress(claimed, "m_weekly_claims_8"))
    }

    @Test
    fun `mark claimed keeps the first timestamp`() {
        val state = TaskState(daily = periodState(TaskPeriod.DAILY, listOf(definition("d_clear_2"))))
        val once = engine.markClaimed(state, TaskPeriod.DAILY, "d_clear_2", 1_000L)
        val twice = engine.markClaimed(once, TaskPeriod.DAILY, "d_clear_2", 5_000L)

        assertEquals(1_000L, twice.daily?.instance("d_clear_2")?.claimedAtMillis)
    }

    @Test
    fun `ready to claim only lists complete unclaimed tasks`() {
        val state = TaskState(
            daily = TaskPeriodState(
                period = TaskPeriod.DAILY,
                key = engine.dailyKey(today),
                startEpochDay = today,
                instances = listOf(
                    TaskInstance(definition("d_clear_2"), progress = 2),
                    TaskInstance(definition("d_stars_3"), progress = 3, claimedAtMillis = 10L),
                    TaskInstance(definition("d_pairs_15"), progress = 4),
                ),
            ),
        )

        val ready = engine.readyToClaim(state, TaskPeriod.DAILY)

        assertEquals(listOf("d_clear_2"), ready.map { it.definition.id })
        assertFalse(engine.allClaimed(state, TaskPeriod.DAILY))
    }

    @Test
    fun `monthly milestone needs every monthly mission claimed`() {
        val monthly = periodState(
            TaskPeriod.MONTHLY,
            listOf(definition("m_clear_100"), definition("m_pairs_1000")),
        )
        val partlyClaimed = monthly.copy(
            instances = listOf(
                monthly.instances[0].copy(claimedAtMillis = 10L),
                monthly.instances[1],
            ),
        )
        val allClaimed = monthly.copy(
            instances = monthly.instances.map { it.copy(claimedAtMillis = 10L) },
        )

        assertFalse(engine.monthlyMilestoneReady(TaskState(monthly = partlyClaimed)))
        assertFalse(engine.monthlyMilestoneReady(TaskState(monthly = allClaimed.copy(milestoneClaimed = true))))
        assertTrue(engine.monthlyMilestoneReady(TaskState(monthly = allClaimed)))
    }
}
