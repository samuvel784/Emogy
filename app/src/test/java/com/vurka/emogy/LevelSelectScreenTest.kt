package com.vurka.emogy

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vurka.emogy.ui.levels.LevelSelectScreen
import com.vurka.emogy.ui.theme.LocalReduceMotion
import com.vurka.emogy.ui.theme.MemoryMatchTheme
import com.vurka.emogy.viewmodel.LevelSelectionViewModel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-port-xhdpi")
@LooperMode(LooperMode.Mode.PAUSED)
class LevelSelectScreenTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun flush() {
        repeat(3) { shadowOf(Looper.getMainLooper()).idle() }
    }

    @Test
    fun level1IsOpenAndLevel2IsLockedUntilLevel1IsCompleted() {
        val app = RuntimeEnvironment.getApplication()
        val viewModel = LevelSelectionViewModel(app)
        var selectedLevel = 0

        compose.setContent {
            MemoryMatchTheme(themeMode = "Light") {
                CompositionLocalProvider(LocalReduceMotion provides true) {
                    LevelSelectScreen(
                        viewModel = viewModel,
                        onLevelSelected = { selectedLevel = it }
                    )
                }
            }
        }
        flush()

        // Level 1: open and selectable
        compose.onNodeWithTag("level_item_1").performClick()
        flush()
        compose.onNodeWithText("Level 1").assertIsDisplayed()
        compose.onNodeWithTag("play_level_button").performClick()
        flush()
        assertEquals(1, selectedLevel)

        // Level 2: locked initially
        compose.onNodeWithTag("level_item_2").performClick()
        flush()
        compose.onNodeWithText("Level 2").assertDoesNotExist()
    }
}
