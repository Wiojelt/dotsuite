package io.github.wiojelt.dotsuite.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.wiojelt.dotsuite.diagnostics.BugReport
import io.github.wiojelt.dotsuite.diagnostics.RecentDiagnostics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun BugReportScreen(padding: PaddingValues, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var events by remember { mutableStateOf(RecentDiagnostics.text()) }
    var include by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    val report = remember(events, include) { BugReport.snapshot(events, include) }
    fun send(email: Boolean) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                val uri = withContext(Dispatchers.IO) { BugReport.attachment(context, report) }
                val opened = if (email) BugReport.openEmail(context, uri) else BugReport.share(context, uri)
                status = if (opened) "Draft opened. Nothing is sent until you send it yourself."
                    else "No compatible email app. Use Share report or copy the preview."
            } catch (cancel: kotlinx.coroutines.CancellationException) {
                throw cancel
            } catch (error: Exception) {
                RecentDiagnostics.failure("report.prepare", error)
                status = "Report could not be prepared. Nothing was sent."
            } finally { busy = false }
        }
    }
    BugReportContent(padding, report, include, busy, status, onBack, { include = it },
        { events = RecentDiagnostics.text(); status = "Preview refreshed" }, { send(true) }, { send(false) })
}

@Composable
internal fun BugReportContent(padding: PaddingValues, report: String, include: Boolean, busy: Boolean, status: String,
    onBack: () -> Unit, onInclude: (Boolean) -> Unit, onRefresh: () -> Unit, onEmail: () -> Unit, onShare: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("bug-report-page")
        .padding(start = 20.dp, end = 20.dp, top = padding.calculateTopPadding() + 8.dp,
            bottom = padding.calculateBottomPadding() + 28.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        PageBackButton(onBack)
        ScreenHeader("Report a bug")
        Text("Review first. Your email app opens a draft addressed to ${BugReport.EMAIL}.")
        SoftGroup { ToggleRow("Include last 60 seconds", "App events and sanitised errors only. No PINs, notifications, clipboard, images or routes.",
            include, !busy, onInclude) }
        SectionLabel("Attachment preview")
        SelectionContainer {
            Text(report, Modifier.fillMaxWidth().heightIn(max = 240.dp).verticalScroll(rememberScrollState())
                .testTag("report-preview"), style = MaterialTheme.typography.bodySmall)
        }
        TextButton(onClick = onRefresh, enabled = !busy) { Text("Refresh last minute") }
        Button(onClick = onEmail, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("report-email")) { Text("Email report") }
        OutlinedButton(onClick = onShare, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Share report") }
        if (status.isNotEmpty()) Text(status, style = MaterialTheme.typography.bodySmall)
        Text("No automatic upload. The preview is frozen so the attachment contains exactly what you reviewed. Full SystemUI logs are not collected by this screen.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
