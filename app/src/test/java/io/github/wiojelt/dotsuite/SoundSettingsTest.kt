package io.github.wiojelt.dotsuite

import io.github.wiojelt.dotsuite.data.SoundSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class SoundSettingsTest {
    @Test
    fun unknownPanelSideFallsBackToAuto() {
        assertEquals(SoundSettings.PanelSide.AUTO, SoundSettings.PanelSide.from(99))
    }

    @Test
    fun persistedEnumValuesRemainStable() {
        assertEquals(0, SoundSettings.PanelSide.AUTO.value)
        assertEquals(1, SoundSettings.PanelSide.LEFT.value)
        assertEquals(2, SoundSettings.PanelSide.RIGHT.value)
    }

    @Test
    fun panelTimeoutAcceptsEveryWholeSecondFromOneToTen() {
        (1..10).forEach { seconds ->
            assertEquals(seconds * 1_000, SoundSettings.normalizePanelTimeout(seconds * 1_000))
        }
    }

    @Test
    fun invalidPanelTimeoutFallsBackToThreeSeconds() {
        assertEquals(3_000, SoundSettings.normalizePanelTimeout(500))
        assertEquals(3_000, SoundSettings.normalizePanelTimeout(10_500))
        assertEquals(3_000, SoundSettings.normalizePanelTimeout(11_000))
    }
}
