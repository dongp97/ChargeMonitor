package com.dongp.switch5g;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.ContentResolver;
import android.provider.Settings;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 网络模式切换核心。
 *
 * 原理：Android 上第三方 App 无法调用 TelephonyManager.setPreferredNetworkType()
 * （需要 MODIFY_PHONE_STATE 系统签名权限，Android 9+ 隐藏 API 也被屏蔽）。
 * 唯一无需 root 的可行路径是：持有 WRITE_SECURE_SETTINGS 权限（电脑 adb 一次性授权），
 * 直接改写 Settings.Global 中的 preferred_network_mode* 字段，
 * 高通/MTK 的 telephony 模块监听该字段变化后会自动重新选网。
 */
public final class Switcher {

    /** 各机型/卡槽可能使用的字段名，逐个探测，只改已存在的那些 */
    static final String[] MODE_KEYS = {
            "preferred_network_mode1",
            "preferred_network_mode2",
            "preferred_network_mode3",
            "preferred_network_mode",
    };

    /** 仅用于诊断展示的候选字段：不同 ROM 可能把 5G 开关存在别处，不写入、只看 */
    private static final String[] PROBE_KEYS = {
            "smart_5g", "smart_5g1", "fiveg_mode", "nr_mode",
            "user_preferred_network_mode1", "user_preferred_network_mode2",
    };

    /** 校准值存放键（ON / OFF 各一份），放在 Global 表里，两个 App 天然共享 */
    private static final String CAL_PREFIX = "switch5g_cal_";

    /** 高通常见取值，仅作为未校准时的兜底 */
    static final int FALLBACK_ON = 33;   // NR_LTE_TDSCDMA_GSM_WCDMA
    static final int FALLBACK_OFF = 22;  // LTE_TDSCDMA_CDMA_EVDO_GSM_WCDMA

    private Switcher() {
    }

    static boolean hasPermission(Context c) {
        return c.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** 读取当前系统里实际存在的网络模式字段及其值 */
    static LinkedHashMap<String, String> readCurrent(Context c) {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        ContentResolver cr = c.getContentResolver();
        for (String key : MODE_KEYS) {
            String v = null;
            try {
                v = Settings.Global.getString(cr, key);
            } catch (Throwable ignored) {
            }
            if (v != null && !v.trim().isEmpty()) {
                map.put(key, v.trim());
            }
        }
        return map;
    }

    /** 读取已记录的校准值，形如 {"preferred_network_mode1":"33", ...} */
    static Map<String, String> readCalibration(Context c, String mode) {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        String raw = null;
        try {
            raw = Settings.Global.getString(c.getContentResolver(), calKey(mode));
        } catch (Throwable ignored) {
        }
        if (raw == null || raw.isEmpty()) {
            return map;
        }
        try {
            JSONObject obj = new JSONObject(raw);
            java.util.Iterator<String> it = obj.keys();
            while (it.hasNext()) {
                String k = it.next();
                map.put(k, obj.optString(k, ""));
            }
        } catch (Throwable ignored) {
        }
        return map;
    }

    /** 把当前系统值记录为该模式的校准值 */
    static boolean saveCalibration(Context c, String mode) {
        if (!hasPermission(c)) {
            return false;
        }
        Map<String, String> cur = readCurrent(c);
        if (cur.isEmpty()) {
            return false;
        }
        JSONObject obj = new JSONObject();
        try {
            for (Map.Entry<String, String> e : cur.entrySet()) {
                obj.put(e.getKey(), e.getValue());
            }
            return Settings.Global.putString(c.getContentResolver(), calKey(mode), obj.toString());
        } catch (Throwable ignored) {
            return false;
        }
    }

    static void clearCalibration(Context c, String mode) {
        try {
            Settings.Global.putString(c.getContentResolver(), calKey(mode), "");
        } catch (Throwable ignored) {
        }
    }

    private static String calKey(String mode) {
        return CAL_PREFIX + mode.toLowerCase(java.util.Locale.US);
    }

    /**
     * 执行一次切换。
     *
     * @param mode "ON" 切 5G 优先；"OFF" 切 4G（关闭 5G）
     * @return 给人看的执行结果
     */
    static String apply(Context c, String mode) {
        if (!hasPermission(c)) {
            return "缺少 WRITE_SECURE_SETTINGS 权限，请先用电脑执行 adb 授权（见 App 内说明）";
        }

        Map<String, String> cal = readCalibration(c, mode);
        Map<String, String> current = readCurrent(c);
        if (current.isEmpty()) {
            current = new LinkedHashMap<>();
            current.put("preferred_network_mode1", "");
        }

        String fallback = "ON".equals(mode) ? String.valueOf(FALLBACK_ON) : String.valueOf(FALLBACK_OFF);
        List<String> applied = new ArrayList<>();
        boolean usedFallback = false;

        for (String key : current.keySet()) {
            String target = cal.get(key);
            if (target == null || target.isEmpty()) {
                target = fallback;
                usedFallback = true;
            }
            boolean written = false;
            try {
                written = Settings.Global.putString(c.getContentResolver(), key, target);
            } catch (SecurityException e) {
                return "写入被拒绝：无 WRITE_SECURE_SETTINGS 权限";
            } catch (Throwable ignored) {
            }
            if (written) {
                applied.add(key.replace("preferred_network_mode", "mode") + "=" + target);
            }
        }

        if (applied.isEmpty()) {
            return "写入失败：没有可写入的网络模式字段（请先做一次校准）";
        }

        String label = "ON".equals(mode) ? "已开启 5G" : "已关闭 5G（切 4G）";
        if (usedFallback) {
            return label + " [未校准，用的默认值]";
        }
        return label;
    }

    /** 生成诊断文本：当前字段值 + 校准值 */
    static String dump(Context c) {
        StringBuilder sb = new StringBuilder();
        sb.append("MODE=").append(BuildConfig.MODE).append('\n');
        sb.append("permission=").append(hasPermission(c)).append('\n');
        sb.append("--- current ---\n");
        Map<String, String> cur = readCurrent(c);
        if (cur.isEmpty()) {
            sb.append("(none)\n");
        } else {
            for (Map.Entry<String, String> e : cur.entrySet()) {
                sb.append(e.getKey()).append(" = ").append(e.getValue()).append('\n');
            }
        }
        sb.append("--- probe (只读，用于排查) ---\n");
        for (String key : PROBE_KEYS) {
            String v = null;
            try {
                v = Settings.Global.getString(c.getContentResolver(), key);
            } catch (Throwable ignored) {
            }
            if (v != null && !v.isEmpty()) {
                sb.append("  ").append(key).append(" = ").append(v).append('\n');
            }
        }
        sb.append("--- calibration ON ---\n").append(readCalibration(c, "ON")).append('\n');
        sb.append("--- calibration OFF ---\n").append(readCalibration(c, "OFF")).append('\n');
        sb.append("--- timer ON ---\n").append(Scheduler.getTimer(c, "ON")).append('\n');
        sb.append("--- timer OFF ---\n").append(Scheduler.getTimer(c, "OFF")).append('\n');
        return sb.toString();
    }
}
