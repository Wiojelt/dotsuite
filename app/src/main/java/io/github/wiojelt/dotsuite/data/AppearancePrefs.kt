package io.github.wiojelt.dotsuite.data

import android.content.Context
import androidx.core.content.edit

data class AppearanceOptions(
    val translucent: Boolean = true,
    val roulette: Boolean = false,
    val reduceMotion: Boolean = false,
    val transitionMs: Int = 500,
    val backdrop: HomeBackdrop = HomeBackdrop.NONE,
    val touchSounds: Boolean = false,
)

object AppearancePrefs {
    fun prefs(context: Context) = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
    fun read(context: Context): AppearanceOptions = prefs(context).let {
        AppearanceOptions(
            translucent = it.getBoolean("translucent", true),
            roulette = it.getBoolean("roulette", false),
            reduceMotion = it.getBoolean("reduce_motion", false),
            transitionMs = it.getInt("transition_ms", 500).coerceIn(200, 800),
            backdrop = HomeBackdrop.entries.firstOrNull { mode -> mode.name == it.getString("backdrop", "NONE") } ?: HomeBackdrop.NONE,
            touchSounds = it.getBoolean("touch_sounds", false),
        )
    }
    fun save(context: Context, options: AppearanceOptions) = prefs(context).edit {
        putBoolean("translucent", options.translucent)
        putBoolean("roulette", options.roulette)
        putBoolean("reduce_motion", options.reduceMotion)
        putInt("transition_ms", options.transitionMs.coerceIn(200, 800))
        putString("backdrop", options.backdrop.name)
        putBoolean("touch_sounds", options.touchSounds)
    }
}
