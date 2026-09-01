package io.github.wiojelt.dotsuite

import io.github.wiojelt.dotsuite.privileged.parseAudioFocusMode
import io.github.wiojelt.dotsuite.service.hasAndroidAutoFocus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudioFocusModeTest {
    @Test fun parsesEverySupportedExplicitMode() {
        listOf("default", "allow", "foreground", "ignore", "deny").forEach { mode ->
            assertEquals(mode, parseAudioFocusMode("TAKE_AUDIO_FOCUS: $mode; time=+1m"))
        }
    }

    @Test fun noOverrideMeansDefaultAndErrorsStayErrors() {
        assertEquals("default", parseAudioFocusMode("No operations.\nDefault mode: foreground"))
        assertNull(parseAudioFocusMode("ERROR: bridge disconnected"))
    }

    @Test fun androidAutoMustBeInTheCurrentFocusStackNotOnlyHistory() {
        val active = """Audio Focus stack entries (last is top of stack):
            source:x -- pack: com.google.android.projection.gearhead -- gain: GAIN
            No external focus policy
            requestAudioFocus() callingPack=other.history
        """.trimIndent()
        val historyOnly = """Audio Focus stack entries (last is top of stack):
            No external focus policy
            requestAudioFocus() callingPack=com.google.android.projection.gearhead
        """.trimIndent()
        assertEquals(true, hasAndroidAutoFocus(active))
        assertEquals(false, hasAndroidAutoFocus(historyOnly))
    }
}
