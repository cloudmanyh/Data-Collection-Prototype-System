package nudt.wifiP2P.bean

import kotlinx.serialization.Serializable

@Serializable
data class AshcanFileBean(
    val deviceId: Int,
    val fileIndex: Int,
    val fileSize: Long,
    val dataValue: Int,
    val generateTime: Long,
) {
}