package nudt.wifiP2P.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.pm.PackageManager
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo
import android.os.Binder
import android.os.Environment
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.toList
import nudt.wifiP2P.bean.DeviceBean
import nudt.wifiP2P.broadcast.DirectBroadcastReceiver
import nudt.wifiP2P.callback.DirectActionListener
import nudt.wifiP2P.common.Constants
import nudt.wifiP2P.util.*
import java.io.DataInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.UnsupportedEncodingException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.ConcurrentHashMap

class ReceiveFileService : Service() {

    private var wifiP2pManager: WifiP2pManager? = null
    private var channel: WifiP2pManager.Channel? = null
    private var broadcastReceiver: BroadcastReceiver? = null
    private var serverSocket: ServerSocket? = null

    var logOutputListener: ((String) -> Unit)? = null
    var connectedInfoListener: ((String) -> Unit)? = null
    var deviceInfoListener: ((DeviceBean?) -> Unit)? = null

    private val job = Job()
    private val coroutineScope = CoroutineScope(job + Dispatchers.IO)

    private var startReceiveTimeMap: ConcurrentHashMap<Int, Long> = ConcurrentHashMap()
    private var primarySizeBeforeSendMap: ConcurrentHashMap<Int, Long> = ConcurrentHashMap()
    private var deviceBeanMap: ConcurrentHashMap<Int, DeviceBean?> = ConcurrentHashMap()

    private val directActionListener: DirectActionListener = object : DirectActionListener {
        override fun wifiP2pEnabled(enabled: Boolean) {
            logOutputListener?.invoke("wifiP2pEnabled: $enabled")
        }

        override fun onConnectionInfoAvailable(wifiP2pInfo: WifiP2pInfo) {
            onConnected(wifiP2pInfo)
        }

        override fun onDisconnection() {
            Log.i(TAG, "onDisconnection")
        }

        override fun onSelfDeviceAvailable(wifiP2pDevice: WifiP2pDevice) {
            Log.i(TAG, "onSelfDeviceAvailable")
            Log.i(TAG, "DeviceName: " + wifiP2pDevice.deviceName)
            Log.i(TAG, "DeviceAddress: " + wifiP2pDevice.deviceAddress)
            Log.i(TAG, "Status: " + wifiP2pDevice.status)
        }

        override fun onPeersAvailable(wifiP2pDeviceList: Collection<WifiP2pDevice>) {
            Log.i(TAG, "onPeersAvailable :" + wifiP2pDeviceList.size)
            for (wifiP2pDevice in wifiP2pDeviceList) {
//                Log.i(TAG, "onPeersAvailable ")
//                Log.i(TAG, "DeviceName: " + wifiP2pDevice.deviceName)
//                Log.i(TAG, "DeviceAddress: " + wifiP2pDevice.deviceAddress)
//                Log.i(TAG, "Status: " + wifiP2pDevice.status)
            }
        }

        override fun onChannelDisconnected() {
            Log.i(TAG, "onChannelDisconnected")
        }
    }

    override fun onCreate() {
        super.onCreate()
        initWifiManager()
    }

