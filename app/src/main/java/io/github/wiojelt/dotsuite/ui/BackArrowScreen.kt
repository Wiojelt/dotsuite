package io.github.wiojelt.dotsuite.ui

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.wiojelt.dotsuite.data.BackArrowPolicy as B
import io.github.wiojelt.dotsuite.data.SystemOptions
import io.github.wiojelt.dotsuite.drawing.BackArrowRenderer
import io.github.wiojelt.dotsuite.privileged.PrivilegedManager
import io.github.wiojelt.dotsuite.ui.theme.LocalMotionAllowed
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

internal val backArrowStyles = listOf("Stock", "Chevron", "Arrow", "Double", "Pixels", "Beads", "Curved", "Triangle", "Ring", "Bracket",
    "Hairline", "Capsule", "Kite", "Orbit", "Notched", "Triple")
internal val backArrowMotions = listOf("Native", "Grow", "Unfold", "Soft pulse", "Elastic", "Draw in", "Weight", "Tilt",
    "Glide", "Rise", "Compress", "Turn")

@Composable
internal fun BackArrowScreen(padding: PaddingValues, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val setup by PrivilegedManager.setup.collectAsState()
    val scope = rememberCoroutineScope()
    var revision by remember { mutableIntStateOf(0) }
    var loaded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }
    val supported = Build.VERSION.SDK_INT == 36 && Build.DEVICE == "Asteroids"
    val ready = supported && setup.ready && loaded && !busy
    var enabled by remember { mutableStateOf(false) }
    var style by androidx.compose.runtime.saveable.rememberSaveable { mutableIntStateOf(0) }
    var motion by androidx.compose.runtime.saveable.rememberSaveable { mutableIntStateOf(0) }
    var size by androidx.compose.runtime.saveable.rememberSaveable { mutableFloatStateOf(100f) }
    var edited by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(revision) {
        val values = withContext(Dispatchers.IO) { B.KEYS.associateWith { key ->
            runCatching { SystemOptions.read(context, key) }.getOrNull()
        } }
        enabled = values[B.ENABLED] == "1"
        if (!edited) {
            style = (values[B.STYLE]?.toIntOrNull() ?: 0).coerceIn(0, 15)
            motion = (values[B.MOTION]?.toIntOrNull() ?: 0).coerceIn(0, 11)
            size = (values[B.SIZE]?.toFloatOrNull() ?: 100f).takeIf { it.isFinite() }?.coerceIn(80f, 120f) ?: 100f
        }
        loaded = true
    }
    fun save(key: String, value: String?) {
        if (!ready) return
        busy = true
        scope.launch { try { message = SystemOptions.write(context, key, value); revision++ } finally { busy = false } }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp,
        top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { PageBackButton(onBack) }
        item { ScreenHeader("System back arrow") }
        item { FeatureRequirementNote("back-arrow") }
        item { Text("Nothing Phone (3a) Pro · Android 16. Load this version in Vector / LSPosed before enabling.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { SoftGroup { ToggleRow("Custom back arrow", "The system edge-swipe indicator, not app toolbar arrows.",
            enabled, ready) { if (it) confirm = true else save(B.ENABLED, "0") } } }
        item { BackArrowPreview(style, motion, size.roundToInt()) }
        item { Text("Preview a shape and motion, then apply. After a module update, use Settings → Restore changes → Reload SystemUI if the arrow stays stock.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { Button(enabled = ready, modifier = Modifier.fillMaxWidth(), onClick = {
            val selected = listOf(B.STYLE to style.toString(), B.MOTION to motion.toString(), B.SIZE to size.roundToInt().toString())
            busy = true
            scope.launch {
                try {
                    val results = selected.map { (key, value) -> SystemOptions.write(context, key, value) }
                    message = if (results.all { it == "Saved" })
                        "Saved. Changes appear on the next gesture when the module is loaded."
                        else "Some settings were not saved. Check the bridge and retry; previews are unchanged."
                    revision++
                } finally { busy = false }
            }
        }) { Text(if (busy) "Saving settings…" else "Apply preview to system") } }
        if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (message.isNotEmpty()) item { Text(message, style = MaterialTheme.typography.bodySmall) }
        item { SectionLabel("Shape") }
        item { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            backArrowStyles.chunked(2).forEachIndexed { row, titles ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { titles.forEachIndexed { col, title ->
                    val id = row * 2 + col
                    FilterChip(selected = style == id, onClick = { edited = true; style = id },
                        leadingIcon = { BackArrowGlyph(id) }, label = { Text(title) },
                        modifier = Modifier.weight(1f).testTag("arrow-style-$id"))
                } }
            }
        } }
        item { SectionLabel("Gesture-following motion") }
        item { SoftGroup {
            backArrowMotions.forEachIndexed { id, title ->
                if (id > 0) SoftDivider()
                Row(Modifier.fillMaxWidth().selectable(motion == id, role = Role.RadioButton,
                    enabled = style != 0, onClick = { edited = true; motion = id })
                    .padding(horizontal = 18.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, Modifier.weight(1f)); RadioButton(motion == id, onClick = null, enabled = style != 0)
                }
            }
            SoftDivider()
            DiscreteSliderSetting("Icon size", "Stock ignores size and motion. Back sensitivity stays unchanged.",
                "${size.roundToInt()}%", size, 80f..120f, 39, style != 0, { edited = true; size = it.roundToInt().toFloat() }, {})
        } }
        item { Text("Both edges keep the system's direction and contrast. Motion follows your swipe and respects Android's animation setting.",
            style = MaterialTheme.typography.bodySmall) }
        if (!ready) item { Text("Preview only until a supported phone and authorised Shizuku / Sui are available. The native drawing change also requires the root module.", style = MaterialTheme.typography.bodySmall) }
        item { TextButton(enabled = ready, onClick = {
            busy = true
            scope.launch { try { message = SystemOptions.restore(context, B.KEYS.toList()); edited = false; revision++ } finally { busy = false } }
        }) { Text("Restore original arrow settings") } }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Enable experimental native drawing?") },
        text = { Text("Only enable after this build has been loaded by Vector / LSPosed. DotSuite will not restart SystemUI. Switch this off to restore stock drawing on the next gesture.") },
        confirmButton = { TextButton(onClick = { confirm = false; save(B.ENABLED, "1") }) { Text("Enable") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } })
}

