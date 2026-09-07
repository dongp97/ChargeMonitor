package com.dongp.switch5g;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;

import java.util.Calendar;
import java.util.Locale;

/**
 * 每日定时调度（App 内置，作为「小米自动任务」之外的备选方案）。
 * 配置同样存在 Settings.Global，两个 App 各存各的（ON / OFF）。
 */
public final class Scheduler {

    private Scheduler() {
    }

    private static String timerKey(String mode) {
        return "switch5g_timer_" + mode.toLowerCase(Locale.US);
    }

    /** 返回 "HH:mm"，未设置返回空串 */
    static String getTimer(Context c, String mode) {
        try {
            String v = Settings.Global.getString(c.getContentResolver(), timerKey(mode));
            return v == null ? "" : v;
        } catch (Throwable ignored) {
            return "";
        }
    }

    static void setTimer(Context c, String mode, int hour, int minute) {
        String val = String.format(Locale.US, "%02d:%02d", hour, minute);
        try {
            Settings.Global.putString(c.getContentResolver(), timerKey(mode), val);
        } catch (Throwable ignored) {
            return;
        }
        scheduleNext(c, mode);
    }

    static void clearTimer(Context c, String mode) {
        try {
            Settings.Global.putString(c.getContentResolver(), timerKey(mode), "");
        } catch (Throwable ignored) {
        }
        cancel(c, mode);
    }

    private static PendingIntent pending(Context c, String mode) {
        Intent i = new Intent(c, AlarmReceiver.class);
        i.setAction("com.dongp.switch5g.ALARM_" + mode);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(c, mode.hashCode(), i, flags);
    }

    /** 计算下一个触发点并注册闹钟；未设置定时则什么都不做 */
    static void scheduleNext(Context c, String mode) {
        String t = getTimer(c, mode);
        if (t == null || !t.contains(":")) {
            return;
        }
        int hour, minute;
        try {
            hour = Integer.parseInt(t.split(":")[0]);
            minute = Integer.parseInt(t.split(":")[1]);
        } catch (Throwable e) {
            return;
        }

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, minute);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        if (cal.getTimeInMillis() <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }

        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) {
            return;
        }
        PendingIntent pi = pending(c, mode);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi);
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi);
            }
        } catch (SecurityException ignored) {
            // 部分系统不允许精确闹钟，忽略即可（已退化为非精确）
        }
    }

    static void cancel(Context c, String mode) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am != null) {
            am.cancel(pending(c, mode));
        }
    }
}
