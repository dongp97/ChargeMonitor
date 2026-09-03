package com.example.chargemonitor.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "session")
data class Session(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "start_time")
    val startTime: Long,

    @ColumnInfo(name = "end_time")
    val endTime: Long? = null,

    @ColumnInfo(name = "duration_s")
    val durationS: Long = 0,

    @ColumnInfo(name = "start_level")
    val startLevel: Int,

    @ColumnInfo(name = "end_level")
    val endLevel: Int = 0,

    @ColumnInfo(name = "total_mah")
    val totalMah: Double = 0.0,

    @ColumnInfo(name = "total_wh")
    val totalWh: Double = 0.0,

    @ColumnInfo(name = "avg_power_w")
    val avgPowerW: Double = 0.0,

    @ColumnInfo(name = "peak_power_w")
    val peakPowerW: Double = 0.0,

    @ColumnInfo(name = "charge_type")
    val chargeType: String
)
