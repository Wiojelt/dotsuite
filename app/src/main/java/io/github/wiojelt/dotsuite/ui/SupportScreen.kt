package io.github.wiojelt.dotsuite.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.wiojelt.dotsuite.data.FeatureArea
import io.github.wiojelt.dotsuite.ui.theme.LocalMotionAllowed

internal const val SUPPORT_URL = "https://buymeacoffee.com/wiojelt"
internal val CoffeeAccent = Color(0xFFFFD879)
internal val CoffeeSurface = Color(0xDD3C3020)

/** The sole external action is an explicit support-button tap. Roulette only navigates here. */
internal fun openCoffeePage(context: Context): Boolean = runCatching {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SUPPORT_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
}.getOrDefault(false)

@Composable
internal fun SupportScreen(
    padding: PaddingValues,
    onBack: () -> Unit,
    backLabel: String = "DotSuite",
    onOpenSupport: (() -> Boolean)? = null,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val motion = LocalMotionAllowed.current
    val entrance = remember { Animatable(if (motion) 0f else 1f) }
    LaunchedEffect(motion) {
        if (motion) entrance.animateTo(1f, tween(320)) else entrance.snapTo(1f)
    }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(if (pressed && motion) .98f else 1f,
        tween(if (motion) 100 else 0), label = "coffee-button")
    var unavailable by remember { mutableStateOf(false) }
    fun visit() { unavailable = !(onOpenSupport?.invoke() ?: openCoffeePage(context)) }
    LazyColumn(Modifier.fillMaxSize().testTag("support-page"),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp,
            top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        item { Box(Modifier.fillMaxWidth()) {
            TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) { Text("‹  $backLabel", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } }
        item { Column(Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Surface(Modifier.size(144.dp).graphicsLayer {
                alpha = entrance.value
                scaleX = .94f + .06f * entrance.value
                scaleY = scaleX
            }, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerLow,
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Box(contentAlignment = Alignment.Center) { DotGlyph(FeatureArea.SUPPORT, Modifier.size(82.dp)) }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("A coffee for wiojelt", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                Text("Thank you for supporting DotSuite.", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        } }
        item { Column(Modifier.widthIn(max = 360.dp).fillMaxWidth().padding(top = 16.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = ::visit, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)
                .graphicsLayer { scaleX = pressScale; scaleY = pressScale }.testTag("coffee-external-link"),
                interactionSource = interaction,
                colors = ButtonDefaults.buttonColors(containerColor = CoffeeAccent, contentColor = Color(0xFF29200F))) {
                Text("Buy me a coffee  ↗")
            }
            TextButton(onClick = onBack, modifier = Modifier.testTag("coffee-maybe-later")) { Text("Maybe later", color = MaterialTheme.colorScheme.onSurface) }
            Text("Optional. No features are locked.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            if (unavailable) Text("No browser available.\nbuymeacoffee.com/wiojelt", style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } }
    }
}
