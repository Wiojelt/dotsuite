package io.github.wiojelt.dotsuite.ui

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.wiojelt.dotsuite.dock.*
import io.github.wiojelt.dotsuite.data.DockStyle
import io.github.wiojelt.dotsuite.data.DockMotion
import kotlin.math.roundToInt
import io.github.wiojelt.dotsuite.maps.*
import io.github.wiojelt.dotsuite.standby.*
import io.github.wiojelt.dotsuite.privileged.PrivilegedManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun MapsModeSettings(ready: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var enabled by remember { mutableStateOf(MapsMode.enabled(context)) }
    val connected by MapsMode.listenerConnected.collectAsState()
    val navigating by MapsMode.navigating.collectAsState()
    val result by MapsMode.result.collectAsState()
    var busy by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Use Google Maps' minimal navigation screen through Shizuku / Sui.")
        SoftGroup {
            ToggleRow("Open after waking the screen", "Only during an ongoing Maps navigation. Off by default.", enabled, true) {
                enabled = it; MapsMode.prefs(context).edit().putBoolean("enabled", it).apply()
                scope.launch(Dispatchers.IO) {
                    io.github.wiojelt.dotsuite.data.FeatureJournal.record(context, "maps.automation", "enabled=$it")
                }
            }
        }
        Text("Disable the same Maps feature in Essentials before enabling it here. Two automation owners can launch the screen twice.", style = MaterialTheme.typography.bodySmall)
        Text("Notification access: ${if (connected) "connected" else "not connected"}\nMaps navigation: ${if (navigating) "active" else "not detected"}")
        OutlinedButton(onClick = {
            runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        }) { Text("Notification access") }
        Button(enabled = ready && !busy, onClick = {
            busy = true
            scope.launch { try { MapsMode.result.value = PrivilegedManager.launchMapsMinMode() } finally { busy = false } }
        }) { Text("Open minimal mode now") }
        Text(if (result == "OK") "Launch sent. Google Maps controls the actual screen." else result, style = MaterialTheme.typography.bodySmall)
        Text("Start a driving route first. The automatic mode reads only Maps' notification category, never route text. This is not Pixel's low-power AOD implementation.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
internal fun StandbySettings(revision: Int) {
    val context = LocalContext.current
    var night by remember { mutableStateOf(StandbyPreferences.night(context)) }
    var clockStyle by remember { mutableIntStateOf(StandbyPreferences.clock(context)) }
    var localRevision by remember { mutableIntStateOf(0) }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("A quiet desk display. Full clock, a clock–widget pair, or two widgets. Turn the phone sideways for the wide layout.")
        androidx.compose.ui.viewinterop.AndroidView(
            factory = { StandbyClockView(it) },
            update = { it.refresh(); it.clockStyle = clockStyle; it.night = night; it.invalidate() },
            modifier = Modifier.fillMaxWidth().height(160.dp))
        SegmentedSetting("Clock face", "Original dot, thin digital and analog faces.",
            StandbyPreferences.clockNames.mapIndexed { index, name -> index to name }, clockStyle, true) {
            clockStyle = it; StandbyPreferences.setClock(context, it)
        }
        SoftGroup {
            ToggleRow("Night clock", "Dim red digits on a black background.", night, true) {
                night = it; StandbyPreferences.setNight(context, it)
            }
        }
        for (slot in 0..1) {
            val id = remember(revision, localRevision) { StandbyPreferences.widget(context, slot) }
            val info = remember(id) { if (id < 0) null else AppWidgetManager.getInstance(context).getAppWidgetInfo(id) }
            SectionLabel("Widget ${slot + 1}")
            if (info != null) WidgetArtwork(info)
            Text(info?.loadLabel(context.packageManager)?.toString() ?: "Choose from your installed apps",
                style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { context.startActivity(Intent(context, WidgetPickerActivity::class.java).putExtra("slot", slot)) }) { Text("Choose widget") }
                if (id >= 0) TextButton(onClick = {
                    AppWidgetHost(context, StandbyPreferences.HOST_ID).deleteAppWidgetId(id)
                    StandbyPreferences.setWidget(context, slot, -1); localRevision++
                }) { Text("Remove") }
            }
        }
        Button(onClick = { context.startActivity(Intent(context, StandbyPreviewActivity::class.java).putExtra("landscape", true)) }) { Text("Open StandBy") }
        OutlinedButton(onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_DREAM_SETTINGS)) } }) { Text("Set charging screensaver") }
        Text("Swipe across the clock or use Clock / Pair / Widgets to change the display. In Pair, 1 / 2 switches the widget. Select DotSuite StandBy in Android's screensaver settings to use it while charging. Android controls activation. OEM-only widgets may refuse other hosts; widgets stay hidden while securely locked. This is a screensaver, not low-power AOD.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
internal fun DockSettings() {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(DockPreferences.packages(context)) }
    var left by remember { mutableStateOf(DockPreferences.prefs(context).getBoolean("left", false)) }
    var choosing by remember { mutableStateOf(false) }
    var style by remember { mutableStateOf(DockPreferences.style(context)) }
    var apps by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    LaunchedEffect(choosing) {
        if (choosing) apps = withContext(Dispatchers.IO) {
            context.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                .map { it.activityInfo.packageName to it.loadLabel(context.packageManager).toString() }
                .distinctBy { it.first }.sortedBy { it.second.lowercase() }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Button(onClick = { context.startActivity(Intent(context, QuickDockActivity::class.java)) }, modifier = Modifier.fillMaxWidth()) { Text("Open quick dock") }
        OutlinedButton(onClick = { choosing = true }, modifier = Modifier.fillMaxWidth()) { Text("Choose apps · ${selected.size}/8") }
        SectionLabel("Placement")
        SoftGroup {
            SegmentedSetting("Side", "The window opens on this side.", listOf(true to "Left", false to "Right"), left, true) {
                left = it; DockPreferences.setLeft(context, it)
            }
        }
        DockAppearanceControls(style) { style = it.bounded(); DockPreferences.style(context, style) }
        TextButton(onClick = { style = DockStyle(); DockPreferences.style(context, style) }) { Text("Reset dock appearance") }
        Text("Open from Quick Settings or a notch gesture. This temporary side window leaves Back and system edge gestures unchanged.", style = MaterialTheme.typography.bodySmall)
    }
    if (choosing) AlertDialog(onDismissRequest = { choosing = false }, title = { Text("Quick dock apps") },
        text = { LazyColumn(Modifier.heightIn(max = 400.dp)) { items(apps, key = { it.first }) { app ->
            val checked = app.first in selected
            val allowed = checked || selected.size < 8
            Row(Modifier.fillMaxWidth().toggleable(checked, enabled = allowed, role = androidx.compose.ui.semantics.Role.Checkbox, onValueChange = {
                    selected = if (it) selected + app.first else selected - app.first
                    DockPreferences.save(context, selected)
                }), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(checked, enabled = allowed, onCheckedChange = null)
                Text(app.second, Modifier.weight(1f))
            }
        } } }, confirmButton = { TextButton(onClick = { choosing = false }) { Text("Done") } })
}

@Composable
internal fun DockAppearanceControls(style: DockStyle, onStyle: (DockStyle) -> Unit) {
    SoftGroup {
        DockSlider("Vertical position", "Top to bottom, within the usable screen.", style.positionPercent, 0..100, "%") { onStyle(style.copy(positionPercent = it)) }
        SoftDivider()
        DockSlider("Edge inset", "Zero joins the bezel; a gap makes a floating pill.", style.edgeInsetDp, 0..24, " dp") { onStyle(style.copy(edgeInsetDp = it)) }
    }
    SectionLabel("Size & surface")
    SoftGroup {
        DockSlider("Dock size", "Scales the rail and icons; touch targets stay usable.", style.sizePercent, 80..140, "%") { onStyle(style.copy(sizePercent = it)) }
        SoftDivider()
        DockSlider("Visible apps", "Extra shortcuts scroll inside the rail.", style.visibleApps, 3..8, "") { onStyle(style.copy(visibleApps = it)) }
        SoftDivider()
        DockSlider("Background opacity", "Icons and labels keep their full contrast.", style.opacityPercent, 35..100, "%") { onStyle(style.copy(opacityPercent = it)) }
        SoftDivider()
        DockSlider("Dim behind dock", "Zero leaves the rest of the screen unchanged.", style.dimPercent, 0..35, "%") { onStyle(style.copy(dimPercent = it)) }
    }
    SectionLabel("Motion")
    SoftGroup {
        DockMotion.entries.forEachIndexed { index, mode ->
            if (index > 0) SoftDivider()
            Row(Modifier.fillMaxWidth().selectable(style.motion == mode, role = androidx.compose.ui.semantics.Role.RadioButton,
                onClick = { onStyle(style.copy(motion = mode)) }).padding(horizontal = 18.dp, vertical = 6.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(mode.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                RadioButton(style.motion == mode, onClick = null)
            }
        }
    }
    Text("Reduce motion and Android's animation settings take priority. Changes apply on the next opening.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun DockSlider(title: String, detail: String, current: Int, range: IntRange, suffix: String, onSave: (Int) -> Unit) {
    var value by remember(current) { mutableFloatStateOf(current.toFloat()) }
    DiscreteSliderSetting(title, detail, "${value.roundToInt()}$suffix", value, range.first.toFloat()..range.last.toFloat(),
        range.last - range.first - 1, true, { value = it.roundToInt().toFloat() }, { onSave(value.roundToInt()) })
}
