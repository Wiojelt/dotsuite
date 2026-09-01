package io.github.wiojelt.dotsuite

import android.content.Intent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import io.github.wiojelt.dotsuite.data.*
import io.github.wiojelt.dotsuite.diagnostics.*
import io.github.wiojelt.dotsuite.ui.*
import io.github.wiojelt.dotsuite.ui.theme.*
import org.junit.*
import org.junit.Assert.*

class DiagnosticsUiTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @Before fun silentDefaults() { AppearancePrefs.save(context, AppearanceOptions(reduceMotion = true)) }
    @Test fun reportRequiresAnExplicitClickAndLogsCanBeExcluded() {
        var sends = 0
        var include by mutableStateOf(true)
        compose.setContent { DotSuiteTheme {
            BugReportContent(PaddingValues(), BugReport.snapshot("test event", include), include, false, "",
                {}, { include = it }, {}, { sends++ }, {})
        } }
        compose.runOnIdle { assertEquals(0, sends) }
        compose.onNodeWithText("Include last 60 seconds").performClick()
        compose.onNodeWithTag("report-preview").assertTextContains("Logs excluded by the user.", substring = true)
        compose.onNodeWithTag("report-email").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, sends) }
    }
    @Test fun attachmentMatchesFrozenPreviewAndGrantsOnlyReadAccess() {
        val report = BugReport.snapshot("One reviewed event", true)
        val uri = BugReport.attachment(context, report)
        assertEquals("content", uri.scheme)
        assertEquals("${context.packageName}.reports", uri.authority)
        assertEquals(report, context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() })
        val intent = BugReport.sendIntent(context, uri)
        assertEquals(Intent.ACTION_SEND, intent.action)
        assertArrayEquals(arrayOf(BugReport.EMAIL), intent.getStringArrayExtra(Intent.EXTRA_EMAIL))
        assertEquals(uri, intent.clipData!!.getItemAt(0).uri)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(0, intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        val provider = context.packageManager.resolveContentProvider("${context.packageName}.reports", 0)!!
        assertFalse(provider.exported)
        assertTrue(provider.grantUriPermissions)
    }
    @Test fun reportCacheIsBounded() {
        repeat(12) { BugReport.attachment(context, "review $it") }
        val files = java.io.File(context.cacheDir, "reports").listFiles().orEmpty()
        assertTrue(files.size <= 8)
    }
    @Test fun originalSoundAssetsHaveShortSoftEndpointsWithoutPlayingAudio() {
        for (id in listOf(R.raw.dot_tap, R.raw.dot_open, R.raw.dot_back)) {
            val bytes = context.resources.openRawResource(id).use { it.readBytes() }
            val pcm = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            assertEquals(1, pcm.getShort(22).toInt())
            assertEquals(24000, pcm.getInt(24))
            assertTrue(bytes.size < 4000)
            assertEquals(0, pcm.getShort(44).toInt())
            assertEquals(0, pcm.getShort(bytes.size - 2).toInt())
            var max = 0
            for (i in 44 until bytes.size step 2) max = maxOf(max, kotlin.math.abs(pcm.getShort(i).toInt()))
            assertTrue(max in 1..4200)
        }
    }
    @Test fun allBackdropsRenderAndDisappearWhenHomeIsHidden() {
        var options by mutableStateOf(AppearanceOptions(reduceMotion = true, backdrop = HomeBackdrop.MATRIX))
        var visible by mutableStateOf(true)
        compose.setContent { DotSuiteTheme {
            CompositionLocalProvider(LocalAppearance provides options, LocalMotionAllowed provides false) {
                HomeBackdropEffect(visible)
            }
        } }
        for (mode in listOf(HomeBackdrop.MATRIX, HomeBackdrop.SNOW, HomeBackdrop.MAZE)) {
            compose.runOnIdle { options = options.copy(backdrop = mode) }
            compose.onNodeWithTag("backdrop-${mode.name}").assertExists()
        }
        compose.runOnIdle { visible = false }
        compose.onNodeWithTag("backdrop-MAZE").assertDoesNotExist()
    }
    @Test fun backdropChoiceAndSoundOptInPersistIndependently() {
        AppearancePrefs.save(context, AppearanceOptions(backdrop = HomeBackdrop.SNOW, reduceMotion = true))
        compose.setContent { DotSuiteTheme { AppearanceScreen(PaddingValues(), {}) } }
        compose.onNodeWithText("Maze").performClick()
        compose.runOnIdle {
            assertEquals(HomeBackdrop.MAZE, AppearancePrefs.read(context).backdrop)
            assertFalse(AppearancePrefs.read(context).touchSounds)
        }
    }
}
