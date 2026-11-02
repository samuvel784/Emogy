package com.vurka.emogy

import com.vurka.emogy.ui.navigation.Screen
import com.vurka.emogy.ui.navigation.TopLevelDestinations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the split between the five tab roots and every secondary screen. A route that is missing
 * here either loses its bottom bar or shows one on a board, so this is verified without Compose.
 */
class TopLevelDestinationsTest {

    @Test
    fun exactlyFiveRootsKeepTheBottomBar() {
        assertEquals(5, TopLevelDestinations.tabs.size)
        assertEquals(
            setOf("home", "world_map", "daily", "album", "more"),
            TopLevelDestinations.routes,
        )
    }

    @Test
    fun boardAndSecondaryRoutesHideTheBar() {
        listOf(
            Screen.Game.route,
            Screen.DailyGame.route,
            Screen.Settings.route,
            Screen.Stats.route,
            Screen.Tasks.route,
            Screen.Themes.route,
            Screen.Store.route,
            Screen.Legal.route,
            null,
        ).forEach { route ->
            assertFalse("$route should not show the bar", TopLevelDestinations.showsBottomBar(route))
        }
    }

    @Test
    fun everyTabRouteShowsTheBar() {
        TopLevelDestinations.tabs.forEach { tab ->
            assertTrue(tab.route, TopLevelDestinations.showsBottomBar(tab.route))
            assertEquals(tab, TopLevelDestinations.tabFor(tab.route))
        }
    }

    @Test
    fun routesAndTestTagsAreUnique() {
        assertEquals(
            TopLevelDestinations.tabs.size,
            TopLevelDestinations.tabs.map { it.testTag }.toSet().size,
        )
        TopLevelDestinations.tabs.forEach { tab ->
            assertTrue("${tab.route} is missing from routes", tab.route in TopLevelDestinations.routes)
            assertTrue("${tab.testTag} needs a label", tab.labelRes != 0)
        }
        assertNull(TopLevelDestinations.tabFor("game/1"))
    }
}
