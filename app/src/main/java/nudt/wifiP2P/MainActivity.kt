package nudt.wifiP2P

import android.Manifest.permission
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.wifi.p2p.WifiP2pManager
import android.os.Bundle
import android.os.Environment
import android.os.IBinder
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import androidx.core.app.ActivityCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.GridLayoutManager
import kotlinx.coroutines.*
import nudt.wifiP2P.bean.DeviceBean
import nudt.wifiP2P.bean.StoreLogBean
import nudt.wifiP2P.database.AppDatabase
import nudt.wifiP2P.database.dao.StoreLogDao
import nudt.wifiP2P.databinding.ActivityMainBinding
import nudt.wifiP2P.service.CopyFilesService
import nudt.wifiP2P.service.HttpService
import nudt.wifiP2P.service.ReceiveFileService
import nudt.wifiP2P.service.SendFilesService
import nudt.wifiP2P.util.*
import org.apache.commons.math3.distribution.PoissonDistribution

class MainActivity : BaseActivity() {

    private var _binding: ActivityMainBinding? = null

    // this property is valid between onCreateView and onDestroyView.
    private val binding: ActivityMainBinding
        get() = _binding!!

    private var mReceiveFileService: ReceiveFileService? = null
    private var mSendFilesService: SendFilesService? = null
    private var mCopyFilesService: CopyFilesService? = null
    private var mHttpService: HttpService? = null

    private val deviceAdapter = DeviceAdapter(deviceList)


    private val coroutineScope = CoroutineScope(Job() + Dispatchers.IO)
    private var job: Job? = null

    private lateinit var db: AppDatabase
    private lateinit var storeLogDao: StoreLogDao

