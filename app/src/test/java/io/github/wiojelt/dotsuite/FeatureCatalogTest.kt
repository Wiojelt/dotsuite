package io.github.wiojelt.dotsuite
import io.github.wiojelt.dotsuite.data.FeatureCatalog as C
import org.junit.Assert.*
import org.junit.Test

class FeatureCatalogTest {
    @Test fun allRoutesAreUnique() { assertEquals(C.entries.size, C.routes.size) }
    @Test fun blankSearchKeepsEveryFeatureInOrder() { assertEquals(C.entries, C.search("  ")) }
    @Test fun turkishAliasesAndEnglishTitlesAreSearchable() {
        assertEquals("notch", C.search("çentik").single().page)
        assertTrue(C.search("UYKU").isEmpty())
        assertEquals(setOf("motion", "back-arrow"), C.search("ANİMASYON").map { it.page }.toSet())
        assertEquals("hearing", C.search("left balance").single().page)
    }
    @Test fun unknownQueriesDoNotInventFeatures() { assertTrue(C.search("invisible remote camera").isEmpty()) }
    @Test fun selectedToolsExistButRejectedFeaturesDoNot() {
        assertTrue(C.routes.containsAll(listOf("maps", "standby", "dock", "power", "share")))
        assertFalse(C.routes.any { it in setOf("timer", "eq", "routing", "edge", "weather") })
    }
    @Test fun navigationAreasIncludeEveryRouteExactlyOnce() {
        assertEquals("SOUND", C.areaFor("panel").name)
        assertEquals("SOUND", C.areaFor("apps").name)
        assertEquals("TOOLS", C.areaFor("maps").name)
        assertEquals("STANDBY", C.areaFor("standby").name)
        assertEquals("CAMERA", C.areaFor("capture").name)
        assertEquals("SHORTCUTS", C.areaFor("notch").name)
        assertEquals("SETTINGS", C.areaFor("recovery").name)
        assertEquals(C.routes, C.entries.groupBy { it.area }.values.flatten().map { it.page }.toSet())
        assertEquals(io.github.wiojelt.dotsuite.data.FeatureArea.entries.toSet(), C.entries.map { it.area }.toSet())
    }
    @Test fun rootVisibilityRequiresActualAuthorizedRootOrExplicitOptIn() {
        val policy = io.github.wiojelt.dotsuite.data.FeatureVisibilityPolicy
        assertTrue(policy.showRoot(0, false))
        assertTrue(policy.showRoot(2000, true))
        assertFalse(policy.showRoot(2000, false))
        assertFalse(policy.showRoot(null, false))
        assertFalse(policy.showRoot(-1, false))
        assertTrue(policy.showRoot(null, true))
    }
    @Test fun shellAccessDoesNotClaimToReplaceModuleHooks() {
        val policy = io.github.wiojelt.dotsuite.data.FeatureVisibilityPolicy
        val shown = C.entries.filter { policy.visible(it, false) }.map { it.page }.toSet()
        assertTrue(shown.containsAll(listOf("apps", "motion", "capture", "standby", "maps", "diagnostics", "recovery")))
        assertFalse(shown.any { it in setOf("panel", "keys", "lockscreen", "notch", "carrier", "power", "share") })
        assertEquals(C.entries, C.entries.filter { policy.visible(it, true) })
    }
}
