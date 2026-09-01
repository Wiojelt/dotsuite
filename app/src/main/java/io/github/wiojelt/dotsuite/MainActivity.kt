package io.github.wiojelt.dotsuite

import android.graphics.Color
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.wiojelt.dotsuite.data.MixPrefs
import io.github.wiojelt.dotsuite.data.OnboardingPrefs
import io.github.wiojelt.dotsuite.privileged.PrivilegedManager
import io.github.wiojelt.dotsuite.ui.MainScreen
import io.github.wiojelt.dotsuite.ui.OnboardingScreen
import io.github.wiojelt.dotsuite.ui.theme.DotSuiteTheme

class MainActivity : ComponentActivity() {

    private val prefs by lazy { MixPrefs(this) }
    private var featurePage by mutableStateOf<String?>(null)
    private var navigationRequest by mutableStateOf(0)
    private var bridgeHeld = false

    private fun featureFrom(intent: Intent?): String? = intent?.getStringExtra("feature_page")?.takeIf {
        it in io.github.wiojelt.dotsuite.data.FeatureCatalog.routes
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        featurePage = featureFrom(intent)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            DotSuiteTheme {
                val previewPage = intent.getIntExtra("preview_onboarding_page", -1)
                val onboardingPreview = BuildConfig.DEBUG && previewPage in 0..2
                var onboarding by remember {
                    mutableStateOf(onboardingPreview || !OnboardingPrefs.isComplete(this))
                }
                AnimatedContent(
                    targetState = onboarding,
                    transitionSpec = {
                        fadeIn(tween(240)).togetherWith(fadeOut(tween(180)))
                    },
                    label = "appFlow",
                ) { showOnboarding ->
                    if (showOnboarding) {
                        OnboardingScreen(
                            initialPage = if (onboardingPreview) previewPage else 0,
                            previewPendingAccess = onboardingPreview,
                            onFinished = {
                                if (!onboardingPreview) {
                                    OnboardingPrefs.markComplete(this@MainActivity)
                                }
                                onboarding = false
                            },
                        )
                    } else {
                        MainScreen(
                            prefs = prefs,
                            initialFeature = featurePage,
                            navigationRequest = navigationRequest,
                            onRunSetup = {
                                OnboardingPrefs.reset(this@MainActivity)
                                onboarding = true
                            },
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        PrivilegedManager.refresh()
    }

    override fun onStart() {
        super.onStart()
        if (!bridgeHeld) { bridgeHeld = true; PrivilegedManager.retainClient(this) }
    }

    override fun onStop() {
        if (bridgeHeld) { bridgeHeld = false; PrivilegedManager.releaseClient() }
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        featureFrom(intent)?.let { featurePage = it; navigationRequest++ }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (bridgeHeld) { bridgeHeld = false; PrivilegedManager.releaseClient() }
    }
}
