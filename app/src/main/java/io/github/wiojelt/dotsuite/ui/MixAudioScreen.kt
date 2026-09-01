package io.github.wiojelt.dotsuite.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilterChip
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.github.wiojelt.dotsuite.R
import io.github.wiojelt.dotsuite.data.AppInfo
import io.github.wiojelt.dotsuite.data.AppRepository
import io.github.wiojelt.dotsuite.data.MixPrefs
import io.github.wiojelt.dotsuite.i18n.strings
import io.github.wiojelt.dotsuite.privileged.PrivilegedManager
import io.github.wiojelt.dotsuite.ui.theme.successColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Material 3's disabled opacity, matching the greyed-out sections on the other tabs. */
private const val DISABLED_ALPHA = 0.38f

/**
 * How often the setup checklist re-measures Shizuku while it's on screen. Shizuku tells us when
 * its service starts or dies, but nothing tells us the user finished installing the app or
 * authorised us from inside Shizuku's own UI, and a checklist that needs the screen reopened
 * before it notices isn't a checklist.
 */
private const val SETUP_POLL_MS = 600L

/**
 * The audio-mixing tab. Hosted inside [MainScreen]'s scaffold, so it receives the shared
 * [contentPadding] and [snackbar] rather than owning its own.
 *
 * Mixing configures which apps participate in simultaneous playback. In the SystemUI-integrated
 * build the page remains available while Android's own volume panel is selected because that panel
 * now exposes DotSuite's per-app controls directly.
 */
