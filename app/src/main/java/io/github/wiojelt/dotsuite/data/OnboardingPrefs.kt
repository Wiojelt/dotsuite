package io.github.wiojelt.dotsuite.data

import android.content.Context

/** Versioned first-run state so a future setup migration can be shown once without clearing data. */
object OnboardingPrefs {
    private const val FILE = "dotsuite_onboarding"
    private const val KEY_VERSION = "completed_version"
    private const val CURRENT_VERSION = 1

    fun isComplete(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getInt(KEY_VERSION, 0) >= CURRENT_VERSION

    fun markComplete(context: Context) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_VERSION, CURRENT_VERSION)
            .apply()
    }

    fun reset(context: Context) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_VERSION)
            .apply()
    }
}
