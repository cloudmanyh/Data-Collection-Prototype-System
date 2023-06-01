package nudt.wifiP2P.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import nudt.wifiP2P.bean.StoreLogBean

@Dao
interface StoreLogDao {
    @Query("SELECT * FROM store_log")
    fun getAll(): List<StoreLogBean>

    @Query("SELECT * FROM store_log WHERE saveTime < :endTime")
    fun getAll(endTime: Long): List<StoreLogBean>

    @Insert
    fun insertAll(vararg bean: StoreLogBean)

    @Query("DELETE FROM store_log")
    fun deleteAll()
}