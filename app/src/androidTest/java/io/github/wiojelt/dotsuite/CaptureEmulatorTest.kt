package io.github.wiojelt.dotsuite

import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.wiojelt.dotsuite.capture.CapturePreferences
import io.github.wiojelt.dotsuite.capture.CaptureService
import io.github.wiojelt.dotsuite.capture.CaptureShortcutActivity
import io.github.wiojelt.dotsuite.data.PersonalizationPolicy as P
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.FileInputStream

/** Never capture a real person's surroundings during an automated test. Emulated camera only. */
@RunWith(AndroidJUnit4::class)
class CaptureEmulatorTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Before fun prepare() {
        Assume.assumeTrue("Capture tests run on the isolated emulator only", Build.HARDWARE == "ranchu")
        listOf("CAMERA", "POST_NOTIFICATIONS").forEach { permission ->
            instrumentation.uiAutomation.executeShellCommand("pm grant ${context.packageName} android.permission.$permission").use {
                FileInputStream(it.fileDescriptor).readBytes()
            }
        }
        CapturePreferences.setEnabled(context, true)
        CapturePreferences.setAudio(context, false)
    }

    @After fun cleanup() {
        if (Build.HARDWARE != "ranchu") return
        if (CaptureService.state.value.busy) {
            context.startService(Intent(context, CaptureService::class.java).setAction(CaptureService.STOP))
            await { !CaptureService.state.value.busy }
        }
        CapturePreferences.setEnabled(context, false)
    }

    @Test fun frontPhotoSavesWithoutPreview() = photo(P.PHOTO_FRONT)
    @Test fun rearPhotoSavesWithoutPreview() = photo(P.PHOTO_REAR)

    private fun photo(action: Int) {
        val intent = Intent(context, CaptureShortcutActivity::class.java).putExtra("capture_action", action)
        ActivityScenario.launch<CaptureShortcutActivity>(intent).use {
            await { CaptureService.state.value.phase in setOf("saved", "error") }
            assertEquals(CaptureService.state.value.message, "saved", CaptureService.state.value.phase)
            assertTrue(CaptureService.state.value.message.startsWith("Photo saved"))
        }
    }

    @Test fun videoContinuesAfterLaunchSheetClosesAndNotificationStopsIt() = video(P.VIDEO_REAR)
    @Test fun frontVideoAlsoStopsAndSaves() = video(P.VIDEO_FRONT)

    @Test fun disabledShortcutNeverStartsTheCamera() {
        CapturePreferences.setEnabled(context, false)
        ActivityScenario.launch<CaptureShortcutActivity>(
            Intent(context, CaptureShortcutActivity::class.java).putExtra("capture_action", P.VIDEO_REAR)).use {
            instrumentation.waitForIdleSync()
            assertFalse(CaptureService.state.value.busy)
            assertFalse(context.getSystemService(NotificationManager::class.java).activeNotifications.any { it.id == 4101 })
        }
    }

    private fun video(action: Int) {
        val intent = Intent(context, CaptureShortcutActivity::class.java).putExtra("capture_action", action)
        ActivityScenario.launch<CaptureShortcutActivity>(intent).use {
            await { CaptureService.state.value.phase in setOf("recording", "error") }
            assertEquals(CaptureService.state.value.message, "recording", CaptureService.state.value.phase)
            Thread.sleep(1_500) // give encoder frames before stop; no host audio is used
            assertEquals("recording", CaptureService.state.value.phase)
            val manager = context.getSystemService(NotificationManager::class.java)
            val notice = manager.activeNotifications.firstOrNull { it.id == 4101 }
            assertNotNull("Visible Stop notification is required", notice)
            notice!!.notification.actions.first().actionIntent.send()
            await { !CaptureService.state.value.busy }
            assertEquals(CaptureService.state.value.message, "saved", CaptureService.state.value.phase)
        }
    }

    private fun await(predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 25_000
        while (!predicate() && System.currentTimeMillis() < deadline) Thread.sleep(100)
        assertTrue("Timed out: ${CaptureService.state.value}", predicate())
    }
}
