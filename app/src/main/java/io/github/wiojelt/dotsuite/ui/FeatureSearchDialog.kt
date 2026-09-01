package io.github.wiojelt.dotsuite.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.wiojelt.dotsuite.R
import io.github.wiojelt.dotsuite.data.FeatureEntry
import io.github.wiojelt.dotsuite.ui.theme.LocalTouchSound
import io.github.wiojelt.dotsuite.ui.theme.TouchCue

@Composable
internal fun SearchDot(modifier: Modifier, enabled: Boolean, onClick: () -> Unit) {
    val sound = LocalTouchSound.current
    Surface(onClick = { sound(TouchCue.OPEN); onClick() }, enabled = enabled, modifier = modifier.testTag("hub-search-dot"),
        shape = CircleShape, color = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer,
        border = BorderStroke(.7.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(painterResource(R.drawable.ic_search), null, Modifier.size(28.dp))
            Text("Search", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
internal fun SearchListAction(enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().testTag("hub-search-list")) {
        Icon(painterResource(R.drawable.ic_search), null, Modifier.size(20.dp))
        Text("Search features", Modifier.padding(start = 10.dp))
    }
}

/** Search is opened by the centre dot; no persistent text field above the orbit. */
@Composable
internal fun FeatureSearchDialog(query: String, onQuery: (String) -> Unit, results: List<FeatureEntry>, hidden: Boolean,
    onClose: () -> Unit, onFeature: (String) -> Unit, onSettings: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val request = remember { FocusRequester() }
        val keyboard = LocalSoftwareKeyboardController.current
        val focus = LocalFocusManager.current
        fun releaseFocus() { keyboard?.hide(); focus.clearFocus() }
        LaunchedEffect(Unit) { request.requestFocus() }
        Surface(Modifier.fillMaxSize().testTag("feature-search-page"), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(horizontal = 20.dp)) {
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Search", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = { releaseFocus(); onClose() }, modifier = Modifier.testTag("hub-search-close")) { Text("Close") }
                }
                OutlinedTextField(query, onQuery, singleLine = true, placeholder = { Text("Find a feature") },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_search), null) },
                    trailingIcon = if (query.isNotEmpty()) { { TextButton(onClick = { onQuery("") }) { Text("Clear") } } } else null,
                    modifier = Modifier.fillMaxWidth().focusRequester(request).testTag("hub-search"))
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    results.groupBy { it.area }.forEach { (area, entries) ->
                        item(key = area.name) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                SectionLabel(area.title)
                                SoftGroup { entries.forEachIndexed { index, entry ->
                                    if (index > 0) SoftDivider()
                                    ActionRow(entry.title, entry.detail, entry.access) { releaseFocus(); onFeature(entry.page) }
                                } }
                            }
                        }
                    }
                    if (results.isEmpty()) item { Text("No matching feature", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    if (hidden) item { TextButton(onClick = { releaseFocus(); onSettings() }) { Text("Root features hidden · manage visibility") } }
                }
            }
        }
    }
}
