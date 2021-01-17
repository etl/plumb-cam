package co.nicejourney.plumbcam

import android.app.Application
import android.os.Handler
import android.util.Log
import java.lang.ref.WeakReference

class App : Application() {

    private var monitorHandler: WeakReference<Handler>? = null

    override fun onCreate() {
        super.onCreate()
        instance = this

        if (Config.instance.firstRun || true) {
            val dest = Config.DEFAULT_WWW_PATH + "/admin"
            Util.copyAssetFolder("admin", dest)
            Log.i(LOG_TAG, "First run, copying admin folder to $dest")
        }
    }


    fun setMonitorHandler(h: Handler) {
        monitorHandler = WeakReference(h)
    }

    fun getMonitorHandler(): Handler? {
        return monitorHandler?.get()
    }


    companion object {
        private var instance: App? = null

        fun instance(): App {
            return App.instance!!
        }

        const val CONFIG_CAPTURE_INTERVAL_SECONDS = "CAPTURE_INTERVAL_SECONDS"
        const val CONFIG_PORT = "PORT"
        const val CONFIG_PATH = "PATH"

        const val LOG_TAG = "$$$"
        const val VERSION = "1.0"
    }
}