package com.dongp.switch5g;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * 恢复内置定时的时机：
 * - 开机完成
 * - 小米的快速重启（QUICKBOOT_POWERON）
 * - 本应用被重新安装/更新（MY_PACKAGE_REPLACED，此时闹钟也会失效）
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String ACTION_QUICKBOOT = "android.intent.action.QUICKBOOT_POWERON";

    @Override
    public void onReceive(Context c, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        if (action == null
                || (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !ACTION_QUICKBOOT.equals(action)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action))) {
            return;
        }
        Scheduler.scheduleNext(c, BuildConfig.MODE);
    }
}
