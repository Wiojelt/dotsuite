package io.github.wiojelt.dotsuite.data

enum class HomeBackdrop(val title: String, val detail: String) {
    NONE("None", "Quiet, static background"),
    MATRIX("Matrix", "Slow columns of light"),
    SNOW("Snow", "Fine drifting dots"),
    MAZE("Maze", "A tracing light in a geometric maze"),
}

/** Stable bounded coordinates; no random allocations per rendered frame. */
object BackdropPattern {
    fun seed(index: Int, salt: Int = 0): Float {
        var bits = index * 374761393 + salt * 668265263
        bits = (bits xor (bits ushr 13)) * 1274126177
        return ((bits xor (bits ushr 16)) and 0xFFFF) / 65536f
    }
    fun wrap(value: Float): Float = value - kotlin.math.floor(value)
    fun animate(mode: HomeBackdrop, homeVisible: Boolean, resumed: Boolean, motion: Boolean) =
        mode != HomeBackdrop.NONE && homeVisible && resumed && motion
}
