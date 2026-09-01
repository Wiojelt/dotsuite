package io.github.wiojelt.dotsuite

import android.content.Context
import android.provider.Settings
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.wiojelt.dotsuite.data.*
import io.github.wiojelt.dotsuite.ui.MainScreen
import io.github.wiojelt.dotsuite.ui.theme.DotSuiteTheme
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class OrbitUiTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @Before fun prepare() {
        AppearancePrefs.save(context, AppearanceOptions())
        context.getSharedPreferences("appearance", Context.MODE_PRIVATE).edit()
            .putBoolean("feature_list", false).putBoolean("show_root_features", false).commit()
    }
    @After fun cleanup() { AppearancePrefs.save(context, AppearanceOptions()) }
    private fun open(roulette: Boolean = false, reduce: Boolean = false) {
        AppearancePrefs.save(context, AppearanceOptions(roulette = roulette, reduceMotion = reduce))
        compose.setContent { DotSuiteTheme { MainScreen(MixPrefs(context), onRunSetup = {}) } }
        compose.waitForIdle()
    }
    private fun screenshot(name: String) {
        // Test-only app-owned pixels, not the notification shade or private content.
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(context.getExternalFilesDir(null), "$name.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }
    @Test fun ordinaryFlickMovesDotsButDoesNotOpenOrEnableFeatures() {
        open()
        val before = compose.onNodeWithTag("category-SOUND").fetchSemanticsNode().boundsInRoot.center
        compose.onNodeWithTag("category-honeycomb").performTouchInput {
            swipe(Offset(width * .2f, height * .25f), Offset(width * .8f, height * .25f), 250)
        }
        compose.mainClock.advanceTimeBy(4000)
        val after = compose.onNodeWithTag("category-SOUND").fetchSemanticsNode().boundsInRoot.center
        assertTrue((after - before).getDistance() > 5f)
        compose.onNodeWithText("‹  DotSuite").assertDoesNotExist()
    }
    @Test fun rouletteCanBeCancelledWithoutOpeningAnything() {
        open(roulette = true)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("spin-roulette").performClick()
        compose.mainClock.advanceTimeBy(160)
        compose.onNodeWithTag("cancel-roulette").assertExists()
        screenshot("dev4-roulette-moving")
        compose.onNodeWithTag("cancel-roulette").performClick()
        compose.mainClock.advanceTimeBy(5000)
        compose.onNodeWithTag("category-honeycomb").assertExists()
        compose.onNodeWithText("‹  DotSuite").assertDoesNotExist()
        compose.mainClock.autoAdvance = true
    }
    @Test fun centerSearchStopsRouletteAndDoesNotMoveWithTheRing() {
        open(roulette = true)
        val center = compose.onNodeWithTag("hub-search-dot").fetchSemanticsNode().boundsInRoot.center
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("spin-roulette").performClick()
        compose.mainClock.advanceTimeBy(180)
        assertEquals(center, compose.onNodeWithTag("hub-search-dot").fetchSemanticsNode().boundsInRoot.center)
        compose.onNodeWithTag("hub-search-dot").performClick()
        compose.mainClock.advanceTimeBy(5000)
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("hub-search-close").performClick()
        compose.onNodeWithTag("category-honeycomb").assertExists()
        compose.onNodeWithText("‹  DotSuite").assertDoesNotExist()
    }
    @Test fun reducedMotionRouletteOnlyNavigatesAndLeavesSystemOptionsUntouched() {
        val keys = listOf(PersonalizationPolicy.NOTCH_ENABLED, PersonalizationPolicy.POWER_TORCH, PersonalizationPolicy.HIDE_NAV_PILL)
        val before = keys.associateWith { Settings.Secure.getString(context.contentResolver, it) }
        open(roulette = true, reduce = true)
        compose.onNodeWithText("Pick a category").performClick()
        compose.onNodeWithText("‹  DotSuite").assertIsDisplayed()
        compose.onNodeWithTag("category-honeycomb").assertDoesNotExist()
        assertEquals(before, keys.associateWith { Settings.Secure.getString(context.contentResolver, it) })
    }
    @Test fun normalRouletteLandsAndOpensExactlyOneCategory() {
        open(roulette = true)
        compose.onNodeWithTag("spin-roulette").performClick()
        compose.mainClock.advanceTimeBy(5000)
        compose.onAllNodesWithText("‹  DotSuite").assertCountEquals(1)
        compose.onNodeWithTag("category-honeycomb").assertDoesNotExist()
        compose.onNodeWithText("‹  DotSuite").performClick()
        compose.onNodeWithTag("category-honeycomb").assertExists()
    }
    @Test fun portalOpensFromDotAndReversesBackToHub() {
        open()
        screenshot("dev4-hub")
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("category-INTERFACE").performClick()
        compose.mainClock.advanceTimeBy(160)
        screenshot("dev4-portal-opening")
        compose.mainClock.advanceTimeBy(600)
        screenshot("dev4-display")
        compose.onNodeWithText("‹  DotSuite").performClick()
        compose.mainClock.advanceTimeBy(160)
        screenshot("dev4-portal-closing")
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithTag("category-honeycomb").assertExists()
        compose.mainClock.autoAdvance = true
    }
    @Test fun appearanceSettingsAreLocalAndRootIsNotRequired() {
        open()
        compose.onNodeWithTag("category-SETTINGS").performClick()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Appearance & play"))
        compose.onNodeWithText("Appearance & play").performClick()
        compose.onNodeWithText("Russian roulette").performClick()
        assertTrue(AppearancePrefs.read(context).roulette)
        compose.onNodeWithText("Translucent theme").performClick()
        assertFalse(AppearancePrefs.read(context).translucent)
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Restore appearance defaults"))
        compose.onNodeWithText("Restore appearance defaults").performClick()
        assertEquals(AppearanceOptions(), AppearancePrefs.read(context))
    }
}
