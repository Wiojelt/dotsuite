package io.github.wiojelt.dotsuite.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = OnPrimaryDark,
    primaryContainer = PrimaryContainerDark,
    onPrimaryContainer = OnPrimaryContainerDark,
    secondary = SecondaryDark,
    onSecondary = OnSecondaryDark,
    secondaryContainer = SecondaryContainerDark,
    onSecondaryContainer = OnSecondaryContainerDark,
    tertiary = TertiaryDark,
    onTertiary = OnTertiaryDark,
    tertiaryContainer = TertiaryContainerDark,
    onTertiaryContainer = OnTertiaryContainerDark,
    error = ErrorDark,
    onError = OnErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark,
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    surfaceContainerLowest = SurfaceContainerLowestDark,
    surfaceContainerLow = SurfaceContainerLowDark,
    surfaceContainer = SurfaceContainerDark,
    surfaceContainerHigh = SurfaceContainerHighDark,
    surfaceContainerHighest = SurfaceContainerHighestDark,
    inversePrimary = InversePrimaryDark,
    inverseSurface = InverseSurfaceDark,
    inverseOnSurface = InverseOnSurfaceDark,
)

@Composable
fun DotSuiteTheme(
    content: @Composable () -> Unit
) {
    val appearance = rememberAppearance()
    val motion = rememberMotionAllowed(appearance)
    val sound = rememberTouchSound(appearance.touchSounds)
    val colors = if (appearance.translucent) DarkColorScheme.copy(
        background = Color(0xFF0C0E11),
        surfaceContainerLow = Color(0xB521242A),
        surfaceContainer = Color(0xC52C3037),
        surfaceContainerHigh = Color(0xD5393D45),
        outlineVariant = Color(0x38C3C8D1),
    ) else DarkColorScheme
    CompositionLocalProvider(LocalAppearance provides appearance, LocalMotionAllowed provides motion, LocalTouchSound provides sound) {
    MaterialTheme(
        colorScheme = colors,
        typography = Typography,
        content = content
    )
    }
}