@Composable
fun MixAudioScreen(
    contentPadding: PaddingValues,
    snackbar: SnackbarHostState,
    prefs: MixPrefs,
    onBack: (() -> Unit)? = null,
) {
    if (onBack != null) androidx.activity.compose.BackHandler(onBack = onBack)
    val s = strings()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val setup by PrivilegedManager.setup.collectAsState()

    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var enabled by remember { mutableStateOf(prefs.enabledPackages()) }
    var query by remember { mutableStateOf("") }
    var hideSystem by remember { mutableStateOf(true) }
    var warningDismissed by remember { mutableStateOf(prefs.isWarningDismissed()) }

    LaunchedEffect(setup.ready) {
        if (setup.ready && apps.isEmpty()) {
            prefs.migrateToMultiSelect()
            enabled = prefs.enabledPackages()
            apps = AppRepository.loadInstalledApps(context)
        }
    }

    // A car/USB safety event can restore focus while this page is visible. Reflect that external
    // change without keeping any process or observer alive after the page leaves composition.
    LaunchedEffect(setup.ready) {
        while (setup.ready) {
            val actual = prefs.enabledPackages()
            if (actual != enabled) enabled = actual
            delay(500)
        }
    }

    // Keep the checklist honest while the user is off installing, starting or authorising Shizuku.
    // Stop once mixing is live or while the app is in the background.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(setup.ready, lifecycleOwner) {
        if (setup.ready) return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                PrivilegedManager.refresh()
                delay(SETUP_POLL_MS)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                if (onBack != null) PageBackButton(onBack)
                ScreenHeader(
                    title = "App audio",
                    subtitle = "Select every app that may keep playing",
                )
            }

            if (!setup.ready) {
                SetupChecklist(setup = setup)
            } else {
                val filtered = remember(apps, query, hideSystem) {
                    apps.filter { app ->
                        (!hideSystem || !app.isSystem) &&
                            (query.isBlank() || app.label.contains(query, ignoreCase = true))
                    }
                }
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                ) {
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text(s.mixingSearchApps) },
                        singleLine = true,
                        shape = RoundedCornerShape(22.dp),
                        leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_search),
                                contentDescription = null,
                            )
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    FilterChip(
                        selected = hideSystem,
                        onClick = { hideSystem = !hideSystem },
                        label = { Text(s.mixingHideSystemApps) },
                    )
                    if (enabled.isNotEmpty()) {
                        TextButton(onClick = {
                            scope.launch {
                                val remaining = enabled.toMutableSet()
                                for (packageName in enabled) {
                                    val original = prefs.originalMode(packageName) ?: "default"
                                    if (PrivilegedManager.setAudioFocusMode(packageName, original)) {
                                        prefs.setEnabled(packageName, false)
                                        prefs.forgetOriginalMode(packageName)
                                        remaining.remove(packageName)
                                    }
                                }
                                enabled = remaining
                                snackbar.showSnackbar(
                                    if (remaining.isEmpty()) "All audio focus settings restored."
                                    else "Some apps couldn't be restored."
                                )
                            }
                        }) { Text("Restore all") }
                    }
                }
                if (!warningDismissed) {
                    WarningBanner(
                        onDismiss = {
                            prefs.setWarningDismissed(true)
                            warningDismissed = true
                        },
                    )
                }
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 24.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(filtered, key = { it.packageName }) { app ->
                        AppRow(
                            app = app,
                            isEnabled = enabled.contains(app.packageName),
                            onToggle = { want ->
                                scope.launch {
                                    if (want && PrivilegedManager.isAndroidAutoActive()) {
                                        snackbar.showSnackbar(
                                            "Android Auto is active. Disconnect it before enabling app mixing."
                                        )
                                        return@launch
                                    }
                                    val original = prefs.originalMode(app.packageName)
                                        ?: if (want) PrivilegedManager
                                            .getAudioFocusMode(app.packageName) else "default"
                                    val ok = if (original == null) false else {
                                        if (want) prefs.rememberOriginalMode(app.packageName, original)
                                        PrivilegedManager.setAudioFocusMode(
                                            app.packageName,
                                            if (want) "ignore" else original,
                                        )
                                    }
                                    if (ok) {
                                        prefs.setEnabled(app.packageName, want)
                                        if (!want) prefs.forgetOriginalMode(app.packageName)
                                        enabled = prefs.enabledPackages()
                                    } else {
                                        snackbar.showSnackbar(s.mixingCouldntUpdate(app.label))
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WarningBanner(onDismiss: () -> Unit) {
    val s = strings()
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_warning),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = s.mixingWarning,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    painter = painterResource(R.drawable.ic_close),
                    contentDescription = s.dismiss,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun AppRow(
    app: AppInfo,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        if (app.icon != null) {
            Image(
                bitmap = app.icon,
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.label,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = isEnabled, onCheckedChange = onToggle)
    }
}

/**
 * The three things that have to be true before mixing works, as a checklist that ticks itself off
 * while the user goes and does them. A step stays greyed out until the one above it is done —
 * there's nothing useful to do in it yet, and usually nothing to measure either: whether Shizuku
 * has authorised us is unknowable until its service is actually running.
 */
@Composable
private fun SetupChecklist(setup: PrivilegedManager.Setup) {
    val s = strings()
    val context = LocalContext.current
    val openShizuku: () -> Unit = {
        val launch = context.packageManager.getLaunchIntentForPackage(PrivilegedManager.PACKAGE_NAME)
        // No launcher activity means a broken install; the download page beats a dead button.
        if (launch != null) {
            runCatching { context.startActivity(launch) }
        } else {
            openShizukuReleases(context)
        }
    }

    // Strictly cumulative: a step counts as done only if everything above it is, and unlocks only
    // once the step above is done. Keying each step off its own fact instead let a later one report
    // green — or worse, unlock — while an earlier one was still red, which is nonsense to read and
    // nonsense to act on.
    val installed = setup.installed
    val running = installed && setup.serviceRunning
    val granted = running && setup.accessGranted && !setup.connectFailed

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = s.setupIntroShizuku,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                SetupStep(
                    number = 1,
                    title = if (installed) s.setupShizukuInstalled else s.setupShizukuNotInstalled,
                    detail = s.setupShizukuInstallDetail,
                    done = installed,
                    unlocked = true,
                    actionLabel = s.install,
                    onAction = { openShizukuReleases(context) },
                )
                SetupStep(
                    number = 2,
                    title = if (running) s.setupServiceRunning else s.setupServiceNotRunning,
                    detail = if (setup.serverUnusable) s.setupServerUnusableDetail
                    else s.setupServiceStartDetail,
                    done = running,
                    unlocked = installed,
                    actionLabel = if (setup.serverUnusable) s.setupRestartShizuku else s.setupSetUpNow,
                    onAction = openShizuku,
                )
                SetupStep(
                    number = 3,
                    title = if (granted) s.setupAccessGranted else s.setupGrantAccessTitle,
                    detail = when {
                        !running -> s.setupAccessDetail
                        setup.connectFailed -> s.setupConnectFailedDetail
                        setup.connecting -> s.setupConnectingDetail
                        else -> s.setupAccessDetail
                    },
                    done = granted,
                    unlocked = running,
                    actionLabel = if (running && setup.connectFailed) s.tryAgain else s.setupGrantAccess,
                    onAction = {
                        if (setup.connectFailed) {
                            PrivilegedManager.retry()
                        } else if (!PrivilegedManager.requestPermission()) {
                            // Nothing to prompt with — a pre-v11 Shizuku, or an earlier "deny and
                            // don't ask again". Authorising from inside Shizuku is the way through.
                            openShizuku()
                        }
                    },
                    busy = running && setup.connecting,
                )
            }
        }
    }
}

/** Shizuku's release page, the only official place to get it. */
private fun openShizukuReleases(context: Context) {
    val intent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("https://github.com/RikkaApps/Shizuku/releases"),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

/**
 * One checklist row: ticked green when [done], red and carrying its action while it's the step to
 * do next, greyed out with no action at all until [unlocked].
 */
@Composable
private fun SetupStep(
    number: Int,
    title: String,
    detail: String,
    done: Boolean,
    unlocked: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
    busy: Boolean = false,
) {
    val accent = when {
        done -> successColor
        unlocked -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (unlocked) 1f else DISABLED_ALPHA)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Icon(
            painter = painterResource(
                if (done) R.drawable.ic_check_circle else R.drawable.ic_circle_outline,
            ),
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = strings().setupStep(number, title),
                style = MaterialTheme.typography.bodyLarge,
                color = accent,
            )
            if (!done || busy) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (unlocked && !done && !busy) {
                Spacer(Modifier.height(10.dp))
                Button(onClick = onAction) { Text(actionLabel) }
            }
        }
        if (busy) {
            Spacer(Modifier.width(12.dp))
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
