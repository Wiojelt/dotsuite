package io.github.wiojelt.dotsuite.standby

import android.app.Activity
import android.os.Bundle
import android.service.dreams.DreamService
import android.view.WindowManager
import android.view.WindowInsets

/** Selected by the user in Android's screensaver settings; never replaces the keyguard. */
class StandbyDreamService : DreamService() {
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = true; isFullscreen = true; isScreenBright = false
        setContentView(StandbyView(this))
    }
}

class StandbyPreviewActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.getBooleanExtra("landscape", false))
            requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        window.attributes = window.attributes.apply { screenBrightness = .15f }
        setContentView(StandbyView(this))
        window.decorView.post {
            window.insetsController?.apply {
                systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsets.Type.systemBars())
            }
        }
    }
    override fun onResume() {
        super.onResume()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
    override fun onPause() {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onPause()
    }
}
