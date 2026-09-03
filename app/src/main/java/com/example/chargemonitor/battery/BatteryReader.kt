package com.example.chargemonitor.battery

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

class BatteryReader(private val context: Context) {

    private val batteryManager: BatteryManager by lazy {
        context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    }

    /**
     * 读取当前电流（mA），正值=充电，负值=放电
     * BATTERY_PROPERTY_CURRENT_NOW 返回 μA，转 mA
     */
    fun readCurrentMa(): Double {
        val currentMicro = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        return currentMicro / 1000.0
    }

    /**
     * 读取电量百分比
     */
    fun readLevel(): Int {
        return batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    /**
     * 一次性读取完整采样数据。
     * 电压 / 温度 / 充电方式共用一次 ACTION_BATTERY_CHANGED，避免重复 registerReceiver。
     */
    fun readSampleData(): SampleData {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val mv = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1
        val tempTenths = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1

        return SampleData(
            currentMa = readCurrentMa(),
            voltageV = if (mv > 0) mv / 1000.0 else 0.0,
            tempC = if (tempTenths > 0) tempTenths / 10.0 else 0.0,
            level = readLevel(),
            chargeType = ChargeType.fromPluggedType(plugged)
        )
    }
}

data class SampleData(
    val currentMa: Double,
    val voltageV: Double,
    val tempC: Double,
    val level: Int,
    val chargeType: ChargeType
)
