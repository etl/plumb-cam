package co.nicejourney.plumbcam.backup

//import android.util.Log
//import androidx.camera.core.CameraSelector
//import androidx.camera.core.ImageCapture
//import androidx.camera.core.ImageCaptureException
//import androidx.camera.lifecycle.ProcessCameraProvider
//import androidx.lifecycle.LifecycleOwner
//import co.nicejourney.plumbcam.App
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.asExecutor
//import java.io.File
//import java.text.SimpleDateFormat
//import java.util.*
//
//
//class Capture(private var lifecycleOwner: LifecycleOwner, val photoPath: String) {
//
//    var imageCapture: ImageCapture = ImageCapture.Builder()
//        .setFlashMode(ImageCapture.FLASH_MODE_ON)
//        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
//        .build()
//
//    init {
//        // Set up the capture use case to allow users to take photos.
//    }
//
//    fun capture(callback: (file: File?, err: Exception?) -> Unit) {
//        Log.i(LOG_TAG, "capture")
//
//        val cameraProvider = ProcessCameraProvider.getInstance(App.instance()).get()
//
//        // Choose the camera by requiring a lens facing
//        val cameraSelector = CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build()
//
//        // Attach use cases to the camera with the same lifecycle owner
//        val camera = try {
//            cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, imageCapture)
//        } catch (e: Exception) {
//            Log.e(LOG_TAG, "Capture failed $e")
//            return
//        }
//
//        imageCapture.targetRotation = App.instance().display!!.rotation
//        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(Date())
//        val file = File.createTempFile(timeStamp, ".jpg", File(photoPath))
//
//        val outputFileOptions = ImageCapture.OutputFileOptions.Builder(file).build()
//        imageCapture.takePicture(outputFileOptions, Dispatchers.Main.asExecutor(),
//            object : ImageCapture.OnImageSavedCallback {
//                override fun onError(error: ImageCaptureException) {
//                    onOperationEnd(cameraProvider, null, error, callback)
//                }
//
//                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
//                    onOperationEnd(cameraProvider, file, null, callback)
//                }
//            })
//
//    }
//
//    fun onOperationEnd(
//        cameraProvider: ProcessCameraProvider,
//        file: File?,
//        error: Exception?,
//        callback: ((file: File?, err: Exception?) -> Unit)?,
//    ) {
//        cameraProvider.unbindAll()
//        if (error != null) {
//            Log.i(LOG_TAG, error.toString())
//        } else {
//            val msg = "Photo capture succeeded: $file"
//            Log.i(LOG_TAG, msg)
//        }
//        callback?.let { it(file, error) }
//    }
//
//}