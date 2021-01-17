package co.nicejourney.plumbcam

import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
import android.graphics.Color
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.work.*
import co.nicejourney.plumbcam.App.Companion.LOG_TAG
import kotlinx.coroutines.*
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class CaptureService(context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams), LifecycleOwner {

    private val lifecycleRegistry: LifecycleRegistry = LifecycleRegistry(this)

    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }


    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {

        var httpd: HttpServer? = null
        var cameraAPI: CameraAPI? = null

        try {
            Log.i(LOG_TAG, "Background Service Starts")
            lifecycleRegistry.currentState = Lifecycle.State.STARTED
            setForeground(createForegroundInfo("Started"))

            val port = Config.instance.port
            val photoPath = Config.instance.capturePath
            val adminPath = Config.DEFAULT_WWW_PATH

            httpd = HttpServer(
                port,
                photoPath,
                adminPath
            )

            cameraAPI = CameraAPI(this@CaptureService.applicationContext)
            while (isActive) {
                var onDemand = false
                lock.withLock {
                    onDemand = triggerShutter.await(3, TimeUnit.SECONDS)
                }
                val captureInterval = Config.instance.captureIntervalSec * 1000
                val timeBeforeStart = lastCaptureTime + captureInterval - System.currentTimeMillis()
                if (timeBeforeStart <= 0 || onDemand) {
                    try {
                        val filePath = CameraAPI.photoImagePath(photoPath)
                        cameraAPI.backCamera?.capture(filePath)
                        lastCapturedFile = filePath
                        lastCaptureTime = System.currentTimeMillis()
                        lock.withLock {
                            triggerResult.signalAll()
                        }
                    } catch (e: Exception) {
                        Log.e(LOG_TAG, "While capturing: $e")
                    }
                } else {
                    Log.i(LOG_TAG, "ping ... time before start:${timeBeforeStart / 1000} s")
                }
            }


        } catch (error: CancellationException) {
            Log.i(LOG_TAG, "Was canceled: " + error.toString())
        } catch (error: Exception) {
            Log.e(LOG_TAG, error.toString())
            error.printStackTrace()
            lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
            return@withContext Result.failure()
        } finally {
            done(cameraAPI, httpd)
        }
        return@withContext Result.success()
    }

    fun done(cameraAPI: CameraAPI?, httpd: HttpServer?) {
        try {
            cameraAPI?.close()
            httpd?.done()
        } catch (e: Exception) {
        }
        Log.i(LOG_TAG, "Background Service Done")
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        setForegroundAsync(createForegroundInfo("Done"))
    }

    private fun createForegroundInfo(progress: String): ForegroundInfo {
        val id = CaptureService.TAG
        val title = CaptureService.TAG
//        val cancel = applicationContext.getString(R.string.cancel_download)
        // This PendingIntent can be used to cancel the worker
//        val intent = WorkManager.getInstance(applicationContext).createCancelPendingIntent(getId())

        val notification = NotificationCompat.Builder(applicationContext, id)
            .setContentTitle(title)
            .setTicker(title)
            .setContentText(progress)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setColor(Color.WHITE)
            .setOngoing(true)
//            .addAction(android.R.drawable.ic_delete, cancel, intent)
            .build()
        return ForegroundInfo(0, notification, FOREGROUND_SERVICE_TYPE_CAMERA)
    }

    companion object {
        const val TAG: String = "Capture-Service"

        @Volatile
        var lastCaptureTime: Long = 0

        @Volatile
        var startTime: Long = System.currentTimeMillis()

        @Volatile
        var lastCapturedFile: String? = null

        val status: WorkInfo?
            get() {
                val workInfosForUniqueWorkFuture = WorkManager.getInstance(App.instance().applicationContext).getWorkInfosForUniqueWork(CaptureService.TAG)
                val item: WorkInfo? = workInfosForUniqueWorkFuture.get().firstOrNull()
                return item
            }

        fun stop() {
            WorkManager.getInstance(App.instance().applicationContext).cancelUniqueWork(CaptureService.TAG)
        }

        fun start() {
            val workRequest = OneTimeWorkRequestBuilder<CaptureService>().addTag(CaptureService.TAG).build()
            val workManager = WorkManager.getInstance(App.instance().applicationContext)
            workManager.enqueueUniqueWork(CaptureService.TAG, ExistingWorkPolicy.KEEP, workRequest)
        }

        val uptime get() = System.currentTimeMillis() - startTime

        private val lock = ReentrantLock()
        private val triggerShutter = lock.newCondition()
        private val triggerResult = lock.newCondition()
        fun captureOnDemand(): String? {
            lock.withLock {
                triggerShutter.signalAll()
                val ok = triggerResult.await(10, TimeUnit.SECONDS)
                Log.i(TAG, "Capture returned $ok: $lastCapturedFile")
                return if (ok) lastCapturedFile else null
            }
        }

    }

    override fun getLifecycle(): Lifecycle {
        return lifecycleRegistry
    }

}
