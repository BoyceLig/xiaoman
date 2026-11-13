package dev.xiaoman.hands

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Session-scoped screen capture for Android 10, where [android.accessibilityservice.AccessibilityService.takeScreenshot]
 * (API 30) is not available. It mirrors the screen through [MediaProjection]:
 *
 * - the first capture launches [ProjectionConsentActivity]; the user taps "Start now" once,
 * - the foreground service [ProjectionService] (type mediaProjection) keeps the grant alive,
 * - every later capture in the same session reuses the projection without asking again.
 *
 * The projection dies when the app is killed, the user stops it from the status bar, or
 * [release] is called; the next capture then asks for consent again. An explicit denial is
 * remembered so the consent dialog is not shown on every observation.
 */
class ProjectionCapture(context: Context) {

    private val appContext: Context = context.applicationContext ?: context
    private val mutex = Mutex()
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var width = 0
    private var height = 0
    private var denied = false
    private var consent: CompletableDeferred<Boolean>? = null

    /**
     * One screenshot as a Bitmap, or null when capture is unavailable (denied, consent not
     * granted, or no frame in time). Safe to call repeatedly; blocks while the consent dialog
     * is on screen.
     */
    suspend fun captureBitmap(): Bitmap? = mutex.withLock {
        if (denied) return@withLock null
        val current = projection ?: if (requestConsent()) projection else null
        current?.let { captureFrame(it) }
    }

    private suspend fun requestConsent(): Boolean {
        active = this
        val deferred = CompletableDeferred<Boolean>()
        consent = deferred
        try {
            appContext.startActivity(
                Intent(appContext, ProjectionConsentActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (e: Exception) {
            Log.w(TAG, "cannot launch projection consent: ${e.message}")
            consent = null
            denied = true
            return false
        }
        val granted = withTimeoutOrNull(CONSENT_TIMEOUT_MS) { deferred.await() } ?: false
        consent = null
        if (!granted) denied = true
        return granted
    }

    private fun deliver(resultCode: Int, data: Intent?) {
        val deferred = consent ?: return
        if (resultCode != Activity.RESULT_OK || data == null) {
            Log.i(TAG, "projection consent denied")
            deferred.complete(false)
            return
        }
        try {
            appContext.startForegroundService(
                Intent(appContext, ProjectionService::class.java)
                    .putExtra(ProjectionService.EXTRA_RESULT_CODE, resultCode)
                    .putExtra(ProjectionService.EXTRA_RESULT_DATA, data),
            )
        } catch (e: Exception) {
            Log.w(TAG, "projection service not started: ${e.message}")
            deferred.complete(false)
        }
    }

    /** Called by [ProjectionService] once it is in the foreground; the token may then be used. */
    private fun startProjection(resultCode: Int, resultData: Intent) {
        val deferred = consent ?: return
        try {
            val manager = appContext.getSystemService(MediaProjectionManager::class.java)
            val projection = manager.getMediaProjection(resultCode, resultData)
                ?: throw IllegalStateException("getMediaProjection returned null")
            projection.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    Log.i(TAG, "media projection stopped; asking again next time")
                    release()
                }
            }, Handler(Looper.getMainLooper()))
            this.projection = projection
            deferred.complete(true)
        } catch (e: Exception) {
            Log.w(TAG, "projection start failed: ${e.message}")
            deferred.complete(false)
        }
    }

    private suspend fun captureFrame(projection: MediaProjection): Bitmap? {
        val (w, h, dpi) = realMetrics()
        if (reader == null || w != width || h != height) recreateDisplay(projection, w, h, dpi)
        val reader = reader ?: return null
        repeat(FRAME_ATTEMPTS) {
            val image = reader.acquireLatestImage()
            if (image != null) {
                try {
                    return image.toBitmap(width, height)
                } finally {
                    image.close()
                }
            }
            delay(FRAME_DELAY_MS)
        }
        Log.w(TAG, "projection frame not available in time")
        return null
    }

