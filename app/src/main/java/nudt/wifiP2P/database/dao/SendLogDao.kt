package nudt.wifiP2P.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import nudt.wifiP2P.bean.SendLogBean

@Dao
interface SendLogDao {
    @Query("SELECT * FROM send_log")
    fun getAll(): List<SendLogBean>

    @Query("SELECT * FROM send_log WHERE sendTime < :endTime")
    fun getAll(endTime: Long): List<SendLogBean>

    @Insert
    fun insertAll(vararg bean: SendLogBean)

    @Query("DELETE FROM send_log")
    fun deleteAll()
}