package io.github.wiojelt.dotsuite

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.wiojelt.dotsuite.ui.ActionPickerRow
import io.github.wiojelt.dotsuite.ui.PersonalizationScreen
import io.github.wiojelt.dotsuite.ui.SettingsScreen
import io.github.wiojelt.dotsuite.ui.MainScreen
import io.github.wiojelt.dotsuite.data.MixPrefs
import io.github.wiojelt.dotsuite.ui.theme.DotSuiteTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PersonalizationUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun actionPickerCommitsExactlyOneActionAndDismisses() {
        var selected = -1
        compose.setContent { DotSuiteTheme { ActionPickerRow("Double tap", 0, true) { selected = it } } }
        compose.onNodeWithText("Double tap").performClick()
        compose.onNodeWithText("Screenshot").performClick()
        compose.runOnIdle { assertEquals(1, selected) }
        compose.onNodeWithText("Cancel").assertDoesNotExist()
    }

    @Test fun actionPickerCancelDoesNotChangeSelection() {
        var selected = -1
        compose.setContent { DotSuiteTheme { ActionPickerRow("Single tap", 0, true) { selected = it } } }
        compose.onNodeWithText("Single tap").performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertEquals(-1, selected) }
    }

    @Test fun mapsPageDoesNotPretendToBePixelPowerSaving() {
        compose.setContent { DotSuiteTheme { PersonalizationScreen("maps", PaddingValues(), {}) } }
        compose.onNodeWithText("Open minimal mode now").assertIsNotEnabled()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("This is not Pixel", substring = true))
        compose.onNodeWithText("This is not Pixel", substring = true).assertExists()
    }

    @Test fun motionPageExplainsRestoreAndUnsupportedStyles() {
        compose.setContent { DotSuiteTheme { PersonalizationScreen("motion", PaddingValues(), {}) } }
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Restore my original motion settings"))
        compose.onNodeWithText("Restore my original motion settings").assertIsNotEnabled()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("CRT, pixel-dissolve", substring = true))
        compose.onNodeWithText("CRT, pixel-dissolve", substring = true).assertExists()
    }

    @Test fun searchOpensAFeatureAndKeepsTheQueryOnBack() {
        compose.setContent { DotSuiteTheme { SettingsScreen(PaddingValues(), {}) } }
        compose.onNodeWithText("Find a feature").performTextInput("motion")
        compose.onNodeWithText("Motion").performScrollTo().performClick()
        compose.onNodeWithText("Windows").assertExists()
        compose.onNodeWithText("‹  Settings").performClick()
        compose.onNodeWithText("motion").assertExists()
        compose.onNodeWithText("Clear").performClick()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Restore changes"))
        compose.onNodeWithText("Restore changes").assertExists()
    }

    @Test fun dotHubReturnsFromNestedPageToItsCategory() {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        compose.setContent { DotSuiteTheme { MainScreen(MixPrefs(context), initialFeature = "motion", onRunSetup = {}) } }
        compose.onNodeWithText("Windows").assertExists()
        compose.onNodeWithText("‹  Display").performClick()
        compose.onNodeWithText("‹  DotSuite").performClick()
        compose.onNodeWithTag("category-SOUND").performClick()
        compose.onNodeWithText("Hearing").assertExists()
        compose.onNodeWithText("‹  DotSuite").performClick()
        compose.onNodeWithTag("category-INTERFACE").performClick()
        compose.onNodeWithText("Motion").performScrollTo().performClick()
        compose.onNodeWithText("Windows").assertExists()
    }

    @Test fun recoveryNeverStartsPrivilegedWritesWithoutConnection() {
        compose.setContent { DotSuiteTheme { PersonalizationScreen("recovery", PaddingValues(), {}) } }
        compose.onNodeWithText("Restore saved settings…").assertIsNotEnabled()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Disable gestures & share filtering"))
        compose.onNodeWithText("Disable gestures & share filtering").assertIsNotEnabled()
    }
    @Test fun everyNewFeatureIsReachableThroughTheActualSettingsRouter() {
        val page = mutableStateOf("home")
        val request = mutableIntStateOf(0)
        compose.setContent { DotSuiteTheme {
            SettingsScreen(PaddingValues(), {}, initialPage = page.value, navigationRequest = request.intValue)
        } }
        val destinations = listOf(
            "standby" to "Night clock",
            "dock" to "Open quick dock",
            "power" to "Screen-off long press for flashlight",
            "share" to "Hide suggested contacts",
            "maps" to "Open minimal mode now",
            "clock" to "Show seconds",
            "clock-style" to "Show weekday",
            "navigation" to "Hide gesture line",
            "rotation" to "Auto rotate",
        )
        destinations.forEach { (destination, control) ->
            compose.runOnIdle { page.value = destination; request.intValue++ }
            compose.onNodeWithText(control).assertExists()
            compose.onNodeWithText("‹  Settings").performClick()
            compose.onNodeWithText("Find a feature").assertExists()
        }
    }
}