    private fun recreateDisplay(projection: MediaProjection, w: Int, h: Int, dpi: Int) {
        display?.release()
        reader?.close()
        val newReader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, MAX_IMAGES)
        display = projection.createVirtualDisplay(
            "xiaoman-capture", w, h, dpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            newReader.surface, null, null,
        )
        reader = newReader
        width = w
        height = h
    }

    @Suppress("DEPRECATION") // getRealMetrics: the only real-size API on API 29.
    private fun realMetrics(): Triple<Int, Int, Int> {
        val display = appContext.getSystemService(DisplayManager::class.java)
            .getDisplay(Display.DEFAULT_DISPLAY)
        val metrics = DisplayMetrics()
        display.getRealMetrics(metrics)
        return Triple(metrics.widthPixels, metrics.heightPixels, metrics.densityDpi)
    }

    /** Stops the projection and the foreground service; the next capture asks for consent again. */
    fun release() {
        val projection = projection
        this.projection = null
        display?.release()
        display = null
        reader?.close()
        reader = null
        projection?.stop()
        try {
            appContext.stopService(Intent(appContext, ProjectionService::class.java))
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val TAG = "Xiaoman"
        private const val CONSENT_TIMEOUT_MS = 60_000L
        private const val FRAME_ATTEMPTS = 20
        private const val FRAME_DELAY_MS = 100L
        private const val MAX_IMAGES = 2

        @Volatile private var active: ProjectionCapture? = null

        /** Called by [ProjectionConsentActivity] with the dialog outcome. */
        internal fun onConsentResult(resultCode: Int, data: Intent?) {
            active?.deliver(resultCode, data)
        }

        /** Called by [ProjectionService] after it entered the foreground. */
        internal fun onServiceReady(resultCode: Int, data: Intent) {
            active?.startProjection(resultCode, data)
        }
    }
}

private fun Image.toBitmap(width: Int, height: Int): Bitmap {
    val plane = planes[0]
    val buffer = plane.buffer
    val rowPad = plane.rowStride - plane.pixelStride * width
    return if (rowPad == 0) {
        buffer.rewind()
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { copyPixelsFromBuffer(buffer) }
    } else {
        // Rows are padded to the stride; copy into a wider bitmap and crop.
        val rowWidth = width + rowPad / plane.pixelStride
        buffer.rewind()
        val padded = Bitmap.createBitmap(rowWidth, height, Bitmap.Config.ARGB_8888)
        try {
            padded.copyPixelsFromBuffer(buffer)
            Bitmap.createBitmap(padded, 0, 0, width, height)
        } finally {
            padded.recycle()
        }
    }
}

/** Transparent activity that shows the system MediaProjection consent dialog once. */
class ProjectionConsentActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            startActivityForResult(
                getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent(),
                REQUEST_CODE,
            )
        } catch (e: Exception) {
            Log.w(TAG, "projection consent not available: ${e.message}")
            finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE) {
            ProjectionCapture.onConsentResult(resultCode, data)
            finish()
        }
    }

    private companion object {
        const val TAG = "Xiaoman"
        const val REQUEST_CODE = 7001
    }
}

/**
 * Foreground service (type mediaProjection) that holds the screen-capture grant while the
 * session uses [ProjectionCapture]. Started only on Android 10; Android 11+ screenshots go
 * through the accessibility API and never start this service.
 */
class ProjectionService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val code = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        @Suppress("DEPRECATION")
        val data = intent?.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
        if (!enterForeground()) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (code == Activity.RESULT_CANCELED || data == null) {
            stopSelf()
        } else {
            ProjectionCapture.onServiceReady(code, data)
        }
        return START_NOT_STICKY
    }

    private fun enterForeground(): Boolean = try {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.xiaoman_projection_channel),
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_projection)
            .setContentTitle(getString(R.string.xiaoman_projection_title))
            .setContentText(getString(R.string.xiaoman_projection_text))
            .setOngoing(true)
            .build()
        // The typed overload exists since API 29 (our minSdk); the service declares the type.
        startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        true
    } catch (e: Exception) {
        Log.w(TAG, "projection foreground failed: ${e.message}")
        false
    }

    companion object {
        private const val TAG = "Xiaoman"
        const val EXTRA_RESULT_CODE = "xiaoman.result_code"
        const val EXTRA_RESULT_DATA = "xiaoman.result_data"
        private const val CHANNEL_ID = "xiaoman_projection"
        private const val NOTIFICATION_ID = 1002
    }
}
