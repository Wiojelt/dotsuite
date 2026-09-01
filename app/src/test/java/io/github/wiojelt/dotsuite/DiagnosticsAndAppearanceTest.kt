package io.github.wiojelt.dotsuite

import io.github.wiojelt.dotsuite.data.*
import io.github.wiojelt.dotsuite.diagnostics.DiagnosticBuffer
import org.junit.Test
import org.junit.Assert.*

class DiagnosticsAndAppearanceTest {
    private fun entry(time: Long, detail: String = "safe") = DiagnosticBuffer.Entry(time, time, "INFO", "test", detail)
    @Test fun reportUsesExactlyOneMinuteAndDoesNotReturnFutureEntries() {
        val log = DiagnosticBuffer()
        log.add(entry(0)); log.add(entry(1)); log.add(entry(60_000))
        assertEquals(3, log.recent(60_000).size)
        assertEquals(2, log.recent(60_001).size)
        assertEquals(0, log.recent(120_001).size)
        log.add(entry(200_000))
        assertTrue(log.recent(100_000).isEmpty())
    }
    @Test fun reportIsBoundedEvenUnderErrorFlood() {
        val log = DiagnosticBuffer(8)
        repeat(1000) { log.add(entry(it.toLong(), "x".repeat(2000))) }
        assertEquals(8, log.recent(1000).size)
        assertTrue(log.recent(1000).all { it.detail.length == 640 })
        log.clear(); assertTrue(log.recent(1000).isEmpty())
    }
    @Test fun sensitiveDataAndLogInjectionAreRedacted() {
        val text = DiagnosticBuffer.clean("pin=1234 token=abc email a@b.com https://private.example/x\n/data/user/0/private 123456789")
        listOf("1234", "abc", "a@b.com", "private", "123456789", "\n").forEach { assertFalse(text.contains(it)) }
        val log = DiagnosticBuffer()
        log.add(entry(1).copy(level = "ERROR\nforged"))
        assertEquals("INFO", log.recent(1).single().level)
    }
    @Test fun exceptionMessagesAreNeverIncluded() {
        val e = IllegalStateException("private password value", IllegalArgumentException("private token value"))
        e.stackTrace = arrayOf(StackTraceElement("io.github.wiojelt.dotsuite.Example", "go", "Example.kt", 42))
        val clean = DiagnosticBuffer.throwable(e)
        assertTrue(clean.contains("Example.go:42"))
        assertFalse(clean.contains("private"))
        assertTrue(clean.contains("IllegalArgumentException"))
    }
    @Test fun decorationsAreDefaultOffAndStopInEveryHiddenOrReducedMotionState() {
        assertEquals(HomeBackdrop.NONE, AppearanceOptions().backdrop)
        assertFalse(AppearanceOptions().touchSounds)
        assertFalse(BackdropPattern.animate(HomeBackdrop.NONE, true, true, true))
        assertTrue(BackdropPattern.animate(HomeBackdrop.SNOW, true, true, true))
        for (mode in HomeBackdrop.entries) {
            assertFalse(BackdropPattern.animate(mode, false, true, true))
            assertFalse(BackdropPattern.animate(mode, true, false, true))
            assertFalse(BackdropPattern.animate(mode, true, true, false))
        }
    }
    @Test fun backdropSeedsAreStableAndCoordinatesRemainBounded() {
        repeat(10_000) {
            assertTrue(BackdropPattern.seed(it, 8) in 0f..1f)
            assertEquals(BackdropPattern.seed(it, 8), BackdropPattern.seed(it, 8))
            assertTrue(BackdropPattern.wrap(it * -.31f) in 0f..1f)
        }
    }
    @Test fun touchSoundsRespectEveryGuardAndNeverNeedMediaFocus() {
        fun allowed(enabled: Boolean = true, foreground: Boolean = true, ringer: Boolean = true,
            system: Boolean = true, dnd: Boolean = true, call: Boolean = false, media: Boolean = false, volume: Int = 1) =
            TouchSoundPolicy.allowed(enabled, foreground, ringer, system, dnd, call, media, volume)
        assertTrue(allowed())
        assertFalse(allowed(enabled = false)); assertFalse(allowed(foreground = false))
        assertFalse(allowed(ringer = false)); assertFalse(allowed(system = false))
        assertFalse(allowed(dnd = false)); assertFalse(allowed(call = true))
        assertFalse(allowed(media = true)); assertFalse(allowed(volume = 0))
        assertEquals(.25f, TouchSoundPolicy.gain())
        assertEquals(.35f, TouchSoundPolicy.gain(100))
    }
}
