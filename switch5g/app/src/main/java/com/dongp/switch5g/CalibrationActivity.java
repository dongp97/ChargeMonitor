package com.dongp.switch5g;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import java.util.Map;

/**
 * 校准 / 设置界面（从桌面长按图标 → 「校准与设置」进入）。
 *
 * 校准的必要性：不同机型、不同运营商的 preferred_network_mode 取值并不一致，
 * 与其硬编码，不如让用户手动切一次，App 自己把「当前值」记住。
 * 校准结果写进 Settings.Global，所以「开启5G」和「关闭5G」两个 App 共享同一份校准数据。
 */
public class CalibrationActivity extends Activity {

    private String mode;
    private TextView tvInfo;
    private TimePicker timePicker;
    private CheckBox cbTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mode = BuildConfig.MODE;
        setContentView(R.layout.activity_calibration);

        tvInfo = findViewById(R.id.tv_info);
        timePicker = findViewById(R.id.time_picker);
        cbTimer = findViewById(R.id.cb_timer);
        timePicker.setIs24HourView(true);

        Button btnCalOn = findViewById(R.id.btn_cal_on);
        Button btnCalOff = findViewById(R.id.btn_cal_off);
        Button btnTest = findViewById(R.id.btn_test);
        Button btnSaveTimer = findViewById(R.id.btn_save_timer);
        Button btnClearTimer = findViewById(R.id.btn_clear_timer);
        Button btnDiag = findViewById(R.id.btn_diag);

        btnCalOn.setOnClickListener(v -> doCalibrate("ON"));
        btnCalOff.setOnClickListener(v -> doCalibrate("OFF"));
        btnTest.setOnClickListener(v -> toast(Switcher.apply(this, mode)));
        btnSaveTimer.setOnClickListener(v -> saveTimer());
        btnClearTimer.setOnClickListener(v -> {
            Scheduler.clearTimer(this, mode);
            cbTimer.setChecked(false);
            refresh();
            toast("已取消定时");
        });
        btnDiag.setOnClickListener(v -> copyDiag());
        cbTimer.setOnCheckedChangeListener((buttonView, isChecked) -> timePicker.setEnabled(isChecked));

        restoreTimerUi();
        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void restoreTimerUi() {
        String t = Scheduler.getTimer(this, mode);
        boolean enabled = t != null && t.contains(":");
        int hour = 23;
        int minute = 0;
        if (enabled) {
            try {
                hour = Integer.parseInt(t.split(":")[0]);
                minute = Integer.parseInt(t.split(":")[1]);
            } catch (Throwable e) {
                enabled = false;
            }
        }
        timePicker.setHour(hour);
        timePicker.setMinute(minute);
        cbTimer.setChecked(enabled);
        timePicker.setEnabled(enabled);
    }

    private void saveTimer() {
        if (!cbTimer.isChecked()) {
            Scheduler.clearTimer(this, mode);
            timePicker.setEnabled(false);
            refresh();
            toast("已取消定时");
            return;
        }
        if (!Switcher.hasPermission(this)) {
            toast("还没有 WRITE_SECURE_SETTINGS 权限，无法保存定时");
            return;
        }
        try {
            Scheduler.setTimer(this, mode, timePicker.getHour(), timePicker.getMinute());
            timePicker.setEnabled(true);
            refresh();
            toast("定时已保存：每天 " + String.format(java.util.Locale.US, "%02d:%02d",
                    timePicker.getHour(), timePicker.getMinute()));
        } catch (Throwable t) {
            toast("保存失败：" + t.getMessage());
        }
    }

    private void doCalibrate(String target) {
        if (!Switcher.hasPermission(this)) {
            toast("缺少权限：请先用电脑执行 adb 授权，见下方说明");
            return;
        }
        boolean ok = Switcher.saveCalibration(this, target);
        String label = "ON".equals(target) ? "5G 开启" : "5G 关闭（4G）";
        toast(ok ? ("已记录「" + label + "」对应的网络模式值") : "记录失败：没读到网络模式字段");
        refresh();
    }

    private void copyDiag() {
        String text = Switcher.dump(this);
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("switch5g", text));
            toast("诊断信息已复制，可粘贴发给我排查");
        } else {
            toast(text);
        }
    }

    private void refresh() {
        StringBuilder sb = new StringBuilder();
        sb.append("当前 App： ").append("ON".equals(mode) ? "开启5G" : "关闭5G").append('\n');
        sb.append("权限状态： ").append(Switcher.hasPermission(this) ? "已授权 ✓" : "未授权 ✘（见下方 adb 命令）").append("\n\n");

        sb.append("系统当前值：\n");
        Map<String, String> cur = Switcher.readCurrent(this);
        if (cur.isEmpty()) {
            sb.append("  （没读到，需排查字段名）\n");
        } else {
            for (Map.Entry<String, String> e : cur.entrySet()) {
                sb.append("  ").append(e.getKey()).append(" = ").append(e.getValue()).append('\n');
            }
        }

        sb.append("\n已记录的 5G 开： ").append(fmtCal("ON")).append('\n');
        sb.append("已记录的 5G 关： ").append(fmtCal("OFF")).append('\n');
        sb.append("内置定时： ").append(Scheduler.getTimer(this, mode).isEmpty()
                ? "未启用" : ("每天 " + Scheduler.getTimer(this, mode)));

        tvInfo.setText(sb.toString());
        View v = findViewById(R.id.tv_help);
        if (v instanceof TextView) {
            // help_text 里 %1$s 是包名，不同 flavor 包名不同，这里格式化填进去
            ((TextView) v).setText(String.format(getString(R.string.help_text), getPackageName()));
        }
    }

    private String fmtCal(String target) {
        Map<String, String> m = Switcher.readCalibration(this, target);
        return m.isEmpty() ? "未校准（会用默认值）" : String.valueOf(m.values());
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }
}
