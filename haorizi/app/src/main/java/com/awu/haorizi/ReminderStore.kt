package com.awu.haorizi

import android.content.Context
import android.os.Build
import org.json.JSONArray

/**
 * 提醒数据的本地持久化。
 *
 * JS 侧每次改动数据 / 打开 App 时都会把「未来 120 天内要触发的提醒」
 * 推给原生层，原生层存进 SharedPreferences。这样开机自启的
 * BootReceiver 不需要 WebView 就能恢复闹钟。
 */
object ReminderStore {

    private const val PREF = "haorizi_reminders"
    private const val KEY_LIST = "pending"
    private const val KEY_BG = "chrome_bg"
    private const val KEY_LIGHT = "chrome_light"

    /** 一个提醒槽位 */
    data class Slot(val id: String, val title: String, val text: String, val at: Long)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun save(ctx: Context, json: String) {
        prefs(ctx).edit().putString(KEY_LIST, json).apply()
    }

    fun raw(ctx: Context): String = prefs(ctx).getString(KEY_LIST, "[]") ?: "[]"

    /** 解析出仍然在未来（且未超过上限）的槽位 */
    fun pending(ctx: Context, now: Long = System.currentTimeMillis()): List<Slot> {
        val out = ArrayList<Slot>()
        try {
            val arr = JSONArray(raw(ctx))
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val at = o.optLong("at", 0L)
                val id = o.optString("id")
                if (id.isEmpty() || at <= now) continue
                out.add(
                    Slot(
                        id = id,
                        title = o.optString("title", "好日子"),
                        text = o.optString("text", ""),
                        at = at
                    )
                )
            }
        } catch (_: Exception) {
            // 数据损坏时忽略，下次同步会覆盖
        }
        return out
    }

    // ------------------------------------------------------------ 界面配色

    fun saveChrome(ctx: Context, bg: String, lightIcons: Boolean) {
        prefs(ctx).edit().putString(KEY_BG, bg).putBoolean(KEY_LIGHT, lightIcons).apply()
    }

    fun chromeBg(ctx: Context): String = prefs(ctx).getString(KEY_BG, "#FBF7F2") ?: "#FBF7F2"

    fun chromeLight(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_LIGHT, true)

    /** 供通知点击后回到 App 使用 */
    fun isModern(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
}
