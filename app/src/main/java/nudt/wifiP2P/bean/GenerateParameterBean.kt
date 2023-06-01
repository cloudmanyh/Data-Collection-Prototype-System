package nudt.wifiP2P.bean

import kotlinx.serialization.Serializable

@Serializable
data class GenerateParameterBean(
    val deviceId: Int,
    val limitedGenerateSize: Long,
    val generateInterval: Double,
)