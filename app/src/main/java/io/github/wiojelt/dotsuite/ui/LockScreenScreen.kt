package io.github.wiojelt.dotsuite.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.wiojelt.dotsuite.data.SoundSettings
import io.github.wiojelt.dotsuite.privileged.PrivilegedManager
import kotlinx.coroutines.launch

@Composable
fun LockScreenScreen(contentPadding: PaddingValues, onBack: () -> Unit) {
    val context = LocalContext.current
    val setup by PrivilegedManager.setup.collectAsState()
    val scope = rememberCoroutineScope()
    var settings by remember { mutableStateOf(SoundSettings.read(context)) }

    LaunchedEffect(setup.ready) { settings = SoundSettings.read(context) }

    fun save(key: String, value: Boolean, update: (SoundSettings.Snapshot) -> SoundSettings.Snapshot) {
        scope.launch {
            if (SoundSettings.writeBoolean(key, value)) settings = update(settings)
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(
            start = 20.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            end = 20.dp,
            bottom = contentPadding.calculateBottomPadding() + 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) {
                Text("‹  ${LocalSectionTitle.current}")
            }
        }
        item {
            ScreenHeader(
                "Lock screen",
                "Privacy controls for Nothing's native PIN bouncer",
            )
        }
        item { FeatureRequirementNote("lockscreen") }
        item {
            SoftGroup {
                ToggleRow(
                    title = "Scramble PIN keypad",
                    detail = "Randomise all ten digits whenever the PIN screen opens.",
                    checked = settings.scramblePin,
                    enabled = setup.ready,
                    onCheckedChange = { wanted ->
                        save(SoundSettings.SCRAMBLE_PIN, wanted) { it.copy(scramblePin = wanted) }
                    },
                )
                SoftDivider()
                ToggleRow(
                    title = "Hide entered digits",
                    detail = "Show a dot immediately instead of briefly revealing each number.",
                    checked = settings.hidePinInput,
                    enabled = setup.ready,
                    onCheckedChange = { wanted ->
                        save(SoundSettings.HIDE_PIN_INPUT, wanted) { it.copy(hidePinInput = wanted) }
                    },
                )
            }
        }
        item {
            SoftGroup {
                ToggleRow(
                    title = "Soft Material keys",
                    detail = "Use subtle circular surfaces while keeping native haptics and motion.",
                    checked = settings.materialPinKeys,
                    enabled = setup.ready,
                    onCheckedChange = { wanted ->
                        save(SoundSettings.MATERIAL_PIN_KEYS, wanted) {
                            it.copy(materialPinKeys = wanted)
                        }
                    },
                )
            }
        }
        item {
            SectionLabel(
                if (setup.ready) "Saved settings need the SystemUI module scope"
                else "Connect Shizuku / Sui in Settings to change these settings",
            )
        }
    }
}
