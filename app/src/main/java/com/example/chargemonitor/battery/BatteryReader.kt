package com.example.chargemonitor.battery

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import kotlin.math.abs

class BatteryReader(private val context: Context) {

    private val batteryManager: BatteryManager by lazy {
        context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    }

    /**
     * 读取电量百分比
     */
    fun readLevel(): Int {
        return batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    /**
     * 一次性读取完整采样数据。
     */
    fun readSampleData(): SampleData {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val mv = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1
        val tempTenths = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val chargeType = ChargeType.fromPluggedType(plugged)
        val isCharging = chargeType != ChargeType.NONE

        val rawCurrent = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)

        return SampleData(
            currentMa = normalizeCurrent(rawCurrent, isCharging),
            voltageV = if (mv > 0) mv / 1000.0 else 0.0,
            tempC = if (tempTenths > 0) tempTenths / 10.0 else 0.0,
            level = readLevel(),
            chargeType = chargeType
        )
    }

    /**
     * 把原始电流值归一化为 mA，并强制方向（充电为正、放电为负）。
     *
     * BATTERY_PROPERTY_CURRENT_NOW 规范单位是 μA，但三星/华为等大量设备返回 mA 甚至 nA，
     * 直接除 1000 会导致电流偏小 1000 倍（充 5 分钟却只累计 1mAh 的根因）。
     * 启发式：|raw| > 100000 视为 μA（除以 1000），否则视为 mA（直接使用）。
     * 方向由 plugged 状态决定，不信任原始符号（部分设备符号也反转）。
     */
    private fun normalizeCurrent(raw: Int, isCharging: Boolean): Double {
        if (raw == 0 || raw == Int.MIN_VALUE) return 0.0
        val magnitudeMa = if (abs(raw) > 100_000) abs(raw) / 1000.0 else abs(raw).toDouble()
        return if (isCharging) magnitudeMa else -magnitudeMa
    }
}

data class SampleData(
    val currentMa: Double,
    val voltageV: Double,
    val tempC: Double,
    val level: Int,
    val chargeType: ChargeType
)
