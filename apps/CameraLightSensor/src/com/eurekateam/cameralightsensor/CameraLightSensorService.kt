package com.eurekateam.cameralightsensor

import android.annotation.SuppressLint
import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.Image
import android.media.ImageReader
import android.os.*
import android.provider.Settings
import android.provider.Settings.SettingNotFoundException
import android.util.Log
import androidx.core.app.NotificationCompat
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

class CameraLightSensorService : Service() {
    private lateinit var screenStateFilter: IntentFilter
    lateinit var mContext: Context
    private var cameraDevice: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private lateinit var manager: CameraManager
    private lateinit var mCameraHandler: Handler
    private fun pushNotification(): Notification {
        val nm = mContext.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            mContext.basePackageName, "CameraLightSensor",
            NotificationManager.IMPORTANCE_NONE
        )
        channel.isBlockable = true
        nm.createNotificationChannel(channel)
        val builder = NotificationCompat.Builder(mContext, mContext.basePackageName)
        val notificationIntent = Intent(mContext, CameraLightSensorService::class.java)
        val contentIntent = PendingIntent.getActivity(
            mContext, 50,
            notificationIntent, PendingIntent.FLAG_IMMUTABLE
        )

        builder.setContentIntent(contentIntent)
        builder.setSmallIcon(R.drawable.ic_brightness)
        builder.setContentTitle("Camera Light Sensor Service")
        builder.setChannelId(mContext.basePackageName)
        return builder.build()
    }

    override fun onDestroy() {
        if (DEBUG) Log.d(TAG, "Destroying service")
        if (mRegistered) contentResolver!!.unregisterContentObserver(mSettingsObserver)
        mRegistered = false
        imageReader.close()
        stopForeground(true)
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    private val mScreenStateReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_USER_PRESENT) {
                if (mPoolExecutor == null) {
                    mPoolExecutor = ScheduledThreadPoolExecutor(4)
                    mPoolExecutor!!.scheduleWithFixedDelay(
                        mScheduler, 0, 2, TimeUnit.SECONDS
                    )
                }
            } else if (intent.action == Intent.ACTION_SCREEN_OFF) {
                if (mPoolExecutor != null) {
                    mPoolExecutor!!.shutdown()
                    mPoolExecutor = null
                }
            }
        }
    }
    private val mScheduler = Runnable { readyCamera() }

    // Make a listener for settings
    private var mSettingsObserver: ContentObserver =
        object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                super.onChange(selfChange)
                Log.i(TAG, "observer: Brightness Settings Changed")
                try {
                    if (Settings.System.getInt(
                            contentResolver,
                            Settings.System.SCREEN_BRIGHTNESS_MODE
                        )
                        == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
                    ) {
                        registerReceiver(mScreenStateReceiver, screenStateFilter)
                        mRegistered = true
                        if (mPoolExecutor == null) {
                            mPoolExecutor = ScheduledThreadPoolExecutor(4)
                            mPoolExecutor!!.scheduleWithFixedDelay(
                                mScheduler, 0, 2, TimeUnit.SECONDS
                            )
                        }
                    } else {
                        if (mRegistered) unregisterReceiver(mScreenStateReceiver)
                        mRegistered = false
                        if (mPoolExecutor != null) {
                            mPoolExecutor!!.shutdown()
                            mPoolExecutor = null
                        }
                    }
                } catch (e: SettingNotFoundException) {
                    e.printStackTrace()
                }
            }

            override fun deliverSelfNotifications(): Boolean {
                return true
            }
        }
    private var cameraStateCallback: CameraDevice.StateCallback =
        object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                Log.d(TAG, "CameraDevice.StateCallback onOpened")
                cameraDevice = camera
                actOnReadyCameraDevice()
            }

            override fun onDisconnected(camera: CameraDevice) {
                Log.w(TAG, "CameraDevice.StateCallback onDisconnected")
            }

            override fun onError(camera: CameraDevice, error: Int) {
                Log.e(TAG, "CameraDevice.StateCallback onError $error")
            }
        }
    private var sessionStateCallback: CameraCaptureSession.StateCallback =
        object : CameraCaptureSession.StateCallback() {
            override fun onReady(session: CameraCaptureSession) {
                this@CameraLightSensorService.session = session
                try {
                    if (createCaptureRequest() == null) return
                    try {
                        session.capture(createCaptureRequest(), null, mCameraHandler)
                    } catch (e: CameraAccessException) {
                        e.printStackTrace()
                    } catch (e: IllegalStateException) {
                        Log.w(TAG, "onReady: Session is NULL")
                    }
                    cameraDevice!!.close()
                    session.close()
                } catch (e: Exception) {
                    Log.e(TAG, "Camera is in use")
                    e.printStackTrace()
                }
            }
            override fun onConfigured(session: CameraCaptureSession) {}
            override fun onConfigureFailed(session: CameraCaptureSession) {}
        }
    private var onImageAvailableListener =
        ImageReader.OnImageAvailableListener { reader: ImageReader ->
            if (DEBUG) Log.d(TAG, "onImageAvailable: Capturing")
            val img = reader.acquireLatestImage()
            if (img != null) {
                try {
                    processImage(img)
                } catch (e: SettingNotFoundException) {
                    e.printStackTrace()
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
                img.close()
            }
        }

    @SuppressLint("MissingPermission")
    fun readyCamera() {
        manager = getSystemService(CAMERA_SERVICE) as CameraManager
        try {
            val pickedCamera = getCamera(manager)
            manager.openCamera(pickedCamera, cameraStateCallback, mCameraHandler)
            imageReader.setOnImageAvailableListener(onImageAvailableListener, mCameraHandler)
            if (DEBUG) Log.d(TAG, "imageReader created")
        } catch (e: CameraAccessException) {
            Log.e(TAG, e.message)
        }
    }

    private fun getCamera(manager: CameraManager?): String? {
        try {
            for (cameraId in manager!!.cameraIdList) {
                val characteristics = manager.getCameraCharacteristics(cameraId)
                val cOrientation = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (cOrientation == CAMERA_CHOICE) {
                    return cameraId
                }
            }
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val mCameraHandlerThread = HandlerThread("CameraLightSensor")
        mCameraHandlerThread.start()
        mCameraHandler = Handler(mCameraHandlerThread.looper)
        mContext = this
        @Suppress("SameParameterValue")
        startForeground(50, pushNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
        mRegistered = false
        screenStateFilter = IntentFilter(Intent.ACTION_USER_PRESENT)
        screenStateFilter.addAction(Intent.ACTION_SCREEN_OFF)
        try {
            if (Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE)
                == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
            ) {
                registerReceiver(mScreenStateReceiver, screenStateFilter)
                mRegistered = true
            }
        } catch (e: SettingNotFoundException) {
            e.printStackTrace()
        }
        val setting = Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS_MODE)
        contentResolver?.registerContentObserver(setting, false, mSettingsObserver)
        if (DEBUG) Log.d(TAG, "onStartCommand flags $flags startId $startId")
        startForeground(50, pushNotification())
        return START_STICKY
    }

    override fun onCreate() {
        if (DEBUG) Log.d(TAG, "onCreate service")
        mContext = this
        startForeground(50, pushNotification())
        super.onCreate()
    }

    fun actOnReadyCameraDevice() {
        try {
            cameraDevice!!.createCaptureSession(
                listOf(imageReader.surface),
                sessionStateCallback,
                mCameraHandler
            )
        } catch (e: CameraAccessException) {
            Log.e(TAG, e.message)
        }
    }

    @Throws(SettingNotFoundException::class, InterruptedException::class)
    private fun processImage(image: Image) {
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.capacity())
        buffer[bytes]
        val bitmapImage = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, null)
        val brightness = calculateBrightnessEstimate(bitmapImage, 2)
        adjustBrightness(brightness)
    }

    protected fun createCaptureRequest(): CaptureRequest? {
        return try {
            val builder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
            builder.addTarget(imageReader!!.surface)
            builder.build()
        } catch (e: CameraAccessException) {
            Log.e(TAG, e.message)
            null
        }
    }
    private fun calculateBrightnessEstimate(bitmap: Bitmap, pixelSpacing: Int): Int {
        var r = 0
        var g = 0
        var b = 0
        val height = bitmap.height
        val width = bitmap.width
        var n = 0
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        var i = 0
        while (i < pixels.size) {
            val color = pixels[i]
            r += Color.red(color)
            g += Color.green(color)
            b += Color.blue(color)
            n++
            i += pixelSpacing
        }
        return (r + g + b) / (n * 3)
    }

    @Throws(SettingNotFoundException::class)
    private fun adjustBrightness(brightness: Int) {
        if (DEBUG) Log.i(TAG, "AdjustBrightness: Received Brightness Value $brightness")
        val oldbrightness =
            Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        if (DEBUG) Log.i(
            TAG,
            "AdjustBrightness: OldVal = " + oldbrightness + " NewVal = " +
                brightness + " Adjusting.."
        )
        var newbrightness = 2 * brightness - oldbrightness
        if (newbrightness > 255) {
            newbrightness = 255
        } else if (newbrightness < 0) {
            newbrightness = 0
        }
        Settings.System.putInt(
            contentResolver,
            Settings.System.SCREEN_BRIGHTNESS,
            newbrightness
        )
    }

    companion object {
        private val imageReader =
            ImageReader.newInstance(50, 50, ImageFormat.JPEG, 2 /* images buffered */)
        protected val TAG: String = CameraLightSensorService::class.java.simpleName
        const val DEBUG = false
        const val CAMERA_CHOICE = CameraCharacteristics.LENS_FACING_FRONT
        private var mPoolExecutor: ScheduledThreadPoolExecutor? = null
        private var mRegistered = false
    }
}
