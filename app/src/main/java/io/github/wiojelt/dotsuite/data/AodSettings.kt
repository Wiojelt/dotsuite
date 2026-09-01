package io.github.wiojelt.dotsuite.data

import io.github.wiojelt.dotsuite.privileged.PrivilegedManager
import java.time.LocalTime

data class AodSnapshot(
    val available: Boolean = false,
    val nothing: Boolean = false,
    val defaultEnabled: Boolean = false,
    val defaultMode: Int = 2,
    val allDay: Boolean = false,
    val schedule: Boolean = false,
    val tapMode: Boolean = false,
    val powerSaver: Boolean = false,
    val inverted: Boolean = false,
    val start: String? = null,
    val end: String? = null,
    val values: Map<String, String?> = emptyMap(),
    val writable: Set<String> = emptySet(),
    val error: String? = null,
) {
    val enabled get() = AodPolicy.enabled(values[AodPolicy.ENABLED], defaultEnabled)
    val mode get() = values[AodPolicy.MODE]?.toIntOrNull() ?: defaultMode
    fun status(at: LocalTime = LocalTime.now()): String = when {
        error != null -> error
        !available -> "Native AOD is unavailable on this device."
        !enabled -> "Native AOD is off. Wake gestures can still follow your system settings."
        powerSaver -> "Battery saver is active. Nothing OS can pause AOD; DotSuite does not override it."
        inverted -> "Colour inversion is active. Android can suppress AOD."
        nothing && mode == 2 -> "Tap-to-show selected. Nothing OS controls the timeout and sensors."
        nothing && mode == 1 -> {
            val from = AodPolicy.minute(start)
            val until = AodPolicy.minute(end)
            if (from == null || until == null) "Native schedule selected. Times are managed in Nothing Settings."
            else if (AodPolicy.insideWindow(at.hour * 60 + at.minute, from, until))
                "Inside your native schedule. Pocket detection and system power policy still take priority."
            else "Outside your native schedule. AOD can stay dark until the next window."
        }
        else -> "AOD enabled. Pocket detection and system power policy still take priority."
    }
}

object AodSettings {
    suspend fun read(): AodSnapshot {
        val caps = PrivilegedManager.aodCapabilities()
            ?: return AodSnapshot(error = "Connect Shizuku / Sui to read native AOD support.")
        val read = AodPolicy.KEYS.associateWith { PrivilegedManager.readSystemOption(it) }
        fun text(name: String) = if (caps.isNull(name)) null else caps.optString(name).takeIf { it.isNotEmpty() }
        return AodSnapshot(
            available = caps.optBoolean("available"), nothing = caps.optBoolean("nothing"),
            defaultEnabled = caps.optBoolean("defaultEnabled"), defaultMode = caps.optInt("defaultMode", 2),
            allDay = caps.optBoolean("allDay"), schedule = caps.optBoolean("schedule"),
            tapMode = caps.optBoolean("tapMode"), powerSaver = caps.optBoolean("powerSaver"),
            inverted = text("inversion") == "1", start = text("start"), end = text("end"),
            values = read.filterValues { it.available }.mapValues { it.value.value },
            writable = read.filterValues { it.available }.keys,
            error = if (caps.optBoolean("available") && read[AodPolicy.ENABLED]?.available != true)
                "AOD settings could not be read. Nothing changed." else null,
        )
    }
}
