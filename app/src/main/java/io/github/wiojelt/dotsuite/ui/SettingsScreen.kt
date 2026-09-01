package io.github.wiojelt.dotsuite.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import io.github.wiojelt.dotsuite.data.FeatureArea
import io.github.wiojelt.dotsuite.data.MixPrefs
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.wiojelt.dotsuite.R
import io.github.wiojelt.dotsuite.config.AppConfig
import io.github.wiojelt.dotsuite.data.FeatureCatalog
import io.github.wiojelt.dotsuite.data.FeatureAccess
import io.github.wiojelt.dotsuite.data.FeatureVisibilityPolicy
import io.github.wiojelt.dotsuite.privileged.PrivilegedManager
import io.github.wiojelt.dotsuite.ui.theme.LocalMotionAllowed

private val CoffeeYellow = Color(0xFFFFDD00)

@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    onRunSetup: () -> Unit,
    initialPage: String = "home",
    navigationRequest: Int = 0,
    area: FeatureArea = FeatureArea.SETTINGS,
    prefs: MixPrefs? = null,
    snackbar: SnackbarHostState? = null,
    onBackToHub: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val motionAllowed = LocalMotionAllowed.current
    val actualPrefs = prefs ?: remember(context) { MixPrefs(context) }
    val actualSnackbar = snackbar ?: remember { SnackbarHostState() }
    var page by rememberSaveable { mutableStateOf(initialPage) }
    var search by rememberSaveable { mutableStateOf("") }
    var lastRequest by rememberSaveable { mutableStateOf(navigationRequest) }
    LaunchedEffect(page, area) {
        val safePage = page.takeIf { it in FeatureCatalog.routes || it in setOf("home", "about") } ?: "home"
        io.github.wiojelt.dotsuite.diagnostics.RecentDiagnostics.record("navigation", "${area.name}/$safePage")
    }
    LaunchedEffect(navigationRequest) {
        if (lastRequest != navigationRequest) {
            page = initialPage
            lastRequest = navigationRequest
        }
    }
    BackHandler(enabled = page != "home" || onBackToHub != null) {
        if (page != "home") page = "home" else onBackToHub?.invoke()
    }
    CompositionLocalProvider(LocalSectionTitle provides area.title) {
    AnimatedContent(
        targetState = page,
        transitionSpec = {
            val direction = if (targetState == "home") -1 else 1
            (fadeIn(tween(if (motionAllowed) 200 else 0)) + slideInHorizontally(tween(if (motionAllowed) 240 else 0)) { it / 10 * direction })
                .togetherWith(
                    fadeOut(tween(if (motionAllowed) 140 else 0)) +
                        slideOutHorizontally(tween(if (motionAllowed) 210 else 0)) { -it / 14 * direction },
                )
        },
        label = "settingsPage",
    ) { destination ->
        when (destination) {
            "support" -> SupportScreen(contentPadding, onBack = { page = "home" }, backLabel = area.title)
            "appearance" -> AppearanceScreen(contentPadding) { page = "home" }
            "aod" -> AodScreen(contentPadding) { page = "home" }
            "back-arrow" -> BackArrowScreen(contentPadding) { page = "home" }
            "bug-report" -> BugReportScreen(contentPadding) { page = "home" }
            "clock", "clock-style", "navigation", "rotation" -> NativeDisplayScreen(destination, contentPadding) { page = "home" }
            "panel" -> PanelScreen(contentPadding, actualSnackbar) { page = "home" }
            "apps" -> MixAudioScreen(contentPadding, actualSnackbar, actualPrefs) { page = "home" }
            "keys" -> KeysScreen(contentPadding, actualSnackbar) { page = "home" }
            "about" -> AboutScreen(contentPadding = contentPadding, onBack = { page = "home" })
            "lockscreen" -> LockScreenScreen(
                contentPadding = contentPadding,
                onBack = { page = "home" },
            )
            in FeatureCatalog.routes -> PersonalizationScreen(
                destination, contentPadding, onBack = { page = "home" },
            )
            else -> SettingsHome(
                    area = area,
                    onBackToHub = onBackToHub,
                    onOpenSettings = onOpenSettings,
                    contentPadding = contentPadding,
                    onRunSetup = onRunSetup,
                    onLockScreen = { page = "lockscreen" },
                    onAbout = { page = "about" },
                    onFeature = { page = it },
                    search = search,
                    onSearch = { search = it },
                )
        }
    }
    }
}

