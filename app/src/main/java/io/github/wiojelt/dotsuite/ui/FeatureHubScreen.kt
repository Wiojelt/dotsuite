package io.github.wiojelt.dotsuite.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.wiojelt.dotsuite.R
import io.github.wiojelt.dotsuite.data.FeatureArea
import io.github.wiojelt.dotsuite.data.FeatureCatalog
import io.github.wiojelt.dotsuite.data.FeatureVisibilityPolicy

/** Category navigation with local search, accessible list fallback and input-driven orbit motion. */
@Composable
internal fun FeatureHubScreen(
    contentPadding: PaddingValues,
    onArea: (FeatureArea) -> Unit,
    onFeature: (String) -> Unit,
    onOrigin: (Offset, Float) -> Unit = { _, _ -> },
    active: Boolean = true,
) {
    val context = LocalContext.current
    val appearance = remember(context) { context.getSharedPreferences("appearance", Context.MODE_PRIVATE) }
    var listMode by rememberSaveable { mutableStateOf(appearance.getBoolean("feature_list", false)) }
    var query by rememberSaveable { mutableStateOf("") }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    val largeText = LocalDensity.current.fontScale > 1.25f
    val visibility = rememberRootFeatureVisibility()
    val candidates = remember(query) { FeatureCatalog.search(query) }
    val results = remember(candidates, visibility.visible) {
        candidates.filter { FeatureVisibilityPolicy.visible(it, visibility.visible) }
    }
    val density = LocalDensity.current
    var headerHeight by remember { mutableStateOf(38.dp) }
    var toolbarHeight by remember { mutableStateOf(48.dp) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val available = maxHeight - contentPadding.calculateTopPadding() - contentPadding.calculateBottomPadding() -
        52.dp - headerHeight - toolbarHeight - 48.dp
    LazyColumn(
        userScrollEnabled = active,
        modifier = Modifier.fillMaxSize().imePadding().testTag("feature-hub"),
        contentPadding = PaddingValues(
            start = 20.dp, end = 20.dp,
            top = contentPadding.calculateTopPadding() + 24.dp,
            bottom = contentPadding.calculateBottomPadding() + 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Row(Modifier.fillMaxWidth().onSizeChanged { headerHeight = with(density) { it.height.toDp() } }, verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.ic_dotsuite_mark), contentDescription = null,
                    modifier = Modifier.size(38.dp))
                Text("DotSuite", style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.padding(start = 12.dp).semantics { heading() })
            }
        }
        run {
            item {
                Row(Modifier.fillMaxWidth().onSizeChanged { toolbarHeight = with(density) { it.height.toDp() } }, verticalAlignment = Alignment.CenterVertically) {
                    Text("Explore", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    if (largeText) {
                        Text("List view", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else TextButton(enabled = active, onClick = {
                        listMode = !listMode
                        appearance.edit().putBoolean("feature_list", listMode).apply()
                    }, modifier = Modifier.testTag("hub-view-toggle")) {
                        Text(if (listMode) "Dot view" else "List view")
                    }
                }
            }
            if (listMode || largeText) {
                item { SearchListAction(active) { searchOpen = true } }
                item { SoftGroup {
                    FeatureArea.entries.forEachIndexed { index, area ->
                        if (index > 0) SoftDivider()
                        CategoryListRow(area, active, grouped = true) { onOrigin(Offset(-1f, -1f), 24f); onArea(area) }
                    }
                } }
            } else item {
                BoxWithConstraints(Modifier.fillMaxWidth().heightIn(min = available.coerceAtLeast(0.dp))
                    .testTag("dot-viewport"), contentAlignment = Alignment.Center) {
                    // On narrow split-screen windows use real text rows, not shrunken labels.
                    if (maxWidth < 300.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SearchListAction(active) { searchOpen = true }
                            FeatureArea.entries.forEach { area -> CategoryListRow(area, active) { onOrigin(Offset(-1f, -1f), 24f); onArea(area) } }
                        }
                    } else {
                        OrbitDots(onArea, onOrigin, active) { searchOpen = true }
                    }
                }
            }
        }
    }
    }
    if (searchOpen && active) FeatureSearchDialog(query, { query = it }, results, results.size < candidates.size,
        onClose = { searchOpen = false }, onFeature = onFeature, onSettings = { onArea(FeatureArea.SETTINGS) })
}

