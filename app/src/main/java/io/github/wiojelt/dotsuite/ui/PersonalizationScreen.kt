package io.github.wiojelt.dotsuite.ui

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.wiojelt.dotsuite.capture.CapturePreferences
import io.github.wiojelt.dotsuite.capture.CaptureService
import io.github.wiojelt.dotsuite.capture.CaptureShortcutActivity
import io.github.wiojelt.dotsuite.data.FeatureJournal
import io.github.wiojelt.dotsuite.data.PersonalizationPolicy as P
import io.github.wiojelt.dotsuite.data.SoundSettings
import io.github.wiojelt.dotsuite.data.SystemOptions
import io.github.wiojelt.dotsuite.privileged.PrivilegedManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private val gestureNames = listOf(P.NOTCH_TAP to "Single tap", P.NOTCH_DOUBLE to "Double tap",
    P.NOTCH_HOLD to "Touch & hold", P.NOTCH_LEFT to "Swipe left", P.NOTCH_RIGHT to "Swipe right")
private val actions = listOf(
    P.NONE to "None", P.SCREENSHOT to "Screenshot", P.FLASHLIGHT to "Flashlight",
    P.PLAY_PAUSE to "Play / pause", P.NEXT to "Next track", P.PREVIOUS to "Previous track",
    P.VOLUME_PANEL to "Volume panel", P.NOTIFICATIONS to "Notifications", P.QUICK_SETTINGS to "Quick settings",
    P.SLEEP to "Lock screen", P.CAMERA to "Open camera", P.PHOTO_FRONT to "Front camera · photo",
    P.PHOTO_REAR to "Rear camera · photo", P.VIDEO_FRONT to "Front camera · start / stop video",
    P.VIDEO_REAR to "Rear camera · start / stop video", P.QUICK_DOCK to "Quick dock")
private val animationKeys = listOf(P.WINDOW_SCALE to "Windows", P.TRANSITION_SCALE to "App transitions", P.ANIMATOR_SCALE to "Interface motion")