@Composable
private fun BackArrowGlyph(style: Int) {
    val path = remember { android.graphics.Path() }
    val paint = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG) }
    val ink = LocalContentColor.current
    Canvas(Modifier.size(24.dp)) {
        val x = 9.dp.toPx(); val y = 8.dp.toPx(); val w = 1.3.dp.toPx()
        if (style == 0) {
            path.rewind()
            for (i in -3..3) path.addCircle(x * kotlin.math.abs(i) / 3, y * i / 3, w, android.graphics.Path.Direction.CW)
        } else BackArrowRenderer.draw(path, style, 0, 100, x, y, w, x)
        paint.color = ink.toArgb()
        drawIntoCanvas {
            it.nativeCanvas.save()
            it.nativeCanvas.translate(7.dp.toPx(), size.height / 2)
            it.nativeCanvas.drawPath(path, paint)
            it.nativeCanvas.restore()
        }
    }
}

@Composable
internal fun BackArrowPreview(style: Int, motion: Int, iconSize: Int) {
    val progress = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val allowed = LocalMotionAllowed.current
    val path = remember { android.graphics.Path() }
    val paint = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG) }
    val ink = MaterialTheme.colorScheme.onSurface
    SoftGroup {
        Canvas(Modifier.fillMaxWidth().height(100.dp).testTag("arrow-preview")) {
            val p = progress.value
            val x = 14.dp.toPx() * p
            val y = 12.dp.toPx() * p
            val stroke = 2.dp.toPx()
            paint.color = ink.toArgb()
            paint.style = android.graphics.Paint.Style.FILL
            if (style == 0) {
                path.rewind()
                for (i in -3..3) path.addCircle(x * kotlin.math.abs(i) / 3, y * i / 3, stroke, android.graphics.Path.Direction.CW)
            } else BackArrowRenderer.draw(path, style, if (allowed) motion else 0, iconSize, x, y, stroke, 14.dp.toPx())
            drawIntoCanvas {
                val canvas = it.nativeCanvas
                for (side in 0..1) {
                    canvas.save()
                    canvas.translate(if (side == 0) 40.dp.toPx() else size.width - 40.dp.toPx(), size.height / 2)
                    if (side == 1) canvas.scale(-1f, 1f)
                    canvas.drawPath(path, paint)
                    canvas.restore()
                }
            }
        }
        TextButton(enabled = allowed && !progress.isRunning, onClick = { scope.launch {
            progress.snapTo(0f); progress.animateTo(1f, tween(650)); progress.animateTo(0f, tween(300)); progress.snapTo(1f)
        } }, modifier = Modifier.fillMaxWidth()) { Text("Preview swipe · no system changes") }
    }
}
