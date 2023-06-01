package nudt.wifiP2P.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.Environment
import android.os.IBinder
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import nudt.wifiP2P.R
import nudt.wifiP2P.bean.DevicePlanBean
import nudt.wifiP2P.bean.GenerateParameterBean
import nudt.wifiP2P.bean.LimitedSendSizeBean
import nudt.wifiP2P.bean.PlanResultBean
import nudt.wifiP2P.common.Constants
import nudt.wifiP2P.database.AppDatabase
import nudt.wifiP2P.database.dao.SendLogDao
import nudt.wifiP2P.database.dao.StoreLogDao
import nudt.wifiP2P.util.*
import org.apache.commons.math3.distribution.PoissonDistribution
import java.io.File

class HttpService : Service() {

    private val job = Job()
    private val coroutineScope = CoroutineScope(job + Dispatchers.IO)
    var reliabilityValue: Double = 0.9
    var onSetLimitedSendSizeListener: ((List<LimitedSendSizeBean>) -> Unit)? = null
    var onSetQuickGenerateSizeListener: ((Long) -> Unit)? = null
    var onSetGenerateParameterListener: ((GenerateParameterBean) -> Unit)? = null

    private lateinit var db: AppDatabase
    private lateinit var sendLogDao: SendLogDao
    private lateinit var storeLogDao: StoreLogDao

