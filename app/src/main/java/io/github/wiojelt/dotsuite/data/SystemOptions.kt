package io.github.wiojelt.dotsuite.data

import android.content.Context
import android.provider.Settings
import io.github.wiojelt.dotsuite.privileged.PrivilegedManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import org.json.JSONObject
import android.util.AtomicFile

/** Capture exact device values before the first write; never restore another device's backup. */
object SystemOptions {
    private val mutex = Mutex()
    suspend fun savedKeys(context: Context): List<String> = mutex.withLock {
        withContext(Dispatchers.IO) {
            load(context).keys().asSequence().filter { PersonalizationPolicy.namespace(it) != null }.toList().sorted()
        }
    }
    fun read(context: Context, key: String): String? = when (PersonalizationPolicy.namespace(key)) {
        "global" -> Settings.Global.getString(context.contentResolver, key)
        "secure" -> Settings.Secure.getString(context.contentResolver, key)
        "system" -> Settings.System.getString(context.contentResolver, key)
        else -> null
    }

    private fun file(context: Context) = AtomicFile(File(context.noBackupFilesDir, "system-originals.json"))
    private fun load(context: Context): JSONObject = runCatching {
        JSONObject(String(file(context).readFully(), Charsets.UTF_8))
    }.getOrElse { if (file(context).baseFile.exists()) throw it else JSONObject() }
    private fun persist(context: Context, json: JSONObject) {
        val atomic = file(context)
        val stream = atomic.startWrite()
        try { stream.write(json.toString().toByteArray()); atomic.finishWrite(stream) }
        catch (error: Throwable) { atomic.failWrite(stream); throw error }
    }

    suspend fun write(context: Context, key: String, value: String?): String = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (!PersonalizationPolicy.acceptsString(key, value)) return@withContext "Invalid value"
            runCatching {
                val saved = load(context)
                val before = PrivilegedManager.readSystemOption(key)
                if (!before.available) return@withContext "Original value unavailable. Nothing changed."
                val current = before.value
                val row = saved.optJSONObject(key) ?: JSONObject()
                    .put("original", current ?: JSONObject.NULL).put("last", current ?: JSONObject.NULL)
                if (row.has("pending")) {
                    val pending = if (row.isNull("pending")) null else row.getString("pending")
                    if (PersonalizationPolicy.sameSetting(key, current, pending)) row.put("last", current ?: JSONObject.NULL)
                }
                // Journal an in-flight value first: a process death after the system write is recoverable.
                row.put("pending", value ?: JSONObject.NULL)
                saved.put(key, row)
                persist(context, saved)
                val result = PrivilegedManager.setSystemOption(key, value)
                val after = PrivilegedManager.readSystemOption(key)
                val verified = result == "OK" && after.available && PersonalizationPolicy.sameSetting(key, after.value, value)
                if (verified) {
                    row.put("last", value ?: JSONObject.NULL)
                    row.remove("pending")
                    persist(context, saved)
                }
                FeatureJournal.record(context, key, if (verified) "applied; original saved" else "write failed")
                if (verified) "Saved" else "Could not apply. Check Shizuku / Sui."
            }.getOrElse { "Could not save the backup; no further changes were made." }
        }
    }

    suspend fun restore(context: Context, keys: List<String>): String = mutex.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                val saved = load(context)
                var restored = 0
                var skipped = 0
                for (key in keys) {
                    val row = saved.optJSONObject(key) ?: continue
                    val original = if (row.isNull("original")) null else row.getString("original")
                    val last = if (row.isNull("last")) null else row.getString("last")
                    val state = PrivilegedManager.readSystemOption(key)
                    if (!state.available) { skipped++; continue }
                    val current = state.value
                    if (PersonalizationPolicy.sameSetting(key, current, original)) {
                        saved.remove(key); restored++; continue
                    }
                    val pending = if (row.isNull("pending")) null else row.getString("pending")
                    if (!PersonalizationPolicy.sameSetting(key, current, last)
                        && !(row.has("pending") && PersonalizationPolicy.sameSetting(key, current, pending))) {
                        skipped++; FeatureJournal.record(context, key, "restore skipped: changed elsewhere"); continue
                    }
                    if (PrivilegedManager.setSystemOption(key, original) == "OK") {
                        saved.remove(key); restored++
                        FeatureJournal.record(context, key, "original restored")
                    } else skipped++
                }
                persist(context, saved)
                "$restored restored · $skipped skipped (changed elsewhere or unavailable)"
            }.getOrElse { "Backup could not be read; nothing else was changed." }
        }
    }
}
