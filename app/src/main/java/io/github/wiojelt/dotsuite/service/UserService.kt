package io.github.wiojelt.dotsuite.service

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import org.json.JSONObject
import io.github.wiojelt.dotsuite.IUserService
import io.github.wiojelt.dotsuite.data.PersonalizationPolicy
import io.github.wiojelt.dotsuite.data.SettingQueryResult
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import kotlin.system.exitProcess

internal fun hasAndroidAutoFocus(audioDump: String): Boolean {
    val currentFocus = audioDump.substringAfter("Audio Focus stack entries", "")
        .substringBefore("No external focus policy", "")
    return currentFocus.contains("pack: com.google.android.projection.gearhead")
}

/**
 * Runs inside the Shizuku-spawned process (shell UID when Shizuku is started via ADB).
 * That process is allowed to run `appops set <pkg> TAKE_AUDIO_FOCUS ...` and to call the
 * hidden per-player volume APIs, which the app's own UID is not. Everything privileged
 * happens here.
 *
 * Shizuku instantiates this via either the [Context] constructor or the no-arg one, so
 * both must exist; the Context is needed for the audio/package services.
 */
class UserService() : IUserService.Stub() {

    private var context: Context? = null
    private val commandWatchdog = java.util.concurrent.ScheduledThreadPoolExecutor(1) { task ->
        Thread(task, "dotsuite-command-timeout").apply { isDaemon = true }
    }.apply { removeOnCancelPolicy = true }

    @Suppress("unused")
    constructor(context: Context) : this() {
        this.context = context
    }

    override fun destroy() {
        exitProcess(0)
    }

    override fun exit() {
        destroy()
    }

    override fun reloadSystemUi(): String {
        if (android.os.Process.myUid() != 0 || Build.VERSION.SDK_INT != 36 || Build.DEVICE != "Asteroids")
            return "ERROR: this action requires root/Sui on the supported phone"
        val pid = runCommand(arrayOf("pidof", "com.android.systemui")).trim().toIntOrNull()
            ?: return "ERROR: one live SystemUI process was not found"
        if (pid <= 1) return "ERROR: invalid process"
        val name = runCatching { File("/proc/$pid/cmdline").readText().substringBefore('\u0000') }.getOrNull()
        if (name != "com.android.systemui") return "ERROR: process changed; nothing stopped"
        return runCommand(arrayOf("kill", "-TERM", pid.toString())).let {
            if (it.startsWith("ERROR")) it else "Reload requested. Wait for the status bar to return."
        }
    }

    override fun setAudioFocusMode(packageName: String, mode: String): String {
        if (!PACKAGE_NAME.matches(packageName)) return "ERROR: invalid package name"
        if (mode !in AUDIO_FOCUS_MODES) return "ERROR: invalid audio focus mode"
        val ctx = context ?: return "ERROR: bridge context unavailable"
        if (runCatching { ctx.packageManager.getApplicationInfo(packageName, 0) }.isFailure) {
            return "ERROR: package not installed"
        }
        return runCommand(arrayOf("appops", "set", packageName, "TAKE_AUDIO_FOCUS", mode))
    }

    override fun getAudioFocusMode(packageName: String): String {
        return runCommand(arrayOf("appops", "get", packageName, "TAKE_AUDIO_FOCUS"))
    }

    override fun isAndroidAutoActive(): Boolean {
        // `dumpsys audio` is much larger than the normal command-output safety cap. Read only the
        // current focus section and stop before the historical event log, which may contain old
        // Android Auto requests long after the car disconnected.
        return runCatching {
            val process = ProcessBuilder("dumpsys", "audio").redirectErrorStream(true).start()
            val expired = java.util.concurrent.atomic.AtomicBoolean(false)
            val watchdog = commandWatchdog.schedule(
                { expired.set(true); process.destroy() },
                5,
                java.util.concurrent.TimeUnit.SECONDS,
            )
            try {
                var inCurrentFocus = false
                var found = false
                process.inputStream.bufferedReader().useLines { lines ->
                    for (line in lines) {
                        if (line.contains("Audio Focus stack entries")) inCurrentFocus = true
                        if (inCurrentFocus && line.contains(
                                "pack: com.google.android.projection.gearhead"
                            )) found = true
                        if (inCurrentFocus && line.contains("No external focus policy")) break
                    }
                }
                !expired.get() && found
            } finally {
                watchdog.cancel(false)
                process.destroy()
            }
        }.getOrDefault(false)
    }

