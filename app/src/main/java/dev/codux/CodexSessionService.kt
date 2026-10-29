package dev.codux

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class CodexSessionService : Service() {
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Sessões Codex", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); return START_NOT_STICKY }
            ACTION_UPDATE -> updateNotification(intent.getStringExtra(EXTRA_TEXT) ?: "O Codex está a trabalhar")
            else -> updateNotification(intent?.getStringExtra(EXTRA_TEXT) ?: "Sessão Codex ativa")
        }
        return START_NOT_STICKY
    }

    private fun updateNotification(text: String) {
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentTitle("Codux")
            .setContentText(text)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "codux_session"
        private const val NOTIFICATION_ID = 7301
        private const val EXTRA_TEXT = "codux.text"
        private const val ACTION_START = "dev.codux.session.start"
        private const val ACTION_UPDATE = "dev.codux.session.update"
        private const val ACTION_STOP = "dev.codux.session.stop"

        fun start(context: Context, text: String = "O Codex está a trabalhar") {
            val intent = Intent(context, CodexSessionService::class.java).setAction(ACTION_START).putExtra(EXTRA_TEXT, text)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
        }

        fun update(context: Context, text: String) {
            context.startService(Intent(context, CodexSessionService::class.java).setAction(ACTION_UPDATE).putExtra(EXTRA_TEXT, text))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CodexSessionService::class.java).setAction(ACTION_STOP))
        }
    }
}
