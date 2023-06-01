package nudt.wifiP2P

import android.app.Application
import android.os.Handler
import android.os.HandlerThread
import nudt.wifiP2P.util.getOrCreate
import nudt.wifiP2P.util.logDir
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

class NewApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Timber.plant(FileLoggingTree(this))
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }

    class FileLoggingTree(private val application: Application) : Timber.DebugTree() {

        private val handler: Handler

        init {
            val thread = HandlerThread("")
            thread.start()
            handler = Handler(thread.looper)
        }

        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            handler.post {
                try {
                    val direct = application.getExternalFilesDir(logDir)?.getOrCreate()
                    val fileNameTimeStamp =
                        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                    val logTimeStamp =
                        SimpleDateFormat("yyyy-MM-dd hh:mm:ss:SSS", Locale.getDefault())
                            .format(Date())
                    val fileName = "$fileNameTimeStamp.log"
                    val file = File(direct, fileName)
                    file.createNewFile()
                    if (file.exists()) {
                        val fileOutputStream: OutputStream = FileOutputStream(file, true)
                        fileOutputStream.write(("$logTimeStamp   $message \n").toByteArray())
                        fileOutputStream.close()
                    }
                    //if (context != null)
                    //MediaScannerConnection.scanFile(context, new String[]{file.getAbsolutePath()}, null, null);
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Error while logging into file : ${e.message}")
                }
            }
        }

        companion object {
            private val TAG = FileLoggingTree::class.java.simpleName
        }
    }
}