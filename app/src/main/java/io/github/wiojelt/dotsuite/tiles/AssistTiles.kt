package io.github.wiojelt.dotsuite.tiles

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import io.github.wiojelt.dotsuite.MainActivity
import io.github.wiojelt.dotsuite.capture.CapturePreferences
import io.github.wiojelt.dotsuite.capture.CaptureService
import io.github.wiojelt.dotsuite.capture.CaptureShortcutActivity
import io.github.wiojelt.dotsuite.data.PersonalizationPolicy as P
import io.github.wiojelt.dotsuite.data.SystemOptions
import io.github.wiojelt.dotsuite.privileged.PrivilegedManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

internal fun settingsIntent(context: Context, page: String) = Intent(context, MainActivity::class.java)
    .putExtra("feature_page", page).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

@Suppress("DEPRECATION")
@android.annotation.SuppressLint("StartActivityAndCollapseDeprecated") // Old overload only on API 24–33, where PendingIntent overload does not exist.
internal fun TileService.openAndCollapse(intent: Intent) {
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (Build.VERSION.SDK_INT >= 34) startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
    else startActivityAndCollapse(intent)
}

/** No polling and no helper process while idle. Binding is scoped to one explicit tile click. */
abstract class OptionTile : TileService() {
    protected abstract val option: String
    protected abstract val page: String
    protected abstract val title: String
    protected open val defaultEnabled: Boolean get() = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var running: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        val value = runCatching { SystemOptions.read(this, option) }
        update(if (value.isSuccess) io.github.wiojelt.dotsuite.data.AodPolicy.enabled(value.getOrNull(), defaultEnabled) else null)
    }

    override fun onClick() {
        super.onClick()
        if (running?.isActive == true) return
        unlockAndRun {
            running = scope.launch {
                PrivilegedManager.retainClient(this@OptionTile)
                try {
                    val connected = withTimeoutOrNull(12_000) {
                        PrivilegedManager.setup.first { it.ready || !it.serviceRunning || !it.accessGranted || it.connectFailed }
                    }
                    if (connected?.ready != true) {
                        openAndCollapse(settingsIntent(this@OptionTile, page)); return@launch
                    }
                    val before = PrivilegedManager.readSystemOption(option)
                    if (!before.available) {
                        Toast.makeText(this@OptionTile, "$title is unavailable on this device", Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    val wanted = if (io.github.wiojelt.dotsuite.data.AodPolicy.enabled(before.value, defaultEnabled)) "0" else "1"
                    val result = SystemOptions.write(this@OptionTile, option, wanted)
                    if (result == "Saved") update(wanted == "1")
                    else Toast.makeText(this@OptionTile, result, Toast.LENGTH_LONG).show()
                } finally { PrivilegedManager.releaseClient() }
            }
        }
    }

    private fun update(active: Boolean?) {
        qsTile?.apply {
            label = title
            state = if (active == true) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            if (Build.VERSION.SDK_INT >= 29) subtitle = if (active == null) "Tap to check / change" else null
            updateTile()
        }
    }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}

class MonoAudioTile : OptionTile() {
    override val option = P.MASTER_MONO
    override val page = "hearing"
    override val title = "Mono audio"
}

class ExtraDimTile : OptionTile() {
    override val option = P.EXTRA_DIM
    override val page = "display"
    override val title = "Extra dim"
}

/** Toggles the native master only; never changes mode, schedule, wake gestures or battery saver. */
class AodTile : OptionTile() {
    override val option = io.github.wiojelt.dotsuite.data.AodPolicy.ENABLED
    override val page = "aod"
    override val title = "Native AOD"
    override val defaultEnabled: Boolean get() = runCatching {
        val id = resources.getIdentifier("config_dozeAlwaysOnEnabled", "bool", "android")
        id != 0 && resources.getBoolean(id)
    }.getOrDefault(false)
}

class CameraCaptureTile : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observing: Job? = null
    override fun onStartListening() {
        super.onStartListening()
        observing?.cancel()
        observing = scope.launch {
            CaptureService.state.collect { status ->
                qsTile?.apply {
                    label = if (status.busy) "Stop capture" else "Camera shortcut"
                    state = if (status.busy) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                    updateTile()
                }
            }
        }
    }
    override fun onClick() {
        super.onClick()
        if (CaptureService.state.value.busy) {
            startService(Intent(this, CaptureService::class.java).setAction(CaptureService.STOP)); return
        }
        unlockAndRun {
            if (!CapturePreferences.enabled(this)) openAndCollapse(settingsIntent(this, "capture"))
            else openAndCollapse(Intent(this, CaptureShortcutActivity::class.java)
                .putExtra("capture_action", CapturePreferences.tileAction(this)))
        }
    }
    override fun onStopListening() { observing?.cancel(); observing = null; super.onStopListening() }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}

class MapsMinModeTile : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply { label = "Maps MinMode"; state = Tile.STATE_INACTIVE; updateTile() }
    }
    override fun onClick() { super.onClick(); unlockAndRun { openAndCollapse(settingsIntent(this, "maps")) } }
}

class QuickDockTile : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply { label = "Quick dock"; state = Tile.STATE_INACTIVE; updateTile() }
    }
    override fun onClick() {
        super.onClick()
        unlockAndRun { openAndCollapse(Intent(this, io.github.wiojelt.dotsuite.dock.QuickDockActivity::class.java)) }
    }
}
