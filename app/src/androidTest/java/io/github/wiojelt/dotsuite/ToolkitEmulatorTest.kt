package io.github.wiojelt.dotsuite

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetHost
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.KeyEvent
import android.view.WindowManager
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.wiojelt.dotsuite.dock.DockPreferences
import io.github.wiojelt.dotsuite.dock.QuickDockActivity
import io.github.wiojelt.dotsuite.standby.*
import io.github.wiojelt.dotsuite.ui.PersonalizationScreen
import io.github.wiojelt.dotsuite.ui.theme.DotSuiteTheme
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Only our isolated emulator: never change real widget IDs or the user's preferences. */
@RunWith(AndroidJUnit4::class)
class ToolkitEmulatorTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    @Before fun emulatorOnly() { Assume.assumeTrue(Build.HARDWARE == "ranchu") }

    @Test fun standbyIsAnOptInScreensaverNotAReplacementLockScreen() {
        compose.setContent { DotSuiteTheme { PersonalizationScreen("standby", PaddingValues(), {}) } }
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Night clock"))
        compose.onNodeWithText("Night clock").assertExists()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Choose widget"))
        compose.onAllNodesWithText("Choose widget").onFirst().assertExists()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Set charging screensaver"))
        compose.onNodeWithText("Set charging screensaver").assertExists()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("not low-power AOD", substring = true))
        compose.onNodeWithText("not low-power AOD", substring = true).assertExists()
    }

    @Test fun widgetPickerUsesSystemUiAndCancellingKeepsThePreviousSlot() {
        val host = AppWidgetHost(context, StandbyPreferences.HOST_ID)
        val before = host.appWidgetIds.toSet()
        val slotBefore = StandbyPreferences.widget(context, 0)
        compose.setContent { DotSuiteTheme { PersonalizationScreen("standby", PaddingValues(), {}) } }
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Choose widget"))
        compose.onAllNodesWithText("Choose widget").onFirst().performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("System picker").assertIsDisplayed()
        assertEquals(before, host.appWidgetIds.toSet())
        compose.onNodeWithText("System picker").performClick()
        compose.waitUntil(5_000) {
            instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString() == "com.android.settings"
        }
        assertEquals(1, (host.appWidgetIds.toSet() - before).size)
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitUntil(5_000) { host.appWidgetIds.toSet() == before }
        assertEquals(slotBefore, StandbyPreferences.widget(context, 0))
    }

    @Test fun widgetCatalogueCancellationAndRecreationDoNotAllocateOrReplaceWidgets() {
        val host = AppWidgetHost(context, StandbyPreferences.HOST_ID)
        val ids = host.appWidgetIds.toSet()
        val previous = StandbyPreferences.widget(context, 0)
        ActivityScenario.launch<WidgetPickerActivity>(Intent(context, WidgetPickerActivity::class.java)).use { scene ->
            scene.recreate()
            assertEquals(ids, host.appWidgetIds.toSet())
        }
        assertEquals(ids, host.appWidgetIds.toSet())
        assertEquals(previous, StandbyPreferences.widget(context, 0))
    }

    @Test fun clockFacesRenderInBothOrientationsWithoutBackgroundUpdates() {
        val old = StandbyPreferences.clock(context)
        try {
            for (style in 0..2) {
                StandbyPreferences.setClock(context, style)
                instrumentation.runOnMainSync {
                    val clock = StandbyClockView(context)
                    for ((w, h) in listOf(1000 to 420, 420 to 600)) {
                        clock.layout(0, 0, w, h)
                        val bitmap = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
                        clock.draw(android.graphics.Canvas(bitmap))
                        assertFalse(clock.contentDescription.isNullOrEmpty())
                        assertTrue((0 until w step 10).any { x -> (0 until h step 10).any { y ->
                            android.graphics.Color.alpha(bitmap.getPixel(x, y)) > 0
                        } })
                        bitmap.recycle()
                    }
                }
            }
        } finally { StandbyPreferences.setClock(context, old) }
    }

    @Test fun standbyPreviewReleasesItsKeepAwakeFlagWhenHidden() {
        ActivityScenario.launch<StandbyPreviewActivity>(Intent(context, StandbyPreviewActivity::class.java)).use { scene ->
            scene.onActivity {
                assertTrue(it.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0)
                assertTrue((it.findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0) as StandbyView).updatesActive)
                assertEquals(.15f, it.window.attributes.screenBrightness)
                val clocks = arrayListOf<android.view.View>()
                it.findViewById<android.view.View>(android.R.id.content).findViewsWithText(
                    clocks, "StandBy clock", android.view.View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION)
                assertEquals(1, clocks.size)
            }
            scene.moveToState(Lifecycle.State.CREATED)
            scene.onActivity {
                assertEquals(0, it.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                assertFalse((it.findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0) as StandbyView).updatesActive)
            }
        }
    }

    @Test fun quickDockCanBeConfiguredWithoutOverlayOrEdgeGesturePermission() {
        compose.setContent { DotSuiteTheme { PersonalizationScreen("dock", PaddingValues(), {}) } }
        compose.onNodeWithText("Choose apps", substring = true).performClick()
        compose.onNodeWithText("Quick dock apps").assertExists()
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithText("Open quick dock").assertExists()
    }

    @Test fun quickDockActuallyOpensOnTheSelectedSide() {
        val oldLeft = DockPreferences.prefs(context).getBoolean("left", false)
        val oldStyle = DockPreferences.style(context)
        try {
            DockPreferences.style(context, io.github.wiojelt.dotsuite.data.DockStyle())
            for (left in listOf(true, false)) {
                DockPreferences.prefs(context).edit().putBoolean("left", left).commit()
                ActivityScenario.launch<QuickDockActivity>(Intent(context, QuickDockActivity::class.java)).use { scene ->
                    scene.onActivity {
                        assertEquals(if (left) android.view.Gravity.LEFT else android.view.Gravity.RIGHT,
                            it.window.attributes.gravity and android.view.Gravity.HORIZONTAL_GRAVITY_MASK)
                        assertTrue(it.window.attributes.width <= 90 * it.resources.displayMetrics.density)
                        assertTrue(it.window.attributes.height > 0)
                        assertTrue(it.window.attributes.width > 0)
                    }
                }
            }
        } finally {
            DockPreferences.prefs(context).edit().putBoolean("left", oldLeft).apply()
            DockPreferences.style(context, oldStyle)
        }
    }

    @Test fun powerPageDoesNotEnableUnsupportedNativeHooksOnAnEmulator() {
        compose.setContent { DotSuiteTheme { PersonalizationScreen("power", PaddingValues(), {}) } }
        compose.onAllNodes(isToggleable()).assertCountEquals(1)
        compose.onNode(isToggleable()).assertIsNotEnabled()
    }
    @Test fun dockAppearancePersistsFitsInsetsAndResetKeepsAppsAndSide() {
        val old = DockPreferences.style(context)
        val oldApps = DockPreferences.packages(context)
        val oldLeft = DockPreferences.prefs(context).getBoolean("left", false)
        try {
            DockPreferences.save(context, listOf("com.android.settings"))
            DockPreferences.setLeft(context, true)
            for (position in listOf(0, 50, 100)) {
                val style = io.github.wiojelt.dotsuite.data.DockStyle(140, position, 35, 24, 3, 0,
                    io.github.wiojelt.dotsuite.data.DockMotion.NONE)
                DockPreferences.style(context, style)
                assertEquals(style, DockPreferences.style(context))
                ActivityScenario.launch<QuickDockActivity>(Intent(context, QuickDockActivity::class.java)).use { scene ->
                    instrumentation.waitForIdleSync()
                    scene.onActivity {
                        val metrics = it.windowManager.currentWindowMetrics
                        val inset = metrics.windowInsets.getInsetsIgnoringVisibility(android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.displayCutout())
                        val lp = it.window.attributes
                        assertTrue(lp.x >= 0 && lp.y >= 0)
                        assertTrue(lp.width + lp.x <= metrics.bounds.width() - inset.left - inset.right)
                        assertTrue(lp.height + lp.y <= metrics.bounds.height() - inset.top - inset.bottom)
                        assertEquals(0f, lp.dimAmount)
                    }
                }
            }
            DockPreferences.style(context, io.github.wiojelt.dotsuite.data.DockStyle())
            assertEquals(listOf("com.android.settings"), DockPreferences.packages(context))
            assertTrue(DockPreferences.prefs(context).getBoolean("left", false))
        } finally {
            DockPreferences.style(context, old)
            DockPreferences.save(context, oldApps)
            DockPreferences.setLeft(context, oldLeft)
        }
    }

    @Test fun noMediaTimerOrExactAlarmPermissionIsInstalled() {
        val receivers = context.packageManager.getPackageInfo(context.packageName,
            android.content.pm.PackageManager.GET_RECEIVERS).receivers.orEmpty()
        assertFalse(receivers.any { it.name.contains("SleepTimer") })
        val permissions = context.packageManager.getPackageInfo(context.packageName,
            android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
        assertFalse("android.permission.SCHEDULE_EXACT_ALARM" in permissions)
    }
}
