package io.github.wiojelt.dotsuite.standby

import android.app.KeyguardManager
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.*
import android.graphics.*
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.format.DateFormat
import android.view.*
import android.widget.*
import io.github.wiojelt.dotsuite.data.FeatureJournal
import java.util.Calendar
import java.util.Date
import kotlin.math.*

/** Minute-driven desk display. No sensor loop, overlay, replacement lock screen or low-power AOD claim. */
class StandbyView(context: Context) : LinearLayout(context) {
    private val host = AppWidgetHost(context, StandbyPreferences.HOST_ID)
    private val handler = Handler(Looper.getMainLooper())
    private val clock = StandbyClockView(context)
    private val detail = TextView(context)
    private val body = LinearLayout(context)
    private val clockPane = LinearLayout(context)
    private val widgets = LinearLayout(context)
    private val controls = LinearLayout(context)
    private var listening = false
    private var tracking = false
    internal val updatesActive: Boolean get() = tracking
    private var lastSignature = ""
    private var screenOff = false
    private var locked = false
    private var page = if ((0..1).any { StandbyPreferences.widget(context, it) >= 0 }) 1 else 0
    private var slot = 0
    private val tick = object : Runnable {
        override fun run() {
            if (!tracking) return
            update()
            handler.postDelayed(this, 60_000 - System.currentTimeMillis() % 60_000)
        }
    }
    private val events = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (i.action == Intent.ACTION_SCREEN_OFF) screenOff = true
            if (i.action == Intent.ACTION_SCREEN_ON || i.action == Intent.ACTION_USER_PRESENT) screenOff = false
            update()
        }
    }
    init {
        orientation = VERTICAL; gravity = Gravity.CENTER
        setBackgroundColor(Color.BLACK)
        setPadding(dp(24), dp(16), dp(24), dp(12))
        clockPane.orientation = VERTICAL; clockPane.gravity = Gravity.CENTER
        clockPane.addView(clock, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        detail.gravity = Gravity.CENTER; detail.textSize = 14f
        detail.setPadding(0, dp(8), 0, dp(8))
        clockPane.addView(detail, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        body.gravity = Gravity.CENTER
        body.addView(clockPane)
        widgets.gravity = Gravity.CENTER
        body.addView(widgets)
        addView(body, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        controls.gravity = Gravity.CENTER
        addView(controls, LayoutParams(LayoutParams.MATCH_PARENT, dp(48)))
        for ((index, label) in listOf("Clock", "Pair", "Widgets").withIndex()) {
            controls.addView(Button(context, null, android.R.attr.borderlessButtonStyle).apply {
                text = label; textSize = 12f; isAllCaps = false
                setOnClickListener { page = index; lastSignature = ""; update() }
            }, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        }
        controls.addView(Button(context, null, android.R.attr.borderlessButtonStyle).apply {
            text = "1 / 2"; textSize = 12f
            contentDescription = "Switch paired widget"
            setOnClickListener { slot = 1 - slot; lastSignature = ""; update() }
        }, LayoutParams(dp(64), LayoutParams.MATCH_PARENT))
        // Swipe clock area only: never steal a widget's own scroll/tap gestures.
        val detector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true
            override fun onSingleTapUp(e: MotionEvent): Boolean { clock.performClick(); return true }
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (e1 == null || abs(vx) < 250 || abs(e2.x - e1.x) < dp(56) || abs(vx) <= abs(vy)) return false
                page = (page + if (vx < 0) 1 else 2) % 3
                lastSignature = ""; update(); return true
            }
        })
        clock.setOnTouchListener { _, e -> detector.onTouchEvent(e) }
        clock.setOnClickListener { /* Explicit buttons provide the accessible equivalent. */ }
        isFocusable = true; contentDescription = "StandBy clock"
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        lastSignature = ""; if (tracking) update()
    }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (windowVisibility == VISIBLE) startUpdates()
    }
    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (isAttachedToWindow) { if (visibility == VISIBLE) startUpdates() else stopUpdates() }
    }
    private fun startUpdates() {
        if (tracking) return
        lastSignature = ""
        screenOff = !context.getSystemService(android.os.PowerManager::class.java).isInteractive
        context.registerReceiver(events, IntentFilter().apply {
            addAction(Intent.ACTION_TIME_CHANGED); addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_USER_PRESENT); addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON)
        })
        tracking = true; handler.post(tick)
    }
    private fun update() {
        locked = screenOff || context.getSystemService(KeyguardManager::class.java).isKeyguardLocked
        val battery = context.getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        detail.text = DateFormat.format("EEE, d MMM", Date()).toString() +
            (if (battery in 0..100) "  ·  $battery%" else "") + if (locked) "  ·  Unlock to use widgets" else ""
        val night = StandbyPreferences.night(context)
        val ink = if (night) Color.rgb(188, 60, 52) else Color.rgb(232, 231, 224)
        detail.setTextColor(ink)
        clock.refresh()
        for (i in 0 until controls.childCount) (controls.getChildAt(i) as Button).apply {
            setTextColor(if (i == page) ink else if (night) Color.rgb(115, 45, 40) else Color.GRAY)
            isEnabled = !locked || i == 0
        }
        controls.getChildAt(3).visibility = if (page == 1 && !locked) VISIBLE else GONE
        val minute = System.currentTimeMillis() / 60_000
        clockPane.translationX = dp(((minute % 5) - 2).toInt()).toFloat()
        clockPane.translationY = dp((((minute / 5) % 5) - 2).toInt()).toFloat()
        val ids = (0..1).map { StandbyPreferences.widget(context, it) }
        val signature = "$locked:$page:$slot:$width:$height:$ids"
        if (signature == lastSignature) return
        lastSignature = signature
        runCatching { host.stopListening() }; listening = false
        widgets.removeAllViews()
        val actual = if (locked) 0 else page
        val landscape = width > height
        body.orientation = if (landscape) HORIZONTAL else VERTICAL
        clockPane.visibility = if (actual == 2) GONE else VISIBLE
        widgets.visibility = if (actual == 0) GONE else VISIBLE
        fun paneParams() = if (landscape) LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
            else LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
        clockPane.layoutParams = paneParams()
        widgets.layoutParams = paneParams()
        widgets.orientation = if (landscape) HORIZONTAL else VERTICAL
        if (actual == 0) return
        val slots = if (actual == 1) listOf(slot) else listOf(0, 1)
        for (s in slots) {
            val id = ids[s]
            val info = if (id >= 0) AppWidgetManager.getInstance(context).getAppWidgetInfo(id) else null
            val params = if (landscape) LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
                else LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
            params.setMargins(dp(8), dp(8), dp(8), dp(8))
            val view = if (info != null) runCatching {
                host.createView(context, id, info).apply {
                    setAppWidget(id, info)
                    addOnLayoutChangeListener { _, l, t, r, b, ol, ot, or, ob ->
                        if (r > l && b > t && (r - l != or - ol || b - t != ob - ot)) {
                            val density = resources.displayMetrics.density
                            runCatching { updateAppWidgetSize(Bundle(), listOf(android.util.SizeF((r-l)/density, (b-t)/density))) }
                        }
                    }
                }
            }.onFailure { FeatureJournal.record(context, "standby.widget", "render failed") }.getOrNull() else null
            val container = FrameLayout(context)
            val content = view ?: TextView(context).apply {
                text = "Widget ${s + 1}\nAdd one in StandBy settings"
                textSize = 16f; gravity = Gravity.CENTER; setTextColor(ink)
            }
            container.addView(content, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, Gravity.CENTER))
            if (info != null && view != null) {
                val ratio = if (info.minHeight > 0) (info.minWidth.toFloat() / info.minHeight).coerceIn(.65f, 2.4f) else 1f
                container.addOnLayoutChangeListener { _, l, t, r, b, _, _, _, _ ->
                    val contentWidth = minOf(r-l, ((b-t) * ratio).toInt()).coerceAtLeast(1)
                    val contentHeight = (contentWidth / ratio).toInt().coerceAtLeast(1)
                    if (content.layoutParams.width != contentWidth || content.layoutParams.height != contentHeight)
                        content.layoutParams = FrameLayout.LayoutParams(contentWidth, contentHeight, Gravity.CENTER)
                }
            }
            widgets.addView(container, params)
        }
        listening = runCatching { host.startListening(); true }.getOrElse {
            FeatureJournal.record(context, "standby.widget", "updates unavailable"); false
        }
    }
    private fun stopUpdates() {
        handler.removeCallbacksAndMessages(null)
        if (tracking) runCatching { context.unregisterReceiver(events) }
        runCatching { host.stopListening() }
        listening = false; tracking = false; lastSignature = ""
        widgets.removeAllViews()
    }
    override fun onDetachedFromWindow() { stopUpdates(); super.onDetachedFromWindow() }
}

