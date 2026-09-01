package io.github.wiojelt.dotsuite.ui.theme

import android.content.*
import android.database.ContentObserver
import android.os.*
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import io.github.wiojelt.dotsuite.data.AppearanceOptions
import io.github.wiojelt.dotsuite.data.AppearancePrefs

val LocalAppearance = staticCompositionLocalOf { AppearanceOptions() }
val LocalMotionAllowed = staticCompositionLocalOf { true }

@Composable
internal fun rememberAppearance(): AppearanceOptions {
    val context = LocalContext.current
    var options by remember { mutableStateOf(AppearancePrefs.read(context)) }
    DisposableEffect(context) {
        val prefs = AppearancePrefs.prefs(context)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> options = AppearancePrefs.read(context) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return options
}

@Composable
internal fun rememberMotionAllowed(options: AppearanceOptions): Boolean {
    val context = LocalContext.current
    val power = remember { context.getSystemService(PowerManager::class.java) }
    fun allowed() = !power.isPowerSaveMode &&
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    var systemAllowed by remember { mutableStateOf(allowed()) }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { systemAllowed = allowed() }
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) { systemAllowed = allowed() }
        }
        context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        context.registerReceiver(receiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED), Context.RECEIVER_NOT_EXPORTED)
        onDispose { context.contentResolver.unregisterContentObserver(observer); context.unregisterReceiver(receiver) }
    }
    return !options.reduceMotion && systemAllowed
}

/** App-owned diffuse backdrop. Does not read wallpaper, windows, screen pixels or other apps. */
@Composable
fun AppBackdrop(modifier: Modifier = Modifier) {
    val translucent = LocalAppearance.current.translucent
    Canvas(modifier.fillMaxSize()) {
        drawRect(Color(0xFF0C0E11))
        if (translucent) {
            drawRect(Brush.radialGradient(
                listOf(Color(0xFF434D59).copy(alpha = .65f), Color.Transparent),
                center = Offset(size.width * .8f, size.height * .22f), radius = size.width * 1.05f,
            ))
            drawRect(Brush.radialGradient(
                listOf(Color(0xFF643D3F).copy(alpha = .36f), Color.Transparent),
                center = Offset(size.width * -.12f, size.height * .7f), radius = size.width * .95f,
            ))
            drawRect(Brush.linearGradient(
                listOf(Color.White.copy(alpha = .025f), Color.Transparent),
                start = Offset.Zero, end = Offset(size.width, size.height),
            ))
        }
    }
}
