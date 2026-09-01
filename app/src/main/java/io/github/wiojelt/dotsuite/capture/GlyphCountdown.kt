package io.github.wiojelt.dotsuite.capture

import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.nothing.ketchum.Common
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphManager

/** One capture-owned session. No global Glyph preferences or debug settings are changed. */
class GlyphCountdown(private val context: Context) {
    companion object {
        fun supported() = Build.VERSION.SDK_INT >= 36 && runCatching { Common.is24111() }.getOrDefault(false)
    }
    private val handler = Handler(Looper.getMainLooper())
    private var manager: GlyphManager? = null
    private var closed = false
    private var session = false
    fun start(seconds: Int, progress: (Int) -> Unit, finish: () -> Unit, failure: () -> Unit) {
        if (!supported() || seconds !in setOf(3, 5, 10)) { failure(); return }
        fun fail() { if (!closed) { close(); failure() } }
        handler.postDelayed({ fail() }, 5000)
        runCatching {
            manager = GlyphManager.getInstance(context)
            manager!!.init(object : GlyphManager.Callback {
                override fun onServiceConnected(componentName: ComponentName) {
                    if (closed) return
                    handler.removeCallbacksAndMessages(null)
                    runCatching {
                        check(manager!!.register(Glyph.DEVICE_24111))
                        manager!!.openSession(); session = true
                        var left = seconds
                        val tick = object : Runnable {
                            override fun run() {
                                if (closed) return
                                if (left <= 0) { close(); finish(); return }
                                runCatching {
                                    val frame = manager!!.glyphFrameBuilder
                                    val count = ((20 * left + seconds - 1) / seconds).coerceIn(1, 20)
                                    for (channel in 0 until count) frame.buildChannel(channel)
                                    manager!!.toggle(frame.build())
                                    progress(left--)
                                    handler.postDelayed(this, 1000)
                                }.onFailure { fail() }
                            }
                        }
                        handler.post(tick)
                    }.onFailure { fail() }
                }
                override fun onServiceDisconnected(componentName: ComponentName) { fail() }
            })
        }.onFailure { fail() }
    }
    fun close() {
        if (closed) return
        closed = true
        handler.removeCallbacksAndMessages(null)
        // Only release a session we opened; never switch off another app's Glyph on init failure.
        if (session) {
            runCatching { manager?.turnOff() }
            runCatching { manager?.closeSession() }
        }
        runCatching { manager?.unInit() }; manager = null; session = false
    }
}
