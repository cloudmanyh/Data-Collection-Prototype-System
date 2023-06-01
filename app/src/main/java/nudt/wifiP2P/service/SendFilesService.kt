package nudt.wifiP2P.service

import android.Manifest.permission
import android.annotation.SuppressLint
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.pm.PackageManager
import android.net.wifi.WpsInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.WifiP2pManager.DnsSdServiceResponseListener
import android.net.wifi.p2p.WifiP2pManager.DnsSdTxtRecordListener
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest
import android.os.Binder
import android.os.Environment
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.sample
import nudt.wifiP2P.broadcast.DirectBroadcastReceiver
import nudt.wifiP2P.callback.DirectActionListener
import nudt.wifiP2P.common.Constants
import nudt.wifiP2P.database.AppDatabase
import nudt.wifiP2P.database.dao.SendLogDao
import nudt.wifiP2P.http.HttpClient
import nudt.wifiP2P.util.*
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.UnsupportedEncodingException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URLEncoder
import kotlin.math.pow

@OptIn(FlowPreview::class)
@SuppressLint("LogNotTimber")
class SendFilesService : Service() {
    private var broadcastReceiver: BroadcastReceiver? = null
    private var mWifiP2pDevice: WifiP2pDevice? = null
    private var wifiP2pManager: WifiP2pManager? = null
    private var channel: WifiP2pManager.Channel? = null
    private var wifiP2pInfo: WifiP2pInfo? = null

    private var socket: Socket? = null
    private var dos: DataOutputStream? = null


    private val job = Job()
    private val coroutineScope = CoroutineScope(job + Dispatchers.IO)
    private val discoverServiceFlow = MutableStateFlow(0)

    var logOutputListener: ((String) -> Unit)? = null
    var connectedInfoListener: ((String) -> Unit)? = null
    private var limitedPrimarySendSize: Long = 0
    private var limitedBackupSendSize: Long = 0
    private var currentPrimarySendSize: Long = 0
    private var currentBackupSendSize: Long = 0

    private lateinit var db: AppDatabase
    private lateinit var sendLogDao: SendLogDao

    private val directActionListener: DirectActionListener = object : DirectActionListener {
        override fun wifiP2pEnabled(enabled: Boolean) {
            if (enabled) {
                discoverServiceFlow.value += 1
            }
        }

        override fun onConnectionInfoAvailable(wifiP2pInfo: WifiP2pInfo) {
            onConnected(wifiP2pInfo)
            wifiP2pInfo.groupOwnerAddress?.hostAddress?.let {
                HttpClient.host = it
                coroutineScope.launch {
                    runCatching {
                        HttpClient.canSend(deviceId)
                    }.onSuccess { result ->
                        if (result.primarySize == 0L && result.backupSize == 0L) {
                            Log.d(TAG, "服务端拒绝接收")
                            logOutputListener?.invoke("The server rejects the request")
                            disconnect()
                        } else {
                            limitedPrimarySendSize = result.primarySize
                            limitedBackupSendSize = result.backupSize
                            currentPrimarySendSize = 0
                            currentBackupSendSize = 0

                            if (limitedPrimarySendSize <= 0L && limitedBackupSendSize <= 0L) {
                                Log.d(TAG, "The server rejects the request")
                                logOutputListener?.invoke("The server rejects the request")
                                disconnect()
                                return@onSuccess
                            }

                            reliability = result.reliability
                            receivedFileList = result.receivedFileList
                            logOutputListener?.invoke("The server agrees to receive and can send the $limitedPrimarySendSize primary byte and $limitedBackupSendSize backup byte")
                            openSocket()
                        }
                    }.onFailure { throwable ->
                        Log.e("HttpClient", "请求失败", throwable)
                    }
                }
            }
        }

        override fun onDisconnection() {
            Log.i(TAG, "onDisconnection")
            wifiP2pInfo = null
            logOutputListener?.invoke("The connection is down. Try again")
            connectedInfoListener?.invoke("The connection is down try again\nIP address：$localIPAddress")
            discoverServiceFlow.value += 1
        }

        override fun onSelfDeviceAvailable(wifiP2pDevice: WifiP2pDevice) {
            Log.i(TAG, "onSelfDeviceAvailable")
            Log.i(TAG, "DeviceName: " + wifiP2pDevice.deviceName)
            Log.i(TAG, "DeviceAddress: " + wifiP2pDevice.deviceAddress)
            Log.i(TAG, "Status: " + wifiP2pDevice.status)
        }

        override fun onPeersAvailable(wifiP2pDeviceList: Collection<WifiP2pDevice>) {
            Log.i(TAG, "onPeersAvailable :" + wifiP2pDeviceList.size)
            if (wifiP2pInfo == null) {
                discoverServiceFlow.value += 1
            }
            for (wifiP2pDevice in wifiP2pDeviceList) {
//                Log.i(TAG, "onPeersAvailable ")
//                Log.i(TAG, "DeviceName: " + wifiP2pDevice.deviceName)
//                Log.i(TAG, "DeviceAddress: " + wifiP2pDevice.deviceAddress)
//                Log.i(TAG, "Status: " + wifiP2pDevice.status)
            }
        }

        override fun onChannelDisconnected() {
            Log.i(TAG, "onChannelDisconnected")
            discoverServiceFlow.value += 1
        }
    }
    private var txtListener: DnsSdTxtRecordListener =
        DnsSdTxtRecordListener { _, record, _ ->
            /* Callback includes:
            * fullDomain: full domain name: e.g "printer._ipp._tcp.local."
            * record: TXT record dta as a map of key/value pairs.
            * device: The device running the advertised service.
            */  Log.d(TAG, "DnsSdTxtRecord available -$record")
        }
    private var servListener = DnsSdServiceResponseListener { instanceName, _, srcDevice ->
        if (instanceName == Constants.INSTANCE_NAME && wifiP2pInfo == null) {
            connect(srcDevice)
        }
    }

