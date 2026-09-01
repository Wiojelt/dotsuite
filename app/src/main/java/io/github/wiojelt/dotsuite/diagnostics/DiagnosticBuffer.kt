package io.github.wiojelt.dotsuite.diagnostics

/** Fixed-size, monotonic-time window. Never accepts intents, UI input or raw system logcat. */
class DiagnosticBuffer(private val capacity: Int = 256) {
    init { require(capacity in 1..256) }
    data class Entry(val elapsed: Long, val wall: Long, val level: String, val operation: String, val detail: String)
    private val entries = ArrayDeque<Entry>()
    @Synchronized fun add(entry: Entry) {
        entries.addLast(entry.copy(level = entry.level.takeIf { it in setOf("INFO", "WARN", "ERROR") } ?: "INFO",
            operation = clean(entry.operation).take(80), detail = clean(entry.detail).take(640)))
        while (entries.size > capacity) entries.removeFirst()
        trim(entry.elapsed)
    }
    @Synchronized fun recent(now: Long): List<Entry> { trim(now); return entries.toList() }
    @Synchronized fun clear() { entries.clear() }
    private fun trim(now: Long) { entries.removeAll { it.elapsed > now || now - it.elapsed > WINDOW_MS } }
    companion object {
        const val WINDOW_MS = 60_000L
        fun clean(value: String): String = value
            .replace(Regex("[\\r\\n\\t]"), " ")
            .replace(Regex("(?i)(https?://|content://|file://)\\S+"), "[uri]")
            .replace(Regex("[\\w.+-]+@[\\w.-]+\\.[A-Za-z]{2,}"), "[email]")
            .replace(Regex("(?i)(pin|password|token|secret|authorization)\\s*[:=]\\s*\\S+"), "$1=[redacted]")
            .replace(Regex("(?<![A-Za-z])\\d{5,}(?![A-Za-z])"), "[number]")
            .replace(Regex("/(?:storage|sdcard|data)/\\S+"), "[path]")
        fun throwable(error: Throwable): String {
            val seen = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Throwable, Boolean>())
            val parts = mutableListOf<String>()
            var current: Throwable? = error
            while (current != null && seen.add(current) && parts.size < 3) {
                // Exception messages can contain private values. Only class + app stack locations.
                val frames = current.stackTrace.filter { it.className.startsWith("io.github.wiojelt.dotsuite.") }
                    .take(6).joinToString(" <- ") { "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" }
                parts += current.javaClass.simpleName + if (frames.isEmpty()) "" else " at $frames"
                current = current.cause
            }
            return clean(parts.joinToString(" caused by ")).take(640)
        }
    }
}
