package io.github.wiojelt.dotsuite

import io.github.wiojelt.dotsuite.data.OrbitPhysics as O
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class OrbitPhysicsTest {
    @Test fun fasterFlickTravelsFurtherAndDecayIsMonotonic() {
        assertTrue(O.travel(900f, 2f) > O.travel(300f, 2f))
        assertTrue(O.speedAt(900f, 2f) < O.speedAt(900f, 1f))
        assertTrue(O.travel(900f, 2f) < 900f / O.FRICTION)
    }
    @Test fun reverseFlickHasSymmetricMotion() {
        assertEquals(-O.travel(500f, 1f), O.travel(-500f, 1f), .001f)
        assertEquals(-O.speedAt(500f, 1f), O.speedAt(-500f, 1f), .001f)
    }
    @Test fun speedIsCappedAndInvalidInputCannotPoisonAnimation() {
        assertEquals(O.MAX_SPEED, O.safeSpeed(10000f))
        assertEquals(-O.MAX_SPEED, O.safeSpeed(-10000f))
        assertEquals(0f, O.safeSpeed(Float.NaN))
        assertEquals(0f, O.travel(200f, Float.NaN))
        assertEquals(0f, O.travel(200f, -3f))
        assertEquals(0f, O.normalized(Float.POSITIVE_INFINITY))
    }
    @Test fun angleWrapUsesShortestPath() {
        assertEquals(359f, O.normalized(-1f))
        assertEquals(2f, O.shortestDelta(359f, 1f))
        assertEquals(-2f, O.shortestDelta(1f, 359f))
    }
    @Test fun everyLandingPointsAtExactlyOneCategory() {
        for (angle in -1440..1440 step 7) {
            val winner = O.winner(angle.toFloat())
            val snapped = O.snap(angle.toFloat(), winner)
            assertTrue(winner in O.areas.indices)
            assertTrue(abs(snapped - angle) <= 180f / O.areas.size + .001f)
            assertEquals(winner, O.winner(snapped))
            assertEquals(0f, O.shortestDelta(O.angle(winner, snapped), -90f), .001f)
        }
    }
    @Test fun coffeeIsOneOrdinarySectorNotAWeightedDonationTrap() {
        assertEquals(8, O.areas.size)
        assertEquals(8, O.areas.toSet().size)
        assertEquals(1, O.areas.count { it == io.github.wiojelt.dotsuite.data.FeatureArea.SETTINGS })
        assertEquals(1, O.areas.count { it == io.github.wiojelt.dotsuite.data.FeatureArea.SUPPORT })
        for (index in O.areas.indices) {
            val centered = -90f - O.angle(index)
            for (delta in listOf(-20f, 0f, 20f)) assertEquals(index, O.winner(centered + delta))
        }
    }
}
