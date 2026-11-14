package com.brotik.dshcloud

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/** Каналы и сами уведомления: ответы агента и «держу связь». */
object Notifier {

    const val CHANNEL_REPLIES = "dsh-replies"
    const val CHANNEL_LINK = "dsh-link"
    private const val REPLY_ID = 1001
    const val LINK_ID = 1002

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_REPLIES, context.getString(R.string.channel_replies), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.channel_replies_desc)
                enableVibration(true)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_LINK, context.getString(R.string.channel_link), NotificationManager.IMPORTANCE_MIN).apply {
                description = context.getString(R.string.channel_link_desc)
                setShowBadge(false)
            },
        )
    }

    private fun openAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        return PendingIntent.getActivity(context, 0, intent, flags)
    }

    /** Уведомление «агент ответил»: превью в 2 строки, тап открывает приложение. */
    fun showReply(context: Context, preview: String) {
        ensureChannels(context)
        val text = preview.trim().ifEmpty { context.getString(R.string.reply_empty) }
        val notification = NotificationCompat.Builder(context, CHANNEL_REPLIES)
            .setSmallIcon(R.drawable.ic_stat_reply)
            .setColor(0xFF2D7FF9.toInt())
            .setContentTitle(context.getString(R.string.reply_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setOnlyAlertOnce(false)
            .setContentIntent(openAppIntent(context))
            .build()
        try {
            NotificationManagerCompat.from(context).notify(REPLY_ID, notification)
        } catch (e: SecurityException) {
            // нет разрешения POST_NOTIFICATIONS — просто молчим
        }
    }

    /** Убрать уведомление об ответе: вызывается, когда БРАТИК вернулся в приложение. */
    fun cancelReply(context: Context) {
        try {
            NotificationManagerCompat.from(context).cancel(REPLY_ID)
        } catch (e: SecurityException) {
            // разрешения нет — и уведомления не было
        }
    }

    fun test(context: Context) {
        showReply(context, context.getString(R.string.reply_test))
    }

    /** Постоянное уведомление foreground-service. */
    fun linkNotification(context: Context): android.app.Notification {
        ensureChannels(context)
        return NotificationCompat.Builder(context, CHANNEL_LINK)
            .setSmallIcon(R.drawable.ic_stat_reply)
            .setContentTitle(context.getString(R.string.link_title))
            .setContentText(context.getString(R.string.link_text))
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(openAppIntent(context))
            .build()
    }
}
