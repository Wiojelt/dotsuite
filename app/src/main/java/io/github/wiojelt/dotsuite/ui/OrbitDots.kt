package io.github.wiojelt.dotsuite.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.wiojelt.dotsuite.data.FeatureArea
import io.github.wiojelt.dotsuite.data.OrbitPhysics
import io.github.wiojelt.dotsuite.ui.theme.LocalAppearance
import io.github.wiojelt.dotsuite.ui.theme.LocalMotionAllowed
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.*
import kotlin.random.Random

private val orbitAreas = OrbitPhysics.areas

/** Input-driven animation only. Jobs are cancelled on a new gesture, cancel, or leaving the hub. */
@Composable
internal fun OrbitDots(
    onArea: (FeatureArea) -> Unit,
    onOrigin: (Offset, Float) -> Unit,
    active: Boolean,
    onSearch: () -> Unit = {},
) {
    val options = LocalAppearance.current
    val motion = LocalMotionAllowed.current
    val scope = rememberCoroutineScope()
    var rotation by rememberSaveable { mutableFloatStateOf(0f) }
    var spinning by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var landed by remember { mutableStateOf<FeatureArea?>(null) }
    var spinJob by remember { mutableStateOf<Job?>(null) }
    var sizePx by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    val latestArea by rememberUpdatedState(onArea)
    val latestOrigin by rememberUpdatedState(onOrigin)
    val currentMotion by rememberUpdatedState(motion)
    val currentRoulette by rememberUpdatedState(options.roulette)
    val currentActive by rememberUpdatedState(active)
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    val nodes = remember { mutableMapOf<FeatureArea, Pair<Offset, Float>>() }
    fun open(area: FeatureArea) {
        if (!currentActive) return
        nodes[area]?.let { latestOrigin(it.first, it.second) }
        latestArea(area)
    }
    fun stop() { spinJob?.cancel(); spinJob = null; spinning = false; dragging = false; landed = null }
    LaunchedEffect(active, motion, options.roulette) { if (!active || !motion) stop() }
    DisposableEffect(Unit) { onDispose { spinJob?.cancel() } }

    fun launchSpin(speed: Float) {
        spinJob?.cancel()
        landed = null
        dragging = false
        if (!currentActive) return
        if (!currentMotion) {
            if (currentRoulette) open(orbitAreas[Random.nextInt(orbitAreas.size)])
            return
        }
        val boundedSpeed = OrbitPhysics.safeSpeed(speed)
        if (abs(boundedSpeed) < 30f) return
        spinJob = scope.launch {
            spinning = true
            try {
                val startAngle = rotation
                val start = withFrameNanos { it }
                do {
                    val elapsed = withFrameNanos { (it - start) / 1_000_000_000f }
                    rotation = startAngle + OrbitPhysics.travel(boundedSpeed, elapsed)
                } while (currentActive && elapsed < 3.2f && abs(OrbitPhysics.speedAt(boundedSpeed, elapsed)) > 12f)
                if (!currentActive) return@launch
                val chosen = OrbitPhysics.winner(rotation)
                if (currentRoulette) {
                    val alignment = Animatable(rotation)
                    alignment.animateTo(OrbitPhysics.snap(rotation, chosen), tween(280, easing = FastOutSlowInEasing)) { rotation = value }
                    rotation = OrbitPhysics.normalized(rotation)
                    landed = orbitAreas[chosen]
                    // Let blur clear and briefly identify the winner before opening its local page.
                    // Frame-clock driven: cancelled with the spin, including on background/Back.
                    Animatable(0f).animateTo(1f, tween(200))
                    open(orbitAreas[chosen])
                } else rotation = OrbitPhysics.normalized(rotation)
            } finally { spinning = false; landed = null }
        }
    }
    val latestLaunch by rememberUpdatedState<(Float) -> Unit> { launchSpin(it) }
    val latestStop by rememberUpdatedState<() -> Unit> { stop() }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) latestStop()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val blurred = options.roulette && motion && landed == null && (spinning || dragging)
    val blurRadius by animateFloatAsState(if (blurred) 7f else 0f, tween(if (motion) 160 else 0), label = "orbitClarity")
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val field = maxWidth.coerceAtMost(420.dp)
            val diameter = field / 4.9f
            val radius = field * .35f
            val diameterPx = with(density) { diameter.toPx() }
            val radiusPx = with(density) { radius.toPx() }
            Box(
                Modifier.size(field).testTag("category-honeycomb")
                    .onGloballyPositioned {
                        sizePx = Offset(it.size.width.toFloat(), it.size.height.toFloat())
                    }
                    .semantics {
                        contentDescription = if (options.roulette) "Category roulette" else "Rotating category dots"
                        stateDescription = if (spinning || dragging) "Moving" else "Stopped"
                        customActions = listOf(
                            CustomAccessibilityAction("Rotate clockwise") { launchSpin(480f); true },
                            CustomAccessibilityAction("Cancel rotation") { stop(); true },
                        )
                    }
                    .pointerInput(Unit) {
                        val tracker = VelocityTracker()
                        var last = Offset.Zero
                        var unwrapped = 0f
                        detectDragGestures(
                            onDragStart = { point ->
                                latestStop()
                                last = point
                                unwrapped = 0f
                                tracker.resetTracking()
                                dragging = currentActive
                            },
                            onDragCancel = { latestStop() },
                            onDragEnd = {
                                val speed = tracker.calculateVelocity().x
                                dragging = false
                                latestLaunch(speed)
                            },
                        ) { change, amount ->
                            if (!currentActive) return@detectDragGestures
                            val center = sizePx / 2f
                            val a = last - center
                            val b = change.position - center
                            val delta = if (a.getDistance() > sizePx.x * .15f && b.getDistance() > sizePx.x * .15f)
                                OrbitPhysics.shortestDelta(
                                    Math.toDegrees(atan2(a.y, a.x).toDouble()).toFloat(),
                                    Math.toDegrees(atan2(b.y, b.x).toDouble()).toFloat(),
                                )
                            else amount.x / (sizePx.x.coerceAtLeast(1f)) * 360f
                            if (currentMotion) rotation += delta
                            unwrapped += delta
                            tracker.addPosition(change.uptimeMillis, Offset(unwrapped, 0f))
                            last = change.position
                            change.consume()
                        }
                    },
            ) {
                if (options.roulette) Canvas(Modifier.fillMaxSize()) {
                    val x = size.width / 2f
                    drawLine(color, Offset(x, 0f), Offset(x, 9.dp.toPx()), 2.dp.toPx())
                    drawCircle(color, 2.dp.toPx(), Offset(x, 13.dp.toPx()))
                }
                orbitAreas.forEachIndexed { index, area ->
                    val radians = Math.toRadians(OrbitPhysics.angle(index, rotation).toDouble())
                    val dx = cos(radians).toFloat() * radiusPx
                    val dy = sin(radians).toFloat() * radiusPx
                    CategoryDot(area, Modifier.align(Alignment.Center)
                        .offset { IntOffset(dx.roundToInt(), dy.roundToInt()) }
                        .size(diameter)
                        .onGloballyPositioned {
                            nodes[area] = it.positionInRoot() + Offset(it.size.width / 2f, it.size.height / 2f) to diameterPx / 2f
                        }
                        .then(if (blurRadius > .01f) Modifier.blur(blurRadius.dp, edgeTreatment = BlurredEdgeTreatment(CircleShape)) else Modifier),
                        enabled = active && !spinning && !dragging,
                        highlighted = landed == area,
                    ) { open(area) }
                }
                SearchDot(Modifier.size(diameter * 1.06f).align(Alignment.Center), active) {
                    stop()
                    onSearch()
                }
            }
        }
        if (options.roulette) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (spinning || dragging) {
                    Text(landed?.let { if (it == FeatureArea.SUPPORT) "A coffee break?" else it.title } ?: "Choosing a category…", style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { stop() }, modifier = Modifier.testTag("cancel-roulette")) { Text("Cancel") }
                } else {
                    TextButton(onClick = { launchSpin(Random.nextInt(600, 1200).toFloat()) },
                        modifier = Modifier.testTag("spin-roulette")) {
                        Text(if (motion) "Spin the dots" else "Pick a category")
                    }
                }
            }
        } else Text("Flick to explore", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
