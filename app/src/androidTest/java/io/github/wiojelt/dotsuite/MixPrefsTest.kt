package io.github.wiojelt.dotsuite

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.wiojelt.dotsuite.data.MixPrefs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MixPrefsTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before @After fun clear() {
        context.getSharedPreferences("mix_audio", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun twoAppsRemainSelectedIndependently() {
        val prefs = MixPrefs(context)
        prefs.setEnabled("one.audio.app", true)
        prefs.setEnabled("two.audio.app", true)
        assertEquals(setOf("one.audio.app", "two.audio.app"), prefs.enabledPackages())

        prefs.setEnabled("one.audio.app", false)
        assertEquals(setOf("two.audio.app"), prefs.enabledPackages())
    }

    @Test fun exclusivePreferenceMigratesWithoutDroppingTheSelection() {
        context.getSharedPreferences("mix_audio", Context.MODE_PRIVATE).edit()
            .putString("selected_package", "legacy.audio.app").commit()
        val prefs = MixPrefs(context)
        prefs.migrateToMultiSelect()
        assertEquals(setOf("legacy.audio.app"), prefs.enabledPackages())
    }

    @Test fun originalModeSurvivesUntilTheOverrideIsRestored() {
        val prefs = MixPrefs(context)
        prefs.rememberOriginalMode("one.audio.app", "foreground")
        prefs.rememberOriginalMode("one.audio.app", "default")
        assertEquals("foreground", prefs.originalMode("one.audio.app"))
        prefs.forgetOriginalMode("one.audio.app")
        assertNull(prefs.originalMode("one.audio.app"))
    }
}
