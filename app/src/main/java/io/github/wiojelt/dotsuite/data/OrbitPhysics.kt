package io.github.wiojelt.dotsuite.data

import kotlin.math.*

/** Degrees and seconds only. No random side effects, Android calls or unbounded simulation. */
object OrbitPhysics {
    val areas = listOf(FeatureArea.SOUND, FeatureArea.SHORTCUTS, FeatureArea.TOOLS,
        FeatureArea.CAMERA, FeatureArea.SUPPORT, FeatureArea.STANDBY, FeatureArea.INTERFACE, FeatureArea.SETTINGS)
    // Eight equally weighted destinations; the fixed centre is search, never a roulette result.
    const val startAngle = -90f
    const val FRICTION = 2.5f
    const val MAX_SPEED = 1440f
    fun normalized(degrees: Float) = if (degrees.isFinite()) ((degrees % 360f) + 360f) % 360f else 0f
    fun shortestDelta(from: Float, to: Float) = (normalized(to - from) + 180f) % 360f - 180f
    fun safeSpeed(speed: Float) = if (speed.isFinite()) speed.coerceIn(-MAX_SPEED, MAX_SPEED) else 0f
    private fun time(seconds: Float) = if (seconds.isFinite()) seconds.coerceIn(0f, 5f) else 0f
    fun speedAt(initialSpeed: Float, seconds: Float) = safeSpeed(initialSpeed) * exp(-FRICTION * time(seconds))
    fun travel(initialSpeed: Float, seconds: Float) = safeSpeed(initialSpeed) / FRICTION * (1 - exp(-FRICTION * time(seconds)))
    fun angle(index: Int, rotation: Float = 0f, count: Int = areas.size): Float {
        require(count > 0 && index in 0 until count)
        return startAngle + index * 360f / count + rotation
    }
    fun winner(rotation: Float, count: Int = areas.size): Int {
        require(count > 0)
        return (0 until count).minBy { abs(shortestDelta(angle(it, rotation, count), -90f)) }
    }
    fun snap(rotation: Float, winner: Int, count: Int = areas.size): Float =
        rotation + shortestDelta(angle(winner, rotation, count), -90f)
}
