package com.example.chargemonitor.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 充/用电阶段：以充电状态切换为分节点。
 * charging = 充电阶段；discharging = 用电（放电）阶段。
 */
@Entity(tableName = "phase")
data class Phase(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "type")
    val type: String,

    @ColumnInfo(name = "start_time")
    val startTime: Long,

    @ColumnInfo(name = "end_time")
    val endTime: Long,

    @ColumnInfo(name = "duration_s")
    val durationS: Long,

    @ColumnInfo(name = "total_mah")
    val totalMah: Double,

    @ColumnInfo(name = "avg_current_ma")
    val avgCurrentMa: Double,

    @ColumnInfo(name = "avg_power_w")
    val avgPowerW: Double
) {
    companion object {
        const val TYPE_CHARGING = "charging"
        const val TYPE_DISCHARGING = "discharging"
    }
}
