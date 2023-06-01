package nudt.wifiP2P.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nudt.wifiP2P.bean.AshcanFileBean
import nudt.wifiP2P.bean.SendLogBean
import org.joda.time.DateTime
import java.io.File
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.text.DecimalFormat
import java.util.*

/**
 * 内网IP地址
 */
val localIPAddress: String
    get() {
        val addressList = mutableListOf<String>()
        val en: Enumeration<NetworkInterface> = NetworkInterface.getNetworkInterfaces()
        while (en.hasMoreElements()) {
            val intf: NetworkInterface = en.nextElement()
            val enumIpAddr: Enumeration<InetAddress> = intf.inetAddresses
            while (enumIpAddr.hasMoreElements()) {
                val inetAddress: InetAddress = enumIpAddr.nextElement()
                if (!inetAddress.isLoopbackAddress && inetAddress is Inet4Address) {
                    addressList.add(inetAddress.hostAddress.toString())
                }
            }
        }
        return addressList.joinToString()
    }

val File?.totalSize: Long
    get() {
        return if (this == null) {
            0L
        } else if (isDirectory) {
            listFiles()?.sumOf { it.totalSize } ?: 0L
        } else {
            length()
        }
    }

fun File.getOrCreate(): File {
    if (!exists()) {
        mkdirs()
    }
    return this
}

fun File.takeLimitedSizeFiles(limitedSize: Long): List<File> {
    val originFiles = listFiles()?.apply { sortBy { it.lastModified() } }?.toMutableList()
    if (!isDirectory || originFiles.isNullOrEmpty() || limitedSize <= 0L) {
        return listOf()
    }
    val takeFiles = mutableListOf<File>()
    var takeSize = 0L
    val iterator = originFiles.listIterator()
    while (iterator.hasNext()) {
        val file = iterator.next()
        takeSize += file.length()
        takeFiles.add(file)
        iterator.remove()
        if (takeSize > limitedSize) {
            return takeFiles
        }
    }
    return takeFiles
}

fun findFitSizeFiles(originFiles: MutableList<File>, limitedSize: Long): List<File> {
    var remainSize = limitedSize
    val takeFiles = mutableListOf<File>()
    originFiles.sortByDescending { it.length() }
    val iterator = originFiles.listIterator()
    while (iterator.hasNext()) {
        val file = iterator.next()
        if (file.length() < remainSize) {
            remainSize = limitedSize - file.length()
            takeFiles.add(file)
            iterator.remove()
        }
    }
    takeFiles.add(originFiles.last())
    return takeFiles
}

fun Long.getFormatTime(): String {
    return DateTime(this).toString("yyyy-MM-dd HH:mm:ss")
}

fun Long.getFormatSize(): String {
    return if (this < 1024.0) {
        this.toString() + "B"
    } else if (this < 1024 * 1024) {
        val dou = this / 1024.00
        val df = DecimalFormat("#.##")
        df.format(dou) + "K"
    } else {
        val dou = this / 1024.00 / 1024.00
        val df = DecimalFormat("#.##")
        df.format(dou) + "M"
    }
}

fun Double.getFormatSize(): String {
    return if (this < 1024.0) {
        this.toString() + "B"
    } else if (this < 1024 * 1024) {
        val dou = this / 1024.00
        val df = DecimalFormat("#.##")
        df.format(dou) + "K"
    } else {
        val dou = this / 1024.00 / 1024.00
        val df = DecimalFormat("#.##")
        df.format(dou) + "M"
    }
}

suspend fun printReceiveLog(clientDir: File, log: String) {
    withContext(Dispatchers.IO) {
        val receiveLogFile = File(clientDir, "ReceiveLog.text")
        if (!receiveLogFile.exists()) receiveLogFile.createNewFile()
        receiveLogFile.appendText("$log\n")
    }
}


fun getSendLogFromFileName(fileName: String): SendLogBean {
    val stringList = fileName.split("_")
    val isBackup = stringList[0] == "backup"
    val startIndex = if (isBackup) 1 else 0
    return SendLogBean(
        0,
        stringList[startIndex].toInt(),
        stringList[startIndex + 1].toInt(),
        stringList[startIndex + 2].toLong(),
        stringList[startIndex + 3].toInt(),
        stringList[startIndex + 4].split(".")[0].toLong(),
        System.currentTimeMillis(),
        isBackup
    )
}

fun getAshcanFileFromFileName(fileName: String): AshcanFileBean {
    val stringList = fileName.split("_")
    val isBackup = stringList[0] == "backup"
    val startIndex = if (isBackup) 1 else 0
    return AshcanFileBean(
        stringList[startIndex].toInt(),
        stringList[startIndex + 1].toInt(),
        stringList[startIndex + 2].toLong(),
        stringList[startIndex + 3].toInt(),
        stringList[startIndex + 4].split(".")[0].toLong(),
    )
}