package com.example.chargemonitor.ui.history

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.chargemonitor.R
import com.example.chargemonitor.data.entity.Phase
import com.example.chargemonitor.databinding.ItemPhaseBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PhaseListAdapter : ListAdapter<Phase, PhaseListAdapter.ViewHolder>(DiffCallback()) {

    private val dateFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPhaseBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemPhaseBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(phase: Phase) {
            val context = binding.root.context
            val isCharging = phase.type == Phase.TYPE_CHARGING
            val color = ContextCompat.getColor(
                context,
                if (isCharging) R.color.power_red else R.color.current_green
            )

            binding.tvPhaseType.text = context.getString(
                if (isCharging) R.string.phase_charging else R.string.phase_discharging
            )
            binding.tvPhaseType.setTextColor(color)
            binding.tvPhaseTime.text = dateFormat.format(Date(phase.startTime))

            binding.tvPhaseMah.text = context.getString(
                if (isCharging) R.string.phase_charged_mah else R.string.phase_consumed_mah,
                String.format("%.0f", phase.totalMah)
            )
            binding.tvPhaseMah.setTextColor(color)

            binding.tvPhaseDuration.text = formatDuration(phase.durationS)
            binding.tvPhaseAvgCurrent.text = String.format("%.0f mA", phase.avgCurrentMa)
            binding.tvPhaseAvgPower.text = String.format("%.1f W", phase.avgPowerW)
        }
    }

    private fun formatDuration(durationS: Long): String {
        val hours = durationS / 3600
        val minutes = (durationS % 3600) / 60
        val seconds = durationS % 60
        return when {
            hours > 0 -> "${hours}h${minutes}m"
            minutes > 0 -> "${minutes}m${seconds}s"
            else -> "${seconds}s"
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<Phase>() {
        override fun areItemsTheSame(oldItem: Phase, newItem: Phase): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: Phase, newItem: Phase): Boolean =
            oldItem == newItem
    }
}
