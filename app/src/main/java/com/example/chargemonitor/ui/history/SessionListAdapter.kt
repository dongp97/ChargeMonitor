package com.example.chargemonitor.ui.history

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.chargemonitor.data.entity.Session
import com.example.chargemonitor.databinding.ItemSessionBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SessionListAdapter(
    private val onClick: (Session) -> Unit
) : ListAdapter<Session, SessionListAdapter.ViewHolder>(DiffCallback()) {

    private val dateFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSessionBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemSessionBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(session: Session) {
            val context = binding.root.context

            binding.tvDate.text = dateFormat.format(Date(session.startTime))
            binding.tvChargeType.text = when (session.chargeType) {
                "wireless" -> "无线"
                "wired" -> "有线"
                "usb" -> "USB"
                else -> "未知"
            }

            // 格式化时长
            val hours = session.durationS / 3600
            val minutes = (session.durationS % 3600) / 60
            val seconds = session.durationS % 60
            binding.tvDuration.text = if (hours > 0) {
                "${hours}h${minutes}m"
            } else if (minutes > 0) {
                "${minutes}m${seconds}s"
            } else {
                "${seconds}s"
            }

            binding.tvMah.text = String.format("%.0f", session.totalMah)
            binding.tvAvgPower.text = String.format("%.1fW", session.avgPowerW)
            binding.tvPeakPower.text = String.format("%.1fW", session.peakPowerW)

            binding.root.setOnClickListener { onClick(session) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<Session>() {
        override fun areItemsTheSame(oldItem: Session, newItem: Session): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Session, newItem: Session): Boolean {
            return oldItem == newItem
        }
    }
}