    private fun initWifiManager(): Boolean {
        wifiP2pManager = getSystemService(WIFI_P2P_SERVICE) as WifiP2pManager
        if (wifiP2pManager == null) {
            return true
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "没有权限")
            return true
        }
        channel = wifiP2pManager?.initialize(this, mainLooper, directActionListener)
        broadcastReceiver = DirectBroadcastReceiver(
            wifiP2pManager!!, channel,
            directActionListener
        )
        registerReceiver(broadcastReceiver, DirectBroadcastReceiver.intentFilter)
        wifiP2pManager!!.requestConnectionInfo(channel) { info ->
            if (info != null && info.groupFormed) {
                removeGroup()
                createGroup()
            } else {
                connectedInfoListener?.invoke("Connection status：Offline")
                createGroup()
            }
        }
        return false
    }

    private fun createGroup() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val record: MutableMap<String, String> = HashMap()
        record["listenport"] = Constants.FILE_PORT.toString()
        record["available"] = "visible"
        val serviceInfo =
            WifiP2pDnsSdServiceInfo.newInstance(Constants.INSTANCE_NAME, "_presence._tcp", record)
        wifiP2pManager?.addLocalService(
            channel,
            serviceInfo,
            object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.i(TAG, "addLocalService onSuccess")
                }

                override fun onFailure(arg0: Int) {
                    Log.e(TAG, "addLocalService onFailure:$arg0")
                }
            })
        wifiP2pManager?.createGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                logOutputListener?.invoke("createGroup onSuccess")
            }

            override fun onFailure(reason: Int) {
                logOutputListener?.invoke("createGroup onFailure: $reason")
            }
        })
    }

    private fun removeGroup() {
        wifiP2pManager?.clearLocalServices(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.i(TAG, "clearLocalServices onSuccess")
            }

            override fun onFailure(reason: Int) {
                Log.e(TAG, "clearLocalServices onFailure:$reason")
            }
        })
        wifiP2pManager?.removeGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                logOutputListener?.invoke("removeGroup onSuccess")
            }

            override fun onFailure(reason: Int) {
                logOutputListener?.invoke("removeGroup onFailure")
            }
        })
    }

    @SuppressLint("MissingPermission")
    private fun onConnected(wifiP2pInfo: WifiP2pInfo) {
        if (wifiP2pInfo.groupFormed) {
            val stringBuilder = StringBuilder()
            stringBuilder.append("Connection status：Online\n")
//            stringBuilder.append("是否群主：")
//            stringBuilder.append(if (wifiP2pInfo.isGroupOwner) "是群主" else "非群主")
            stringBuilder.append("\n")
            stringBuilder.append("IP address：$localIPAddress")
//            stringBuilder.append("\n")
//            stringBuilder.append("群主IP地址：")
//            stringBuilder.append(wifiP2pInfo.groupOwnerAddress.hostAddress)
            if (wifiP2pInfo.isGroupOwner) {
                wifiP2pManager?.requestGroupInfo(
                    channel,
                    WifiP2pManager.GroupInfoListener { group ->
                        if (group == null) {
                            return@GroupInfoListener
                        }
                        val size = group.clientList.size
                        stringBuilder.append("\nConnected device number：$size")
                        connectedInfoListener?.invoke(stringBuilder.toString())
                        startReceive()
                    })
            } else {
                connectedInfoListener?.invoke(stringBuilder.toString())
            }
        } else {
            connectedInfoListener?.invoke("Connection status：Offline")
        }
    }

    private fun startReceive() {
        if (serverSocket != null) {
            return
        }
        logOutputListener?.invoke("The file receiving service has been started")
        coroutineScope.launch {
            runCatching {
                val externalFilesDir =
                    getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.getOrCreate()
                val planDir = externalFilesDir?.listFiles()
                    ?.onEach { Log.d(this.javaClass.simpleName, "下载目录名：${it.name}") }?.maxOrNull()
                    ?.also { Log.d(this.javaClass.simpleName, "使用目录名：${it.name}") }
                serverSocket = ServerSocket()
                serverSocket?.use { socket ->
                    socket.reuseAddress = true
                    socket.bind(InetSocketAddress(Constants.FILE_PORT))
                    flow {
                        while (true) {
                            val client = socket.accept()
                            emit(async { receiveFile(client, planDir) })
                        }
                    }.flowOn(Dispatchers.IO).toList().awaitAll()
                }
            }.onFailure {
                if (isActive) {
                    Log.e(TAG, "socket Exception: " + it.message, it)
                    logOutputListener?.invoke("socket error")
                }
            }

            serverSocket = null
            if (isActive) {
                logOutputListener?.invoke("The file receiving service is being restarted")
                startReceive()
            }
        }
    }

    private suspend fun receiveFile(
        client: Socket,
        planDir: File?
    ) {
        Log.d(TAG, "客户端IP地址 : " + client.inetAddress.hostAddress)
        logOutputListener?.invoke("File receiving services received connection, the client IP for ${client.inetAddress.hostAddress}")

        runCatching {
            DataInputStream(client.getInputStream()).use { dis ->

                val id = dis.readInt()
                val clientDir = File(planDir, id.toString()).getOrCreate()
                val primarySize = dis.readLong()
                val backupSize = dis.readLong()
                logOutputListener?.invoke("Receive client ID: the ID of the message, the main folder can send size for: ${primarySize.getFormatSize()}, the backup folder can send size for: ${backupSize.getFormatSize()}")
//                Timber.i("收到客户端ID：$id 的消息，主文件夹可发送大小为：${primarySize.getFormatSize()}，备份文件夹可发送大小为：${backupSize.getFormatSize()}")

                val receivedFileList = currentReceivedFileMap.getOrDefault(id, mutableListOf())

                if (startReceiveTimeMap[id] == null || startReceiveTimeMap[id] == 0L) {
                    startReceiveTimeMap[id] = System.currentTimeMillis()
                    printReceiveLog(
                        clientDir,
                        "${startReceiveTimeMap[id]?.getFormatTime()}:  The client [$id] task starts"
                    )
                    primarySizeBeforeSendMap[id] = primarySize
                }
                deviceBeanMap[id] =
                    deviceBeanMap[id]?.copy(
                        online = true,
                        limitedPrimarySize = limitedSendPrimaryFileSizeMap[id] ?: 0L,
                        limitedBackupSize = limitedSendBackupFileSizeMap[id] ?: 0L,
                        receivePrimarySize = realReceivePrimaryFileSizeMap[id] ?: 0L,
                        receiveBackupSize = realReceiveBackupFileSizeMap[id] ?: 0L,
                    ) ?: DeviceBean(
                        id = id,
                        online = true,
                        startTime = startReceiveTimeMap[id] ?: 0,
                        limitedPrimarySize = limitedSendPrimaryFileSizeMap[id] ?: 0L,
                        limitedBackupSize = limitedSendBackupFileSizeMap[id] ?: 0L,
                        receivePrimarySize = realReceivePrimaryFileSizeMap[id] ?: 0L,
                        receiveBackupSize = realReceiveBackupFileSizeMap[id] ?: 0L,
                    )
                deviceInfoListener?.invoke(deviceBeanMap[id])

                val backupCount = dis.readInt()
                for (i in 0 until backupCount) {
                    saveFile(dis, clientDir, id, realReceiveBackupFileSizeMap, receivedFileList)
                }
                remainingSendBackupFileSizeMap[id] = 0

                val primaryCount = dis.readInt()
                for (i in 0 until primaryCount) {
                    saveFile(dis, clientDir, id, realReceivePrimaryFileSizeMap, receivedFileList)
                }
                //                        while (dis.available() > 0) {
                //                            val clientDir = File(planDir, id.toString()).getOrCreate()
                //                            saveFile(dis, clientDir, id, realReceivePrimaryFileSizeMap)
                //                        }


                val fileMessageByte = ByteArray(70)
                dis.read(fileMessageByte, 0, fileMessageByte.size)

                //parse文件头信息，得到剩余文件夹大小
                val fileMessage = String(fileMessageByte)
                Log.d(TAG, "文件尾为：$fileMessage")
                val remainingLength =
                    fileMessage.split("--").toTypedArray()[1].toLong()
                val ashcanLength =
                    fileMessage.split("--").toTypedArray()[2].toLong()
                val backupLength =
                    fileMessage.split("--").toTypedArray()[3].toLong()
                logOutputListener?.invoke("The client ID: $id, the client can send remaining size is: {remainingLength.getFormatSize ()}")
//                Timber.i("客户端ID：$id ，客户端剩余可发送大小为：${remainingLength.getFormatSize()} , 已丢弃大小为：${ashcanLength.getFormatSize()}")
                client.shutdownInput()
                client.getOutputStream().use {
                    it.write("end".toByteArray())
                    client.shutdownOutput()
                }
                currentReceivedFileMap[id] = receivedFileList

                val receivePrimarySize =
                    realReceivePrimaryFileSizeMap.getOrDefault(id, 0L)
                val receiveBackupSize =
                    realReceiveBackupFileSizeMap.getOrDefault(id, 0L)
                if (receivePrimarySize > limitedSendPrimaryFileSizeMap.getOrDefault(id, 0L)) {
                    logOutputListener?.invoke("Received Size is: $receivePrimarySize")
                    val endTime = System.currentTimeMillis()
                    val speed =
                        ((receivePrimarySize + receiveBackupSize) / ((endTime + 1 - (startReceiveTimeMap[id]
                            ?: 0L)) / 1000.0))
                    logOutputListener?.invoke("Client ID: $id. The transmission rate of the client is ${speed.getFormatSize()}/s")
//                    Timber.i("客户端ID：$id ，客户端传输速率为：${speed.getFormatSize()}/s")
                    val generateSize =
                        remainingLength + receivePrimarySize - (primarySizeBeforeSendMap[id] ?: 0)
                    Log.i(
                        this.javaClass.simpleName,
                        "剩余大小：$remainingLength ，收到大小：$receivePrimarySize , 之前大小：${primarySizeBeforeSendMap[id]} , 生成大小：$generateSize"
                    )
                    printReceiveLog(
                        clientDir,
                        "id： $id , endTime: $endTime, startTime: ${startReceiveTimeMap[id]?.getFormatTime()}"
                    )
                    val generateSpeed =
                        generateSize / ((endTime - (startReceiveTimeMap[id] ?: 0L)) / 1000.0)
                    deviceBeanMap[id] = deviceBeanMap[id]?.copy(
                        online = false,
                        time = endTime - (deviceBeanMap[id]?.startTime ?: 0),
                        endTime = endTime,
                        limitedPrimarySize = limitedSendPrimaryFileSizeMap[id] ?: 0L,
                        limitedBackupSize = limitedSendBackupFileSizeMap[id] ?: 0L,
                        receivePrimarySize = realReceivePrimaryFileSizeMap[id] ?: 0L,
                        receiveBackupSize = realReceiveBackupFileSizeMap[id] ?: 0L,
                        sendSpeed = speed.toLong(),
                        generateSpeed = generateSpeed.toLong(),
                        totalSize = remainingLength + backupLength,
                        remainSize = remainingLength,
                        backupSize = backupLength
                    )
                    deviceInfoListener?.invoke(deviceBeanMap[id])
                    startReceiveTimeMap[id] = 0L
                    printReceiveLog(
                        clientDir,
                        "id： $id ,  startTime清空"
                    )
                    printReceiveLog(
                        clientDir,
                        "${System.currentTimeMillis().getFormatTime()}:  任务已完成"
                    )
                }
            }
        }.onFailure {
            Log.e(TAG, "文件接收 Exception: " + it.message, it)
            logOutputListener?.invoke("The file receiving service has an error")
        }
    }

    private suspend fun saveFile(
        dis: DataInputStream,
        externalFilesDir: File,
        id: Int,
        receiveFileSizeMap: MutableMap<Int, Long>,
        receivedFileList: MutableList<String>
    ) {
        //读取文件头信息
        val fileMessageByte = ByteArray(157)
        dis.read(fileMessageByte, 0, fileMessageByte.size)
        val fileMessage = String(fileMessageByte)

        Log.d(TAG, "文件头为：$fileMessage")
        //parse文件头信息，得到文件名称和文件长度
        val fileNameWithSpace = fileMessage.split("--").toTypedArray()[1]
        var fileName = fileNameWithSpace.split(" ").toTypedArray()[0]
        try {
            fileName = URLDecoder.decode(fileName, "utf-8")
        } catch (e: UnsupportedEncodingException) {
            e.printStackTrace()
        }
        val fileLength = fileMessage.split("--").toTypedArray()[2].toLong()
        var remainingLength = fileLength

        //创建文件实例和文件输出流
        val file = File(externalFilesDir, fileName)
        runCatching {

            FileOutputStream(file).use { fileOutputStream ->
                //开始接受文件主体
                var bytes = ByteArray(8192)
                var length: Int
                var total: Long = 0

                //这个地方是关键，你输出的总byte必须和文件长度是一样的，所以当剩余的文件长度小于传输
                //的bytes[]的长度的话，要把bytes[]的长度重新设置为小于等于剩余文件的长度。当fileLength
                //为0的时候，所有数据传输完毕。
                while (dis.read(bytes, 0, bytes.size)
                        .also { length = it } != -1
                ) {
                    fileOutputStream.write(bytes, 0, length)
                    fileOutputStream.flush()
                    total += length.toLong()
                    remainingLength -= length.toLong()
                    if (remainingLength == 0L) {
                        break
                    }
                    if (remainingLength < bytes.size) {
                        bytes = ByteArray(remainingLength.toInt())
                    }
//                    val progress = (total * 100 / fileLength).toInt()
//                    Log.i(
//                        TAG,
//                        "文件名：$fileName, 接收进度: $progress , len = $length , total = $total"
//                    )
                }
                Log.d(TAG, "文件接收成功，文件的MD5码是：" + Md5Util.getMd5(file))
                receiveFileSizeMap[id] =
                    (receiveFileSizeMap[id] ?: 0L).plus(fileLength)
//                logOutputListener?.invoke("文件接收成功，文件名：$fileName, ID为 $id 的终端已发送 ${receiveFileSizeMap[id] ?: 0L} 字节数据")
                receivedFileList.add(fileName)
                val endTime = System.currentTimeMillis()
                deviceBeanMap[id] =
                    deviceBeanMap[id]?.copy(
                        time = endTime - (deviceBeanMap[id]?.startTime ?: 0),
                        endTime = endTime,
                        receivePrimarySize = realReceivePrimaryFileSizeMap[id]!!,
                        receiveBackupSize = realReceiveBackupFileSizeMap[id]!!
                    )
                deviceInfoListener?.invoke(deviceBeanMap[id])
                printReceiveLog(
                    externalFilesDir,
                    "${
                        System.currentTimeMillis().getFormatTime()
                    }:  文件[$fileName]已接收，id： $id , endTime: $endTime, startTime: ${startReceiveTimeMap[id]?.getFormatTime()}"
                )
            }
        }.onFailure {
            Log.d(TAG, "文件接收失败，文件名：$fileName", it)
            throw it
        }
    }

    private fun checkReceiveComplete(
        id: Int
    ): Boolean {
        if ((realReceivePrimaryFileSizeMap[id] ?: 0L) > (limitedSendPrimaryFileSizeMap[id] ?: 0L)) {
            logOutputListener?.invoke("Description The number of files received by the terminal whose ID is $id reached the upper limit，Cap of ${limitedSendPrimaryFileSizeMap[id]} bytes of data")
            return true
        }
        return false
    }

    override fun onDestroy() {
        logOutputListener?.invoke("The file receiving service is closed")
        try {
            unregisterReceiver(broadcastReceiver)
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
        }
        job.cancel()
        coroutineScope.cancel()
        serverSocket?.close()
        removeGroup()
        super.onDestroy()
    }

    inner class MyBinder : Binder() {
        val service: ReceiveFileService
            get() = this@ReceiveFileService
    }

    override fun onBind(intent: Intent): IBinder {
        return MyBinder()
    }

    companion object {
        private const val TAG = "ReceiveFileService"
    }
}