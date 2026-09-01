package io.github.wiojelt.dotsuite.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.wiojelt.dotsuite.data.FeatureJournal
import io.github.wiojelt.dotsuite.data.MixPrefs
import io.github.wiojelt.dotsuite.privileged.PrivilegedManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A selected app has TAKE_AUDIO_FOCUS relaxed. That is useful on the phone, but must never survive
 * an Android Auto / external USB route negotiation. These are event-driven broadcasts, so this
 * receiver consumes no memory between connections and performs no audio or volume operation.
 */
class ExternalAudioSafetyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == USB_STATE && !intent.getBooleanExtra("connected", false)) return
        val app = context.applicationContext
        val prefs = MixPrefs(app)
        val selected = prefs.enabledPackages()
        if (selected.isEmpty()) return

        val pending = goAsync()
        PrivilegedManager.retainClient(app)
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
            try {
                var attempts = 0
                while (!PrivilegedManager.setup.value.ready && attempts < 50) {
                    PrivilegedManager.refresh()
                    delay(100)
                    attempts++
                }
                if (!PrivilegedManager.setup.value.ready) {
                    FeatureJournal.record(app, "audio.external_restore", "bridge unavailable")
                    return@launch
                }
                var restored = 0
                for (packageName in selected) {
                    val original = prefs.originalMode(packageName) ?: "default"
                    if (PrivilegedManager.setAudioFocusMode(packageName, original)) {
                        prefs.setEnabled(packageName, false)
                        prefs.forgetOriginalMode(packageName)
                        restored++
                    }
                }
                FeatureJournal.record(
                    app,
                    "audio.external_restore",
                    "restored $restored of ${selected.size} focus overrides",
                )
            } finally {
                PrivilegedManager.releaseClient()
                pending.finish()
            }
        }
    }

    private companion object {
        const val USB_STATE = "android.hardware.usb.action.USB_STATE"
    }
}
