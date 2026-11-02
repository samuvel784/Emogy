package com.vurka.emogy

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.vurka.emogy.data.GameKeys
import com.vurka.emogy.data.login.LoginClaimResult
import com.vurka.emogy.data.login.LoginRepository
import com.vurka.emogy.data.login.LoginRules
import com.vurka.emogy.data.login.LoginState
import com.vurka.emogy.data.time.GameCalendar
import com.vurka.emogy.data.time.TaskClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Calendar
import java.util.TimeZone

class LoginRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val utc = TimeZone.getTimeZone("UTC")
    private val day = 86_400_000L

    private lateinit var scope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var calendar: GameCalendar
    private lateinit var repository: LoginRepository

    /** Sunday 2024-06-02 12:00 UTC. */
    private var now = millis(2024, 6, 2, 12)

    @Before
    fun setUp() {
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        dataStore = PreferenceDataStoreFactory.create(scope = scope) {
            File(tempFolder.root, "login.preferences_pb")
        }
        calendar = GameCalendar(TaskClock { now }, utc)
        repository = LoginRepository(dataStore, calendar)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun millis(year: Int, month: Int, day: Int, hour: Int = 12): Long =
        Calendar.getInstance(utc).apply {
            clear()
            set(year, month - 1, day, hour, 0, 0)
        }.timeInMillis

    private fun state(): LoginState = runBlocking { repository.state.first() }
    private fun coins(): Int = runBlocking { dataStore.data.first()[GameKeys.COINS] ?: 0 }
    private fun hints(): Int = runBlocking { dataStore.data.first()[GameKeys.HINT_TOKENS] ?: 0 }
    private fun owned(): Set<String> =
        runBlocking { dataStore.data.first()[GameKeys.OWNED_COSMETICS] ?: emptySet() }

    @Test
    fun `checking in twice in one day advances the streak once`() = runBlocking {
        val first = repository.checkIn()
        val second = repository.checkIn()

        assertTrue(first.advanced)
        assertTrue(second.alreadyCheckedIn)
        assertEquals(1, state().streak)
        assertEquals(calendar.today(), state().lastCheckInDay)
    }

    @Test
    fun `consecutive local days build the streak and a missed day resets it`() = runBlocking {
        repository.checkIn()
        now += day
        repository.checkIn()
        assertEquals(2, state().streak)

        now += 2 * day
        val afterGap = repository.checkIn()

        assertTrue(afterGap.brokeStreak)
        assertEquals(1, state().streak)
    }

    @Test
    fun `thirty consecutive days grant the theme exactly once`() = runBlocking {
        var reachedOn: Int? = null
        repeat(30) { index ->
            val result = repository.checkIn()
            if (result.reachedTarget) reachedOn = index + 1
            now += day
        }

        assertEquals(30, reachedOn)
        assertEquals(30, state().streak)
        assertTrue(state().themeGranted)
        assertTrue(LoginRules.STREAK_THEME_ID in owned())

        // Day 31 must not pay the theme a second time or overflow the counter.
        repository.checkIn()
        assertEquals(30, state().streak)
        assertTrue(state().themeGranted)
        assertEquals(1, owned().count { it == LoginRules.STREAK_THEME_ID })
    }

    @Test
    fun `check in never pays coins or hints by itself`() = runBlocking {
        repository.checkIn()
        repository.checkIn()

        assertEquals(0, coins())
        assertEquals(0, hints())
    }

    @Test
    fun `a backwards clock records nothing and grants nothing`() = runBlocking {
        dataStore.edit { it[GameKeys.LAST_SEEN_DAY] = calendar.today() + 5 }
        now -= day

        val checkIn = repository.checkIn()
        val claim = repository.claimToday()

        assertTrue(checkIn.clockAnomaly)
        assertEquals(LoginClaimResult.CLOCK_ANOMALY, claim.result)
        assertEquals(0, state().streak)
        assertEquals(0, coins())
    }

    @Test
    fun `the weekly slot pays once and only for today`() = runBlocking {
        repository.checkIn()

        val first = repository.claimToday()
        val second = repository.claimToday()

        assertEquals(LoginClaimResult.GRANTED, first.result)
        assertEquals(75, first.reward?.coins)
        assertEquals(LoginClaimResult.ALREADY_CLAIMED, second.result)
        assertEquals(75, coins())
        assertTrue(calendar.today() in state().claimedDays)
    }

    @Test
    fun `the Saturday slot includes its hint`() = runBlocking {
        now = millis(2024, 6, 8)
        repository.checkIn()

        val outcome = repository.claimToday()

        assertEquals(LoginClaimResult.GRANTED, outcome.result)
        assertEquals(250, outcome.reward?.coins)
        assertEquals(1, outcome.reward?.hints)
        assertEquals(250, coins())
        assertEquals(1, hints())
    }

    @Test
    fun `a new week offers a fresh slot`() = runBlocking {
        val firstWeekDay = calendar.today()
        repository.checkIn()
        repository.claimToday()
        assertEquals(75, coins())

        now += 7 * day
        repository.checkIn()
        val nextWeek = repository.claimToday()

        assertEquals(LoginClaimResult.GRANTED, nextWeek.result)
        assertEquals(150, coins())
        assertFalse(firstWeekDay in state().claimedDays)
    }

    @Test
    fun `streak and weekly calendar survive independent resets`() = runBlocking {
        // Build a streak through a full week boundary.
        repeat(8) {
            repository.checkIn()
            now += day
        }

        assertEquals(8, state().streak)
        // Old claimed days are pruned by the week window, never by the streak logic.
        assertTrue(state().claimedDays.all { it >= calendar.weekStart(calendar.today()) - 7 })
    }

    @Test
    fun `a legacy last seen day is honoured on migration`() = runBlocking {
        dataStore.edit { it[GameKeys.LAST_SEEN_DAY] = calendar.today() }
        now -= day

        val claim = repository.claimToday()

        assertEquals(LoginClaimResult.CLOCK_ANOMALY, claim.result)
        assertEquals(calendar.today() + 1, state().lastSeenDay)
    }
}
