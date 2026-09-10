package com.awu.haorizi

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * 用 AlarmManager 调度本地通知。
 *
 * 设计取舍：
 * - 槽位式 requestCode（0..MAX_SLOTS-1）。每次同步先把所有槽位取消，
 *   再按 JS 传来的顺序重新分配 —— 不需要维护 id→requestCode 的映射，
 *   也不会残留幽灵闹钟。
 * - 精确闹钟被系统拒绝时（Android 12+ 用户可关闭）自动降级为 set()，
 *   提醒时间可能有几分钟偏差，但不会静默丢失。
 */
object ReminderScheduler {

    const val TAG = "HaoRiZi"
    const val MAX_SLOTS = 200
    private const val MAX_HORIZON_DAYS = 120L

    private const val ACTION_PREFIX = "com.awu.haorizi.REMINDER."

    private fun buildIntent(ctx: Context, slot: Int, fill: Boolean, title: String = "", text: String = "", nid: Int = 0): Intent {
        val it = Intent(ctx, ReminderReceiver::class.java)
        it.action = ACTION_PREFIX + slot
        if (fill) {
            it.putExtra(ReminderReceiver.EXTRA_TITLE, title)
            it.putExtra(ReminderReceiver.EXTRA_TEXT, text)
            it.putExtra(ReminderReceiver.EXTRA_NID, nid)
        }
        return it
    }

    fun cancelAll(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        for (i in 0 until MAX_SLOTS) {
            val pi = PendingIntent.getBroadcast(
                ctx, i, buildIntent(ctx, i, false),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            ) ?: continue
            am.cancel(pi)
            pi.cancel()
        }
    }

    fun canScheduleExact(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
        return am.canScheduleExactAlarms()
    }

    /** 从持久化的槽位重新排闹钟（开机、改系统时间、App 启动时调用） */
    fun reschedule(ctx: Context) {
        val now = System.currentTimeMillis()
        val horizon = now + MAX_HORIZON_DAYS * 86400_000L
        schedule(ctx, ReminderStore.pending(ctx, now).filter { it.at <= horizon })
    }

    /** 全量重排：先清空所有槽位，再按顺序写入 */
    fun schedule(ctx: Context, slots: List<ReminderStore.Slot>) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        cancelAll(ctx)

        val exact = canScheduleExact(ctx)
        var done = 0
        for ((i, s) in slots.withIndex()) {
            if (i >= MAX_SLOTS) break
            val pi = PendingIntent.getBroadcast(
                ctx, i,
                buildIntent(ctx, i, true, s.title, s.text, s.id.hashCode()),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            try {
                if (exact) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, s.at, pi)
                } else {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, s.at, pi)
                }
                done++
            } catch (e: SecurityException) {
                Log.w(TAG, "精确闹钟被拒，降级: ${e.message}")
                try {
                    am.set(AlarmManager.RTC_WAKEUP, s.at, pi)
                    done++
                } catch (e2: Exception) {
                    Log.e(TAG, "排闹钟失败: ${e2.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "排闹钟失败: ${e.message}")
            }
        }
        Log.i(TAG, "已排 $done 个提醒（精确=$exact）")
    }

    /** 立刻弹一条测试通知，方便用户验证权限是否给够 */
    fun fireTest(ctx: Context) {
        Notifier.post(
            ctx,
            "好日子",
            "提醒功能正常，重要的日子我会替你记着 ⚡",
            "test-${System.currentTimeMillis()}".hashCode()
        )
    }
}