    private val receiveServiceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val binder = service as ReceiveFileService.MyBinder
            mReceiveFileService = binder.service
            mReceiveFileService?.logOutputListener = this@MainActivity::log
            mReceiveFileService?.connectedInfoListener = {
                runOnUiThread {
                    binding.tvConnectionInfo.text =
                        "$it\nTask start time：${planStartTime.getFormatTime()}"
                }
            }
            mReceiveFileService?.deviceInfoListener = {
                runOnUiThread {
                    deviceList.replaceAll { oldBean -> if (it?.id == oldBean.id) it else oldBean }
                    deviceAdapter.notifyDataSetChanged()
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            mReceiveFileService = null
        }
    }
    private val sendServiceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val binder = service as SendFilesService.MyBinder
            mSendFilesService = binder.service
            mSendFilesService?.logOutputListener = this@MainActivity::log
            mSendFilesService?.connectedInfoListener = {
                binding.tvConnectionInfo.text = it
            }
            mSendFilesService?.startWifiManager()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            mSendFilesService = null
        }
    }
    private val copyServiceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val binder = service as CopyFilesService.MyBinder
            mCopyFilesService = binder.service
        }

        override fun onServiceDisconnected(name: ComponentName) {
            mCopyFilesService = null
        }
    }
    private val httpServiceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val binder = service as HttpService.MyBinder
            mHttpService = binder.service
            mHttpService?.onSetLimitedSendSizeListener = {
                planStartTime = System.currentTimeMillis()
                deviceList.clear()
                deviceList.addAll(it.map { bean ->
                    DeviceBean(
                        bean.id,
                        false,
                        0,
                        0,
                        0,
                        bean.primarySize,
                        bean.backupSize,
                        0,
                        0,
                        0,
                        0,
                        0, 0, 0
                    )
                })
                runOnUiThread {
                    binding.tvTab.visibility = View.VISIBLE
                    deviceAdapter.notifyDataSetChanged()
                    binding.tvLog.text = ""
                    binding.tvConnectionInfo.text =
                        "${binding.tvConnectionInfo.text.split("Task start time：")[0]}Task start time：${planStartTime.getFormatTime()}"
                    restartReceiveService()
                }
            }
            mHttpService?.onSetGenerateParameterListener = {
                runOnUiThread {
                    binding.etId.setText(it.deviceId.toString())
                    binding.etGenerateSize.setText((it.limitedGenerateSize).toString())
                    binding.etGenerateInterval.setText(it.generateInterval.toString())
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            mHttpService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        _binding = ActivityMainBinding.inflate(LayoutInflater.from(this))
        setContentView(binding.root)

        initDB()
        binding.swReceiver.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                startReceiveService()
            } else {
                startSendService()
            }
            binding.tvFileInfo.isVisible = !isChecked
            binding.groupSettings.isVisible = !isChecked
            binding.groupReliability.isVisible = isChecked
        }
        binding.etId.doAfterTextChanged {
            if (!it.isNullOrEmpty()) {
                deviceId = it.toString().toInt()
            }
        }
        binding.etGenerateSize.doAfterTextChanged {
            if (!it.isNullOrEmpty()) {
                val long = it.toString().toLong()
                if (long > 0) {
                    limitedGenerateSize = long * 1024 * 1024
                }
            }
        }
        binding.etGenerateInterval.doAfterTextChanged {
            if (!it.isNullOrEmpty()) {
                val double = it.toString().toDouble()
                if (double <= 0.0) {
                    return@doAfterTextChanged
                }
                poisson = PoissonDistribution(double)
            }
        }
        binding.etReliability.doAfterTextChanged {
            if (!it.isNullOrEmpty()) {
                mHttpService?.reliabilityValue = it.toString().toDouble()
            }
        }
        binding.rvDevice.layoutManager = GridLayoutManager(this, 2)
        binding.rvDevice.adapter = deviceAdapter
        checkPermission()

        val intent = Intent(this, HttpService::class.java)
        bindService(intent, httpServiceConnection, BIND_AUTO_CREATE)
    }

    private fun initDB() {
        db = AppDatabaseUtils.getInstance(applicationContext)
        storeLogDao = db.storeLogDao()
    }

    override fun onResume() {
        super.onResume()
        var storeLogSaveTime = 0L
        job = coroutineScope.launch {
            while (isActive) {
                val primaryDirLength =
                    getExternalFilesDir(Environment.DIRECTORY_PICTURES)?.getOrCreate()?.totalSize
                        ?: 0
                val primaryPercent =
                    String.format("%.2f", (primaryDirLength * 100.0 / limitedGenerateSize))
                val backupDirLength = getExternalFilesDir(backup)?.getOrCreate()?.totalSize ?: 0
                val backupPercent =
                    String.format("%.2f", (backupDirLength * 100.0 / limitedGenerateSize))
                val totalDirLength = primaryDirLength + backupDirLength
                val totalPercent =
                    String.format("%.2f", (totalDirLength * 100.0 / limitedGenerateSize))
                val ashcanDirLength = getExternalFilesDir(ashcan)?.getOrCreate()?.totalSize ?: 0

                withContext(Dispatchers.Main) {
                    binding.tvFileInfo.text =
                        "Primary queue length：${primaryDirLength.getFormatSize()}($primaryPercent%)\n" +
                            "Backup queue length：${backupDirLength.getFormatSize()}($backupPercent%)\n" +
                            "Data queue length：${totalDirLength.getFormatSize()}($totalPercent%)\n" +
                            "Discard data amount：${ashcanDirLength.getFormatSize()}"
                }

                withContext(Dispatchers.IO) {
                    if (System.currentTimeMillis() - storeLogSaveTime < 15000) {
                        return@withContext
                    }
                    storeLogSaveTime = System.currentTimeMillis()
                    storeLogDao.insertAll(
                        StoreLogBean(
                            0,
                            primaryDirLength.getFormatSize(),
                            primaryPercent,
                            backupDirLength.getFormatSize(),
                            backupPercent,
                            totalDirLength.getFormatSize(),
                            totalPercent,
                            ashcanDirLength.getFormatSize(),
                            System.currentTimeMillis()
                        )
                    )
                }

                delay(1000)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        job?.cancel()
    }

    private fun checkPermission() {
        ActivityCompat.requestPermissions(
            this, arrayOf(
                permission.CHANGE_NETWORK_STATE,
                permission.ACCESS_NETWORK_STATE,
                permission.WRITE_EXTERNAL_STORAGE,
                permission.READ_EXTERNAL_STORAGE, permission.ACCESS_WIFI_STATE,
                permission.CHANGE_WIFI_STATE,
                permission.ACCESS_FINE_LOCATION
            ), CODE_REQ_PERMISSIONS
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CODE_REQ_PERMISSIONS) {
            for (i in grantResults.indices) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    showToast("缺少权限，请先授予权限")
                    showToast(permissions[i])
                    return
                }
            }
            showToast("已获得权限")
            initWifiManager()
        }
    }

    private fun initWifiManager(): Boolean {
        val wifiP2pManager = getSystemService(WIFI_P2P_SERVICE) as WifiP2pManager?
        if (wifiP2pManager == null) {
            finish()
            return true
        }
        if (ActivityCompat.checkSelfPermission(this, permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "没有权限")
            return true
        }
        val channel = wifiP2pManager.initialize(this, mainLooper, null)
        wifiP2pManager.requestConnectionInfo(channel) { info ->
            if (info != null && info.groupFormed) {
                binding.swReceiver.isChecked = info.isGroupOwner
            } else {
                binding.tvConnectionInfo.text = "Connection status：Offline"
                binding.swReceiver.isChecked = true
            }
        }

        return false
    }

    override fun onDestroy() {
        mReceiveFileService?.let {
            unbindService(receiveServiceConnection)
            mReceiveFileService = null
        }
        mSendFilesService?.let {
            unbindService(sendServiceConnection)
            mSendFilesService = null
        }
        mCopyFilesService?.let {
            unbindService(copyServiceConnection)
            mCopyFilesService = null
        }
        mHttpService?.let {
            unbindService(httpServiceConnection)
            mHttpService = null
        }
        stopService(Intent(this, ReceiveFileService::class.java))
        stopService(Intent(this, SendFilesService::class.java))
        stopService(CopyFilesService::class.java)
        stopService(HttpService::class.java)

        super.onDestroy()
    }

    private fun startSendService() {
        mReceiveFileService?.let {
            unbindService(receiveServiceConnection)
        }
        stopService(Intent(this, ReceiveFileService::class.java))
        bindSendService()
        bindCopyService()
    }

    private fun startReceiveService() {
        mSendFilesService?.let {
            unbindService(sendServiceConnection)
        }
        mCopyFilesService?.let {
            unbindService(copyServiceConnection)
        }
        stopService(Intent(this, SendFilesService::class.java))
        stopService(CopyFilesService::class.java)
        bindReceiveService()
    }

    private fun restartReceiveService() {
        if (binding.swReceiver.isChecked) {
            mReceiveFileService?.let {
                unbindService(receiveServiceConnection)
            }
            stopService(Intent(this, ReceiveFileService::class.java))
            bindReceiveService()
        } else {
            binding.swReceiver.isChecked = true
        }
    }

    private fun log(log: String) {
        runOnUiThread {
            binding.tvLog.append("$log\n")
            binding.tvLog.append("----------\n")
        }
        Log.d(this.javaClass.simpleName, log)
    }

    private fun bindReceiveService() {
        val intent = Intent(this, ReceiveFileService::class.java)
        bindService(intent, receiveServiceConnection, BIND_AUTO_CREATE)
    }

    private fun bindSendService() {
        val intent = Intent(this, SendFilesService::class.java)
        bindService(intent, sendServiceConnection, BIND_AUTO_CREATE)
    }

    private fun bindCopyService() {
        val intent = Intent(this, CopyFilesService::class.java)
        bindService(intent, copyServiceConnection, BIND_AUTO_CREATE)
    }

    companion object {
        private const val CODE_REQ_PERMISSIONS = 665
        private const val TAG = "MainActivity"
    }
}