@Composable
fun PersonalizationScreen(page: String, contentPadding: PaddingValues, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val setup by PrivilegedManager.setup.collectAsState()
    val scope = rememberCoroutineScope()
    var revision by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) revision++ }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val nativeSupported = Build.VERSION.SDK_INT == 36 && Build.DEVICE == "Asteroids"
    val nativeReady = setup.ready && nativeSupported && !busy
    fun readInt(key: String, default: Int = 0): Int {
        @Suppress("UNUSED_VARIABLE") val trigger = revision
        return runCatching { Settings.Secure.getInt(context.contentResolver, key, default) }.getOrDefault(default)
    }
    fun saveInt(key: String, value: Int) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                status = SystemOptions.write(context, key, value.toString())
                revision++
            } finally { busy = false }
        }
    }
    fun saveString(key: String, value: String?) {
        if (busy) return
        busy = true
        scope.launch { status = SystemOptions.write(context, key, value); revision++; busy = false }
    }
    fun restore(keys: List<String>) {
        if (busy) return
        busy = true
        scope.launch { status = SystemOptions.restore(context, keys); revision++; busy = false }
    }

    val title = when (page) {
        "notch" -> "Notch gestures"
        "capture" -> "Camera shortcuts"
        "carrier" -> "Carrier label"
        "motion" -> "Motion"
        "hearing" -> "Hearing"
        "display" -> "Extra dim"
        "tiles" -> "Quick settings"
        "feedback" -> "Sounds & feedback"
        "recovery" -> "Restore changes"
        "maps" -> "Maps · Minimal mode"
        "standby" -> "StandBy"
        "dock" -> "Quick dock"
        "power" -> "Power button"
        "share" -> "Share sheet"
        "launcher" -> "Home screen & Icons (Home Up)"
        "duosb" -> "Status Bar (DuoSB & QuickStar)"
        "screen-anim" -> "Screen transitions & Lock (LockStar)"
        "navstar" -> "Navigation & Gestures (NavStar)"
        "soundassist" -> "Volume & Audio (SoundAssistant)"
        "security" -> "System & Privacy (FlagSecure)"
        else -> "Local diagnostics"
    }
    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.fillMaxSize(),
    ) {
        item { PageBackButton(onBack) }
        item { ScreenHeader(title) }
        item { FeatureRequirementNote(page) }
        if (page in setOf("notch", "carrier")) item {
            Text(if (nativeSupported) "Load this version in Vector / LSPosed. After updating, use Settings → Restore changes → Reload SystemUI if shortcuts do not respond."
                else "Native integration is only enabled for Nothing Phone (3a) Pro / Android 16.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        when (page) {
            "notch" -> {
                item { SoftGroup {
                    ToggleRow("Notch gestures", "Touch the camera cutout. Only while unlocked, in portrait.",
                        readInt(P.NOTCH_ENABLED) == 1, nativeReady) { saveInt(P.NOTCH_ENABLED, if (it) 1 else 0) }
                    SoftDivider()
                    ToggleRow("Haptic feedback", "Use the system's touch feedback preference.",
                        readInt(P.NOTCH_HAPTICS, 1) == 1, nativeReady) { saveInt(P.NOTCH_HAPTICS, if (it) 1 else 0) }
                } }
                item { SectionLabel("Actions") }
                items(gestureNames, key = { it.first }) { (key, label) ->
                    ActionPickerRow(label, readInt(key), nativeReady) { saveInt(key, it) }
                }
                item { SoftGroup {
                    ToggleRow("Status bar · double tap to lock", "Outside the notch. Uses the native screen-off animation.",
                        readInt(P.STATUS_DOUBLE_SLEEP) == 1, nativeReady) { saveInt(P.STATUS_DOUBLE_SLEEP, if (it) 1 else 0) }
                } }
                item { Text("Downward swipes still open notifications. Disabled during TalkBack touch exploration. Camera actions require separate opt-in in Camera shortcuts.", style = MaterialTheme.typography.bodySmall) }
                item { TextButton(enabled = nativeReady, onClick = {
                    busy = true
                    scope.launch {
                        val results = (gestureNames.map { it.first } + P.NOTCH_ENABLED + P.STATUS_DOUBLE_SLEEP).map { SoundSettings.write(it, 0) }
                        status = if (results.all { it }) "Gestures reset; stock touches restored" else "Some settings could not be reset"
                        revision++; busy = false
                    }
                }) { Text("Reset gestures") } }
            }
            "capture" -> {
                item { CaptureSettings() }
            }
            "carrier" -> {
                item {
                    var label by remember(revision) { mutableStateOf(SystemOptions.read(context, P.CARRIER_LABEL).orEmpty()) }
                    OutlinedTextField(label, { label = it }, label = { Text("Display name") }, singleLine = true,
                        supportingText = { Text("${label.codePointCount(0, label.length)} / 32 · blank uses the network name") },
                        isError = !P.acceptsString(P.CARRIER_LABEL, label.trim()), modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(enabled = nativeReady && P.acceptsString(P.CARRIER_LABEL, label.trim()),
                            onClick = { saveString(P.CARRIER_LABEL, label.trim().ifEmpty { null }) }) { Text("Save label") }
                        TextButton(enabled = nativeReady, onClick = { restore(listOf(P.CARRIER_LABEL)) }) { Text("Restore original") }
                    }
                }
                item { Text("Display-only, on native carrier surfaces that use CarrierTextManager. Does not change your SIM, subscription or mobile network. Emergency, no-SIM, airplane and satellite states keep their native wording. Applied on the next native carrier refresh.", style = MaterialTheme.typography.bodyMedium) }
            }
            "motion" -> {
                item { Text("System animation duration. 0× removes motion, 0.5× is faster, 1× is Android's usual scale. Your exact existing value is backed up before editing.", style = MaterialTheme.typography.bodyMedium) }
                items(animationKeys, key = { it.first }) { (key, label) ->
                    var value by remember(revision) { mutableFloatStateOf(SystemOptions.read(context, key)?.toFloatOrNull() ?: 1f) }
                    SoftGroup { DiscreteSliderSetting(label, "Changes timing, not the animation style.", "$value×", value.coerceIn(0f, 2f),
                        0f..2f, 7, setup.ready && !busy, { value = (it * 4).roundToInt() / 4f },
                        { saveString(key, value.toString()) }) }
                }
                item { TextButton(enabled = setup.ready && !busy, onClick = { restore(animationKeys.map { it.first }) }) { Text("Restore my original motion settings") } }
                item { Text("CRT, pixel-dissolve and launcher unlock choreography are not included in this build. They need device-specific transition hooks and an on-device recovery test; a speed slider cannot reproduce those effects.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            "hearing" -> {
                item { Text("Android's own output controls. No volume boost, equaliser, background audio processing or test sounds.", style = MaterialTheme.typography.bodyMedium) }
                item { SoftGroup {
                    val mono = remember(revision) { SystemOptions.read(context, P.MASTER_MONO) == "1" }
                    ToggleRow("Mono audio", "Combine left and right channels for single-ear listening.", mono,
                        setup.ready && !busy) { saveString(P.MASTER_MONO, if (it) "1" else "0") }
                } }
                item {
                    var balance by remember(revision) { mutableFloatStateOf(SystemOptions.read(context, P.MASTER_BALANCE)?.toFloatOrNull() ?: 0f) }
                    SoftGroup { DiscreteSliderSetting("Left / right balance",
                        "Center preserves both channels. Moving toward one side attenuates the other; it does not increase system volume.",
                        when { balance < 0f -> "L ${(balance * -100).roundToInt()}%"; balance > 0f -> "R ${(balance * 100).roundToInt()}%"; else -> "Center" },
                        balance.coerceIn(-1f, 1f), -1f..1f, 19, setup.ready && !busy,
                        { balance = (it * 10).roundToInt() / 10f }, { saveString(P.MASTER_BALANCE, balance.toString()) }) }
                    TextButton(enabled = setup.ready && !busy, onClick = { saveString(P.MASTER_BALANCE, "0.0") }) { Text("Center balance") }
                }
                item { TextButton(enabled = setup.ready && !busy,
                    onClick = { restore(listOf(P.MASTER_MONO, P.MASTER_BALANCE)) }) { Text("Restore original audio options") } }
                item { Text("Applies system-wide. Effects can differ with Bluetooth offload, spatial audio or device-specific DSP. Test with quiet media when your phone reconnects.", style = MaterialTheme.typography.bodySmall) }
            }
            "display" -> {
                item { ExtraDimSettings(revision, busy, setup.ready, ::saveString, ::restore) }
            }
            "tiles" -> {
                item {
                    val qsTranslucent = readInt(P.TRANSLUCENT_QS, 1) == 1
                    var qsAlpha by remember(revision) { mutableFloatStateOf(readInt(P.QS_TRANSPARENCY, 50).toFloat()) }
                    var qsStyle by remember(revision) { mutableIntStateOf(readInt(P.QS_TILE_STYLE, 1)) }
                    var qsAnim by remember(revision) { mutableIntStateOf(readInt(P.QS_ANIMATION_STYLE, 1)) }
                    SoftGroup {
                        ToggleRow("Translucent Quick Settings", "Match Nothing OS 3.0 translucent glass theme with blurred background and glass tiles.",
                            qsTranslucent, nativeReady) { saveInt(P.TRANSLUCENT_QS, if (it) 1 else 0) }
                        if (qsTranslucent) {
                            SoftDivider()
                            DiscreteSliderSetting("Glass transparency", "Adjust background blur and frosted glass opacity level.",
                                "${qsAlpha.roundToInt()}%", qsAlpha.coerceIn(10f, 100f), 10f..100f, 9, nativeReady,
                                { qsAlpha = (it / 10).roundToInt() * 10f },
                                { saveInt(P.QS_TRANSPARENCY, qsAlpha.roundToInt()) })
                            SoftDivider()
                            SegmentedSetting("Tile style", "Visual theme for Quick Settings capsules.",
                                listOf(1 to "Frosted Glass", 2 to "Liquid Glass", 3 to "Capsule", 0 to "Stock"),
                                qsStyle, nativeReady) {
                                qsStyle = it
                                saveInt(P.QS_TILE_STYLE, it)
                            }
                            SoftDivider()
                            SegmentedSetting("Transition animation", "Enter and exit dynamics for the QS shade.",
                                listOf(1 to "Spring", 2 to "Fade", 3 to "Pop", 0 to "Stock"),
                                qsAnim, nativeReady) {
                                qsAnim = it
                                saveInt(P.QS_ANIMATION_STYLE, it)
                            }
                        }
                    }
                }
                item { QuickTileSettings() }
            }
            "launcher" -> {
                item {
                    var rows by remember(revision) { mutableFloatStateOf(readInt(P.LAUNCHER_GRID_ROWS, 5).toFloat()) }
                    var cols by remember(revision) { mutableFloatStateOf(readInt(P.LAUNCHER_GRID_COLS, 5).toFloat()) }
                    var iconScale by remember(revision) { mutableFloatStateOf(readInt(P.LAUNCHER_ICON_SCALE, 100).toFloat()) }
                    val glassIcons = readInt(P.LAUNCHER_GLASS_ICONS, 0) == 1
                    var iconAlpha by remember(revision) { mutableFloatStateOf(readInt(P.LAUNCHER_ICON_ALPHA, 100).toFloat()) }
                    var iconShape by remember(revision) { mutableIntStateOf(readInt(P.LAUNCHER_ICON_SHAPE, 0)) }
                    SoftGroup {
                        DiscreteSliderSetting("Grid rows", "Number of vertical app icon rows on desktop.",
                            "${rows.roundToInt()} rows", rows.coerceIn(4f, 8f), 4f..8f, 4, nativeReady,
                            { rows = it.roundToInt().toFloat() },
                            { saveInt(P.LAUNCHER_GRID_ROWS, rows.roundToInt()) })
                        SoftDivider()
                        DiscreteSliderSetting("Grid columns", "Number of horizontal app icon columns on desktop.",
                            "${cols.roundToInt()} cols", cols.coerceIn(4f, 8f), 4f..8f, 4, nativeReady,
                            { cols = it.roundToInt().toFloat() },
                            { saveInt(P.LAUNCHER_GRID_COLS, cols.roundToInt()) })
                        SoftDivider()
                        DiscreteSliderSetting("Icon scale", "Resize home screen and app drawer icons.",
                            "${iconScale.roundToInt()}%", iconScale.coerceIn(70f, 140f), 70f..140f, 7, nativeReady,
                            { iconScale = (it / 10).roundToInt() * 10f },
                            { saveInt(P.LAUNCHER_ICON_SCALE, iconScale.roundToInt()) })
                        SoftDivider()
                        ToggleRow("Frosted glass icons", "Transform app icons into translucent frosted glass matching Nothing widgets.",
                            glassIcons, nativeReady) { saveInt(P.LAUNCHER_GLASS_ICONS, if (it) 1 else 0) }
                        if (glassIcons) {
                            SoftDivider()
                            DiscreteSliderSetting("Icon opacity", "Transparency level for desktop icons.",
                                "${iconAlpha.roundToInt()}%", iconAlpha.coerceIn(20f, 100f), 20f..100f, 8, nativeReady,
                                { iconAlpha = (it / 10).roundToInt() * 10f },
                                { saveInt(P.LAUNCHER_ICON_ALPHA, iconAlpha.roundToInt()) })
                        }
                        SoftDivider()
                        SegmentedSetting("Icon mask shape", "Geometric cutout style applied to launcher app icons.",
                            listOf(0 to "Stock", 1 to "Circle", 2 to "Squircle", 3 to "Rounded"),
                            iconShape, nativeReady) {
                            iconShape = it
                            saveInt(P.LAUNCHER_ICON_SHAPE, it)
                        }
                    }
                }
                item { Text("Applies to Nothing Launcher (com.nothing.launcher). Custom icon overrides from third-party icon packs can be selected in the upcoming icon pack picker.", style = MaterialTheme.typography.bodySmall) }
            }
            "duosb" -> {
                item {
                    val duoEnabled = readInt(P.DUO_STATUSBAR, 0) == 1
                    val batteryPercent = readInt(P.DUO_BATTERY_PERCENT, 1) == 1
                    val trafficEnabled = readInt(P.NETWORK_TRAFFIC, 0) == 1
                    val dt2s = readInt(P.STATUS_DOUBLE_SLEEP, 0) == 1
                    val brightnessSlider = readInt(P.STATUS_BAR_BRIGHTNESS, 0) == 1
                    var paddingStart by remember(revision) { mutableFloatStateOf(readInt(P.STATUS_PADDING_START, 0).toFloat()) }
                    var paddingEnd by remember(revision) { mutableFloatStateOf(readInt(P.STATUS_PADDING_END, 0).toFloat()) }
                    val clockSeconds = readInt(P.CLOCK_SECONDS, 0) == 1
                    val clockDay = readInt(P.CLOCK_DAY, 0) == 1
                    SoftGroup {
                        ToggleRow("Duo Status Bar capsule", "Unified pill capsule combining battery percentage and network indicator in Nothing aesthetic.",
                            duoEnabled, nativeReady) { saveInt(P.DUO_STATUSBAR, if (it) 1 else 0) }
                        if (duoEnabled) {
                            SoftDivider()
                            ToggleRow("Show battery percentage", "Display exact battery number inside the Duo capsule.",
                                batteryPercent, nativeReady) { saveInt(P.DUO_BATTERY_PERCENT, if (it) 1 else 0) }
                            SoftDivider()
                            ToggleRow("Network speed monitor", "Display live download/upload transfer speed in status bar.",
                                trafficEnabled, nativeReady) { saveInt(P.NETWORK_TRAFFIC, if (it) 1 else 0) }
                        }
                        SoftDivider()
                        ToggleRow("Double-tap status bar to sleep", "Turn off display by double tapping anywhere along the top edge.",
                            dt2s, nativeReady) { saveInt(P.STATUS_DOUBLE_SLEEP, if (it) 1 else 0) }
                        SoftDivider()
                        ToggleRow("Brightness gesture slider", "Slide horizontally across status bar to smoothly change screen brightness.",
                            brightnessSlider, nativeReady) { saveInt(P.STATUS_BAR_BRIGHTNESS, if (it) 1 else 0) }
                        SoftDivider()
                        DiscreteSliderSetting("Left margin padding", "Horizontal inset spacing on status bar start edge.",
                            "${paddingStart.roundToInt()} dp", paddingStart.coerceIn(0f, 32f), 0f..32f, 8, nativeReady,
                            { paddingStart = (it / 4).roundToInt() * 4f },
                            { saveInt(P.STATUS_PADDING_START, paddingStart.roundToInt()) })
                        SoftDivider()
                        DiscreteSliderSetting("Right margin padding", "Horizontal inset spacing on status bar end edge.",
                            "${paddingEnd.roundToInt()} dp", paddingEnd.coerceIn(0f, 32f), 0f..32f, 8, nativeReady,
                            { paddingEnd = (it / 4).roundToInt() * 4f },
                            { saveInt(P.STATUS_PADDING_END, paddingEnd.roundToInt()) })
                        SoftDivider()
                        ToggleRow("Status bar clock seconds", "Show ticking seconds (HH:mm:ss) on status bar clock.",
                            clockSeconds, nativeReady) { saveInt(P.CLOCK_SECONDS, if (it) 1 else 0) }
                        SoftDivider()
                        ToggleRow("Status bar weekday", "Prepend short day of week (e.g. Mon, Tue) before the clock.",
                            clockDay, nativeReady) { saveInt(P.CLOCK_DAY, if (it) 1 else 0) }
                    }
                }
                item { Text("Inspired by DuoSB (kvmy666) & NothingTweaks. Injects directly into PhoneStatusBarView without replacing system components.", style = MaterialTheme.typography.bodySmall) }
            }
            "navstar" -> {
                item {
                    val hidePill = readInt(P.HIDE_NAV_PILL, 0) == 1
                    val backKill = readInt(P.BACK_GESTURE_KILL, 0) == 1
                    var holdMs by remember(revision) { mutableFloatStateOf(readInt(P.BACK_GESTURE_TIMEOUT, 450).toFloat()) }
                    SoftGroup {
                        ToggleRow("Hide gesture bar line (Pill)", "Removes the navigation bar bottom bar and margin for full screen immersive display.",
                            hidePill, nativeReady) { saveInt(P.HIDE_NAV_PILL, if (it) 1 else 0) }
                        SoftDivider()
                        ToggleRow("Long press back to kill app", "Hold the edge back gesture to force-close the foreground application.",
                            backKill, nativeReady) { saveInt(P.BACK_GESTURE_KILL, if (it) 1 else 0) }
                        if (backKill) {
                            SoftDivider()
                            DiscreteSliderSetting("Hold duration to kill", "Time needed to hold edge back gesture before terminating app.",
                                "${holdMs.roundToInt()}ms", holdMs.coerceIn(200f, 1000f), 200f..1000f, 8, nativeReady,
                                { holdMs = (it / 50).roundToInt() * 50f },
                                { saveInt(P.BACK_GESTURE_TIMEOUT, holdMs.roundToInt()) })
                        }
                    }
                }
                item { Text("Equivalent to Samsung Good Lock NavStar. Injects directly into NavigationBarView and EdgeBackGestureHandler.", style = MaterialTheme.typography.bodySmall) }
            }
            "soundassist" -> {
                item {
                    var timeoutMs by remember(revision) { mutableFloatStateOf(readInt(P.VOLUME_PANEL_TIMEOUT, 3000).toFloat()) }
                    val disableSafeVol = readInt(P.DISABLE_SAFE_VOLUME, 0) == 1
                    SoftGroup {
                        DiscreteSliderSetting("Volume dialog timeout", "Duration the volume slider popup remains visible on screen.",
                            "${(timeoutMs / 1000).toString().take(3)}s (${timeoutMs.roundToInt()}ms)",
                            timeoutMs.coerceIn(500f, 10000f), 500f..10000f, 19, nativeReady,
                            { timeoutMs = (it / 500).roundToInt() * 500f },
                            { saveInt(P.VOLUME_PANEL_TIMEOUT, timeoutMs.roundToInt()) })
                        SoftDivider()
                        ToggleRow("Disable safe volume warning", "Prevent the high volume hearing damage warning popup when turning up audio.",
                            disableSafeVol, nativeReady) { saveInt(P.DISABLE_SAFE_VOLUME, if (it) 1 else 0) }
                    }
                }
                item { Text("Equivalent to Samsung Good Lock SoundAssistant. Injects directly into VolumeDialogImpl and AudioManager.", style = MaterialTheme.typography.bodySmall) }
            }
            "security" -> {
                item {
                    val bypassSecure = readInt(P.BYPASS_FLAG_SECURE, 0) == 1
                    SoftGroup {
                        ToggleRow("Bypass FLAG_SECURE", "Permit screenshots, screen mirroring and video recordings across protected banking and DRM apps.",
                            bypassSecure, nativeReady) { saveInt(P.BYPASS_FLAG_SECURE, if (it) 1 else 0) }
                    }
                }
                item { Text("Directly overrides WindowState.isSecureLocked in System Server. Fully reversible at any time.", style = MaterialTheme.typography.bodySmall) }
            }
            "screen-anim" -> {
                item {
                    var animStyle by remember(revision) { mutableIntStateOf(readInt(P.SCREEN_OFF_ANIM, 0)) }
                    val chargingInfo = readInt(P.LOCKSCREEN_CHARGING_INFO, 1) == 1
                    val scramblePin = readInt(P.SCRAMBLE_PIN, 0) == 1
                    val hidePin = readInt(P.HIDE_PIN_INPUT, 0) == 1
                    SoftGroup {
                        SegmentedSetting("Screen-off animation", "Transition effect when turning off display.",
                            listOf(0 to "Stock", 1 to "CRT TV", 2 to "Fade", 3 to "Scale"),
                            animStyle, nativeReady) {
                            animStyle = it
                            saveInt(P.SCREEN_OFF_ANIM, it)
                        }
                        SoftDivider()
                        ToggleRow("Lock screen charging wattage", "Show live power wattage (W), voltage (V) and current (mA) when charging.",
                            chargingInfo, nativeReady) { saveInt(P.LOCKSCREEN_CHARGING_INFO, if (it) 1 else 0) }
                        SoftDivider()
                        ToggleRow("Scramble PIN keypad", "Randomize the 0-9 digits on lock screen keypad on each unlock.",
                            scramblePin, nativeReady) { saveInt(P.SCRAMBLE_PIN, if (it) 1 else 0) }
                        SoftDivider()
                        ToggleRow("Hide PIN input dots", "Keep entered digits completely invisible to nearby eyes.",
                            hidePin, nativeReady) { saveInt(P.HIDE_PIN_INPUT, if (it) 1 else 0) }
                    }
                }
                item { Text("Screen-off transitions hook DisplayPowerController ColorFade. PIN privacy hooks KeyguardPINView natively.", style = MaterialTheme.typography.bodySmall) }
            }
            "feedback" -> {
                item { FeedbackSettings(revision, busy, setup.ready, ::saveString, ::restore) }
            }
            "recovery" -> {
                item { RecoverySettings() }
            }
            "maps" -> { item { MapsModeSettings(setup.ready) } }
            "standby" -> { item { StandbySettings(revision) } }
            "dock" -> { item { DockSettings() } }
            "power" -> {
                item { SoftGroup {
                    val torchEnabled = readInt(P.POWER_TORCH) == 1
                    var torchLevel by remember(revision) { mutableFloatStateOf(readInt(P.TORCH_LEVEL, 5).toFloat()) }
                    var caffeinateTime by remember(revision) { mutableIntStateOf(readInt(P.CAFFEINATE_TIMEOUT, 0)) }
                    ToggleRow("Screen-off long press for flashlight", "Uses only an unassigned native screen-off action.", torchEnabled,
                        nativeReady) { saveInt(P.POWER_TORCH, if (it) 1 else 0) }
                    if (torchEnabled) {
                        SoftDivider()
                        DiscreteSliderSetting("Flashlight intensity", "Brightness level for LED torch via Nothing CameraManager.",
                            "Level ${torchLevel.roundToInt()}", torchLevel.coerceIn(1f, 10f), 1f..10f, 9, nativeReady,
                            { torchLevel = it.roundToInt().toFloat() },
                            { saveInt(P.TORCH_LEVEL, torchLevel.roundToInt()) })
                    }
                    SoftDivider()
                    SegmentedSetting("Caffeinate keep-awake", "Prevent screen sleep while on dock or charging.",
                        listOf(0 to "Off", 5 to "5m", 10 to "10m", 30 to "30m", -1 to "Inf"),
                        caffeinateTime, nativeReady) {
                        caffeinateTime = it
                        saveInt(P.CAFFEINATE_TIMEOUT, it)
                    }
                    SoftDivider()
                    ToggleRow("Advanced power menu", "Add Recovery and Bootloader reboot options into power menu.",
                        readInt(P.ADVANCED_POWER_MENU) == 1, nativeReady) { saveInt(P.ADVANCED_POWER_MENU, if (it) 1 else 0) }
                } }
                item { SectionLabel("Battery protection") }
                item {
                    var capEnabled by remember(revision) { mutableStateOf(readInt(P.BATTERY_CAP_ENABLED) == 1) }
                    var capLevel by remember(revision) { mutableFloatStateOf(readInt(P.BATTERY_CAP_LEVEL, 80).toFloat()) }
                    SoftGroup {
                        ToggleRow("Charge limit", "Pauses charging via Nothing ICharge HAL when battery reaches target.",
                            capEnabled, nativeReady) {
                            capEnabled = it
                            saveInt(P.BATTERY_CAP_ENABLED, if (it) 1 else 0)
                        }
                        if (capEnabled) {
                            SoftDivider()
                            DiscreteSliderSetting("Stop level", "Battery holds charge at this percentage.", "${capLevel.roundToInt()}%",
                                capLevel.coerceIn(50f, 100f), 50f..100f, 10, nativeReady,
                                { capLevel = (it / 5).roundToInt() * 5f },
                                { saveInt(P.BATTERY_CAP_LEVEL, capLevel.roundToInt()) })
                        }
                    }
                }
                item { Text("Short press, double press, SOS, key combinations and screen-on long press stay native. If Nothing OS already assigns this screen-off gesture, DotSuite skips it. It never rewrites system button settings.", style = MaterialTheme.typography.bodySmall) }
                item { Text(when (readInt(P.POWER_TORCH_STATUS)) {
                    1 -> "Last attempt: flashlight changed"
                    2 -> "Conflict: Nothing OS owns screen-off long press. Native action preserved."
                    3 -> "Camera state initialised. Try the gesture once more."
                    4 -> "Camera / flashlight is currently unavailable."
                    else -> "Not verified on this phone yet. Requires module scope: System Framework."
                }) }
            }
            "share" -> {
                item { SoftGroup {
                    ToggleRow("Hide suggested contacts", "Removes Direct Share contacts, not the app targets.",
                        readInt(P.HIDE_DIRECT_SHARE) == 1, nativeReady) { saveInt(P.HIDE_DIRECT_SHARE, if (it) 1 else 0) }
                } }
                item { Text("Enable the module for Intent Resolver (com.android.intentresolver), then reopen the share sheet. Inspired by CleanShare; this version does not change global low-RAM detection, remove Quick Share or delete screenshots. App-specific share screens stay unchanged.", style = MaterialTheme.typography.bodySmall) }
                item { OutlinedButton(onClick = { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND)
                    .setType("text/plain").putExtra(Intent.EXTRA_TEXT, "DotSuite share sheet check"), "Share sheet check")) }) { Text("Open a sample share sheet") } }
            }
            else -> {
                item {
                    var journal by remember { mutableStateOf("Loading…") }
                    LaunchedEffect(Unit) { journal = withContext(Dispatchers.IO) { FeatureJournal.read(context) } }
                    Text("Last 60 seconds of app events and sanitised errors. No PINs, carrier label text, notification contents, images or routes are logged. Nothing is uploaded automatically.", style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = {
                        val report = "DotSuite ${io.github.wiojelt.dotsuite.config.AppConfig.VERSION_NAME}\n" +
                            "Android ${Build.VERSION.RELEASE} · ${Build.MANUFACTURER} ${Build.MODEL}\n\n" + journal
                        runCatching { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND)
                            .setType("text/plain").putExtra(Intent.EXTRA_TEXT, report), "Share diagnostics")) }
                    }) { Text("Share these diagnostics…") }
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        Text(journal, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 18.dp))
                    }
                }
            }
        }
        if (status.isNotEmpty()) item { Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
        if (page in setOf("notch", "carrier", "motion", "maps", "hearing", "display", "feedback") && !setup.ready) item { Text("Connect Shizuku / Sui in More to change these settings.", style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
internal fun ActionPickerRow(title: String, value: Int, enabled: Boolean, onSelect: (Int) -> Unit) {
    var choosing by rememberSaveable { mutableStateOf(false) }
    Surface(onClick = { choosing = true }, enabled = enabled, color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(actions.firstOrNull { it.first == value }?.second ?: "None", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (choosing) AlertDialog(onDismissRequest = { choosing = false }, title = { Text(title) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(actions, key = { it.first }) { (id, label) ->
                    Surface(onClick = { onSelect(id); choosing = false }, color = MaterialTheme.colorScheme.surface) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            RadioButton(selected = id == value, onClick = null)
                            Text(label, Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { choosing = false }) { Text("Cancel") } })
}

@Composable
private fun CaptureSettings() {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(CapturePreferences.enabled(context)) }
    var audio by remember { mutableStateOf(CapturePreferences.audio(context)) }
    var minutes by remember { mutableFloatStateOf(CapturePreferences.minutes(context).toFloat()) }
    var glyphSeconds by remember { mutableIntStateOf(CapturePreferences.glyphSeconds(context)) }
    var tileAction by remember { mutableIntStateOf(CapturePreferences.tileAction(context)) }
    val state by CaptureService.state.collectAsState()
    val supported = Build.VERSION.SDK_INT >= 30
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("Preview-free capture with Android's camera indicator and a permanent Stop notification. A brief launch sheet is needed to start safely. Saved only to your gallery.")
        SoftGroup {
            ToggleRow("Enable camera shortcuts", "Only explicit taps start capture. Never starts on boot or by itself.", enabled, supported) {
                enabled = it; CapturePreferences.setEnabled(context, it)
                if (!it && state.busy) context.startService(Intent(context, CaptureService::class.java).setAction(CaptureService.STOP))
            }
            SoftDivider()
            ToggleRow("Record microphone audio", "Off by default. Android asks for microphone permission when needed.", audio, enabled && !state.busy) {
                audio = it; CapturePreferences.setAudio(context, it)
            }
        }
        SoftGroup { DiscreteSliderSetting("Recording limit", "Stops automatically; also capped at 512 MB.", "${minutes.roundToInt()} min", minutes,
            1f..30f, 28, enabled && !state.busy, { minutes = it.roundToInt().toFloat() },
            { CapturePreferences.setMinutes(context, minutes.roundToInt()) }) }
        SoftGroup {
            SegmentedSetting("Glyph photo countdown", "Rear photos only. A temporary Glyph session; privacy indicators remain visible.",
                listOf(0 to "Off", 3 to "3s", 5 to "5s", 10 to "10s"), glyphSeconds,
                enabled && !state.busy && io.github.wiojelt.dotsuite.capture.GlyphCountdown.supported()) {
                glyphSeconds = it; CapturePreferences.setGlyphSeconds(context, it)
            }
        }
        Text("Glyph countdown targets Phone (3a) / (3a) Pro on Android 16+. An emulator cannot verify the rear lights.", style = MaterialTheme.typography.bodySmall)
        SectionLabel("Try a shortcut")
        listOf(P.PHOTO_FRONT to "Front photo", P.PHOTO_REAR to "Rear photo",
            P.VIDEO_FRONT to "Front video", P.VIDEO_REAR to "Rear video").chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (action, label) ->
                    OutlinedButton(enabled = enabled && !state.busy, modifier = Modifier.weight(1f), onClick = {
                        context.startActivity(Intent(context, CaptureShortcutActivity::class.java).putExtra("capture_action", action))
                    }) { Text(label) }
                }
            }
        }
        if (state.busy) Button(onClick = { context.startService(Intent(context, CaptureService::class.java).setAction(CaptureService.STOP)) }) { Text("Stop capture") }
        SectionLabel("Quick settings tile")
        SoftGroup {
            SegmentedSetting("Camera", "Used by the Camera shortcut tile.", listOf(true to "Front", false to "Rear"),
                P.isFront(tileAction), !state.busy) { front ->
                tileAction = if (P.isVideo(tileAction)) { if (front) P.VIDEO_FRONT else P.VIDEO_REAR }
                    else { if (front) P.PHOTO_FRONT else P.PHOTO_REAR }
                CapturePreferences.setTileAction(context, tileAction)
            }
            SegmentedSetting("Capture", "Tap again or use the notification to stop video.", listOf(false to "Photo", true to "Video"),
                P.isVideo(tileAction), !state.busy) { video ->
                tileAction = if (P.isFront(tileAction)) { if (video) P.VIDEO_FRONT else P.PHOTO_FRONT }
                    else { if (video) P.VIDEO_REAR else P.PHOTO_REAR }
                CapturePreferences.setTileAction(context, tileAction)
            }
        }
        if (state.message.isNotEmpty()) Text(state.message, style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = {
            val intent = if (Build.VERSION.SDK_INT >= 26) Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${context.packageName}"))
            runCatching { context.startActivity(intent) }
        }) { Text("Notification permissions") }
        Text("Experimental until tested on your phone. Video uses HD quality where available. Switching apps keeps recording; removing the task requests a stop. Locked-screen starts and secure-screen capture bypasses are not supported.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ExtraDimSettings(revision: Int, busy: Boolean, ready: Boolean,
    save: (String, String?) -> Unit, restore: (List<String>) -> Unit) {
    var available by remember { mutableStateOf(false) }
    var activated by remember { mutableStateOf(false) }
    var level by remember { mutableFloatStateOf(50f) }
    LaunchedEffect(revision, ready) {
        available = false
        if (ready) {
            val flag = PrivilegedManager.readSystemOption(P.EXTRA_DIM)
            val intensity = PrivilegedManager.readSystemOption(P.EXTRA_DIM_LEVEL)
            available = flag.available && intensity.available
            activated = flag.value == "1"
            level = intensity.value?.toFloatOrNull() ?: 50f
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("Uses Android's native color dimming. No dark overlay and no background drawing service.")
        SoftGroup {
            ToggleRow("Extra dim", "Your original state is saved before the first change.", activated, available && !busy) {
                save(P.EXTRA_DIM, if (it) "1" else "0")
            }
            SoftDivider()
            DiscreteSliderSetting("Intensity", "The slider is capped at 80% to keep the screen readable.", "${level.roundToInt()}%",
                level.coerceIn(0f, 80f), 0f..80f, 15, available && !busy,
                { level = (it / 5).roundToInt() * 5f }, { save(P.EXTRA_DIM_LEVEL, level.roundToInt().toString()) })
        }
        TextButton(enabled = available && !busy, onClick = { restore(listOf(P.EXTRA_DIM, P.EXTRA_DIM_LEVEL)) }) { Text("Restore original dimming settings") }
        if (!available) Text(if (ready) "This OS did not expose native Extra dim support. No changes will be made."
            else "Connect Shizuku / Sui to check device support.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun QuickTileSettings() {
    val context = LocalContext.current
    var status by remember { mutableStateOf("") }
    val entries = listOf(
        Triple("Native AOD", ".tiles.AodTile", io.github.wiojelt.dotsuite.R.drawable.ic_qs_aod),
        Triple("Mono audio", ".tiles.MonoAudioTile", io.github.wiojelt.dotsuite.R.drawable.ic_qs_mono),
        Triple("Extra dim", ".tiles.ExtraDimTile", io.github.wiojelt.dotsuite.R.drawable.ic_qs_dim),
        Triple("Camera shortcut", ".tiles.CameraCaptureTile", io.github.wiojelt.dotsuite.R.drawable.ic_qs_camera),
        Triple("Maps MinMode", ".tiles.MapsMinModeTile", io.github.wiojelt.dotsuite.R.drawable.ic_qs_maps),
        Triple("Quick dock", ".tiles.QuickDockTile", io.github.wiojelt.dotsuite.R.drawable.ic_qs_dock))
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Add only the tiles you need. Native AOD, Mono audio and Extra dim use Shizuku / Sui. Camera uses normal camera permissions. Maps opens its experimental controls.")
        entries.forEach { (title, suffix, icon) ->
            FeatureAccessBadge(when (suffix) {
                ".tiles.CameraCaptureTile" -> io.github.wiojelt.dotsuite.data.FeatureAccess.PERMISSION
                ".tiles.QuickDockTile" -> io.github.wiojelt.dotsuite.data.FeatureAccess.ON_DEVICE
                else -> io.github.wiojelt.dotsuite.data.FeatureAccess.BRIDGE
            })
            OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = {
                if (Build.VERSION.SDK_INT >= 33) {
                    runCatching { context.getSystemService(android.app.StatusBarManager::class.java).requestAddTileService(
                        android.content.ComponentName(context.packageName, context.packageName + suffix), title,
                        android.graphics.drawable.Icon.createWithResource(context, icon), context.mainExecutor,
                    ) { result -> status = when (result) {
                        android.app.StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> "$title added"
                        android.app.StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> "$title is already added"
                        else -> "Tile not added. You can also add it from the Quick Settings editor."
                    } } }.onFailure { status = "Open the Quick Settings editor to add this tile." }
                } else status = "Open the Quick Settings editor and drag $title into the active area."
            }) { Text("Add $title") }
        }
        if (status.isNotEmpty()) Text(status, style = MaterialTheme.typography.bodySmall)
        Text("Camera tile starts require an unlocked phone. Stopping an existing capture is always available. The app does not add or rearrange tiles without your confirmation.", style = MaterialTheme.typography.bodySmall)
    }
}