@Composable
private fun SettingsHome(
    area: FeatureArea,
    onBackToHub: (() -> Unit)?,
    onOpenSettings: (() -> Unit)?,
    contentPadding: PaddingValues,
    onRunSetup: () -> Unit,
    onLockScreen: () -> Unit,
    onAbout: () -> Unit,
    onFeature: (String) -> Unit,
    search: String,
    onSearch: (String) -> Unit,
) {
    val context = LocalContext.current
    val setup by PrivilegedManager.setup.collectAsState()
    val visibility = rememberRootFeatureVisibility()
    var pendingShizukuPrompt by rememberSaveable { mutableStateOf(false) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(search) { listState.scrollToItem(0) }

    LaunchedEffect(pendingShizukuPrompt, setup.serviceRunning, setup.accessGranted) {
        if (!pendingShizukuPrompt) return@LaunchedEffect
        if (setup.accessGranted ||
            (setup.serviceRunning && PrivilegedManager.requestPermission())
        ) {
            pendingShizukuPrompt = false
        }
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(
            start = 20.dp,
            top = contentPadding.calculateTopPadding() + 18.dp,
            end = 20.dp,
            bottom = contentPadding.calculateBottomPadding() + 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (onBackToHub != null) item {
            TextButton(onClick = onBackToHub, contentPadding = PaddingValues(0.dp)) {
                Text("‹  DotSuite", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item { ScreenHeader(area.title, area.description) }
        item {
            androidx.compose.material3.OutlinedTextField(search, onSearch, singleLine = true,
                placeholder = { Text("Find a feature") }, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
                trailingIcon = if (search.isNotEmpty()) { { TextButton(onClick = { onSearch("") }) { Text("Clear") } } } else null)
        }
        if (search.isBlank() && area == FeatureArea.SETTINGS) item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel("Access")
                SoftGroup {
                    ShizukuAccessRow(
                        status = when {
                            setup.ready -> "Connected"
                            setup.connecting -> "Connecting"
                            else -> "Permission needed"
                        },
                        connected = setup.ready,
                        onClick = {
                            pendingShizukuPrompt = true
                            if (requestOrOpenShizuku(context)) pendingShizukuPrompt = false
                        },
                    )
                    SoftDivider()
                    RootFeaturesSetting()
                }
                Text(when {
                    setup.accessGranted && setup.bridgeUid == 0 -> "Root bridge authorised (UID 0). No standard su calls."
                    setup.accessGranted && setup.bridgeUid == 2000 -> "Shizuku authorised through ADB (UID 2000)."
                    setup.accessGranted -> "Bridge authorised; identity is not yet available."
                    else -> "Connect Shizuku / Sui to detect the granted access."
                }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        val candidates = if (search.isBlank()) FeatureCatalog.entries.filter { it.area == area && it.page != "bug-report" } else FeatureCatalog.search(search)
        val results = candidates.filter { FeatureVisibilityPolicy.visible(it, visibility.visible) }
        results.groupBy { it.access }.toSortedMap(compareBy { it.ordinal }).forEach { (access, entries) ->
            item(key = access.name) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionLabel(access.label)
                    SoftGroup {
                        entries.forEachIndexed { index, entry ->
                            if (index > 0) SoftDivider()
                            ActionRow(entry.title, entry.detail, entry.access) {
                                if (entry.page == "lockscreen") onLockScreen() else onFeature(entry.page)
                            }
                        }
                    }
                }
            }
        }
        if (results.isEmpty()) item { Text("No matching feature", style = MaterialTheme.typography.bodyMedium) }
        if (results.size < candidates.size) item {
            Column {
                Text("Root features are hidden.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (onOpenSettings != null) TextButton(onClick = onOpenSettings) { Text("Manage access & visibility") }
            }
        }
        if (search.isBlank() && area == FeatureArea.SETTINGS) item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel("General")
                SoftGroup {
                    ActionRow(
                        title = "Setup",
                        detail = "Module scopes and Shizuku",
                        onClick = onRunSetup,
                    )
                    SoftDivider()
                    ActionRow(
                        title = "Report a bug",
                        detail = "Review and email the last 60 seconds of app logs",
                        onClick = { onFeature("bug-report") },
                    )
                    SoftDivider()
                    ActionRow(
                        title = "About",
                        detail = "Version ${AppConfig.VERSION_NAME}",
                        onClick = onAbout,
                    )
                }
            }
        }
    }
}

@Composable
private fun ShizukuAccessRow(
    status: String,
    connected: Boolean,
    onClick: () -> Unit,
) {
    Surface(onClick = onClick, color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Shizuku / Sui", style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f))
                    Text(
                        status,
                        color = if (connected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                Text(
                    "Binder bridge · the app never calls su",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(start = 12.dp))
        }
    }
}

@Composable
internal fun ActionRow(title: String, detail: String, access: FeatureAccess? = null, onClick: () -> Unit) {
    val sound = io.github.wiojelt.dotsuite.ui.theme.LocalTouchSound.current
    val area = remember(title) { FeatureCatalog.entries.firstOrNull { it.title == title }?.area }
    Surface(onClick = { sound(io.github.wiojelt.dotsuite.ui.theme.TouchCue.TAP); onClick() }, color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
        ) {
            if (area != null) Box(Modifier.padding(end = 14.dp).size(32.dp), contentAlignment = Alignment.Center) {
                DotGlyph(area, Modifier.size(26.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall)
                if (access != null) Box(Modifier.padding(top = 7.dp)) { FeatureAccessBadge(access) }
            }
            Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
private fun AboutScreen(contentPadding: PaddingValues, onBack: () -> Unit) {
    val context = LocalContext.current

    LazyColumn(
        contentPadding = PaddingValues(
            start = 20.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            end = 20.dp,
            bottom = contentPadding.calculateBottomPadding() + 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(22.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) {
                Text("‹  ${LocalSectionTitle.current}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        item { SupportBanner(onClick = { openSupportPage(context) }) }
        item { ActionRow("Project & release notes", "Private development repository · sign-in required") {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Viollje/dotsuite"))) }
        } }

        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Image(
                    painter = painterResource(R.drawable.ic_dotsuite_mark),
                    contentDescription = "DotSuite logo",
                    modifier = Modifier.size(78.dp),
                )
                Text(
                    AppConfig.APP_NAME,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
                Text(
                    "Version ${AppConfig.VERSION_NAME}  ·  wiojelt",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
                Text(
                    "Sound, shortcuts and small essentials for Nothing OS.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                )
            }
        }

    }
}

@Composable
private fun SupportBanner(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = CoffeeYellow,
        contentColor = Color(0xFF111111),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(18.dp),
        ) {
            Surface(color = Color(0xFFFFF3A8), shape = CircleShape,
                modifier = Modifier.size(48.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(R.drawable.ic_coffee),
                        contentDescription = null,
                        tint = Color(0xFF111111),
                        modifier = Modifier.size(25.dp),
                    )
                }
            }
            Column(modifier = Modifier.padding(start = 14.dp).weight(1f)) {
                Text("Buy me a coffee", fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium)
                Text("Support ongoing development", color = Color(0xFF4C4300),
                    style = MaterialTheme.typography.bodySmall)
            }
            Text("↗", fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleLarge)
        }
    }
}

private fun openSupportPage(context: Context) {
    openCoffeePage(context)
}

private fun requestOrOpenShizuku(context: Context): Boolean {
    if (PrivilegedManager.requestPermission()) return true
    val launch = context.packageManager.getLaunchIntentForPackage(PrivilegedManager.PACKAGE_NAME)
    if (launch != null) {
        runCatching { context.startActivity(launch) }
    } else {
        val web = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://github.com/RikkaApps/Shizuku/releases"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(web) }
    }
    return false
}
