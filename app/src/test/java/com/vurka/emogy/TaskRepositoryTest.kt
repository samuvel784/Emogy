package com.vurka.emogy

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.vurka.emogy.data.GameKeys
import com.vurka.emogy.data.tasks.ClaimOutcome
import com.vurka.emogy.data.tasks.LevelCompletedEvent
import com.vurka.emogy.data.tasks.TaskConfig
import com.vurka.emogy.data.tasks.TaskDefinition
import com.vurka.emogy.data.tasks.TaskEngine
import com.vurka.emogy.data.tasks.TaskInstance
import com.vurka.emogy.data.tasks.TaskPeriod
import com.vurka.emogy.data.tasks.TaskPeriodState
import com.vurka.emogy.data.tasks.TaskRepository
import com.vurka.emogy.data.tasks.TaskState
import com.vurka.emogy.data.tasks.TaskStateCodec
import com.vurka.emogy.data.time.GameCalendar
import com.vurka.emogy.data.time.TaskClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Calendar
import java.util.TimeZone

class TaskRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val utc = TimeZone.getTimeZone("UTC")
    private val day = 86_400_000L

    private lateinit var scope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var calendar: GameCalendar
    private lateinit var repository: TaskRepository
    private lateinit var engine: TaskEngine

    /** Wednesday 2024-06-05 12:00 UTC. */
    private var now = millis(2024, 6, 5, 12)

    @Before
    fun setUp() {
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        dataStore = PreferenceDataStoreFactory.create(scope = scope) {
            File(tempFolder.root, "tasks.preferences_pb")
        }
        calendar = GameCalendar(TaskClock { now }, utc)
        engine = TaskEngine(calendar)
        repository = TaskRepository(dataStore, calendar)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun millis(year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0): Long =
        Calendar.getInstance(utc).apply {
            clear()
            set(year, month - 1, day, hour, minute, 0)
        }.timeInMillis

    private fun definition(id: String): TaskDefinition =
        requireNotNull(TaskConfig.definition(id)) { "missing task $id" }

    private fun dailyState(vararg tasks: TaskInstance): TaskPeriodState {
        val today = calendar.today()
        return TaskPeriodState(
            period = TaskPeriod.DAILY,
            key = engine.dailyKey(today),
            startEpochDay = today,
            instances = tasks.toList(),
        )
    }

    private fun seed(state: TaskState, coins: Int = 0, hints: Int = 0) = runBlocking {
        dataStore.edit { prefs ->
            prefs[GameKeys.TASK_STATE] = TaskStateCodec.encode(state)
            prefs[GameKeys.LAST_SEEN_DAY] = state.daily?.startEpochDay ?: calendar.today()
            if (coins > 0) prefs[GameKeys.COINS] = coins
            if (hints > 0) prefs[GameKeys.HINT_TOKENS] = hints
        }
    }

    private fun coins(): Int = runBlocking { repository.coins.first() }
    private fun hints(): Int = runBlocking { repository.hintTokens.first() }
    private fun owned(): Set<String> = runBlocking { repository.ownedCosmetics.first() }

    private fun event(
        levelId: Int,
        mistakes: Int = 0,
        hintsUsed: Int = 0,
        pairs: Int = 8,
        moves: Int = 16,
    ) = LevelCompletedEvent(
        atMillis = now,
        levelId = levelId,
        world = 1,
        stars = 3,
        pairs = pairs,
        distinctEmojiIds = setOf("a$levelId", "b$levelId"),
        moves = moves,
        mistakes = mistakes,
        hintsUsed = hintsUsed,
        shuffles = 0,
        seconds = 60,
        coins = 20,
        isBoss = false,
        isDaily = false,
        isTimed = false,
        isHighDifficulty = false,
    )

    @Test
    fun `a completed task can be claimed exactly once`() = runBlocking {
        seed(
            TaskState(daily = dailyState(TaskInstance(definition("d_clear_2"), progress = 2))),
        )

        val first = repository.claim(TaskPeriod.DAILY, "d_clear_2")
        val second = repository.claim(TaskPeriod.DAILY, "d_clear_2")

        assertEquals(ClaimOutcome.Granted(50, 0, null), first)
        assertEquals(ClaimOutcome.AlreadyClaimed, second)
        assertEquals(50, coins())
        assertEquals(0, hints())
    }

    @Test
    fun `two concurrent claims grant one reward`() = runBlocking {
        seed(
            TaskState(daily = dailyState(TaskInstance(definition("d_clear_2"), progress = 2))),
        )

        val outcomes = coroutineScope {
            val first = async(Dispatchers.IO) { repository.claim(TaskPeriod.DAILY, "d_clear_2") }
            val second = async(Dispatchers.IO) { repository.claim(TaskPeriod.DAILY, "d_clear_2") }
            listOf(first.await(), second.await())
        }

        assertEquals(1, outcomes.count { it is ClaimOutcome.Granted })
        assertEquals(1, outcomes.count { it == ClaimOutcome.AlreadyClaimed })
        assertEquals(50, coins())
    }

    @Test
    fun `an incomplete task cannot be claimed`() = runBlocking {
        seed(
            TaskState(daily = dailyState(TaskInstance(definition("d_clear_2"), progress = 1))),
        )

        val outcome = repository.claim(TaskPeriod.DAILY, "d_clear_2")

        assertEquals(ClaimOutcome.NotComplete, outcome)
        assertEquals(0, coins())
    }

    @Test
    fun `claiming a task from an earlier cycle is rejected`() = runBlocking {
        seed(
            TaskState(daily = dailyState(TaskInstance(definition("d_clear_2"), progress = 2))),
        )
        now += day

        val outcome = repository.claim(TaskPeriod.DAILY, "d_clear_2")

        assertEquals(ClaimOutcome.Expired, outcome)
        assertEquals(0, coins())
    }

    @Test
    fun `claiming an unknown id is rejected`() = runBlocking {
        seed(TaskState(daily = dailyState(TaskInstance(definition("d_clear_2"), progress = 2))))

        val outcome = repository.claim(TaskPeriod.DAILY, "not_a_task")

        assertEquals(ClaimOutcome.Expired, outcome)
        assertEquals(0, coins())
    }

    @Test
    fun `a backwards clock blocks every claim`() = runBlocking {
        seed(
            TaskState(daily = dailyState(TaskInstance(definition("d_clear_2"), progress = 2))),
        )
        now -= day

        val outcome = repository.claim(TaskPeriod.DAILY, "d_clear_2")
        val batch = repository.claimAll(TaskPeriod.DAILY)

        assertEquals(ClaimOutcome.ClockAnomaly, outcome)
        assertTrue(batch.isEmpty)
        assertEquals(0, coins())
    }

    @Test
    fun `claim all pays every ready mission in one write`() = runBlocking {
        seed(
            TaskState(
                daily = dailyState(
                    TaskInstance(definition("d_clear_2"), progress = 2),
                    TaskInstance(definition("d_pairs_15"), progress = 15),
                    TaskInstance(definition("d_stars_3"), progress = 1),
                ),
            ),
        )

        val batch = repository.claimAll(TaskPeriod.DAILY)

        assertEquals(2, batch.claimedCount)
        assertEquals(95, batch.coins)
        assertEquals(95, coins())
        assertTrue(repository.claimAll(TaskPeriod.DAILY).isEmpty)
    }

    @Test
    fun `gameplay events advance progress and the claim then pays out`() = runBlocking {
        seed(
            TaskState(daily = dailyState(TaskInstance(definition("d_clear_2"), progress = 0))),
        )

        repository.recordLevelCompleted(event(levelId = 11))
        assertEquals(ClaimOutcome.NotComplete, repository.claim(TaskPeriod.DAILY, "d_clear_2"))

        repository.recordLevelCompleted(event(levelId = 12))
        val outcome = repository.claim(TaskPeriod.DAILY, "d_clear_2")

        assertEquals(ClaimOutcome.Granted(50, 0, null), outcome)
        assertEquals(50, coins())
    }

    @Test
    fun `an event from an earlier day does not resurrect yesterday's progress`() = runBlocking {
        seed(
            TaskState(daily = dailyState(TaskInstance(definition("d_clear_2"), progress = 1))),
        )
        now += day

        repository.recordLevelCompleted(event(levelId = 11).copy(atMillis = now - day))
        repository.refresh()

        val state = repository.state.first()
        assertEquals(engine.dailyKey(calendar.today()), state.daily?.key)
        assertTrue(state.daily?.instances?.all { it.progress == 0 } == true)
    }

    @Test
    fun `monthly milestone pays the theme once and never repeats`() = runBlocking {
        val monthlyDefinitions = TaskConfig.all
            .filter { it.period == TaskPeriod.MONTHLY }
            .take(5)
        val monthly = TaskPeriodState(
            period = TaskPeriod.MONTHLY,
            key = engine.monthlyKey(calendar.today()),
            startEpochDay = calendar.monthStart(calendar.today()),
            instances = monthlyDefinitions.map { definition ->
                TaskInstance(
                    definition = definition,
                    progress = definition.target,
                    claimedAtMillis = now - 1_000L,
                )
            },
        )
        seed(TaskState(monthly = monthly))

        val first = repository.claimMonthlyMilestone()
        val second = repository.claimMonthlyMilestone()

        assertTrue(first is ClaimOutcome.Granted)
        assertEquals(3_000, (first as ClaimOutcome.Granted).coins)
        assertEquals(5, first.hints)
        assertEquals("eclipse_sovereign", first.themeId)
        assertEquals(ClaimOutcome.AlreadyClaimed, second)
        assertEquals(3_000, coins())
        assertEquals(5, hints())
        assertTrue("eclipse_sovereign" in owned())
    }

    @Test
    fun `monthly milestone is rejected until every monthly mission is claimed`() = runBlocking {
        val monthlyDefinitions = TaskConfig.all
            .filter { it.period == TaskPeriod.MONTHLY }
            .take(5)
        val monthly = TaskPeriodState(
            period = TaskPeriod.MONTHLY,
            key = engine.monthlyKey(calendar.today()),
            startEpochDay = calendar.monthStart(calendar.today()),
            instances = monthlyDefinitions.mapIndexed { index, definition ->
                TaskInstance(
                    definition = definition,
                    progress = definition.target,
                    claimedAtMillis = if (index == 0) 0L else now - 1_000L,
                )
            },
        )
        seed(TaskState(monthly = monthly))

        assertEquals(ClaimOutcome.NotComplete, repository.claimMonthlyMilestone())
        assertEquals(0, coins())
    }

    @Test
    fun `refresh rolls the daily set once per local date`() = runBlocking {
        repository.refresh()
        val first = repository.state.first()
        repository.refresh()
        val second = repository.state.first()

        assertEquals(first.daily?.key, second.daily?.key)
        assertEquals(5, second.daily?.instances?.size)
        assertEquals(6, second.weekly?.instances?.size)
        assertEquals(5, second.monthly?.instances?.size)

        now += day
        repository.refresh()
        val tomorrow = repository.state.first()

        assertEquals(true, tomorrow.daily?.key != first.daily?.key)
    }

    @Test
    fun `monthly task cannot be claimed after deadline`() = runBlocking {
        val monthStartMillis = millis(2024, 6, 1)
        val monthStartDay = calendar.epochDayAt(monthStartMillis)
        now = monthStartMillis + 27 * day + 12 * 3600_000L // Day 28, 12:00 UTC (after deadline)

        val monthlyDef = TaskConfig.all.first { it.period == TaskPeriod.MONTHLY }
        val monthly = TaskPeriodState(
            period = TaskPeriod.MONTHLY,
            key = engine.monthlyKey(calendar.today()),
            startEpochDay = monthStartDay,
            instances = listOf(TaskInstance(monthlyDef, progress = monthlyDef.target)),
        )
        seed(TaskState(monthly = monthly))

        val outcome = repository.claim(TaskPeriod.MONTHLY, monthlyDef.id)
        assertEquals(ClaimOutcome.Expired, outcome)
        assertEquals(0, coins())
    }

    @Test
    fun `monthly milestone cannot be claimed after deadline`() = runBlocking {
        val monthStartMillis = millis(2024, 6, 1)
        val monthStartDay = calendar.epochDayAt(monthStartMillis)
        now = monthStartMillis + 27 * day + 12 * 3600_000L // Day 28, 12:00 UTC (after deadline)

        val monthlyDefinitions = TaskConfig.all
            .filter { it.period == TaskPeriod.MONTHLY }
            .take(5)
        val monthly = TaskPeriodState(
            period = TaskPeriod.MONTHLY,
            key = engine.monthlyKey(calendar.today()),
            startEpochDay = monthStartDay,
            instances = monthlyDefinitions.map { definition ->
                TaskInstance(
                    definition = definition,
                    progress = definition.target,
                    claimedAtMillis = now - 1_000L,
                )
            },
        )
        seed(TaskState(monthly = monthly))

        val outcome = repository.claimMonthlyMilestone()
        assertEquals(ClaimOutcome.Expired, outcome)
        assertEquals(0, coins())
    }

    @Test
    fun `daily and weekly tasks remain claimable after monthly deadline`() = runBlocking {
        val monthStartMillis = millis(2024, 6, 1)
        now = monthStartMillis + 27 * day + 12 * 3600_000L // Day 28, 12:00 UTC (after monthly deadline)

        val dailyDef = TaskConfig.all.first { it.period == TaskPeriod.DAILY }
        val weeklyDef = TaskConfig.all.first { it.period == TaskPeriod.WEEKLY }

        val today = calendar.today()
        val dailyState = TaskPeriodState(
            period = TaskPeriod.DAILY,
            key = engine.dailyKey(today),
            startEpochDay = today,
            instances = listOf(TaskInstance(dailyDef, progress = dailyDef.target)),
        )
        val weeklyState = TaskPeriodState(
            period = TaskPeriod.WEEKLY,
            key = engine.weeklyKey(today),
            startEpochDay = calendar.weekStart(today),
            instances = listOf(TaskInstance(weeklyDef, progress = weeklyDef.target)),
        )
        seed(TaskState(daily = dailyState, weekly = weeklyState))

        val dailyClaim = repository.claim(TaskPeriod.DAILY, dailyDef.id)
        assertEquals(ClaimOutcome.Granted(dailyDef.reward.coins, dailyDef.reward.hints, dailyDef.reward.themeId), dailyClaim)

        val weeklyClaim = repository.claim(TaskPeriod.WEEKLY, weeklyDef.id)
        assertTrue("Weekly claim should succeed after monthly deadline", weeklyClaim is ClaimOutcome.Granted)
    }
}
