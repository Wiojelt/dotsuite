package io.github.wiojelt.dotsuite

import io.github.wiojelt.dotsuite.data.PersonalizationPolicy as P
import io.github.wiojelt.dotsuite.data.GesturePolicy as G
import org.junit.Assert.*
import org.junit.Test

class PersonalizationPolicyTest {
    @Test fun nativeGestureKeysSupportExactBackupAndRestoration() {
        listOf(P.NOTCH_ENABLED, P.NOTCH_HAPTICS, P.STATUS_DOUBLE_SLEEP, P.POWER_TORCH, P.HIDE_DIRECT_SHARE).forEach { key ->
            assertEquals("secure", P.namespace(key))
            listOf(null, "0", "1").forEach { assertTrue(P.acceptsString(key, it)) }
            listOf("2", "-1", "true", "0.5", "1;reboot").forEach { assertFalse(P.acceptsString(key, it)) }
        }
        listOf(P.NOTCH_TAP, P.NOTCH_DOUBLE, P.NOTCH_HOLD, P.NOTCH_LEFT, P.NOTCH_RIGHT).forEach { key ->
            assertEquals("secure", P.namespace(key))
            assertTrue(P.acceptsString(key, null))
            (0..15).forEach { assertTrue(P.acceptsString(key, it.toString())) }
            assertFalse(P.acceptsString(key, "16"))
        }
        assertNull(P.namespace(P.POWER_TORCH_STATUS))
    }
    @Test fun audioOptionsCannotChangeMasterVolume() {
        assertEquals("system", P.namespace(P.MASTER_MONO))
        assertEquals("system", P.namespace(P.MASTER_BALANCE))
        assertFalse(P.acceptsString("volume_master", "1"))
        assertTrue(P.acceptsString(P.MASTER_MONO, "1"))
        assertFalse(P.acceptsString(P.MASTER_MONO, "2"))
        listOf("-1.0", "-0.4", "0", "0.8", "1").forEach { assertTrue(P.acceptsString(P.MASTER_BALANCE, it)) }
        listOf("-1.1", "2", "NaN", "-Infinity").forEach { assertFalse(P.acceptsString(P.MASTER_BALANCE, it)) }
    }
    @Test fun onlyKnownSettingsHaveNamespaces() {
        assertEquals("secure", P.namespace(P.CARRIER_LABEL))
        assertEquals("global", P.namespace(P.WINDOW_SCALE))
        listOf("adb_enabled", "usb_config", "foo", "", "animator_duration_scale;reboot").forEach {
            assertNull(P.namespace(it)); assertFalse(P.acceptsString(it, null)); assertFalse(P.acceptsInt(it, 1))
        }
    }
    @Test fun actionsAreBoundedAndStable() {
        assertEquals(11, P.PHOTO_FRONT); assertEquals(14, P.VIDEO_REAR)
        assertTrue((0..15).all { P.acceptsInt(P.NOTCH_TAP, it) })
        assertFalse(P.acceptsInt(P.NOTCH_DOUBLE, -1)); assertFalse(P.acceptsInt(P.NOTCH_HOLD, 16))
        assertFalse(P.isCaptureAction(P.CAMERA)); assertTrue(P.isCaptureAction(P.PHOTO_REAR))
    }
    @Test fun flagsRejectActionIds() {
        assertTrue(P.acceptsInt(P.NOTCH_ENABLED, 0)); assertTrue(P.acceptsInt(P.NOTCH_HAPTICS, 1))
        assertFalse(P.acceptsInt(P.STATUS_DOUBLE_SLEEP, 2))
    }
    @Test fun newFlagsAreBooleanAndRuntimeStatusCannotBeWrittenThroughTheBridge() {
        listOf(P.POWER_TORCH, P.HIDE_DIRECT_SHARE).forEach { key ->
            assertTrue(P.acceptsInt(key, 0)); assertTrue(P.acceptsInt(key, 1))
            assertFalse(P.acceptsInt(key, -1)); assertFalse(P.acceptsInt(key, 2))
        }
        assertFalse(P.acceptsInt(P.POWER_TORCH_STATUS, 1))
        assertFalse(P.isCaptureAction(P.QUICK_DOCK))
    }
    @Test fun cameraDirectionAndModeStayIndependent() {
        assertTrue(P.isFront(P.PHOTO_FRONT)); assertFalse(P.isVideo(P.PHOTO_FRONT))
        assertTrue(P.isVideo(P.VIDEO_REAR)); assertFalse(P.isFront(P.VIDEO_REAR))
        assertTrue(P.isVideo(P.VIDEO_FRONT)); assertTrue(P.isFront(P.VIDEO_FRONT))
    }
    @Test fun carrierAcceptsUnicodeWithoutShellInterpretation() {
        listOf("wiojelt", "Türkçe", "📶 Nothing", "NULL", "test; echo 1", "").forEach { assertTrue(P.acceptsString(P.CARRIER_LABEL, it)) }
        assertTrue(P.acceptsString(P.CARRIER_LABEL, null))
        assertFalse(P.acceptsString(P.CARRIER_LABEL, "null"))
    }
    @Test fun feedbackFlagsOnlyAcceptBooleansAndOriginalRestoration() {
        listOf(P.TOUCH_SOUNDS, P.LOCK_SOUNDS, P.DIAL_SOUNDS, P.CHARGING_SOUNDS, P.CHARGING_VIBRATION).forEach { key ->
            listOf("0", "1", null).forEach { assertTrue(P.acceptsString(key, it)) }
            listOf("true", "2", "-1", "0.5", "").forEach { assertFalse(P.acceptsString(key, it)) }
        }
        assertEquals("secure", P.namespace(P.CHARGING_SOUNDS))
        assertEquals("system", P.namespace(P.LOCK_SOUNDS))
    }
    @Test fun extraDimIsBoundedAndCannotWriteBrightness() {
        listOf("0", "50", "80", "100", null).forEach { assertTrue(P.acceptsString(P.EXTRA_DIM_LEVEL, it)) }
        listOf("-1", "101", "0.5", "NaN").forEach { assertFalse(P.acceptsString(P.EXTRA_DIM_LEVEL, it)) }
        assertFalse(P.acceptsString("screen_brightness", "0"))
    }
    @Test fun carrierRejectsControlsBidiAndOverlongValues() {
        listOf("A\nB", "A\tB", "\u202ERLO", "\u0000", " leading", "trailing ", "a".repeat(33), "\uD800").forEach {
            assertFalse("accepted: $it", P.acceptsString(P.CARRIER_LABEL, it))
        }
        assertTrue(P.acceptsString(P.CARRIER_LABEL, "🙂".repeat(32)))
    }
    @Test fun scalesAcceptSafeDecimalValuesAndNullRestoration() {
        listOf("0", "0.25", "0.5", "0.75", "1.0", "2", "10", null).forEach { assertTrue(P.acceptsString(P.ANIMATOR_SCALE, it)) }
    }
    @Test fun scalesRejectNonFiniteOrExecutableText() {
        listOf("NaN", "Infinity", "-1", "10.1", "1;reboot", "1e1", "", "0,5").forEach {
            assertFalse(P.acceptsString(P.TRANSITION_SCALE, it))
        }
    }
    @Test fun restoreComparisonUnderstandsNumericFormattingButNotCarrierText() {
        assertTrue(P.sameSetting(P.WINDOW_SCALE, "0.50", "0.5"))
        assertFalse(P.sameSetting(P.CARRIER_LABEL, "0.50", "0.5"))
        assertFalse(P.sameSetting(P.CARRIER_LABEL, null, "null"))
        assertFalse(P.sameSetting(P.WINDOW_SCALE, "1.0", "0.5"))
    }
    @Test fun verticalAndDiagonalShadeGesturesWin() {
        assertEquals(G.SHADE, G.motion(40f, 80f, 1, 8f, 36f))
        assertEquals(G.SHADE, G.motion(50f, 50f, 1, 8f, 36f))
        assertEquals(G.SHADE, G.motion(0f, -10f, 1, 8f, 36f))
    }
    @Test fun horizontalGesturesRequireDistanceAndDirection() {
        assertEquals(G.LEFT, G.motion(-50f, 10f, 1, 8f, 36f))
        assertEquals(G.RIGHT, G.motion(50f, 10f, 1, 8f, 36f))
        assertEquals(G.STILL, G.motion(30f, 0f, 1, 8f, 36f))
        assertEquals(G.STILL, G.motion(40f, 30f, 1, 8f, 36f))
    }
    @Test fun multiTouchCancelsBeforeShortcuts() {
        assertEquals(G.CANCEL, G.motion(80f, 0f, 2, 8f, 36f))
        assertEquals(G.CANCEL, G.motion(0f, 0f, 0, 8f, 36f))
    }
    @Test fun tapSlopUsesBothAxes() {
        assertTrue(G.tap(4f, 4f, 8f)); assertFalse(G.tap(7f, 7f, 8f))
    }
}
