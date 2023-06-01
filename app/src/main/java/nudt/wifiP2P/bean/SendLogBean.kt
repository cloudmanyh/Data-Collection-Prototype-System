package nudt.wifiP2P.bean

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "send_log")
data class SendLogBean(
    @PrimaryKey(autoGenerate = true) val id: Int,
    val senderId: Int,
    val fileIndex: Int,
    val fileSize: Long,
    val dataValue: Int,
    val generateTime: Long,
    val sendTime: Long,
    val backup: Boolean,
) {
}