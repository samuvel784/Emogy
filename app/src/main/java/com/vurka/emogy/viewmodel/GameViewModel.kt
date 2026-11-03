package com.vurka.emogy.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vurka.emogy.data.DailyChallenge
import com.vurka.emogy.data.EmojiCatalog
import com.vurka.emogy.data.GameSnapshot
import com.vurka.emogy.data.LevelCatalog
import com.vurka.emogy.data.SnapshotCard
import com.vurka.emogy.data.tasks.LevelCompletedEvent
import com.vurka.emogy.game.AssistRules
import com.vurka.emogy.game.GameState
import com.vurka.emogy.game.MemoryGameEngine
import com.vurka.emogy.model.LevelConfig
import com.vurka.emogy.model.MemoryCard
import kotlin.random.Random
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class GameViewModel(
    val levelNumber: Int,
    private val progress: GameProgress,
    private val settings: GameSettings,
    private val feedback: GameFeedback,
    private val clock: GameClock,
    themeIdFlow: Flow<String>,
    private val snapshots: GameSnapshotStore,
    /** A daily run uses [DailyChallenge] instead of a numbered level. */
    val isDaily: Boolean = false,
    val dailyEpochDay: Long = 0L,
    cardThemeIdFlow: Flow<String> = flowOf("classic"),
    boardThemeIdFlow: Flow<String> = flowOf("board_default"),
    matchEffectIdFlow: Flow<String> = flowOf("sparkle_none"),
    victoryEffectIdFlow: Flow<String> = flowOf("confetti_classic"),
    private val hints: HintWallet = NoHintWallet,
    private val tasks: GameplayTracker = NoGameplayTracker,
) : ViewModel() {
    val levelConfig: LevelConfig =
        if (isDaily) DailyChallenge.config else LevelCatalog.getLevel(levelNumber)

    private fun newEngine() = MemoryGameEngine(
        levelConfig,
        if (isDaily) Random(DailyChallenge.seedFor(dailyEpochDay)) else Random.Default,
    )

    private var engine = newEngine()

    val selectedThemeId: StateFlow<String> = themeIdFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = "default",
    )

    val selectedCardThemeId: StateFlow<String> = cardThemeIdFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = "classic",
    )

    val selectedBoardThemeId: StateFlow<String> = boardThemeIdFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = "board_default",
    )

    val selectedMatchEffectId: StateFlow<String> = matchEffectIdFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = "sparkle_none",
    )

    val selectedVictoryEffectId: StateFlow<String> = victoryEffectIdFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = "confetti_classic",
    )

    /** Purchased hint inventory, shown next to the free allowance in the HUD. */
    val hintTokens: StateFlow<Int> = hints.tokens.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = 0,
    )

    /** Human friendly name of the world this level belongs to, or the daily label. */
    val worldName: String =
        if (isDaily) "Daily Challenge" else LevelCatalog.getWorldName(levelNumber)

    /** Mirrors the sound preference so the victory/loss dialogs can play their own feedback. */
    val soundEnabled: StateFlow<Boolean> = settings.soundEnabled.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = true,
    )

    private val hapticsEnabled: StateFlow<Boolean> = settings.hapticsEnabled.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = true,
    )

    /** UI mirror of Assist mode; the top bar only shows the heart button when this is true. */
    val isAssistMode: StateFlow<Boolean> = settings.assistMode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = false,
    )

    private var assistModeEnabled: Boolean = false

    private val _state = MutableStateFlow(GameState())
    val state: StateFlow<GameState> = _state.asStateFlow()

    private var pendingFlips = 0
    private var pendingAnimalFlips = 0

    fun flushPendingFlips() {
        val flips = pendingFlips
        val animalFlips = pendingAnimalFlips
        if (flips <= 0) return
        pendingFlips = 0
        pendingAnimalFlips = 0
        val nonAnimalFlips = flips - animalFlips
        viewModelScope.launch {
            if (nonAnimalFlips > 0) {
                progress.incrementCardFlips(nonAnimalFlips, isAnimal = false)
            }
            if (animalFlips > 0) {
                progress.incrementCardFlips(animalFlips, isAnimal = true)
            }
        }
    }

    private var previewJob: Job? = null
    private var timerJob: Job? = null
    private var mismatchJob: Job? = null
    private var hintJob: Job? = null
    private var assistJob: Job? = null
    private var cooldownJob: Job? = null
    private var autoHideJob: Job? = null
    private var snapshotJob: Job? = null
    private var resumeJob: Job? = null

    init {
        // Wait for the persisted settings before dealing. Otherwise a Zen run could be created with
        // finite hints and a shuffle countdown while DataStore is still answering.
        viewModelScope.launch {
            // Single authoritative "last played" write: entering a level from Home, the map, or a
            // victory shortcut all end up here. A daily run is not a level, so it must not move the
            // Continue button or overwrite the suspended level's snapshot.
            if (!isDaily) progress.setLastPlayedLevel(levelNumber)
            assistModeEnabled = settings.assistMode.first()

            if (isDaily) {
                startNewGame()
                return@launch
            }

            val saved = snapshots.load()
            when {
                saved == null -> startNewGame()
                saved.level != levelNumber -> startNewGame()
                saved.isExpired(clock.nowMillis()) -> {
                    snapshots.clear()
                    startNewGame()
                }
                !restore(saved) -> {
                    snapshots.clear()
                    startNewGame()
                }
            }
        }
    }

    fun calculateStars(totalPairs: Int, timeElapsedSec: Int, mistakes: Int, timeLimitSec: Int = 0): Int =
        engine.calculateStars(totalPairs, timeElapsedSec, mistakes, timeLimitSec)

    private fun cancelAllJobs() {
        previewJob?.cancel()
        timerJob?.cancel()
        mismatchJob?.cancel()
        hintJob?.cancel()
        assistJob?.cancel()
        cooldownJob?.cancel()
        autoHideJob?.cancel()
        snapshotJob?.cancel()
        resumeJob?.cancel()
    }

    fun startNewGame() {
        cancelAllJobs()
        // A daily replay must deal the exact same board, so the engine is recreated from the
        // day seed every time.
        engine = newEngine()
        if (!isDaily) {
            viewModelScope.launch { snapshots.clear() }
        }

        val deck = engine.generateDeck()
        _state.value = GameState(
            cards = deck.map { it.copy(isFlipped = true) },
            totalPairs = levelConfig.pairCount,
            isPreviewing = true,
            timeRemainingSec = levelConfig.timeLimitSec,
            hintsLeft = levelConfig.maxHints,
            shuffleCountdownSec = levelConfig.shuffleIntervalSec,
        )
    }

    fun manualFlipCards() {
        val current = _state.value
        if (!current.isPreviewing) return
        previewJob?.cancel()
        _state.update { state ->
            state.copy(
                cards = state.cards.map { it.copy(isFlipped = false) },
                isPreviewing = false,
            )
        }
        if (!_state.value.isPaused) {
            startTimer()
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1.seconds)
                val snapshot = _state.value
                if (snapshot.isPaused || snapshot.isGameOver || snapshot.isPreviewing) break
                tick()
            }
        }
    }

    private fun tick() {
        var lostByTime = false
        var shuffled = false

        _state.update { current ->
            lostByTime = false
            shuffled = false
            if (current.isPaused || current.isGameOver) return@update current

            val elapsed = current.timeElapsedSec + 1
            var remaining = current.timeRemainingSec
            var lost = false
            if (levelConfig.timeLimitSec > 0) {
                remaining -= 1
                if (remaining <= 0) lost = true
            }

            var next = current.copy(
                timeElapsedSec = elapsed,
                timeRemainingSec = remaining.coerceAtLeast(0),
                isGameOver = lost,
                isWon = false,
                lossReason = if (lost) "time" else current.lossReason,
                assistCooldownRemainingSec = (current.assistCooldownRemainingSec - 1).coerceAtLeast(0),
            )
            lostByTime = lost

            // Reshuffle countdown (only while the player is idle, so a pick is never disturbed).
            if ((!lost) && (levelConfig.shuffleIntervalSec > 0)) {
                val idle = (current.firstSelectedCardId == null) &&
                    (current.secondSelectedCardId == null) &&
                    (mismatchJob?.isActive != true)
                val left = current.shuffleCountdownSec - 1
                if (left <= 0) {
                    if (idle) {
                        next = next.copy(
                            cards = engine.shuffleUnmatched(current.cards),
                            shuffleCountdownSec = levelConfig.shuffleIntervalSec,
                            shuffleCount = current.shuffleCount + 1,
                        )
                        shuffled = true
                    } else {
                        next = next.copy(shuffleCountdownSec = 1) // retry next second
                    }
                } else {
                    next = next.copy(shuffleCountdownSec = left)
                }
            }
            next
        }

        if (shuffled) {
            scheduleSnapshotSave()
            feedback.play(
                cue = GameCue.SHUFFLE,
                soundEnabled = soundEnabled.value,
                hapticsEnabled = hapticsEnabled.value,
            )
        }
        if (lostByTime) {
            timerJob?.cancel()
            clearSnapshot()
            feedback.play(
                cue = GameCue.DEFEAT,
                soundEnabled = soundEnabled.value,
                hapticsEnabled = hapticsEnabled.value,
            )
            recordLoss()
        } else {
            // Final ten seconds: a quiet tick, sharper and higher for the last three.
            val snapshot = _state.value
            if ((!snapshot.isGameOver) && (!snapshot.isPaused) && (levelConfig.timeLimitSec > 0)) {
                val remaining = snapshot.timeRemainingSec
                if (remaining in 1..10) {
                    feedback.play(
                        cue = if (remaining <= 3) GameCue.TICK_URGENT else GameCue.TICK,
                        soundEnabled = soundEnabled.value,
                        hapticsEnabled = false,
                    )
                }
            }
        }
    }

    /**
     * Reports one genuine victory to the task engine. Called exactly once per win, after the coin
     * write, so the payload contains the real payout. A replay still counts as a completed level
     * (missions ask for levels, not first clears), but it reports zero coins.
     */
    private suspend fun recordTaskProgress(
        matchedCards: List<MemoryCard>,
        stars: Int,
        moves: Int,
    ) {
        val current = _state.value
        val worldIndex = if (isDaily) 0 else LevelCatalog.getWorldInfo(levelNumber).index
        tasks.record(
            LevelCompletedEvent(
                atMillis = clock.nowMillis(),
                levelId = if (isDaily) 0 else levelNumber,
                world = worldIndex,
                stars = stars,
                pairs = current.totalPairs,
                distinctEmojiIds = matchedCards.filter { it.isMatched }.map { it.emojiId }.toSet(),
                moves = moves,
                mistakes = current.mistakes,
                hintsUsed = current.hintsUsed,
                shuffles = current.shuffleCount,
                seconds = current.timeElapsedSec,
                coins = current.coinsAwarded,
                isBoss = levelConfig.isBoss,
                isDaily = isDaily,
                isTimed = levelConfig.timeLimitSec > 0,
                isHighDifficulty = levelConfig.difficultyLabel == "Expert" ||
                    levelConfig.difficultyLabel == "Master",
            ),
        )
    }

    /**
     * A finished run counts once, whether it was won or lost: the daily streak should reward the
     * days you played, and "games played" must not silently ignore defeats.
     */
    private fun recordLoss() {
        flushPendingFlips()
        viewModelScope.launch {
            progress.updateDailyStreak()
            progress.updatePlayStats(isPerfect = false)
        }
    }

    fun useHint() {
        val current = _state.value
        if (current.isPreviewing || current.isPaused || current.isGameOver || current.isHintOnCooldown) return

        val freeAvailable = current.hintsLeft < 0 || current.hintsLeft > 0
        val tokenAvailable = hintTokens.value > 0
        if (!freeAvailable && !tokenAvailable) return

        // Block rapid repeated taps synchronously
        _state.update { it.copy(isHintOnCooldown = true, hintCooldownRemainingSec = 10) }

        if (freeAvailable) {
            revealHint(consumeFree = true)
        } else {
            viewModelScope.launch {
                if (!hints.tryConsume()) {
                    _state.update { it.copy(isHintOnCooldown = false, hintCooldownRemainingSec = 0) }
                    return@launch
                }
                revealHint(consumeFree = false)
            }
        }
    }

    private fun revealHint(consumeFree: Boolean) {
        feedback.play(
            cue = GameCue.HINT,
            soundEnabled = soundEnabled.value,
            hapticsEnabled = hapticsEnabled.value,
        )

        _state.update { state ->
            val nextHintsLeft = if (consumeFree && state.hintsLeft > 0) state.hintsLeft - 1 else state.hintsLeft
            state.copy(
                cards = state.cards.map { if (!it.isMatched) it.copy(isFlipped = true) else it },
                isHintOnCooldown = true,
                hintCooldownRemainingSec = 10,
                hintsLeft = nextHintsLeft,
                hintsUsed = state.hintsUsed + 1,
            )
        }
        scheduleSnapshotSave()

        hintJob?.cancel()
        hintJob = viewModelScope.launch {
            delay(5000L)
            _state.update { state ->
                state.copy(
                    cards = state.cards.map {
                        if ((!it.isMatched) && (it.id != state.firstSelectedCardId) && (it.id != state.secondSelectedCardId)) {
                            it.copy(isFlipped = false)
                        } else it
                    }
                )
            }
        }

        cooldownJob?.cancel()
        cooldownJob = viewModelScope.launch {
            for (sec in 10 downTo 1) {
                delay(1.seconds)
                _state.update { it.copy(hintCooldownRemainingSec = sec - 1) }
            }
            _state.update { it.copy(isHintOnCooldown = false) }
        }
    }

    /**
     * Assist mode: reveal one matching pair, regardless of hint allowance. Boss levels have zero
     * hints, so Assist is the one helping hand they get. It does not touch moves, mistakes or stars.
     */
    fun useAssist() {
        if (!assistModeEnabled) return
        val current = _state.value
        if (current.isPreviewing || current.isPaused || current.isGameOver) return
        if (current.assistCooldownRemainingSec > 0) return

        val pair = AssistRules.pickPair(current.cards)
        if (pair.size != 2) return

        feedback.play(
            cue = GameCue.HINT,
            soundEnabled = soundEnabled.value,
            hapticsEnabled = hapticsEnabled.value,
        )

        _state.update {
            it.copy(
                assistCardIds = pair,
                assistCooldownRemainingSec = AssistRules.COOLDOWN_SEC,
            )
        }

        assistJob?.cancel()
        assistJob = viewModelScope.launch {
            delay(AssistRules.REVEAL_MS.milliseconds)
            _state.update { state ->
                // Leave matched cards alone; only this reveal is cleared.
                state.copy(assistCardIds = state.assistCardIds - pair)
            }
        }
    }

    fun onCardClicked(cardId: Int) {
        val current = _state.value
        if (current.isPreviewing || current.isPaused || current.isGameOver || (mismatchJob?.isActive == true)) return

        val clickedCard = current.cards.find { it.id == cardId } ?: return
        if (clickedCard.isFlipped || clickedCard.isMatched) return

        feedback.play(
            cue = GameCue.FLIP,
            soundEnabled = soundEnabled.value,
            hapticsEnabled = hapticsEnabled.value,
        )

        val isAnimal = clickedCard.emoji in EmojiCatalog.animalEmojiIds
        pendingFlips++
        if (isAnimal) pendingAnimalFlips++

        val updatedCards = current.cards.map { if (it.id == cardId) it.copy(isFlipped = true) else it }

        // ---- First card of a pair ----
        if (current.firstSelectedCardId == null) {
            _state.value = current.copy(cards = updatedCards, firstSelectedCardId = cardId)
            scheduleAutoHide(cardId)
            return
        }
        if ((current.secondSelectedCardId != null) || (current.firstSelectedCardId == cardId)) return

        // ---- Second card of a pair ----
        autoHideJob?.cancel()
        val firstCard = updatedCards.find { it.id == current.firstSelectedCardId } ?: return
        val newMoves = current.moves + 1
        flushPendingFlips()

        if (firstCard.emojiId == clickedCard.emojiId) {
            // Every successful pair unlocks its emoji in the offline album, including the final
            // pair of a level and daily runs.
            viewModelScope.launch {
                progress.collectEmoji(firstCard.emojiId)
            }
            val matchedCards = updatedCards.map {
                if ((it.id == cardId) || (it.id == firstCard.id)) it.copy(isMatched = true) else it
            }
            val newMatchedPairs = current.matchedPairsCount + 1
            val newCombo = current.combo + 1
            val newMaxCombo = maxOf(current.maxCombo, newCombo)
            val won = newMatchedPairs == current.totalPairs

            if (won) {
                // The final pair gets the fanfare only - playing the match chime first made the two
                // cues collide into noise.
                feedback.play(
                    cue = GameCue.VICTORY,
                    soundEnabled = soundEnabled.value,
                    hapticsEnabled = hapticsEnabled.value,
                )

                cancelAllJobs()
                clearSnapshot()

                val earnedStars = calculateStars(
                    totalPairs = current.totalPairs,
                    timeElapsedSec = current.timeElapsedSec,
                    mistakes = current.mistakes,
                    timeLimitSec = levelConfig.timeLimitSec
                )

                _state.value = current.copy(
                    cards = matchedCards,
                    firstSelectedCardId = null,
                    secondSelectedCardId = null,
                    moves = newMoves,
                    matchedPairsCount = newMatchedPairs,
                    combo = newCombo,
                    maxCombo = newMaxCombo,
                    isGameOver = true,
                    isWon = true,
                    stars = earnedStars,
                    assistCardIds = emptySet(),
                )

                // Exactly one write per victory. The dialog appears immediately and the reward
                // arrives as a state update a moment later.
                viewModelScope.launch {
                    if (isDaily) {
                        val result = progress.recordDailyChallenge(
                            day = dailyEpochDay,
                            moves = newMoves,
                            timeSec = current.timeElapsedSec,
                        )
                        progress.updateDailyStreak()
                        progress.updatePlayStats(current.mistakes == 0)
                        _state.update {
                            it.copy(
                                coinsAwarded = result.coinsAwarded,
                                isFirstClear = result.isFirstCompletion,
                                isPersonalBest = result.isPersonalBestTime,
                            )
                        }
                    } else {
                        val result = progress.recordLevelResult(
                            level = levelNumber,
                            stars = earnedStars,
                            moves = newMoves,
                            timeSec = current.timeElapsedSec,
                        )
                        progress.updateDailyStreak()
                        progress.updatePlayStats(current.mistakes == 0)
                        _state.update {
                            it.copy(
                                coinsAwarded = result.coinsAwarded,
                                isFirstClear = result.isFirstClear,
                                isPersonalBest = result.isPersonalBest,
                            )
                        }
                    }
                    recordTaskProgress(matchedCards, earnedStars, newMoves)
                }
                return
            }

            // The match chime climbs the pentatonic ladder with the combo, so long chains sound
            // like a melody instead of the same beep repeated.
            feedback.play(
                cue = GameCue.MATCH,
                combo = newCombo,
                soundEnabled = soundEnabled.value,
                hapticsEnabled = hapticsEnabled.value,
            )

            _state.value = current.copy(
                cards = matchedCards,
                firstSelectedCardId = null,
                secondSelectedCardId = null,
                moves = newMoves,
                matchedPairsCount = newMatchedPairs,
                combo = newCombo,
                maxCombo = newMaxCombo,
                assistCardIds = emptySet(),
            )
            scheduleSnapshotSave()
        } else {
            feedback.play(
                cue = GameCue.MISMATCH,
                soundEnabled = soundEnabled.value,
                hapticsEnabled = hapticsEnabled.value,
            )

            val firstId = current.firstSelectedCardId
            _state.value = current.copy(
                cards = updatedCards,
                secondSelectedCardId = cardId,
                moves = newMoves,
                mistakes = current.mistakes + 1,
                combo = 0,
                assistCardIds = emptySet(),
            )
            scheduleSnapshotSave()

            mismatchJob = viewModelScope.launch {
                delay(levelConfig.mismatchDelayMs.milliseconds)
                _state.update { state ->
                    state.copy(
                        cards = state.cards.map {
                            if ((it.id == firstId) || (it.id == cardId)) it.copy(isFlipped = false) else it
                        },
                        firstSelectedCardId = null,
                        secondSelectedCardId = null
                    )
                }
            }
        }

        // Move limit
        if ((levelConfig.moveLimit > 0) && (newMoves >= levelConfig.moveLimit) && (!_state.value.isWon)) {
            cancelAllJobs()
            clearSnapshot()
            feedback.play(
                cue = GameCue.DEFEAT,
                soundEnabled = soundEnabled.value,
                hapticsEnabled = hapticsEnabled.value,
            )
            _state.update { it.copy(isGameOver = true, isWon = false, lossReason = "moves") }
            recordLoss()
        }
    }

    /** High levels: a lone face-up card flips back by itself, so the player must be quick. */
    private fun scheduleAutoHide(cardId: Int) {
        autoHideJob?.cancel()
        if (levelConfig.autoHideMs <= 0) return
        autoHideJob = viewModelScope.launch {
            delay(levelConfig.autoHideMs.milliseconds)
            _state.update { state ->
                if (state.firstSelectedCardId == cardId && state.secondSelectedCardId == null && !state.isPaused && !state.isGameOver) {
                    state.copy(
                        cards = state.cards.map { if (it.id == cardId) it.copy(isFlipped = false) else it },
                        firstSelectedCardId = null
                    )
                } else state
            }
        }
    }

    /** Saves the current board now. Called when the activity stops. */
    fun saveSnapshotNow() {
        flushPendingFlips()
        viewModelScope.launch { persistSnapshot() }
    }

    /** Leaving the game through the pause menu discards the resumable run. */
    fun abandonGame() {
        flushPendingFlips()
        cancelAllJobs()
        clearSnapshot()
    }

    private fun scheduleSnapshotSave() {
        snapshotJob?.cancel()
        snapshotJob = viewModelScope.launch {
            delay(400)
            persistSnapshot()
        }
    }

    private suspend fun persistSnapshot() {
        // Daily runs are one sitting by design: they never overwrite the resumable level.
        if (isDaily) return
        val current = _state.value
        if (current.cards.isEmpty() || current.isGameOver || current.isPreviewing) return
        snapshots.save(
            GameSnapshot(
                level = levelNumber,
                savedAtMillis = clock.nowMillis(),
                cards = current.cards.map { card ->
                    SnapshotCard(
                        id = card.id,
                        emojiId = card.emojiId,
                        emoji = card.emoji,
                        isMatched = card.isMatched,
                    )
                },
                moves = current.moves,
                mistakes = current.mistakes,
                matchedPairs = current.matchedPairsCount,
                timeElapsedSec = current.timeElapsedSec,
                timeRemainingSec = current.timeRemainingSec,
                hintsLeft = current.hintsLeft,
                combo = current.combo,
                maxCombo = current.maxCombo,
                shuffleCountdownSec = current.shuffleCountdownSec,
                shuffleCount = current.shuffleCount,
                wasPaused = current.isPaused,
                hintsUsed = current.hintsUsed,
            )
        )
    }

    private fun clearSnapshot() {
        if (isDaily) return
        snapshotJob?.cancel()
        viewModelScope.launch { snapshots.clear() }
    }

    private fun restore(snapshot: GameSnapshot): Boolean {
        val expectedCards = levelConfig.pairCount * 2
        if (snapshot.cards.size != expectedCards) return false
        if (snapshot.cards.map { it.id }.distinct().size != expectedCards) return false
        if (snapshot.cards.any { it.id < 0 }) return false

        val byEmoji = snapshot.cards.groupBy { it.emojiId }
        if (byEmoji.size != levelConfig.pairCount || byEmoji.values.any { it.size != 2 }) return false

        val matchedCards = snapshot.cards.count { it.isMatched }
        if (matchedCards % 2 != 0) return false
        if (matchedCards / 2 != snapshot.matchedPairs) return false
        if (snapshot.matchedPairs !in 0 until levelConfig.pairCount) return false

        if (snapshot.moves < 0 || snapshot.mistakes < 0) return false
        if (snapshot.timeElapsedSec < 0 || snapshot.timeRemainingSec < 0) return false
        if (levelConfig.timeLimitSec > 0) {
            if (snapshot.timeRemainingSec <= 0 || snapshot.timeRemainingSec > levelConfig.timeLimitSec) return false
        } else if (snapshot.timeRemainingSec != 0) {
            return false
        }
        if (snapshot.hintsLeft < -1) return false
        if (levelConfig.maxHints >= 0 && snapshot.hintsLeft > levelConfig.maxHints) return false
        if (snapshot.hintsUsed < 0) return false
        if (snapshot.combo < 0 || snapshot.maxCombo < snapshot.combo) return false
        if (snapshot.shuffleCountdownSec < 0 || snapshot.shuffleCount < 0) return false
        if (levelConfig.shuffleIntervalSec == 0 && snapshot.shuffleCountdownSec != 0) return false
        if (levelConfig.shuffleIntervalSec > 0 && snapshot.shuffleCountdownSec > levelConfig.shuffleIntervalSec) {
            return false
        }
        _state.value = GameState(
            cards = snapshot.cards.map { card ->
                MemoryCard(
                    id = card.id,
                    emojiId = card.emojiId,
                    emoji = card.emoji,
                    isFlipped = false,
                    isMatched = card.isMatched,
                )
            },
            totalPairs = levelConfig.pairCount,
            moves = snapshot.moves,
            mistakes = snapshot.mistakes,
            matchedPairsCount = snapshot.matchedPairs,
            isPaused = snapshot.wasPaused,
            timeElapsedSec = snapshot.timeElapsedSec,
            timeRemainingSec = snapshot.timeRemainingSec,
            hintsLeft = snapshot.hintsLeft,
            combo = snapshot.combo,
            maxCombo = snapshot.maxCombo,
            shuffleCountdownSec = snapshot.shuffleCountdownSec,
            shuffleCount = snapshot.shuffleCount,
            hintsUsed = snapshot.hintsUsed,
            wasRestored = true,
            restoredPaused = snapshot.wasPaused,
        )

        if (!snapshot.wasPaused) {
            // A short grace period so the player is not dropped straight into a ticking clock.
            resumeJob = viewModelScope.launch {
                delay(1.seconds)
                val current = _state.value
                if (!current.isPaused && !current.isGameOver && !current.isPreviewing) startTimer()
            }
        }
        return true
    }

    fun togglePause() {
        if (_state.value.isGameOver) return
        setPaused(!_state.value.isPaused)
    }

    /** Called when the app goes to the background, so the clock never runs while away. */
    fun pauseIfRunning() {
        val s = _state.value
        // Pause during the memorise preview too: otherwise the preview ends while the app is in
        // the background and the countdown starts ticking unseen.
        if (!s.isPaused && !s.isGameOver) setPaused(paused = true)
    }

    private fun setPaused(paused: Boolean) {
        _state.update { it.copy(isPaused = paused) }
        feedback.play(
            cue = GameCue.PAUSE,
            soundEnabled = soundEnabled.value,
            hapticsEnabled = false,
        )
        if (paused) {
            flushPendingFlips()
            timerJob?.cancel()
            autoHideJob?.cancel()
            assistJob?.cancel()
            _state.update { it.copy(assistCardIds = emptySet()) }
            saveSnapshotNow()
        } else {
            val s = _state.value
            if (!s.isGameOver && !s.isPreviewing) startTimer()
            s.firstSelectedCardId?.let { if (s.secondSelectedCardId == null) scheduleAutoHide(it) }
        }
    }

    override fun onCleared() {
        flushPendingFlips()
        cancelAllJobs()
    }
}
