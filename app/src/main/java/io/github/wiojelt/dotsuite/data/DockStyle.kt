package io.github.wiojelt.dotsuite.data

import kotlin.math.roundToInt

enum class DockMotion(val title: String) { SLIDE("Slide"), SOFT("Soft spring"), FADE("Fade"), NONE("None") }

data class DockStyle(
    val sizePercent: Int = 100,
    val positionPercent: Int = 50,
    val opacityPercent: Int = 92,
    val edgeInsetDp: Int = 0,
    val visibleApps: Int = 5,
    val dimPercent: Int = 0,
    val motion: DockMotion = DockMotion.SLIDE,
) {
    fun bounded() = copy(sizePercent = sizePercent.coerceIn(80, 140), positionPercent = positionPercent.coerceIn(0, 100),
        opacityPercent = opacityPercent.coerceIn(35, 100), edgeInsetDp = edgeInsetDp.coerceIn(0, 24),
        visibleApps = visibleApps.coerceIn(3, 8), dimPercent = dimPercent.coerceIn(0, 35))
    val scale get() = bounded().sizePercent / 100f
    val rowDp get() = (52 * scale).coerceAtLeast(48f)
    val iconDp get() = 32 * scale
    val railDp get() = (68 * scale).coerceAtLeast(60f)
}

/** Coordinates relative to the system-bar/cutout-inset frame, never the physical screen. */
data class DockFrame(val width: Int, val height: Int, val x: Int, val y: Int)
object DockGeometry {
    fun frame(width: Int, height: Int, density: Float, count: Int, expanded: Boolean, style: DockStyle): DockFrame {
        val d = density.takeIf { it.isFinite() && it > 0 } ?: 1f
        val s = style.bounded()
        val availableW = width.coerceAtLeast(1)
        val availableH = height.coerceAtLeast(1)
        val inset = (s.edgeInsetDp * d).roundToInt().coerceAtMost((availableW - 1).coerceAtLeast(0) / 2)
        val w = ((if (expanded) 224 * s.scale else s.railDp) * d).roundToInt().coerceIn(1, availableW - inset)
        val rows = count.coerceIn(1, s.visibleApps)
        val h = ((rows * s.rowDp + if (expanded) 164 else 76) * d).roundToInt().coerceIn(1, availableH)
        return DockFrame(w, h, inset, ((availableH - h) * s.positionPercent / 100f).roundToInt())
    }
}
