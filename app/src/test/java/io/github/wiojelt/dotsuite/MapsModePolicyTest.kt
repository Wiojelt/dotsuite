package io.github.wiojelt.dotsuite
import io.github.wiojelt.dotsuite.maps.MapsModePolicy as P
import org.junit.Assert.*
import org.junit.Test

class MapsModePolicyTest {
    @Test fun onlyOngoingMapsNavigationQualifies() {
        assertTrue(P.isNavigation("com.google.android.apps.maps", "navigation", true))
        assertFalse(P.isNavigation("com.other", "navigation", true))
        assertFalse(P.isNavigation("com.google.android.apps.maps", "transport", true))
        assertFalse(P.isNavigation("com.google.android.apps.maps", "navigation", false))
        assertFalse(P.isNavigation("com.google.android.apps.maps", null, true))
    }
    @Test fun noLaunchWithoutEveryGate() {
        assertTrue(P.shouldLaunch(true, true, true, false, 100, 0))
        assertFalse(P.shouldLaunch(false, true, true, false, 100, 0))
        assertFalse(P.shouldLaunch(true, false, true, false, 100, 0))
        assertFalse(P.shouldLaunch(true, true, false, false, 100, 0))
        assertFalse(P.shouldLaunch(true, true, true, true, 100, 0))
        assertFalse(P.shouldLaunch(true, true, true, false, 4999, 1))
        assertTrue(P.shouldLaunch(true, true, true, false, 5001, 1))
    }
}
