package app.watercup

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

/**
 * 내비 등 다른 앱을 보는 동안에도 물컵 기록과 음성 코칭이 이어지도록 앱을 살려 두는 서비스.
 * 화면 갱신은 하지 않고, 차량 신호 확인과 음성만 계속한다.
 */
class CoachService : Service() {
    override fun onCreate() {
        super.onCreate()
        Coach.get(application)
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.coach_channel), NotificationManager.IMPORTANCE_LOW)
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(this, CHANNEL)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.coach_running))
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    /** 사용자가 앱을 완전히 닫으면 함께 끝낸다. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        Coach.get(application).shutdown()
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL = "coach"

        fun start(context: Context) {
            try {
                context.startForegroundService(Intent(context, CoachService::class.java))
            } catch (e: Exception) {
                // 서비스가 허용되지 않는 환경에서는 화면이 떠 있는 동안만 동작한다
                android.util.Log.w("WaterCup", "coach service start failed", e)
            }
        }
    }
}
