package com.mondiro.mride.tracking

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.mondiro.mride.R
import com.mondiro.mride.ui.Fmt
import com.mondiro.mride.ui.MainActivity

object Notifs {
    const val CH_TRACK = "tracking"
    const val CH_ALERT = "alerts"
    const val TRACK_ID = 1
    const val GPS_ID = 2

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CH_TRACK, "Đang ghi hành trình", NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_ALERT, "Nhắc nhở", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    private fun openApp(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx, 0,
        Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun tracking(ctx: Context, distM: Double, start: Long): Notification {
        val stop = PendingIntent.getService(
            ctx, 1,
            Intent(ctx, TrackingService::class.java).setAction(TrackingService.ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(ctx, CH_TRACK)
            .setSmallIcon(R.drawable.ic_stat_ride)
            .setColor(0xFFE5383B.toInt())
            .setContentTitle("Đang ghi hành trình")
            .setContentText("${Fmt.km(distM)} km · bắt đầu ${Fmt.hm(start)}")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp(ctx))
            .addAction(0, "Dừng", stop)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    fun update(ctx: Context, distM: Double, start: Long) {
        if (!Perms.hasNotif(ctx)) return
        try {
            NotificationManagerCompat.from(ctx).notify(TRACK_ID, tracking(ctx, distM, start))
        } catch (_: SecurityException) {
        }
    }

    fun gpsOff(ctx: Context) {
        if (!Perms.hasNotif(ctx)) return
        val n = NotificationCompat.Builder(ctx, CH_ALERT)
            .setSmallIcon(R.drawable.ic_stat_ride)
            .setColor(0xFFE5383B.toInt())
            .setContentTitle("Bạn đang đi xe nhưng vị trí đang tắt")
            .setContentText("Chạm để bật vị trí, M Ride sẽ ghi hành trình")
            .setAutoCancel(true)
            .setContentIntent(openApp(ctx))
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(GPS_ID, n)
        } catch (_: SecurityException) {
        }
    }
}
