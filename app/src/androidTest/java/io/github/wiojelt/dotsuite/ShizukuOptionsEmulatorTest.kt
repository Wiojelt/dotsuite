package io.github.wiojelt.dotsuite

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.wiojelt.dotsuite.data.PersonalizationPolicy as P
import io.github.wiojelt.dotsuite.data.SystemOptions
import io.github.wiojelt.dotsuite.privileged.PrivilegedManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Explicit -e shizukuTests true is required. Never changes a physical phone's settings. */
@RunWith(AndroidJUnit4::class)
class ShizukuOptionsEmulatorTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private var retained = false
    @Before fun connect() = runBlocking {
        Assume.assumeTrue(Build.HARDWARE == "ranchu" && InstrumentationRegistry.getArguments().getString("shizukuTests") == "true")
        instrumentation.runOnMainSync { PrivilegedManager.retainClient(context); retained = true }
        withTimeout(20_000) { PrivilegedManager.setup.first { it.ready } }
        Unit
    }
    @After fun disconnect() { if (retained) instrumentation.runOnMainSync { PrivilegedManager.releaseClient() } }

    @Test fun animationSnapshotAndRestorePreservesAbsentOrExactOriginal() = runBlocking {
        val key = P.WINDOW_SCALE
        val before = PrivilegedManager.readSystemOption(key)
        assertTrue(before.toString(), before.available)
        try {
            assertEquals("Saved", SystemOptions.write(context, key, "0.75"))
            assertEquals("0.75", PrivilegedManager.readSystemOption(key).value)
            assertTrue(SystemOptions.restore(context, listOf(key)).startsWith("1 restored"))
            assertEquals(before, PrivilegedManager.readSystemOption(key))
        } finally { PrivilegedManager.setSystemOption(key, before.value) }
    }

    @Test fun restoreDoesNotOverwriteASettingChangedElsewhere() = runBlocking {
        val key = P.ANIMATOR_SCALE
        val before = PrivilegedManager.readSystemOption(key)
        assertTrue(before.toString(), before.available)
        try {
            assertEquals("Saved", SystemOptions.write(context, key, "0.75"))
            assertEquals("OK", PrivilegedManager.setSystemOption(key, "1.25"))
            assertTrue(SystemOptions.restore(context, listOf(key)).startsWith("0 restored"))
            assertEquals("1.25", PrivilegedManager.readSystemOption(key).value)
            // Put our last-owned value back so the ledger can restore without an external conflict.
            assertEquals("OK", PrivilegedManager.setSystemOption(key, "0.75"))
            assertTrue(SystemOptions.restore(context, listOf(key)).startsWith("1 restored"))
        } finally { PrivilegedManager.setSystemOption(key, before.value) }
    }

    @Test fun carrierLiteralUppercaseNullIsNotConfusedWithMissingRow() = runBlocking {
        val key = P.CARRIER_LABEL
        val before = PrivilegedManager.readSystemOption(key)
        assertTrue(before.toString(), before.available)
        try {
            assertEquals("Invalid value", SystemOptions.write(context, key, "null"))
            assertEquals("Saved", SystemOptions.write(context, key, "NULL"))
            assertEquals("NULL", PrivilegedManager.readSystemOption(key).value)
            assertTrue(SystemOptions.restore(context, listOf(key)).startsWith("1 restored"))
            assertEquals(before, PrivilegedManager.readSystemOption(key))
        } finally { PrivilegedManager.setSystemOption(key, before.value) }
    }

    @Test fun allowListRejectsUsbAndMasterVolumeChanges() = runBlocking {
        listOf("adb_enabled", "volume_master", "sys.usb.config").forEach {
            assertFalse(PrivilegedManager.readSystemOption(it).available)
            assertTrue(PrivilegedManager.setSystemOption(it, "1").startsWith("ERROR"))
        }
    }

    @Test fun nativeGestureSettingsRestoreAndUnsupportedReloadIsRejected() = runBlocking {
        for ((key, wanted) in listOf(P.NOTCH_TAP to "15", P.NOTCH_HAPTICS to "0")) {
            val before = PrivilegedManager.readSystemOption(key)
            assertTrue(before.available)
            try {
                assertEquals("Saved", SystemOptions.write(context, key, wanted))
                assertEquals(wanted, PrivilegedManager.readSystemOption(key).value)
                assertTrue(SystemOptions.restore(context, listOf(key)).startsWith("1 restored"))
                assertEquals(before, PrivilegedManager.readSystemOption(key))
            } finally { PrivilegedManager.setSystemOption(key, before.value) }
        }
        assertTrue(PrivilegedManager.reloadSystemUi().startsWith("ERROR"))
    }

    @Test fun aodCapabilityGatesAndNativeMasterRoundTrip() = runBlocking {
        val caps = PrivilegedManager.aodCapabilities()
        assertNotNull(caps)
        val key = io.github.wiojelt.dotsuite.data.AodPolicy.ENABLED
        val before = PrivilegedManager.readSystemOption(key)
        if (caps!!.optBoolean("available")) {
            assertTrue(before.toString(), before.available)
            try {
                assertEquals("Saved", SystemOptions.write(context, key, "1"))
                assertEquals("1", PrivilegedManager.readSystemOption(key).value)
                assertTrue(SystemOptions.restore(context, listOf(key)).startsWith("1 restored"))
                assertEquals(before, PrivilegedManager.readSystemOption(key))
            } finally { PrivilegedManager.setSystemOption(key, before.value) }
        } else {
            assertFalse(before.available)
            assertTrue(PrivilegedManager.setSystemOption(key, "1").startsWith("ERROR"))
        }
        // No Nothing mode writes on the AOSP emulator, and never alter power/burn-in policy.
        assertTrue(PrivilegedManager.setSystemOption("aod_display_mode", "0").startsWith("ERROR"))
        for (protected in listOf("nt_aod_start_time", "always_on_display_constants", "low_power"))
            assertTrue(PrivilegedManager.setSystemOption(protected, "1").startsWith("ERROR"))
    }

    @Test fun nativeDisplayPreferencesRoundTripWithoutSystemUiRestart() = runBlocking {
        for ((key, wanted) in listOf(P.CLOCK_SECONDS to "1", P.CLOCK_DAY to "1", P.HIDE_NAV_PILL to "1",
                P.USER_ROTATION to "0", P.AUTO_ROTATE to "0")) {
            val before = PrivilegedManager.readSystemOption(key)
            assertTrue(before.toString(), before.available)
            try {
                assertEquals("Saved", SystemOptions.write(context, key, wanted))
                assertEquals(wanted, PrivilegedManager.readSystemOption(key).value)
                assertTrue(SystemOptions.restore(context, listOf(key)).startsWith("1 restored"))
                assertEquals(before, PrivilegedManager.readSystemOption(key))
            } finally { PrivilegedManager.setSystemOption(key, before.value) }
        }
    }

    @Test fun monoAndBalanceRoundTripWithoutPlayingAudio() = runBlocking {
        for ((key, wanted) in listOf(P.MASTER_MONO to "1", P.MASTER_BALANCE to "-0.2")) {
            val before = PrivilegedManager.readSystemOption(key)
            assertTrue(before.toString(), before.available)
            try {
                assertEquals("Saved", SystemOptions.write(context, key, wanted))
                assertEquals(wanted, PrivilegedManager.readSystemOption(key).value)
                SystemOptions.restore(context, listOf(key))
                assertEquals(before, PrivilegedManager.readSystemOption(key))
            } finally { PrivilegedManager.setSystemOption(key, before.value) }
        }
    }

    @Test fun feedbackFlagsRestoreWithoutPlayingSounds() = runBlocking {
        for (key in listOf(P.TOUCH_SOUNDS, P.LOCK_SOUNDS, P.DIAL_SOUNDS, P.CHARGING_SOUNDS, P.CHARGING_VIBRATION)) {
            val before = PrivilegedManager.readSystemOption(key)
            assertTrue(before.toString(), before.available)
            try {
                assertEquals("Saved", SystemOptions.write(context, key, "0"))
                assertEquals("0", PrivilegedManager.readSystemOption(key).value)
                assertTrue(SystemOptions.restore(context, listOf(key)).startsWith("1 restored"))
                assertEquals(before, PrivilegedManager.readSystemOption(key))
            } finally { PrivilegedManager.setSystemOption(key, before.value) }
        }
    }

    @Test fun extraDimEitherRoundTripsOrExplicitlyRefusesUnsupportedDevice() = runBlocking {
        val key = P.EXTRA_DIM_LEVEL
        val before = PrivilegedManager.readSystemOption(key)
        if (!before.available) {
            assertTrue(PrivilegedManager.setSystemOption(key, "30").startsWith("ERROR"))
        } else try {
            assertEquals("Saved", SystemOptions.write(context, key, "30"))
            assertEquals("30", PrivilegedManager.readSystemOption(key).value)
            assertTrue(SystemOptions.restore(context, listOf(key)).startsWith("1 restored"))
            assertEquals(before, PrivilegedManager.readSystemOption(key))
        } finally { PrivilegedManager.setSystemOption(key, before.value) }
    }
}
