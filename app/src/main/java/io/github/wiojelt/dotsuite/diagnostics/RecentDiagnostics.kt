package io.github.wiojelt.dotsuite.diagnostics

import android.content.Context
import android.os.SystemClock
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Event-driven only: bounded RAM, coalesced disk writes, no logcat reader or background service. */
object RecentDiagnostics {
    private val buffer = DiagnosticBuffer()
    private var file: AtomicFile? = null
    private val pending = AtomicBoolean(false)
    private val writer = Executors.newSingleThreadScheduledExecutor { task -> Thread(task, "dotsuite-diagnostics").apply { isDaemon = true } }
    @Synchronized fun init(context: Context) {
        if (file != null) return
        file = AtomicFile(File(context.noBackupFilesDir, "recent-diagnostics.json"))
        runCatching {
            if (file!!.baseFile.length() > 256_000L) return@runCatching
            val rows = JSONArray(String(file!!.readFully(), Charsets.UTF_8))
            val wall = System.currentTimeMillis()
            for (i in 0 until rows.length().coerceAtMost(256)) {
                val row = rows.getJSONObject(i)
                if (wall - row.getLong("wall") in 0..DiagnosticBuffer.WINDOW_MS) buffer.add(DiagnosticBuffer.Entry(
                    row.getLong("elapsed"), row.getLong("wall"), row.getString("level"), row.getString("operation"), row.getString("detail")))
            }
        }
    }
    fun record(operation: String, detail: String, level: String = "INFO") {
        buffer.add(DiagnosticBuffer.Entry(SystemClock.elapsedRealtime(), System.currentTimeMillis(), level, operation, detail))
        if (pending.compareAndSet(false, true)) writer.schedule({
            pending.set(false)
            flush()
        }, 500, TimeUnit.MILLISECONDS)
    }
    fun failure(operation: String, error: Throwable) = record(operation, DiagnosticBuffer.throwable(error), "ERROR")
    fun text(): String {
        val format = SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT)
        return buffer.recent(SystemClock.elapsedRealtime()).joinToString("\n") {
            "${format.format(Date(it.wall))} ${it.level} ${it.operation}: ${it.detail}"
        }.ifEmpty { "No app events in the last 60 seconds." }
    }
    @Synchronized fun flush() {
        val target = file ?: return
        runCatching {
            val rows = JSONArray()
            buffer.recent(SystemClock.elapsedRealtime()).forEach { row -> rows.put(JSONObject()
                .put("elapsed", row.elapsed).put("wall", row.wall).put("level", row.level)
                .put("operation", row.operation).put("detail", row.detail)) }
            val stream = target.startWrite()
            try { stream.write(rows.toString().toByteArray()); target.finishWrite(stream) }
            catch (error: Throwable) { target.failWrite(stream) }
        }
    }
    fun clear() { buffer.clear(); flush() }
}
