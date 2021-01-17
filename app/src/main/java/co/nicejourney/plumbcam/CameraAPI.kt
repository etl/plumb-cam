package co.nicejourney.plumbcam

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.hardware.camera2.CameraCaptureSession.CaptureCallback
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.OrientationEventListener
import android.view.Surface
import co.nicejourney.plumbcam.App.Companion.LOG_TAG
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine


class CameraAPI(private val context: Context) {

    var frontCamera: Camera? = null
    var backCamera: Camera? = null
    var orientation: Int = Surface.ROTATION_0

    internal lateinit var backgroundHandler: Handler
    private lateinit var backgroundThread: HandlerThread

    val screenOrientationProvider = { orientation }
    private val orientationListener = object : OrientationEventListener(context) { // 3 sec
        override fun onOrientationChanged(angle: Int) {
            if (angle == ORIENTATION_UNKNOWN) {
                return
            }
            orientation = when (angle) {
                in 45 until 135 -> Surface.ROTATION_270
                in 135 until 225 -> Surface.ROTATION_180
                in 225 until 315 -> Surface.ROTATION_90
                else -> Surface.ROTATION_0
            }
            Log.i(LOG_TAG, "New orientation: $angle -> $orientation")
        }
    }


    init {
        try {
            val cameraManager: CameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            cameraManager.cameraIdList.forEach {
                val cameraCharacteristics = cameraManager.getCameraCharacteristics(it)
                val configurationMap: StreamConfigurationMap = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!

                val resolutions = configurationMap.getOutputSizes(ImageFormat.JPEG).sortedByDescending { it.width * it.height }
                val facing = cameraCharacteristics.get(CameraCharacteristics.LENS_FACING)

                when (facing) {
                    CameraCharacteristics.LENS_FACING_BACK ->
                        backCamera = Camera(this, cameraManager, it, resolutions)
                    CameraCharacteristics.LENS_FACING_FRONT ->
                        frontCamera = Camera(this, cameraManager, it, resolutions)
                }
            }
            Log.i(LOG_TAG, "Camera found: $backCamera, $frontCamera")

            backgroundThread = HandlerThread("Camera Background")
            backgroundThread.start()
            backgroundHandler = Handler(backgroundThread.looper)
            orientationListener.enable()
        } catch (e: Throwable) {
            Log.e(LOG_TAG, e.toString())
            e.printStackTrace()
        }
    }

    fun close() {
        orientationListener.disable()
        backgroundThread.quitSafely()
        try {
            backgroundThread.join()
        } catch (e: InterruptedException) {
            Log.e(LOG_TAG, e.toString())
        }
        Log.i(LOG_TAG, "${this.javaClass.simpleName} closed")
    }

    companion object {
        fun photoImagePath(basePath: String): String = File(basePath, SimpleDateFormat("yyyy-MM-dd_HH_mm_ss", Locale.ROOT).format(Date()) + ".jpg").absolutePath
    }
}


class Camera(private val cameraApi: CameraAPI, val cameraManager: CameraManager, val id: String, val resolutions: List<Size>) {
    private var cameraDevice: CameraDevice? = null

    val isOpen get() = cameraDevice != null

    @SuppressLint("MissingPermission")
    private suspend fun open(): CameraDevice {
        cameraDevice = suspendCoroutine<CameraDevice?> { cont ->
            try {
                Log.i(LOG_TAG, "Opening camera: $this")
                cameraManager.openCamera(
                    id, object : CameraDevice.StateCallback() {
                        override fun onOpened(camera: CameraDevice) {
                            Log.i(LOG_TAG, "Camera${camera.id} - Opened")
                            cont.resume(camera)
                        }

                        override fun onDisconnected(camera: CameraDevice) {
                            Log.i(LOG_TAG, "Camera${camera.id} - Disconnted")
                            closeCamera()
                        }

                        override fun onClosed(camera: CameraDevice) {
                            Log.i(LOG_TAG, "Camera${camera.id} - Closed")
                        }

                        override fun onError(camera: CameraDevice, error: Int) {
                            val errMessage = "Camera${camera.id} - error.toString()"
                            Log.i(LOG_TAG, errMessage)
                            cont.resumeWithException(Exception(errMessage))
                        }
                    },
                    cameraApi.backgroundHandler
                )
            } catch (e: Exception) {
                Log.i(LOG_TAG, e.toString())
            }
        }
//        Log.i(LOG_TAG, "HERE1 -------")
        return cameraDevice!!
    }

