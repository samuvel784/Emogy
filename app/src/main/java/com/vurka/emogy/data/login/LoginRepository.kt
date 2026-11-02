package com.vurka.emogy.data.login

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.vurka.emogy.data.GameKeys
import com.vurka.emogy.data.gameDataStore
import com.vurka.emogy.data.tasks.TaskReward
import com.vurka.emogy.data.time.GameCalendar
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class WeeklyClaimOutcome(
    val result: LoginClaimResult,
    val reward: TaskReward? = null,
)

/**
 * Durable login state for the seven-day calendar and the independent 30-day streak.
 *
 * Check-in is automatic when the app comes to the foreground; the weekly reward still needs an
 * explicit tap and is only available for the current local date.
 */
class LoginRepository(
    private val dataStore: DataStore<Preferences>,
    private val calendar: GameCalendar,
) {
    constructor(context: Context) : this(context.gameDataStore, GameCalendar())

    val state: Flow<LoginState> = dataStore.data.map { read(it) }

    /**
     * Records today's qualifying login. Idempotent for the same local date: opening the app twice
     * can never advance the streak twice.
     */
    suspend fun checkIn(): CheckInResult {
        var result = CheckInResult(LoginState())
        dataStore.edit { prefs ->
            val current = read(prefs)
            result = LoginRules.checkIn(current, calendar.today())
            if (result.clockAnomaly) return@edit
            write(prefs, result.state)
            if (result.reachedTarget) {
                val owned = prefs[GameKeys.OWNED_COSMETICS] ?: emptySet()
                if (LoginRules.STREAK_THEME_ID !in owned) {
                    prefs[GameKeys.OWNED_COSMETICS] = owned + LoginRules.STREAK_THEME_ID
                }
            }
        }
        return result
    }

    /** Claims today's slot in the Sunday–Saturday calendar. */
    suspend fun claimToday(): WeeklyClaimOutcome {
        var outcome = WeeklyClaimOutcome(LoginClaimResult.CLOCK_ANOMALY)
        dataStore.edit { prefs ->
            val today = calendar.today()
            val current = read(prefs)
            if (today < current.lastSeenDay) {
                outcome = WeeklyClaimOutcome(LoginClaimResult.CLOCK_ANOMALY)
                return@edit
            }
            val weekStart = calendar.weekStart(today)
            val (next, reward) = LoginRules.claimToday(current, today, weekStart)
            if (reward == null) {
                outcome = WeeklyClaimOutcome(LoginClaimResult.ALREADY_CLAIMED)
                return@edit
            }
            write(prefs, next)
            if (reward.coins > 0) {
                prefs[GameKeys.COINS] = ((prefs[GameKeys.COINS] ?: 0) + reward.coins)
                    .coerceIn(0, GameKeys.MAX_COINS)
            }
            if (reward.hints > 0) {
                prefs[GameKeys.HINT_TOKENS] = (prefs[GameKeys.HINT_TOKENS] ?: 0) + reward.hints
            }
            outcome = WeeklyClaimOutcome(LoginClaimResult.GRANTED, reward)
        }
        return outcome
    }

    private fun read(prefs: Preferences): LoginState = LoginState(
        lastCheckInDay = prefs[LAST_CHECK_IN] ?: 0L,
        streak = (prefs[STREAK] ?: 0).coerceIn(0, LoginRules.STREAK_TARGET),
        streakStartDay = prefs[STREAK_START] ?: 0L,
        lastSeenDay = prefs[LAST_SEEN] ?: (prefs[GameKeys.LAST_SEEN_DAY] ?: 0L),
        themeGranted = prefs[THEME_GRANTED] ?: false,
        claimedDays = prefs[CLAIMED_DAYS]?.mapNotNull { it.toLongOrNull() }?.toSet().orEmpty(),
    )

    private fun write(prefs: MutablePreferences, state: LoginState) {
        prefs[LAST_CHECK_IN] = state.lastCheckInDay
        prefs[STREAK] = state.streak
        prefs[STREAK_START] = state.streakStartDay
        prefs[LAST_SEEN] = state.lastSeenDay
        prefs[THEME_GRANTED] = state.themeGranted
        prefs[CLAIMED_DAYS] = state.claimedDays.map { it.toString() }.toSet()
        prefs[GameKeys.LAST_SEEN_DAY] = maxOf(prefs[GameKeys.LAST_SEEN_DAY] ?: 0L, state.lastSeenDay)
    }

    private companion object {
        val LAST_CHECK_IN = longPreferencesKey("login_last_check_in_day")
        val STREAK = intPreferencesKey("login_streak")
        val STREAK_START = longPreferencesKey("login_streak_start_day")
        val LAST_SEEN = longPreferencesKey("login_last_seen_day")
        val THEME_GRANTED = booleanPreferencesKey("login_streak_theme_granted")
        val CLAIMED_DAYS = stringSetPreferencesKey("login_claimed_days")
    }
}
