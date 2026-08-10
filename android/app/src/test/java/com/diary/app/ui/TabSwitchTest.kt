package com.diary.app.ui

import androidx.lifecycle.Lifecycle
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class TabSwitchTest {

    @get:Rule
    val composeRule = createComposeRule()

    private enum class Tab(val route: String) {
        DIARY("diary"), CALENDAR("calendar"), MINE("mine")
    }

    /**
     * The exact selectTab logic from DiaryApp: synchronous stack-top check,
     * popUpTo the start destination (saving state), restoreState on return.
     */
    private fun selectTabLogic(
        nav: NavHostController,
        tab: Tab,
    ) {
        val topRoute = nav.currentBackStackEntry?.destination?.route
        if (topRoute == tab.route) return
        nav.navigate(tab.route) {
            popUpTo(nav.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    private fun topOf(nav: NavHostController): String? =
        nav.currentBackStackEntry?.destination?.route

    // The NavBackStackEntry id is unique per entry: if the old buggy code
    // popped the current tab and re-pushed it, this id would change even
    // though the route stays the same.
    private fun topEntryId(nav: NavHostController): String? =
        nav.currentBackStackEntry?.id

    private fun hostController(): NavHostController {
        lateinit var nav: NavHostController
        composeRule.setContent {
            nav = rememberNavController()
            NavHost(nav, startDestination = Tab.DIARY.route) {
                Tab.entries.forEach { t ->
                    composable(t.route) { Text(t.route) }
                }
            }
        }
        composeRule.waitForIdle()
        return nav
    }

    @Test
    fun clickingCurrentTab_doesNotNavigate() {
        val nav = hostController()

        // Switch to the calendar tab.
        composeRule.runOnIdle { selectTabLogic(nav, Tab.CALENDAR) }
        composeRule.waitForIdle()
        assertEquals("calendar", topOf(nav))
        val id = topEntryId(nav)

        // Tapping the already-current tab must not re-navigate: the stack
        // top entry must stay the same instance (old code popped it and
        // re-pushed a new entry, replaying the transition animation).
        composeRule.runOnIdle { selectTabLogic(nav, Tab.CALENDAR) }
        composeRule.waitForIdle()
        assertEquals("calendar", topOf(nav))
        assertEquals(id, topEntryId(nav))
    }

    @Test
    fun clickingAnimatingTab_immediatelyAfterSwitch_doesNotNavigate() {
        val nav = hostController()

        // Switch to calendar, then IMMEDIATELY tap calendar again, before
        // the transition has had any chance to recompose (the composable
        // currentRoute state lags; the synchronous check must still catch it).
        var idAfterFirst: String? = null
        composeRule.runOnIdle {
            selectTabLogic(nav, Tab.CALENDAR)
            idAfterFirst = topEntryId(nav)
        }
        composeRule.runOnIdle { selectTabLogic(nav, Tab.CALENDAR) }
        composeRule.waitForIdle()
        assertEquals("calendar", topOf(nav))
        assertEquals(idAfterFirst, topEntryId(nav))
    }

    @Test
    fun switchingToAnotherTab_navigates() {
        val nav = hostController()

        composeRule.runOnIdle { selectTabLogic(nav, Tab.CALENDAR) }
        composeRule.waitForIdle()
        composeRule.runOnIdle { selectTabLogic(nav, Tab.MINE) }
        composeRule.waitForIdle()
        assertEquals("mine", topOf(nav))
    }

    /**
     * Regression: after a tab round trip (diary -> calendar -> diary), the
     * diary NavBackStackEntry must be RESUMED again. With the old
     * popUpTo-only navigation it stayed CREATED forever, which froze
     * collectAsStateWithLifecycle on the page: the list never recomposed,
     * so a deleted card stayed visible until the next page switch.
     */
    @Test
    fun returningToStartTab_resumesLifecycle() {
        val nav = hostController()

        composeRule.runOnIdle { selectTabLogic(nav, Tab.CALENDAR) }
        composeRule.waitForIdle()
        composeRule.runOnIdle { selectTabLogic(nav, Tab.DIARY) }
        composeRule.waitForIdle()

        assertEquals("diary", topOf(nav))
        // The lifecycle transition is async; give it a moment.
        composeRule.waitUntil(timeoutMillis = 5_000) {
            nav.currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED
        }
    }
}
