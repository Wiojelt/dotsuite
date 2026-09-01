package io.github.wiojelt.dotsuite.capture

import android.content.Context
import androidx.core.content.edit
import io.github.wiojelt.dotsuite.data.PersonalizationPolicy as P

object CapturePreferences {
    private fun prefs(context: Context) = context.getSharedPreferences("capture-device", Context.MODE_PRIVATE)
    fun enabled(context: Context) = prefs(context).getBoolean("enabled", false)
    fun setEnabled(context: Context, enabled: Boolean) { prefs(context).edit { putBoolean("enabled", enabled) } }
    fun audio(context: Context) = prefs(context).getBoolean("audio", false)
    fun setAudio(context: Context, enabled: Boolean) { prefs(context).edit { putBoolean("audio", enabled) } }
    fun minutes(context: Context) = prefs(context).getInt("minutes", 5).coerceIn(1, 30)
    fun setMinutes(context: Context, minutes: Int) { prefs(context).edit { putInt("minutes", minutes.coerceIn(1, 30)) } }
    fun glyphSeconds(context: Context) = prefs(context).getInt("glyph_seconds", 0).takeIf { it in setOf(0, 3, 5, 10) } ?: 0
    fun setGlyphSeconds(context: Context, value: Int) { if (value in setOf(0, 3, 5, 10)) prefs(context).edit { putInt("glyph_seconds", value) } }
    fun tileAction(context: Context): Int = prefs(context).getInt("tile_action", P.VIDEO_REAR).takeIf { P.isCaptureAction(it) } ?: P.VIDEO_REAR
    fun setTileAction(context: Context, action: Int) { if (P.isCaptureAction(action)) prefs(context).edit { putInt("tile_action", action) } }
}