    /**
     * Ringer mode via AudioService's *internal* setter, which is what `cmd audio set-ringer-mode`
     * reaches — the path the system volume panel itself uses, and the only one that can select
     * SILENT without the framework switching Do Not Disturb on (see the AIDL doc). The sub-command
     * is recent, so on older platforms this comes back as the shell's own error text and the caller
     * falls back.
     */
    override fun setRingerMode(mode: String): String {
        return runCommand(arrayOf("cmd", "audio", "set-ringer-mode", mode))
    }

    override fun setFeatureEnabled(key: String, enabled: Boolean): String {
        if (key !in FEATURE_FLAGS) return "ERROR: unsupported feature flag"
        return runCommand(
            arrayOf("settings", "put", "secure", key, if (enabled) "1" else "0")
        )
    }

    override fun setSoundSetting(key: String, value: Int): String {
        if (value !in SOUND_SETTINGS[key].orEmpty() && !PersonalizationPolicy.acceptsInt(key, value)) {
            return "ERROR: unsupported setting or invalid value"
        }
        return runCommand(arrayOf("settings", "put", "secure", key, value.toString()))
    }

    override fun setSystemOption(key: String, value: String?): String {
        if (!PersonalizationPolicy.acceptsString(key, value)) return "ERROR: invalid system option"
        if (io.github.wiojelt.dotsuite.data.AodPolicy.owns(key)
            && context?.let { AodPlatform(it).supports(key, value) } != true)
            return "ERROR: this native AOD control is unavailable on this device"
        if (key in setOf(PersonalizationPolicy.EXTRA_DIM, PersonalizationPolicy.EXTRA_DIM_LEVEL) && !extraDimSupported())
            return "ERROR: Extra dim is not supported by this device"
        val namespace = PersonalizationPolicy.namespace(key) ?: return "ERROR: unsupported option"
        val command = if (value == null) arrayOf("settings", "delete", namespace, key)
            else arrayOf("settings", "put", namespace, key, value)
        val output = runCommand(command)
        if (output.startsWith("ERROR")) return output
        val result = readSystemOption(key)
        if (result.startsWith("ERROR")) return result
        val actual = JSONObject(result).let { if (it.getBoolean("present")) it.getString("value") else null }
        return if (PersonalizationPolicy.sameSetting(key, actual, value)) "OK" else "ERROR: verification failed"
    }

    override fun readSystemOption(key: String): String {
        val namespace = PersonalizationPolicy.namespace(key) ?: return "ERROR: unsupported option"
        val ctx = context ?: return "ERROR: bridge context unavailable"
        if (io.github.wiojelt.dotsuite.data.AodPolicy.owns(key) && !AodPlatform(ctx).supports(key))
            return "ERROR: this native AOD control is unavailable on this device"
        if (key in setOf(PersonalizationPolicy.EXTRA_DIM, PersonalizationPolicy.EXTRA_DIM_LEVEL) && !extraDimSupported())
            return "ERROR: Extra dim is not supported by this device"
        return runCatching {
            val value = when (namespace) {
                "system" -> Settings.System.getString(ctx.contentResolver, key)
                "global" -> Settings.Global.getString(ctx.contentResolver, key)
                else -> Settings.Secure.getString(ctx.contentResolver, key)
            }
            JSONObject().put("present", value != null).put("value", value ?: JSONObject.NULL).toString()
        }.getOrElse {
            // A package Context can fail SettingsProvider's attribution check in a shell process.
            // Query exactly this allow-listed key; never enumerate the full settings table.
            val output = runCommand(arrayOf("content", "query", "--uri", "content://settings/$namespace/$key", "--projection", "value"), trim = false)
            val parsed = SettingQueryResult.parse(output)
            if (!parsed.valid) return@getOrElse "ERROR: could not read original value"
            // `content query` prints SQL NULL as uppercase NULL, which is also a valid label.
            // A second single-key lookup resolves that ambiguity without enumerating settings.
            val value = if (parsed.value == "NULL") {
                when (runCommand(arrayOf("settings", "get", namespace, key), trim = false)) {
                    "null" -> null
                    "NULL" -> "NULL"
                    else -> return@getOrElse "ERROR: ambiguous original value"
                }
            } else parsed.value
            JSONObject().put("present", value != null).put("value", value ?: JSONObject.NULL).toString()
        }
    }

