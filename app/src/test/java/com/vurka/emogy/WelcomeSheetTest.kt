package com.vurka.emogy

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vurka.emogy.ui.components.WelcomeSheet
import com.vurka.emogy.ui.theme.MemoryMatchTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-port-xhdpi")
@LooperMode(LooperMode.Mode.PAUSED)
class WelcomeSheetTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun showSheet(onDismiss: () -> Unit = {}, onHowToPlay: () -> Unit = {}) {
        compose.setContent {
            MemoryMatchTheme(themeMode = "Light") {
                WelcomeSheet(onDismiss = onDismiss, onHowToPlay = onHowToPlay)
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun introductionExplainsTheLoopAndOffersBothExits() {
        showSheet()

        compose.onNodeWithText("👋 Welcome to Emogy").assertIsDisplayed()
        compose.onNodeWithText("Find every pair").assertIsDisplayed()
        compose.onNodeWithText("Beat your own best").assertIsDisplayed()
        compose.onNodeWithText("Come back for more").assertExists()
    }

    @Test
    fun startPlayingDismisses() {
        var dismissed = 0
        showSheet(onDismiss = { dismissed++ })

        compose.onNodeWithTag("welcome_start_button").performScrollTo().performClick()

        assertEquals(1, dismissed)
    }

    @Test
    fun readTheRulesOpensTheRulesInstead() {
        var dismissed = 0
        var rulesOpened = 0
        showSheet(onDismiss = { dismissed++ }, onHowToPlay = { rulesOpened++ })

        compose.onNodeWithTag("welcome_rules_button").performScrollTo().performClick()

        assertEquals(1, rulesOpened)
        assertEquals(0, dismissed)
    }

    @Test
    fun closeButtonDismisses() {
        var dismissed = 0
        showSheet(onDismiss = { dismissed++ })

        compose.onNodeWithTag("welcome_close_button").performClick()

        assertEquals(1, dismissed)
    }
}
