package nudt.wifiP2P.util

import android.content.Context
import androidx.room.Room
import nudt.wifiP2P.database.AppDatabase

object AppDatabaseUtils {
    lateinit var appDatabase: AppDatabase

    fun getInstance(context: Context): AppDatabase {
        if (!this::appDatabase.isInitialized) {
            appDatabase = Room
                .databaseBuilder(context, AppDatabase::class.java, "wifi_p2p")
                .fallbackToDestructiveMigration()
                .build()
        }
        return appDatabase
    }

}