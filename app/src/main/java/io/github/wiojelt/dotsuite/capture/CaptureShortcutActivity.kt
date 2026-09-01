package io.github.wiojelt.dotsuite.capture

import android.Manifest
import android.app.KeyguardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.wiojelt.dotsuite.data.PersonalizationPolicy
import io.github.wiojelt.dotsuite.ui.theme.DotSuiteTheme
import kotlinx.coroutines.launch

/** A visible launch sheet satisfies Android's while-in-use rules. No preview or permission bypass. */
class CaptureShortcutActivity : ComponentActivity() {
    private var requested = false
    private var started = false
    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (required().all { p -> ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED }) startCapture()
        else showMessage("Permission not granted. No capture was started.")
    }
    private val action get() = intent.getIntExtra("capture_action", 0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requested = savedInstanceState?.getBoolean("requested") ?: false
        started = savedInstanceState?.getBoolean("started") ?: false
        if (CaptureService.state.value.busy && savedInstanceState == null) {
            startService(Intent(this, CaptureService::class.java).setAction(CaptureService.STOP))
            finish(); return
        }
        showMessage("Starting ${if (PersonalizationPolicy.isFront(action)) "front" else "rear"} camera…")
        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) { maybeStartCapture() }
        }
        lifecycleScope.launch {
            CaptureService.state.collect { state ->
                if (!started) return@collect
                if (state.phase == "recording" || state.phase == "saved") finish()
                else if (state.phase == "error") showMessage(state.message)
            }
        }
    }

    override fun onUserLeaveHint() {
        // Leaving during permission/start-up must not result in a late, surprising camera start.
        if (started && CaptureService.state.value.phase == "starting") {
            startService(Intent(this, CaptureService::class.java).setAction(CaptureService.STOP))
        }
        super.onUserLeaveHint()
    }

    private fun maybeStartCapture() {
        if (started || requested) return
        if (Build.VERSION.SDK_INT < 30 || !PersonalizationPolicy.isCaptureAction(action)
            || !CapturePreferences.enabled(this) || getSystemService(KeyguardManager::class.java).isKeyguardLocked) {
            showMessage("Unlock the phone and enable camera shortcuts in More → Camera shortcuts first."); return
        }
        requested = true
        val missing = required().filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) startCapture() else permissions.launch(missing.toTypedArray())
    }

    private fun required(): List<String> = buildList {
        add(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        if (PersonalizationPolicy.isVideo(action) && CapturePreferences.audio(this@CaptureShortcutActivity)) add(Manifest.permission.RECORD_AUDIO)
    }

    private fun startCapture() {
        if (!lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
            requested = false; return // permission dialog/lifecycle still settling; onResume retries
        }
        if (!CapturePreferences.enabled(this) || getSystemService(KeyguardManager::class.java).isKeyguardLocked
            || !PersonalizationPolicy.isCaptureAction(action)) {
            showMessage("Capture cancelled: unlock the phone and enable the shortcut first."); return
        }
        if (!CaptureService.notificationsAvailable(this)) {
            showMessage("Enable the Camera shortcuts notification channel first; its Stop button must stay visible."); return
        }
        started = true
        // Reset stale saved/error state before the collector observes this new attempt.
        CaptureService.prepare()
        runCatching { ContextCompat.startForegroundService(this,
            Intent(this, CaptureService::class.java).putExtra("capture_action", action))
        }.onFailure {
            started = false
            CaptureService.cancelPreparation()
            showMessage("Android could not start the camera. Nothing was recorded.")
        }
    }

    private fun showMessage(message: String) {
        setContent {
            DotSuiteTheme {
                Surface(Modifier.fillMaxSize(), color = androidx.compose.ui.graphics.Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onBackground) {
                    io.github.wiojelt.dotsuite.ui.theme.AppBackdrop()
                    Column(Modifier.safeDrawingPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Camera shortcut", style = MaterialTheme.typography.titleLarge)
                        Text(message)
                        Text("No preview. The system camera indicator and a Stop notification remain visible.", style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = {
                            if (started) startService(Intent(this@CaptureShortcutActivity, CaptureService::class.java).setAction(CaptureService.STOP))
                            finish()
                        }) { Text(if (started) "Stop / cancel" else "Close") }
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("requested", requested); outState.putBoolean("started", started)
        super.onSaveInstanceState(outState)
    }
}
