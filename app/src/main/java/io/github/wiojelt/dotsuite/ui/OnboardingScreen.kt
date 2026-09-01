package io.github.wiojelt.dotsuite.ui

import android.content.Context
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.wiojelt.dotsuite.R
import io.github.wiojelt.dotsuite.config.AppConfig
import io.github.wiojelt.dotsuite.privileged.PrivilegedManager
import kotlinx.coroutines.delay

private val OnboardingRed = Color(0xFFE3262E)
private val OnboardingIvory = Color(0xFFF2F0E9)

private const val WELCOME = 0
private const val MODULE_SCOPE = 1
private const val PRIVILEGED_ACCESS = 2

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    initialPage: Int = WELCOME,
    previewPendingAccess: Boolean = false,
) {
    val context = LocalContext.current
    val realSetup by PrivilegedManager.setup.collectAsState()
    val setup = if (previewPendingAccess) {
        realSetup.copy(
            ready = false,
            connecting = false,
            connectFailed = false,
        )
    } else {
        realSetup
    }
    var page by rememberSaveable { mutableIntStateOf(initialPage) }
    var pendingShizukuPrompt by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(page, setup.ready, previewPendingAccess) {
        if (!previewPendingAccess && page == PRIVILEGED_ACCESS && setup.ready) {
            delay(900)
            onFinished()
        }
    }

    Surface(Modifier.fillMaxSize(), color = Color.Transparent, contentColor = MaterialTheme.colorScheme.onBackground) {
        io.github.wiojelt.dotsuite.ui.theme.AppBackdrop()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
        ) {
            OnboardingTopBar(page = page, onSkip = onFinished)
            AnimatedContent(
                targetState = page,
                transitionSpec = {
                    val direction = if (targetState > initialState) 1 else -1
                    (fadeIn(tween(220)) + slideInHorizontally(tween(260)) { it / 10 * direction })
                        .togetherWith(
                            fadeOut(tween(150)) +
                                slideOutHorizontally(tween(220)) { -it / 14 * direction },
                        )
                },
                label = "onboardingPage",
                modifier = Modifier.weight(1f),
            ) { targetPage ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                ) {
                    when (targetPage) {
                    WELCOME -> WelcomePage(onContinue = { page = MODULE_SCOPE })
                    MODULE_SCOPE -> ScopePage(onContinue = { page = PRIVILEGED_ACCESS })
                    else -> PrivilegedPage(
                        context = context,
                        setup = setup,
                        pendingShizukuPrompt = pendingShizukuPrompt,
                        setPendingShizukuPrompt = { pendingShizukuPrompt = it },
                    )
                    }
                }
            }
            ProgressRail(page = page)
        }
    }
}

@Composable
private fun OnboardingTopBar(page: Int, onSkip: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 16.dp, bottom = 10.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.ic_dotsuite_mark),
            contentDescription = null,
            modifier = Modifier.size(36.dp),
        )
        Text(
            text = AppConfig.APP_NAME,
            color = OnboardingIvory,
            fontWeight = FontWeight.Medium,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(start = 10.dp).weight(1f),
        )
        if (page != PRIVILEGED_ACCESS) {
            TextButton(onClick = onSkip) {
                Text("Skip", color = Color(0xFF9D9B96))
            }
        } else {
            TextButton(onClick = onSkip) {
                Text("Later", color = Color(0xFF9D9B96))
            }
        }
    }
}

@Composable
private fun WelcomePage(onContinue: () -> Unit) {
    Column(
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize().padding(vertical = 20.dp),
    ) {
        DotSuiteMark(modifier = Modifier.size(120.dp).align(Alignment.CenterHorizontally))
        Text(
            text = "DotSuite",
            color = OnboardingIvory,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Choose your tools. Enable only what you need.",
            color = Color(0xFFA9A7A2),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 28.dp),
        )
        PrimaryAction(text = "Continue", onClick = onContinue)
    }
}

@Composable
private fun ScopePage(onContinue: () -> Unit) {
    Column(modifier = Modifier.padding(top = 34.dp, bottom = 24.dp)) {
        StepLabel("Module access")
        Text(
            text = "Enable two scopes",
            color = OnboardingIvory,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            text = "Enable these scopes in Vector or LSPosed. Disable any previous module first; " +
                "do not run both together.",
            color = Color(0xFFA9A7A2),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 12.dp, bottom = 24.dp),
        )
        PermissionCard(
            title = "System UI",
            detail = "Adds per-app sliders to Nothing OS's own expanded volume panel.",
        )
        Spacer(Modifier.height(12.dp))
        PermissionCard(
            title = "System Framework",
            detail = "Optional screen-off media keys and conflict-aware power-button flashlight.",
        )
        Spacer(Modifier.height(26.dp))
        Text("StandBy and Quick dock do not need these scopes. Share-sheet filtering optionally uses " +
            "Intent Resolver; its setup is on the feature page.", style = MaterialTheme.typography.bodySmall,
            color = Color(0xFFA9A7A2))
        Spacer(Modifier.height(16.dp))
        PrimaryAction(text = "Continue", onClick = onContinue)
        Text(
            text = "A single reboot is required after adding these scopes.",
            color = Color(0xFF777570),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )
    }
}

