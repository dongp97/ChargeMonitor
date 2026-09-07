package com.dongp.switch5g;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Toast;

/**
 * 主入口：被桌面图标或「小米自动任务」拉起后，立即执行切换并退出，全程无界面停留。
 */
public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String result;
        try {
            result = Switcher.apply(this, BuildConfig.MODE);
        } catch (Throwable t) {
            result = "切换失败：" + t.getMessage();
        }
        Toast.makeText(getApplicationContext(), result, Toast.LENGTH_SHORT).show();

        finish();
        overridePendingTransition(0, 0);
    }
}
