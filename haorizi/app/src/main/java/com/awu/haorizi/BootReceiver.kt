package com.awu.haorizi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 开机 / 改系统时间 / 版本更新后，把闹钟重新排一遍。
 * 数据从 SharedPreferences 读，不依赖 WebView —— 开机时不会有 JS 环境。
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.i(ReminderScheduler.TAG, "BootReceiver: $action，恢复提醒")
        try {
            ReminderScheduler.reschedule(context)
        } catch (e: Exception) {
            Log.e(ReminderScheduler.TAG, "恢复提醒失败: ${e.message}")
        }
    }
}
