package nudt.wifiP2P.util

import nudt.wifiP2P.bean.DeviceBean
import org.apache.commons.math3.distribution.PoissonDistribution

var limitedGenerateSize = 1000 * 1024 * 1024L
const val limitedValue = 1.5

var deviceId: Int = 0
var poisson = PoissonDistribution(4.0)
var reliability: Double = 0.9
var planStartTime: Long = 0L
var generateIndex = 1

val deviceList = mutableListOf<DeviceBean>()
val limitedSendPrimaryFileSizeMap = mutableMapOf<Int, Long>()
val limitedSendBackupFileSizeMap = mutableMapOf<Int, Long>()
val remainingSendBackupFileSizeMap = mutableMapOf<Int, Long>()
val realReceivePrimaryFileSizeMap = mutableMapOf<Int, Long>()
val realReceiveBackupFileSizeMap = mutableMapOf<Int, Long>()

var allReceivedFileMap = mutableMapOf<Int, List<String>>()
var currentReceivedFileMap = mutableMapOf<Int, MutableList<String>>()
var receivedFileList = listOf<String>()

const val penalty: Int = 3
const val backup = "backup"
const val ashcan = "ashcan"
const val logDir = "log"