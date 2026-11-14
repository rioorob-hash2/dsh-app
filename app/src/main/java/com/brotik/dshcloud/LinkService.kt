package com.brotik.dshcloud

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat

/**
 * Держит процесс живым, пока приложение свёрнуто: без него Android быстро
 * выгружает WebView, и наблюдение за ответами агента умирает вместе с ним.
 * Показывает постоянное тихое уведомление «Держу связь с DSH».
 */
class LinkService : Service() {

    override fun onCreate() {
        super.onCreate()
        val notification = Notifier.linkNotification(this)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
        ServiceCompat.startForeground(this, Notifier.LINK_ID, notification, type)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null
}
