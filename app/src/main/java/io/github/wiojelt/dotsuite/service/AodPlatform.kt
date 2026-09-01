package io.github.wiojelt.dotsuite.service

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import io.github.wiojelt.dotsuite.data.AodPolicy as A
import org.json.JSONObject

/** Read-only platform discovery in the existing privileged bridge, not a SystemUI hook. */
internal class AodPlatform(private val context: Context) {
    private val config by lazy {
        runCatching { Class.forName("android.hardware.display.AmbientDisplayConfiguration")
            .getConstructor(Context::class.java).newInstance(context) }.getOrNull()
    }
    private fun available(name: String) = runCatching {
        config?.javaClass?.getMethod(name)?.invoke(config) == true
    }.getOrDefault(false)
    private fun nativeFlag(name: String) = runCatching {
        val type = Class.forName("com.nothing.NtFeaturesUtils")
        val flag = type.getField(name).getInt(null)
        type.getMethod("isSupport", IntArray::class.java).invoke(null, intArrayOf(flag)) == true
    }.getOrDefault(false)
    private fun resourceDefault(name: String) = runCatching {
        val id = context.resources.getIdentifier(name, "bool", "android")
        id != 0 && context.resources.getBoolean(id)
    }.getOrDefault(false)
    val nothing get() = Build.DEVICE == "Asteroids" && Build.MODEL == "A059P" && Build.VERSION.SDK_INT == 36
    fun supports(key: String, value: String? = null): Boolean {
        // A restore can delete a row on supported hardware; unsupported feature probing never writes.
        if (!available("alwaysOnAvailable")) return false
        return when (key) {
            A.ENABLED -> true
            A.NOTIFICATIONS -> available("pulseOnNotificationAvailable")
            A.TAP -> available("tapSensorAvailable")
            A.DOUBLE_TAP -> available("doubleTapSensorAvailable")
            A.LIFT -> nothing
            A.MODE -> nothing && (value == null || A.canUseMode(value.toIntOrNull() ?: -1,
                nativeFlag("NTF_ALL_DAY_AOD"), nativeFlag("NTF_SCHEDULE_AOD"), nativeFlag("NTF_TAP_AOD")))
            else -> false
        }
    }
    fun snapshot(): JSONObject = JSONObject().apply {
        put("available", available("alwaysOnAvailable"))
        put("nothing", nothing)
        put("defaultEnabled", resourceDefault("config_dozeAlwaysOnEnabled"))
        put("defaultMode", if (nativeFlag("NTF_GENERAL_AOD")) 1 else 2)
        put("allDay", nothing && nativeFlag("NTF_ALL_DAY_AOD"))
        put("schedule", nothing && nativeFlag("NTF_SCHEDULE_AOD"))
        put("tapMode", nothing && nativeFlag("NTF_TAP_AOD"))
        put("notifications", supports(A.NOTIFICATIONS))
        put("tap", supports(A.TAP))
        put("doubleTap", supports(A.DOUBLE_TAP))
        put("lift", supports(A.LIFT))
        put("powerSaver", context.getSystemService(PowerManager::class.java).isPowerSaveMode)
        // Read only: do not overwrite missing native schedule defaults. Nothing's init and
        // deletion-observer fallbacks differ on this build. Configure times in its own Settings.
        for ((name, key) in listOf("start" to "nt_aod_start_time", "end" to "nt_aod_end_time",
                "inversion" to "accessibility_display_inversion_enabled")) {
            put(name, runCatching { Settings.Secure.getString(context.contentResolver, key) }.getOrNull() ?: JSONObject.NULL)
        }
    }
}
