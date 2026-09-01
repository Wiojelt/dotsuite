package io.github.wiojelt.dotsuite.data

import android.content.Context

/**
 * Remembers which packages the user has enabled mixing for, so the switches reflect the
 * last applied state instantly without an appops round-trip per app. The actual system
 * state is still owned by appops; this is a fast local mirror kept in sync on every toggle.
 */
class MixPrefs(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("mix_audio", Context.MODE_PRIVATE)

    /** Every app whose focus override is currently owned by DotSuite. */
    fun enabledPackages(): Set<String> {
        val enabled = prefs.getStringSet(KEY_ENABLED, emptySet()).orEmpty().toMutableSet()
        // dev9 briefly stored only one package. Keep that user's choice while moving back to the
        // intended independent multi-select model.
        prefs.getString(KEY_SELECTED, null)?.let(enabled::add)
        return enabled
    }

    fun setEnabled(packageName: String, enabled: Boolean) {
        val packages = enabledPackages().toMutableSet()
        if (enabled) packages.add(packageName) else packages.remove(packageName)
        prefs.edit()
            .putStringSet(KEY_ENABLED, packages)
            .remove(KEY_SELECTED)
            .remove(KEY_EXCLUSIVE_MIGRATED)
            .apply()
    }

    fun originalMode(packageName: String): String? =
        prefs.getString(originalModeKey(packageName), null)

    /** Save before writing appops so a process death can still restore the exact previous mode. */
    fun rememberOriginalMode(packageName: String, mode: String) {
        if (originalMode(packageName) != null) return
        prefs.edit().putString(originalModeKey(packageName), mode).commit()
    }

    fun forgetOriginalMode(packageName: String) {
        prefs.edit().remove(originalModeKey(packageName)).apply()
    }

    /** One-time, lossless migration from the short-lived exclusive preference format. */
    fun migrateToMultiSelect() {
        val packages = enabledPackages()
        prefs.edit()
            .putStringSet(KEY_ENABLED, packages)
            .remove(KEY_SELECTED)
            .remove(KEY_EXCLUSIVE_MIGRATED)
            .apply()
    }

    /** Whether the user has dismissed the "may be unstable" warning banner. */
    fun isWarningDismissed(): Boolean = prefs.getBoolean(KEY_WARNING_DISMISSED, false)

    fun setWarningDismissed(dismissed: Boolean) {
        prefs.edit().putBoolean(KEY_WARNING_DISMISSED, dismissed).apply()
    }

    private companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_SELECTED = "selected_package"
        const val KEY_EXCLUSIVE_MIGRATED = "exclusive_mix_v2"
        const val KEY_WARNING_DISMISSED = "warning_dismissed"
        const val ORIGINAL_MODE_PREFIX = "original_mode:"

        fun originalModeKey(packageName: String) = ORIGINAL_MODE_PREFIX + packageName
    }
}
