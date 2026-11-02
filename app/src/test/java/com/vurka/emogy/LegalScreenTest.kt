package com.vurka.emogy

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vurka.emogy.ui.legal.LegalScreen
import com.vurka.emogy.ui.theme.MemoryMatchTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/** About & Legal: the list, the version line and the three offline documents. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-port-xhdpi")
@LooperMode(LooperMode.Mode.PAUSED)
class LegalScreenTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun show(onBack: () -> Unit = {}) {
        compose.setContent {
            MemoryMatchTheme(themeMode = "Light") { LegalScreen(onBackPressed = onBack) }
        }
        compose.waitForIdle()
    }

    private fun waitForDocument() {
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithTag("legal_document").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun listShowsVersionAndAllEntries() {
        show()

        compose.onNodeWithTag("legal_screen").assertIsDisplayed()
        compose.onNodeWithTag("legal_version").assertTextContains(BuildConfig.VERSION_NAME, substring = true)
        compose.onNodeWithTag("legal_privacy").assertIsDisplayed()
        compose.onNodeWithTag("legal_terms").assertIsDisplayed()
        compose.onNodeWithTag("legal_credits").assertIsDisplayed()
    }

    @Test
    fun privacyPolicyOpensOfflineAndBackReturnsToTheList() {
        show()

        compose.onNodeWithTag("legal_privacy").performClick()
        waitForDocument()

        compose.onNodeWithTag("legal_document").assertIsDisplayed()
        assertTrue(compose.onAllNodesWithText("Summary").fetchSemanticsNodes().isNotEmpty())

        compose.onNodeWithTag("back_button").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("legal_screen").assertIsDisplayed()
    }

    @Test
    fun termsAndCreditsOpen() {
        show()

        compose.onNodeWithTag("legal_terms").performClick()
        waitForDocument()
        compose.onNodeWithTag("back_button").performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("legal_credits").performClick()
        waitForDocument()
        compose.onNodeWithTag("legal_document").assertIsDisplayed()
    }

    @Test
    fun backFromTheListLeavesTheScreen() {
        var left = 0
        show(onBack = { left++ })

        compose.onNodeWithTag("back_button").performClick()

        assertEquals(1, left)
    }
}