    override fun onCreate() {
        super.onCreate()
        initDB()

        coroutineScope.launch {
            discoverServiceFlow.sample(30000).collectLatest {
                discoverServices()
            }
        }
    }

    private fun initDB() {
        db = AppDatabaseUtils.getInstance(applicationContext)
        sendLogDao = db.sendLogDao()
    }

    fun startWifiManager() {
        wifiP2pManager = getSystemService(WIFI_P2P_SERVICE) as WifiP2pManager
        if (wifiP2pManager == null) {
            Log.e(TAG, "wifiP2pManager == null")
            return
        }
        channel = wifiP2pManager?.initialize(this, mainLooper, directActionListener)
        wifiP2pManager?.setDnsSdResponseListeners(channel, servListener, txtListener)
        wifiP2pManager?.clearServiceRequests(
            channel,
            object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.i(TAG, "clearServiceRequests onSuccess")
                }

                override fun onFailure(code: Int) {
                    Log.e(TAG, "clearServiceRequests onFailure:$code")
                }
            })
        val serviceRequest = WifiP2pDnsSdServiceRequest.newInstance()
        wifiP2pManager?.addServiceRequest(
            channel,
            serviceRequest,
            object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.i(TAG, "addServiceRequest onSuccess")
                }

                override fun onFailure(code: Int) {
                    Log.e(TAG, "addServiceRequest onFailure:$code")
                }
            })
        broadcastReceiver = DirectBroadcastReceiver(
            wifiP2pManager!!, channel,
            directActionListener
        )
        registerReceiver(broadcastReceiver, DirectBroadcastReceiver.intentFilter)
        wifiP2pManager?.requestConnectionInfo(channel) { info ->
            if (info != null && info.groupFormed) {
                onConnected(info)
            } else {
                connectedInfoListener?.invoke(
                    "Connection status：Offline\nIP address：$localIPAddress"
                )
                discoverServiceFlow.value += 1
            }
        }
    }

    private fun onConnected(wifiP2pInfo: WifiP2pInfo) {
        val stringBuilder = StringBuilder()
        if (mWifiP2pDevice != null) {
            stringBuilder.append("Device：")
            stringBuilder.append(mWifiP2pDevice!!.deviceName)
            stringBuilder.append("\n")
            stringBuilder.append("Device Address：")
            stringBuilder.append(mWifiP2pDevice!!.deviceAddress)
        }
//        stringBuilder.append("\n")
//        stringBuilder.append("是否群主：")
//        stringBuilder.append(if (wifiP2pInfo.isGroupOwner) "是群主" else "非群主")
//        stringBuilder.append("\n")
//        stringBuilder.append("群主IP地址：")
//        stringBuilder.append(wifiP2pInfo.groupOwnerAddress?.hostAddress)
        Log.i(TAG, stringBuilder.toString())
        connectedInfoListener?.invoke(
            "$stringBuilder\nIP address：$localIPAddress"
        )
        if (wifiP2pInfo.groupFormed && !wifiP2pInfo.isGroupOwner) {
            this@SendFilesService.wifiP2pInfo = wifiP2pInfo
        }
    }

    private fun discoverServices() {
        if (ActivityCompat.checkSelfPermission(this, permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "无权限")
            return
        }
        if (wifiP2pInfo != null) {
            return
        }
        logOutputListener?.invoke("Search service")
        wifiP2pManager?.discoverServices(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.i(TAG, "discoverServices onSuccess")
                logOutputListener?.invoke("Search service completion")
                discoverServiceFlow.value += 1
            }

            override fun onFailure(code: Int) {
                Log.e(TAG, "discoverServices onFailure:$code")
                logOutputListener?.invoke("Search service failed, failure code:$code")
                discoverServiceFlow.value += 1
            }
        })
    }

    private fun connect(device: WifiP2pDevice) {
        mWifiP2pDevice = device
        val config = WifiP2pConfig()
        if (config.deviceAddress != null && mWifiP2pDevice != null) {
            config.deviceAddress = mWifiP2pDevice!!.deviceAddress
            config.wps.setup = WpsInfo.PBC
            if (ActivityCompat.checkSelfPermission(this, permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
            ) {
                Log.i(TAG, "没有权限")
                return
            }
            Log.i(TAG, "尝试连接 设备名为:" + mWifiP2pDevice?.deviceName)
            wifiP2pManager?.connect(channel, config, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.i(TAG, "connect onSuccess")
                }

                override fun onFailure(reason: Int) {
                    Log.i(TAG, "connect onFailure: $reason")
                    mWifiP2pDevice = null
                    discoverServiceFlow.value += 1
                }
            })
        }
    }

    private fun disconnect() {
        Log.d(TAG, "断开连接")
        wifiP2pManager?.removeGroup(
            channel,
            object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.i(TAG, "删除群组 成功")
                    logOutputListener?.invoke("Deleting the group succeeded")
                    wifiP2pInfo = null
                    mWifiP2pDevice = null
                    discoverServiceFlow.value += 1
                }

                override fun onFailure(reason: Int) {
                    if (wifiP2pInfo == null) {
                        return
                    }
                    Log.i(TAG, "删除群组 失败: $reason")
                    logOutputListener?.invoke("Group deletion failed")
                    coroutineScope.launch {
                        delay(30000)
                        disconnect()
                    }
                }

            })
    }

    private fun openSocket() {
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    socket = Socket()
                    socket!!.bind(null)
                    socket!!.connect(
                        InetSocketAddress(
                            wifiP2pInfo!!.groupOwnerAddress.hostAddress,
                            Constants.FILE_PORT
                        ), 10000
                    )
                    dos = DataOutputStream(socket!!.getOutputStream())

                    sendHead()
                    sendBackupFiles()
                    sendPrimaryFiles()
                }.onFailure {
                    Log.e(this.javaClass.simpleName, "socket打开失败", it)
                    disconnect()
                }
            }
        }
    }

    private suspend fun sendHead() {
        if (wifiP2pInfo == null) {
            logOutputListener?.invoke("The connection has been interrupted and $currentPrimarySendSize bytes have been sent")
            return
        }
        withContext(Dispatchers.IO) {

            val dir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)?.getOrCreate()
            val backupDir = getExternalFilesDir(backup)?.getOrCreate()
            if (dir?.listFiles().isNullOrEmpty() && backupDir?.listFiles().isNullOrEmpty()) {
                return@withContext
            }
            runCatching {

                if (dos == null) {
                    logOutputListener?.invoke("The connection has been interrupted and $currentPrimarySendSize bytes have been sent")
                    return@withContext
                }

                dos?.writeInt(deviceId)
                dos?.writeLong(dir.totalSize)
                dos?.writeLong(backupDir.totalSize)
            }.onFailure {
                Log.e(this.javaClass.simpleName, "头部发送失败", it)
                logOutputListener?.invoke("Header failed")
                disconnect()
            }
        }
    }

    private suspend fun sendBackupFiles() {
        if (wifiP2pInfo == null) {
            logOutputListener?.invoke("The connection has been interrupted and $currentBackupSendSize bytes have been sent to the backup folder")
            return
        }

        if (dos == null) {
            logOutputListener?.invoke("The connection has been interrupted and $currentBackupSendSize bytes have been sent to the backup folder")
            return
        }

        runCatching {

            val backupDir = getExternalFilesDir(backup)?.getOrCreate()
            //删除已接收的文件
            backupDir?.listFiles()
                ?.filter { receivedFileList.contains(it.name.substring("backup_".length)) }
                ?.forEach { it.delete() }
            if (backupDir?.listFiles().isNullOrEmpty()) {
                dos?.writeInt(0)
                return
            }
            val backupFiles =
                backupDir?.takeLimitedSizeFiles(limitedBackupSendSize - currentBackupSendSize)
            dos?.writeInt(backupFiles?.size ?: 0)
            if (backupFiles != null) {
                for (file in backupFiles) {
                    if (wifiP2pInfo == null) {
                        logOutputListener?.invoke("The connection has been interrupted and $currentBackupSendSize bytes have been sent to the backup folder")
                        break
                    }
                    sendFile(dos, file, true)
                }
                logOutputListener?.invoke("$currentBackupSendSize bytes are sent to the backup folder")
                limitedBackupSendSize = 0
                currentBackupSendSize = 0
            }
        }.onFailure {
            Log.e(this.javaClass.simpleName, "Failed to send the backup folder. Procedure", it)
            logOutputListener?.invoke("Failed to send the backup folder. Procedure")
            disconnect()
        }
    }

    private suspend fun sendPrimaryFiles() {
        if (wifiP2pInfo == null) {
            logOutputListener?.invoke("The connection has been interrupted and $currentPrimarySendSize bytes have been sent")
            return
        }

        val primaryDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)?.getOrCreate()
        val ashcanDir = getExternalFilesDir(ashcan)?.getOrCreate()
        val backupDir = getExternalFilesDir(backup)?.getOrCreate()
        if (primaryDir?.listFiles().isNullOrEmpty()) {
            logOutputListener?.invoke("The amount of data in the primary folder is insufficient. $currentPrimarySendSize bytes are sent. The sending starts again 5 seconds later")
            delay(5000)
            disconnect()
            return
        }

        runCatching {

            if (dos == null) {
                logOutputListener?.invoke("The connection has been interrupted and $currentPrimarySendSize bytes have been sent")
                return
            }

            val primaryFiles =
                primaryDir?.takeLimitedSizeFiles(limitedPrimarySendSize - currentPrimarySendSize)
            primaryFiles?.forEach { Log.d(this.javaClass.simpleName, "预备发送文件名：${it.name}") }
            logOutputListener?.invoke("The amount of data to be sent to the primary folder is ${primaryFiles?.sumOf { it.length() }}")
            dos?.writeInt(primaryFiles?.size ?: 0)
            if (primaryFiles != null) {
                for (file in primaryFiles) {
                    if (wifiP2pInfo == null) {
                        logOutputListener?.invoke("The connection has been interrupted and $currentPrimarySendSize bytes have been sent")
                        break
                    }
                    sendFile(dos, file, false)
                }
            }
            val dirSizeMessage = String.format(
                "Size--%020d--%020d--%020d",
                primaryDir.totalSize,
                ashcanDir?.totalSize ?: 0L,
                backupDir?.totalSize ?: 0L
            )
            dos?.write(
                dirSizeMessage.toByteArray(),
                0,
                dirSizeMessage.toByteArray().size
            )
            dos?.flush()

            if (currentPrimarySendSize >= limitedPrimarySendSize) {
                socket?.shutdownOutput()
                socket?.getInputStream().use {
                    while (it?.read() == -1) {
                        socket?.shutdownInput()
                    }
                }
                dos?.close()
                socket?.close()
                dos = null
                socket = null

                Log.i(TAG, "发送数据已达上限，停止发送")
                logOutputListener?.invoke("The data sent has reached the upper limit. Procedure")
                logOutputListener?.invoke("The $currentPrimarySendSize byte has been sent")
                logOutputListener?.invoke("The $currentPrimarySendSize byte has been sent to the main folder")
                disconnect()
                discoverServiceFlow.emit(discoverServiceFlow.value + 1)
            } else {
                logOutputListener?.invoke("The amount of data in the primary folder is insufficient. $currentPrimarySendSize bytes are sent. The sending starts again 5 seconds later")
                delay(5000)
                disconnect()
            }
        }.onFailure {
            Log.e(this.javaClass.simpleName, "主文件夹发送失败", it)
            logOutputListener?.invoke("Failed to send the main folder")
            disconnect()
        }
    }

    private suspend fun sendFile(dos: DataOutputStream?, file: File, isBackup: Boolean) {
        runCatching {
            FileInputStream(file).use { inputStream ->
                val fileSize = file.length()
                var encodeFileName = file.name
                try {
                    encodeFileName = URLEncoder.encode(file.name, "utf-8")
                } catch (e: UnsupportedEncodingException) {
                    e.printStackTrace()
                }
                val fileMessage =
                    String.format("Start--%-128s--%020d", encodeFileName, fileSize)
                dos?.write(fileMessage.toByteArray(), 0, fileMessage.toByteArray().size)
                dos?.flush()
                var total: Long = 0
                val buf = ByteArray(8192)
                var len: Int
                while (inputStream.read(buf).also { len = it } != -1) {
                    dos?.write(buf, 0, len)
                    total += len.toLong()
//                    val progress = (total * 100 / fileSize).toInt()
//                    Log.i(TAG, "文件名：${file.name}, 发送进度：$progress")
                }
                dos?.flush()
                Log.d(TAG, "文件发送成功，文件的MD5码是：${Md5Util.getMd5(file)}")
//                logOutputListener?.invoke("文件发送成功，文件名：${file.name}")

                //发送记录插入数据库
                sendLogDao.insertAll(getSendLogFromFileName(file.name))

                if (!isBackup) {
                    backup(file)
                }
                val delete = file.delete()
                Log.i(TAG, if (delete) "文件删除成功" else "文件删除失败")
                if (isBackup) {
                    currentBackupSendSize += fileSize
                } else {
                    currentPrimarySendSize += fileSize
                }
            }
        }.onFailure {
            Log.e(
                this.javaClass.simpleName,
                "${if (isBackup) "备份" else "主"} 文件 ${file.name} 发送失败",
                it
            )
            logOutputListener?.invoke("${if (isBackup) "backup" else "primary"} file ${file.name} send failure")
            disconnect()
        }
    }

    private suspend fun backup(file: File) {
        withContext(Dispatchers.IO) {
            runCatching {
                val needBackup = needBackup(file)
                val name = file.name
//                Timber.i("$name 已发送，${if (needBackup) "已备份" else "无需备份"}")
//                logOutputListener?.invoke("$name 已发送，${if (needBackup) "已备份" else "无需备份"}")
                if (needBackup) {
                    val dir = getExternalFilesDir(backup)?.getOrCreate()

                    val newName = "backup_$name"
                    val backupFile = File("$dir/${newName}")
                    file.copyTo(backupFile, true)
                }
            }
        }
    }

    private suspend fun needBackup(file: File): Boolean {
        return withContext(Dispatchers.IO) {
            runCatching {
                val mainDirLength =
                    getExternalFilesDir(Environment.DIRECTORY_PICTURES)?.getOrCreate()?.totalSize
                        ?: 0
                val backupDirLength = getExternalFilesDir(backup)?.getOrCreate()?.totalSize ?: 0

                val split = file.name.split("_")
                val size = split[2].substringBefore("K").toDouble()
                val value = split[3].split(".")[0].toDouble()
                val occupancyRate =
                    (mainDirLength + backupDirLength - file.totalSize).toDouble() / limitedGenerateSize.toDouble()

                val pow = occupancyRate.pow(3)

                return@runCatching (value / size) > (pow / ((1 - pow) * (1 - reliability) * (1 + penalty)))
            }.getOrNull() ?: false
        }
    }

    override fun onDestroy() {
        logOutputListener?.invoke("The file sending service is closed")
        try {
            unregisterReceiver(broadcastReceiver)
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
        }
        job.cancel()
        coroutineScope.cancel()
        disconnect()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder {
        return MyBinder()
    }

    inner class MyBinder : Binder() {
        val service: SendFilesService
            get() = this@SendFilesService
    }

    companion object {
        private const val TAG = "SendFilesService"
    }
}