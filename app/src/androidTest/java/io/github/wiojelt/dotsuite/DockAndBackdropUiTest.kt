package io.github.wiojelt.dotsuite

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.github.wiojelt.dotsuite.data.*
import io.github.wiojelt.dotsuite.dock.DockRail
import io.github.wiojelt.dotsuite.ui.MainScreen
import io.github.wiojelt.dotsuite.ui.theme.*
import org.junit.*
import org.junit.Assert.*
import java.io.File

class DockAndBackdropUiTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @Before fun silentStatic() {
        AppearancePrefs.save(context, AppearanceOptions(reduceMotion = true))
        AppearancePrefs.prefs(context).edit().putBoolean("feature_list", false).commit()
    }
    @Test fun emptyDockProvidesAnExplicitSetupActionAndCanClose() {
        var edits = 0
        var closes = 0
        var labels by mutableStateOf(false)
        compose.setContent { DotSuiteTheme {
            DockRail(emptyList(), false, labels, false, true, Modifier.width(232.dp).height(350.dp),
                { closes++ }, { labels = !labels }, { edits++ }, {})
        } }
        compose.onNodeWithContentDescription("Choose dock apps").performClick()
        compose.runOnIdle { assertEquals(1, edits) }
        compose.onNodeWithContentDescription("Show app labels").performClick()
        compose.onNodeWithContentDescription("Close dock").performClick()
        compose.runOnIdle { assertEquals(1, closes) }
    }
    @Test fun dockCanLaunchMultipleDifferentShortcutsAndExpandLabels() {
        val icon = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        val apps = listOf(Triple("settings", "Settings", icon), Triple("files", "Files", icon))
        var labels by mutableStateOf(false)
        val launched = mutableListOf<String>()
        compose.setContent { DotSuiteTheme {
            DockRail(apps, true, labels, false, true, Modifier.width(if (labels) 232.dp else 88.dp).height(350.dp),
                {}, { labels = !labels }, {}, { launched += it })
        } }
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithContentDescription("Files").performClick()
        compose.runOnIdle { assertEquals(listOf("settings", "files"), launched) }
        compose.onNodeWithContentDescription("Show app labels").performClick()
        compose.onNodeWithText("Edit shortcuts").assertExists()
        compose.onNodeWithText("Files").assertExists()
    }
    @Test fun captureActualHomeForEveryBackdropAndHideItInsideACategory() {
        var options by mutableStateOf(AppearanceOptions(reduceMotion = true))
        compose.setContent { DotSuiteTheme {
            CompositionLocalProvider(LocalAppearance provides options, LocalMotionAllowed provides false) {
                MainScreen(MixPrefs(context), onRunSetup = {})
            }
        } }
        for (mode in HomeBackdrop.entries) {
            compose.runOnIdle { options = options.copy(backdrop = mode) }
            val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
            File(context.getExternalFilesDir(null), "dev6-home-${mode.name}.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        compose.onNodeWithTag("category-SUPPORT").performClick()
        compose.onNodeWithTag("backdrop-MAZE").assertDoesNotExist()
    }
}
