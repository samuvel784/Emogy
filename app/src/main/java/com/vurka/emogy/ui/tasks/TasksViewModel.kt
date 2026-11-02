package com.vurka.emogy.ui.tasks

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vurka.emogy.R
import com.vurka.emogy.data.ProgressRepository
import com.vurka.emogy.data.login.LoginClaimResult
import com.vurka.emogy.data.login.LoginRepository
import com.vurka.emogy.data.login.LoginRules
import com.vurka.emogy.data.login.LoginSlotState
import com.vurka.emogy.data.login.LoginState
import com.vurka.emogy.data.login.WeeklyClaimOutcome
import com.vurka.emogy.data.store.StoreRepository
import com.vurka.emogy.data.tasks.ClaimBatch
import com.vurka.emogy.data.tasks.ClaimOutcome
import com.vurka.emogy.data.tasks.TaskEngine
import com.vurka.emogy.data.tasks.TaskInstance
import com.vurka.emogy.data.tasks.TaskPeriod
import com.vurka.emogy.data.tasks.TaskPeriodState
import com.vurka.emogy.data.tasks.TaskRepository
import com.vurka.emogy.data.tasks.TaskState
import com.vurka.emogy.data.time.GameCalendar
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LoginSlotUi(
    val index: Int,
    val dayLabel: String,
    val rewardCoins: Int,
    val rewardHints: Int,
    val state: LoginSlotState,
    val isToday: Boolean,
)

data class TasksPeriodUi(
    val period: TaskPeriod,
    val subtitle: String,
    val instances: List<TaskInstance>,
    val windowOpen: Boolean,
    val allClaimed: Boolean,
    val claimableCount: Int,
)

data class MonthlyMilestoneUi(
    val available: Boolean,
    val claimed: Boolean,
    val coins: Int,
    val hints: Int,
    val themeId: String,
)

data class TasksUiState(
    val loading: Boolean = true,
    val coins: Int = 0,
    val hintTokens: Int = 0,
    val ownedCosmetics: Set<String> = emptySet(),
    val daily: TasksPeriodUi? = null,
    val weekly: TasksPeriodUi? = null,
    val monthly: TasksPeriodUi? = null,
    val loginSlots: List<LoginSlotUi> = emptyList(),
    val streak: Int = 0,
    val streakTarget: Int = LoginRules.STREAK_TARGET,
    val streakThemeId: String = LoginRules.STREAK_THEME_ID,
    val streakThemeGranted: Boolean = false,
    val canClaimToday: Boolean = false,
    val weekLabel: String = "",
    val milestone: MonthlyMilestoneUi? = null,
    val monthlyDeadlineLabel: String = "",
    val clockWarning: Boolean = false,
) {
    val hasClaimable: Boolean get() = canClaimToday || (daily?.claimableCount ?: 0) > 0 || (weekly?.claimableCount ?: 0) > 0 || (monthly?.claimableCount ?: 0) > 0 || (milestone != null && milestone.available && !milestone.claimed)
}

sealed interface TaskUiEvent {
    data class Claimed(val coins: Int, val hints: Int, val theme: String?) : TaskUiEvent
    data object NothingToClaim : TaskUiEvent
    data object AlreadyClaimed : TaskUiEvent
    data object ClockAnomaly : TaskUiEvent
    data object LoginAlreadyClaimed : TaskUiEvent
}

class TasksViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val calendar = GameCalendar()
    private val taskRepository = TaskRepository(application)
    private val loginRepository = LoginRepository(application)
    private val storeRepository = StoreRepository(application)
    private val progressRepository = ProgressRepository(application)

    val soundEnabled: StateFlow<Boolean> = progressRepository.soundEnabled.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = true,
    )

    private val _events = MutableSharedFlow<TaskUiEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<TaskUiEvent> = _events

    private var midnightJob: Job? = null

    val ui: StateFlow<TasksUiState> = combine(
        taskRepository.state,
        loginRepository.state,
        storeRepository.coins,
        storeRepository.hintTokens,
        storeRepository.owned,
    ) { tasks, login, coins, hints, owned ->
        buildUi(tasks, login, coins, hints, owned)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TasksUiState(),
    )

    init {
        viewModelScope.launch {
            loginRepository.checkIn()
            taskRepository.refresh()
        }
    }

    /** Long-lived screens refresh on resume so a date change is picked up without a restart. */
    fun onResume() {
        viewModelScope.launch {
            loginRepository.checkIn()
            taskRepository.refresh()
        }
        scheduleMidnightRefresh()
    }

    fun onHidden() {
        midnightJob?.cancel()
        midnightJob = null
    }

    fun claim(period: TaskPeriod, instanceId: String) {
        viewModelScope.launch {
            when (val outcome = taskRepository.claim(period, instanceId)) {
                is ClaimOutcome.Granted -> _events.emit(
                    TaskUiEvent.Claimed(outcome.coins, outcome.hints, outcome.themeId),
                )
                ClaimOutcome.AlreadyClaimed -> _events.emit(TaskUiEvent.AlreadyClaimed)
                ClaimOutcome.ClockAnomaly -> _events.emit(TaskUiEvent.ClockAnomaly)
                else -> _events.emit(TaskUiEvent.NothingToClaim)
            }
        }
    }

    fun claimAll(period: TaskPeriod) {
        viewModelScope.launch {
            val batch: ClaimBatch = taskRepository.claimAll(period)
            if (batch.isEmpty) {
                _events.emit(TaskUiEvent.NothingToClaim)
            } else {
                _events.emit(TaskUiEvent.Claimed(batch.coins, batch.hints, batch.themes.firstOrNull()))
            }
        }
    }

    fun claimMilestone() {
        viewModelScope.launch {
            when (val outcome = taskRepository.claimMonthlyMilestone()) {
                is ClaimOutcome.Granted -> _events.emit(
                    TaskUiEvent.Claimed(outcome.coins, outcome.hints, outcome.themeId),
                )
                ClaimOutcome.AlreadyClaimed -> _events.emit(TaskUiEvent.AlreadyClaimed)
                ClaimOutcome.ClockAnomaly -> _events.emit(TaskUiEvent.ClockAnomaly)
                else -> _events.emit(TaskUiEvent.NothingToClaim)
            }
        }
    }

    fun claimWeeklyLogin() {
        viewModelScope.launch {
            // A slot belongs to today's login, so make sure today is checked in first.
            loginRepository.checkIn()
            when (val outcome = loginRepository.claimToday()) {
                is WeeklyClaimOutcome -> when (outcome.result) {
                    LoginClaimResult.GRANTED -> {
                        val reward = outcome.reward
                        _events.emit(
                            TaskUiEvent.Claimed(reward?.coins ?: 0, reward?.hints ?: 0, null),
                        )
                    }
                    LoginClaimResult.ALREADY_CLAIMED -> _events.emit(TaskUiEvent.LoginAlreadyClaimed)
                    LoginClaimResult.CLOCK_ANOMALY -> _events.emit(TaskUiEvent.ClockAnomaly)
                }
            }
        }
    }

    private fun scheduleMidnightRefresh() {
        midnightJob?.cancel()
        midnightJob = viewModelScope.launch {
            while (true) {
                val wait = (calendar.nextMidnightMillis() - calendar.nowMillis() + 1_500L).coerceAtLeast(1_000L)
                delay(wait)
                loginRepository.checkIn()
                taskRepository.refresh()
            }
        }
    }

    private fun buildUi(
        tasks: TaskState,
        login: LoginState,
        coins: Int,
        hints: Int,
        owned: Set<String>,
    ): TasksUiState {
        val today = calendar.today()
        val weekStart = calendar.weekStart(today)
        val monthlyOpen = calendar.isMonthlyWindowOpen(today)
        val daysLeft = calendar.daysUntilMonthlyDeadline(today)
        val engineState = TaskEngine(calendar).ensureCurrent(tasks, calendar.nowMillis())

        val daily = engineState.daily
        val weekly = engineState.weekly
        val monthly = engineState.monthly
        val milestoneClaimed = monthly?.milestoneClaimed == true
        val milestoneReady = monthly != null &&
            monthly.instances.isNotEmpty() &&
            monthly.instances.all { it.isClaimed }

        return TasksUiState(
            loading = daily == null || weekly == null || monthly == null,
            coins = coins,
            hintTokens = hints,
            ownedCosmetics = owned,
            daily = daily?.toUi(
                period = TaskPeriod.DAILY,
                subtitle = context.getString(
                    R.string.tasks_daily_subtitle,
                    calendar.dayLabel(today),
                ),
                windowOpen = true,
            ),
            weekly = weekly?.toUi(
                period = TaskPeriod.WEEKLY,
                subtitle = context.getString(R.string.tasks_weekly_subtitle),
                windowOpen = true,
            ),
            monthly = monthly?.toUi(
                period = TaskPeriod.MONTHLY,
                subtitle = if (monthlyOpen) {
                    context.getString(
                        R.string.tasks_monthly_subtitle,
                        calendar.deadlineLabel(today),
                        context.resources.getQuantityString(
                            R.plurals.tasks_monthly_days_left,
                            daysLeft,
                            daysLeft,
                        ),
                    )
                } else {
                    context.getString(
                        R.string.tasks_monthly_subtitle_closed,
                        calendar.deadlineLabel(today),
                    )
                },
                windowOpen = monthlyOpen,
            ),
            loginSlots = LoginRules.weekSlots(login, weekStart, today).map { slot ->
                LoginSlotUi(
                    index = slot.index,
                    dayLabel = calendar.shortDayLabel(slot.epochDay),
                    rewardCoins = slot.reward.coins,
                    rewardHints = slot.reward.hints,
                    state = slot.state,
                    isToday = slot.epochDay == today,
                )
            },
            streak = login.streak,
            streakThemeGranted = login.themeGranted || LoginRules.STREAK_THEME_ID in owned,
            canClaimToday = LoginRules.canClaimToday(login, today, weekStart),
            weekLabel = "${calendar.dayLabel(weekStart)} – ${calendar.dayLabel(weekStart + 6)}",
            milestone = if (monthly == null) {
                null
            } else {
                MonthlyMilestoneUi(
                    available = milestoneReady && !milestoneClaimed,
                    claimed = milestoneClaimed,
                    coins = TaskRepository.MONTHLY_MILESTONE_REWARD.coins,
                    hints = TaskRepository.MONTHLY_MILESTONE_REWARD.hints,
                    themeId = TaskRepository.MONTHLY_MILESTONE_REWARD.themeId.orEmpty(),
                )
            },
            monthlyDeadlineLabel = calendar.deadlineLabel(today),
            clockWarning = login.lastSeenDay > today || (tasks.daily?.key?.removePrefix("D")?.toLongOrNull() ?: today) > today,
        )
    }

    private fun TaskPeriodState.toUi(
        period: TaskPeriod,
        subtitle: String,
        windowOpen: Boolean,
    ): TasksPeriodUi = TasksPeriodUi(
        period = period,
        subtitle = subtitle,
        instances = instances,
        windowOpen = windowOpen,
        allClaimed = instances.isNotEmpty() && instances.all { it.isClaimed },
        claimableCount = instances.count { it.isComplete && !it.isClaimed },
    )
}
