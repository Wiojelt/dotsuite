package io.github.wiojelt.dotsuite.data

/** A cue must never override the user's audio/privacy choices. No focus request or volume write. */
object TouchSoundPolicy {
    fun allowed(enabled: Boolean, foreground: Boolean, normalRinger: Boolean, systemSounds: Boolean,
        dndOff: Boolean, inCommunication: Boolean, mediaActive: Boolean, streamVolume: Int): Boolean =
        enabled && foreground && normalRinger && systemSounds && dndOff && !inCommunication && !mediaActive && streamVolume > 0
    fun gain(percent: Int = 25): Float = percent.coerceIn(0, 35) / 100f
}
