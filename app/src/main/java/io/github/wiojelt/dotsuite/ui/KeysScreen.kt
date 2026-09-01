package io.github.wiojelt.dotsuite.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.wiojelt.dotsuite.data.FeatureFlags
import io.github.wiojelt.dotsuite.data.SoundSettings
import io.github.wiojelt.dotsuite.privileged.PrivilegedManager
import kotlinx.coroutines.launch

@Composable
fun KeysScreen(contentPadding: PaddingValues, snackbar: SnackbarHostState, onBack: (() -> Unit)? = null) {
    if (onBack != null) androidx.activity.compose.BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val setup by PrivilegedManager.setup.collectAsState()
    var screenOffEnabled by remember {
        mutableStateOf(FeatureFlags.isEnabled(context, FeatureFlags.SCREEN_OFF_KEYS))
    }
    var settings by remember { mutableStateOf(SoundSettings.read(context)) }

    LaunchedEffect(setup.ready) {
        screenOffEnabled = FeatureFlags.isEnabled(context, FeatureFlags.SCREEN_OFF_KEYS)
        settings = SoundSettings.read(context)
    }

    fun save(key: String, value: Int, apply: (SoundSettings.Snapshot) -> SoundSettings.Snapshot) {
        scope.launch {
            if (SoundSettings.write(key, value)) {
                settings = apply(settings)
            } else {
                snackbar.showSnackbar("Complete Shizuku / Sui access first.")
            }
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(
            start = 20.dp,
            top = contentPadding.calculateTopPadding() + 18.dp,
            end = 20.dp,
            bottom = contentPadding.calculateBottomPadding() + 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (onBack != null) item { PageBackButton(onBack) }
        item { ScreenHeader("Volume keys") }
        item { FeatureRequirementNote("keys") }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Volume change")
                SoftGroup {
                    SegmentedSetting(
                        title = "Step per press",
                        detail = effectiveStepDetail(settings.volumeStepPercent),
                        options = listOf(
                            0 to "Native",
                            5 to "5%",
                            10 to "10%",
                            15 to "15%",
                            20 to "20%",
                        ),
                        selected = settings.volumeStepPercent,
                        enabled = setup.ready,
                        onSelect = { percent ->
                            save(SoundSettings.VOLUME_STEP_PERCENT, percent) {
                                it.copy(volumeStepPercent = percent)
                            }
                        },
                    )
                    SoftDivider()
                    ToggleRow(
                        title = "Always control media",
                        detail = "Use media volume while the device is not in a call.",
                        checked = settings.alwaysMediaVolume,
                        enabled = setup.ready,
                        onCheckedChange = { wanted ->
                            save(SoundSettings.ALWAYS_MEDIA_VOLUME, if (wanted) 1 else 0) {
                                it.copy(alwaysMediaVolume = wanted)
                            }
                        },
                    )
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Screen off")
                SoftGroup {
                    ToggleRow(
                        title = "Track shortcuts",
                        detail = when {
                            !setup.ready -> "Finish Shizuku / Sui access to change this"
                            screenOffEnabled -> "Available while media is playing"
                            else -> "Disabled"
                        },
                        checked = screenOffEnabled,
                        enabled = setup.ready,
                        onCheckedChange = { wanted ->
                            scope.launch {
                                if (FeatureFlags.setEnabled(FeatureFlags.SCREEN_OFF_KEYS, wanted)) {
                                    screenOffEnabled = wanted
                                } else {
                                    snackbar.showSnackbar("Complete Shizuku / Sui access first.")
                                }
                            }
                        },
                    )
                    SoftDivider()
                    KeyRow(symbol = "+", title = "Hold volume up", detail = "Next track")
                    SoftDivider()
                    KeyRow(symbol = "−", title = "Hold volume down", detail = "Previous track")
                }
            }
        }

        item {
            Text(
                "Hold threshold  ·  550 ms",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

private fun effectiveStepDetail(percent: Int): String = when (percent) {
    5 -> "About 1 native level (6.25%) on this device."
    10 -> "About 2 native levels (12.5%) on this device."
    15 -> "About 3 native levels (18.75%) on this device."
    20 -> "About 4 native levels (25%) on this device."
    else -> "Use Nothing OS's standard one-level change."
}

@Composable
private fun KeyRow(symbol: String, title: String, detail: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = CircleShape,
            modifier = Modifier.size(38.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    symbol,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    style = MaterialTheme.typography.titleLarge,
                )
            }
        }
        Column(modifier = Modifier.padding(start = 14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                detail,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
