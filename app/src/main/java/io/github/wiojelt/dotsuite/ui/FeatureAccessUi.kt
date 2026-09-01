package io.github.wiojelt.dotsuite.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.wiojelt.dotsuite.data.FeatureAccess
import io.github.wiojelt.dotsuite.data.FeatureCatalog
import io.github.wiojelt.dotsuite.data.FeatureVisibilityPolicy
import io.github.wiojelt.dotsuite.privileged.PrivilegedManager

internal data class RootFeatureVisibility(
    val automatic: Boolean,
    val visible: Boolean,
    val setRequested: (Boolean) -> Unit,
)

@Composable
internal fun rememberRootFeatureVisibility(): RootFeatureVisibility {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("appearance", Context.MODE_PRIVATE) }
    val setup by PrivilegedManager.setup.collectAsState()
    var requested by remember { mutableStateOf(prefs.getBoolean("show_root_features", false)) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "show_root_features") requested = prefs.getBoolean(key, false)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val uid = if (setup.accessGranted) setup.bridgeUid else null
    return RootFeatureVisibility(
        automatic = uid == 0,
        visible = FeatureVisibilityPolicy.showRoot(uid, requested),
        setRequested = {
            requested = it
            prefs.edit().putBoolean("show_root_features", it).apply()
        },
    )
}

@Composable
internal fun FeatureAccessBadge(access: FeatureAccess) {
    Surface(
        color = if (access == FeatureAccess.ROOT_MODULE) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (access == FeatureAccess.ROOT_MODULE) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(6.dp),
    ) {
        Text(access.label, style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp))
    }
}

@Composable
internal fun FeatureRequirementNote(page: String) {
    val entry = FeatureCatalog.entries.firstOrNull { it.page == page } ?: return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FeatureAccessBadge(entry.access)
        if (entry.access == FeatureAccess.ROOT_MODULE) {
            Text(entry.access.explanation, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun RootFeaturesSetting() {
    val visibility = rememberRootFeatureVisibility()
    if (visibility.automatic) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Show root features", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text("On · automatic", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 10.dp))
            }
            Text("Root bridge authorised. Module scopes still need to be enabled.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    ToggleRow(
        title = "Show root features",
        detail = "Include features that need Vector / LSPosed. Showing them does not grant root or enable any feature.",
        checked = visibility.visible, enabled = true,
        onCheckedChange = visibility.setRequested,
    )
}
