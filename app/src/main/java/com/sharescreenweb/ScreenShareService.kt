package com.sharescreenweb

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.SystemClock
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Foreground service that captures the screen via MediaProjection, encodes frames as JPEG
 * and publishes them to the MJPEG HTTP server.
 */
class ScreenShareService : Service() {

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var server: MjpegServer? = null

    private lateinit var captureThread: HandlerThread
    private lateinit var captureHandler: Handler

    private var captureWidth = 0
    private var captureHeight = 0
    private var densityDpi = 0
    private var lastFrameAt = 0L
    private var paddedBitmap: Bitmap? = null
    private val jpegBuffer = ByteArrayOutputStream(256 * 1024)

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            // User stopped sharing (e.g. via status bar chip or lock screen).
            stopSharing()
        }

        override fun onCapturedContentResize(width: Int, height: Int) {
            // API 34+: captured content changed size (rotation, single-app capture resized).
            captureHandler.post { resizeCapture(width, height) }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        captureThread = HandlerThread("screen-capture").also { it.start() }
        captureHandler = Handler(captureThread.looper)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSharing()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val data: Intent? = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION") intent.getParcelableExtra(EXTRA_RESULT_DATA)
                }
                // Must be in the foreground BEFORE obtaining the MediaProjection (Android 10+/14+),
                // and always promptly after startForegroundService().
                goForeground()
                if (projection != null) return START_NOT_STICKY
                if (data == null) {
                    stopSharing()
                    return START_NOT_STICKY
                }
                try {
                    startSharing(resultCode, data)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start sharing", e)
                    stopSharing()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startSharing(resultCode: Int, data: Intent) {
        val srv = MjpegServer()
        val port = srv.start()
        server = srv

        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val mp = mpm.getMediaProjection(resultCode, data)
            ?: throw IllegalStateException("MediaProjection unavailable")
        projection = mp
        // Registering a callback before createVirtualDisplay is mandatory on Android 14+.
        mp.registerCallback(projectionCallback, captureHandler)

        val (w, h) = scaledScreenSize()
        captureWidth = w
        captureHeight = h
        densityDpi = resources.displayMetrics.densityDpi

        val reader = newImageReader(w, h)
        imageReader = reader
        virtualDisplay = mp.createVirtualDisplay(
            "ScreenShareWeb", w, h, densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface, null, captureHandler
        )
        ShareState.update(port)
    }

    private fun newImageReader(w: Int, h: Int): ImageReader =
        ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2).apply {
            setOnImageAvailableListener({ onImageAvailable(it) }, captureHandler)
        }

    private fun onImageAvailable(reader: ImageReader) {
        val image = try {
            reader.acquireLatestImage()
        } catch (_: Exception) {
            null
        } ?: return
        try {
            val now = SystemClock.elapsedRealtime()
            if (now - lastFrameAt < FRAME_INTERVAL_MS) return
            lastFrameAt = now

            val plane = image.planes[0]
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val width = image.width
            val height = image.height
            val paddedWidth = rowStride / pixelStride

            var bmp = paddedBitmap
            if (bmp == null || bmp.width != paddedWidth || bmp.height != height) {
                bmp?.recycle()
                bmp = Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888)
                paddedBitmap = bmp
            }
            plane.buffer.rewind()
            bmp!!.copyPixelsFromBuffer(plane.buffer)

            val frame = if (paddedWidth != width) Bitmap.createBitmap(bmp, 0, 0, width, height) else bmp
            jpegBuffer.reset()
            frame.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, jpegBuffer)
            if (frame !== bmp) frame.recycle()
            server?.publish(jpegBuffer.toByteArray())
        } catch (e: Exception) {
            Log.w(TAG, "Frame processing failed", e)
        } finally {
            image.close()
        }
    }

    /** Recreates the capture surface with a new size, keeping the aspect ratio of the content. */
    private fun resizeCapture(contentWidth: Int, contentHeight: Int) {
        val vd = virtualDisplay ?: return
        val (w, h) = scale(contentWidth, contentHeight)
        if (w == captureWidth && h == captureHeight) return
        captureWidth = w
        captureHeight = h
        val oldReader = imageReader
        val reader = newImageReader(w, h)
        imageReader = reader
        vd.resize(w, h, densityDpi)
        vd.surface = reader.surface
        oldReader?.setOnImageAvailableListener(null, null)
        oldReader?.close()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Before Android 14 there is no onCapturedContentResize; handle rotation here.
        if (Build.VERSION.SDK_INT < 34 && virtualDisplay != null) {
            val (rw, rh) = realScreenSize()
            captureHandler.post { resizeCapture(rw, rh) }
        }
    }

    private fun scaledScreenSize(): Pair<Int, Int> {
        val (w, h) = realScreenSize()
        return scale(w, h)
    }

    private fun realScreenSize(): Pair<Int, Int> {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return if (Build.VERSION.SDK_INT >= 30) {
            val b = wm.maximumWindowMetrics.bounds
            b.width() to b.height()
        } else {
            val dm = DisplayMetrics()
            @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(dm)
            dm.widthPixels to dm.heightPixels
        }
    }

    /** Scales so the longer side is at most MAX_DIMENSION; dimensions are kept even. */
    private fun scale(w: Int, h: Int): Pair<Int, Int> {
        val longer = max(w, h)
        val factor = if (longer > MAX_DIMENSION) MAX_DIMENSION.toFloat() / longer else 1f
        fun even(v: Float) = max(2, (v.roundToInt() / 2) * 2)
        return even(w * factor) to even(h * factor)
    }

    private fun goForeground() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW)
            )
        }
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, ScreenShareService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL_ID)
        else @Suppress("DEPRECATION") Notification.Builder(this)
        val notification = builder
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.app_name))
            .setContentIntent(openApp)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, getString(R.string.notif_stop), stop).build())
            .build()

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopSharing() {
        ShareState.update(null)
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.setOnImageAvailableListener(null, null)
        imageReader?.close()
        imageReader = null
        projection?.let {
            it.unregisterCallback(projectionCallback)
            it.stop()
        }
        projection = null
        server?.stop()
        server = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (projection != null || server != null) stopSharing()
        captureThread.quitSafely()
        paddedBitmap?.recycle()
        paddedBitmap = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ScreenShareService"
        const val ACTION_START = "com.sharescreenweb.START"
        const val ACTION_STOP = "com.sharescreenweb.STOP"
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_RESULT_DATA = "resultData"

        private const val CHANNEL_ID = "screen_share"
        private const val NOTIFICATION_ID = 1
        private const val MAX_DIMENSION = 1280
        private const val JPEG_QUALITY = 70
        private const val FRAME_INTERVAL_MS = 66L // ~15 fps
    }
}
