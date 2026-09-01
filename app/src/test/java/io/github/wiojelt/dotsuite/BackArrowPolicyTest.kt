package io.github.wiojelt.dotsuite

import io.github.wiojelt.dotsuite.data.BackArrowPolicy as B
import io.github.wiojelt.dotsuite.data.PersonalizationPolicy as P
import org.junit.Assert.*
import org.junit.Test

class BackArrowPolicyTest {
    @Test fun allowListAcceptsOnlyExactKeysAndBoundedValues() {
        B.KEYS.forEach { assertEquals("secure", P.namespace(it)); assertTrue(P.acceptsString(it, null)) }
        for (v in -5..130) {
            assertEquals(v in 0..1, P.acceptsString(B.ENABLED, v.toString()))
            assertEquals(v in 0..15, P.acceptsString(B.STYLE, v.toString()))
            assertEquals(v in 0..11, P.acceptsString(B.MOTION, v.toString()))
            assertEquals(v in 80..120, P.acceptsString(B.SIZE, v.toString()))
        }
        for (s in listOf("", "NaN", "1.0", "1\n", "+1", "1;reboot", "true", "999999999999999")) {
            B.KEYS.forEach { assertFalse(P.acceptsString(it, s)) }
        }
        assertFalse(P.acceptsString(B.STYLE + "_other", "1"))
    }
    @Test fun motionIsBoundedReversibleAndFinite() {
        assertEquals(0f, B.progress(Float.NaN, 10f))
        assertEquals(0f, B.progress(10f, 0f))
        assertEquals(1f, B.progress(1000f, 10f))
        for (motion in 0..3) for (p in -20..120) {
            val scale = B.motionScale(motion, p / 100f)
            assertTrue(scale.isFinite() && scale in .65f..1f)
        }
        assertEquals(1f, B.motionScale(0, .5f))
        assertEquals(1f, B.motionScale(3, 1f), .001f)
    }
}
