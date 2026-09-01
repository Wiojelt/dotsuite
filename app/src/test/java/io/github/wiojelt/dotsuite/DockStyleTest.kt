package io.github.wiojelt.dotsuite

import io.github.wiojelt.dotsuite.data.*
import org.junit.Assert.*
import org.junit.Test

class DockStyleTest {
    @Test fun valuesClampAndSmallIconsKeepFortyEightDpTouchRows() {
        val s = DockStyle(-1, 999, 0, -5, 99, -2).bounded()
        assertEquals(DockStyle(80, 100, 35, 0, 8, 0), s)
        assertEquals(48f, s.rowDp)
        assertTrue(s.railDp >= 60)
        assertEquals(0, DockStyle().dimPercent)
    }
    @Test fun everyPositionSizeAndDirectionFitsTheUsableFrame() {
        for (width in listOf(1, 200, 1080)) for (height in listOf(1, 100, 2200)) {
            for (density in listOf(Float.NaN, 1f, 3f)) for (position in 0..100 step 10)
                for (expanded in listOf(false, true)) for (size in listOf(80, 100, 140)) {
                    val f = DockGeometry.frame(width, height, density, 8, expanded, DockStyle(size, position, edgeInsetDp = 24))
                    assertTrue(f.width > 0 && f.height > 0)
                    assertTrue(f.x >= 0 && f.y >= 0)
                    assertTrue(f.x + f.width <= width && f.y + f.height <= height)
                }
        }
    }
    @Test fun bottomPositionReachesBottomAndRowLimitOnlyClampsVisibleCount() {
        val s = DockStyle(positionPercent = 100, visibleApps = 3)
        val three = DockGeometry.frame(1080, 2200, 3f, 3, false, s)
        assertEquals(2200, three.y + three.height)
        assertEquals(three, DockGeometry.frame(1080, 2200, 3f, 8, false, s))
        assertTrue(DockGeometry.frame(1080, 2200, 3f, 8, false, s.copy(visibleApps = 8)).height > three.height)
    }
}
