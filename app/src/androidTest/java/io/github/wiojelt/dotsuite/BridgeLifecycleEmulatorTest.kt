package io.github.wiojelt.dotsuite

import android.os.Build
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.wiojelt.dotsuite.privileged.PrivilegedManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BridgeLifecycleEmulatorTest {
    @Test fun backgroundReleasesHelperAndForegroundReconnectsRepeatedly() = runBlocking {
        Assume.assumeTrue(Build.HARDWARE == "ranchu" && InstrumentationRegistry.getArguments().getString("shizukuTests") == "true")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            repeat(3) {
                withTimeout(20_000) { PrivilegedManager.setup.first { it.ready } }
                scenario.moveToState(Lifecycle.State.CREATED)
                withTimeout(3000) { PrivilegedManager.setup.first { !it.ready && !it.connecting } }
                scenario.moveToState(Lifecycle.State.RESUMED)
            }
            withTimeout(20_000) { PrivilegedManager.setup.first { it.ready } }
        }
        withTimeout(3000) { PrivilegedManager.setup.first { !it.ready } }
        Unit
    }
}
