package com.dongp.switch5g;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

/**
 * 备用触发方式：外部（Tasker / MacroDroid / adb）发广播即可切换，无需启动界面。
 *
 * adb 示例（配合定时任务）：
 *   adb shell am broadcast -a com.dongp.switch5g.ACTION_ON  -n com.dongp.switch5g.on/.SwitchReceiver
 *   adb shell am broadcast -a com.dongp.switch5g.ACTION_OFF -n com.dongp.switch5g.off/.SwitchReceiver
 */
public class SwitchReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context c, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        String expected = "com.dongp.switch5g.ACTION_" + BuildConfig.MODE;
        if (action == null || !expected.equals(action)) {
            return; // 两个 App 共用同一广播名，各自只响应属于自己的那个
        }
        String result;
        try {
            result = Switcher.apply(c, BuildConfig.MODE);
        } catch (Throwable t) {
            result = "切换失败：" + t.getMessage();
        }
        Toast.makeText(c, result, Toast.LENGTH_SHORT).show();
    }
}
