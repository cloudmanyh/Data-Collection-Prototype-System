package nudt.wifiP2P.database

import androidx.room.Database
import androidx.room.RoomDatabase
import nudt.wifiP2P.bean.SendLogBean
import nudt.wifiP2P.bean.StoreLogBean
import nudt.wifiP2P.database.dao.SendLogDao
import nudt.wifiP2P.database.dao.StoreLogDao

@Database(entities = [SendLogBean::class, StoreLogBean::class], version = 3)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sendLogDao(): SendLogDao
    abstract fun storeLogDao(): StoreLogDao
}