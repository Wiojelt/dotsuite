package io.github.wiojelt.dotsuite.ui.theme

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.wiojelt.dotsuite.R
import io.github.wiojelt.dotsuite.data.TouchSoundPolicy
import io.github.wiojelt.dotsuite.diagnostics.RecentDiagnostics

enum class TouchCue { TAP, OPEN, BACK }
val LocalTouchSound = staticCompositionLocalOf<(TouchCue) -> Unit> { {} }

/** Owned by the visible activity. Disabled by default; releases native audio resources on stop. */
internal class TouchSoundPlayer(private val context: Context) {
    private var pool: SoundPool? = null
    private val samples = mutableMapOf<TouchCue, Int>()
    private val loaded = mutableSetOf<Int>()
    private var lastPlay = -1000L
    fun start() {
        if (pool != null) return
        runCatching {
            val created = SoundPool.Builder().setMaxStreams(1).setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
            pool = created
            created.setOnLoadCompleteListener { source, id, status -> if (source === pool && status == 0) loaded.add(id) }
            samples[TouchCue.TAP] = created.load(context, R.raw.dot_tap, 1)
            samples[TouchCue.OPEN] = created.load(context, R.raw.dot_open, 1)
            samples[TouchCue.BACK] = created.load(context, R.raw.dot_back, 1)
        }.onFailure { stop(); RecentDiagnostics.failure("ui.sound.load", it) }
    }
    fun stop() { pool?.release(); pool = null; samples.clear(); loaded.clear() }
    fun play(cue: TouchCue) {
        val player = pool ?: return
        val id = samples[cue]?.takeIf { it in loaded } ?: return
        runCatching {
            val audio = context.getSystemService(AudioManager::class.java)
            val notices = context.getSystemService(NotificationManager::class.java)
            val allowed = TouchSoundPolicy.allowed(true, true,
                audio.ringerMode == AudioManager.RINGER_MODE_NORMAL,
                Settings.System.getInt(context.contentResolver, Settings.System.SOUND_EFFECTS_ENABLED, 0) == 1,
                notices.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL,
                audio.mode != AudioManager.MODE_NORMAL, audio.isMusicActive,
                audio.getStreamVolume(AudioManager.STREAM_SYSTEM))
            val now = SystemClock.elapsedRealtime()
            if (allowed && now - lastPlay >= 90L) {
                player.play(id, TouchSoundPolicy.gain(), TouchSoundPolicy.gain(), 1, 0, 1f)
                lastPlay = now
            }
        }.onFailure { RecentDiagnostics.failure("ui.sound.play", it) }
    }
}

@Composable
internal fun rememberTouchSound(enabled: Boolean): (TouchCue) -> Unit {
    val context = LocalContext.current.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val player = remember(context) { TouchSoundPlayer(context) }
    DisposableEffect(player, enabled, lifecycle) {
        fun sync() { if (enabled && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) player.start() else player.stop() }
        val observer = LifecycleEventObserver { _, _ -> sync() }
        lifecycle.addObserver(observer)
        sync()
        onDispose { lifecycle.removeObserver(observer); player.stop() }
    }
    return remember(player, enabled, lifecycle) { { cue ->
        if (enabled && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) player.play(cue)
    } }
}
