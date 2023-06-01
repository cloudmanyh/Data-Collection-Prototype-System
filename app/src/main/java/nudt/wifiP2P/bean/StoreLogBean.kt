package nudt.wifiP2P.bean

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "store_log")
data class StoreLogBean(
    @PrimaryKey(autoGenerate = true) val id: Int,
    val primaryLength: String,
    val primaryPercent: String,
    val backupLength: String,
    val backupPercent: String,
    val totalLength: String,
    val totalPercent: String,
    val discardLength: String,
    val saveTime: Long
) {
}