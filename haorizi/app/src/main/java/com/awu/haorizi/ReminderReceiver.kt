package com.awu.haorizi

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/** 通知统一出口：建通道 + 发通知 */
object Notifier {

    const val CHANNEL_ID = "haorizi_reminder"
    private const val CHANNEL_NAME = "重要日子提醒"

    fun ensureChannel(context: Context, nm: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val ch = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH)
        ch.description = "生日、纪念日、倒数日的提醒"
        ch.enableVibration(true)
        nm.createNotificationChannel(ch)
    }

    fun post(context: Context, title: String, text: String, nid: Int) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        ensureChannel(context, nm)

        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context, nid, open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }

        val notif = builder
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .build()

        try {
            nm.notify(nid, notif)
        } catch (_: SecurityException) {
            // 没给通知权限，静默忽略（App 内倒计时仍然可用）
        }
    }
}

/** 闹钟到点 -> 弹通知 */
class ReminderReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_TITLE = "title"
        const val EXTRA_TEXT = "text"
        const val EXTRA_NID = "nid"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Notifier.post(
            context,
            intent.getStringExtra(EXTRA_TITLE) ?: "好日子",
            intent.getStringExtra(EXTRA_TEXT) ?: "",
            intent.getIntExtra(EXTRA_NID, 1024)
        )
    }
}
