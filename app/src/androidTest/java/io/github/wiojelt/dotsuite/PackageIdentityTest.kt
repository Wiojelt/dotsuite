package io.github.wiojelt.dotsuite

import android.content.pm.PackageManager
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class PackageIdentityTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun manifestAndModuleUseDotSuiteIdentity() {
        assertEquals("io.github.wiojelt.dotsuite", context.packageName)
        assertEquals("DotSuite", context.applicationInfo.loadLabel(context.packageManager).toString())
        assertEquals("io.github.wiojelt.dotsuite.systemui.DotSuiteHook",
            context.assets.open("xposed_init").bufferedReader().use { it.readText().trim() })
        val info = context.packageManager.getPackageInfo(context.packageName,
            PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or PackageManager.GET_PROVIDERS)
        // Debug tooling and CameraX contribute their own manifest components.
        val libraryActivities = setOf("androidx.compose.ui.tooling.PreviewActivity", "androidx.activity.ComponentActivity")
        val libraryServices = setOf("androidx.camera.core.impl.MetadataHolderService")
        val activities = info.activities.orEmpty().map { it.name }
        assertTrue(context.packageName + ".MainActivity" in activities)
        activities.forEach { assertTrue(it, it.startsWith(context.packageName + ".") || it in libraryActivities) }
        info.services.orEmpty().forEach { assertTrue(it.name, it.name.startsWith(context.packageName + ".") || it.name in libraryServices) }
        info.providers.orEmpty().forEach { assertTrue(it.authority, it.authority.startsWith(context.packageName + ".")) }
    }

    @Test fun aidlDescriptorBelongsToNewPackage() {
        assertEquals("io.github.wiojelt.dotsuite.IUserService", IUserService.DESCRIPTOR)
    }
}
