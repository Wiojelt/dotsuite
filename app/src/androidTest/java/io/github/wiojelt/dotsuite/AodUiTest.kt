package io.github.wiojelt.dotsuite

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import io.github.wiojelt.dotsuite.data.AodPolicy as A
import io.github.wiojelt.dotsuite.data.AodSnapshot
import io.github.wiojelt.dotsuite.data.FeatureAccess
import io.github.wiojelt.dotsuite.data.FeatureCatalog
import io.github.wiojelt.dotsuite.ui.AodContent
import io.github.wiojelt.dotsuite.ui.theme.DotSuiteTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AodUiTest {
    @get:Rule val compose = createComposeRule()
    private val changes = mutableListOf<Pair<String, String>>()
    private var nativeVisits = 0
    private var restores = 0
    private val native = AodSnapshot(available = true, nothing = true, allDay = false,
        schedule = true, tapMode = true, writable = A.KEYS.toSet())
    private fun open(state: AodSnapshot = native, canEdit: Boolean = true) {
        compose.setContent { DotSuiteTheme {
            AodContent(PaddingValues(), state, false, canEdit, "", {},
                { key, value -> changes += key to value }, { restores++ }, { nativeVisits++ }, {})
        } }
    }
    @Test fun entryDoesNotChangeAnythingAndOnlyRequestedMasterIsWritten() {
        open()
        assertTrue(changes.isEmpty())
        compose.onNodeWithText("Native AOD").performClick()
        assertEquals(listOf(A.ENABLED to "1"), changes)
    }
    @Test fun unsupportedModesStayDisabledAndModeDoesNotEnableMaster() {
        open()
        compose.onNodeWithText("All day").assertIsNotEnabled()
        compose.onNodeWithText("Schedule").performClick()
        assertEquals(listOf(A.MODE to "1"), changes)
    }
    @Test fun wakeControlUsesOneNativeKeyAndScheduleOpensOnlyOnTap() {
        open()
        compose.onNodeWithText("Notification pulse").performScrollTo().performClick()
        assertEquals(listOf(A.NOTIFICATIONS to "0"), changes)
        assertEquals(0, nativeVisits)
        compose.onNodeWithText("Native schedule & clock").performScrollTo().performClick()
        assertEquals(1, nativeVisits)
    }
    @Test fun missingAccessOrCapabilityCannotWrite() {
        open(AodSnapshot(), canEdit = false)
        compose.onNodeWithText("Native AOD").assertIsNotEnabled()
        compose.onNodeWithText("Lift to wake").performScrollTo().assertIsNotEnabled()
        assertTrue(changes.isEmpty())
    }
    @Test fun restoreAndSearchRemainAvailableWithoutRootModule() {
        open()
        compose.onNodeWithTag("aod-page").performScrollToNode(hasText("Restore my original AOD settings"))
        compose.onNodeWithText("Restore my original AOD settings").performClick()
        assertEquals(1, restores)
        assertTrue(changes.isEmpty())
        assertEquals(FeatureAccess.BRIDGE, FeatureCatalog.entries.single { it.page == "aod" }.access)
        assertTrue(FeatureCatalog.search("aod").any { it.page == "aod" })
    }
}
