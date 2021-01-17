package co.nicejourney.plumbcam

import android.Manifest
import android.Manifest.permission.ACCESS_WIFI_STATE
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import co.nicejourney.plumbcam.App.Companion.LOG_TAG
import kotlinx.coroutines.*


class MainActivity : AppCompatActivity() {

    lateinit var editPath: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (allPermissionsGranted()) {
            Toast.makeText(this, "All needed permissions granted by the user.", Toast.LENGTH_SHORT).show()
            initWorkerMonitor()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }
    }


    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                Log.d(LOG_TAG, ".. allPermissionsGranted")
            } else {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }


    var lastState: WorkInfo.State? = null
    var canStart = false

    private fun initWorkerMonitor() {
        val button: Button = findViewById(R.id.btnStartStopService);

//        val handler = Handler(Looper.getMainLooper())
        CaptureService.stop()
        CaptureService.start()
        suspend fun checker() {
            while (true) {
                val status: WorkInfo? = CaptureService.status

                runOnUiThread {
                    if (status == null) {
                        button.text = "Start Capture Service"
                        lastState = null
                        canStart = true
                    } else {
                        lastState = status.state
                        when (status.state) {
                            WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED -> {
                                button.text = "Stop Capture wService\n[${status.state}]"
                                canStart = false
                            }
                            else -> {
                                button.text = "Start Capture Service\n[${status.state}]"
                                canStart = true
                            }
                        }
                    }
                    button.isEnabled = true
                }
                delay(1000)
            }
        }
        GlobalScope.launch { checker() }
    }


    fun btnStartStopService(view: View) {
        if (canStart) {
            CaptureService.start()
        } else {
            CaptureService.stop()
        }
        view.isEnabled = false
    }

    fun btnCapture(view: View) {
        Toast.makeText(this@MainActivity, CaptureService.captureOnDemand(), Toast.LENGTH_SHORT).show()
//        GlobalScope.launch(Dispatchers.IO) {
//            val cameraAPI = CameraAPI(this@MainActivity)
//            val fileName = CameraAPI.photoImagePath(Config.DEFAULT_WWW_PATH)
//            var result = ""
//            cameraAPI.backCamera?.let {
//                it.capture(fileName)
//                result = "Captured into $fileName"
//            } ?: {
//                result = "Back camera is unavailable"
//            }
//            runOnUiThread {
//                Toast.makeText(this@MainActivity, result, Toast.LENGTH_SHORT).show()
//            }
//            cameraAPI.close()
//        }
    }

    fun btnTest1(view: View) {
        val capi = CameraAPI(this)
        Log.i(
            LOG_TAG, """
           front -> ${capi.frontCamera}
           back -> ${capi.backCamera}
           ${capi.backCamera?.resolutions}
        """.trimIndent()
        )


        //        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
//        startActivityForResult(intent, 100)
    }

    fun btnTest2(view: View) {
        GlobalScope.launch(Dispatchers.IO) {
            val cameraAPI = CameraAPI(this@MainActivity)
            Dispatchers.IO.run {
                val fileName = Config.DEFAULT_WWW_PATH + "/123.jpg"
                cameraAPI.backCamera?.capture(fileName)
            }
            cameraAPI.close()
        }
    }

//    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
//        super.onActivityResult(requestCode, resultCode, data)
//        if (requestCode == 100 && resultCode == RESULT_OK) {
//            val url = data?.data
//            Log.i(LOG_TAG, url.toString())
//        }
//    }


    companion object {
        val REQUEST_CODE_PERMISSIONS = 10
        val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ACCESS_WIFI_STATE
        )
    }
}


//
//var lastState: WorkInfo.State? = null
//var canStart = false
//private fun initWorkerMonitor() {
//    val button: Button = findViewById(R.id.btnStartStopService);
//
//    val workManager = WorkManager.getInstance(this)
//    val handler = Handler(Looper.getMainLooper())
//
//    val mainExecutor = ContextCompat.getMainExecutor(this)
//    val runnable = object : Runnable {
//        override fun run() {
//            val workInfosForUniqueWorkFuture = workManager.getWorkInfosForUniqueWork(CaptureService.TAG)
//            workInfosForUniqueWorkFuture.addListener(Runnable {
//                val item: WorkInfo? = workInfosForUniqueWorkFuture.get().firstOrNull()
//                if (item == null) {
//                    button.text = "Start Capture Service"
//                    lastState = null
//                    canStart = true
//                } else {
//                    lastState = item.state
//                    when (item.state) {
//                        WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED -> {
//                            button.text = "Stop Capture wService\n[${item.state}]"
//                            canStart = false
//                        }
//                        else -> {
//                            button.text = "Start Capture Service\n[${item.state}]"
//                            canStart = true
//                        }
//                    }
//                }
//                button.isEnabled = true
//                handler.postDelayed(this, 1000)
//
//            }, mainExecutor)
//        }
//    }
//    handler.postDelayed(runnable, 1000)
////}
//
//
//val url = try {
//    URI(editPath.text.toString())
//} catch (e: Exception) {
//    Toast.makeText(this, "Error $e", Toast.LENGTH_LONG).show()
//    return
//}
//if (url.scheme != "smb") {
//    Toast.makeText(this, "Error: proto should be smb", Toast.LENGTH_SHORT).show()
//    return
//}
//
//val paths = Paths.get(url.path)
//if (paths.nameCount < 1) {
//    Toast.makeText(this, "Error: path should contain at least share name", Toast.LENGTH_SHORT).show()
//    return
//}
//val shareName = paths.getName(0).toString()
//val path = if (paths.nameCount > 1) paths.subpath(1, paths.nameCount).toString() else ""
//Log.i(LOG_TAG, "${url.host}, $shareName, $path")
//
//Executors.newSingleThreadExecutor().execute {
////            val smbClient = SMBClient()
////            val c = smbClient.connect(url.host)
////            val session = c.authenticate(AuthenticationContext.guest())
////            val connectShare = session.connectShare(shareName) as DiskShare
////            connectShare.list(path).forEach {
////                Log.i(LOG_TAG, it.getFileName())
////            }
//
////            val base = SingletonContext.getInstance();
////            var authed = base.withGuestCrendentials()
////            val f = SmbFile(url.toString(), authed)
////            Log.i(LOG_TAG, f.list().toList().toString())
//}