@Composable
private fun PrivilegedPage(
    context: Context,
    setup: PrivilegedManager.Setup,
    pendingShizukuPrompt: Boolean,
    setPendingShizukuPrompt: (Boolean) -> Unit,
) {
    // A user may need to leave us to start Shizuku. Remember that their explicit button press is
    // still pending and raise the real permission dialog as soon as the binder becomes available.
    LaunchedEffect(pendingShizukuPrompt, setup.serviceRunning, setup.accessGranted) {
        if (!pendingShizukuPrompt) return@LaunchedEffect
        if (setup.accessGranted) {
            setPendingShizukuPrompt(false)
        } else if (setup.serviceRunning && PrivilegedManager.requestPermission()) {
            setPendingShizukuPrompt(false)
        }
    }
    val title = if (setup.ready) "Ready" else "Connect Shizuku"
    Column(modifier = Modifier.padding(top = 34.dp, bottom = 24.dp)) {
        StepLabel("Privileged access")
        Text(
            text = title,
            color = OnboardingIvory,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            text = if (setup.ready) {
                "Access granted. Opening the app…"
            } else {
                "Shizuku / Sui handles app controls. Module access is used separately for the " +
                    "native panel and screen-off keys."
            },
            color = Color(0xFFA9A7A2),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 12.dp, bottom = 24.dp),
        )

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(20.dp),
            ) {
                StatusOrb(done = setup.ready, busy = setup.connecting)
                Column(modifier = Modifier.padding(start = 16.dp)) {
                    Text(
                        text = if (setup.ready) "Connected" else "Shizuku / Sui",
                        color = OnboardingIvory,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.1.sp,
                    )
                    Text(
                        text = if (setup.ready) {
                            "Privileged helper is available"
                        } else {
                            "Binder connection · the app never calls su"
                        },
                        color = Color(0xFF8D8B87),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        if (!setup.ready) {
            Spacer(Modifier.height(24.dp))
            PrimaryAction(
                text = if (setup.connecting) "Connecting…" else "Connect Shizuku / Sui",
                enabled = !setup.connecting,
                onClick = {
                    setPendingShizukuPrompt(true)
                    if (openOrAuthorizeShizuku(context)) {
                        setPendingShizukuPrompt(false)
                    }
                },
            )
        }
    }
}

private fun openOrAuthorizeShizuku(context: Context): Boolean {
    if (PrivilegedManager.requestPermission()) return true
    val launch = context.packageManager.getLaunchIntentForPackage(PrivilegedManager.PACKAGE_NAME)
    if (launch != null) runCatching { context.startActivity(launch) }
    return false
}

@Composable
private fun PermissionCard(title: String, detail: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(18.dp), verticalAlignment = Alignment.Top) {
            Surface(color = OnboardingRed, shape = CircleShape,
                modifier = Modifier.padding(top = 6.dp).size(8.dp)) {}
            Column(modifier = Modifier.padding(start = 14.dp)) {
                Text(title, color = OnboardingIvory, fontWeight = FontWeight.SemiBold)
                Text(detail, color = Color(0xFF999792), style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

@Composable
private fun PrimaryAction(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = OnboardingRed,
            contentColor = Color.White,
            disabledContainerColor = Color(0xFF3D1416),
            disabledContentColor = Color(0xFF8A6C6D),
        ),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().height(58.dp),
    ) {
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun StepLabel(text: String) {
    Text(text, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium,
        style = MaterialTheme.typography.labelLarge)
}

@Composable
private fun StatusOrb(done: Boolean, busy: Boolean) {
    Surface(
        color = if (done) OnboardingIvory else Color(0xFF1D1D1D),
        shape = CircleShape,
        modifier = Modifier.size(52.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            when {
                busy -> CircularProgressIndicator(color = OnboardingRed, strokeWidth = 2.dp,
                    modifier = Modifier.size(24.dp))
                done -> Text("✓", color = Color.Black, fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.titleLarge)
                else -> Surface(color = OnboardingRed, shape = CircleShape,
                    modifier = Modifier.size(9.dp)) {}
            }
        }
    }
}

@Composable
private fun ProgressRail(page: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
    ) {
        repeat(3) { index ->
            Surface(
                color = if (index <= page) OnboardingRed else Color(0xFF2B2B2B),
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier.weight(1f).height(if (index == page) 4.dp else 2.dp),
            ) {}
        }
    }
}

@Composable
private fun DotSuiteMark(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.ic_dotsuite_mark),
        contentDescription = "DotSuite logo",
        modifier = modifier,
    )
}
