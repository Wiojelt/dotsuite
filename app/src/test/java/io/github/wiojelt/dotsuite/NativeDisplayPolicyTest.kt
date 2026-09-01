package io.github.wiojelt.dotsuite

import io.github.wiojelt.dotsuite.data.PersonalizationPolicy as P
import io.github.wiojelt.dotsuite.data.FeatureCatalog as C
import io.github.wiojelt.dotsuite.data.FeatureAccess
import org.junit.Assert.*
import org.junit.Test

class NativeDisplayPolicyTest {
    @Test fun nativeDisplayValuesAreStrictlyBoundedAndRestorable() {
        for (key in listOf(P.CLOCK_SECONDS, P.CLOCK_DAY, P.HIDE_NAV_PILL, P.AUTO_ROTATE)) {
            assertTrue(P.acceptsString(key, null))
            assertTrue(P.acceptsString(key, "0"))
            assertTrue(P.acceptsString(key, "1"))
            for (bad in listOf("2", "true", "1.0", "1; reboot", "-1", "")) assertFalse(P.acceptsString(key, bad))
        }
        for (value in 0..3) assertTrue(P.acceptsString(P.USER_ROTATION, value.toString()))
        assertTrue(P.acceptsString(P.USER_ROTATION, null))
        for (bad in listOf("4", "-1", "90", "01", "0.0")) assertFalse(P.acceptsString(P.USER_ROTATION, bad))
    }
    @Test fun nativeNamespacesAreExact() {
        assertEquals("secure", P.namespace(P.CLOCK_SECONDS))
        assertEquals("secure", P.namespace(P.CLOCK_DAY))
        assertEquals("secure", P.namespace(P.HIDE_NAV_PILL))
        assertEquals("system", P.namespace(P.USER_ROTATION))
        assertEquals("system", P.namespace(P.AUTO_ROTATE))
        assertNull(P.namespace("sys.powerctl"))
    }
    @Test fun rootHooksAreNotMislabelledAsShizukuFeatures() {
        assertTrue(C.entries.filter { it.page in listOf("clock-style", "navigation") }.all { it.access == FeatureAccess.ROOT_MODULE })
        assertTrue(C.entries.filter { it.page in listOf("clock", "rotation") }.all { it.access == FeatureAccess.BRIDGE })
    }
}
