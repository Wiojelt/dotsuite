package io.github.wiojelt.dotsuite.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.wiojelt.dotsuite.data.AppearancePrefs
import io.github.wiojelt.dotsuite.data.HomeBackdrop
import io.github.wiojelt.dotsuite.ui.theme.LocalAppearance
import kotlin.math.roundToInt

@Composable
internal fun AppearanceScreen(padding: PaddingValues, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val options = LocalAppearance.current
    var duration by remember(options.transitionMs) { mutableFloatStateOf(options.transitionMs.toFloat()) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp, padding.calculateTopPadding() + 8.dp, 20.dp, padding.calculateBottomPadding() + 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item { PageBackButton(onBack) }
        item { ScreenHeader("Appearance & play") }
        item { SectionLabel("Dot screen background") }
        item { SoftGroup {
            HomeBackdrop.entries.forEachIndexed { index, mode ->
                if (index > 0) SoftDivider()
                Row(Modifier.fillMaxWidth().selectable(mode == options.backdrop, role = androidx.compose.ui.semantics.Role.RadioButton,
                    onClick = { AppearancePrefs.save(context, options.copy(backdrop = mode)) }).padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(mode.title, style = MaterialTheme.typography.bodyLarge)
                        Text(mode.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    RadioButton(selected = mode == options.backdrop, onClick = null)
                }
            }
        } }
        item { Text("Only behind the dot home screen. Pauses when hidden; Reduce motion and battery saver keep it still.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { SectionLabel("Interaction") }
        item { SoftGroup {
            ToggleRow("Touch sounds", "Original quiet clicks for navigation and switches. Off by default; respects silent mode, Do Not Disturb and system touch sounds.",
                options.touchSounds, true) { AppearancePrefs.save(context, options.copy(touchSounds = it)) }
        } }
        item { SoftGroup {
            ToggleRow("Translucent theme", "Frosted layers over DotSuite's own backdrop. Does not change the system theme.",
                options.translucent, true) { AppearancePrefs.save(context, options.copy(translucent = it)) }
            SoftDivider()
            ToggleRow("Russian roulette", "Flick the dots. The category under the pointer opens when the wheel stops. Never enables features.",
                options.roulette, true) { AppearancePrefs.save(context, options.copy(roulette = it)) }
            SoftDivider()
            ToggleRow("Reduce motion", "Use calm transitions and an instant category pick. System animation and battery-saver settings also take priority.",
                options.reduceMotion, true) { AppearancePrefs.save(context, options.copy(reduceMotion = it)) }
        } }
        item { SoftGroup {
            DiscreteSliderSetting("Portal duration", "Time for a dot to expand into a category and return.", "${duration.roundToInt()} ms",
                duration, 200f..800f, 11, !options.reduceMotion, { duration = (it / 50f).roundToInt() * 50f },
                { AppearancePrefs.save(context, options.copy(transitionMs = duration.roundToInt())) })
        } }
        item { Text("Dot view rotates with your gesture and slows naturally. Text stays upright; roulette blurs the dots only while moving. Cancel stops the draw. No sound or vibration loop.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { TextButton(onClick = { AppearancePrefs.save(context, io.github.wiojelt.dotsuite.data.AppearanceOptions()) }) { Text("Restore appearance defaults") } }
    }
}
