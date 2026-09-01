package io.github.wiojelt.dotsuite.standby

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.os.Bundle
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import io.github.wiojelt.dotsuite.data.FeatureJournal
import io.github.wiojelt.dotsuite.ui.theme.DotSuiteTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Preview catalogue with Android-owned consent/configuration; never forces widget binding. */
class WidgetPickerActivity : ComponentActivity() {
    private var pendingId = -1
    private var slot = 0
    private var pending by mutableStateOf(false)
    private var error by mutableStateOf("")
    private val host by lazy { AppWidgetHost(this, StandbyPreferences.HOST_ID) }
    private val manager by lazy { AppWidgetManager.getInstance(this) }
    private val bind = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) configureOrAccept() else abandon()
    }
    private val pick = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) configureOrAccept() else abandon()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        slot = (savedInstanceState?.getInt("slot") ?: intent.getIntExtra("slot", 0)).coerceIn(0, 1)
        pendingId = savedInstanceState?.getInt("id", -1) ?: -1
        pending = pendingId >= 0
        if (savedInstanceState == null) {
            val orphan = StandbyPreferences.prefs(this).getInt("pending_widget", -1)
            if (orphan >= 0 && (0..1).none { StandbyPreferences.widget(this, it) == orphan })
                runCatching { host.deleteAppWidgetId(orphan) }
            StandbyPreferences.prefs(this).edit().remove("pending_widget").apply()
        }
        setContent { DotSuiteTheme {
            WidgetCatalogue(pending, error, { finish() }, { choose(it) }, { systemPicker() })
        } }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("slot", slot); outState.putInt("id", pendingId)
        super.onSaveInstanceState(outState)
    }
    private fun allocate(): Boolean {
        if (pending) return false
        return runCatching {
            pendingId = host.allocateAppWidgetId()
            StandbyPreferences.prefs(this).edit().putInt("pending_widget", pendingId).apply()
            pending = true; error = ""; true
        }.getOrElse { fail(); false }
    }
    private fun choose(info: AppWidgetProviderInfo) {
        if (!allocate()) return
        runCatching {
            if (manager.bindAppWidgetIdIfAllowed(pendingId, info.profile, info.provider, null)) configureOrAccept()
            else bind.launch(Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingId)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile))
        }.onFailure { fail() }
    }
    private fun systemPicker() {
        if (!allocate()) return
        runCatching { pick.launch(Intent(AppWidgetManager.ACTION_APPWIDGET_PICK)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingId)) }.onFailure { fail() }
    }
    private fun configureOrAccept() {
        val info = if (pendingId >= 0) manager.getAppWidgetInfo(pendingId) else null
        if (info == null) { fail(); return }
        if (info.configure == null) accept()
        else runCatching {
            host.startAppWidgetConfigureActivityForResult(this, pendingId, 0, 601, null)
        }.onFailure { fail() }
    }
    @Deprecated("Framework AppWidgetHost configuration result")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 601) { if (resultCode == RESULT_OK) accept() else abandon() }
    }
    private fun accept() {
        if (pendingId < 0 || manager.getAppWidgetInfo(pendingId) == null) { fail(); return }
        val previous = StandbyPreferences.widget(this, slot)
        StandbyPreferences.setWidget(this, slot, pendingId)
        pendingId = -1; pending = false
        StandbyPreferences.prefs(this).edit().remove("pending_widget").apply()
        if (previous >= 0) runCatching { host.deleteAppWidgetId(previous) }
        finish()
    }
    private fun abandon() {
        if (pendingId >= 0) runCatching { host.deleteAppWidgetId(pendingId) }
        pendingId = -1; pending = false
        StandbyPreferences.prefs(this).edit().remove("pending_widget").apply()
    }
    private fun fail() {
        abandon()
        error = "This widget could not be added. Try another widget or the system picker."
        FeatureJournal.record(this, "standby.widget", "provider setup failed")
    }
    override fun onDestroy() {
        if (isFinishing) abandon()
        super.onDestroy()
    }
}

