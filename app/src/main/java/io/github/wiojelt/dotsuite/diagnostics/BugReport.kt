package io.github.wiojelt.dotsuite.diagnostics

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import io.github.wiojelt.dotsuite.config.AppConfig
import io.github.wiojelt.dotsuite.privileged.PrivilegedManager
import java.io.File
import java.util.UUID

object BugReport {
    const val EMAIL = "wiojelt@wiojelt.onmicrosoft.com"
    fun snapshot(events: String, includeLogs: Boolean): String = buildString {
        appendLine("DotSuite ${AppConfig.VERSION_NAME} (${AppConfig.VERSION_CODE})")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
        appendLine("Android: ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}")
        val setup = PrivilegedManager.setup.value
        appendLine("Bridge: ${if (setup.ready) "connected" else "not connected"}")
        appendLine("Scope: DotSuite app events only; no system-wide logcat or other apps' content.")
        appendLine("Native hook execution is not implied by a connected bridge or a saved setting.")
        appendLine()
        if (includeLogs) {
            appendLine("Last 60 seconds at preview capture (max 256 events):"); append(events.take(180_000))
            if (events.length > 180_000) appendLine("\n[Preview limit reached]")
        }
        else appendLine("Logs excluded by the user.")
    }
    fun attachment(context: Context, text: String): Uri {
        require(text.length <= 192_000) { "Report exceeds preview limit" }
        val directory = File(context.cacheDir, "reports").apply { check(mkdirs() || isDirectory) }
        // Generated cache files only. No backups or other application files are exposed.
        directory.listFiles()?.filter { it.name.matches(Regex("dotsuite-[a-f0-9-]+\\.txt")) }
            ?.sortedByDescending { it.lastModified() }?.drop(7)?.forEach { it.delete() }
        val file = File(directory, "dotsuite-${UUID.randomUUID()}.txt")
        file.writeText(text, Charsets.UTF_8)
        return FileProvider.getUriForFile(context, "${context.packageName}.reports", file)
    }
    fun sendIntent(context: Context, uri: Uri): Intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_EMAIL, arrayOf(EMAIL))
        putExtra(Intent.EXTRA_SUBJECT, "DotSuite ${AppConfig.VERSION_NAME} bug report")
        putExtra(Intent.EXTRA_TEXT, "Describe what happened and how to reproduce it:\n\n\nThe reviewed diagnostic report is attached.")
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri("DotSuite diagnostic report", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    fun openEmail(context: Context, uri: Uri): Boolean {
        val clients = context.packageManager.queryIntentActivities(
            Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$EMAIL")), 0).map { it.activityInfo.packageName }.distinct()
        val targets = clients.map { sendIntent(context, uri).setPackage(it) }
            .filter { it.resolveActivity(context.packageManager) != null }
        if (targets.isEmpty()) return false
        return runCatching {
            val chooser = Intent.createChooser(targets.first(), "Email report").apply {
                if (targets.size > 1) putExtra(Intent.EXTRA_INITIAL_INTENTS, targets.drop(1).toTypedArray())
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser); true
        }.getOrDefault(false)
    }
    fun share(context: Context, uri: Uri): Boolean = runCatching {
        context.startActivity(Intent.createChooser(sendIntent(context, uri), "Share reviewed report")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    }.getOrDefault(false)
}
