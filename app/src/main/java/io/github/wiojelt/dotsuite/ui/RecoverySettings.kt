package io.github.wiojelt.dotsuite.ui

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.wiojelt.dotsuite.capture.CapturePreferences
import io.github.wiojelt.dotsuite.capture.CaptureService
import io.github.wiojelt.dotsuite.data.FeatureJournal
import io.github.wiojelt.dotsuite.data.PersonalizationPolicy as P
import io.github.wiojelt.dotsuite.data.SoundSettings
import io.github.wiojelt.dotsuite.data.SystemOptions
import io.github.wiojelt.dotsuite.privileged.PrivilegedManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun RecoverySettings() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val setup by PrivilegedManager.setup.collectAsState()
    var keys by remember { mutableStateOf<List<String>>(emptyList()) }
    var revision by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    var confirmReload by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    LaunchedEffect(revision) {
        runCatching { SystemOptions.savedKeys(context) }
            .onSuccess { keys = it }
            .onFailure { status = "The original-values file could not be read. Nothing will be overwritten." }
    }
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("Restore the values this app saved before its first change—not a guessed factory default.", style = MaterialTheme.typography.bodyLarge)
        Text("Saved system values: ${keys.size}", style = MaterialTheme.typography.titleMedium)
        Text("Covers AOD and wake behaviour, motion timing, mono / balance, dimming, feedback, carrier label, clock, gesture line and rotation. Changes made elsewhere after our last write are skipped. This backup stays on this device and is not included in cloud backup.", style = MaterialTheme.typography.bodySmall)
        Button(enabled = keys.isNotEmpty() && setup.ready && !busy, onClick = { confirm = true }) { Text("Restore saved settings…") }
        HorizontalDivider()
        Text("Quick stop", style = MaterialTheme.typography.titleMedium)
        OutlinedButton(enabled = !busy, onClick = {
            CapturePreferences.setEnabled(context, false)
            io.github.wiojelt.dotsuite.maps.MapsMode.prefs(context).edit().putBoolean("enabled", false).apply()
            if (CaptureService.state.value.busy) context.startService(Intent(context, CaptureService::class.java).setAction(CaptureService.STOP))
            busy = true
            scope.launch {
                withContext(Dispatchers.IO) { FeatureJournal.record(context, "recovery.local", "camera and Maps automation disabled") }
                status = "Camera and Maps automation disabled; capture stop requested"; busy = false
            }
        }) { Text("Stop camera & Maps automation") }
        OutlinedButton(enabled = setup.ready && !busy, onClick = {
            busy = true
            scope.launch {
                // Do not short-circuit: attempt every independent setting even if a previous write failed.
                val results = listOf(P.NOTCH_ENABLED, P.STATUS_DOUBLE_SLEEP, P.POWER_TORCH, P.HIDE_DIRECT_SHARE)
                    .map { SoundSettings.write(it, 0) }
                val allDisabled = results.all { it }
                status = if (allDisabled) "New gestures and share filtering disabled; assignments kept" else "Some changes could not be disabled; check Shizuku / Sui"
                withContext(Dispatchers.IO) { FeatureJournal.record(context, "recovery.gestures", if (allDisabled) "disabled" else "incomplete") }
                busy = false
            }
        }) { Text("Disable gestures & share filtering") }
        if (!setup.ready) Text("Connect Shizuku / Sui to restore system settings or disable native gestures.", style = MaterialTheme.typography.bodySmall)
        if (status.isNotEmpty()) Text(status, style = MaterialTheme.typography.bodyMedium)
        Text("After a module update", style = MaterialTheme.typography.titleMedium)
        OutlinedButton(enabled = setup.ready && !busy, onClick = { confirmReload = true }) { Text("Reload SystemUI…") }
        Text("Root/Sui only. The status bar and system panels briefly disappear. This does not reboot the phone or replace the launcher. If SystemUI cannot stay open, disable DotSuite in Vector / LSPosed.", style = MaterialTheme.typography.bodySmall)
    }
    if (confirmReload) AlertDialog(onDismissRequest = { confirmReload = false },
        title = { Text("Reload SystemUI?") },
        text = { Text("Use after updating the root module. Finish any system dialog first and keep your recovery backup. No reboot or USB changes.") },
        confirmButton = { TextButton(onClick = {
            confirmReload = false; busy = true
            scope.launch {
                try {
                    withContext(Dispatchers.IO) { FeatureJournal.record(context, "systemui.reload", "user requested") }
                    status = PrivilegedManager.reloadSystemUi()
                } finally { busy = false }
            }
        }) { Text("Reload now") } },
        dismissButton = { TextButton(onClick = { confirmReload = false }) { Text("Cancel") } })
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Restore saved system settings?") },
        text = { Text("Restore saved values (${keys.size}). Settings changed by another app will be left alone. Camera permissions, existing panel preferences and app data are not removed.") },
        confirmButton = { TextButton(onClick = {
            confirm = false; busy = true
            scope.launch { status = SystemOptions.restore(context, keys); revision++; busy = false }
        }) { Text("Restore") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } })
}