    private val server = coroutineScope.embeddedServer(
        factory = Netty,
        port = Constants.HTTP_PORT,
    ) {
        println("load module")
        install(CallLogging)
        // 跨域访问
        install(CORS)
        install(ContentNegotiation) {
            json()
        }
        configureException()

        routing {
            get("/") {
                call.respondText("手机型号 ${Build.MODEL} 运行正常", ContentType.Text.Plain)
            }
            get("/canSend") {
                val id = call.request.queryParameters["id"]?.toInt()
                if (limitedSendPrimaryFileSizeMap.containsKey(id)) {
                    call.respond(
                        DevicePlanBean(
                            limitedSendPrimaryFileSizeMap[id]!! - realReceivePrimaryFileSizeMap[id]!!,
                            remainingSendBackupFileSizeMap[id]!! - realReceiveBackupFileSizeMap[id]!!,
                            reliabilityValue,
                            allReceivedFileMap[id] ?: emptyList()
                        )
                    )
                } else {
                    call.respond(
                        DevicePlanBean(
                            0,
                            0,
                            0.0,
                            emptyList()
                        )
                    )
                }
            }
            get("/getPlanResult") {
                deviceList.onEach {
                    if (it.receivePrimarySize < it.limitedPrimarySize) {
                        call.respondText("undone", ContentType.Text.Plain)
                        return@get
                    }
                }
                call.respond(
                    PlanResultBean(
                        planStartTime,
                        deviceList.sumOf { it.time },
                        deviceList
                    )
                )
            }
            delete("/clearPlanData") {
                val externalFilesDir =
                    getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.getOrCreate()
                val delete = externalFilesDir?.deleteRecursively() ?: false
                call.respondText(if (delete) "deleted" else "delete fail")
            }
            delete("/clearGenerateData") {
                val primaryDir =
                    getExternalFilesDir(Environment.DIRECTORY_PICTURES)?.getOrCreate()
                val backupDir =
                    getExternalFilesDir(backup)?.getOrCreate()
                val ashcanDir =
                    getExternalFilesDir(ashcan)?.getOrCreate()
                val logDir =
                    getExternalFilesDir(logDir)?.getOrCreate()
                sendLogDao.deleteAll()
                storeLogDao.deleteAll()
                val delete =
                    primaryDir?.deleteRecursively() ?: false && backupDir?.deleteRecursively() ?: false && ashcanDir?.deleteRecursively() ?: false && logDir?.deleteRecursively() ?: false
                generateIndex = 1
                call.respondText(if (delete) "deleted" else "delete fail")
            }
            post("/setLimitedSendSize") {
                var list = call.receive<List<LimitedSendSizeBean>>()
                list = list.map {
                    it.copy(
                        primarySize = it.primarySize * 1024 * 1024,
                        backupSize = it.backupSize * 1024 * 1024
                    )
                }
                limitedSendPrimaryFileSizeMap.clear()
                limitedSendPrimaryFileSizeMap.putAll(list.associate { it.id to it.primarySize })
                limitedSendBackupFileSizeMap.clear()
                limitedSendBackupFileSizeMap.putAll(list.associate { it.id to it.backupSize })
                remainingSendBackupFileSizeMap.clear()
                remainingSendBackupFileSizeMap.putAll(list.associate { it.id to it.backupSize })
                realReceivePrimaryFileSizeMap.clear()
                realReceivePrimaryFileSizeMap.putAll(list.associate { it.id to 0 })
                realReceiveBackupFileSizeMap.clear()
                realReceiveBackupFileSizeMap.putAll(list.associate { it.id to 0 })
                currentReceivedFileMap.clear()
                onSetLimitedSendSizeListener?.invoke(list)
                call.respondText { "OK" }
                val externalFilesDir =
                    getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.getOrCreate()
                File(externalFilesDir, System.currentTimeMillis().getFormatTime()).getOrCreate()
            }
            post("/setQuickGenerateSize") {
                onSetQuickGenerateSizeListener?.invoke(
                    (call.parameters["size"]?.toLong() ?: 0L) * 1024 * 1024
                )
                call.respondText { "OK" }
            }
            post("/setReceivedFileList") {
                allReceivedFileMap = call.receive()
                call.respondText { "OK" }
            }
            get("/getReceivedFileList") {
                call.respond(currentReceivedFileMap)
            }
            post("/setGenerateParameter") {
                val generateParameterBean = call.receive<GenerateParameterBean>()
                deviceId = generateParameterBean.deviceId
                limitedGenerateSize = generateParameterBean.limitedGenerateSize * 1024 * 1024
                poisson = PoissonDistribution(generateParameterBean.generateInterval)
                onSetGenerateParameterListener?.invoke(generateParameterBean)
                call.respondText { "OK" }
            }
            get("/getGenerateParameter") {
                call.respond(
                    GenerateParameterBean(
                        deviceId,
                        (limitedGenerateSize / 1024.0 / 1024.0).toLong(),
                        poisson.mean
                    )
                )
            }

            get("/getSendLog") {
                val endTime = call.parameters["endTime"]?.toLong() ?: System.currentTimeMillis()
                call.respond(sendLogDao.getAll(endTime))
            }

            get("/getStoreLog") {
                val endTime = call.parameters["endTime"]?.toLong() ?: System.currentTimeMillis()
                call.respond(storeLogDao.getAll(endTime))
            }

            get("/getDiscardLog") {
                val endTime = call.parameters["endTime"]?.toLong() ?: System.currentTimeMillis()
                val ashcanDir = getExternalFilesDir(ashcan)?.getOrCreate()
                val ashcanFileBeans =
                    ashcanDir?.listFiles()?.filter { it.lastModified() < endTime }
                        ?.map { getAshcanFileFromFileName(it.name) }
                        ?: emptyList()
                call.respond(ashcanFileBeans)
            }
        }
        println("Server started.")
    }

    override fun onCreate() {
        super.onCreate()
        goForeground()
        println("Server starting...")

        initDB()

        coroutineScope.launch {
            server.start(wait = true)
        }
    }

    private fun initDB() {
        db = AppDatabaseUtils.getInstance(applicationContext)
        sendLogDao = db.sendLogDao()
        storeLogDao = db.storeLogDao()
    }

    private fun goForeground() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel =
                NotificationChannel("WIFI_P2P", "WIFI_P2P", NotificationManager.IMPORTANCE_NONE)
            (getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)?.createNotificationChannel(
                channel
            )
            val notification = Notification.Builder(this, "WIFI_P2P")
                .setContentTitle("WIFI P2P 服务运行中")
                .setSmallIcon(R.drawable.ic_cloud)
                .build()
            startForeground(10101, notification)
        }
    }

    override fun onDestroy() {
        server.stop(100, 1000)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder {
        return MyBinder()
    }

    inner class MyBinder : Binder() {
        val service: HttpService
            get() = this@HttpService
    }
}