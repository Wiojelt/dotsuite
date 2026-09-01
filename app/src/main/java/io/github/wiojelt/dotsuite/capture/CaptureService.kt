package io.github.wiojelt.dotsuite.capture

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import io.github.wiojelt.dotsuite.R
import io.github.wiojelt.dotsuite.data.FeatureJournal
import io.github.wiojelt.dotsuite.data.PersonalizationPolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Only an explicit visible activity can start capture. Never restarted, booted or remotely triggered. */
class CaptureService : LifecycleService() {
    data class State(val phase: String = "idle", val message: String = "") {
        val busy get() = phase in setOf("starting", "recording", "stopping")
    }
    companion object {
        const val STOP = "io.github.wiojelt.dotsuite.STOP_CAPTURE"
        private const val CHANNEL = "camera_shortcuts"
        private const val NOTICE = 4101
        private val mutableState = MutableStateFlow(State())
        val state = mutableState.asStateFlow()
        fun prepare() { mutableState.value = State("starting") }
        fun cancelPreparation() {
            if (mutableState.value.phase == "starting") mutableState.value = State("error", "Camera start was cancelled.")
        }
        fun notificationsAvailable(context: Context): Boolean {
            val manager = context.getSystemService(NotificationManager::class.java)
            return manager.areNotificationsEnabled() && (Build.VERSION.SDK_INT < 26 ||
                manager.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE)
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var provider: ProcessCameraProvider? = null
    private var recording: Recording? = null
    private var glyph: GlyphCountdown? = null
    private var launched = false
    private var disposed = false
    private var completed = false
    private var stopping = false
    private val startTimeout = Runnable { fail("Camera did not start in time. It has been released.") }
    private val stopTimeout = Runnable { fail("Camera stopped without finalising the file. Check the gallery.") }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, "Camera shortcuts", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Visible capture status and a Stop button"; setSound(null, null)
                })
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == STOP) { stopCapture(); return START_NOT_STICKY }
        if (launched) return START_NOT_STICKY
        val action = intent?.getIntExtra("capture_action", 0) ?: 0
        if (Build.VERSION.SDK_INT < 30 || !PersonalizationPolicy.isCaptureAction(action)
            || !CapturePreferences.enabled(this) || !notificationsAvailable(this)
            || getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked) {
            fail("Capture is disabled or notification access is unavailable."); return START_NOT_STICKY
        }
        launched = true
        val audio = PersonalizationPolicy.isVideo(action) && CapturePreferences.audio(this)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
            || (audio && ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)) {
            fail("Camera or microphone permission is missing."); return START_NOT_STICKY
        }
        runCatching {
            val type = ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or
                if (audio) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
            startForeground(NOTICE, notification("Starting camera…"), type)
            mutableState.value = State("starting")
            handler.postDelayed(startTimeout, 30_000)
            val future = ProcessCameraProvider.getInstance(this)
            future.addListener({
                if (disposed || completed || stopping) return@addListener
                runCatching {
                    check(!getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked) { "Phone locked before camera start" }
                    provider = future.get()
                    val selector = if (PersonalizationPolicy.isFront(action)) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                    check(provider!!.hasCamera(selector)) { "Requested lens unavailable" }
                    if (PersonalizationPolicy.isVideo(action)) video(selector, audio) else photo(selector)
                }.onFailure { fail("Camera unavailable. Close other camera apps and try again.") }
            }, ContextCompat.getMainExecutor(this))
        }.onFailure { fail("Android denied camera access. No recording was started.") }
        return START_NOT_STICKY
    }

    private fun values(video: Boolean) = ContentValues().apply {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.ROOT).format(Date())
        put(MediaStore.MediaColumns.DISPLAY_NAME, "DotSuite_$stamp.${if (video) "mp4" else "jpg"}")
        put(MediaStore.MediaColumns.MIME_TYPE, if (video) "video/mp4" else "image/jpeg")
        put(MediaStore.MediaColumns.RELATIVE_PATH, if (video) "Movies/DotSuite" else "Pictures/DotSuite")
    }

    @SuppressLint("MissingPermission") // Checked before foreground service promotion; audio is explicit opt-in.
    private fun video(selector: CameraSelector, audio: Boolean) {
        val recorder = Recorder.Builder().setQualitySelector(
            QualitySelector.from(Quality.HD, FallbackStrategy.lowerQualityOrHigherThan(Quality.HD))).build()
        val capture = VideoCapture.withOutput(recorder)
        capture.targetRotation = displayRotation()
        provider!!.bindToLifecycle(this, selector, capture)
        val options = MediaStoreOutputOptions.Builder(contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(values(true)).setDurationLimitMillis(CapturePreferences.minutes(this) * 60_000L)
            .setFileSizeLimit(512L * 1024 * 1024).build()
        var pending = recorder.prepareRecording(this, options)
        if (audio) pending = pending.withAudioEnabled()
        recording = pending.start(ContextCompat.getMainExecutor(this)) { event ->
            if (disposed || completed) return@start
            when (event) {
                is VideoRecordEvent.Start -> {
                    handler.removeCallbacks(startTimeout)
                    if (stopping) { recording?.stop(); return@start }
                    mutableState.value = State("recording", "Recording · ${if (audio) "with audio" else "silent"}")
                    getSystemService(NotificationManager::class.java).notify(NOTICE, notification(mutableState.value.message))
                    FeatureJournal.record(this, "capture.video", "started")
                }
                is VideoRecordEvent.Finalize -> {
                    val saved = event.error in setOf(VideoRecordEvent.Finalize.ERROR_NONE,
                        VideoRecordEvent.Finalize.ERROR_DURATION_LIMIT_REACHED, VideoRecordEvent.Finalize.ERROR_FILE_SIZE_LIMIT_REACHED)
                    complete(saved, if (saved) "Video saved in Movies / DotSuite" else "Video could not be saved (camera error ${event.error}).")
                }
            }
        }
    }

    private fun photo(selector: CameraSelector) {
        val capture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setTargetRotation(displayRotation()).build()
        provider!!.bindToLifecycle(this, selector, capture)
        val options = ImageCapture.OutputFileOptions.Builder(contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values(false)).build()
        val takePhoto = {
            capture.takePicture(options, ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                if (!disposed) complete(true, "Photo saved in Pictures / DotSuite")
            }
            override fun onError(exception: ImageCaptureException) {
                if (!disposed) fail("Photo could not be saved. Camera released.")
            }
            })
        }
        val seconds = if (selector == CameraSelector.DEFAULT_BACK_CAMERA) CapturePreferences.glyphSeconds(this) else 0
        if (seconds == 0) takePhoto()
        else {
            glyph = GlyphCountdown(this)
            glyph!!.start(seconds, { remaining ->
                mutableState.value = State("starting", "Photo in $remaining")
                getSystemService(NotificationManager::class.java).notify(NOTICE, notification("Photo in $remaining · Stop to cancel"))
            }, {
                if (!disposed && !completed && !stopping) {
                    if (getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked) fail("Phone locked; photo cancelled.")
                    else takePhoto()
                }
            }, { fail("Glyph unavailable. No photo taken; disable the countdown or retry.") })
        }
    }

    @Suppress("DEPRECATION")
    private fun displayRotation(): Int = getSystemService(android.view.WindowManager::class.java)
        ?.defaultDisplay?.rotation ?: Surface.ROTATION_0

    private fun notification(text: String): android.app.Notification {
        val stop = PendingIntent.getService(this, 1, Intent(this, CaptureService::class.java).setAction(STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val open = PendingIntent.getActivity(this, 2,
            Intent(this, io.github.wiojelt.dotsuite.MainActivity::class.java)
                .putExtra("feature_page", "capture").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_qs_camera)
            .setContentTitle("DotSuite · Camera").setContentText(text)
            .setOngoing(true).setSilent(true).setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(android.R.drawable.ic_media_pause, "Stop capture", stop).setContentIntent(open).build()
    }

    private fun stopCapture() {
        if (stopping) return
        glyph?.close(); glyph = null
        stopping = true
        mutableState.value = State("stopping")
        handler.removeCallbacks(startTimeout)
        if (recording != null) {
            recording?.stop()
            handler.postDelayed(stopTimeout, 8_000)
        } else complete(false, "Capture cancelled.")
    }

    private fun fail(message: String) = complete(false, message)
    private fun complete(saved: Boolean, message: String) {
        if (disposed || completed) return
        glyph?.close(); glyph = null
        completed = true
        handler.removeCallbacksAndMessages(null)
        mutableState.value = State(if (saved) "saved" else "error", message)
        FeatureJournal.record(this, "capture", if (saved) "saved locally" else "stopped / failed")
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onTaskRemoved(rootIntent: Intent?) { stopCapture(); super.onTaskRemoved(rootIntent) }
    override fun onDestroy() {
        glyph?.close(); glyph = null
        disposed = true
        handler.removeCallbacksAndMessages(null)
        runCatching { recording?.close() }; recording = null
        runCatching { provider?.unbindAll() }; provider = null
        if (mutableState.value.busy) mutableState.value = State("error", "Capture ended; camera released.")
        super.onDestroy()
    }
}
