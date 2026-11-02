package com.vurka.emogy

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vurka.emogy.ui.navigation.TopLevelDestinations
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/**
 * Launches the real activity so the scaffold, navigation graph and DataStore wiring are exercised
 * together. No device is required, and the tab roots are checked against actual screen tags.
 *
 * The welcome sheet is dismissed when present so a fresh or returning store both work; the sheet's
 * own behaviour is covered by [WelcomeSheetTest].
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-port-xhdpi")
@LooperMode(LooperMode.Mode.PAUSED)
class MainActivityNavigationTest {

    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: TestRule = RuleChain
        .outerRule(ClearProgressRule())
        .around(compose)

    @Test
    fun tabsNavigateAndSecondaryScreensHideTheBar() {
        waitForHome()

        compose.onNodeWithTag("nav_home").assertIsDisplayed()

        compose.onNodeWithTag("nav_daily").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("daily_screen").assertIsDisplayed()
        compose.onNodeWithTag("nav_daily").assertIsSelected()

        compose.onNodeWithTag("nav_album").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("album_screen").assertIsDisplayed()

        compose.onNodeWithTag("nav_map").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("nav_map").assertIsSelected()

        compose.onNodeWithTag("nav_more").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("more_screen").assertIsDisplayed()

        // A secondary screen keeps its own back arrow and gives the bar back to the content.
        compose.onNodeWithTag("more_screen").performScrollToNode(hasTestTag("more_settings"))
        compose.onNodeWithTag("more_settings").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("nav_more").assertDoesNotExist()
        compose.onNodeWithTag("back_button").assertIsDisplayed()
    }

    @Test
    fun dailyTabShowsMissionsAndKeepsItsBottomBar() {
        waitForHome()

        compose.onNodeWithTag("nav_daily").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("daily_screen").assertIsDisplayed()

        // The board card stays first; the progression hub continues below it in the same scroll.
        compose.onNodeWithTag("daily_list").performScrollToNode(hasTestTag("weekly_login_card"))
        compose.onNodeWithTag("weekly_login_card").assertIsDisplayed()

        compose.onNodeWithTag("daily_list").performScrollToNode(hasTestTag("streak_card"))
        compose.onNodeWithTag("streak_card").assertIsDisplayed()

        compose.onNodeWithTag("daily_list").performScrollToNode(hasTestTag("section_daily"))
        compose.onNodeWithTag("section_daily").assertIsDisplayed()

        compose.onNodeWithTag("daily_list").performScrollToNode(hasTestTag("section_weekly"))
        compose.onNodeWithTag("section_weekly").assertIsDisplayed()

        compose.onNodeWithTag("daily_list").performScrollToNode(hasTestTag("section_monthly"))
        compose.onNodeWithTag("section_monthly").assertIsDisplayed()

        // Embedding the missions must not cost the Daily tab its navigation.
        compose.onNodeWithTag("nav_daily").assertIsDisplayed()
        compose.onNodeWithTag("nav_daily").assertIsSelected()
        compose.onNodeWithTag("nav_home").assertIsDisplayed()
    }

    @Test
    @Config(sdk = [36], qualifiers = "w320dp-h480dp-port-mdpi")
    fun smallScreenKeepsEveryTabReachable() {
        waitForHome()

        TopLevelDestinations.tabs.forEach { tab ->
            compose.onNodeWithTag(tab.testTag).assertIsDisplayed()
        }

        compose.onNodeWithTag("nav_more").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("more_screen").performScrollToNode(hasTestTag("more_how_to_play"))
        compose.onNodeWithTag("more_how_to_play").assertIsDisplayed()
    }

    /** Waits for the real Home screen, then clears the one-time welcome if this store was fresh. */
    private fun waitForHome() {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("play_button").fetchSemanticsNodes().isNotEmpty()
        }
        repeat(40) {
            val closeIsUp = compose.onAllNodesWithTag("welcome_close_button")
                .fetchSemanticsNodes()
                .isNotEmpty()
            if (closeIsUp) {
                compose.onNodeWithTag("welcome_close_button").performClick()
                compose.waitForIdle()
                return
            }
            compose.waitForIdle()
            Thread.sleep(50)
        }
    }

    /** DataStore caches its instance per process, so removing the files is best-effort isolation. */
    private class ClearProgressRule : ExternalResource() {
        override fun before() {
            val context = ApplicationProvider.getApplicationContext<Context>()
            File(context.filesDir, "datastore").deleteRecursively()
        }
    }
}
