package io.github.wiojelt.dotsuite.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntSize
import io.github.wiojelt.dotsuite.data.FeatureArea
import io.github.wiojelt.dotsuite.data.FeatureCatalog
import io.github.wiojelt.dotsuite.data.MixPrefs
import io.github.wiojelt.dotsuite.ui.theme.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlin.math.hypot
import kotlin.math.max

/** A circular reveal from the selected dot; navigation never enables the selected tools. */
@Composable
fun MainScreen(prefs: MixPrefs, initialFeature: String? = null, navigationRequest: Int = 0, onRunSetup: () -> Unit) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val appearance = LocalAppearance.current
    val motion = LocalMotionAllowed.current
    val sound = LocalTouchSound.current
    var selectedArea by rememberSaveable { mutableStateOf(initialFeature?.let { FeatureCatalog.areaFor(it).name }) }
    var requestedPage by rememberSaveable { mutableStateOf(initialFeature ?: "home") }
    var localRequest by rememberSaveable { mutableIntStateOf(0) }
    var lastExternalRequest by rememberSaveable { mutableIntStateOf(navigationRequest) }
    var portalX by rememberSaveable { mutableFloatStateOf(-1f) }
    var portalY by rememberSaveable { mutableFloatStateOf(-1f) }
    var startRadius by rememberSaveable { mutableFloatStateOf(42f) }
    var closing by remember { mutableStateOf(false) }
    var closeJob by remember { mutableStateOf<Job?>(null) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val reveal = remember { Animatable(if (initialFeature == null) 0f else 1f) }
    val areaState = rememberSaveableStateHolder()
    val portalEasing = remember { CubicBezierEasing(.2f, 0f, 0f, 1f) }

    fun open(area: FeatureArea, page: String = "home") {
        io.github.wiojelt.dotsuite.diagnostics.RecentDiagnostics.record("category", area.name)
        sound(TouchCue.OPEN)
        closeJob?.cancel()
        closing = false
        focus.clearFocus()
        requestedPage = page
        localRequest++
        selectedArea = area.name
    }
    fun close() {
        if (closing) return
        io.github.wiojelt.dotsuite.diagnostics.RecentDiagnostics.record("category", "back to hub")
        sound(TouchCue.BACK)
        closing = true
        focus.clearFocus()
        closeJob = scope.launch {
            try {
                reveal.animateTo(0f, tween(if (motion) appearance.transitionMs else 0, easing = FastOutSlowInEasing))
                selectedArea = null
            } finally { closing = false }
        }
    }
    LaunchedEffect(selectedArea, localRequest) {
        if (selectedArea != null) reveal.animateTo(1f, tween(if (motion) appearance.transitionMs else 0, easing = portalEasing))
    }
    LaunchedEffect(navigationRequest) {
        if (lastExternalRequest != navigationRequest) {
            if (initialFeature != null) open(FeatureCatalog.areaFor(initialFeature), initialFeature)
            lastExternalRequest = navigationRequest
        }
    }

    Box(Modifier.fillMaxSize().onSizeChanged { viewport = it }) {
        AppBackdrop()
        HomeBackdropEffect(visible = selectedArea == null)
        Scaffold(containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.onBackground,
            snackbarHost = { SnackbarHost(snackbar) }) { padding ->
            val center = Offset(
                portalX.takeIf { it >= 0 } ?: viewport.width / 2f,
                portalY.takeIf { it >= 0 } ?: viewport.height / 2f,
            )
            val farX = max(center.x, viewport.width - center.x)
            val farY = max(center.y, viewport.height - center.y)
            val maxRadius = hypot(farX, farY) + 2f
            Box(Modifier.fillMaxSize().graphicsLayer {
                alpha = 1f - reveal.value * .55f
                scaleX = 1f - reveal.value * .055f
                scaleY = scaleX
                transformOrigin = TransformOrigin(
                    (center.x / viewport.width.coerceAtLeast(1)).coerceIn(0f, 1f),
                    (center.y / viewport.height.coerceAtLeast(1)).coerceIn(0f, 1f),
                )
            }.then(if (selectedArea != null) Modifier.clearAndSetSemantics { } else Modifier)) {
                areaState.SaveableStateProvider("hub") {
                    FeatureHubScreen(
                        contentPadding = padding, active = selectedArea == null,
                        onArea = { if (selectedArea == null) open(it) },
                        onFeature = {
                            if (selectedArea == null) {
                                portalX = viewport.width / 2f; portalY = viewport.height / 2f; startRadius = 24f
                                open(FeatureCatalog.areaFor(it), it)
                            }
                        },
                        onOrigin = { point, radius -> portalX = point.x; portalY = point.y; startRadius = radius },
                    )
                }
            }
            selectedArea?.let { name ->
                Box(Modifier.fillMaxSize().drawWithContent {
                    val radius = startRadius + (maxRadius - startRadius) * reveal.value
                    val path = Path().apply { addOval(androidx.compose.ui.geometry.Rect(center, radius)) }
                    clipPath(path) { this@drawWithContent.drawContent() }
                }) {
                    AppBackdrop()
                    Box(Modifier.fillMaxSize().graphicsLayer {
                        val p = reveal.value
                        alpha = ((p - .08f) / .82f).coerceIn(0f, 1f)
                        scaleX = .94f + .06f * p
                        scaleY = scaleX
                        transformOrigin = TransformOrigin(
                            (center.x / viewport.width.coerceAtLeast(1)).coerceIn(0f, 1f),
                            (center.y / viewport.height.coerceAtLeast(1)).coerceIn(0f, 1f),
                        )
                    }) {
                        areaState.SaveableStateProvider(name) {
                            if (name == FeatureArea.SUPPORT.name) {
                                SupportScreen(padding, onBack = { close() })
                            } else SettingsScreen(
                                contentPadding = padding, onRunSetup = onRunSetup,
                                initialPage = requestedPage, navigationRequest = localRequest,
                                area = FeatureArea.valueOf(name), prefs = prefs, snackbar = snackbar,
                                onBackToHub = { close() },
                                onOpenSettings = { open(FeatureArea.SETTINGS) },
                            )
                        }
                    }
                }
                if (motion && reveal.value > 0f && reveal.value < 1f) Canvas(Modifier.fillMaxSize()) {
                    val radius = startRadius + (maxRadius - startRadius) * reveal.value
                    val rim = if (name == FeatureArea.SUPPORT.name) CoffeeAccent else Color.White
                    drawCircle(rim.copy(alpha = .2f * kotlin.math.sin(reveal.value * Math.PI).toFloat()), radius, center,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
                }
            }
        }
    }
}
