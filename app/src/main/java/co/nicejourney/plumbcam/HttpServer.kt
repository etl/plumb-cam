package co.nicejourney.plumbcam

import android.content.Context.WIFI_SERVICE
import android.net.wifi.WifiManager
import android.util.Log
import androidx.work.WorkInfo
import co.nicejourney.plumbcam.App.Companion.LOG_TAG
import fi.iki.elonen.SimpleWebServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.net.InetAddress
import java.util.*


class HttpServer(val port: Int, photoPath: String, adminPath: String) :
    SimpleWebServer("0.0.0.0", port, listOf(File(adminPath), File(photoPath), File(photoPath).parentFile), true, CORS) {

    init {
        try {
            start()

            Log.i(LOG_TAG, "WebServer started on: ${this.hostname}:${this.port}")
            val wm = App.instance().applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
            val ip = InetAddress.getByAddress(wm.connectionInfo.ipAddress.toLong().toBigInteger().toByteArray().reversedArray()).hostAddress
            val ssid = wm.connectionInfo.ssid
            Log.i(LOG_TAG, "Device ip: $ip, $ssid")
        } catch (e: Exception) {
            Log.e(LOG_TAG, "HttpServer $e")
            stop()
        }
    }

    override fun serve(session: IHTTPSession): Response {
        if (session.method == Method.GET) {
            val path = session.uri.split('/').filter { it.isNotBlank() }

            // redirect to admin
            when {
                path.isEmpty() -> {
                    val response = newFixedLengthResponse(Response.Status.REDIRECT, MIME_HTML, "")
                    response.addHeader("Location", "/admin")
                    return response
                }

                path[0] == "api" && path.size >= 2 -> {
                    val response = handle(path[1], session.parameters)
                    if (CORS.isNotBlank()) {
                        return addCORSHeaders(session.headers, response, CORS)
                    }
                }

            }
        }
        return super.serve(session)
//        Log.i(LOG_TAG, session.uri.toString())
//        return newFixedLengthResponse("<b>Hello world</b>")
    }

    private fun handle(cmd: String, params: MutableMap<String, MutableList<String>>): Response {
        when (cmd) {
            "status" -> {
                val ds = DiskStats.build(Config.instance.capturePath)
                return newFixedLengthResponse(
                    Response.Status.OK, MIME_JSON, """
                     { 
                         "result": {
                            "version": "${App.VERSION}", 
                            "time": "${Date()}", 
                            "status": "${CaptureService.status?.state}", 
                            "uptime": ${CaptureService.uptime},
                            "lastCaptureTime": ${CaptureService.lastCaptureTime}, 
                            "freeSpace": "${ds.freeMB} MB",
                            "usedSpace": "${ds.usedMB} MB"
                         }
                     }""".trimIndent()
                )
            }

            "setConfig" -> {
                val c = Config.instance
                var v: String? = null
                return try {
                    if (params.containsKey("port")) c.port = params["port"]!!.first().toInt()
                    if (params.containsKey("captureIntervalSec")) c.captureIntervalSec = params["captureIntervalSec"]!!.first().toInt()
                    if (params.containsKey("capturePath")) {
                        v = params["capturePath"]!!.first()
                        if (!File(v).exists()) throw Exception("Error: $v does not exists")
                        c.capturePath = v
                    }
                    newFixedLengthResponse(Response.Status.OK, MIME_JSON, """ {"result": "ok"} """)
                } catch (err: Exception) {
                    newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_JSON, """ {"error": "$err"} """)
                }
            }

            "getConfig" -> {
                val c = Config.instance
                return newFixedLengthResponse(
                    Response.Status.OK, MIME_JSON, """
                     { 
                         "result": {
                            "inited": "${c.inited}",  
                            "capturePath": "${c.capturePath}", 
                            "port": ${c.port}, 
                            "captureIntervalSec": ${c.captureIntervalSec} 
                         }
                     }""".trimIndent()
                )
            }


            "restart" -> {
                GlobalScope.launch(Dispatchers.IO) {
                    CaptureService.stop()
                    delay(3000)
                    CaptureService.start()
                }
                return newFixedLengthResponse(Response.Status.OK, MIME_JSON, """ {"result": "ok"} """)
            }

            "capture" -> {
                if (CaptureService.status?.state !== WorkInfo.State.RUNNING) {
                    return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_JSON, """ {"error": "Capture Service is not running"} """)
                }
                val filePath = CaptureService.captureOnDemand()
                if (filePath != null) {
                    val f = File(filePath)
                    if (f.exists() && f.isFile) {
                        return newFixedLengthResponse(Response.Status.OK, MIME_JSON, """ {"result": "${f.name}"} """)
                    }
                }
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_JSON, """ {"error": "Bad file returned $filePath"} """)
            }

            else -> {
                return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_JSON, """ {"err": "Unsupported request"}""")
            }
        }
    }

    fun done() {
        this.stop()
        Log.i(LOG_TAG, "WebServer stopped")
    }

    companion object {
        const val MIME_JSON = "application/json"
        const val CORS = "*"
    }

}