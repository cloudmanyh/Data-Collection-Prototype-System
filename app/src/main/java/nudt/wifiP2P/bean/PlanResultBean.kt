package nudt.wifiP2P.bean

import kotlinx.serialization.Serializable

@Serializable
data class PlanResultBean(
    val planStartTime: Long,
    val planDuration: Long,
    val deviceList: List<DeviceBean>
)
