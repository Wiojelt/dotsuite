package io.github.wiojelt.dotsuite.data

import android.content.Context
import android.provider.Settings
import io.github.wiojelt.dotsuite.privileged.PrivilegedManager

/** Typed settings shared by the Compose UI, SystemUI hook and system-server key hook. */
object SoundSettings {
    const val PANEL_SIDE = "dotsuite_panel_side"
    const val SHOW_CAPTIONS = "dotsuite_show_captions"
    const val SHOW_SETTINGS = "dotsuite_show_settings"
    const val PANEL_TIMEOUT_MS = "dotsuite_panel_timeout_ms"
    const val AUTO_EXPAND = "dotsuite_auto_expand"
    const val VOLUME_STEP_PERCENT = "dotsuite_volume_step_percent"
    const val ALWAYS_MEDIA_VOLUME = "dotsuite_always_media_volume"
    const val SCRAMBLE_PIN = "dotsuite_scramble_pin"
    const val HIDE_PIN_INPUT = "dotsuite_hide_pin_input"
    const val MATERIAL_PIN_KEYS = "dotsuite_material_pin_keys"

    const val DEFAULT_PANEL_TIMEOUT_MS = 3_000

    enum class PanelSide(val value: Int) {
        AUTO(0), LEFT(1), RIGHT(2);

        companion object {
            fun from(value: Int) = entries.firstOrNull { it.value == value } ?: AUTO
        }
    }

    data class Snapshot(
        val side: PanelSide,
        val showCaptions: Boolean,
        val showSettings: Boolean,
        val panelTimeoutMs: Int,
        val autoExpand: Boolean,
        val volumeStepPercent: Int,
        val alwaysMediaVolume: Boolean,
        val scramblePin: Boolean,
        val hidePinInput: Boolean,
        val materialPinKeys: Boolean,
    )

    fun read(context: Context): Snapshot = Snapshot(
        side = PanelSide.from(readInt(context, PANEL_SIDE, PanelSide.AUTO.value)),
        showCaptions = readBoolean(context, SHOW_CAPTIONS, true),
        showSettings = readBoolean(context, SHOW_SETTINGS, true),
        panelTimeoutMs = normalizePanelTimeout(
            readInt(context, PANEL_TIMEOUT_MS, DEFAULT_PANEL_TIMEOUT_MS),
        ),
        autoExpand = readBoolean(context, AUTO_EXPAND, false),
        volumeStepPercent = readInt(context, VOLUME_STEP_PERCENT, 0)
            .takeIf { it in setOf(0, 5, 10, 15, 20) } ?: 0,
        alwaysMediaVolume = readBoolean(context, ALWAYS_MEDIA_VOLUME, false),
        scramblePin = readBoolean(context, SCRAMBLE_PIN, false),
        hidePinInput = readBoolean(context, HIDE_PIN_INPUT, true),
        materialPinKeys = readBoolean(context, MATERIAL_PIN_KEYS, false),
    )

    suspend fun write(key: String, value: Int): Boolean =
        PrivilegedManager.setSoundSetting(key, value)

    suspend fun writeBoolean(key: String, value: Boolean): Boolean =
        write(key, if (value) 1 else 0)

    /** Only whole seconds from 1 through 10 are accepted by the UI, service and hook. */
    fun normalizePanelTimeout(value: Int): Int =
        value.takeIf { it in 1_000..10_000 && it % 1_000 == 0 }
            ?: DEFAULT_PANEL_TIMEOUT_MS

    private fun readBoolean(context: Context, key: String, default: Boolean): Boolean =
        readInt(context, key, if (default) 1 else 0) != 0

    private fun readInt(context: Context, key: String, default: Int): Int =
        runCatching { Settings.Secure.getInt(context.contentResolver, key, default) }
            .getOrDefault(default)
}