private data class WidgetEntry(val info: AppWidgetProviderInfo, val app: String, val label: String)

@Composable
private fun WidgetCatalogue(busy: Boolean, error: String, back: () -> Unit,
    choose: (AppWidgetProviderInfo) -> Unit, systemPicker: () -> Unit) {
    val context = LocalContext.current
    var query by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    val entries by produceState<List<WidgetEntry>?>(null) {
        value = withContext(Dispatchers.IO) {
            runCatching { AppWidgetManager.getInstance(context).getInstalledProvidersForProfile(Process.myUserHandle())
                .filter { it.widgetCategory and AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN != 0 }
                .mapNotNull { runCatching { WidgetEntry(it, context.packageManager.getApplicationLabel(
                    context.packageManager.getApplicationInfo(it.provider.packageName, 0)).toString(),
                    it.loadLabel(context.packageManager).toString()) }.getOrNull() }
                .sortedWith(compareBy<WidgetEntry> { !it.info.provider.packageName.startsWith("com.nothing") }
                    .thenBy { it.app.lowercase() }.thenBy { it.label.lowercase() })
            }.getOrDefault(emptyList())
        }
    }
    val filtered = remember(entries, query) { entries.orEmpty().filter {
        query.isBlank() || it.app.contains(query, true) || it.label.contains(query, true)
    } }
    Surface(Modifier.fillMaxSize(), color = androidx.compose.ui.graphics.Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground) {
        io.github.wiojelt.dotsuite.ui.theme.AppBackdrop()
        LazyColumn(Modifier.fillMaxSize().safeDrawingPadding().testTag("widget-catalogue"),
            contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = back) { Text("Back") }
                TextButton(enabled = !busy, onClick = systemPicker) { Text("System picker") }
            } }
            item { Text("Widgets", style = MaterialTheme.typography.headlineLarge) }
            item { Text("Choose a widget, then confirm with Android.",
                style = MaterialTheme.typography.bodyMedium) }
            item { OutlinedTextField(query, { query = it }, label = { Text("Search widgets") },
                singleLine = true, modifier = Modifier.fillMaxWidth()) }
            if (error.isNotEmpty()) item { Text(error, color = MaterialTheme.colorScheme.error) }
            if (busy || entries == null) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (entries != null && filtered.isEmpty()) item { Text("No matching widgets. Try the system picker for other profiles.") }
            items(filtered, key = { it.info.provider.flattenToString() }) { entry ->
                Card(onClick = { choose(entry.info) }, enabled = !busy, modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(entry.app, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        WidgetArtwork(entry.info)
                        Text(entry.label, style = MaterialTheme.typography.titleMedium)
                        Text(if (entry.info.targetCellWidth > 0 && entry.info.targetCellHeight > 0)
                            "${entry.info.targetCellWidth} × ${entry.info.targetCellHeight}"
                            else "Android widget", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

/** Static provider artwork only; browsing never allocates IDs or starts widget updates. */
@Composable
internal fun WidgetArtwork(info: AppWidgetProviderInfo) {
    val context = LocalContext.current
    val artwork by produceState<android.graphics.Bitmap?>(null, info.provider) {
        value = withContext(Dispatchers.IO) { runCatching {
            val drawable = info.loadPreviewImage(context, context.resources.displayMetrics.densityDpi)
                ?: return@runCatching null
            val w = drawable.intrinsicWidth.coerceAtLeast(1)
            val h = drawable.intrinsicHeight.coerceAtLeast(1)
            val scale = minOf(1f, 640f / w, 320f / h)
            drawable.toBitmap((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
        }.getOrNull() }
    }
    if (artwork != null) Image(artwork!!.asImageBitmap(), null,
        Modifier.fillMaxWidth().height(140.dp), contentScale = ContentScale.Fit)
    else Box(Modifier.fillMaxWidth().height(68.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
        Text("Preview not supplied by this app", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
