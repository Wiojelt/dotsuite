package io.github.wiojelt.dotsuite.dock

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.*
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import io.github.wiojelt.dotsuite.MainActivity
import io.github.wiojelt.dotsuite.data.FeatureJournal
import io.github.wiojelt.dotsuite.ui.theme.DotSuiteTheme
import io.github.wiojelt.dotsuite.ui.theme.LocalMotionAllowed
import io.github.wiojelt.dotsuite.ui.theme.LocalTouchSound
import io.github.wiojelt.dotsuite.ui.theme.TouchCue
import io.github.wiojelt.dotsuite.diagnostics.RecentDiagnostics
import io.github.wiojelt.dotsuite.data.DockStyle
import io.github.wiojelt.dotsuite.data.DockMotion
import io.github.wiojelt.dotsuite.data.DockGeometry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

object DockPreferences {
    fun prefs(context: Context) = context.getSharedPreferences("quick-dock", Context.MODE_PRIVATE)
    fun packages(context: Context) = prefs(context).getString("apps", "").orEmpty().split(';').filter { it.isNotBlank() }.take(8)
    fun save(context: Context, apps: List<String>) {
        prefs(context).edit().putString("apps", apps.distinct().take(8).joinToString(";")).apply()
        FeatureJournal.record(context, "dock.apps", "count=${apps.distinct().take(8).size}")
    }
    fun setLeft(context: Context, left: Boolean) {
        prefs(context).edit().putBoolean("left", left).apply()
        FeatureJournal.record(context, "dock.side", if (left) "left" else "right")
    }
    fun style(context: Context): DockStyle = prefs(context).let { p -> DockStyle(
        p.getInt("size", 100), p.getInt("position", 50), p.getInt("opacity", 92), p.getInt("inset", 0),
        p.getInt("visible", 5), p.getInt("dim", 0), DockMotion.entries.firstOrNull { it.name == p.getString("motion", "SLIDE") } ?: DockMotion.SLIDE,
    ).bounded() }
    fun style(context: Context, value: DockStyle) {
        val s = value.bounded()
        prefs(context).edit().putInt("size", s.sizePercent).putInt("position", s.positionPercent)
            .putInt("opacity", s.opacityPercent).putInt("inset", s.edgeInsetDp).putInt("visible", s.visibleApps)
            .putInt("dim", s.dimPercent).putString("motion", s.motion.name).apply()
        FeatureJournal.record(context, "dock.appearance", "saved")
    }
}

/** A transient side window opened explicitly by a tile/notch; no edge listener or overlay service. */
class QuickDockActivity : ComponentActivity() {
    private var expanded = false
    private var itemCount = 1
    private val left get() = DockPreferences.prefs(this).getBoolean("left", false)
    private val style by lazy { DockPreferences.style(this) }
    private fun sizeWindow() {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            runOnUiThread { sizeWindow() }
            return
        }
        if (isFinishing || isDestroyed) return
        val metrics = windowManager.currentWindowMetrics
        val types = android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.displayCutout()
        val insets = metrics.windowInsets.getInsetsIgnoringVisibility(types)
        val frame = DockGeometry.frame(metrics.bounds.width() - insets.left - insets.right,
            metrics.bounds.height() - insets.top - insets.bottom, resources.displayMetrics.density, itemCount, expanded, style)
        window.attributes = window.attributes.apply {
            gravity = (if (left) Gravity.LEFT else Gravity.RIGHT) or Gravity.TOP
            x = frame.x; y = frame.y; width = frame.width; height = frame.height
            setFitInsetsTypes(types)
            setFitInsetsIgnoringVisibility(true)
        }
        window.setDimAmount(style.dimPercent / 100f)
        window.setWindowAnimations(0)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (getSystemService(KeyguardManager::class.java).isKeyguardLocked) { finish(); return }
        setFinishOnTouchOutside(true)
        setContent {
            DotSuiteTheme {
                var apps by remember { mutableStateOf<List<Triple<String, String, android.graphics.Bitmap>>>(emptyList()) }
                var loading by remember { mutableStateOf(true) }
                var labels by remember { mutableStateOf(false) }
                var closing by remember { mutableStateOf(false) }
                val motion = LocalMotionAllowed.current
                val sound = LocalTouchSound.current
                val shift = remember { Animatable(1f) }
                val scope = rememberCoroutineScope()
                val animate = motion && style.motion != DockMotion.NONE
                fun dismiss(after: () -> Unit = {}) {
                    if (closing) return
                    closing = true
                    scope.launch {
                        shift.animateTo(1f, tween(if (animate) 160 else 0))
                        after()
                        finish()
                    }
                }
                BackHandler { dismiss() }
                LaunchedEffect(Unit) {
                    if (animate && style.motion == DockMotion.SOFT) shift.animateTo(0f,
                        androidx.compose.animation.core.spring(dampingRatio = .88f, stiffness = 420f))
                    else shift.animateTo(0f, tween(if (animate) 240 else 0))
                }
                LaunchedEffect(Unit) {
                    apps = withContext(Dispatchers.IO) {
                        DockPreferences.packages(this@QuickDockActivity).mapNotNull { pkg ->
                            runCatching {
                                if (packageManager.getLaunchIntentForPackage(pkg) == null) return@runCatching null
                                Triple(pkg, packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString(),
                                    packageManager.getApplicationIcon(pkg).toBitmap(96, 96))
                            }.onFailure { RecentDiagnostics.failure("dock.icon", it) }.getOrNull()
                        }
                    }
                    loading = false
                    itemCount = apps.size
                    sizeWindow()
                }
                DockRail(apps, left, labels, loading, !closing,
                    Modifier.fillMaxSize().graphicsLayer {
                        if (style.motion == DockMotion.FADE) alpha = (1f - shift.value).coerceIn(0f, 1f)
                        else translationX = size.width * shift.value * if (left) -1f else 1f
                    }, style = style,
                    onClose = { sound(TouchCue.BACK); dismiss() },
                    onLabels = { sound(TouchCue.TAP); labels = !labels; expanded = labels; sizeWindow() },
                    onEdit = { sound(TouchCue.OPEN); dismiss {
                        startActivity(Intent(this@QuickDockActivity, MainActivity::class.java).putExtra("feature_page", "dock"))
                    } },
                    onLaunch = { pkg ->
                        sound(TouchCue.OPEN)
                        dismiss {
                            if (!getSystemService(KeyguardManager::class.java).isKeyguardLocked) {
                                runCatching { packageManager.getLaunchIntentForPackage(pkg)?.let(::startActivity) }
                                    .onFailure { RecentDiagnostics.failure("dock.launch", it) }
                            }
                        }
                    })
            }
        }
    }
    override fun onResume() {
        super.onResume()
        // setContent installs match-parent parameters; apply the bounded rail afterwards.
        sizeWindow()
    }
    override fun onPause() { super.onPause(); if (!isChangingConfigurations) finish() }
}

