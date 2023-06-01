package nudt.wifiP2P

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import nudt.wifiP2P.bean.DeviceBean
import nudt.wifiP2P.util.getFormatSize
import nudt.wifiP2P.util.getFormatTime
import java.time.Duration
import kotlin.time.toKotlinDuration

class DeviceAdapter(private val wifiP2pDeviceList: List<DeviceBean>) :
    RecyclerView.Adapter<DeviceAdapter.ViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_device, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val deviceBean = wifiP2pDeviceList[position]
        holder.tvDeviceId.text = "Device Id: ${deviceBean.id}"
        holder.tvDeviceOnline.text =
            "Device state: ${if (deviceBean.online) "Online" else "Offline"}"
        holder.tvDeviceTime.text =
            "Service duration: ${
                Duration.ofMillis(deviceBean.time).toKotlinDuration()
            }"
        holder.tvStartTime.text =
            "Start time: ${
                deviceBean.startTime.getFormatTime()
            }"
        holder.tvEndTime.text =
            "End time: ${
                deviceBean.endTime.getFormatTime()
            }"
        holder.tvDeviceLimitedSize.text =
            "Planning volume: ${deviceBean.limitedPrimarySize.getFormatSize()} (${deviceBean.limitedBackupSize.getFormatSize()})"
        holder.tvDeviceRealSize.text =
            "Collection volume: ${deviceBean.receivePrimarySize.getFormatSize()} (${deviceBean.receiveBackupSize.getFormatSize()})"
        holder.tvSendSpeed.text =
            "Communication rate: ${deviceBean.sendSpeed.getFormatSize()}/s"
        holder.tvGenerateSpeed.text =
            "Data arrival rate: ${deviceBean.generateSpeed.getFormatSize()}/s"
        holder.tvTotalSize.text =
            "Data queue length: ${deviceBean.totalSize.getFormatSize()}"
        holder.tvMainSize.text =
            "Primary queue length: ${deviceBean.remainSize.getFormatSize()}"
        holder.tvBackupSize.text =
            "Backup queue length: ${deviceBean.backupSize.getFormatSize()}"
        holder.itemView.tag = position
    }

    override fun getItemCount(): Int {
        return wifiP2pDeviceList.size
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvDeviceId: TextView
        val tvDeviceOnline: TextView
        val tvDeviceTime: TextView
        val tvStartTime: TextView
        val tvEndTime: TextView
        val tvDeviceLimitedSize: TextView
        val tvDeviceRealSize: TextView
        val tvSendSpeed: TextView
        val tvGenerateSpeed: TextView
        val tvTotalSize: TextView
        val tvMainSize: TextView
        val tvBackupSize: TextView

        init {
            tvDeviceId = itemView.findViewById(R.id.tv_id)
            tvDeviceOnline = itemView.findViewById(R.id.tv_online)
            tvDeviceTime = itemView.findViewById(R.id.tv_time)
            tvStartTime = itemView.findViewById(R.id.tv_start_time)
            tvEndTime = itemView.findViewById(R.id.tv_end_time)
            tvDeviceLimitedSize = itemView.findViewById(R.id.tv_limited_size)
            tvDeviceRealSize = itemView.findViewById(R.id.tv_real_size)
            tvSendSpeed = itemView.findViewById(R.id.tv_send_speed)
            tvGenerateSpeed = itemView.findViewById(R.id.tv_generate_speed)
            tvTotalSize = itemView.findViewById(R.id.tv_total_size)
            tvMainSize = itemView.findViewById(R.id.tv_main_size)
            tvBackupSize = itemView.findViewById(R.id.tv_backup_size)
        }
    }
}