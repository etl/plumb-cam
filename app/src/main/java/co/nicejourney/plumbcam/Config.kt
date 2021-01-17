package co.nicejourney.plumbcam

import android.app.Application
import android.content.SharedPreferences
import android.util.Log
import co.nicejourney.plumbcam.App.Companion.LOG_TAG
import java.util.*


class Config {

    var firstRun = true

    var inited: String = ""
        set(value) {
            field = value; sharedPreferences.edit().putString("inited", value).apply()
        }
    var captureIntervalSec: Int = 10
        set(value) {
            field = value; sharedPreferences.edit().putInt("captureIntervalSec", value).apply()
        }
    var port: Int = 8080
        set(value) {
            field = value; sharedPreferences.edit().putInt("port", value).apply()
        }
    var capturePath: String = DEFAULT_WWW_PATH
        set(value) {
            field = value; sharedPreferences.edit().putString("capturePath", value).apply()
        }

    private val sharedPreferences: SharedPreferences = App.instance().getSharedPreferences("config", Application.MODE_PRIVATE)

    init {
        captureIntervalSec = sharedPreferences.getInt("captureIntervalSec", captureIntervalSec)
        port = sharedPreferences.getInt("port", port)
        capturePath = sharedPreferences.getString("capturePath", DEFAULT_CAPTURE_PATH)!!
        inited = sharedPreferences.getString("inited", "")!!
        if (inited.isEmpty()) {
            inited = Date().toString()
        } else {
            firstRun = false
        }
        Log.i(LOG_TAG, sharedPreferences.all.toString())
    }

    companion object {
        val DEFAULT_CAPTURE_PATH = App.instance().getExternalFilesDir("capture").toString()
        val DEFAULT_WWW_PATH = App.instance().getExternalFilesDir("www").toString()
        val instance = Config()
    }
}



