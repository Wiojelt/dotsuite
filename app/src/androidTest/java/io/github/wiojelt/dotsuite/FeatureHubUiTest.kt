package io.github.wiojelt.dotsuite

import android.content.Context
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.wiojelt.dotsuite.data.FeatureArea
import io.github.wiojelt.dotsuite.data.MixPrefs
import io.github.wiojelt.dotsuite.ui.FeatureHubScreen
import io.github.wiojelt.dotsuite.ui.MainScreen
import io.github.wiojelt.dotsuite.ui.RootFeaturesSetting
import io.github.wiojelt.dotsuite.ui.theme.DotSuiteTheme
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FeatureHubUiTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @Before fun resetNavigationPreference() {
        io.github.wiojelt.dotsuite.data.AppearancePrefs.save(context, io.github.wiojelt.dotsuite.data.AppearanceOptions())
        context.getSharedPreferences("appearance", Context.MODE_PRIVATE).edit()
            .putBoolean("feature_list", false).putBoolean("show_root_features", false).commit()
    }
    private fun openApp() {
        compose.setContent { DotSuiteTheme { MainScreen(MixPrefs(context), onRunSetup = {}) } }
    }

    @Test fun allEightDotsAreLabeledClickableAndDoNotOverlap() {
        openApp()
        val nodes = FeatureArea.entries.map { area ->
            compose.onNodeWithTag("category-${area.name}").assertHasClickAction().assertIsDisplayed()
                .fetchSemanticsNode().boundsInRoot
        } + compose.onNodeWithTag("hub-search-dot").assertHasClickAction().assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        nodes.forEachIndexed { i, rect ->
            assertTrue(rect.width >= 48f && rect.height >= 48f)
            nodes.drop(i + 1).forEach { other -> assertFalse("Overlapping category targets", rect.overlaps(other)) }
        }
        FeatureArea.entries.forEach { area ->
            compose.onNodeWithTag("category-${area.name}").performClick()
            compose.onNodeWithText("‹  DotSuite").assertIsDisplayed().performClick()
            compose.onNodeWithTag("category-honeycomb").assertExists()
        }
    }

    @Test fun globalSearchKeepsQueryAfterRoundTrip() {
        openApp()
        compose.onNodeWithTag("hub-search").assertDoesNotExist()
        compose.onNodeWithTag("hub-search-dot").performClick()
        compose.onNodeWithTag("hub-search").performTextInput("animasyon")
        compose.onNodeWithText("Motion").performClick()
        compose.onNodeWithText("Windows").assertExists()
        compose.onNodeWithText("‹  Display").performClick()
        compose.onNodeWithText("‹  DotSuite").performClick()
        compose.onNodeWithText("animasyon").assertExists()
        compose.onNodeWithText("Clear").performClick()
        compose.onNodeWithTag("hub-search-close").performClick()
        compose.onNodeWithTag("category-honeycomb").assertExists()
    }

    @Test fun dotFieldIsCenteredInItsRemainingViewportAfterListSwitch() {
        openApp()
        compose.onNodeWithTag("hub-view-toggle").performClick()
        compose.onNodeWithTag("hub-view-toggle").performClick()
        val viewport = compose.onNodeWithTag("dot-viewport").fetchSemanticsNode().boundsInRoot
        val field = compose.onNodeWithTag("category-honeycomb").fetchSemanticsNode().boundsInRoot
        // Orbit footer is below the circular field; its center is slightly above the container center.
        assertTrue(field.center.y <= viewport.center.y)
        assertTrue(viewport.center.y - field.center.y < 90f)
        assertTrue(kotlin.math.abs(field.center.x - viewport.center.x) < 2f)
    }

    @Test fun rootVisibilityCanBeOptedIntoWithoutEnablingAnyFeature() {
        val keys = listOf("dotsuite_systemui_enabled", "dotsuite_power_torch", "dotsuite_notch_enabled")
        val before = keys.associateWith { android.provider.Settings.Secure.getString(context.contentResolver, it) }
        openApp()
        compose.onNodeWithTag("category-SOUND").performClick()
        compose.onNodeWithText("Volume panel").assertDoesNotExist()
        compose.onNodeWithText("App volume & mixing").assertExists()
        compose.onNodeWithText("‹  DotSuite").performClick()
        compose.onNodeWithTag("category-SETTINGS").performClick()
        compose.onNodeWithText("Show root features").performScrollTo().performClick()
        compose.onNodeWithText("Show root features").assertIsOn()
        compose.onNodeWithText("‹  DotSuite").performScrollTo().performClick()
        compose.onNodeWithTag("category-SOUND").performClick()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Volume panel"))
        compose.onNodeWithText("Volume panel").performScrollTo().performClick()
        compose.onNodeWithText("Native app sliders").assertExists()
        compose.onNodeWithText("Root + module").assertExists()
        assertEquals(before, keys.associateWith { android.provider.Settings.Secure.getString(context.contentResolver, it) })
    }

    @Test fun listModeIsARealAlternativeAndIsRemembered() {
        openApp()
        compose.onNodeWithTag("hub-view-toggle").performClick()
        compose.onNodeWithTag("category-honeycomb").assertDoesNotExist()
        compose.onNodeWithTag("category-CAMERA").performScrollTo().performClick()
        compose.onNodeWithText("Camera shortcuts").assertExists()
        compose.onNodeWithText("‹  DotSuite").performClick()
        assertTrue(context.getSharedPreferences("appearance", Context.MODE_PRIVATE).getBoolean("feature_list", false))
        compose.onNodeWithTag("category-honeycomb").assertDoesNotExist()
    }

    @Test fun largeTextUsesScrollableRowsInsteadOfClippedDots() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                DotSuiteTheme { FeatureHubScreen(PaddingValues(), {}, {}) }
            }
        }
        compose.onNodeWithTag("category-honeycomb").assertDoesNotExist()
        compose.onNodeWithTag("feature-hub").performScrollToNode(hasTestTag("category-SETTINGS"))
        compose.onNodeWithTag("category-SETTINGS").assertIsDisplayed()
        compose.onNodeWithText("Access, restore & app information").assertExists()
    }
}
