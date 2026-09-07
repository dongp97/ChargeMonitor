package com.dongp.switch5g;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 内置每日定时的触发点：执行切换，然后顺延排到明天同一时间。 */
public class AlarmReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context c, Intent intent) {
        final String mode = BuildConfig.MODE;
        try {
            Switcher.apply(c, mode);
        } catch (Throwable ignored) {
        }
        Scheduler.scheduleNext(c, mode);
    }
}
