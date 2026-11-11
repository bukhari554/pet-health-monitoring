package com.pethealth.monitor.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.pethealth.monitor.MainActivity
import com.pethealth.monitor.R
import com.pethealth.monitor.data.AlertSeverity
import com.pethealth.monitor.data.HealthAlert

class AlertNotifier(private val context: Context) {
    companion object {
        const val CHANNEL_ID = "pet_health_critical"
    }

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Pet Health Critical Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Context-aware critical pet health alerts"
        }
        mgr?.createNotificationChannel(channel)
    }

    fun notifyCritical(alert: HealthAlert) {
        if (alert.severity != AlertSeverity.CRITICAL) return
        ensureChannel()
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(alert.reason.title)
            .setContentText(alert.description)
            .setStyle(NotificationCompat.BigTextStyle().bigText("${alert.description}\n${alert.contextNote}"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(alert.id.hashCode(), notification)
        }
    }
}
