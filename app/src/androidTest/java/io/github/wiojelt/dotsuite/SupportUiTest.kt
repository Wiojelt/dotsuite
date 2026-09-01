package io.github.wiojelt.dotsuite

import android.content.Context
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import io.github.wiojelt.dotsuite.data.*
import io.github.wiojelt.dotsuite.ui.*
import io.github.wiojelt.dotsuite.ui.theme.DotSuiteTheme
import io.github.wiojelt.dotsuite.ui.theme.LocalMotionAllowed
import org.junit.*
import org.junit.Assert.*
import java.io.File

class SupportUiTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @Before fun reset() {
        AppearancePrefs.save(context, AppearanceOptions())
        context.getSharedPreferences("appearance", Context.MODE_PRIVATE).edit().putBoolean("feature_list", false).commit()
    }
    @Test fun coffeeDotOpensLocalPageAndMaybeLaterReturnsToSameHub() {
        compose.setContent { DotSuiteTheme { MainScreen(MixPrefs(context), onRunSetup = {}) } }
        saveFrame("dotsuite-home.png")
        compose.onNodeWithTag("category-SUPPORT").assertIsDisplayed().performClick()
        compose.onNodeWithTag("support-page").assertExists()
        compose.onNodeWithText("Optional. No features are locked.").assertExists()
        compose.onNodeWithText("Show support QR").assertDoesNotExist()
        compose.onNodeWithContentDescription("Buy Me a Coffee QR for wiojelt").assertDoesNotExist()
        saveFrame("dotsuite-coffee.png")
        compose.onNodeWithTag("coffee-maybe-later").performScrollTo().performClick()
        compose.onNodeWithTag("category-SUPPORT").assertIsDisplayed()
    }
    @Test fun supportNeedsExplicitTapAndNeverOpensBrowserOnEntryOrBack() {
        var visits = 0
        var backs = 0
        compose.setContent { DotSuiteTheme {
            SupportScreen(PaddingValues(), onBack = { backs++ }, onOpenSupport = { visits++; true })
        } }
        compose.runOnIdle { assertEquals(0, visits) }
        compose.onNodeWithTag("coffee-external-link").performClick()
        compose.runOnIdle { assertEquals(1, visits) }
        compose.onNodeWithTag("coffee-maybe-later").performClick()
        compose.runOnIdle { assertEquals(1, visits); assertEquals(1, backs) }
    }
    @Test fun coffeeIsSearchableWithoutRootAndMissingBrowserFailsPolitely() {
        assertEquals("support", FeatureCatalog.search("kahve").single().page)
        assertEquals(FeatureAccess.ON_DEVICE, FeatureCatalog.search("kahve").single().access)
        compose.setContent { DotSuiteTheme { SupportScreen(PaddingValues(), {}, onOpenSupport = { false }) } }
        compose.onNodeWithTag("coffee-external-link").performClick()
        compose.onNodeWithText("No browser available.", substring = true).assertExists()
    }
    @Test fun supportDeepLinkNeverRoutesToDiagnosticsOrRequiresPermissions() {
        compose.setContent { DotSuiteTheme { MainScreen(MixPrefs(context), initialFeature = "support", onRunSetup = {}) } }
        compose.onNodeWithTag("support-page").assertExists()
        compose.onNodeWithText("Local diagnostics").assertDoesNotExist()
        compose.onNodeWithText("‹  DotSuite").performClick()
        compose.onNodeWithTag("category-SUPPORT").assertExists()
    }

    @Test fun largeTextAndReducedMotionKeepSupportOptional() {
        var visits = 0
        compose.setContent { DotSuiteTheme {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f),
                LocalMotionAllowed provides false) {
                SupportScreen(PaddingValues(), {}, onOpenSupport = { visits++; true })
            }
        } }
        compose.onNodeWithTag("coffee-external-link").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithTag("coffee-maybe-later").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, visits) }
    }

    private fun saveFrame(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(context.getExternalFilesDir(null), name).outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
