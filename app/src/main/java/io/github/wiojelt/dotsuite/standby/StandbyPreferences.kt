package io.github.wiojelt.dotsuite.standby

import android.content.Context
import io.github.wiojelt.dotsuite.data.FeatureJournal

object StandbyPreferences {
    val clockNames = listOf("Dots", "Digital", "Dial")
    fun clock(context: Context) = prefs(context).getInt("clock", 0).coerceIn(0, 2)
    fun setClock(context: Context, value: Int) {
        prefs(context).edit().putInt("clock", value.coerceIn(0, 2)).apply()
        FeatureJournal.record(context, "standby.clock", "style=${value.coerceIn(0, 2)}")
    }
    const val HOST_ID = 50401
    fun prefs(context: Context) = context.getSharedPreferences("standby", Context.MODE_PRIVATE)
    fun widget(context: Context, slot: Int) = prefs(context).getInt("widget_$slot", -1)
    fun setWidget(context: Context, slot: Int, id: Int) {
        require(slot in 0..1)
        prefs(context).edit().putInt("widget_$slot", id).apply()
        FeatureJournal.record(context, "standby.widget", if (id >= 0) "slot=$slot configured" else "slot=$slot removed")
    }
    fun night(context: Context) = prefs(context).getBoolean("night", true)
    fun setNight(context: Context, night: Boolean) {
        prefs(context).edit().putBoolean("night", night).apply()
        FeatureJournal.record(context, "standby.night", "enabled=$night")
    }
}