/** Original clock artwork, using only Android fonts and bounded Canvas drawing. */
class StandbyClockView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val digits = arrayOf("111101101101111", "010110010010111", "111001111100111",
        "111001111001111", "101101111001001", "111100111001111", "111100111101111",
        "111001001001001", "111101111101111", "111101111001111")
    private var text = ""
    private var hour = 0
    private var minute = 0
    var clockStyle = StandbyPreferences.clock(context)
    var night = StandbyPreferences.night(context)
    init { refresh() }
    fun refresh() {
        text = DateFormat.format(if (DateFormat.is24HourFormat(context)) "HH:mm" else "hh:mm", Date()).toString()
        val now = Calendar.getInstance()
        hour = now.get(Calendar.HOUR); minute = now.get(Calendar.MINUTE)
        clockStyle = StandbyPreferences.clock(context); night = StandbyPreferences.night(context)
        contentDescription = text; invalidate()
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        paint.color = if (night) Color.rgb(192, 58, 50) else Color.rgb(235, 232, 226)
        paint.style = Paint.Style.FILL
        when (clockStyle) {
            1 -> {
                paint.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
                paint.textSize = minOf(height * .7f, width / 2.8f)
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText(text, width / 2f, height / 2f - (paint.ascent() + paint.descent()) / 2, paint)
            }
            2 -> {
                val radius = minOf(width, height) * .42f
                val x = width / 2f; val y = height / 2f
                for (i in 0..59) {
                    val a = Math.toRadians(i * 6.0)
                    canvas.drawCircle(x + sin(a).toFloat() * radius, y - cos(a).toFloat() * radius,
                        if (i % 5 == 0) radius * .025f else radius * .008f, paint)
                }
                paint.strokeCap = Paint.Cap.ROUND
                fun hand(degrees: Float, length: Float, thickness: Float) {
                    paint.strokeWidth = thickness
                    val a = Math.toRadians(degrees.toDouble())
                    canvas.drawLine(x, y, x + sin(a).toFloat() * length, y - cos(a).toFloat() * length, paint)
                }
                hand(hour * 30f + minute * .5f, radius * .5f, radius * .045f)
                hand(minute * 6f, radius * .76f, radius * .024f)
                canvas.drawCircle(x, y, radius * .045f, paint)
            }
            else -> {
                // 17 columns including the colon: center the actual glyph span, not an assumed width.
                val cell = minOf(width / 18f, height / 6f)
                val left = (width - cell * 17f) / 2f
                val top = (height - cell * 5f) / 2f
                var column = 0
                for (char in text) {
                    if (char == ':') {
                        for (row in intArrayOf(1, 3)) canvas.drawCircle(left + (column + .5f) * cell,
                            top + (row + .5f) * cell, cell * .23f, paint)
                        column += 2
                    } else {
                        val digit = char.digitToIntOrNull() ?: continue
                        for (row in 0..4) for (col in 0..2) if (digits[digit][row * 3 + col] == '1')
                            canvas.drawCircle(left + (column + col + .5f) * cell, top + (row + .5f) * cell, cell * .29f, paint)
                        column += 4
                    }
                }
            }
        }
    }
}
