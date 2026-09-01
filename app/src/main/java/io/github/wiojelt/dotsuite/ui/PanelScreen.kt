package io.github.wiojelt.dotsuite.ui

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.wiojelt.dotsuite.data.FeatureFlags
import io.github.wiojelt.dotsuite.data.SoundSettings
import io.github.wiojelt.dotsuite.privileged.PrivilegedManager
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun PanelScreen(contentPadding: PaddingValues, snackbar: SnackbarHostState, onBack: (() -> Unit)? = null) {
    if (onBack != null) androidx.activity.compose.BackHandler(onBack = onBack)
    val supported = Build.VERSION.SDK_INT == 36 && Build.DEVICE == "Asteroids"
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val setup by PrivilegedManager.setup.collectAsState()
    var panelEnabled by remember {
        mutableStateOf(FeatureFlags.isEnabled(context, FeatureFlags.PANEL))
    }
    var settings by remember { mutableStateOf(SoundSettings.read(context)) }
    var timeoutDraft by remember { mutableFloatStateOf(settings.panelTimeoutMs / 1_000f) }

    LaunchedEffect(setup.ready) {
        panelEnabled = FeatureFlags.isEnabled(context, FeatureFlags.PANEL)
        settings = SoundSettings.read(context)
        timeoutDraft = settings.panelTimeoutMs / 1_000f
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

    val controlsEnabled = supported && setup.ready && panelEnabled
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
        item { ScreenHeader("Volume panel") }
        item { FeatureRequirementNote("panel") }

        item {
            SoftGroup {
                ToggleRow(
                    title = "Native app sliders",
                    detail = when {
                        !supported -> "Nothing Phone (3a) Pro / Android 16 required"
                        !setup.ready -> "Finish Shizuku / Sui access to change this"
                        panelEnabled -> "Configured on · requires the SystemUI module scope"
                        else -> "All module changes to the panel are off"
                    },
                    checked = panelEnabled,
                    enabled = supported && setup.ready,
                    onCheckedChange = { wanted ->
                        scope.launch {
                            if (FeatureFlags.setEnabled(FeatureFlags.PANEL, wanted)) {
                                panelEnabled = wanted
                            } else {
                                snackbar.showSnackbar("Complete Shizuku / Sui access first.")
                            }
                        }
                    },
                )
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Layout")
                SoftGroup {
                    SegmentedSetting(
                        title = "Panel side",
                        detail = "The drawer enters and expands from the selected edge.",
                        options = listOf(
                            SoundSettings.PanelSide.LEFT to "Left",
                            SoundSettings.PanelSide.AUTO to "Native",
                            SoundSettings.PanelSide.RIGHT to "Right",
                        ),
                        selected = settings.side,
                        enabled = controlsEnabled,
                        onSelect = { side ->
                            save(SoundSettings.PANEL_SIDE, side.value) { it.copy(side = side) }
                        },
                    )
                    SoftDivider()
                    ToggleRow(
                        title = "Expand for active apps",
                        detail = "Open the drawer automatically when app sliders are available.",
                        checked = settings.autoExpand,
                        enabled = controlsEnabled,
                        onCheckedChange = { wanted ->
                            save(SoundSettings.AUTO_EXPAND, if (wanted) 1 else 0) {
                                it.copy(autoExpand = wanted)
                            }
                        },
                    )
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Controls")
                SoftGroup {
                    ToggleRow(
                        title = "Live Caption",
                        detail = "Show the system caption shortcut in the panel.",
                        checked = settings.showCaptions,
                        enabled = controlsEnabled,
                        onCheckedChange = { wanted ->
                            save(SoundSettings.SHOW_CAPTIONS, if (wanted) 1 else 0) {
                                it.copy(showCaptions = wanted)
                            }
                        },
                    )
                    SoftDivider()
                    ToggleRow(
                        title = "Settings shortcut",
                        detail = "Show the system sound settings button.",
                        checked = settings.showSettings,
                        enabled = controlsEnabled,
                        onCheckedChange = { wanted ->
                            save(SoundSettings.SHOW_SETTINGS, if (wanted) 1 else 0) {
                                it.copy(showSettings = wanted)
                            }
                        },
                    )
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Timing")
                SoftGroup {
                    DiscreteSliderSetting(
                        title = "Visible for",
                        detail = "How long the panel waits before dismissing itself.",
                        valueLabel = "${timeoutDraft.roundToInt()}s",
                        value = timeoutDraft,
                        valueRange = 1f..10f,
                        steps = 8,
                        enabled = controlsEnabled,
                        onValueChange = { timeoutDraft = it.roundToInt().toFloat() },
                        onValueChangeFinished = {
                            val timeout = timeoutDraft.roundToInt().coerceIn(1, 10) * 1_000
                            save(SoundSettings.PANEL_TIMEOUT_MS, timeout) {
                                it.copy(panelTimeoutMs = timeout)
                            }
                        },
                    )
                }
            }
        }
    }
}
