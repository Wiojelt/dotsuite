package io.github.wiojelt.dotsuite

import android.graphics.Path
import android.graphics.RectF
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import io.github.wiojelt.dotsuite.drawing.BackArrowRenderer
import io.github.wiojelt.dotsuite.ui.BackArrowScreen
import io.github.wiojelt.dotsuite.ui.theme.DotSuiteTheme
import org.junit.*
import org.junit.Assert.*

class BackArrowUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun allStylesAndMotionsDrawFiniteBoundedPathsAtEveryProgress() {
        val p = Path()
        val b = RectF()
        for (style in 1..15) for (motion in 0..11) for (size in listOf(80, 100, 120)) for (i in 0..100) {
            assertTrue(BackArrowRenderer.draw(p, style, motion, size, i * .42f, i * .36f, 6f, 42f))
            p.computeBounds(b, true)
            assertFalse(p.isEmpty)
            assertTrue(b.left.isFinite() && b.top.isFinite() && b.right.isFinite() && b.bottom.isFinite())
            assertTrue("style=$style motion=$motion size=$size progress=$i bounds=$b",
                b.left >= -12 && b.right <= 75 && b.top >= -60 && b.bottom <= 60)
        }
    }
    @Test fun stockAndInvalidInputDoNotMutateOriginalPath() {
        val p = Path().apply { addRect(10f, 20f, 30f, 40f, Path.Direction.CW) }
        val before = RectF().also { p.computeBounds(it, true) }
        for (style in listOf(-1, 0, 16)) assertFalse(BackArrowRenderer.draw(p, style, 0, 100, 20f, 20f, 2f, 20f))
        for (x in listOf(Float.NaN, Float.POSITIVE_INFINITY, -1f)) assertFalse(BackArrowRenderer.draw(p, 1, 0, 100, x, 20f, 2f, 20f))
        assertEquals(before, RectF().also { p.computeBounds(it, true) })
    }
    @Test fun roundCapsAndJoinsHaveNoWindingHoles() {
        val bitmap = android.graphics.Bitmap.createBitmap(100, 100, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val path = Path()
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
        assertTrue(BackArrowRenderer.draw(path, 1, 0, 100, 24f, 24f, 6f, 24f))
        canvas.translate(30f, 50f)
        canvas.drawPath(path, paint)
        for ((x, y) in listOf(30 to 50, 54 to 26, 54 to 74)) {
            assertTrue("Transparent cap/join at $x,$y", android.graphics.Color.alpha(bitmap.getPixel(x, y)) > 240)
        }
        bitmap.recycle()
    }
    @Test fun emulatorHasPreviewButNativeActivationIsDisabled() {
        compose.setContent { DotSuiteTheme { BackArrowScreen(PaddingValues(), {}) } }
        compose.onNodeWithText("Custom back arrow").assertIsNotEnabled()
        compose.onNodeWithTag("arrow-style-1").performScrollTo().performClick()
        compose.onNodeWithTag("arrow-style-1").assertIsSelected()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Unfold"))
        compose.onNodeWithText("Unfold").performScrollTo().performClick()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Icon size"))
        compose.onNodeWithText("Icon size").performScrollTo().assertIsDisplayed()
    }
    @Test fun previewChangesNeverWriteSystemSettingsAndRemainSelectable() {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val keys = io.github.wiojelt.dotsuite.data.BackArrowPolicy.KEYS
        val before = keys.associateWith { android.provider.Settings.Secure.getString(context.contentResolver, it) }
        compose.setContent { DotSuiteTheme { BackArrowScreen(PaddingValues(), {}) } }
        for (id in listOf(10, 15, 12)) {
            compose.onNodeWithTag("arrow-style-$id").performScrollTo().performClick().assertIsSelected()
        }
        assertEquals(before, keys.associateWith { android.provider.Settings.Secure.getString(context.contentResolver, it) })
    }
    @Test fun captureShapeAndMotionContactSheetFromTheActualRenderer() {
        val bitmap = android.graphics.Bitmap.createBitmap(1620, 1850, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.rgb(14, 15, 18))
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE; textSize = 22f
        }
        val path = Path()
        val motions = io.github.wiojelt.dotsuite.ui.backArrowMotions
        motions.forEachIndexed { i, s -> canvas.drawText(s, 165f + i * 110, 36f, paint) }
        for (style in 1..15) {
            val y = 65f + style * 110
            canvas.drawText(io.github.wiojelt.dotsuite.ui.backArrowStyles[style], 20f, y, paint)
            for (motion in 0..11) {
                // Mid-swipe makes geometry-dependent effects visible.
                BackArrowRenderer.draw(path, style, motion, 100, 27f, 28f, 5f, 42f)
                canvas.save(); canvas.translate(190f + motion * 110, y - 5)
                canvas.drawPath(path, paint); canvas.restore()
            }
        }
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        java.io.File(context.getExternalFilesDir(null), "dev8-back-arrow-shapes.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}
