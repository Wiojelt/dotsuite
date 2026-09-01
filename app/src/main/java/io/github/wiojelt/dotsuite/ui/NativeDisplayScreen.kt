package io.github.wiojelt.dotsuite.ui

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.wiojelt.dotsuite.data.FeatureCatalog
import io.github.wiojelt.dotsuite.data.PersonalizationPolicy as P
import io.github.wiojelt.dotsuite.data.SystemOptions
import io.github.wiojelt.dotsuite.privileged.PrivilegedManager
import kotlinx.coroutines.launch

/** Small, allow-listed native preferences. Every write uses the original-value journal. */
@Composable
fun NativeDisplayScreen(page: String, padding: PaddingValues, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val setup by PrivilegedManager.setup.collectAsState()
    var revision by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) revision++ }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val keys = when (page) {
        "clock" -> listOf(P.CLOCK_SECONDS)
        "clock-style" -> listOf(P.CLOCK_DAY)
        "navigation" -> listOf(P.HIDE_NAV_PILL)
        else -> listOf(P.USER_ROTATION, P.AUTO_ROTATE)
    }
    val values = remember(page, revision) { keys.associateWith { runCatching { SystemOptions.read(context, it) }.getOrNull() } }
    val needsModule = page == "clock-style" || page == "navigation"
    val supported = !needsModule || (Build.VERSION.SDK_INT == 36 && Build.DEVICE == "Asteroids")
    val enabled = setup.ready && supported && !busy
    fun write(key: String, value: String) {
        if (!enabled) return
        busy = true
        scope.launch {
            try { status = SystemOptions.write(context, key, value) }
            finally { revision++; busy = false }
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp,
        top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { PageBackButton(onBack) }
        item { ScreenHeader(FeatureCatalog.entries.first { it.page == page }.title) }
        item { FeatureRequirementNote(page) }
        if (needsModule) item {
            Text(if (supported) "Experimental · requires this build loaded in SystemUI. Saving a preference does not confirm the module is active."
                else "Native hooks are restricted to Nothing Phone (3a) Pro / Android 16.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        when (page) {
            "clock" -> {
                item { SoftGroup { ToggleRow("Show seconds", "Use the system clock's own second updates.",
                    values[P.CLOCK_SECONDS] == "1", enabled) { write(P.CLOCK_SECONDS, if (it) "1" else "0") } } }
                item { Text("Nothing OS must support Android's clock_seconds tuner. The app does not run a background clock or replace the status bar. More frequent native updates may use a little more battery.", style = MaterialTheme.typography.bodyMedium) }
            }
            "clock-style" -> {
                item { SoftGroup { ToggleRow("Show weekday", "Add a short, localised day before the native time.",
                    values[P.CLOCK_DAY] == "1", enabled) { write(P.CLOCK_DAY, if (it) "1" else "0") } } }
                item { Text("Applied on the next native clock refresh. The original 12 / 24-hour formatting and accessibility label stay intact. Can be crowded with many status icons.", style = MaterialTheme.typography.bodyMedium) }
            }
            "navigation" -> {
                item { SoftGroup { ToggleRow("Hide gesture line", "Suppress only the pill's drawing, not its touch area.",
                    values[P.HIDE_NAV_PILL] == "1", enabled) { write(P.HIDE_NAV_PILL, if (it) "1" else "0") } } }
                item { Text("Home, back, app switching and long-press gestures remain owned by Android. Three-button navigation and keyboard insets are unchanged. If Nothing OS uses a different handle class, the stock line is kept.", style = MaterialTheme.typography.bodyMedium) }
            }
            else -> {
                item { SoftGroup { ToggleRow("Auto rotate", "Follow the device sensor using Android's own policy.",
                    values[P.AUTO_ROTATE] == "1", enabled) { write(P.AUTO_ROTATE, if (it) "1" else "0") } } }
                item { Text("Turn auto rotate off to choose a fixed direction. Apps with a fixed orientation and some phone builds may ignore reverse portrait; this does not override their rotation policy.", style = MaterialTheme.typography.bodyMedium) }
                item { SoftGroup {
                    listOf("Portrait · 0°", "Landscape · 90°", "Reverse portrait · 180°", "Landscape · 270°").forEachIndexed { index, label ->
                        if (index > 0) SoftDivider()
                        Surface(onClick = { write(P.USER_ROTATION, index.toString()) },
                            enabled = enabled && values[P.AUTO_ROTATE] != "1", color = androidx.compose.ui.graphics.Color.Transparent) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                RadioButton(selected = (values[P.USER_ROTATION]?.toIntOrNull() ?: 0) == index,
                                    onClick = null, enabled = enabled && values[P.AUTO_ROTATE] != "1")
                                Text(label, Modifier.padding(start = 12.dp))
                            }
                        }
                    }
                } }
            }
        }
        item { TextButton(enabled = enabled, onClick = {
            busy = true
            scope.launch {
                try { status = SystemOptions.restore(context, keys) }
                finally { revision++; busy = false }
            }
        }) { Text("Restore my original settings") } }
        if (status.isNotEmpty()) item { Text(status, color = MaterialTheme.colorScheme.primary) }
        if (!setup.ready) item { Text("Connect Shizuku / Sui in Settings to make changes.", style = MaterialTheme.typography.bodySmall) }
    }
}