    private fun extraDimSupported(): Boolean {
        if (Build.VERSION.SDK_INT < 31) return false
        val ctx = context ?: return false
        return runCatching {
            Class.forName("android.hardware.display.ColorDisplayManager")
                .getMethod("isReduceBrightColorsAvailable", Context::class.java).invoke(null, ctx) == true
        }.getOrDefault(false)
    }

    override fun getAodCapabilities(): String = runCatching {
        context?.let { AodPlatform(it).snapshot().toString() } ?: "ERROR: bridge context unavailable"
    }.getOrDefault("ERROR: AOD discovery unavailable")

    override fun launchMapsMinMode(): String {
        val result = runCommand(arrayOf("am", "start", "--user", "current", "-n",
            "com.google.android.apps.maps/com.google.android.apps.gmm.features.minmode.MinModeActivity"))
        return if (result.contains("Error", ignoreCase = true) || result.contains("Exception"))
            "ERROR: Maps MinMode is unavailable on this Maps / OS version" else "OK"
    }

    /**
     * Enumerates active playback via the public [AudioManager.getActivePlaybackConfigurations],
     * then reflects the hidden per-config accessors (piid, client uid) — reachable here because
     * the shell UID is exempt from the non-SDK interface blocklist. Requires Android 8+ (the
     * config API) and realistically Android 13+ for the player-proxy volume to take effect.
     */
    override fun getActivePlayers(): List<String> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return emptyList()
        val ctx = context ?: return emptyList()
        return try {
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.activePlaybackConfigurations.mapNotNull { cfg ->
                // Some builds keep paused/idle players in the active list; require the STARTED state
                // so we only ever surface players that are genuinely running. Absent method → keep.
                val state = cfg.invokeInt("getPlayerState")
                if (state != null && state != PLAYER_STATE_STARTED) return@mapNotNull null
                // Only real media/game playback — skip notification, alarm, ringtone and assistance
                // sonification players, which aren't something the user is "listening to".
                val usage = cfg.playerUsage()
                if (usage != null && usage !in AUDIBLE_USAGES) return@mapNotNull null
                val piid = cfg.invokeInt("getPlayerInterfaceId") ?: return@mapNotNull null
                val uid = cfg.invokeInt("getClientUid") ?: return@mapNotNull null
                val pkg = ctx.packageManager.getPackagesForUid(uid)?.firstOrNull()
                    ?: return@mapNotNull null
                "$piid|$uid|$pkg"
            }
        } catch (e: Throwable) {
            emptyList()
        }
    }

    override fun setPlayerVolume(piid: Int, volume: Float): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        val ctx = context ?: return false
        return try {
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val cfg = am.activePlaybackConfigurations
                .firstOrNull { it.invokeInt("getPlayerInterfaceId") == piid } ?: return false
            val proxy = cfg.javaClass.getMethod("getPlayerProxy").invoke(cfg) ?: return false
            proxy.javaClass
                .getMethod("setVolume", Float::class.javaPrimitiveType)
                .invoke(proxy, volume.coerceIn(0f, 1f))
            true
        } catch (e: Throwable) {
            false
        }
    }

    /**
     * Whether a process for [packageName] is currently alive, by scanning `/proc` for a matching
     * process name. Android names an app's main process after its package ("com.pkg", or
     * "com.pkg:suffix" for extra processes), so a match on the package portion means the app is
     * still running. When no command line can be read at all (some builds hide other processes even
     * from the shell), returns true so a live per-app session is never discarded on a false "gone".
     */
    override fun isPackageRunning(packageName: String): Boolean {
        return try {
            val dirs = File("/proc").listFiles { f -> f.isDirectory && f.name.toIntOrNull() != null }
                ?: return true
            var readAny = false
            for (dir in dirs) {
                val cmdline = runCatching { File(dir, "cmdline").readText() }.getOrNull()
                if (cmdline.isNullOrEmpty()) continue
                readAny = true
                // cmdline is a NUL-separated argv; argv[0] (up to the first NUL) is the process
                // name. Compare on the package part before any ':' process suffix.
                val name = cmdline.takeWhile { it.code != 0 }.substringBefore(':')
                if (name == packageName) return true
            }
            // Fell through with no match: only trust that as "gone" if we could actually read the
            // process table; otherwise assume alive rather than reset a session we can't verify.
            !readAny
        } catch (e: Throwable) {
            true
        }
    }

    /** Reflectively call a no-arg int getter (a hidden method) on [this]. */
    private fun Any.invokeInt(method: String): Int? =
        try {
            javaClass.getMethod(method).invoke(this) as? Int
        } catch (e: Throwable) {
            null
        }

    /** The [android.media.AudioAttributes] usage of a playback config, or null if unavailable. */
    private fun Any.playerUsage(): Int? =
        try {
            val attrs = javaClass.getMethod("getAudioAttributes").invoke(this) ?: return null
            attrs.javaClass.getMethod("getUsage").invoke(attrs) as? Int
        } catch (e: Throwable) {
            null
        }

    private companion object {
        /** AudioPlaybackConfiguration.PLAYER_STATE_STARTED — the player is actively running. */
        const val PLAYER_STATE_STARTED = 2

        /**
         * AudioAttributes usages that represent media the user is actually listening to
         * (USAGE_UNKNOWN, USAGE_MEDIA, USAGE_GAME, USAGE_ASSISTANT). Everything else — notifications,
         * alarms, ringtones, assistance sonification — is excluded from the per-app list.
         */
        val AUDIBLE_USAGES = setOf(0, 1, 14, 16)
        val PACKAGE_NAME = Regex("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+")
        val AUDIO_FOCUS_MODES = setOf("default", "allow", "foreground", "ignore", "deny")

        val FEATURE_FLAGS = setOf(
            "dotsuite_systemui_enabled",
            "dotsuite_screen_off_keys_enabled",
        )

        val SOUND_SETTINGS = mapOf(
            "dotsuite_panel_side" to setOf(0, 1, 2),
            "dotsuite_show_captions" to setOf(0, 1),
            "dotsuite_show_settings" to setOf(0, 1),
            "dotsuite_panel_timeout_ms" to (1..10).map { it * 1_000 }.toSet(),
            "dotsuite_auto_expand" to setOf(0, 1),
            "dotsuite_volume_step_percent" to setOf(0, 5, 10, 15, 20),
            "dotsuite_always_media_volume" to setOf(0, 1),
            "dotsuite_scramble_pin" to setOf(0, 1),
            "dotsuite_hide_pin_input" to setOf(0, 1),
            "dotsuite_material_pin_keys" to setOf(0, 1),
        )

    }

    private fun runCommand(cmd: Array<String>, trim: Boolean = true): String {
        return try {
            val process = ProcessBuilder(*cmd).redirectErrorStream(true).start()
            val expired = java.util.concurrent.atomic.AtomicBoolean(false)
            val watchdog = commandWatchdog.schedule({ expired.set(true); process.destroy() }, 8, java.util.concurrent.TimeUnit.SECONDS)
            try {
                val output = StringBuilder()
                InputStreamReader(process.inputStream).use { reader ->
                    val buffer = CharArray(1024)
                    while (true) {
                        val count = reader.read(buffer)
                        if (count < 0) break
                        if (output.length + count > 32_768) return "ERROR: command output limit"
                        output.append(buffer, 0, count)
                    }
                }
                val exit = process.waitFor()
                val result = if (trim) output.toString().trim() else output.toString().trimEnd('\r', '\n')
                when {
                    expired.get() -> "ERROR: command timed out"
                    exit != 0 -> "ERROR: command exited $exit"
                    else -> result
                }
            } finally { watchdog.cancel(false); process.destroy() }
        } catch (e: Exception) {
            "ERROR: command failed (${e.javaClass.simpleName})"
        }
    }
}
