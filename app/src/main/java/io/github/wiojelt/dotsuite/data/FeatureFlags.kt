package io.github.wiojelt.dotsuite.data

import android.content.Context
import android.provider.Settings
import io.github.wiojelt.dotsuite.privileged.PrivilegedManager

object FeatureFlags {
    const val PANEL = "dotsuite_systemui_enabled"
    const val SCREEN_OFF_KEYS = "dotsuite_screen_off_keys_enabled"

    fun isEnabled(context: Context, key: String): Boolean =
        runCatching {
            Settings.Secure.getInt(context.contentResolver, key, 0) != 0
        }.getOrDefault(false)

    suspend fun setEnabled(key: String, enabled: Boolean): Boolean =
        PrivilegedManager.setFeatureEnabled(key, enabled)
}
