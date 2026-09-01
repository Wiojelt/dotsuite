package io.github.wiojelt.dotsuite.maps

import android.content.*
import android.media.AudioManager
import android.os.SystemClock
import android.os.PowerManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.content.ContextCompat
import io.github.wiojelt.dotsuite.data.FeatureJournal
import io.github.wiojelt.dotsuite.privileged.PrivilegedManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

object MapsMode {
    fun prefs(context: Context) = context.getSharedPreferences("maps-mode", Context.MODE_PRIVATE)
    fun enabled(context: Context) = prefs(context).getBoolean("enabled", false)
    val listenerConnected = MutableStateFlow(false)
    val navigating = MutableStateFlow(false)
    val result = MutableStateFlow("No launch requested")
}

/** Event-driven and opt-in. No notification extras, route logs or foreground polling. */
class MapsNavigationService : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var launch: Job? = null
    private var armed = false
    private var lastLaunch = 0L
    private var registered = false
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        armed = false
        if (!MapsMode.enabled(this)) launch?.cancel()
    }
    private val screen = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF) {
                launch?.cancel()
                armed = MapsMode.enabled(context) && MapsMode.navigating.value
                return
            }
            if (intent.action != Intent.ACTION_SCREEN_ON) return
            val allowed = MapsModePolicy.shouldLaunch(MapsMode.enabled(context), MapsMode.navigating.value,
                armed, getSystemService(AudioManager::class.java).mode != AudioManager.MODE_NORMAL,
                SystemClock.elapsedRealtime(), lastLaunch)
            armed = false
            if (!allowed || launch?.isActive == true) return
            lastLaunch = SystemClock.elapsedRealtime()
            launch = scope.launch {
                PrivilegedManager.retainClient(this@MapsNavigationService)
                try {
                    val state = withTimeoutOrNull(8_000) {
                        // A newly created listener may receive Shizuku's binder just after onCreate.
                        // Don't mistake the initial "not connected yet" snapshot for a final failure.
                        PrivilegedManager.setup.first { it.ready || it.connectFailed }
                    }
                    ensureActive()
                    if (!MapsMode.enabled(context) || !MapsMode.navigating.value ||
                        !getSystemService(PowerManager::class.java).isInteractive ||
                        getSystemService(AudioManager::class.java).mode != AudioManager.MODE_NORMAL) return@launch
                    MapsMode.result.value = if (state?.ready == true) PrivilegedManager.launchMapsMinMode()
                        else "Shizuku / Sui is not connected"
                    withContext(Dispatchers.IO) {
                        FeatureJournal.record(context, "maps.wake", if (MapsMode.result.value == "OK") "launch sent" else "unavailable")
                    }
                } finally { PrivilegedManager.releaseClient() }
            }
        }
    }
    override fun onListenerConnected() {
        super.onListenerConnected()
        MapsMode.listenerConnected.value = true
        refresh()
        if (!registered) {
            ContextCompat.registerReceiver(this, screen, IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON)
            }, ContextCompat.RECEIVER_NOT_EXPORTED)
            MapsMode.prefs(this).registerOnSharedPreferenceChangeListener(preferenceListener)
            registered = true
        }
    }
    private fun refresh() {
        MapsMode.navigating.value = runCatching {
            activeNotifications?.any { MapsModePolicy.isNavigation(it.packageName, it.notification.category, it.isOngoing) } == true
        }.getOrDefault(false)
        if (!MapsMode.navigating.value) { armed = false; launch?.cancel() }
    }
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn?.packageName == MapsModePolicy.MAPS_PACKAGE) refresh()
    }
    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn?.packageName == MapsModePolicy.MAPS_PACKAGE) refresh()
    }
    override fun onListenerDisconnected() {
        armed = false; launch?.cancel()
        MapsMode.listenerConnected.value = false; MapsMode.navigating.value = false
        super.onListenerDisconnected()
    }
    override fun onDestroy() {
        scope.cancel()
        if (registered) {
            unregisterReceiver(screen)
            MapsMode.prefs(this).unregisterOnSharedPreferenceChangeListener(preferenceListener)
        }
        MapsMode.listenerConnected.value = false; MapsMode.navigating.value = false
        super.onDestroy()
    }
}
