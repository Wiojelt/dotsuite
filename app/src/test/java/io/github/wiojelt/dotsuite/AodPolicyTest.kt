package io.github.wiojelt.dotsuite

import io.github.wiojelt.dotsuite.data.AodPolicy as A
import io.github.wiojelt.dotsuite.data.AodSnapshot
import io.github.wiojelt.dotsuite.data.PersonalizationPolicy as P
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalTime

class AodPolicyTest {
    @Test fun onlyNativeControlsAreWritable() {
        for (key in A.KEYS) {
            assertEquals("secure", P.namespace(key))
            assertTrue(P.acceptsString(key, null))
            assertTrue(P.acceptsString(key, "0"))
            assertTrue(P.acceptsString(key, "1"))
            for (bad in listOf("-1", "3", "true", "1; reboot", "NaN", "", " 1"))
                assertFalse("$key / $bad", P.acceptsString(key, bad))
        }
        assertTrue(P.acceptsString(A.MODE, "2"))
        assertFalse(P.acceptsString(A.ENABLED, "2"))
        for (key in listOf("nt_aod_start_time", "nt_aod_end_time", "always_on_display_constants",
                "low_power", "debug.doze.aod", "doze_screen_brightness", "adb_enabled"))
            assertNull(P.namespace(key))
    }
    @Test fun nativeDefaultsAreNotMistakenForOff() {
        assertTrue(A.enabled(null, true))
        assertFalse(A.enabled(null, false))
        assertFalse(A.enabled("0", true))
        assertTrue(A.enabled("1", false))
    }
    @Test fun unavailableModesAreNotForced() {
        assertFalse(A.canUseMode(0, false, true, true))
        assertTrue(A.canUseMode(1, false, true, false))
        assertTrue(A.canUseMode(2, false, false, true))
        assertFalse(A.canUseMode(3, true, true, true))
    }
    @Test fun nativeWindowHandlesMidnightAndExactEdges() {
        assertTrue(A.insideWindow(1380, 1380, 420))
        assertTrue(A.insideWindow(0, 1380, 420))
        assertFalse(A.insideWindow(420, 1380, 420))
        assertTrue(A.insideWindow(420, 420, 1380))
        assertFalse(A.insideWindow(1380, 420, 1380))
        assertTrue(A.insideWindow(1439, 420, 420))
        assertEquals(1439, A.minute("2359"))
        for (bad in listOf("2400", "1260", "7:00", "", "700")) assertNull(A.minute(bad))
        assertNull(A.minute(null))
    }
    @Test fun statusNeverPromisesToBypassSystemSuppression() {
        val on = AodSnapshot(available = true, nothing = true, defaultEnabled = true, defaultMode = 1,
            start = "0700", end = "2300")
        assertTrue(on.copy(powerSaver = true).status().contains("does not override"))
        assertTrue(on.copy(inverted = true).status().contains("suppress"))
        assertTrue(on.status(LocalTime.of(23, 0)).startsWith("Outside"))
        assertTrue(on.status(LocalTime.of(7, 0)).startsWith("Inside"))
        assertTrue(on.copy(start = null).status().contains("managed"))
        assertTrue(on.copy(values = mapOf(A.ENABLED to "0")).status().contains("off"))
    }
}
