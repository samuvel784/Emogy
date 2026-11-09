package com.vurka.emogy

import androidx.lifecycle.viewModelScope
import com.vurka.emogy.data.DailyChallenge
import com.vurka.emogy.data.LevelRecordResult
import com.vurka.emogy.game.AssistRules
import com.vurka.emogy.viewmodel.FakeClock
import com.vurka.emogy.viewmodel.FakeFeedback
import com.vurka.emogy.viewmodel.FakeGameplayTracker
import com.vurka.emogy.viewmodel.FakeHintWallet
import com.vurka.emogy.viewmodel.FakeProgress
import com.vurka.emogy.viewmodel.FakeSettings
import com.vurka.emogy.viewmodel.FakeSnapshotStore
import com.vurka.emogy.viewmodel.GameClock
import com.vurka.emogy.viewmodel.GameCue
import com.vurka.emogy.viewmodel.GameFeedback
import com.vurka.emogy.viewmodel.GameProgress
import com.vurka.emogy.viewmodel.GameSettings
import com.vurka.emogy.viewmodel.GameViewModel
import com.vurka.emogy.viewmodel.GameplayTracker
import com.vurka.emogy.viewmodel.HintWallet
import com.vurka.emogy.viewmodel.NoGameplayTracker
import com.vurka.emogy.viewmodel.NoHintWallet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Behavioural tests for the timer, rules and reward flow. They use virtual time, so a 90-second
 * level completes instantly and never depends on the wall clock.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelTest {

    private lateinit var dispatcher: TestDispatcher

    @Before
    fun setUp() {
        dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun previewEndsThenTimerCountsDown() = runTest(dispatcher) {
        val vm = newGame(level = 21, finishPreview = false)

        assertTrue(vm.state.value.isPreviewing)
        assertTrue(vm.state.value.cards.all { it.isFlipped })
        assertEquals(vm.levelConfig.timeLimitSec, vm.state.value.timeRemainingSec)
        assertEquals(0, vm.state.value.timeElapsedSec)

        finishPreview(vm)
        assertFalse(vm.state.value.isPreviewing)

        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(1, vm.state.value.timeElapsedSec)
        assertEquals(vm.levelConfig.timeLimitSec - 1, vm.state.value.timeRemainingSec)

        vm.viewModelScope.cancel()
    }

    @Test
    fun timeLimitLossIsRecordedExactlyOnce() = runTest(dispatcher) {
        val progress = FakeProgress()
        val feedback = FakeFeedback()
        val vm = newGame(level = 21, progress = progress, feedback = feedback)

        advanceTimeBy(vm.levelConfig.timeLimitSec * 1_000L)
        runCurrent()

        assertTrue(vm.state.value.isGameOver)
        assertFalse(vm.state.value.isWon)
        assertEquals("time", vm.state.value.lossReason)
        assertEquals(1, progress.losses)
        assertTrue(progress.records.isEmpty())
        assertEquals(1, feedback.calls.count { it.cue == GameCue.DEFEAT })

        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(1, progress.losses)

        vm.viewModelScope.cancel()
    }

    @Test
    fun moveLimitLossStopsTheRun() = runTest(dispatcher) {
        val progress = FakeProgress()
        val vm = newGame(level = 41, progress = progress)
        assertTrue("level 41 must have a move limit", vm.levelConfig.moveLimit > 0)

        var guard = 0
        while (!vm.state.value.isGameOver && guard++ < 100) {
            mismatchOnce(vm)
        }

        assertTrue(vm.state.value.isGameOver)
        assertFalse(vm.state.value.isWon)
        assertEquals("moves", vm.state.value.lossReason)
        assertEquals(vm.levelConfig.moveLimit, vm.state.value.moves)
        assertEquals(1, progress.losses)
        assertTrue(progress.records.isEmpty())

        vm.viewModelScope.cancel()
    }

    @Test
    fun shuffleHappensOnlyWhileIdle() = runTest(dispatcher) {
        val idle = newGame(level = 100)
        advanceTimeBy(idle.levelConfig.shuffleIntervalSec * 1_000L)
        runCurrent()
        assertEquals(1, idle.state.value.shuffleCount)

        val busy = newGame(level = 100)
        val firstCard = busy.state.value.cards.first { !it.isMatched }
        busy.onCardClicked(firstCard.id)
        advanceTimeBy(busy.levelConfig.shuffleIntervalSec * 1_000L)
        runCurrent()
        assertEquals(0, busy.state.value.shuffleCount)
        assertEquals(1, busy.state.value.shuffleCountdownSec)

        idle.viewModelScope.cancel()
        busy.viewModelScope.cancel()
    }

    @Test
    fun loneCardAutoHidesAfterItsWindow() = runTest(dispatcher) {
        val vm = newGame(level = 200)
        assertTrue(vm.levelConfig.autoHideMs > 0)

        val card = vm.state.value.cards.first { !it.isMatched }
        vm.onCardClicked(card.id)
        assertTrue(vm.state.value.cards.first { it.id == card.id }.isFlipped)

        advanceTimeBy(vm.levelConfig.autoHideMs.toLong())
        runCurrent()

        assertFalse(vm.state.value.cards.first { it.id == card.id }.isFlipped)
        assertEquals(null, vm.state.value.firstSelectedCardId)

        vm.viewModelScope.cancel()
    }

    @Test
    fun cardFlipsAreBatchedInMemory() = runTest(dispatcher) {
        val deps = FakeProgress()
        val vm = newGame(level = 101, progress = deps)

        assertEquals(0, deps.flips)

        val card1 = vm.state.value.cards[0]
        vm.onCardClicked(card1.id)
        // First card click batches flip in memory, no DataStore write yet
        assertEquals(0, deps.flips)

        val card2 = vm.state.value.cards[1]
        vm.onCardClicked(card2.id)
        runCurrent()
        // Second card click resolves pair and flushes flips
        assertTrue(deps.flips > 0)

        vm.viewModelScope.cancel()
    }

    @Test
    fun hintsRespectTheLimitAndCooldown() = runTest(dispatcher) {
        val vm = newGame(level = 101)
        val startHints = vm.state.value.hintsLeft
        assertTrue(startHints > 0)

        vm.useHint()
        assertEquals(startHints - 1, vm.state.value.hintsLeft)
        assertTrue(vm.state.value.isHintOnCooldown)

        vm.useHint()
        assertEquals(startHints - 1, vm.state.value.hintsLeft)

        advanceTimeBy(10_000)
        runCurrent()
        assertFalse(vm.state.value.isHintOnCooldown)

        vm.useHint()
        assertEquals(startHints - 2, vm.state.value.hintsLeft)

        vm.viewModelScope.cancel()
    }

    @Test
    fun unlimitedHintsNeverDecrement() = runTest(dispatcher) {
        val vm = newGame(level = 1)
        assertEquals(-1, vm.state.value.hintsLeft)

        vm.useHint()
        assertEquals(-1, vm.state.value.hintsLeft)
        assertTrue(vm.state.value.isHintOnCooldown)

        advanceTimeBy(10_000)
        runCurrent()
        assertFalse(vm.state.value.isHintOnCooldown)

        vm.useHint()
        assertEquals(-1, vm.state.value.hintsLeft)

        vm.viewModelScope.cancel()
    }

    @Test
    fun bossHintsCannotBeUsed() = runTest(dispatcher) {
        val vm = newGame(level = 25)
        assertEquals(0, vm.state.value.hintsLeft)
        vm.useHint()
        assertEquals(0, vm.state.value.hintsLeft)

        vm.viewModelScope.cancel()
    }

    @Test
    fun pauseStopsTheTimerAndResumeContinues() = runTest(dispatcher) {
        val vm = newGame(level = 21)
        advanceTimeBy(2_000)
        runCurrent()
        val elapsed = vm.state.value.timeElapsedSec

        vm.togglePause()
        advanceTimeBy(10_000)
        runCurrent()
        assertTrue(vm.state.value.isPaused)
        assertEquals(elapsed, vm.state.value.timeElapsedSec)

        vm.togglePause()
        advanceTimeBy(1_000)
        runCurrent()
        assertFalse(vm.state.value.isPaused)
        assertEquals(elapsed + 1, vm.state.value.timeElapsedSec)

        vm.viewModelScope.cancel()
    }

    @Test
    fun victoryIsRecordedOnceAndReplayPaysNoCoins() = runTest(dispatcher) {
        val progress = FakeProgress()
        val feedback = FakeFeedback()
        val vm = newGame(level = 1, progress = progress, feedback = feedback)

        matchAll(vm)
        advanceUntilIdle()

        assertTrue(vm.state.value.isWon)
        assertEquals(1, progress.records.size)
        assertTrue(vm.state.value.isFirstClear)
        assertEquals(15 + 5 * vm.state.value.stars, vm.state.value.coinsAwarded)
        assertEquals(1, feedback.calls.count { it.cue == GameCue.VICTORY })

        vm.startNewGame()
        finishPreview(vm)
        matchAll(vm)
        advanceUntilIdle()

        assertEquals(2, progress.records.size)
        assertFalse(vm.state.value.isFirstClear)
        assertEquals(0, vm.state.value.coinsAwarded)

        vm.viewModelScope.cancel()
    }

    @Test
    fun tapsAreIgnoredDuringPreviewAndAfterTheRunEnds() = runTest(dispatcher) {
        val vm = newGame(level = 21, finishPreview = false)
        val card = vm.state.value.cards.first()
        vm.onCardClicked(card.id)
        // Cards are already face-up for the memorise preview; the tap must not start a selection.
        assertEquals(null, vm.state.value.firstSelectedCardId)
        assertEquals(0, vm.state.value.moves)

        finishPreview(vm)
        val second = vm.state.value.cards.first()
        vm.onCardClicked(second.id)
        assertEquals(second.id, vm.state.value.firstSelectedCardId)

        vm.togglePause()
        val third = vm.state.value.cards.first { !it.isFlipped && !it.isMatched }
        vm.onCardClicked(third.id)
        assertFalse(vm.state.value.cards.first { it.id == third.id }.isFlipped)

        vm.viewModelScope.cancel()
    }

    @Test
    fun pausedRunRestoresTheSameBoardAndClock() = runTest(dispatcher) {
        val snapshots = FakeSnapshotStore()
        val clock = FakeClock()
        val first = newGame(level = 21, snapshots = snapshots, clock = clock)
        advanceTimeBy(3_000)
        runCurrent()
        val savedCards = first.state.value.cards.map { it.id to it.emojiId }
        val savedElapsed = first.state.value.timeElapsedSec
        val savedRemaining = first.state.value.timeRemainingSec

        first.togglePause()
        runCurrent()
        first.viewModelScope.cancel()

        val second = newGame(
            level = 21,
            snapshots = snapshots,
            clock = clock,
            finishPreview = false,
        )
        val restored = second.state.value
        assertTrue(restored.wasRestored)
        assertTrue(restored.restoredPaused)
        assertTrue(restored.isPaused)
        assertEquals(savedCards, restored.cards.map { it.id to it.emojiId })
        assertEquals(savedElapsed, restored.timeElapsedSec)
        assertEquals(savedRemaining, restored.timeRemainingSec)
        assertTrue(restored.cards.none { it.isFlipped })

        second.viewModelScope.cancel()
    }

    @Test
    fun unpausedRestoreGetsAGracePeriodBeforeTheClockStarts() = runTest(dispatcher) {
        val snapshots = FakeSnapshotStore()
        val clock = FakeClock()
        val first = newGame(level = 21, snapshots = snapshots, clock = clock)
        advanceTimeBy(2_000)
        runCurrent()
        val elapsed = first.state.value.timeElapsedSec
        first.saveSnapshotNow()
        runCurrent()
        first.viewModelScope.cancel()

        val second = newGame(
            level = 21,
            snapshots = snapshots,
            clock = clock,
            finishPreview = false,
        )
        assertTrue(second.state.value.wasRestored)
        assertFalse(second.state.value.isPaused)
        assertEquals(elapsed, second.state.value.timeElapsedSec)

        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(elapsed, second.state.value.timeElapsedSec)

        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(elapsed + 1, second.state.value.timeElapsedSec)

        second.viewModelScope.cancel()
    }

    @Test
    fun expiredSnapshotIsDiscardedAndANewBoardIsDealt() = runTest(dispatcher) {
        val clock = FakeClock()
        val snapshots = FakeSnapshotStore()
        val first = newGame(level = 21, snapshots = snapshots, clock = clock)
        first.togglePause()
        runCurrent()
        first.viewModelScope.cancel()

        clock.now += 24L * 60L * 60L * 1000L + 1
        val second = newGame(
            level = 21,
            snapshots = snapshots,
            clock = clock,
            finishPreview = false,
        )
        assertFalse(second.state.value.wasRestored)
        assertTrue(second.state.value.isPreviewing)
        assertEquals(null, snapshots.saved)

        second.viewModelScope.cancel()
    }

    @Test
    fun victoryClearsTheResumableRun() = runTest(dispatcher) {
        val snapshots = FakeSnapshotStore()
        val vm = newGame(level = 1, snapshots = snapshots)
        vm.saveSnapshotNow()
        runCurrent()
        assertTrue(snapshots.saved != null)

        matchAll(vm)
        advanceUntilIdle()

        assertTrue(vm.state.value.isWon)
        assertEquals(null, snapshots.saved)

        vm.viewModelScope.cancel()
    }

    @Test
    fun malformedSnapshotIsIgnored() = runTest(dispatcher) {
        val valid = newGame(level = 21, finishPreview = true)
        val good = valid.state.value
        valid.viewModelScope.cancel()

        val broken = com.vurka.emogy.data.GameSnapshot(
            level = 21,
            savedAtMillis = 1_700_000_000_000L,
            cards = good.cards.map {
                com.vurka.emogy.data.SnapshotCard(it.id, it.emojiId, it.emoji, it.isMatched)
            }.dropLast(1),
            moves = 0,
            mistakes = 0,
            matchedPairs = 0,
            timeElapsedSec = 0,
            timeRemainingSec = good.timeRemainingSec,
            hintsLeft = 5,
            combo = 0,
            maxCombo = 0,
            shuffleCountdownSec = 0,
            shuffleCount = 0,
            wasPaused = true,
        )
        val snapshots = FakeSnapshotStore(broken)
        val vm = newGame(level = 21, snapshots = snapshots, finishPreview = false)

        assertFalse(vm.state.value.wasRestored)
        assertTrue(vm.state.value.isPreviewing)
        assertEquals(null, snapshots.saved)

        vm.viewModelScope.cancel()
    }

    @Test
    fun assistRevealsOnePairWithoutUsingHintsOrMoves() = runTest(dispatcher) {
        val vm = newGame(level = 21, assist = true)
        val hints = vm.state.value.hintsLeft

        vm.useAssist()

        val ids = vm.state.value.assistCardIds
        assertEquals(2, ids.size)
        assertEquals(
            1,
            vm.state.value.cards.filter { it.id in ids }.map { it.emojiId }.distinct().size,
        )
        assertEquals("Assist must not consume a hint", hints, vm.state.value.hintsLeft)
        assertEquals("Assist must not count as a move", 0, vm.state.value.moves)
        assertEquals(AssistRules.COOLDOWN_SEC, vm.state.value.assistCooldownRemainingSec)

        advanceTimeBy(AssistRules.REVEAL_MS)
        runCurrent()

        assertTrue("the reveal ends by itself", vm.state.value.assistCardIds.isEmpty())
        assertTrue("the cooldown continues", vm.state.value.assistCooldownRemainingSec > 0)

        vm.viewModelScope.cancel()
    }

    @Test
    fun assistCooldownBlocksReuseUntilItRunsOut() = runTest(dispatcher) {
        val vm = newGame(level = 21, assist = true)
        vm.useAssist()
        advanceTimeBy(AssistRules.REVEAL_MS)
        runCurrent()

        vm.useAssist()
        assertTrue("a second tap during cooldown must do nothing", vm.state.value.assistCardIds.isEmpty())

        advanceTimeBy(AssistRules.COOLDOWN_SEC * 1_000L)
        runCurrent()
        assertEquals(0, vm.state.value.assistCooldownRemainingSec)

        vm.useAssist()
        assertEquals(2, vm.state.value.assistCardIds.size)

        vm.viewModelScope.cancel()
    }

    @Test
    fun assistWorksOnABossLevelThatHasNoHints() = runTest(dispatcher) {
        val vm = newGame(level = 25, assist = true)
        assertEquals(0, vm.state.value.hintsLeft)

        vm.useAssist()

        assertEquals(2, vm.state.value.assistCardIds.size)
        assertEquals(0, vm.state.value.hintsLeft)

        vm.viewModelScope.cancel()
    }

    @Test
    fun assistDoesNothingWhenTheModeIsOff() = runTest(dispatcher) {
        val vm = newGame(level = 21, assist = false)

        vm.useAssist()

        assertTrue(vm.state.value.assistCardIds.isEmpty())
        assertEquals(0, vm.state.value.assistCooldownRemainingSec)

        vm.viewModelScope.cancel()
    }

    @Test
    fun assistIsUnavailableDuringPreviewAndPausingCancelsTheReveal() = runTest(dispatcher) {
        val vm = newGame(level = 21, assist = true, finishPreview = false)

        vm.useAssist()
        assertTrue("no assist before the board is playable", vm.state.value.assistCardIds.isEmpty())

        finishPreview(vm)
        vm.useAssist()
        assertEquals(2, vm.state.value.assistCardIds.size)
        val cooldown = vm.state.value.assistCooldownRemainingSec

        vm.togglePause()
        assertTrue("pausing hides the revealed pair", vm.state.value.assistCardIds.isEmpty())
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals("the cooldown stops while paused", cooldown, vm.state.value.assistCooldownRemainingSec)

        vm.togglePause()
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(cooldown - 1, vm.state.value.assistCooldownRemainingSec)

        vm.viewModelScope.cancel()
    }

    @Test
    fun assistRevealedCardsCanBePlayedNormally() = runTest(dispatcher) {
        val vm = newGame(level = 21, assist = true)

        vm.useAssist()
        val ids = vm.state.value.assistCardIds.toList()
        assertEquals(2, ids.size)

        vm.onCardClicked(ids[0])
        vm.onCardClicked(ids[1])

        assertEquals(1, vm.state.value.moves)
        assertEquals(1, vm.state.value.matchedPairsCount)
        assertTrue("resolving the pair clears the reveal", vm.state.value.assistCardIds.isEmpty())

        vm.viewModelScope.cancel()
    }

    @Test
    fun matchingAPairUnlocksItsEmojiInTheAlbum() = runTest(dispatcher) {
        val progress = FakeProgress()
        val vm = newGame(level = 21, progress = progress)
        val pair = vm.state.value.cards.groupBy { it.emojiId }.entries.first()

        vm.onCardClicked(pair.value[0].id)
        vm.onCardClicked(pair.value[1].id)
        advanceUntilIdle()

        assertEquals(setOf(pair.key), progress.collectedEmojis)

        vm.viewModelScope.cancel()
    }

    @Test
    fun finalPairAndDailyRunsAlsoFillTheAlbum() = runTest(dispatcher) {
        val levelProgress = FakeProgress()
        val levelVm = newGame(level = 21, progress = levelProgress)

        matchAll(levelVm)
        advanceUntilIdle()

        assertEquals(
            "every pair on the board, including the last one, is collected",
            levelVm.state.value.cards.map { it.emojiId }.toSet(),
            levelProgress.collectedEmojis,
        )

        val dailyProgress = FakeProgress()
        val dailyVm = newGame(level = 1, daily = true, dailyEpochDay = 20_645L, progress = dailyProgress)
        matchAll(dailyVm)
        advanceUntilIdle()

        assertEquals(
            dailyVm.state.value.cards.map { it.emojiId }.toSet(),
            dailyProgress.collectedEmojis,
        )

        levelVm.viewModelScope.cancel()
        dailyVm.viewModelScope.cancel()
    }

    @Test
    fun mismatchesDoNotUnlockAnEmoji() = runTest(dispatcher) {
        val progress = FakeProgress()
        val vm = newGame(level = 21, progress = progress)

        mismatchOnce(vm)
        advanceUntilIdle()

        assertTrue(progress.collectedEmojis.isEmpty())

        vm.viewModelScope.cancel()
    }

    @Test
    fun dailyBoardIsDeterministicForTheSameDay() = runTest(dispatcher) {
        val first = newGame(level = 1, daily = true, dailyEpochDay = 20_645L, finishPreview = false)
        val second = newGame(level = 1, daily = true, dailyEpochDay = 20_645L, finishPreview = false)

        assertEquals(
            first.state.value.cards.map { it.emojiId },
            second.state.value.cards.map { it.emojiId },
        )
        assertEquals(DailyChallenge.PAIR_COUNT, second.state.value.totalPairs)
        assertEquals(DailyChallenge.MAX_HINTS, second.state.value.hintsLeft)

        first.viewModelScope.cancel()
        second.viewModelScope.cancel()
    }

    @Test
    fun dailyBoardChangesWithTheDay() = runTest(dispatcher) {
        val first = newGame(level = 1, daily = true, dailyEpochDay = 20_645L, finishPreview = false)
        val second = newGame(level = 1, daily = true, dailyEpochDay = 20_646L, finishPreview = false)

        assertNotEquals(
            first.state.value.cards.map { it.emojiId },
            second.state.value.cards.map { it.emojiId },
        )

        first.viewModelScope.cancel()
        second.viewModelScope.cancel()
    }

    @Test
    fun dailyWinPaysOnceAndLeavesLevelProgressAlone() = runTest(dispatcher) {
        val progress = FakeProgress()
        val vm = newGame(level = 1, daily = true, dailyEpochDay = 20_645L, progress = progress)

        matchAll(vm)
        advanceUntilIdle()

        assertTrue(vm.state.value.isWon)
        assertTrue(vm.state.value.isFirstClear)
        assertEquals(DailyChallenge.REWARD_COINS, vm.state.value.coinsAwarded)
        assertEquals("a daily run is not a numbered level", 0, progress.records.size)
        assertEquals("Continue must not move to level 1", 0, progress.lastPlayedLevel)
        assertEquals(1, progress.dailyRecords.size)
        assertEquals(20_645L, progress.dailyRecords.single().day)
        assertEquals(1, progress.streakUpdates)
        assertEquals(1, progress.playStats.size)

        vm.viewModelScope.cancel()
    }

    @Test
    fun dailyReplayDoesNotPayASecondTime() = runTest(dispatcher) {
        val progress = FakeProgress()
        val vm = newGame(level = 1, daily = true, dailyEpochDay = 20_645L, progress = progress)

        matchAll(vm)
        advanceUntilIdle()
        assertEquals(DailyChallenge.REWARD_COINS, vm.state.value.coinsAwarded)
        val firstDeck = vm.state.value.cards.map { it.emojiId }

        vm.startNewGame()
        runCurrent()
        finishPreview(vm)
        assertEquals("a replay must deal the same board", firstDeck, vm.state.value.cards.map { it.emojiId })
        matchAll(vm)
        advanceUntilIdle()

        assertFalse(vm.state.value.isFirstClear)
        assertEquals(0, vm.state.value.coinsAwarded)
        assertEquals(2, progress.dailyRecords.size)

        vm.viewModelScope.cancel()
    }

    @Test
    fun dailyRunNeitherReadsNorClearsALevelSnapshot() = runTest(dispatcher) {
        val suspendedLevel = com.vurka.emogy.data.GameSnapshot(
            level = 21,
            savedAtMillis = 1_700_000_000_000L,
            cards = emptyList(),
            moves = 0,
            mistakes = 0,
            matchedPairs = 0,
            timeElapsedSec = 0,
            timeRemainingSec = 60,
            hintsLeft = 3,
            combo = 0,
            maxCombo = 0,
            shuffleCountdownSec = 0,
            shuffleCount = 0,
            wasPaused = true,
        )
        val snapshots = FakeSnapshotStore(suspendedLevel)
        val vm = newGame(
            level = 1,
            daily = true,
            dailyEpochDay = 20_645L,
            snapshots = snapshots,
            finishPreview = false,
        )

        vm.saveSnapshotNow()
        runCurrent()

        assertTrue("the level run must survive a daily session", snapshots.saved === suspendedLevel)
        assertFalse("a daily run is never restored from a level snapshot", vm.state.value.wasRestored)

        vm.viewModelScope.cancel()
    }

    @Test
    fun purchasedHintTokensWorkOnABossLevelWithoutFreeHints() = runTest(dispatcher) {
        val wallet = FakeHintWallet(2)
        val vm = newGame(level = 25, hints = wallet)
        assertEquals("boss levels keep zero free hints", 0, vm.state.value.hintsLeft)
        assertEquals(2, vm.hintTokens.value)

        vm.useHint()
        runCurrent()

        assertEquals(0, vm.state.value.hintsLeft)
        assertEquals(1, vm.state.value.hintsUsed)
        assertEquals(1, wallet.current)
        assertTrue(vm.state.value.isHintOnCooldown)
        assertEquals(1, wallet.consumeAttempts)

        // A second tap during the cooldown must not spend another token.
        vm.useHint()
        runCurrent()
        assertEquals(1, wallet.current)
        assertEquals(1, vm.state.value.hintsUsed)

        advanceTimeBy(10_000)
        runCurrent()
        vm.useHint()
        runCurrent()
        assertEquals(2, vm.state.value.hintsUsed)
        assertEquals(0, wallet.current)

        advanceTimeBy(10_000)
        runCurrent()
        vm.useHint()
        runCurrent()
        assertEquals("an empty wallet cannot grant a hint", 2, vm.state.value.hintsUsed)
        assertEquals(0, wallet.current)

        vm.viewModelScope.cancel()
    }

    @Test
    fun freeHintsAreSpentBeforePurchasedTokens() = runTest(dispatcher) {
        val wallet = FakeHintWallet(3)
        val vm = newGame(level = 101, hints = wallet)
        val start = vm.state.value.hintsLeft
        assertTrue(start > 0)

        vm.useHint()
        runCurrent()

        assertEquals(start - 1, vm.state.value.hintsLeft)
        assertEquals(3, wallet.current)
        assertEquals(0, wallet.consumeAttempts)

        vm.viewModelScope.cancel()
    }

    @Test
    fun victoryReportsOneAuthoritativeGameplayEvent() = runTest(dispatcher) {
        val tracker = FakeGameplayTracker()
        val vm = newGame(level = 1, tasks = tracker)

        matchAll(vm)
        advanceUntilIdle()

        assertEquals(1, tracker.calls)
        val event = tracker.events.single()
        assertEquals(1, event.levelId)
        assertEquals(vm.state.value.totalPairs, event.pairs)
        assertEquals(vm.state.value.stars, event.stars)
        assertEquals(vm.state.value.mistakes, event.mistakes)
        assertEquals(vm.state.value.hintsUsed, event.hintsUsed)
        assertEquals(vm.state.value.coinsAwarded, event.coins)
        assertTrue(event.coins > 0)
        assertEquals(false, event.isDaily)
        assertEquals(false, event.isBoss)

        // A replay is still a completed level, but it reports zero coins.
        vm.startNewGame()
        runCurrent()
        finishPreview(vm)
        matchAll(vm)
        advanceUntilIdle()

        assertEquals(2, tracker.events.size)
        assertEquals(0, tracker.events[1].coins)

        vm.viewModelScope.cancel()
    }

    @Test
    fun dailyVictoryReportsTheDailyLevelKey() = runTest(dispatcher) {
        val tracker = FakeGameplayTracker()
        val vm = newGame(level = 1, daily = true, dailyEpochDay = 20_645L, tasks = tracker)

        matchAll(vm)
        advanceUntilIdle()

        val event = tracker.events.single()
        assertEquals(0, event.levelId)
        assertEquals(0, event.world)
        assertTrue(event.isDaily)

        vm.viewModelScope.cancel()
    }

    @Test
    fun hintsUsedSurvivePauseAndRestore() = runTest(dispatcher) {
        val snapshots = FakeSnapshotStore()
        val wallet = FakeHintWallet(1)
        val first = newGame(level = 25, hints = wallet, snapshots = snapshots)
        first.useHint()
        runCurrent()
        assertEquals(1, first.state.value.hintsUsed)

        first.togglePause()
        runCurrent()
        first.viewModelScope.cancel()

        val second = newGame(level = 25, hints = wallet, snapshots = snapshots, finishPreview = false)

        assertTrue(second.state.value.wasRestored)
        assertEquals(1, second.state.value.hintsUsed)
        assertEquals(0, second.state.value.hintsLeft)

        second.viewModelScope.cancel()
    }

    private fun TestScope.newGame(
        level: Int,
        assist: Boolean = false,
        daily: Boolean = false,
        dailyEpochDay: Long = 0L,
        progress: FakeProgress = FakeProgress(),
        feedback: FakeFeedback = FakeFeedback(),
        finishPreview: Boolean = true,
        snapshots: FakeSnapshotStore = FakeSnapshotStore(),
        clock: FakeClock = FakeClock(),
        hints: HintWallet = NoHintWallet,
        tasks: GameplayTracker = NoGameplayTracker,
    ): GameViewModel {
        val vm = GameViewModel(
            levelNumber = level,
            progress = progress,
            settings = FakeSettings(initialAssist = assist),
            feedback = feedback,
            clock = clock,
            themeIdFlow = flowOf("default"),
            snapshots = snapshots,
            isDaily = daily,
            dailyEpochDay = dailyEpochDay,
            hints = hints,
            tasks = tasks,
        )
        runCurrent()
        if (finishPreview) finishPreview(vm)
        return vm
    }

    private fun TestScope.finishPreview(vm: GameViewModel) {
        vm.manualFlipCards()
        runCurrent()
    }

    private fun TestScope.mismatchOnce(vm: GameViewModel) {
        val available = vm.state.value.cards.filter { !it.isFlipped && !it.isMatched }
        val first = available.first()
        val second = available.first { it.emojiId != first.emojiId }
        vm.onCardClicked(first.id)
        vm.onCardClicked(second.id)
        advanceTimeBy(vm.levelConfig.mismatchDelayMs.toLong() + 1)
        runCurrent()
    }

    @Test
    fun hintRevealsWrongCardsAndResetsAfter5Sec() = runTest(dispatcher) {
        val vm = newGame(level = 101)
        val available = vm.state.value.cards.filter { !it.isFlipped && !it.isMatched }
        val first = available[0]
        val secondWrong = available.first { it.emojiId != first.emojiId }
        vm.onCardClicked(first.id)
        vm.onCardClicked(secondWrong.id)
        vm.useHint()
        runCurrent()
        assertTrue(vm.state.value.cards.all { it.isFlipped || it.isMatched })

        advanceTimeBy(5000L)
        runCurrent()
        assertFalse(vm.state.value.cards.filter { !it.isMatched }.any { it.isFlipped })

        vm.viewModelScope.cancel()
    }

    private fun TestScope.matchAll(vm: GameViewModel) {
        val pairs = vm.state.value.cards.groupBy { it.emojiId }
        for ((_, cards) in pairs) {
            vm.onCardClicked(cards[0].id)
            vm.onCardClicked(cards[1].id)
        }
    }
}
