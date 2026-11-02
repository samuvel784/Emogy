package com.vurka.emogy

import androidx.activity.ComponentActivity
import android.os.Looper
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
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
@Config(sdk = [36], qualifiers = "w640dp-h360dp-land-xhdpi")
@LooperMode(LooperMode.Mode.PAUSED)
class GameScreenLandscapeTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun fortyCardBoardUsesSixColumnsAndFitsTheLandscapeScreen() {
        val viewModel = GameViewModel(
            levelNumber = 1000, // 20 pairs, the largest deck
            progress = FakeProgress(),
            settings = FakeSettings(),
            feedback = FakeFeedback(),
            clock = FakeClock(),
            themeIdFlow = flowOf("default"),
            snapshots = FakeSnapshotStore(),
        )
        // The board is dealt on viewModelScope (the Android main looper); flush the chained
        // coroutines before asserting.
        repeat(3) { shadowOf(Looper.getMainLooper()).idle() }
        assertTrue(
            "board should be dealt before composition",
            viewModel.state.value.cards.isNotEmpty(),
        )
        compose.setContent {
            CompositionLocalProvider(LocalReduceMotion provides true) {
                MemoryMatchTheme(themeMode = "Light") {
                    GameScreen(
                        viewModel = viewModel,
                        onBackPressed = {},
                        onNextLevel = {},
                        onWorldMap = {},
                        onHome = {},
                    )
                }
            }
        }
        compose.waitForIdle()
        repeat(3) { shadowOf(Looper.getMainLooper()).idle() }
        compose.waitForIdle()
        val firstCardTag = "card_${viewModel.state.value.cards.first().id}"
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag(firstCardTag).fetchSemanticsNodes().isNotEmpty()
        }

        val root = compose.onRoot().getUnclippedBoundsInRoot()
        assertTrue("landscape root should be wider than tall", (root.right - root.left) > (root.bottom - root.top))

        compose.onNodeWithTag(firstCardTag).assertExists()
        val bounds = compose.onNodeWithTag(firstCardTag).getUnclippedBoundsInRoot()
        val width = bounds.right.value - bounds.left.value
        val height = bounds.bottom.value - bounds.top.value

        assertEquals("cards must stay square", width, height, 1f)
        // Six columns on a 640 dp screen give ~95 dp cards. Three columns would be ~190 dp.
        assertTrue("expected a six-column board, got $width dp cards", width in 80f..110f)
        assertTrue("card must not extend past the right edge", bounds.right.value <= root.right.value + 1f)
    }
}