@Composable
internal fun CategoryDot(area: FeatureArea, modifier: Modifier, enabled: Boolean = true, highlighted: Boolean = false, onClick: () -> Unit) {
    val coffee = area == FeatureArea.SUPPORT
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val motion = io.github.wiojelt.dotsuite.ui.theme.LocalMotionAllowed.current
    val scale by animateFloatAsState(when { pressed -> .94f; highlighted -> 1.055f; else -> 1f },
        tween(if (motion) 130 else 0), label = "dotSelection")
    Surface(
        onClick = onClick, enabled = enabled, shape = CircleShape,
        interactionSource = interactions,
        color = if (coffee) CoffeeSurface else MaterialTheme.colorScheme.surfaceContainer,
        contentColor = if (coffee) CoffeeAccent else MaterialTheme.colorScheme.onSurface,
        modifier = modifier.graphicsLayer { scaleX = scale; scaleY = scale }.testTag("category-${area.name}")
            .then(if (coffee) Modifier.semantics { contentDescription = "Buy me a coffee" } else Modifier),
        border = if (io.github.wiojelt.dotsuite.ui.theme.LocalAppearance.current.translucent)
            androidx.compose.foundation.BorderStroke(if (highlighted) 1.4.dp else 0.7.dp,
                if (coffee || highlighted) CoffeeAccent.copy(alpha = .5f) else MaterialTheme.colorScheme.outlineVariant) else null,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(8.dp),
        ) {
            DotGlyph(area, Modifier.size(30.dp))
            Text(area.title, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 7.dp))
        }
    }
}

@Composable
private fun CategoryListRow(area: FeatureArea, enabled: Boolean = true, grouped: Boolean = false, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(if (grouped) 0.dp else 16.dp),
        color = if (grouped) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().testTag("category-${area.name}")) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            DotGlyph(area, Modifier.size(30.dp))
            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                Text(area.title, style = MaterialTheme.typography.titleMedium)
                Text(area.description, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("›", style = MaterialTheme.typography.titleLarge)
        }
    }
}

/** Original 7×7 dot-matrix symbols. Labels, not decorative pixels, provide semantics. */
@Composable
internal fun DotGlyph(area: FeatureArea, modifier: Modifier) {
    val color = LocalContentColor.current
    val rows = remember(area) { when (area) {
        FeatureArea.SOUND -> listOf("0001000", "0001010", "0101010", "0101010", "0101010", "0001010", "0001000")
        FeatureArea.SHORTCUTS -> listOf("0000100", "0001000", "0011110", "0111100", "0001000", "0010000", "0100000")
        FeatureArea.INTERFACE -> listOf("0111110", "1000001", "1000001", "1000001", "1000001", "1001001", "0111110")
        FeatureArea.STANDBY -> listOf("0011100", "0100010", "1001001", "1001101", "1000001", "0100010", "0011100")
        FeatureArea.CAMERA -> listOf("0011100", "0111110", "1000001", "1001001", "1010101", "1001001", "0111110")
        FeatureArea.TOOLS -> listOf("1100011", "1100011", "0000000", "0001000", "0000000", "1100011", "1100011")
        FeatureArea.SETTINGS -> listOf("0001000", "0101010", "0011100", "1110111", "0011100", "0101010", "0001000")
        FeatureArea.SUPPORT -> listOf("0101000", "0010100", "0000000", "1111110", "1001011", "1001010", "0111000")
    } }
    Canvas(modifier) {
        val step = size.minDimension / 7f
        rows.forEachIndexed { y, row ->
            row.forEachIndexed { x, pixel ->
                if (pixel == '1') drawCircle(color, step * .31f, Offset((x + .5f) * step, (y + .5f) * step))
            }
        }
    }
}