    private fun closeCamera() {
        cameraDevice?.let {
            Log.i(LOG_TAG, "Closing camera${cameraDevice?.id}")
            it.close()
            cameraDevice = null
        }
    }

    suspend fun capture(filePath: String): Boolean {
        try {
            open()
            suspendCoroutine<Unit?> { cont ->

                val imageReader = ImageReader.newInstance(resolutions[0].width, resolutions[0].height, ImageFormat.JPEG, 1)
                val captureRequest = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)!!
                captureRequest.addTarget(imageReader.surface)
                captureRequest.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
                captureRequest.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON_AUTO_FLASH)
                captureRequest.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS[cameraApi.screenOrientationProvider()])

                val readerListener: ImageReader.OnImageAvailableListener = ImageReader.OnImageAvailableListener { reader ->
                    var image: Image? = null
                    try {
                        image = reader.acquireLatestImage()
                        val file = File(filePath)
                        image?.apply {
                            val buffer = this.planes[0].buffer
                            file.outputStream().channel.write(buffer)
                        }
                    } catch (e: Exception) {
                        Log.e(LOG_TAG, e.toString())
                        cont.resumeWithException(e)
                    } finally {
                        image?.apply { this.close() }
                    }
                }
                imageReader.setOnImageAvailableListener(readerListener, cameraApi.backgroundHandler)

                val captureCallback = object : CaptureCallback() {
                    override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                        super.onCaptureCompleted(session, request, result)
                        Log.i(LOG_TAG, "onCaptureCompleted: $filePath")
                        try {
                            session.close()
                        } catch (e: Exception) {
                            Log.e(LOG_TAG, "onCaptureCompleted: $e")
                        }
                        cont.resume(null)
                    }
                }

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    cameraDevice?.createCaptureSession(
                        SessionConfiguration(
                            SessionConfiguration.SESSION_REGULAR,
                            listOf(OutputConfiguration(imageReader.surface)),
                            Dispatchers.IO.asExecutor(),
                            object : CameraCaptureSession.StateCallback() {
                                override fun onConfigured(session: CameraCaptureSession) {
                                    session.capture(captureRequest.build(), captureCallback, cameraApi.backgroundHandler);
                                }

                                override fun onConfigureFailed(session: CameraCaptureSession) {
                                    cont.resumeWithException(Exception("onConfigureFailed"))
                                }
                            }
                        )
                    )
                } else {
                    cameraDevice?.createCaptureSession(
                        listOf(imageReader.surface),
                        object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(session: CameraCaptureSession) {
                                session.capture(captureRequest.build(), captureCallback, cameraApi.backgroundHandler);
                            }

                            override fun onConfigureFailed(session: CameraCaptureSession) {
                                cont.resumeWithException(Exception("onConfigureFailed"))
                            }
                        }, cameraApi.backgroundHandler
                    )
                }

            }
            return true
        } catch (e: CameraAccessException) {
            Log.e(LOG_TAG, "Capture: $e")
            e.printStackTrace()
        } finally {
            closeCamera()
        }
        return false
    }

    companion object {
        val ORIENTATIONS = mapOf(
            Surface.ROTATION_0 to 90,
            Surface.ROTATION_90 to 0,
            Surface.ROTATION_180 to 270,
            Surface.ROTATION_270 to 180
        )
    }
}