/** Original curved, bezel-joined silhouette; mirror geometry, not text, on the left. */
private class BezelShape(private val left: Boolean) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val s = with(density) { 12.dp.toPx() }.coerceAtMost(size.height / 5)
        fun x(value: Float) = if (left) value else size.width - value
        return Outline.Generic(Path().apply {
            moveTo(x(0f), 0f)
            cubicTo(x(0f), s, x(size.width), 0f, x(size.width), s * 2)
            lineTo(x(size.width), size.height - s * 2)
            cubicTo(x(size.width), size.height, x(0f), size.height - s, x(0f), size.height)
            close()
        })
    }
}

@Composable
internal fun DockRail(apps: List<Triple<String, String, android.graphics.Bitmap>>, left: Boolean, labels: Boolean,
    loading: Boolean, enabled: Boolean, modifier: Modifier = Modifier, onClose: () -> Unit, onLabels: () -> Unit,
    onEdit: () -> Unit, onLaunch: (String) -> Unit, style: DockStyle = DockStyle()) {
    val ink = Color(0xFFF2F3F4)
    Surface(modifier.testTag("dock-rail"), shape = remember(left, style.edgeInsetDp) {
        if (style.edgeInsetDp > 0) androidx.compose.foundation.shape.RoundedCornerShape(24.dp) else BezelShape(left)
    }, color = Color(0xFF090A0C).copy(alpha = style.bounded().opacityPercent / 100f), contentColor = ink,
        border = androidx.compose.foundation.BorderStroke(.5.dp, Color.White.copy(alpha = .10f))) {
        Column(Modifier.padding(horizontal = 6.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (labels) TextButton(onClick = onClose, enabled = enabled, modifier = Modifier.height(44.dp).semantics { contentDescription = "Close dock" }) {
                Text("Close", color = ink)
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                if (loading) item { Box(Modifier.height(56.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } }
                else if (apps.isEmpty()) item {
                    TextButton(onClick = onEdit, enabled = enabled, modifier = Modifier.height(style.rowDp.dp).semantics { contentDescription = "Choose dock apps" }) {
                        Text(if (labels) "Choose apps" else "+", color = ink)
                    }
                }
                items(apps, key = { it.first }) { app ->
                    Row(Modifier.fillMaxWidth().height(style.rowDp.dp).clip(CircleShape)
                        .clickable(enabled = enabled, onClickLabel = "Open ${app.second}") { onLaunch(app.first) }
                        .semantics { contentDescription = app.second }.padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = if (labels) Arrangement.Start else Arrangement.Center) {
                        Image(app.third.asImageBitmap(), null, Modifier.size(style.iconDp.dp))
                        if (labels) Text(app.second, Modifier.padding(start = 12.dp), maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            HorizontalDivider(Modifier.padding(horizontal = 14.dp, vertical = 4.dp), color = Color.White.copy(alpha = .12f))
            TextButton(onClick = onLabels, enabled = enabled, contentPadding = PaddingValues(0.dp), modifier = Modifier.height(48.dp).semantics {
                contentDescription = if (labels) "Hide app labels" else "Show app labels"
            }) { Text(if (labels) "Collapse" else "•••", color = ink) }
            if (labels) TextButton(onClick = onEdit, enabled = enabled, modifier = Modifier.height(44.dp)) { Text("Edit shortcuts", color = ink) }
        }
    }
}
