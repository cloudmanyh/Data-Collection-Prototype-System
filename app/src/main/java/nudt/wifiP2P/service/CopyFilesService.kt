package nudt.wifiP2P.service

import android.app.Service
import android.content.ComponentName
import android.content.ContentUris
import android.content.Intent
import android.content.ServiceConnection
import android.database.Cursor
import android.os.Binder
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore.Images
import android.provider.MediaStore.MediaColumns
import android.util.Log
import kotlinx.coroutines.*
import nudt.wifiP2P.util.*
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.*
import kotlin.math.absoluteValue
import kotlin.math.sqrt

class CopyFilesService : Service() {

    private val job = Job()
    private val coroutineScope = CoroutineScope(job + Dispatchers.IO)

    private var quickGenerateSize = 0L
    private var generateSize = 0L
    private val random = Random()
    private var primarySize: Long = 0

    private val httpServiceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val binder = service as HttpService.MyBinder
            val httpService = binder.service
            httpService.onSetQuickGenerateSizeListener = {
                quickGenerateSize = it
                generateSize = 0L
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {

        }
    }

    override fun onCreate() {
        super.onCreate()
        val intent = Intent(this, HttpService::class.java)
        bindService(intent, httpServiceConnection, BIND_AUTO_CREATE)
        coroutineScope.launch {
            while (isActive) {
                copyFiles()
            }
        }
        primarySize = getExternalFilesDir(Environment.DIRECTORY_PICTURES)?.getOrCreate().totalSize
        coroutineScope.launch {
            while (isActive) {
                val primary =
                    getExternalFilesDir(Environment.DIRECTORY_PICTURES)?.getOrCreate().totalSize
                val backup = getExternalFilesDir(backup)?.getOrCreate().totalSize
                val ashcan = getExternalFilesDir(ashcan)?.getOrCreate().totalSize
                val generateSpeed = (primary - primarySize) / 30
                val totalSize = primary + backup
                primarySize = primary
                Timber.i("$deviceId,$generateSpeed,$primary,$backup,$totalSize,$limitedGenerateSize,${totalSize / limitedGenerateSize},$ashcan,${System.currentTimeMillis()}")
                delay(30000)
            }
        }
    }

    private suspend fun copyFiles() {
        val destinationDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)?.getOrCreate()
        val backupDir = getExternalFilesDir(backup)?.getOrCreate()
        val ashcanDir = getExternalFilesDir(ashcan)?.getOrCreate()
        val projection = arrayOf(
            MediaColumns._ID,
            MediaColumns.DISPLAY_NAME,
            MediaColumns.SIZE
        )
        runCatching {
            applicationContext.contentResolver.query(
                Images.Media.EXTERNAL_CONTENT_URI, projection, null, null, null
            ).use { cursor ->
                if (cursor!!.count > 0) {
                    val idColumn = cursor.getColumnIndexOrThrow(Images.Media._ID)
                    val nameColumn = cursor.getColumnIndexOrThrow(Images.Media.DISPLAY_NAME)
                    val sizeColumn = cursor.getColumnIndexOrThrow(Images.Media.SIZE)
                    val position = generationPosition(cursor)
                    cursor.moveToPosition(position)
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn)
                    val size = cursor.getInt(sizeColumn)
                    val contentUri = ContentUris.withAppendedId(
                        Images.Media.EXTERNAL_CONTENT_URI, id
                    )
                    val parcelFileDescriptor =
                        contentResolver.openFileDescriptor(contentUri, "r")
                    FileInputStream(parcelFileDescriptor!!.fileDescriptor).use { inputStream ->
                        val dir =
                            if ((destinationDir.totalSize + backupDir.totalSize) > limitedGenerateSize) {
                                Log.d(TAG, "保存文件夹已达上限，数据丢弃至垃圾桶文件夹")
                                ashcanDir
                            } else {
                                destinationDir
                            }
                        val lastIndex = name.lastIndexOf(".")
                        val absoluteValue = (sqrt(0.5) * random.nextGaussian() + 1).absoluteValue
                        val value = (size / 1024) * absoluteValue
                        val indexName =
                            "${deviceId}_${generateIndex}_${size / 1024}_${value.toInt()}_${System.currentTimeMillis()}"
                        val newName = if (lastIndex < 0) indexName else name.replaceRange(
                            0,
                            lastIndex,
                            "$indexName"
                        )
                        val file = File("$dir/$newName")
                        FileOutputStream(file).use { outputStream ->
                            inputStream.copyTo(outputStream)
                            outputStream.flush()
                        }
                        Log.d(TAG, "文件复制成功，文件名为 $newName , 大小为 $size")
                    }
                    generateIndex++
                    if (quickGenerateSize != 0L) {
                        generateSize += size
                        if (generateSize > quickGenerateSize) {
                            quickGenerateSize = 0
                            generateSize = 0
                        }
                    }
                }
            }
        }.onFailure {
            Log.e(TAG, "文件复制失败 ", it)
        }
        if (quickGenerateSize == 0L) {
            val sample = poisson.sample()
            val sleepTime = sample * 1000
            Log.d(TAG, "休眠 $sleepTime 毫秒")
            delay(sleepTime.toLong())
        }
    }

    private fun generationPosition(cursor: Cursor): Int {
        val count = cursor.count
        val position = Random().nextInt(count)
        Log.d(TAG, "随机复制位置已生成，位置为 $position")
        cursor.moveToPosition(position)
        val sizeColumn = cursor.getColumnIndexOrThrow(Images.Media.SIZE)
        val size = cursor.getInt(sizeColumn)
        return if (size < 20971520) {
            position
        } else {
            generationPosition(cursor)
        }
    }

    override fun onDestroy() {
        job.cancel()
        coroutineScope.cancel()
        unbindService(httpServiceConnection)
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder {
        return MyBinder()
    }

    inner class MyBinder : Binder() {
        val service: CopyFilesService
            get() = this@CopyFilesService
    }

    companion object {
        private const val TAG = "CopyFilesService"
    }
}