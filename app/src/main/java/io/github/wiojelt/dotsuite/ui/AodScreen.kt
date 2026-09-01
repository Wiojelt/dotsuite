package io.github.wiojelt.dotsuite.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.wiojelt.dotsuite.data.AodPolicy as A
import io.github.wiojelt.dotsuite.data.AodSettings
import io.github.wiojelt.dotsuite.data.AodSnapshot
import io.github.wiojelt.dotsuite.data.SystemOptions
import io.github.wiojelt.dotsuite.privileged.PrivilegedManager
import kotlinx.coroutines.launch

internal fun openNativeAod(context: Context): Boolean {
    for (action in listOf("com.android.settings.ACTION_LOCKSCREEN_AND_AOD_SETTINGS",
            "android.settings.AMBIENT_DISPLAY_SETTINGS", Settings.ACTION_DISPLAY_SETTINGS)) {
        if (runCatching { context.startActivity(Intent(action).setPackage("com.android.settings")); true }.getOrDefault(false)) return true
    }
    return false
}

@Composable
internal fun AodScreen(padding: PaddingValues, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val setup by PrivilegedManager.setup.collectAsState()
    var revision by remember { mutableIntStateOf(0) }
    var snapshot by remember { mutableStateOf(AodSnapshot()) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf("") }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) revision++ }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(setup.ready, revision) {
        loading = true
        snapshot = AodSettings.read()
        loading = false
    }
    AodContent(padding, snapshot, loading, setup.ready && !busy && !loading, result, onBack,
        onWrite = { key, value ->
            busy = true
            scope.launch {
                try { result = SystemOptions.write(context, key, value) }
                finally { revision++; busy = false }
            }
        },
        onRestore = {
            busy = true
            scope.launch {
                try { result = SystemOptions.restore(context, A.KEYS) }
                finally { revision++; busy = false }
            }
        },
        onNative = { if (!openNativeAod(context)) result = "Native display settings could not be opened." },
        onRefresh = { revision++ })
}

/** Pure view for permission-denied and unsupported-device UI tests; never mutates settings itself. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AodContent(
    padding: PaddingValues, state: AodSnapshot, loading: Boolean, canEdit: Boolean, result: String,
    onBack: () -> Unit, onWrite: (String, String) -> Unit, onRestore: () -> Unit,
    onNative: () -> Unit, onRefresh: () -> Unit,
) {
    fun ready(key: String) = canEdit && state.available && state.error == null && key in state.writable
    LazyColumn(Modifier.fillMaxSize().testTag("aod-page"),
        contentPadding = PaddingValues(20.dp, padding.calculateTopPadding() + 8.dp, 20.dp, padding.calculateBottomPadding() + 28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { PageBackButton(onBack) }
        item { ScreenHeader("Always-on display") }
        item { FeatureRequirementNote("aod") }
        item { Text("Your Nothing clock and widgets. No replacement screen or background sensor service.",
            style = MaterialTheme.typography.bodyLarge) }
        item { SoftGroup {
            ToggleRow("Native AOD", "Use the system always-on display. No brightness or battery-saver override.",
                state.enabled, ready(A.ENABLED)) { onWrite(A.ENABLED, if (it) "1" else "0") }
        } }
        if (state.nothing) {
            item { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel("When to show")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf("All day", "Schedule", "Tap").forEachIndexed { mode, label ->
                        SegmentedButton(selected = state.mode == mode,
                            enabled = ready(A.MODE) && A.canUseMode(mode, state.allDay, state.schedule, state.tapMode),
                            onClick = { onWrite(A.MODE, mode.toString()) },
                            shape = SegmentedButtonDefaults.itemShape(mode, 3)) { Text(label) }
                    }
                }
                Text("Unavailable modes stay disabled. A selection never enables AOD or another wake gesture automatically.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } }
            item { SoftGroup { ActionRow("Native schedule & clock", "Configure times and appearance in Nothing Settings") { onNative() } } }
        }
        item { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel("Wake behaviour")
            SoftGroup {
                listOf(
                    Triple(A.NOTIFICATIONS, "Notification pulse", "Let eligible notifications wake the ambient screen. DND, channels and privacy remain unchanged."),
                    Triple(A.TAP, "Tap to wake", "The existing system gesture; DotSuite adds no touch listener."),
                    Triple(A.DOUBLE_TAP, "Double tap to wake", "Available only when the system exposes a double-tap sensor."),
                    Triple(A.LIFT, "Lift to wake", "The existing Nothing lift gesture; no extra sensor polling."),
                ).forEachIndexed { index, (key, title, detail) ->
                    if (index > 0) SoftDivider()
                    ToggleRow(title, if (key in state.writable) detail else "Unavailable or not yet verified on this device.",
                        key in state.writable && A.enabled(state.values[key], true), ready(key)) { onWrite(key, if (it) "1" else "0") }
                }
            }
        } }
        item { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel("System status")
            Text(if (loading) "Reading native support…" else state.status(), modifier = Modifier.testTag("aod-status"),
                style = MaterialTheme.typography.bodyMedium)
            Text("Checked when opened or refreshed. AOD and StandBy are different: Android controls which one is shown.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onRefresh, enabled = !loading) { Text("Refresh status") }
            if (!state.nothing) OutlinedButton(onClick = onNative) { Text("Open native display settings") }
        } }
        item { TextButton(onClick = onRestore, enabled = canEdit) { Text("Restore my original AOD settings") } }
        if (result.isNotBlank()) item { Text(result, modifier = Modifier.testTag("aod-result"), color = MaterialTheme.colorScheme.primary) }
    }
}
