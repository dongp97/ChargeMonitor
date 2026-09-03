package com.example.chargemonitor.battery

enum class ChargeType(val label: String) {
    WIRELESS("无线充电"),
    WIRED("有线充电"),
    USB("USB充电"),
    NONE("未充电");

    fun toDbString(): String = when (this) {
        WIRELESS -> "wireless"
        WIRED -> "wired"
        USB -> "usb"
        NONE -> "none"
    }

    companion object {
        fun fromPluggedType(plugged: Int): ChargeType = when {
            (plugged and BatteryConstants.PLUGGED_WIRELESS) != 0 -> WIRELESS
            (plugged and BatteryConstants.PLUGGED_AC) != 0 -> WIRED
            (plugged and BatteryConstants.PLUGGED_USB) != 0 -> USB
            else -> NONE
        }
    }
}

object BatteryConstants {
    const val PLUGGED_WIRELESS = 4  // BatteryManager.BATTERY_PLUGGED_WIRELESS (API 28+)
    const val PLUGGED_AC = 1
    const val PLUGGED_USB = 2
}
