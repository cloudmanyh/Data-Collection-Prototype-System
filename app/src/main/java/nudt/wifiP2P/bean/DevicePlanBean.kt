package nudt.wifiP2P.bean

import kotlinx.serialization.Serializable

@Serializable
data class DevicePlanBean(
    val primarySize: Long,
    val backupSize: Long,
    val reliability: Double,
    val receivedFileList: List<String>
)