package nudt.wifiP2P.bean

import kotlinx.serialization.Serializable

@Serializable
data class LimitedSendSizeBean(val id: Int, val primarySize: Long, val backupSize: Long) {

}
