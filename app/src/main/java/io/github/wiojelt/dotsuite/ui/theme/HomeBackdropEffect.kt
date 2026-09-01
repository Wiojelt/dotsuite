package io.github.wiojelt.dotsuite.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import io.github.wiojelt.dotsuite.data.BackdropPattern
import io.github.wiojelt.dotsuite.data.HomeBackdrop
import kotlinx.coroutines.delay
import kotlin.math.sin

/** Original app-only patterns. No wallpaper capture, shader, blur buffer or offscreen worker. */
@Composable
internal fun HomeBackdropEffect(visible: Boolean, modifier: Modifier = Modifier) {
    val mode = LocalAppearance.current.backdrop
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val animate = BackdropPattern.animate(mode, visible && LocalWindowInfo.current.isWindowFocused,
        lifecycle.isAtLeast(Lifecycle.State.RESUMED), LocalMotionAllowed.current)
    val seconds = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(animate, mode) {
        if (animate) {
            var previous = 0L
            while (true) {
                withFrameNanos { now ->
                    if (previous != 0L) seconds.floatValue += ((now - previous) / 1e9f).coerceAtMost(.1f)
                    previous = now
                }
                // Decoration is deliberately capped below 30 fps, not the display refresh rate.
                delay(40)
            }
        }
    }
    if (mode == HomeBackdrop.NONE || !visible) return
    Canvas(modifier.fillMaxSize().testTag("backdrop-${mode.name}")) {
        // Read in the draw phase: animation never recomposes the feature list/orbit.
        val t = seconds.floatValue
        val ink = Color(0xFFCBD5D9)
        when (mode) {
            HomeBackdrop.SNOW -> repeat(54) { i ->
                val speed = .013f + BackdropPattern.seed(i, 3) * .015f
                val x = BackdropPattern.wrap(BackdropPattern.seed(i, 1) + sin(t * .17f + i) * .028f) * size.width
                val y = BackdropPattern.wrap(BackdropPattern.seed(i, 2) + t * speed) * size.height
                drawCircle(ink.copy(alpha = .12f + BackdropPattern.seed(i, 4) * .22f),
                    (1f + BackdropPattern.seed(i, 5) * 1.4f).dp.toPx(), Offset(x, y))
            }
            HomeBackdrop.MATRIX -> {
                val columns = 18
                val cell = size.width / columns
                repeat(columns) { x ->
                    val head = BackdropPattern.wrap(BackdropPattern.seed(x, 8) + t * (.03f + BackdropPattern.seed(x, 7) * .016f)) * (size.height + cell * 7)
                    repeat(7) { trail ->
                        val y = head - trail * cell - cell * 4
                        if (y in 0f..size.height) {
                            val alpha = (.32f - trail * .04f).coerceAtLeast(.025f)
                            val cx = (x + .5f) * cell
                            // Tiny matrix glyphs made from dots, not text rasterization each frame.
                            repeat(6) { bit -> if (BackdropPattern.seed(x * 42 + trail * 6 + bit, 10) > .38f)
                                drawCircle(ink.copy(alpha = alpha), .9.dp.toPx(), Offset(cx + (bit % 2) * 3.dp.toPx(), y + (bit / 2) * 3.dp.toPx())) }
                        }
                    }
                }
            }
            HomeBackdrop.MAZE -> {
                val cell = 38.dp.toPx()
                val cols = (size.width / cell).toInt() + 1
                val rows = (size.height / cell).toInt() + 1
                val sweep = BackdropPattern.wrap(t / 18f)
                repeat(cols * rows) { i ->
                    val x = i % cols * cell
                    val y = i / cols * cell
                    val diagonal = BackdropPattern.seed(i, 22) > .5f
                    val distance = kotlin.math.abs(y / size.height - sweep)
                    val alpha = if (distance < .075f) .10f + (.075f - distance) * 1.5f else .065f
                    drawLine(ink.copy(alpha = alpha), Offset(x, y + if (diagonal) cell else 0f),
                        Offset(x + cell, y + if (diagonal) 0f else cell), .85.dp.toPx(), StrokeCap.Round)
                }
            }
            HomeBackdrop.NONE -> Unit
        }
    }
}
