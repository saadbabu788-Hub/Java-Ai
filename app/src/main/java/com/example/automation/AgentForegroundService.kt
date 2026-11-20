package com.example.automation

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
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R

class AgentForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_EMERGENCY -> {
                onEmergencyStopRequested?.invoke()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                val statusText = intent?.getStringExtra(EXTRA_STATUS_TEXT)
                    ?: "AuraPilot AI operator is running..."
                val notification = buildNotification(statusText)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_description)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(statusText: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, AgentForegroundService::class.java).apply {
            action = ACTION_STOP_EMERGENCY
        }
        val stopPending = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("AuraPilot Operator Active")
            .setContentText(statusText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(statusText))
            .setOngoing(true)
            .setContentIntent(openPending)
            .addAction(
                android.R.drawable.ic_media_pause,
                getString(R.string.action_stop_execution),
                stopPending
            )
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "aurapilot_agent_runner"
        private const val NOTIFICATION_ID = 4102
        private const val EXTRA_STATUS_TEXT = "extra_status_text"
        private const val ACTION_STOP_EMERGENCY = "com.example.automation.ACTION_STOP_EMERGENCY"

        @Volatile
        var onEmergencyStopRequested: (() -> Unit)? = null

        fun startOrUpdate(context: Context, statusText: String) {
            val intent = Intent(context, AgentForegroundService::class.java).apply {
                putExtra(EXTRA_STATUS_TEXT, statusText)
            }
            runCatching {
                context.startForegroundService(intent)
            }
        }

        fun stop(context: Context) {
            runCatching {
                context.stopService(Intent(context, AgentForegroundService::class.java))
            }
        }
    }
}
