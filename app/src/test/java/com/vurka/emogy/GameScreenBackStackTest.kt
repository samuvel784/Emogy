package com.vurka.emogy

import androidx.activity.ComponentActivity
import android.os.Looper
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vurka.emogy.ui.game.GameScreen
import com.vurka.emogy.ui.theme.LocalReduceMotion
import com.vurka.emogy.ui.theme.MemoryMatchTheme
import com.vurka.emogy.viewmodel.FakeClock
import com.vurka.emogy.viewmodel.FakeFeedback
import com.vurka.emogy.viewmodel.FakeProgress
import com.vurka.emogy.viewmodel.FakeSettings
import com.vurka.emogy.viewmodel.FakeSnapshotStore
import com.vurka.emogy.viewmodel.GameViewModel
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-port-xhdpi")
@LooperMode(LooperMode.Mode.PAUSED)
class GameScreenBackStackTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun systemBackPausesAndASecondBackResumes() {
        val viewModel = newViewModel(level = 21)
        compose.setContent {
            CompositionLocalProvider(LocalReduceMotion provides true) {
                MemoryMatchTheme(themeMode = "Light") {
                    GameScreen(
                        viewModel = viewModel,
                        onBackPressed = {},
                        onNextLevel = {},
                        onWorldMap = {},
                    ) {}
                }
            }
        }
        compose.waitForIdle()

        compose.onNodeWithTag("pause_button").assertIsDisplayed()
        compose.onNodeWithText("Game Paused").assertDoesNotExist()

        compose.runOnUiThread {
            compose.activity.onBackPressedDispatcher.onBackPressed()
        }
        compose.waitForIdle()

        compose.onNodeWithText("Game Paused").assertIsDisplayed()
        compose.onNodeWithText("Resume").assertIsDisplayed()

        // A paused dialog must not trap the player: the next Back resumes instead of exiting.
        compose.runOnUiThread {
            compose.activity.onBackPressedDispatcher.onBackPressed()
        }
        compose.waitForIdle()

        compose.onNodeWithText("Game Paused").assertDoesNotExist()
        assertTrue(!compose.activity.isFinishing)
    }

    @Test
    fun cardsStaySquareAndInsideTheScreenAtTwoHundredPercentFont() {
        val viewModel = newViewModel(level = 1) // 8 cards, 3 columns
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalReduceMotion provides true,
                LocalDensity provides Density(base.density, fontScale = 2f),
            ) {
                MemoryMatchTheme(themeMode = "Light") {
                    GameScreen(
                        viewModel = viewModel,
                        onBackPressed = {},
                        onNextLevel = {},
                        onWorldMap = {},
                    ) {}
                }
            }
        }
        compose.waitForIdle()

        val firstCardTag = "card_${viewModel.state.value.cards.first().id}"
        val (left, top, right, bottom) = compose.onNodeWithTag(firstCardTag).getUnclippedBoundsInRoot()
        val width = right.value - left.value
        val height = bottom.value - top.value
        assertEquals(
            "cards must stay square",
            width,
            height,
            1f,
        )
        assertTrue("card must not be wider than the 360 dp screen", width <= 360f)
        assertTrue("card must remain tappable", width >= 48f)
    }

    @Test
    fun assistHeartIsHiddenWhenTheModeIsOff() {
        val viewModel = newViewModel(level = 21, assist = false)
        compose.setContent {
            CompositionLocalProvider(LocalReduceMotion provides true) {
                MemoryMatchTheme(themeMode = "Light") {
                    GameScreen(
                        viewModel = viewModel,
                        onBackPressed = {},
                        onNextLevel = {},
                        onWorldMap = {},
                    ) {}
                }
            }
        }
        compose.waitForIdle()

        compose.onNodeWithTag("hint_button").assertIsDisplayed()
        compose.onNodeWithTag("assist_button").assertDoesNotExist()
    }

    @Test
    fun assistHeartIsShownWhenTheModeIsOn() {
        val viewModel = newViewModel(level = 21, assist = true)
        compose.setContent {
            CompositionLocalProvider(LocalReduceMotion provides true) {
                MemoryMatchTheme(themeMode = "Light") {
                    GameScreen(
                        viewModel = viewModel,
                        onBackPressed = {},
                        onNextLevel = {},
                        onWorldMap = {},
                    ) {}
                }
            }
        }
        compose.waitForIdle()

        compose.onNodeWithTag("assist_button").assertIsDisplayed()
    }

    @Test
    fun dailyRunShowsTheDailyTitleInsteadOfALevelNumber() {
        val viewModel = newViewModel(level = 1, daily = true)
        compose.setContent {
            CompositionLocalProvider(LocalReduceMotion provides true) {
                MemoryMatchTheme(themeMode = "Light") {
                    GameScreen(
                        viewModel = viewModel,
                        onBackPressed = {},
                        onNextLevel = {},
                        onWorldMap = {},
                    ) {}
                }
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText("📅 Daily Challenge").assertIsDisplayed()
        compose.onNodeWithText("Level 1").assertDoesNotExist()
    }

    private fun newViewModel(level: Int, assist: Boolean = false, daily: Boolean = false): GameViewModel {
        val viewModel = GameViewModel(
            levelNumber = level,
            progress = FakeProgress(),
            settings = FakeSettings(initialAssist = assist),
            feedback = FakeFeedback(),
            clock = FakeClock(),
            themeIdFlow = flowOf("default"),
            snapshots = FakeSnapshotStore(),
            isDaily = daily,
            dailyEpochDay = 20_645L,
        )
        repeat(3) { shadowOf(Looper.getMainLooper()).idle() }
        return viewModel
    }
}
