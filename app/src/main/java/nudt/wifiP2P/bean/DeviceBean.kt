package nudt.wifiP2P.bean

import kotlinx.serialization.Serializable

@Serializable
data class DeviceBean(
    val id: Int = 0,
    val online: Boolean = false,
    val time: Long = 0,
    val startTime: Long = 0,
    val endTime: Long = 0,
    val limitedPrimarySize: Long = 0,
    val limitedBackupSize: Long = 0,
    val receivePrimarySize: Long = 0,
    val receiveBackupSize: Long = 0,
    val sendSpeed: Long = 0,
    val generateSpeed: Long = 0,
    val totalSize: Long = 0,
    val remainSize: Long = 0,
    val backupSize: Long = 0,
) {

}
