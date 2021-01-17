package co.nicejourney.plumbcam

import android.os.StatFs
import android.util.Log
import co.nicejourney.plumbcam.App.Companion.LOG_TAG
import java.io.File
import java.io.File.separator
import java.io.FileOutputStream
import java.io.OutputStream

class Util {

    companion object {

        fun copyAssetFolder(srcName: String, dstName: String) {
            val assets = App.instance().assets
            val fileList = assets.list(srcName) ?: return
            if (fileList.isEmpty()) {
                copyAssetFile(srcName, dstName)
                return
            } else {
                val file = File(dstName)
                file.mkdirs()
                for (filename in fileList) {
                    copyAssetFolder(
                        srcName + separator.toString() + filename, dstName + separator.toString() + filename
                    )
                }
            }
        }

        fun copyAssetFile(srcName: String, dstName: String) {
            val assets = App.instance().assets
            val srcFile = assets.open(srcName)
            val outFile = File(dstName)
            val out: OutputStream = FileOutputStream(outFile)
            val buffer = ByteArray(1024)
            var read: Int
            while (srcFile.read(buffer).also { read = it } != -1) {
                out.write(buffer, 0, read)
            }
            srcFile.close()
            out.close()
            Log.d(LOG_TAG, "cp: $srcName -> $dstName")
        }

    }
}


data class DiskStats(val path: String, val total: Long, val used: Long, val free: Long) {

    val usedMB: Long get() = used / 1024 / 1024
    val freeMB: Long get() = free / 1024 / 1024

    companion object {
        fun build(path: String): DiskStats {
            val stat = StatFs(path)
            val total = stat.blockCountLong * stat.blockSizeLong
            val free = stat.freeBlocksLong * stat.blockSizeLong
            return DiskStats(
                path,
                total,
                total - free,
                free
            )
        }
    }
}
