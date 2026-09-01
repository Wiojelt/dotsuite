package io.github.wiojelt.dotsuite.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.wiojelt.dotsuite.data.PersonalizationPolicy as P
import io.github.wiojelt.dotsuite.data.SystemOptions
import io.github.wiojelt.dotsuite.privileged.PrivilegedManager

private val feedbackOptions = listOf(
    Triple(P.TOUCH_SOUNDS, "Touch sounds", "System interface clicks; app-specific sounds may differ."),
    Triple(P.DIAL_SOUNDS, "Dial pad tones", "Audible keypad feedback, not tones transmitted during a call."),
    Triple(P.LOCK_SOUNDS, "Lock / unlock sounds", "Native keyguard sound effects."),
    Triple(P.CHARGING_SOUNDS, "Charging sound", "The system's charger-connected feedback."),
    Triple(P.CHARGING_VIBRATION, "Charging vibration", "The system's vibration when charging starts."),
)

@Composable
internal fun FeedbackSettings(revision: Int, busy: Boolean, ready: Boolean,
    save: (String, String?) -> Unit, restore: (List<String>) -> Unit) {
    val context = LocalContext.current
    var values by remember { mutableStateOf<Map<String, PrivilegedManager.OptionRead>>(emptyMap()) }
    LaunchedEffect(revision, ready) {
        values = emptyMap()
        // These five AOSP flags are @Readable. Avoid spawning a shell query per visible row.
        // Writes and original-value capture still go through the verified privileged path.
        values = feedbackOptions.associate { entry ->
            entry.first to runCatching { PrivilegedManager.OptionRead(true, SystemOptions.read(context, entry.first)) }
                .getOrDefault(PrivilegedManager.OptionRead(false))
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("System feedback, without changing media, alarm or ringtone volume. Your exact original values are saved before editing.")
        feedbackOptions.forEach { (key, title, detail) ->
            val state = values[key]
            SoftGroup {
                // A missing Settings row is not silently interpreted as on or off.
                SegmentedSetting(title, detail,
                    listOf<String?>(null, "0", "1").map { it to when (it) { "0" -> "Off"; "1" -> "On"; else -> "Default" } },
                    state?.value, ready && state?.available == true && !busy) { save(key, it) }
            }
        }
        TextButton(enabled = ready && !busy, onClick = { restore(feedbackOptions.map { it.first }) }) { Text("Restore my original feedback settings") }
        Text("Default removes this override; it is different from restoring your saved original. Nothing OS or a third-party dialer can ignore individual options. No sounds are played to test them.", style = MaterialTheme.typography.bodySmall)
    }
}
